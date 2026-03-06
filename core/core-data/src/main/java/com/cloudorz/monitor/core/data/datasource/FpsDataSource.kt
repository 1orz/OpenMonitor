package com.cloudorz.monitor.core.data.datasource

import android.util.Log
import com.cloudorz.monitor.core.common.ShellExecutor
import com.cloudorz.monitor.core.common.SysfsReader
import com.cloudorz.monitor.core.model.fps.FpsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FpsDataSource @Inject constructor(
    private val shellExecutor: ShellExecutor,
    private val sysfsReader: SysfsReader,
) {
    // Screen refresh rate cap (will be auto-detected, fallback 165Hz)
    @Volatile private var maxFps = 165f

    // Frame dedup: track last max actualPresent timestamp across calls
    @Volatile private var lastMaxActualPresent: Long = 0

    // ---- Realtime FPS (probe-once, single source, unified delta) ----

    private enum class RealtimeSource { UNPROBED, SYSFS, SF_1013, GFXINFO, NONE }

    @Volatile private var realtimeSource = RealtimeSource.UNPROBED
    @Volatile private var measuredFpsPath: String? = null

    // Unified delta state (single source, no cross-contamination)
    private var deltaLastCount = -1L
    private var deltaLastTime = 0L
    private var deltaLastPackage: String? = null

    // Output smoothing: hold last non-zero value briefly to avoid flicker
    private var lastReportedFps = 0f
    private var lastNonZeroTime = 0L
    private val FPS_HOLD_MS = 1500L

    suspend fun getRealtimeFps(): Float = withContext(Dispatchers.IO) {
        if (realtimeSource == RealtimeSource.UNPROBED) {
            probeRealtimeSource()
        }
        val rawFps = collectFromSource()
        smoothOutput(rawFps)
    }

    private suspend fun probeRealtimeSource() {
        // Auto-detect screen refresh rate for FPS cap
        detectMaxFps()

        // 1. Try sysfs measured_fps
        for (path in MEASURED_FPS_CANDIDATES) {
            if (sysfsReader.readString(path) != null) {
                measuredFpsPath = path
                realtimeSource = RealtimeSource.SYSFS
                Log.d(TAG, "Realtime FPS source: SYSFS ($path), maxFps=$maxFps")
                return
            }
        }
        // 2. Try service call SurfaceFlinger 1013
        val sfResult = shellExecutor.execute("service call SurfaceFlinger 1013")
        if (sfResult.isSuccess && !sfResult.stdout.contains("Error") && !sfResult.stdout.contains("not permitted")) {
            realtimeSource = RealtimeSource.SF_1013
            Log.d(TAG, "Realtime FPS source: SF_1013, maxFps=$maxFps")
            return
        }
        // 3. Try gfxinfo
        val pkg = getFocusedPackage()
        if (pkg != null) {
            val gfxResult = shellExecutor.execute("dumpsys gfxinfo $pkg")
            if (gfxResult.isSuccess && TOTAL_FRAMES_REGEX.containsMatchIn(gfxResult.stdout)) {
                realtimeSource = RealtimeSource.GFXINFO
                Log.d(TAG, "Realtime FPS source: GFXINFO, maxFps=$maxFps")
                return
            }
        }
        realtimeSource = RealtimeSource.NONE
        Log.d(TAG, "Realtime FPS source: NONE")
    }

    private suspend fun detectMaxFps() {
        // Try dumpsys SurfaceFlinger --latency to get refresh period
        val result = shellExecutor.execute("dumpsys SurfaceFlinger --latency")
        if (result.isSuccess) {
            val firstLine = result.stdout.lines().firstOrNull()?.trim()
            val refreshPeriodNs = firstLine?.toLongOrNull()
            if (refreshPeriodNs != null && refreshPeriodNs > 0) {
                maxFps = (1_000_000_000f / refreshPeriodNs).coerceIn(30f, 240f)
                Log.d(TAG, "Detected maxFps=$maxFps from refresh period=${refreshPeriodNs}ns")
                return
            }
        }
        Log.d(TAG, "Using default maxFps=$maxFps")
    }

    private suspend fun collectFromSource(): Float {
        return when (realtimeSource) {
            RealtimeSource.SYSFS -> readSysfsFps() ?: 0f
            RealtimeSource.SF_1013 -> readSf1013FrameCount()?.let { calculateDeltaFps(it) } ?: 0f
            RealtimeSource.GFXINFO -> readGfxInfoFrameCount()?.let { calculateDeltaFps(it) } ?: 0f
            else -> 0f
        }
    }

    private suspend fun readSysfsFps(): Float? {
        val path = measuredFpsPath ?: return null
        val raw = sysfsReader.readString(path) ?: return null
        return raw.split("\\s+".toRegex()).firstOrNull()?.toFloatOrNull()?.coerceIn(0f, maxFps)
    }

    private suspend fun readSf1013FrameCount(): Long? {
        val result = shellExecutor.execute("service call SurfaceFlinger 1013")
        if (!result.isSuccess) return null
        val output = result.stdout.trim()
        val parenIdx = output.indexOf('(')
        if (parenIdx < 0) return null
        val hexPart = output.substring(parenIdx + 1)
            .replace("'", "").replace(".", "").replace(")", "")
            .trim()
            .split("\\s+".toRegex())
            .getOrNull(1) ?: return null
        return try {
            java.lang.Long.parseLong(hexPart, 16)
        } catch (e: NumberFormatException) {
            Log.d(TAG, "readSf1013FrameCount: hex parse failed: $hexPart", e)
            null
        }
    }

    private suspend fun readGfxInfoFrameCount(): Long? {
        val pkg = getFocusedPackage() ?: return null
        // Package changed — reset delta to avoid cross-app spikes
        if (pkg != deltaLastPackage) {
            deltaLastPackage = pkg
            deltaLastCount = -1
            deltaLastTime = 0
        }
        val result = shellExecutor.execute("dumpsys gfxinfo $pkg")
        if (!result.isSuccess) return null
        val match = TOTAL_FRAMES_REGEX.find(result.stdout) ?: return null
        return match.groupValues[1].toLongOrNull()
    }

    private fun calculateDeltaFps(frames: Long): Float {
        val now = System.currentTimeMillis()
        val prevCount = deltaLastCount
        val prevTime = deltaLastTime
        deltaLastCount = frames
        deltaLastTime = now

        if (prevCount < 0 || prevTime <= 0) return 0f
        val dt = now - prevTime
        if (dt <= 0) return 0f
        val delta = frames - prevCount
        if (delta < 0) return 0f
        return (delta * 1000f / dt).coerceIn(0f, maxFps)
    }

    private fun smoothOutput(rawFps: Float): Float {
        val now = System.currentTimeMillis()
        if (rawFps > 0f) {
            lastReportedFps = rawFps
            lastNonZeroTime = now
            return rawFps
        }
        // Hold last non-zero value briefly to prevent flicker
        return if (lastReportedFps > 0f && now - lastNonZeroTime < FPS_HOLD_MS) {
            lastReportedFps
        } else {
            lastReportedFps = 0f
            0f
        }
    }

    suspend fun getFocusedPackage(): String? = withContext(Dispatchers.IO) {
        val result = shellExecutor.execute("dumpsys window displays")
        if (!result.isSuccess) return@withContext null

        for (line in result.stdout.lines()) {
            if (line.contains("mFocusedApp=")) {
                val match = FOCUSED_APP_REGEX.find(line)
                if (match != null) return@withContext match.groupValues[1]
            }
        }
        null
    }

    suspend fun getFpsFromSurfaceFlinger(windowName: String? = null): FpsData? = withContext(Dispatchers.IO) {
        val cmd = if (windowName != null) {
            "dumpsys SurfaceFlinger --latency '$windowName'"
        } else {
            "dumpsys SurfaceFlinger --latency"
        }
        val result = shellExecutor.execute(cmd)
        if (!result.isSuccess) return@withContext null

        parseSurfaceFlingerLatency(result.stdout, windowName ?: "")
    }

    suspend fun getFpsFromGfxInfo(packageName: String): FpsData? = withContext(Dispatchers.IO) {
        val result = shellExecutor.execute("dumpsys gfxinfo $packageName framestats")
        if (!result.isSuccess) return@withContext null

        parseGfxInfoFrameStats(result.stdout, packageName)
    }

    suspend fun getCurrentWindow(): String? = withContext(Dispatchers.IO) {
        val result = shellExecutor.execute("dumpsys SurfaceFlinger --list")
        if (!result.isSuccess) return@withContext null

        result.stdout.lines()
            .filter { it.contains("/") && !it.contains("StatusBar") && !it.contains("NavigationBar") }
            .firstOrNull { it.contains("SurfaceView") || it.contains("Activity") }
            ?.trim()
    }

    internal fun parseSurfaceFlingerLatency(output: String, window: String): FpsData? {
        val lines = output.lines().filter { it.isNotBlank() }
        if (lines.size < 3) return null

        val refreshPeriodNs = lines[0].trim().toLongOrNull() ?: return null
        if (refreshPeriodNs > 0) {
            maxFps = (1_000_000_000f / refreshPeriodNs).coerceIn(30f, 240f)
        }

        val frameTimes = mutableListOf<Int>()
        var prevTimestamp = -1L
        var maxActualPresent = 0L
        var hasNewFrames = false

        for (i in 1 until lines.size) {
            val parts = lines[i].trim().split("\\s+".toRegex())
            if (parts.size < 3) continue

            val desiredPresent = parts[1].toLongOrNull() ?: continue
            val actualPresent = parts[2].toLongOrNull() ?: continue

            if (actualPresent == Long.MAX_VALUE || actualPresent == 0L) continue

            if (actualPresent > maxActualPresent) maxActualPresent = actualPresent
            if (actualPresent > lastMaxActualPresent) hasNewFrames = true

            if (prevTimestamp > 0) {
                val frameTimeMs = ((actualPresent - prevTimestamp) / 1_000_000).toInt()
                if (frameTimeMs in 1..1000) {
                    frameTimes.add(frameTimeMs)
                }
            }
            prevTimestamp = actualPresent
        }

        if (maxActualPresent > 0) lastMaxActualPresent = maxActualPresent

        // All frames are from previous cycle — screen is idle
        if (!hasNewFrames) return FpsData(fps = 0, window = window)

        if (frameTimes.isEmpty()) return null

        val avgFrameTime = frameTimes.average()
        val fps = if (avgFrameTime > 0) 1000.0 / avgFrameTime else 0.0
        val jankThreshold = avgFrameTime * 2
        val bigJankThreshold = avgFrameTime * 3

        val jankCount = frameTimes.count { it > jankThreshold }
        val bigJankCount = frameTimes.count { it > bigJankThreshold }
        val maxFrameTime = frameTimes.maxOrNull() ?: 0

        return FpsData(
            fps = fps.toInt().coerceAtMost(maxFps.toInt()),
            jankCount = jankCount,
            bigJankCount = bigJankCount,
            maxFrameTimeMs = maxFrameTime,
            frameTimesMs = frameTimes.toIntArray(),
            window = window
        )
    }

    private fun parseGfxInfoFrameStats(output: String, packageName: String): FpsData? {
        val lines = output.lines()
        val frameStatsStart = lines.indexOfFirst { it.contains("---PROFILEDATA---") }
        if (frameStatsStart < 0) return null

        val frameTimes = mutableListOf<Int>()

        for (i in (frameStatsStart + 2) until lines.size) {
            val line = lines[i]
            if (line.contains("---PROFILEDATA---")) break

            val parts = line.split(",")
            if (parts.size < 14) continue

            val flags = parts[0].toLongOrNull() ?: continue
            if (flags != 0L) continue

            val intendedVsync = parts[1].toLongOrNull() ?: continue
            val frameCompleted = parts[13].toLongOrNull() ?: continue

            val frameTimeMs = ((frameCompleted - intendedVsync) / 1_000_000).toInt()
            if (frameTimeMs in 1..1000) {
                frameTimes.add(frameTimeMs)
            }
        }

        if (frameTimes.isEmpty()) return null

        val avgFrameTime = frameTimes.average()
        val fps = if (avgFrameTime > 0) 1000.0 / avgFrameTime else 0.0

        val jankCount = frameTimes.count { it > avgFrameTime * 2 }
        val bigJankCount = frameTimes.count { it > avgFrameTime * 3 }

        return FpsData(
            fps = fps.toInt().coerceAtMost(maxFps.toInt()),
            jankCount = jankCount,
            bigJankCount = bigJankCount,
            maxFrameTimeMs = frameTimes.maxOrNull() ?: 0,
            frameTimesMs = frameTimes.toIntArray(),
            window = packageName
        )
    }

    companion object {
        private const val TAG = "FpsDataSource"
        private val MEASURED_FPS_CANDIDATES = listOf(
            "/sys/class/drm/sde-crtc-0/measured_fps",
            "/sys/class/graphics/fb0/measured_fps",
        )
        private val TOTAL_FRAMES_REGEX = Regex("""Total frames rendered:\s*(\d+)""")
        private val FOCUSED_APP_REGEX = Regex("""\bu\d+\s+([\w.]+)/""")
    }
}
