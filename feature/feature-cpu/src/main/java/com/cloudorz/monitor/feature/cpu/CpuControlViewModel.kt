package com.cloudorz.monitor.feature.cpu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.monitor.core.data.repository.CpuRepository
import com.cloudorz.monitor.core.model.cpu.CpuGlobalStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CpuControlUiState(
    val cpuStatus: CpuGlobalStatus = CpuGlobalStatus(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class CpuControlViewModel @Inject constructor(
    private val cpuRepository: CpuRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CpuControlUiState())
    val uiState: StateFlow<CpuControlUiState> = _uiState.asStateFlow()

    init {
        observeCpuStatus()
    }

    private fun observeCpuStatus() {
        viewModelScope.launch {
            cpuRepository.observeCpuStatus(intervalMs = 1000L)
                .catch { /* Silently handle errors, keep last known state */ }
                .collect { status ->
                    _uiState.update {
                        it.copy(
                            cpuStatus = status,
                            isLoading = false,
                        )
                    }
                }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val status = cpuRepository.getCpuStatus()
                _uiState.update {
                    it.copy(
                        cpuStatus = status,
                        isLoading = false,
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setGovernor(policyIndex: Int, governor: String) {
        viewModelScope.launch {
            cpuRepository.setGovernor(policyIndex, governor)
        }
    }
}
