package com.cloudorz.openmonitor.feature.hardware

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

data class CpuAnalysisUiState(
    val cpuStatus: CpuGlobalStatus = CpuGlobalStatus(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class CpuAnalysisViewModel @Inject constructor(
    cpuRepository: CpuRepository,
) : ViewModel() {

    val uiState: StateFlow<CpuAnalysisUiState> = cpuRepository.observeCpuStatus(intervalMs = 1000L)
        .catch { e -> android.util.Log.w("CpuAnalysisVM", "flow error", e); emit(CpuGlobalStatus()) }
        .map { cpu -> CpuAnalysisUiState(cpuStatus = cpu, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CpuAnalysisUiState(),
        )
}
