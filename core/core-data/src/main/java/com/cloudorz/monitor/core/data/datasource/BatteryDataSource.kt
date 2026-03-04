package com.cloudorz.monitor.core.data.datasource

import com.cloudorz.monitor.core.common.SysfsReader
import com.cloudorz.monitor.core.model.battery.BatteryChargingStatus
import com.cloudorz.monitor.core.model.battery.BatteryStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryDataSource @Inject constructor(
    private val sysfsReader: SysfsReader
) {
    private val batteryPath = "/sys/class/power_supply/battery"
    private val bmsPath = "/sys/class/power_supply/bms"

    suspend fun getBatteryStatus(): BatteryStatus = withContext(Dispatchers.IO) {
        val capacity = sysfsReader.readInt("$batteryPath/capacity") ?: -1
        val tempRaw = sysfsReader.readInt("$batteryPath/temp") ?: 0
        val temperature = tempRaw / 10.0
        val currentNow = sysfsReader.readLong("$batteryPath/current_now") ?: run {
            sysfsReader.readLong("$bmsPath/current_now") ?: 0L
        }
        // current_now is in uA, convert to mA
        val currentMa = currentNow / 1000

        val voltageRaw = sysfsReader.readLong("$batteryPath/voltage_now") ?: 0L
        val voltageV = voltageRaw / 1000000.0

        val statusStr = sysfsReader.readString("$batteryPath/status") ?: "Unknown"
        val status = when (statusStr.lowercase()) {
            "charging" -> BatteryChargingStatus.CHARGING
            "discharging" -> BatteryChargingStatus.DISCHARGING
            "full" -> BatteryChargingStatus.FULL
            "not charging" -> BatteryChargingStatus.NOT_CHARGING
            else -> BatteryChargingStatus.UNKNOWN
        }

        val technology = sysfsReader.readString("$batteryPath/technology") ?: ""
        val health = sysfsReader.readString("$batteryPath/health") ?: ""
        val chargeType = sysfsReader.readString("$batteryPath/charge_type") ?: ""

        val powerW = Math.abs(currentMa / 1000.0 * voltageV)

        BatteryStatus(
            capacity = capacity,
            temperatureCelsius = temperature,
            status = status,
            currentMa = currentMa.toInt(),
            voltageV = voltageV,
            technology = technology,
            chargerType = chargeType,
            statusText = statusStr,
            chargerPower = powerW,
            timestamp = System.currentTimeMillis()
        )
    }

    suspend fun setChargingEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        // Try multiple known paths
        val paths = listOf(
            "$batteryPath/battery_charging_enabled" to if (enabled) "1" else "0",
            "$batteryPath/input_suspend" to if (enabled) "0" else "1",
            "$batteryPath/charging_enabled" to if (enabled) "1" else "0"
        )
        for ((path, value) in paths) {
            if (sysfsReader.writeValue(path, value)) return@withContext true
        }
        false
    }

    suspend fun setChargeCurrentLimit(limitMa: Int): Boolean = withContext(Dispatchers.IO) {
        val limitUa = limitMa * 1000
        sysfsReader.writeValue("$batteryPath/constant_charge_current_max", limitUa.toString())
    }

    suspend fun getNightChargingEnabled(): Boolean? = withContext(Dispatchers.IO) {
        sysfsReader.readInt("$batteryPath/night_charging")?.let { it == 1 }
    }

    suspend fun setNightCharging(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        sysfsReader.writeValue("$batteryPath/night_charging", if (enabled) "1" else "0")
    }
}
