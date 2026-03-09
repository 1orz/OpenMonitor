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
import com.cloudorz.openmonitor.core.model.battery.PowerStatSession
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class PowerUiState(
    val currentBattery: BatteryStatus = BatteryStatus(),
    val sessions: List<PowerStatSession> = emptyList(),
    val isTracking: Boolean = false,
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

    private var trackingSessionId: Long? = null
    private var trackingStartCapacity: Int = 0
    private var trackingJob: Job? = null

    val uiState: StateFlow<PowerUiState> = combine(
        batteryFlow,
        sessions,
        isTracking,
    ) { battery, sessionList, tracking ->
        PowerUiState(
            currentBattery = battery,
            sessions = sessionList,
            isTracking = tracking,
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
        trackingJob = viewModelScope.launch {
            val battery = batteryRepository.getBatteryStatus()
            trackingStartCapacity = battery.capacity
            trackingSessionId = powerRepository.startSession(battery.capacity)
        }
    }

    private fun stopTracking() {
        isTracking.value = false
        trackingJob?.cancel()
        val sessionId = trackingSessionId ?: return
        trackingSessionId = null

        viewModelScope.launch {
            val battery = batteryRepository.getBatteryStatus()
            val usedPercent = trackingStartCapacity - battery.capacity
            powerRepository.endSession(
                sessionId = sessionId,
                usedPercent = usedPercent,
                avgPowerW = battery.powerW,
            )
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
}
