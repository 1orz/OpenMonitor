package com.cloudorz.monitor.core.data.datasource

import com.cloudorz.monitor.core.common.SysfsReader
import com.cloudorz.monitor.core.model.thermal.ThermalZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThermalDataSource @Inject constructor(
    private val sysfsReader: SysfsReader
) {
    private val thermalBasePath = "/sys/class/thermal"

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
        val zones = getAllThermalZones()
        return zones.firstOrNull { zone ->
            zone.type.contains("cpu", ignoreCase = true) ||
                zone.type.contains("tsens_tz_sensor", ignoreCase = true) ||
                zone.type.contains("mtktscpu", ignoreCase = true)
        }?.temperatureCelsius ?: -1.0
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
}
