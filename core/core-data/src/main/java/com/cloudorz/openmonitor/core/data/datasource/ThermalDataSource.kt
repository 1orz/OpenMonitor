package com.cloudorz.openmonitor.core.data.datasource

import com.cloudorz.openmonitor.core.common.SysfsReader
import com.cloudorz.openmonitor.core.model.thermal.ThermalZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThermalDataSource @Inject constructor(
    private val sysfsReader: SysfsReader
) {
    private val thermalBasePath = "/sys/class/thermal"

    /**
     * Cached CPU temperature path discovered from [CPU_TEMP_FALLBACK_PATHS].
     * null = not yet probed, "" = probed but none found.
     */
    private val cachedCpuTempPath = AtomicReference<String?>(null)

    suspend fun getAllThermalZones(): List<ThermalZone> = withContext(Dispatchers.IO) {
        val zones = mutableListOf<ThermalZone>()
        for (i in 0..50) {
            val zonePath = "$thermalBasePath/thermal_zone$i"
            val type = sysfsReader.readString("$zonePath/type") ?: break
            val tempRaw = sysfsReader.readInt("$zonePath/temp") ?: continue
            val temperature = if (tempRaw > 1000) tempRaw / 1000.0 else tempRaw.toDouble()
            zones.add(
                ThermalZone(
                    index = i,
                    name = "thermal_zone$i",
                    type = type,
                    temperatureCelsius = temperature
                )
            )
        }
        zones
    }

    suspend fun getCpuTemperature(): Double {
        // Priority 1: thermal_zone type matching
        val zones = getAllThermalZones()
        val fromZone = zones.firstOrNull { zone ->
            zone.type.contains("cpu", ignoreCase = true) ||
                zone.type.contains("tsens_tz_sensor", ignoreCase = true) ||
                zone.type.contains("mtktscpu", ignoreCase = true)
        }?.temperatureCelsius
        if (fromZone != null && fromZone > 0) return fromZone

        // Priority 2: hardcoded fallback paths
        return readCpuTempFromFallbackPaths()
    }

    suspend fun getGpuTemperature(): Double {
        val zones = getAllThermalZones()
        return zones.firstOrNull { zone ->
            zone.type.contains("gpu", ignoreCase = true) ||
                zone.type.contains("tsens_tz_sensor10", ignoreCase = true)
        }?.temperatureCelsius ?: -1.0
    }

    suspend fun getBatteryTemperature(): Double {
        val zones = getAllThermalZones()
        return zones.firstOrNull { zone ->
            zone.type.contains("battery", ignoreCase = true) ||
                zone.type.contains("pm8998_tz", ignoreCase = true)
        }?.temperatureCelsius ?: -1.0
    }

    /**
     * Reads CPU temperature from the cached fallback path.
     * On first call (or after a cached path becomes stale), probes all
     * [CPU_TEMP_FALLBACK_PATHS] to find the first one returning a valid reading.
     */
    private suspend fun readCpuTempFromFallbackPaths(): Double {
        val path = cachedCpuTempPath.get()
        if (path != null && path.isNotEmpty()) {
            val temp = readTempFromPath(path)
            if (temp != null) return temp
            // Cached path went stale — re-probe
            cachedCpuTempPath.set(null)
        }
        if (path == PROBED_NONE) return -1.0

        return probeCpuTempPaths()
    }

    /**
     * Probes all [CPU_TEMP_FALLBACK_PATHS] and caches the first valid one.
     */
    private suspend fun probeCpuTempPaths(): Double {
        for (candidate in CPU_TEMP_FALLBACK_PATHS) {
            val temp = readTempFromPath(candidate)
            if (temp != null) {
                cachedCpuTempPath.set(candidate)
                return temp
            }
        }
        cachedCpuTempPath.set(PROBED_NONE)
        return -1.0
    }

    /**
     * Reads a raw temperature value from [path] and normalises to Celsius.
     * Returns null if unreadable or out of valid range (0..200).
     */
    private suspend fun readTempFromPath(path: String): Double? {
        val raw = sysfsReader.readInt(path) ?: return null
        val celsius = if (raw > 1000) raw / 1000.0 else raw.toDouble()
        return if (celsius > 0 && celsius < 200) celsius else null
    }

    companion object {
        /** Sentinel value: fallback paths have been probed and none worked. */
        private const val PROBED_NONE = ""

        /**
         * Well-known CPU temperature file paths across various SoC vendors.
         * Sourced from CPU Info (github-src-cpu-info) TemperatureProvider.
         *
         * Ordered roughly by popularity so the common cases hit early.
         */
        private val CPU_TEMP_FALLBACK_PATHS = listOf(
            "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp",
            "/sys/devices/system/cpu/cpu0/cpufreq/FakeShmoo_cpu_temp",
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/i2c-adapter/i2c-4/4-004c/temperature",
            "/sys/devices/platform/tegra-i2c.3/i2c-4/4-004c/temperature",
            "/sys/devices/platform/omap/omap_temp_sensor.0/temperature",
            "/sys/devices/platform/tegra_tmon/temp1_input",
            "/sys/kernel/debug/tegra_thermal/temp_tj",
            "/sys/devices/platform/s5p-tmu/temperature",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/class/hwmon/hwmon0/device/temp1_input",
            "/sys/devices/virtual/thermal/thermal_zone1/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone3/temp",
            "/sys/class/thermal/thermal_zone4/temp",
            "/sys/class/hwmon/hwmon0/temp1_input",
            "/sys/class/hwmon/hwmon1/temp1_input",
            "/sys/devices/platform/s5p-tmu/curr_temp",
            "/sys/htc/cpu_temp",
            "/sys/devices/platform/tegra-i2c.3/i2c-4/4-004c/ext_temperature",
            "/sys/devices/platform/tegra-tsensor/tsensor_temperature",
            "/sys/module/msm_thermal/parameters/cpu_temp",
            "/sys/devices/platform/coretemp.0/temp2_input",
        )
    }
}
