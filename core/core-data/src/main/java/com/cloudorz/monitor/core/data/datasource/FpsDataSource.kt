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
    // Separate delta trackers per data source to avoid cross-contamination
    private var sfLastFrameCount = -1L
    private var sfLastFrameTime = -1L

    private var gfxLastFrameCount = -1L
    private var gfxLastFrameTime = -1L
    private var gfxLastPackage: String? = null

    @Volatile private var measuredFpsPath: String? = null
    @Volatile private var sf1013Available = true

    // Screen refresh rate cap (will be auto-detected, fallback 165Hz)
    @Volatile private var maxFps = 165f

    // Frame dedup: track last max actualPresent timestamp across calls
    @Volatile private var lastMaxActualPresent: Long = 0

    suspend fun getRealtimeFps(): Float = withContext(Dispatchers.IO) {
        val sysfsFps = tryMeasuredFps()
        if (sysfsFps != null) return@withContext sysfsFps

        if (sf1013Available) {
            val sfFps = getFrameCountDeltaFps()
            if (sfFps != null) return@withContext sfFps
        }

        getGfxInfoDeltaFps() ?: 0f
    }

    private suspend fun tryMeasuredFps(): Float? {
        var path = measuredFpsPath
        if (path == "") return null

        if (path == null) {
            path = MEASURED_FPS_CANDIDATES.firstOrNull { candidate ->
                sysfsReader.readString(candidate) != null
            } ?: ""
            measuredFpsPath = path
            if (path.isEmpty()) return null
        }

        val raw = sysfsReader.readString(path) ?: return null
        return raw.split("\\s+".toRegex()).firstOrNull()?.toFloatOrNull()
            ?.coerceIn(0f, maxFps)
    }

    private suspend fun getFrameCountDeltaFps(): Float? {
        val result = shellExecutor.execute("service call SurfaceFlinger 1013")
        if (!result.isSuccess) return null

        val output = result.stdout.trim()
        if (output.contains("Error") || output.contains("not permitted")) {
            sf1013Available = false
            return null
        }

        val parenIdx = output.indexOf('(')
        if (parenIdx < 0) return null

        val hexPart = output.substring(parenIdx + 1)
            .replace("'", "").replace(".", "").replace(")", "")
            .trim()
            .split("\\s+".toRegex())
            .getOrNull(1) ?: return null

        val frames = try {
            java.lang.Long.parseLong(hexPart, 16)
        } catch (e: NumberFormatException) {
            Log.d(TAG, "getFrameCountDeltaFps: hex parse failed: $hexPart", e)
            return null
        }

        return calculateDelta(frames, DeltaSource.SF)
    }

    private suspend fun getGfxInfoDeltaFps(): Float? {
        val pkg = getFocusedPackage() ?: return null
        val result = shellExecutor.execute("dumpsys gfxinfo $pkg")
        if (!result.isSuccess) return null

        val match = TOTAL_FRAMES_REGEX.find(result.stdout) ?: return null
        val frames = match.groupValues[1].toLongOrNull() ?: return null

        // Package changed — reset gfxinfo delta to avoid cross-app spikes
        if (pkg != gfxLastPackage) {
            gfxLastPackage = pkg
            gfxLastFrameCount = frames
            gfxLastFrameTime = System.currentTimeMillis()
            return 0f
        }

        return calculateDelta(frames, DeltaSource.GFXINFO)
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

    private fun calculateDelta(frames: Long, source: DeltaSource): Float {
        val now = System.currentTimeMillis()

        val lastCount: Long
        val lastTime: Long
        when (source) {
            DeltaSource.SF -> {
                lastCount = sfLastFrameCount
                lastTime = sfLastFrameTime
                sfLastFrameCount = frames
                sfLastFrameTime = now
            }
            DeltaSource.GFXINFO -> {
                lastCount = gfxLastFrameCount
                lastTime = gfxLastFrameTime
                gfxLastFrameCount = frames
                gfxLastFrameTime = now
            }
        }

        if (lastCount < 0 || lastTime <= 0) return 0f

        val dt = now - lastTime
        if (dt <= 0) return 0f

        val delta = frames - lastCount
        // Frame counter went backwards (app restart / counter reset) — skip
        if (delta < 0) return 0f

        val fps = delta * 1000f / dt
        return fps.coerceIn(0f, maxFps)
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

    private enum class DeltaSource { SF, GFXINFO }

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
