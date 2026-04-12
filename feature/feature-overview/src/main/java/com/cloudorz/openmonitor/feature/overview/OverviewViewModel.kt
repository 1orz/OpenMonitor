package com.cloudorz.openmonitor.feature.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.data.repository.BatteryRepository
import com.cloudorz.openmonitor.core.data.repository.CpuRepository
import com.cloudorz.openmonitor.core.data.repository.GpuRepository
import com.cloudorz.openmonitor.core.data.repository.MemoryRepository
import com.cloudorz.openmonitor.core.data.repository.ProcessRepository
import com.cloudorz.openmonitor.core.data.repository.ThermalRepository
import com.cloudorz.openmonitor.core.model.battery.BatteryStatus
import com.cloudorz.openmonitor.core.model.cpu.CpuGlobalStatus
import com.cloudorz.openmonitor.core.model.gpu.GpuInfo
import com.cloudorz.openmonitor.core.model.memory.MemoryInfo
import com.cloudorz.openmonitor.core.model.memory.SwapInfo
import com.cloudorz.openmonitor.core.model.process.ProcessInfo
import com.cloudorz.openmonitor.core.model.thermal.ThermalZone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OverviewUiState(
    val cpuStatus: CpuGlobalStatus = CpuGlobalStatus(),
    val memoryInfo: MemoryInfo = MemoryInfo(),
    val swapInfo: SwapInfo = SwapInfo(),
    val gpuInfo: GpuInfo = GpuInfo(),
    val batteryStatus: BatteryStatus = BatteryStatus(),
    val thermalZones: List<ThermalZone> = emptyList(),
    val topProcesses: List<ProcessInfo> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val cpuRepository: CpuRepository,
    private val memoryRepository: MemoryRepository,
    private val gpuRepository: GpuRepository,
    private val batteryRepository: BatteryRepository,
    private val thermalRepository: ThermalRepository,
    private val processRepository: ProcessRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OverviewUiState())
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    init {
        observeCpuStatus()
        observeMemoryInfo()
        observeSwapInfo()
        observeGpuInfo()
        observeBatteryStatus()
        observeThermalZones()
        observeTopProcesses()
    }

    private fun observeCpuStatus() {
        viewModelScope.launch {
            cpuRepository.observeCpuStatus(intervalMs = 1000L)
                .catch { e -> android.util.Log.w("OverviewVM", "flow error", e) }
                .collect { cpu ->
                    _uiState.update { it.copy(cpuStatus = cpu, isLoading = false) }
                }
        }
    }

    private fun observeMemoryInfo() {
        viewModelScope.launch {
            memoryRepository.observeMemoryInfo(intervalMs = 2000L)
                .catch { e -> android.util.Log.w("OverviewVM", "flow error", e) }
                .collect { memory ->
                    _uiState.update { it.copy(memoryInfo = memory, isLoading = false) }
                }
        }
    }

    private fun observeSwapInfo() {
        viewModelScope.launch {
            memoryRepository.observeSwapInfo(intervalMs = 3000L)
                .catch { e -> android.util.Log.w("OverviewVM", "flow error", e) }
                .collect { swap ->
                    _uiState.update { it.copy(swapInfo = swap, isLoading = false) }
                }
        }
    }

    private fun observeGpuInfo() {
        viewModelScope.launch {
            gpuRepository.observeGpuInfo(intervalMs = 1000L)
                .catch { e -> android.util.Log.w("OverviewVM", "flow error", e) }
                .collect { gpu ->
                    _uiState.update { it.copy(gpuInfo = gpu, isLoading = false) }
                }
        }
    }

    private fun observeBatteryStatus() {
        viewModelScope.launch {
            batteryRepository.observeBatteryStatus(intervalMs = 5000L)
                .catch { e -> android.util.Log.w("OverviewVM", "flow error", e) }
                .collect { battery ->
                    _uiState.update { it.copy(batteryStatus = battery, isLoading = false) }
                }
        }
    }

    private fun observeThermalZones() {
        viewModelScope.launch {
            thermalRepository.observeThermalZones(intervalMs = 3000L)
                .catch { e -> android.util.Log.w("OverviewVM", "flow error", e) }
                .collect { zones ->
                    _uiState.update { it.copy(thermalZones = zones, isLoading = false) }
                }
        }
    }

    private fun observeTopProcesses() {
        viewModelScope.launch {
            processRepository.observeTopProcesses(count = 5, intervalMs = 2000L)
                .catch { e -> android.util.Log.w("OverviewVM", "flow error", e) }
                .collect { processes ->
                    _uiState.update { it.copy(topProcesses = processes, isLoading = false) }
                }
        }
    }
}
