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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    cpuRepository: CpuRepository,
    memoryRepository: MemoryRepository,
    gpuRepository: GpuRepository,
    batteryRepository: BatteryRepository,
    thermalRepository: ThermalRepository,
    processRepository: ProcessRepository,
) : ViewModel() {

    val uiState: StateFlow<OverviewUiState> = combine(
        cpuRepository.observeCpuStatus(intervalMs = 1000L)
            .catch { e -> android.util.Log.w("OverviewVM", "cpu flow error", e); emit(CpuGlobalStatus()) },
        memoryRepository.observeMemoryInfo(intervalMs = 2000L)
            .catch { e -> android.util.Log.w("OverviewVM", "memory flow error", e); emit(MemoryInfo()) },
        memoryRepository.observeSwapInfo(intervalMs = 3000L)
            .catch { e -> android.util.Log.w("OverviewVM", "swap flow error", e); emit(SwapInfo()) },
        gpuRepository.observeGpuInfo(intervalMs = 1000L)
            .catch { e -> android.util.Log.w("OverviewVM", "gpu flow error", e); emit(GpuInfo()) },
        batteryRepository.observeBatteryStatus(intervalMs = 5000L)
            .catch { e -> android.util.Log.w("OverviewVM", "battery flow error", e); emit(BatteryStatus()) },
    ) { cpu, memory, swap, gpu, battery ->
        OverviewUiState(
            cpuStatus = cpu,
            memoryInfo = memory,
            swapInfo = swap,
            gpuInfo = gpu,
            batteryStatus = battery,
        )
    }.combine(
        thermalRepository.observeThermalZones(intervalMs = 3000L)
            .catch { e -> android.util.Log.w("OverviewVM", "thermal flow error", e); emit(emptyList()) },
    ) { state, zones ->
        state.copy(thermalZones = zones)
    }.combine(
        processRepository.observeTopProcesses(count = 5, intervalMs = 2000L)
            .catch { e -> android.util.Log.w("OverviewVM", "process flow error", e); emit(emptyList()) },
    ) { state, processes ->
        state.copy(topProcesses = processes, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OverviewUiState(),
    )
}
