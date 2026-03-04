package com.cloudorz.monitor.feature.charge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.monitor.core.data.repository.BatteryRepository
import com.cloudorz.monitor.core.model.battery.BatteryStatus
import com.cloudorz.monitor.core.model.battery.ChargeStatSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChargeUiState(
    val currentBattery: BatteryStatus = BatteryStatus(),
    val sessions: List<ChargeStatSession> = emptyList(),
    val chargingEnabled: Boolean = true,
    val currentLimit: Int = 0, // mA, 0 = no limit
    val nightChargingEnabled: Boolean = false,
    val temperatureProtection: Boolean = true,
    val maxTemperature: Float = 40f,
)

@HiltViewModel
class ChargeViewModel @Inject constructor(
    private val batteryRepository: BatteryRepository,
) : ViewModel() {

    private val batteryFlow = batteryRepository.observeBatteryStatus(2000L)
    private val sessions = MutableStateFlow<List<ChargeStatSession>>(emptyList())
    private val chargingEnabled = MutableStateFlow(true)
    private val currentLimit = MutableStateFlow(0)
    private val nightChargingEnabled = MutableStateFlow(false)
    private val temperatureProtection = MutableStateFlow(true)
    private val maxTemperature = MutableStateFlow(40f)

    val uiState: StateFlow<ChargeUiState> = combine(
        batteryFlow,
        sessions,
        combine(
            chargingEnabled,
            currentLimit,
            nightChargingEnabled,
            combine(temperatureProtection, maxTemperature) { tp, mt -> tp to mt },
        ) { ce, cl, nc, (tp, mt) ->
            ChargeControlState(ce, cl, nc, tp, mt)
        },
    ) { battery, sessionList, controlState ->
        ChargeUiState(
            currentBattery = battery,
            sessions = sessionList,
            chargingEnabled = controlState.chargingEnabled,
            currentLimit = controlState.currentLimit,
            nightChargingEnabled = controlState.nightChargingEnabled,
            temperatureProtection = controlState.temperatureProtection,
            maxTemperature = controlState.maxTemperature,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChargeUiState(),
    )

    fun onChargingEnabledChanged(enabled: Boolean) {
        chargingEnabled.value = enabled
        viewModelScope.launch {
            val success = batteryRepository.setChargingEnabled(enabled)
            if (!success) {
                // Revert on failure
                chargingEnabled.value = !enabled
            }
        }
    }

    fun onCurrentLimitChanged(limitMa: Int) {
        currentLimit.value = limitMa
        viewModelScope.launch {
            val success = batteryRepository.setChargeCurrentLimit(limitMa)
            if (!success) {
                currentLimit.value = 0
            }
        }
    }

    fun onNightChargingChanged(enabled: Boolean) {
        nightChargingEnabled.value = enabled
        viewModelScope.launch {
            val success = batteryRepository.setNightCharging(enabled)
            if (!success) {
                nightChargingEnabled.value = !enabled
            }
        }
    }

    fun onTemperatureProtectionChanged(enabled: Boolean) {
        temperatureProtection.value = enabled
    }

    fun onMaxTemperatureChanged(temperature: Float) {
        maxTemperature.value = temperature
    }
}

private data class ChargeControlState(
    val chargingEnabled: Boolean,
    val currentLimit: Int,
    val nightChargingEnabled: Boolean,
    val temperatureProtection: Boolean,
    val maxTemperature: Float,
)
