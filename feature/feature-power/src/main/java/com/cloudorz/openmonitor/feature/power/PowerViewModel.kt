package com.cloudorz.openmonitor.feature.power

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.data.CsvExporter
import com.cloudorz.openmonitor.core.data.repository.BatteryRepository
import com.cloudorz.openmonitor.core.data.repository.PowerRepository
import com.cloudorz.openmonitor.core.model.battery.BatteryStatus
import com.cloudorz.openmonitor.core.model.battery.PowerStatRecord
import com.cloudorz.openmonitor.core.model.battery.PowerStatSession
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.math.abs

data class PowerUiState(
    val currentBattery: BatteryStatus = BatteryStatus(),
    val sessions: List<PowerStatSession> = emptyList(),
    val isTracking: Boolean = false,
    val currentRecords: List<PowerStatRecord> = emptyList(),
    val trackingStartCapacity: Int = 0,
)

@HiltViewModel
class PowerViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val batteryRepository: BatteryRepository,
    private val powerRepository: PowerRepository,
    private val csvExporter: CsvExporter,
) : ViewModel() {

    private val batteryFlow = batteryRepository.observeBatteryStatus(3000L)
    private val isTracking = MutableStateFlow(false)
    private val sessions = MutableStateFlow<List<PowerStatSession>>(emptyList())
    private val currentRecords = MutableStateFlow<List<PowerStatRecord>>(emptyList())
    private val startCapacityFlow = MutableStateFlow(0)

    private var trackingSessionId: Long? = null
    private var trackingStartCapacity: Int = 0
    private var samplingJob: Job? = null
    private var recordsCollectionJob: Job? = null
    private var powerAccumulator: Double = 0.0
    private var powerSampleCount: Int = 0

    val uiState: StateFlow<PowerUiState> = combine(
        batteryFlow,
        sessions,
        isTracking,
        currentRecords,
        startCapacityFlow,
    ) { battery, sessionList, tracking, records, startCap ->
        PowerUiState(
            currentBattery = battery,
            sessions = sessionList,
            isTracking = tracking,
            currentRecords = records,
            trackingStartCapacity = startCap,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PowerUiState(),
    )

    init {
        viewModelScope.launch {
            powerRepository.getAllSessions().collect { list ->
                sessions.value = list
            }
        }
    }

    fun onToggleTracking() {
        if (isTracking.value) {
            stopTracking()
        } else {
            startTracking()
        }
    }

    private fun startTracking() {
        isTracking.value = true
        powerAccumulator = 0.0
        powerSampleCount = 0
        currentRecords.value = emptyList()

        viewModelScope.launch {
            val battery = batteryRepository.getBatteryStatus()
            trackingStartCapacity = battery.capacity
            startCapacityFlow.value = battery.capacity
            val sessionId = powerRepository.startSession(battery.capacity)
            trackingSessionId = sessionId

            // Record initial data point
            recordDataPoint(battery)

            // Start collecting records for real-time charts
            recordsCollectionJob = viewModelScope.launch {
                powerRepository.getRecordsBySession(sessionId).collect { records ->
                    currentRecords.value = records
                }
            }

            // Start periodic sampling (every 30s)
            samplingJob = viewModelScope.launch {
                while (true) {
                    delay(SAMPLING_INTERVAL_MS)
                    val currentBattery = batteryRepository.getBatteryStatus()
                    recordDataPoint(currentBattery)
                }
            }
        }
    }

    private suspend fun recordDataPoint(battery: BatteryStatus) {
        val sessionId = trackingSessionId ?: return
        val power = abs(battery.powerW)
        powerAccumulator += power
        powerSampleCount++
        powerRepository.insertRecord(
            sessionId = sessionId,
            capacity = battery.capacity,
            powerW = power,
            temperature = battery.temperatureCelsius,
            isCharging = battery.isCharging,
            isScreenOn = battery.screenOn,
        )
    }

    private fun stopTracking() {
        isTracking.value = false
        samplingJob?.cancel()
        samplingJob = null
        recordsCollectionJob?.cancel()
        recordsCollectionJob = null
        val sessionId = trackingSessionId ?: return
        trackingSessionId = null

        viewModelScope.launch {
            val battery = batteryRepository.getBatteryStatus()
            // Record final data point
            recordDataPoint(battery)
            val usedPercent = trackingStartCapacity - battery.capacity
            val avgPowerW = if (powerSampleCount > 0) {
                powerAccumulator / powerSampleCount
            } else {
                abs(battery.powerW)
            }
            powerRepository.endSession(
                sessionId = sessionId,
                usedPercent = usedPercent,
                avgPowerW = avgPowerW,
            )
            currentRecords.value = emptyList()
        }
    }

    fun getExportIntent(sessionId: Long, onReady: (Intent) -> Unit) {
        viewModelScope.launch {
            val exportDir = File(context.cacheDir, "export").apply { mkdirs() }
            val file = File(exportDir, "power_session_${sessionId}.csv")
            file.outputStream().use { csvExporter.exportPowerSession(sessionId, it) }
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onReady(Intent.createChooser(intent, null))
        }
    }

    override fun onCleared() {
        super.onCleared()
        samplingJob?.cancel()
        recordsCollectionJob?.cancel()
    }

    companion object {
        private const val SAMPLING_INTERVAL_MS = 30_000L
    }
}
