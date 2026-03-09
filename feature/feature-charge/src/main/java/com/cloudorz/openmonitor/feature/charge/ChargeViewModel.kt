package com.cloudorz.openmonitor.feature.charge

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.data.CsvExporter
import com.cloudorz.openmonitor.core.data.repository.BatteryRepository
import com.cloudorz.openmonitor.core.data.repository.ChargeRepository
import com.cloudorz.openmonitor.core.model.battery.BatteryStatus
import com.cloudorz.openmonitor.core.model.battery.ChargeStatRecord
import com.cloudorz.openmonitor.core.model.battery.ChargeStatSession
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

data class ChargeUiState(
    val currentBattery: BatteryStatus = BatteryStatus(),
    val sessions: List<ChargeStatSession> = emptyList(),
    val currentRecords: List<ChargeChartPoint> = emptyList(),
    val isChargeTracking: Boolean = false,
)

@HiltViewModel
class ChargeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val batteryRepository: BatteryRepository,
    private val chargeRepository: ChargeRepository,
    private val csvExporter: CsvExporter,
) : ViewModel() {

    private val batteryFlow = batteryRepository.observeBatteryStatus(2000L)
    private val sessions = MutableStateFlow<List<ChargeStatSession>>(emptyList())
    private val currentRecords = MutableStateFlow<List<ChargeChartPoint>>(emptyList())
    private val isChargeTracking = MutableStateFlow(false)

    private var activeSessionId: Long? = null
    private var startCapacity: Int = 0
    private var samplingJob: Job? = null
    private var recordsCollectionJob: Job? = null
    private var wasCharging: Boolean = false

    val uiState: StateFlow<ChargeUiState> = combine(
        batteryFlow,
        sessions,
        currentRecords,
        isChargeTracking,
    ) { battery, sessionList, records, tracking ->
        ChargeUiState(
            currentBattery = battery,
            sessions = sessionList,
            currentRecords = records,
            isChargeTracking = tracking,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChargeUiState(),
    )

    init {
        observeSessions()
        observeChargingState()
    }

    private fun observeSessions() {
        viewModelScope.launch {
            chargeRepository.getAllSessions().collect { list ->
                sessions.value = list
            }
        }
    }

    private fun observeChargingState() {
        viewModelScope.launch {
            batteryFlow.collect { battery ->
                val isNowCharging = battery.isCharging
                if (isNowCharging && !wasCharging) {
                    startChargeSession(battery)
                } else if (!isNowCharging && wasCharging) {
                    stopChargeSession(battery)
                }
                wasCharging = isNowCharging
            }
        }
    }

    private fun startChargeSession(battery: BatteryStatus) {
        if (activeSessionId != null) return

        viewModelScope.launch {
            startCapacity = battery.capacity
            val sessionId = chargeRepository.startSession(battery.capacity)
            activeSessionId = sessionId
            isChargeTracking.value = true

            // Record initial data point
            recordDataPoint(battery)

            // Start observing records from DB
            startRecordsCollection(sessionId)

            // Start periodic sampling
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
        val sessionId = activeSessionId ?: return
        chargeRepository.insertRecord(
            sessionId = sessionId,
            capacity = battery.capacity,
            currentMa = abs(battery.currentMa).toLong(),
            temperature = battery.temperatureCelsius.toFloat(),
            powerW = abs(battery.powerW),
        )
    }

    private fun startRecordsCollection(sessionId: Long) {
        recordsCollectionJob?.cancel()
        recordsCollectionJob = viewModelScope.launch {
            chargeRepository.getRecordsBySession(sessionId).collect { entities ->
                currentRecords.value = entities.map { it.toChartPoint() }
            }
        }
    }

    private fun stopChargeSession(battery: BatteryStatus) {
        val sessionId = activeSessionId ?: return
        activeSessionId = null
        isChargeTracking.value = false

        samplingJob?.cancel()
        samplingJob = null
        recordsCollectionJob?.cancel()
        recordsCollectionJob = null

        viewModelScope.launch {
            // Record final data point
            chargeRepository.insertRecord(
                sessionId = sessionId,
                capacity = battery.capacity,
                currentMa = abs(battery.currentMa).toLong(),
                temperature = battery.temperatureCelsius.toFloat(),
                powerW = abs(battery.powerW),
            )
            val capacityDelta = battery.capacity - startCapacity
            chargeRepository.endSession(
                sessionId = sessionId,
                capacityRatio = capacityDelta,
                capacityWh = 0.0,
            )
            currentRecords.value = emptyList()
        }
    }

    fun getExportIntent(sessionId: Long, onReady: (Intent) -> Unit) {
        viewModelScope.launch {
            val exportDir = File(context.cacheDir, "export").apply { mkdirs() }
            val file = File(exportDir, "charge_session_${sessionId}.csv")
            file.outputStream().use { csvExporter.exportChargeSession(sessionId, it) }
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

private fun ChargeStatRecord.toChartPoint() = ChargeChartPoint(
    timestamp = timestamp,
    capacity = capacity,
    currentMa = currentMa.toLong(),
    temperature = temperatureCelsius.toFloat(),
)
