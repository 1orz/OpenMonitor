package com.cloudorz.monitor.feature.power

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.monitor.core.data.repository.BatteryRepository
import com.cloudorz.monitor.core.model.battery.BatteryStatus
import com.cloudorz.monitor.core.model.battery.PowerStatSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PowerUiState(
    val currentBattery: BatteryStatus = BatteryStatus(),
    val sessions: List<PowerStatSession> = emptyList(),
    val isTracking: Boolean = false,
)

@HiltViewModel
class PowerViewModel @Inject constructor(
    private val batteryRepository: BatteryRepository,
) : ViewModel() {

    private val batteryFlow = batteryRepository.observeBatteryStatus(3000L)
    private val isTracking = MutableStateFlow(false)
    private val sessions = MutableStateFlow<List<PowerStatSession>>(emptyList())

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

    fun onToggleTracking() {
        isTracking.value = !isTracking.value
        // TODO: Start/stop actual power tracking via a background service or WorkManager
    }
}
