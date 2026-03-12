package com.cloudorz.openmonitor.core.data.datasource

import android.util.Log
import com.cloudorz.openmonitor.core.data.repository.FpsRepository
import com.cloudorz.openmonitor.core.model.fps.FpsData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class FpsRecordingState {
    IDLE,
    COUNTDOWN,
    RECORDING,
}

data class FpsRecordingInfo(
    val sessionId: Long = 0,
    val countdownSeconds: Int = 0,
    val elapsedSeconds: Long = 0,
    val durationLimitSeconds: Int = 0,
    val avgFps: Double = 0.0,
    val packageName: String = "",
    val appName: String = "",
) {
    val remainingSeconds: Long
        get() = if (durationLimitSeconds > 0) {
            (durationLimitSeconds - elapsedSeconds).coerceAtLeast(0)
        } else {
            0
        }
}

@Singleton
class FpsRecordingManager @Inject constructor(
    private val fpsDataSource: FpsDataSource,
    private val fpsRepository: FpsRepository,
    private val aggregatedMonitorDataSource: AggregatedMonitorDataSource,
    private val batteryDataSource: BatteryDataSource,
    private val cpuDataSource: CpuDataSource,
    private val gpuDataSource: GpuDataSource,
) {
    companion object {
        private const val TAG = "FpsRecordingMgr"
        private const val COUNTDOWN_SECONDS = 3
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(FpsRecordingState.IDLE)
    val state: StateFlow<FpsRecordingState> = _state.asStateFlow()

    private val _info = MutableStateFlow(FpsRecordingInfo())
    val info: StateFlow<FpsRecordingInfo> = _info.asStateFlow()

    private var recordingJob: Job? = null
    private var fpsAccumulator = mutableListOf<Double>()
    private var powerAccumulator = mutableListOf<Double>()
    private var recordingStartTime = 0L

    fun startRecording(durationSeconds: Int) {
        if (_state.value != FpsRecordingState.IDLE) return

        _state.value = FpsRecordingState.COUNTDOWN
        _info.value = FpsRecordingInfo(
            durationLimitSeconds = durationSeconds,
            countdownSeconds = COUNTDOWN_SECONDS,
        )

        recordingJob = scope.launch {
            // Countdown phase
            for (i in COUNTDOWN_SECONDS downTo 1) {
                _info.value = _info.value.copy(countdownSeconds = i)
                delay(1000)
            }

            // Start actual recording
            fpsAccumulator.clear()
            powerAccumulator.clear()
            recordingStartTime = System.currentTimeMillis()

            val sessionId = fpsRepository.startSession(
                packageName = "",
                appName = "",
                mode = "FLOAT",
            )

            _state.value = FpsRecordingState.RECORDING
            _info.value = _info.value.copy(
                sessionId = sessionId,
                countdownSeconds = 0,
                elapsedSeconds = 0,
            )

            Log.e(TAG, "Recording started, sessionId=$sessionId, limit=${durationSeconds}s")

            // Sampling loop: 1 second interval
            while (true) {
                try {
                    val fpsData = fpsDataSource.getDaemonFps()

                    // Collect system metrics in parallel with FPS
                    val snapshot = try { aggregatedMonitorDataSource.collectSnapshot() } catch (_: Exception) { null }
                    val battery = try { batteryDataSource.getBatteryStatus() } catch (_: Exception) { null }
                    val gpuInfo = try { gpuDataSource.getGpuInfo() } catch (_: Exception) { null }

                    // Per-core frequencies
                    val coreCount = try { cpuDataSource.getCpuCoreCount() } catch (_: Exception) { 0 }
                    val coreFreqs = mutableListOf<Long>()
                    for (i in 0 until coreCount) {
                        try {
                            val info = cpuDataSource.getCoreInfo(i)
                            coreFreqs.add(info.currentFreqKHz / 1000) // kHz → MHz
                        } catch (_: Exception) {
                            coreFreqs.add(0L)
                        }
                    }

                    if (fpsData != null) {
                        fpsAccumulator.add(fpsData.fps)

                        val pw = battery?.powerW ?: 0.0
                        if (pw > 0) powerAccumulator.add(pw)

                        // Compute frame time from FPS (daemon doesn't provide per-frame data)
                        val computedFrameTimeMs = if (fpsData.fps > 0) (1000.0 / fpsData.fps).toInt() else 0
                        val enrichedFpsData = fpsData.copy(maxFrameTimeMs = computedFrameTimeMs)

                        fpsRepository.recordFrameRich(
                            sessionId = sessionId,
                            fpsData = enrichedFpsData,
                            cpuLoad = snapshot?.cpuLoadPercent ?: 0.0,
                            cpuTemp = snapshot?.cpuTempCelsius ?: 0.0,
                            gpuLoad = snapshot?.gpuLoadPercent ?: gpuInfo?.loadPercent ?: 0.0,
                            gpuFreqMhz = snapshot?.gpuFreqMhz ?: gpuInfo?.currentFreqMHz?.toInt() ?: 0,
                            batteryCapacity = battery?.capacity ?: 0,
                            batteryCurrentMa = battery?.currentMa ?: 0,
                            batteryTemp = battery?.temperatureCelsius ?: 0.0,
                            powerW = pw,
                            cpuCoreLoads = snapshot?.cpuCoreLoads ?: emptyList(),
                            cpuCoreFreqs = coreFreqs,
                        )

                        // Extract package from fps layer
                        val pkg = extractPackageFromLayer(fpsData.window)
                        if (pkg.isNotEmpty() && pkg != _info.value.packageName) {
                            _info.value = _info.value.copy(packageName = pkg)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Sample failed", e)
                }

                val elapsed = (System.currentTimeMillis() - recordingStartTime) / 1000
                val avg = if (fpsAccumulator.isNotEmpty()) fpsAccumulator.average() else 0.0
                _info.value = _info.value.copy(
                    elapsedSeconds = elapsed,
                    avgFps = avg,
                )

                // Auto-stop when reaching duration limit
                if (durationSeconds > 0 && elapsed >= durationSeconds) {
                    Log.e(TAG, "Duration limit reached, auto-stopping")
                    finishRecording(sessionId)
                    return@launch
                }

                delay(1000)
            }
        }
    }

    fun stopRecording() {
        if (_state.value != FpsRecordingState.RECORDING && _state.value != FpsRecordingState.COUNTDOWN) return

        val sessionId = _info.value.sessionId
        recordingJob?.cancel()
        recordingJob = null

        if (_state.value == FpsRecordingState.COUNTDOWN) {
            _state.value = FpsRecordingState.IDLE
            _info.value = FpsRecordingInfo()
            return
        }

        scope.launch {
            finishRecording(sessionId)
        }
    }

    private suspend fun finishRecording(sessionId: Long) {
        val durationSeconds = ((System.currentTimeMillis() - recordingStartTime) / 1000).toInt()
        val avgFps = if (fpsAccumulator.isNotEmpty()) fpsAccumulator.average() else 0.0
        val avgPower = if (powerAccumulator.isNotEmpty()) powerAccumulator.average() else 0.0

        try {
            fpsRepository.endSession(
                sessionId = sessionId,
                avgFps = avgFps,
                avgPowerW = avgPower,
                durationSeconds = durationSeconds,
            )
            Log.e(TAG, "Recording finished, sessionId=$sessionId, avgFps=%.1f, ${durationSeconds}s".format(avgFps))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to end session", e)
        }

        _state.value = FpsRecordingState.IDLE
        _info.value = FpsRecordingInfo()
        fpsAccumulator.clear()
        powerAccumulator.clear()
    }

    private fun extractPackageFromLayer(layer: String): String {
        if (layer.isBlank()) return ""
        val bracketMatch = Regex("""\[([a-zA-Z][a-zA-Z0-9_.]*)/""").find(layer)
        if (bracketMatch != null) return bracketMatch.groupValues[1]
        val dashMatch = Regex("""- ([a-zA-Z][a-zA-Z0-9_.]*)/""").find(layer)
        if (dashMatch != null) return dashMatch.groupValues[1]
        val slashMatch = Regex("""([a-zA-Z][a-zA-Z0-9_.]*)/[a-zA-Z]""").find(layer)
        if (slashMatch != null) return slashMatch.groupValues[1]
        return ""
    }
}
