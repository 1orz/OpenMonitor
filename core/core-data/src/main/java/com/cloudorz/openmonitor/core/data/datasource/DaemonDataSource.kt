package com.cloudorz.openmonitor.core.data.datasource

import com.elvishew.xlog.XLog
import com.cloudorz.openmonitor.core.model.fps.FpsData
import com.cloudorz.openmonitor.core.model.monitor.MonitorSnapshot
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

    /** Set by DaemonManager during mode switch/restart to prevent killall race condition. */
    @Volatile var suppressKillall = false

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
            if (!wasAlive) XLog.tag(TAG).e("daemon connected (runner=${daemonRunner.ifEmpty { "unknown" }})")
            everAlive = true
            consecutiveFailures = 0
        } else {
            if (wasAlive) XLog.tag(TAG).e("daemon disconnected")
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
                XLog.tag(TAG).e("daemon error: ${obj.optString("error")}")
                return null
            }

            val cpuLoadsArr = if (!obj.isNull("cpu_load")) obj.optJSONArray("cpu_load") else null
            val cpuCoreLoads = if (cpuLoadsArr != null && cpuLoadsArr.length() > 0) {
                (0 until cpuLoadsArr.length()).map { cpuLoadsArr.getDouble(it) }
            } else null
            val cpuLoad = cpuCoreLoads?.average()

            val cpuFreqArr = if (!obj.isNull("cpu_freq")) obj.optJSONArray("cpu_freq") else null
            val cpuCoreFreqs = if (cpuFreqArr != null && cpuFreqArr.length() > 0) {
                (0 until cpuFreqArr.length()).map { cpuFreqArr.getInt(it) }
            } else null

            // FPS: null in JSON → null FpsData; 0.0 → valid FpsData(fps=0)
            val fpsData = if (!obj.isNull("fps")) {
                val fps = obj.getDouble("fps")
                FpsData(
                    fps = fps,
                    jankCount = if (!obj.isNull("jank")) obj.getInt("jank") else 0,
                    bigJankCount = if (!obj.isNull("big_jank")) obj.getInt("big_jank") else 0,
                    window = obj.optString("fps_layer", ""),
                )
            } else null

            val runner = obj.optString("runner", "")
            if (runner.isNotEmpty() && daemonRunner != runner) {
                daemonRunner = runner
                XLog.tag(TAG).e("daemon runner: $runner")
            }

            val temp = if (!obj.isNull("cpu_temp")) obj.getDouble("cpu_temp") else null
            val gpuLoad = if (!obj.isNull("gpu_load")) obj.getDouble("gpu_load") else null
            val gpuFreq = if (!obj.isNull("gpu_freq")) obj.getInt("gpu_freq") else null

            var batteryCurrentMa: Int? = null
            var batteryCurrentUa: Int? = null
            var batteryVoltageUv: Int? = null
            var batteryPowerMw: Int? = null
            var batteryCycleCount: Int? = null
            var batteryChargeFullUah: Int? = null
            var batteryChargeFullDesignUah: Int? = null
            var batteryChargeCounterUah: Int? = null
            var batteryHealth: String? = null
            if (!obj.isNull("battery")) {
                val bat = obj.getJSONObject("battery")
                batteryCurrentMa = if (!bat.isNull("current_ma")) bat.getInt("current_ma") else null
                batteryCurrentUa = if (!bat.isNull("current_ua")) bat.getInt("current_ua") else null
                batteryVoltageUv = if (!bat.isNull("voltage_uv")) bat.getInt("voltage_uv") else null
                batteryPowerMw = if (!bat.isNull("power_mw")) bat.getInt("power_mw") else null
                batteryCycleCount = if (!bat.isNull("cycle_count")) bat.getInt("cycle_count") else null
                batteryChargeFullUah = if (!bat.isNull("charge_full_uah")) bat.getInt("charge_full_uah") else null
                batteryChargeFullDesignUah = if (!bat.isNull("charge_full_design_uah")) bat.getInt("charge_full_design_uah") else null
                batteryChargeCounterUah = if (!bat.isNull("charge_counter_uah")) bat.getInt("charge_counter_uah") else null
                batteryHealth = if (!bat.isNull("health")) bat.getString("health") else null
            }

            MonitorSnapshot(
                cpuLoadPercent = cpuLoad,
                cpuCoreLoads = cpuCoreLoads,
                cpuCoreFreqs = cpuCoreFreqs,
                gpuLoadPercent = gpuLoad,
                gpuFreqMhz = gpuFreq,
                cpuTempCelsius = temp,
                batteryCurrentMa = batteryCurrentMa,
                batteryCurrentUa = batteryCurrentUa,
                batteryVoltageUv = batteryVoltageUv,
                batteryPowerMw = batteryPowerMw,
                batteryCycleCount = batteryCycleCount,
                batteryChargeFullUah = batteryChargeFullUah,
                batteryChargeFullDesignUah = batteryChargeFullDesignUah,
                batteryChargeCounterUah = batteryChargeCounterUah,
                batteryHealth = batteryHealth,
                fpsData = fpsData,
                daemonRunner = runner,
            )
        } catch (e: Exception) {
            XLog.tag(TAG).e("parseSnapshot failed: ${e.message}")
            null
        }
    }


}
