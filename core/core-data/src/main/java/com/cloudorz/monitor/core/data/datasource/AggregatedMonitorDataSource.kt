package com.cloudorz.monitor.core.data.datasource

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.cloudorz.monitor.core.common.PlatformDetector
import com.cloudorz.monitor.core.common.PrivilegeMode
import com.cloudorz.monitor.core.common.ShellExecutor
import com.cloudorz.monitor.core.common.SysfsReader
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
    private val fpsDataSource: FpsDataSource,
    @ApplicationContext private val context: Context,
) {
    companion object {
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

    suspend fun collectSnapshot(): MonitorSnapshot {
        val hasShell = shellExecutor.mode != PrivilegeMode.BASIC
        return if (hasShell) collectViaShell() else collectBasic()
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
        val sfSection = sections.getOrNull(4)?.trim() ?: ""

        val cpuLoad = parseCpuLoad(cpuSection)
        val gpuLoad = parseGpuLoad(gpuSection)
        val temp = parseThermal(thermalSection)
        val currentMa = parseBatteryCurrent(batterySection)
        val fpsData = if (sfSection.isNotEmpty()) {
            fpsDataSource.parseSurfaceFlingerLatency(sfSection, "")
        } else null

        MonitorSnapshot(
            cpuLoadPercent = cpuLoad,
            gpuLoadPercent = gpuLoad,
            cpuTempCelsius = temp,
            batteryCurrentMa = currentMa,
            fpsData = fpsData,
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

        // Section 3: Battery current
        append(";cat /sys/class/power_supply/battery/current_now 2>/dev/null")
        append(";echo '$SEP'")

        // Section 4: SurfaceFlinger latency
        append(";dumpsys SurfaceFlinger --latency")
    }

    // ---- Parsers ----

    private fun parseCpuLoad(procStatOutput: String): Double {
        if (procStatOutput.isEmpty()) return 0.0

        val cpuLine = procStatOutput.lines().firstOrNull { it.startsWith("cpu ") } ?: return 0.0
        val values = cpuLine.trim().split("\\s+".toRegex())
            .drop(1)
            .map { it.toLongOrNull() ?: 0L }
            .toLongArray()

        if (values.size < 5) return 0.0

        val prev = prevProcStat
        prevProcStat = values

        if (prev == null || prev.size < 5) return 0.0

        val idle = values[3] + values[4]
        val prevIdle = prev[3] + prev[4]
        val total = values.sum()
        val prevTotal = prev.sum()

        val totalDiff = (total - prevTotal).toDouble()
        val idleDiff = (idle - prevIdle).toDouble()

        return if (totalDiff > 0) {
            ((totalDiff - idleDiff) / totalDiff * 100.0).coerceIn(0.0, 100.0)
        } else 0.0
    }

    private fun parseGpuLoad(gpuOutput: String): Double {
        if (gpuOutput.isEmpty()) return 0.0
        return gpuOutput.replace("%", "").trim().toDoubleOrNull() ?: 0.0
    }

    private fun parseThermal(thermalOutput: String): Double {
        if (thermalOutput.isEmpty()) return 0.0
        val raw = thermalOutput.trim().toIntOrNull() ?: return 0.0
        return if (raw > 1000) raw / 1000.0 else raw.toDouble()
    }

    private fun parseBatteryCurrent(batteryOutput: String): Int {
        if (batteryOutput.isEmpty()) return getBatteryCurrentFromApi()
        val raw = batteryOutput.trim().toLongOrNull() ?: return getBatteryCurrentFromApi()
        return (raw / 1000).toInt()
    }

    // ---- BASIC mode fallback ----

    private suspend fun collectBasic(): MonitorSnapshot = withContext(Dispatchers.IO) {
        val cpuLoad = try {
            parseCpuLoad(File("/proc/stat").readText())
        } catch (_: Exception) { 0.0 }

        val gpuLoad = try {
            resolveGpuLoadPath()
            val path = gpuLoadPath
            if (path != null) {
                File(path).readText().replace("%", "").trim().toDoubleOrNull() ?: 0.0
            } else 0.0
        } catch (_: Exception) { 0.0 }

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

    private fun getBatteryCurrentFromApi(): Int {
        val currentUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        return if (currentUa != Int.MIN_VALUE) currentUa / 1000 else 0
    }

    @Suppress("UnspecifiedRegisterReceiverFlag")
    private fun getBatteryTempFromApi(): Double {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tempRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            tempRaw / 10.0
        } catch (_: Exception) { 0.0 }
    }
}
