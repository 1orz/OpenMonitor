package com.cloudorz.monitor.feature.fps

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.monitor.core.common.PermissionManager
import com.cloudorz.monitor.core.common.PrivilegeMode
import com.cloudorz.monitor.core.data.repository.BatteryRepository
import com.cloudorz.monitor.core.data.repository.CpuRepository
import com.cloudorz.monitor.core.data.repository.FpsRepository
import com.cloudorz.monitor.core.model.fps.FpsData
import com.cloudorz.monitor.core.model.fps.FpsMethod
import com.cloudorz.monitor.core.model.fps.FpsWatchSession
import com.cloudorz.monitor.core.data.datasource.ChoreographerFpsMonitor
import com.cloudorz.monitor.core.data.datasource.FrameMetricsFpsMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FpsUiState(
    val isRecording: Boolean = false,
    val currentFps: FpsData? = null,
    val fpsHistory: List<Float> = emptyList(),
    val cpuLoad: Double = 0.0,
    val temperature: Double = 0.0,
    val batteryLevel: Int = 0,
    val sessions: List<FpsWatchSession> = emptyList(),
    val currentSessionId: Long? = null,
    val fpsMethod: FpsMethod = FpsMethod.SURFACE_FLINGER,
    val availableMethods: List<FpsMethod> = emptyList(),
    val hasShellAccess: Boolean = false,
)

private const val MAX_HISTORY_SIZE = 60

@HiltViewModel
class FpsViewModel @Inject constructor(
    private val application: Application,
    private val fpsRepository: FpsRepository,
    private val cpuRepository: CpuRepository,
    private val batteryRepository: BatteryRepository,
    private val permissionManager: PermissionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FpsUiState())
    val uiState: StateFlow<FpsUiState> = _uiState.asStateFlow()

    private var fpsCollectionJob: Job? = null
    private var cpuCollectionJob: Job? = null
    private var batteryCollectionJob: Job? = null

    private var recordingStartTime: Long = 0L
    private var fpsAccumulator: MutableList<Double> = mutableListOf()

    private val choreographerMonitor = ChoreographerFpsMonitor()
    private val frameMetricsMonitor = FrameMetricsFpsMonitor()

    init {
        observeSessions()
        updateAvailableMethods()
        // 监听模式变化以更新可用方式
        viewModelScope.launch {
            permissionManager.currentMode.collect {
                updateAvailableMethods()
            }
        }
    }

    private fun updateAvailableMethods() {
        val mode = permissionManager.currentMode.value
        val hasShell = mode == PrivilegeMode.ROOT || mode == PrivilegeMode.ADB || mode == PrivilegeMode.SHIZUKU
        val methods = if (hasShell) {
            FpsMethod.entries.toList()
        } else {
            emptyList()
        }
        _uiState.update {
            if (methods.isEmpty()) {
                it.copy(availableMethods = methods, fpsMethod = FpsMethod.SURFACE_FLINGER, hasShellAccess = false)
            } else {
                val currentMethod = it.fpsMethod
                val validMethod = if (currentMethod in methods) currentMethod else methods.first()
                it.copy(availableMethods = methods, fpsMethod = validMethod, hasShellAccess = true)
            }
        }
    }

    fun setFpsMethod(method: FpsMethod) {
        if (method == _uiState.value.fpsMethod) return
        val wasRecording = _uiState.value.isRecording
        if (wasRecording) {
            stopFpsMonitors()
            fpsCollectionJob?.cancel()
            fpsCollectionJob = null
        }
        _uiState.update { it.copy(fpsMethod = method, currentFps = null, fpsHistory = emptyList()) }
        if (wasRecording) {
            startFpsCollection()
        }
    }

    private fun observeSessions() {
        viewModelScope.launch {
            fpsRepository.getAllSessions()
                .catch { /* Silently handle errors */ }
                .collect { sessions ->
                    _uiState.update { it.copy(sessions = sessions) }
                }
        }
    }

    fun startRecording(packageName: String = "", appName: String = "") {
        if (_uiState.value.isRecording) return

        viewModelScope.launch {
            val method = _uiState.value.fpsMethod
            val sessionId = fpsRepository.startSession(
                packageName = packageName.ifEmpty { "com.unknown" },
                appName = appName.ifEmpty { "Unknown App" },
                mode = method.name,
            )
            recordingStartTime = System.currentTimeMillis()
            fpsAccumulator.clear()

            _uiState.update {
                it.copy(
                    isRecording = true,
                    currentSessionId = sessionId,
                    fpsHistory = emptyList(),
                    currentFps = null,
                )
            }

            startFpsCollection()
            startCpuCollection()
            startBatteryCollection()
        }
    }

    fun stopRecording() {
        if (!_uiState.value.isRecording) return

        stopFpsMonitors()
        fpsCollectionJob?.cancel()
        cpuCollectionJob?.cancel()
        batteryCollectionJob?.cancel()
        fpsCollectionJob = null
        cpuCollectionJob = null
        batteryCollectionJob = null

        val sessionId = _uiState.value.currentSessionId
        if (sessionId != null) {
            val avgFps = if (fpsAccumulator.isNotEmpty()) {
                fpsAccumulator.average()
            } else {
                0.0
            }
            val durationSeconds = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()

            viewModelScope.launch {
                fpsRepository.endSession(
                    sessionId = sessionId,
                    avgFps = avgFps,
                    avgPowerW = 0.0,
                    durationSeconds = durationSeconds,
                )
            }
        }

        _uiState.update {
            it.copy(
                isRecording = false,
                currentSessionId = null,
            )
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            fpsRepository.deleteSession(sessionId)
        }
    }

    private fun startFpsCollection() {
        val method = _uiState.value.fpsMethod
        // 启动对应的 Monitor
        when (method) {
            FpsMethod.CHOREOGRAPHER -> choreographerMonitor.start()
            FpsMethod.FRAME_METRICS -> frameMetricsMonitor.start(application)
            FpsMethod.SURFACE_FLINGER -> { /* 无需预启动 */ }
        }

        fpsCollectionJob = viewModelScope.launch {
            when (method) {
                FpsMethod.SURFACE_FLINGER -> {
                    fpsRepository.observeFps(method = FpsMethod.SURFACE_FLINGER, intervalMs = 1000L)
                        .catch { /* Silently handle errors */ }
                        .collect { fpsData -> handleFpsData(fpsData) }
                }
                FpsMethod.CHOREOGRAPHER -> {
                    choreographerMonitor.fps.collect { fpsValue ->
                        if (fpsValue > 0) {
                            val data = FpsData(fps = fpsValue, window = "self")
                            handleFpsData(data)
                        }
                    }
                }
                FpsMethod.FRAME_METRICS -> {
                    frameMetricsMonitor.fpsData.collect { data ->
                        if (data.fps > 0) {
                            handleFpsData(data)
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleFpsData(fpsData: FpsData?) {
        if (fpsData != null) {
            fpsAccumulator.add(fpsData.fps.toDouble())

            val sessionId = _uiState.value.currentSessionId
            if (sessionId != null) {
                fpsRepository.recordFrame(sessionId, fpsData)
            }

            _uiState.update { state ->
                val newHistory = state.fpsHistory.toMutableList().apply {
                    add(fpsData.fps.toFloat())
                    if (size > MAX_HISTORY_SIZE) removeAt(0)
                }
                state.copy(
                    currentFps = fpsData,
                    fpsHistory = newHistory,
                )
            }
        }
    }

    private fun stopFpsMonitors() {
        choreographerMonitor.stop()
        frameMetricsMonitor.stop()
    }

    private fun startCpuCollection() {
        cpuCollectionJob = viewModelScope.launch {
            cpuRepository.observeCpuStatus(intervalMs = 2000L)
                .catch { /* Silently handle errors */ }
                .collect { cpuStatus ->
                    _uiState.update {
                        it.copy(
                            cpuLoad = cpuStatus.totalLoadPercent,
                            temperature = cpuStatus.temperatureCelsius,
                        )
                    }
                }
        }
    }

    private fun startBatteryCollection() {
        batteryCollectionJob = viewModelScope.launch {
            batteryRepository.observeBatteryStatus(intervalMs = 5000L)
                .catch { /* Silently handle errors */ }
                .collect { batteryStatus ->
                    _uiState.update {
                        it.copy(batteryLevel = batteryStatus.capacity)
                    }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopFpsMonitors()
        fpsCollectionJob?.cancel()
        cpuCollectionJob?.cancel()
        batteryCollectionJob?.cancel()
    }
}
