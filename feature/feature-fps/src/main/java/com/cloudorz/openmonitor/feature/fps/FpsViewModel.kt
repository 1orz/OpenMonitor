package com.cloudorz.openmonitor.feature.fps

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.data.CsvExporter
import com.cloudorz.openmonitor.core.data.repository.BatteryRepository
import com.cloudorz.openmonitor.core.data.repository.CpuRepository
import com.cloudorz.openmonitor.core.data.repository.FpsRepository
import com.cloudorz.openmonitor.core.model.fps.FpsData
import com.cloudorz.openmonitor.core.model.fps.FpsWatchSession
import com.cloudorz.openmonitor.core.data.datasource.DaemonManager
import com.cloudorz.openmonitor.core.data.datasource.DaemonState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
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
    val hasDaemon: Boolean = false,
    val currentPackageName: String = "",
    val currentAppName: String = "",
)

private const val MAX_HISTORY_SIZE = 60

@HiltViewModel
class FpsViewModel @Inject constructor(
    private val application: Application,
    private val fpsRepository: FpsRepository,
    private val cpuRepository: CpuRepository,
    private val batteryRepository: BatteryRepository,
    private val csvExporter: CsvExporter,
    private val daemonManager: DaemonManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FpsUiState())
    val uiState: StateFlow<FpsUiState> = _uiState.asStateFlow()

    private var fpsCollectionJob: Job? = null
    private var cpuCollectionJob: Job? = null
    private var batteryCollectionJob: Job? = null

    private var recordingStartTime: Long = 0L
    private var fpsAccumulator: MutableList<Double> = mutableListOf()

    init {
        observeSessions()
        viewModelScope.launch {
            daemonManager.state.collect { state ->
                _uiState.update { it.copy(hasDaemon = state == DaemonState.RUNNING) }
            }
        }
    }

    private fun observeSessions() {
        viewModelScope.launch {
            fpsRepository.getAllSessions()
                .catch { e -> android.util.Log.w("FpsVM", "flow error", e) }
                .collect { sessions ->
                    _uiState.update { it.copy(sessions = sessions) }
                }
        }
    }

    fun startRecording() {
        if (_uiState.value.isRecording) return

        val state = _uiState.value
        val packageName = state.currentPackageName.ifEmpty { "com.unknown" }
        val appName = state.currentAppName.ifEmpty { "Unknown App" }

        viewModelScope.launch {
            val sessionId = fpsRepository.startSession(
                packageName = packageName,
                appName = appName,
                mode = "DAEMON",
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
        fpsCollectionJob = viewModelScope.launch {
            fpsRepository.observeFps(intervalMs = 1000L)
                .catch { e -> android.util.Log.w("FpsVM", "flow error", e) }
                .collect { fpsData -> handleFpsData(fpsData) }
        }
    }

    private suspend fun handleFpsData(fpsData: FpsData?) {
        if (fpsData != null) {
            fpsAccumulator.add(fpsData.fps)

            val sessionId = _uiState.value.currentSessionId
            if (sessionId != null) {
                fpsRepository.recordFrame(sessionId, fpsData)
            }

            // Extract package name from fps_layer
            val pkg = extractPackageFromLayer(fpsData.window)
            val prevPkg = _uiState.value.currentPackageName

            _uiState.update { state ->
                val newHistory = state.fpsHistory.toMutableList().apply {
                    add(fpsData.fps.toFloat())
                    if (size > MAX_HISTORY_SIZE) removeAt(0)
                }
                val updated = state.copy(
                    currentFps = fpsData,
                    fpsHistory = newHistory,
                )
                if (pkg.isNotEmpty() && pkg != prevPkg) {
                    updated.copy(
                        currentPackageName = pkg,
                        currentAppName = resolveAppName(pkg),
                    )
                } else {
                    updated
                }
            }
        }
    }

    private fun startCpuCollection() {
        cpuCollectionJob = viewModelScope.launch {
            cpuRepository.observeCpuStatus(intervalMs = 2000L)
                .catch { e -> android.util.Log.w("FpsVM", "flow error", e) }
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
                .catch { e -> android.util.Log.w("FpsVM", "flow error", e) }
                .collect { batteryStatus ->
                    _uiState.update {
                        it.copy(batteryLevel = batteryStatus.capacity)
                    }
                }
        }
    }

    fun getExportIntent(sessionId: Long, onReady: (Intent) -> Unit) {
        viewModelScope.launch {
            val context = application.applicationContext
            val exportDir = File(context.cacheDir, "export").apply { mkdirs() }
            val file = File(exportDir, "fps_session_${sessionId}.csv")
            file.outputStream().use { csvExporter.exportFpsSession(sessionId, it) }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onReady(Intent.createChooser(intent, null))
        }
    }

    /**
     * Resolves a human-readable app name from a package name via PackageManager.
     */
    private fun resolveAppName(packageName: String): String {
        return try {
            val pm = application.packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    override fun onCleared() {
        super.onCleared()
        fpsCollectionJob?.cancel()
        cpuCollectionJob?.cancel()
        batteryCollectionJob?.cancel()
    }

    companion object {
        /**
         * Extracts a package name from a SurfaceFlinger layer string.
         *
         * Examples:
         *   "layerName = SurfaceView[com.tencent.lolm/com.tencent.lolm.MainActivity]#14149"
         *     → "com.tencent.lolm"
         *   "SurfaceView - com.example.app/com.example.app.Activity#123"
         *     → "com.example.app"
         *   "com.example.app/com.example.app.Activity#0"
         *     → "com.example.app"
         */
        fun extractPackageFromLayer(layer: String): String {
            if (layer.isBlank()) return ""

            // Pattern 1: "SurfaceView[pkg/activity]#id" or "[pkg/activity]"
            val bracketMatch = Regex("""\[([a-zA-Z][a-zA-Z0-9_.]*)/""").find(layer)
            if (bracketMatch != null) return bracketMatch.groupValues[1]

            // Pattern 2: "SurfaceView - pkg/activity#id"
            val dashMatch = Regex("""- ([a-zA-Z][a-zA-Z0-9_.]*)/""").find(layer)
            if (dashMatch != null) return dashMatch.groupValues[1]

            // Pattern 3: bare "pkg/activity#id"
            val slashMatch = Regex("""([a-zA-Z][a-zA-Z0-9_.]*)/[a-zA-Z]""").find(layer)
            if (slashMatch != null) return slashMatch.groupValues[1]

            return ""
        }
    }
}
