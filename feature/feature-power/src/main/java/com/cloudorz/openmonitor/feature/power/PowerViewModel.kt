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
    val expandedSessionRecords: Map<String, List<PowerStatRecord>> = emptyMap(),
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
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
    private val expandedSessionRecords = MutableStateFlow<Map<String, List<PowerStatRecord>>>(emptyMap())
    private val _selectionMode = MutableStateFlow(false)
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())

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
        expandedSessionRecords,
        _selectionMode,
        _selectedIds,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        PowerUiState(
            currentBattery = values[0] as BatteryStatus,
            sessions = values[1] as List<PowerStatSession>,
            isTracking = values[2] as Boolean,
            currentRecords = values[3] as List<PowerStatRecord>,
            trackingStartCapacity = values[4] as Int,
            expandedSessionRecords = values[5] as Map<String, List<PowerStatRecord>>,
            isSelectionMode = values[6] as Boolean,
            selectedIds = values[7] as Set<Long>,
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
        // Restore active tracking session if ViewModel was recreated
        viewModelScope.launch {
            restoreActiveSession()
        }
    }

    private suspend fun restoreActiveSession() {
        val activeSession = powerRepository.getActiveSession() ?: return
        val sessionId = activeSession.sessionId.toLongOrNull() ?: return

        trackingSessionId = sessionId
        trackingStartCapacity = 0 // We don't know original start capacity, use records
        isTracking.value = true

        // Load existing records to estimate start capacity
        val existingRecords = powerRepository.getRecordsBySessionOnce(sessionId)
        if (existingRecords.isNotEmpty()) {
            trackingStartCapacity = existingRecords.first().capacity
            startCapacityFlow.value = trackingStartCapacity
            // Restore power accumulator from existing records
            powerAccumulator = existingRecords.sumOf { it.powerW }
            powerSampleCount = existingRecords.size
        }

        // Start collecting records for real-time charts
        recordsCollectionJob = viewModelScope.launch {
            powerRepository.getRecordsBySession(sessionId).collect { records ->
                currentRecords.value = records
            }
        }

        // Resume periodic sampling
        samplingJob = viewModelScope.launch {
            while (true) {
                delay(SAMPLING_INTERVAL_MS)
                val currentBattery = batteryRepository.getBatteryStatus()
                recordDataPoint(currentBattery)
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

    fun onToggleSessionExpand(sessionId: String) {
        val current = expandedSessionRecords.value
        if (current.containsKey(sessionId)) {
            expandedSessionRecords.value = current - sessionId
        } else {
            viewModelScope.launch {
                val id = sessionId.toLongOrNull() ?: return@launch
                val records = powerRepository.getRecordsBySessionOnce(id)
                expandedSessionRecords.value = expandedSessionRecords.value + (sessionId to records)
            }
        }
    }

    fun toggleSelectionMode() {
        val newMode = !_selectionMode.value
        _selectionMode.value = newMode
        if (!newMode) _selectedIds.value = emptySet()
    }

    fun exitSelectionMode() {
        _selectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun toggleSelection(sessionId: Long) {
        _selectedIds.value = _selectedIds.value.let { ids ->
            if (ids.contains(sessionId)) ids - sessionId else ids + sessionId
        }
    }

    fun selectAll() {
        _selectedIds.value = sessions.value
            .filter { !it.isActive }
            .mapNotNull { it.sessionId.toLongOrNull() }
            .toSet()
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            powerRepository.deleteSessionsByIds(ids)
            _selectedIds.value = emptySet()
            _selectionMode.value = false
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
