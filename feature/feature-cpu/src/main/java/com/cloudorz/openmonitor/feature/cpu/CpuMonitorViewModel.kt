package com.cloudorz.openmonitor.feature.cpu

import android.util.Log
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

data class CpuMonitorUiState(
    val cpuStatus: CpuGlobalStatus = CpuGlobalStatus(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class CpuMonitorViewModel @Inject constructor(
    private val cpuRepository: CpuRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "CpuMonitorViewModel"
    }

    private val _uiState = MutableStateFlow(CpuMonitorUiState())
    val uiState: StateFlow<CpuMonitorUiState> = _uiState.asStateFlow()

    init {
        observeCpuStatus()
    }

    private fun observeCpuStatus() {
        viewModelScope.launch {
            cpuRepository.observeCpuStatus(intervalMs = 1000L)
                .catch { e -> Log.w(TAG, "observeCpuStatus error", e) }
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
            } catch (e: Exception) {
                Log.w(TAG, "refresh failed", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
