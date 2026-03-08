package com.cloudorz.monitor.core.data.datasource

import android.util.Log
import com.cloudorz.monitor.core.model.fps.FpsData
import com.cloudorz.monitor.core.model.monitor.MonitorSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collects MonitorSnapshot from the running monitor-daemon binary.
 * Daemon must be started externally (via root su / ADB / Shizuku).
 *
 * Availability is checked lazily: first call probes via ping, then cached for 5s.
 */
@Singleton
class DaemonDataSource @Inject constructor(
    private val client: DaemonClient,
) {
    companion object {
        private const val TAG = "DaemonDataSource"
        private const val AVAILABILITY_CACHE_MS = 5_000L
    }

    @Volatile private var lastAliveCheck = 0L
    @Volatile private var cachedAlive = false
    @Volatile private var everAlive = false
    @Volatile private var consecutiveFailures = 0

    /** The runner identity reported by the daemon ("root" | "shell" | ""). Updated on first snapshot. */
    @Volatile var daemonRunner: String = ""
        private set

    /** Returns true if daemon is reachable (result cached 5s to avoid repeated probes). */
    fun isAvailable(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastAliveCheck < AVAILABILITY_CACHE_MS) return cachedAlive
        val alive = client.isAlive()
        val wasAlive = cachedAlive
        if (alive) {
            if (!wasAlive) Log.i(TAG, "daemon connected (runner=${daemonRunner.ifEmpty { "unknown" }})")
            everAlive = true
            consecutiveFailures = 0
        } else {
            if (wasAlive) Log.w(TAG, "daemon disconnected")
            if (everAlive) consecutiveFailures++
        }
        cachedAlive = alive
        lastAliveCheck = now
        return cachedAlive
    }

    /**
     * True when daemon was previously alive but has now failed 3+ consecutive ping checks (~15 s).
     * Indicates a crashed / zombie daemon that should be cleaned up.
     */
    fun isDead(): Boolean = everAlive && consecutiveFailures >= 3

    /** Invalidates the alive cache (call when a command fails). */
    fun invalidate() {
        lastAliveCheck = 0L
        cachedAlive = false
    }

    /** Resets all state after killing the daemon process, allowing a fresh reconnect attempt. */
    fun resetDeadState() {
        consecutiveFailures = 0
        everAlive = false
        lastAliveCheck = 0L
        cachedAlive = false
    }

    /**
     * Fetches a full system snapshot from the daemon.
     * Returns null if daemon is unavailable or the response cannot be parsed.
     * batteryCurrentMa is NOT provided by daemon — caller should fill it separately.
     */
    suspend fun collectSnapshot(): MonitorSnapshot? = withContext(Dispatchers.IO) {
        val raw = client.sendCommand("monitor")
        if (raw == null) {
            invalidate()
            return@withContext null
        }
        parseSnapshot(raw)
    }

    // ---- parsers ----

    private fun parseSnapshot(json: String): MonitorSnapshot? {
        return try {
            val obj = JSONObject(json)
            if (obj.has("error")) {
                Log.w(TAG, "daemon error: ${obj.optString("error")}")
                return null
            }

            val cpuLoadsArr = obj.optJSONArray("cpu_load")
            val cpuCoreLoads = if (cpuLoadsArr != null && cpuLoadsArr.length() > 0) {
                (0 until cpuLoadsArr.length()).map { cpuLoadsArr.getDouble(it) }
            } else emptyList()
            val cpuLoad = if (cpuCoreLoads.isNotEmpty()) {
                cpuCoreLoads.average()
            } else 0.0

            val fps = obj.optDouble("fps", 0.0)
            val fpsData = if (fps > 0.0) {
                FpsData(
                    fps = fps,
                    jankCount = obj.optInt("jank", 0),
                    bigJankCount = obj.optInt("big_jank", 0),
                    window = obj.optString("fps_layer", ""),
                )
            } else null

            val runner = obj.optString("runner", "")
            if (runner.isNotEmpty() && daemonRunner != runner) {
                daemonRunner = runner
                Log.i(TAG, "daemon runner: $runner")
            }

            val temp = obj.optDouble("cpu_temp", 0.0)
            if (cpuLoad == 0.0 && temp == 0.0 && cpuCoreLoads.isEmpty()) {
                Log.e(TAG, "parseSnapshot: all-zero from daemon, json=${json.take(200)}")
            }

            MonitorSnapshot(
                cpuLoadPercent = cpuLoad,
                cpuCoreLoads = cpuCoreLoads,
                gpuLoadPercent = obj.optDouble("gpu_load", 0.0),
                gpuFreqMhz = obj.optInt("gpu_freq", 0),
                cpuTempCelsius = temp,
                fpsData = fpsData,
                daemonRunner = runner,
                // batteryCurrentMa intentionally left 0 — filled by caller from BatteryManager
            )
        } catch (e: Exception) {
            Log.w(TAG, "parseSnapshot failed: ${e.message}")
            null
        }
    }


}
