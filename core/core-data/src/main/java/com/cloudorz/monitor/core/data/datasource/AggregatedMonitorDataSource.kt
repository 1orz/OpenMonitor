package com.cloudorz.monitor.core.data.datasource

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.cloudorz.monitor.core.common.PlatformDetector
import com.cloudorz.monitor.core.common.PrivilegeMode
import com.cloudorz.monitor.core.common.ShellExecutor
import com.cloudorz.monitor.core.common.SysfsReader
import com.cloudorz.monitor.core.data.util.MonitorParser
import com.cloudorz.monitor.core.model.monitor.MonitorSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AggregatedMonitorDataSource @Inject constructor(
    private val shellExecutor: ShellExecutor,
    private val sysfsReader: SysfsReader,
    private val platformDetector: PlatformDetector,
    private val daemonDataSource: DaemonDataSource,
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "AggregatedMonitor"
        private const val SEP = "===MONSEP==="
    }

    // CPU cross-cycle delta state
    @Volatile private var prevProcStat: LongArray? = null

    // Cached thermal zone index: -2=unscanned, -1=not found
    @Volatile private var cpuThermalZoneIndex: Int = -2

    // Cached GPU load sysfs path
    @Volatile private var gpuLoadPath: String? = null
    @Volatile private var gpuLoadPathResolved = false

    private val batteryManager: BatteryManager by lazy {
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }

    suspend fun collectSnapshot(): MonitorSnapshot = when (shellExecutor.mode) {
        PrivilegeMode.ROOT,
        PrivilegeMode.ADB,
        PrivilegeMode.SHIZUKU -> collectWithDaemon()
        PrivilegeMode.BASIC   -> collectBasic()
    }

    /**
     * ROOT / ADB / SHIZUKU path:
     *   1. daemon running  → DaemonDataSource (fast, accurate, GPU on root)
     *   2. daemon offline  → collectViaShell() (compound shell commands, existing logic)
     */
    private suspend fun collectWithDaemon(): MonitorSnapshot {
        if (daemonDataSource.isAvailable()) {
            val snap = daemonDataSource.collectSnapshot()
            if (snap != null) {
                val rawUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                val currentMa = if (rawUa != Int.MIN_VALUE) {
                    MonitorParser.normalizeCurrentToMa(rawUa.toLong())
                } else 0
                return snap.copy(batteryCurrentMa = currentMa)
            }
        }
        // Daemon was alive but has now missed 3+ pings (~15 s) — kill any residual process.
        // Best-effort: silently ignored if daemon already exited or user lacks permission.
        if (daemonDataSource.isDead()) {
            shellExecutor.execute("killall monitor-daemon 2>/dev/null || true")
            daemonDataSource.resetDeadState()
            Log.i(TAG, "collectWithDaemon: daemon dead after 3+ failures, killed residual process")
        }
        return collectViaShell()
    }

    private suspend fun collectViaShell(): MonitorSnapshot = withContext(Dispatchers.IO) {
        resolveGpuLoadPath()
        resolveThermalZoneIndex()

        val cmd = buildCompoundCommand()
        val result = shellExecutor.execute(cmd)
        if (!result.isSuccess) return@withContext collectBasic()

        val sections = result.stdout.split(SEP)

        val cpuSection = sections.getOrNull(0)?.trim() ?: ""
        val gpuSection = sections.getOrNull(1)?.trim() ?: ""
        val thermalSection = sections.getOrNull(2)?.trim() ?: ""
        val batterySection = sections.getOrNull(3)?.trim() ?: ""

        val cpuLoad = parseCpuLoad(cpuSection)
        val gpuLoad = parseGpuLoad(gpuSection)
        val temp = parseThermal(thermalSection)
        val currentMa = parseBatteryCurrent(batterySection)

        MonitorSnapshot(
            cpuLoadPercent = cpuLoad,
            gpuLoadPercent = gpuLoad,
            cpuTempCelsius = temp,
            batteryCurrentMa = currentMa,
        )
    }

    private fun buildCompoundCommand(): String = buildString {
        // Section 0: /proc/stat
        append("cat /proc/stat")
        append(";echo '$SEP'")

        // Section 1: GPU load
        val gpu = gpuLoadPath
        if (gpu != null) {
            append(";cat $gpu 2>/dev/null")
        }
        append(";echo '$SEP'")

        // Section 2: CPU thermal zone temp
        val tIdx = cpuThermalZoneIndex
        if (tIdx >= 0) {
            append(";cat /sys/class/thermal/thermal_zone$tIdx/temp 2>/dev/null")
        }
        append(";echo '$SEP'")

        // Section 3: Battery current (uevent is more reliable across devices)
        append(";cat /sys/class/power_supply/battery/uevent 2>/dev/null")
        // Note: SurfaceFlinger latency removed — FPS is collected separately by
        // DaemonDataSource or FpsDataSource. Including it here caused ~1MB Binder
        // transactions (991KB) that risked TransactionTooLargeException and triggered
        // frequent GC pauses.
    }

    // ---- Parsers (delegated to MonitorParser for testability) ----

    private fun parseCpuLoad(procStatOutput: String): Double {
        val (load, newValues) = MonitorParser.parseCpuLoad(procStatOutput, prevProcStat)
        prevProcStat = newValues
        return load
    }

    private fun parseGpuLoad(gpuOutput: String): Double =
        MonitorParser.parseGpuLoad(gpuOutput)

    private fun parseThermal(thermalOutput: String): Double =
        MonitorParser.parseThermal(thermalOutput)

    private fun parseBatteryCurrent(batteryOutput: String): Int {
        // Try uevent format first (multi-line key=value)
        val fromUevent = MonitorParser.parseBatteryCurrentFromUevent(batteryOutput)
        if (fromUevent != null) return fromUevent
        // Fallback: single value (direct current_now output)
        val fromSysfs = MonitorParser.parseBatteryCurrentFromSysfs(batteryOutput)
        if (fromSysfs != null) return fromSysfs
        // Last resort: Android API
        return getBatteryCurrentFromApi()
    }

    // ---- BASIC mode fallback ----

    private suspend fun collectBasic(): MonitorSnapshot = withContext(Dispatchers.IO) {
        val cpuLoad = try {
            parseCpuLoad(File("/proc/stat").readText())
        } catch (e: Exception) {
            Log.d(TAG, "collectBasic: read /proc/stat failed", e)
            0.0
        }

        val gpuLoad = try {
            resolveGpuLoadPath()
            val path = gpuLoadPath
            if (path != null) {
                File(path).readText().replace("%", "").trim().toDoubleOrNull() ?: 0.0
            } else 0.0
        } catch (e: Exception) {
            Log.d(TAG, "collectBasic: read GPU load failed", e)
            0.0
        }

        val temp = getBatteryTempFromApi()
        val currentMa = getBatteryCurrentFromApi()

        MonitorSnapshot(
            cpuLoadPercent = cpuLoad,
            gpuLoadPercent = gpuLoad,
            cpuTempCelsius = temp,
            batteryCurrentMa = currentMa,
            fpsData = null,
        )
    }

    // ---- Lazy resolvers ----

    private fun resolveGpuLoadPath() {
        if (gpuLoadPathResolved) return
        gpuLoadPath = when (platformDetector.gpuType) {
            PlatformDetector.GpuType.ADRENO -> "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"
            PlatformDetector.GpuType.MALI -> "/sys/class/misc/mali0/device/utilization"
            else -> null
        }
        gpuLoadPathResolved = true
    }

    private suspend fun resolveThermalZoneIndex() {
        if (cpuThermalZoneIndex != -2) return
        for (i in 0..30) {
            val type = sysfsReader.readString("/sys/class/thermal/thermal_zone$i/type") ?: break
            if (type.contains("cpu", ignoreCase = true) ||
                type.contains("tsens_tz_sensor", ignoreCase = true) ||
                type.contains("mtktscpu", ignoreCase = true)) {
                cpuThermalZoneIndex = i
                return
            }
        }
        cpuThermalZoneIndex = -1
    }

    // ---- Android API fallbacks ----

    @Suppress("UnspecifiedRegisterReceiverFlag")
    private fun getBatteryCurrentFromApi(): Int {
        // BatteryManager API
        val currentUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (currentUa != Int.MIN_VALUE && currentUa != 0) {
            return MonitorParser.normalizeCurrentToMa(currentUa.toLong())
        }
        // Intent broadcast fallback
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val raw = intent?.getIntExtra("current_now", 0) ?: 0
            if (raw != 0) MonitorParser.normalizeCurrentToMa(raw.toLong()) else 0
        } catch (e: Exception) {
            Log.d(TAG, "getBatteryCurrentFromIntent failed", e)
            0
        }
    }

    @Suppress("UnspecifiedRegisterReceiverFlag")
    private fun getBatteryTempFromApi(): Double {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tempRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            tempRaw / 10.0
        } catch (e: Exception) {
            Log.d(TAG, "getBatteryTempFromApi failed", e)
            0.0
        }
    }
}
