package com.cloudorz.monitor.ui.sensor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.monitor.core.data.repository.SensorRepository
import com.cloudorz.monitor.core.model.sensor.SensorInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SensorViewModel @Inject constructor(
    sensorRepository: SensorRepository,
) : ViewModel() {

    val sensors: StateFlow<List<SensorInfo>> = sensorRepository
        .observeSensors()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = sensorRepository.getSensorList(),
        )
}
