package com.cloudorz.monitor.core.data.datasource

import com.cloudorz.monitor.core.common.ShellExecutor
import com.cloudorz.monitor.core.model.fps.FpsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FpsDataSource @Inject constructor(
    private val shellExecutor: ShellExecutor
) {
    /**
     * Get FPS data using SurfaceFlinger latency.
     * Parses output of: dumpsys SurfaceFlinger --latency <window>
     */
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

    /**
     * Get FPS data using gfxinfo framestats.
     * Parses output of: dumpsys gfxinfo <package> framestats
     */
    suspend fun getFpsFromGfxInfo(packageName: String): FpsData? = withContext(Dispatchers.IO) {
        val result = shellExecutor.execute("dumpsys gfxinfo $packageName framestats")
        if (!result.isSuccess) return@withContext null

        parseGfxInfoFrameStats(result.stdout, packageName)
    }

    /**
     * Get the current top window name from SurfaceFlinger.
     */
    suspend fun getCurrentWindow(): String? = withContext(Dispatchers.IO) {
        val result = shellExecutor.execute("dumpsys SurfaceFlinger --list")
        if (!result.isSuccess) return@withContext null

        result.stdout.lines()
            .filter { it.contains("/") && !it.contains("StatusBar") && !it.contains("NavigationBar") }
            .firstOrNull { it.contains("SurfaceView") || it.contains("Activity") }
            ?.trim()
    }

    private fun parseSurfaceFlingerLatency(output: String, window: String): FpsData? {
        val lines = output.lines().filter { it.isNotBlank() }
        if (lines.size < 3) return null

        // First line is the refresh period in nanoseconds
        val refreshPeriodNs = lines[0].trim().toLongOrNull() ?: return null

        val frameTimes = mutableListOf<Int>()
        var prevTimestamp = -1L

        for (i in 1 until lines.size) {
            val parts = lines[i].trim().split("\\s+".toRegex())
            if (parts.size < 3) continue

            val desiredPresent = parts[1].toLongOrNull() ?: continue
            val actualPresent = parts[2].toLongOrNull() ?: continue

            if (actualPresent == Long.MAX_VALUE || actualPresent == 0L) continue

            if (prevTimestamp > 0) {
                val frameTimeMs = ((actualPresent - prevTimestamp) / 1_000_000).toInt()
                if (frameTimeMs in 1..1000) {
                    frameTimes.add(frameTimeMs)
                }
            }
            prevTimestamp = actualPresent
        }

        if (frameTimes.isEmpty()) return null

        val avgFrameTime = frameTimes.average()
        val fps = if (avgFrameTime > 0) 1000.0 / avgFrameTime else 0.0
        val jankThreshold = avgFrameTime * 2
        val bigJankThreshold = avgFrameTime * 3

        val jankCount = frameTimes.count { it > jankThreshold }
        val bigJankCount = frameTimes.count { it > bigJankThreshold }
        val maxFrameTime = frameTimes.maxOrNull() ?: 0

        return FpsData(
            fps = fps.toInt(),
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
            if (flags != 0L) continue // Skip non-zero flag frames

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
            fps = fps.toInt(),
            jankCount = jankCount,
            bigJankCount = bigJankCount,
            maxFrameTimeMs = frameTimes.maxOrNull() ?: 0,
            frameTimesMs = frameTimes.toIntArray(),
            window = packageName
        )
    }
}
