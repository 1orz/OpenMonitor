package com.cloudorz.openmonitor.feature.cpu

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.data.repository.CpuRepository
import com.cloudorz.openmonitor.core.model.cpu.CpuGlobalStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CpuMonitorUiState(
    val cpuStatus: CpuGlobalStatus = CpuGlobalStatus(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class CpuMonitorViewModel @Inject constructor(
    cpuRepository: CpuRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "CpuMonitorViewModel"
    }

    fun refresh() {}

    val uiState: StateFlow<CpuMonitorUiState> = cpuRepository.observeCpuStatus(intervalMs = 1000L)
        .catch { e -> Log.w(TAG, "observeCpuStatus error", e); emit(CpuGlobalStatus()) }
        .map { status -> CpuMonitorUiState(cpuStatus = status, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CpuMonitorUiState(),
        )
}
