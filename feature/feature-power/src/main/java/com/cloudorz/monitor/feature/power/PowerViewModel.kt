package com.cloudorz.monitor.feature.power

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.monitor.core.data.repository.BatteryRepository
import com.cloudorz.monitor.core.data.repository.PowerRepository
import com.cloudorz.monitor.core.model.battery.BatteryStatus
import com.cloudorz.monitor.core.model.battery.PowerStatSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PowerUiState(
    val currentBattery: BatteryStatus = BatteryStatus(),
    val sessions: List<PowerStatSession> = emptyList(),
    val isTracking: Boolean = false,
)

@HiltViewModel
class PowerViewModel @Inject constructor(
    private val batteryRepository: BatteryRepository,
    private val powerRepository: PowerRepository,
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
}
