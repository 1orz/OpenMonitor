package com.cloudorz.openmonitor.feature.hardware

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.data.repository.CpuRepository
import com.cloudorz.openmonitor.core.data.repository.DisplayRepository
import com.cloudorz.openmonitor.core.data.repository.GpuRepository
import com.cloudorz.openmonitor.core.data.repository.MemoryRepository
import com.cloudorz.openmonitor.core.data.repository.StorageRepository
import com.cloudorz.openmonitor.core.model.cpu.CpuGlobalStatus
import com.cloudorz.openmonitor.core.model.display.DisplayInfo
import com.cloudorz.openmonitor.core.model.gpu.GpuInfo
import com.cloudorz.openmonitor.core.model.memory.MemoryInfo
import com.cloudorz.openmonitor.core.model.memory.SwapInfo
import com.cloudorz.openmonitor.core.model.storage.StorageInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HardwareInfoUiState(
    val cpuStatus: CpuGlobalStatus = CpuGlobalStatus(),
    val gpuInfo: GpuInfo = GpuInfo(),
    val memoryInfo: MemoryInfo = MemoryInfo(),
    val swapInfo: SwapInfo = SwapInfo(),
    val storageInfo: StorageInfo = StorageInfo(),
    val displayInfo: DisplayInfo = DisplayInfo(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class HardwareInfoViewModel @Inject constructor(
    private val cpuRepository: CpuRepository,
    private val gpuRepository: GpuRepository,
    private val memoryRepository: MemoryRepository,
    private val storageRepository: StorageRepository,
    private val displayRepository: DisplayRepository,
) : ViewModel() {

    private val _staticState = MutableStateFlow(HardwareInfoUiState())

    init {
        loadStaticData()
    }

    private fun loadStaticData() {
        viewModelScope.launch {
            try {
                val cpu = cpuRepository.getCpuStatus()
                _staticState.update { it.copy(cpuStatus = cpu, isLoading = false) }
            } catch (e: Exception) {
                android.util.Log.w("HardwareInfoVM", "CPU load failed", e)
            }
        }
        viewModelScope.launch {
            try {
                val gpu = gpuRepository.getGpuInfo()
                _staticState.update { it.copy(gpuInfo = gpu) }
            } catch (e: Exception) {
                android.util.Log.w("HardwareInfoVM", "GPU load failed", e)
            }
        }
        viewModelScope.launch {
            try {
                val storage = storageRepository.getStorageInfo()
                _staticState.update { it.copy(storageInfo = storage) }
            } catch (e: Exception) {
                android.util.Log.w("HardwareInfoVM", "Storage load failed", e)
            }
        }
        viewModelScope.launch {
            try {
                val display = displayRepository.getDisplayInfo()
                _staticState.update { it.copy(displayInfo = display) }
            } catch (e: Exception) {
                android.util.Log.w("HardwareInfoVM", "Display load failed", e)
            }
        }
    }

    val uiState: StateFlow<HardwareInfoUiState> = combine(
        _staticState,
        memoryRepository.observeMemoryInfo(intervalMs = 5000L)
            .catch { e -> android.util.Log.w("HardwareInfoVM", "Memory flow error", e); emit(MemoryInfo()) },
        memoryRepository.observeSwapInfo(intervalMs = 5000L)
            .catch { e -> android.util.Log.w("HardwareInfoVM", "Swap flow error", e); emit(SwapInfo()) },
    ) { static, memory, swap ->
        static.copy(memoryInfo = memory, swapInfo = swap, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HardwareInfoUiState(),
    )
}
