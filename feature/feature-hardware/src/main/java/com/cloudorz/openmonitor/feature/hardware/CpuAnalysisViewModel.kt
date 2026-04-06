package com.cloudorz.openmonitor.feature.hardware

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.data.repository.CpuRepository
import com.cloudorz.openmonitor.core.model.cpu.CpuGlobalStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CpuAnalysisUiState(
    val cpuStatus: CpuGlobalStatus = CpuGlobalStatus(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class CpuAnalysisViewModel @Inject constructor(
    private val cpuRepository: CpuRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CpuAnalysisUiState())
    val uiState: StateFlow<CpuAnalysisUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            cpuRepository.observeCpuStatus(intervalMs = 1000L)
                .catch { e -> android.util.Log.w("CpuAnalysisVM", "flow error", e) }
                .collect { cpu ->
                    _uiState.update { it.copy(cpuStatus = cpu, isLoading = false) }
                }
        }
    }
}
