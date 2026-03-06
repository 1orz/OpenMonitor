package com.cloudorz.monitor.core.data.datasource

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.cloudorz.monitor.core.common.SysfsReader
import com.cloudorz.monitor.core.model.battery.BatteryChargingStatus
import com.cloudorz.monitor.core.model.battery.BatteryStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryDataSource @Inject constructor(
    private val sysfsReader: SysfsReader,
    @ApplicationContext private val context: Context
) {
    private val batteryPath = "/sys/class/power_supply/battery"

    private val capacityMah: Double by lazy { readDesignCapacity() }
    private val batteryManager: BatteryManager by lazy {
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }

    suspend fun getBatteryStatus(): BatteryStatus = withContext(Dispatchers.IO) {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val level: Int
        val temperature: Double
        val voltageV: Double
        val status: BatteryChargingStatus
        val statusStr: String
        val technology: String
        val health: String

        if (batteryIntent != null) {
            val rawLevel = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            level = if (rawLevel >= 0 && scale > 0) (rawLevel * 100) / scale else -1

            val tempRaw = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            temperature = tempRaw / 10.0

            val voltageRaw = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            voltageV = if (voltageRaw > 1000) voltageRaw / 1000.0 else voltageRaw.toDouble()

            val statusInt = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            status = when (statusInt) {
                BatteryManager.BATTERY_STATUS_CHARGING -> BatteryChargingStatus.CHARGING
                BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryChargingStatus.DISCHARGING
                BatteryManager.BATTERY_STATUS_FULL -> BatteryChargingStatus.FULL
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryChargingStatus.NOT_CHARGING
                else -> BatteryChargingStatus.UNKNOWN
            }
            statusStr = when (status) {
                BatteryChargingStatus.CHARGING -> "Charging"
                BatteryChargingStatus.DISCHARGING -> "Discharging"
                BatteryChargingStatus.FULL -> "Full"
                BatteryChargingStatus.NOT_CHARGING -> "Not charging"
                BatteryChargingStatus.UNKNOWN -> "Unknown"
            }

            technology = batteryIntent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: ""

            val healthInt = batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
            health = when (healthInt) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified failure"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                else -> "Unknown"
            }
        } else {
            level = -1
            temperature = 0.0
            voltageV = 0.0
            status = BatteryChargingStatus.UNKNOWN
            statusStr = "Unavailable"
            technology = ""
            health = "Unavailable"
        }

        // current_now: prefer BatteryManager API (no root needed, API 21+)
        val currentUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = if (currentUa != Int.MIN_VALUE) {
            (currentUa / 1000).toLong()
        } else {
            // API failed, try sysfs (may need root)
            val raw = sysfsReader.readLong("$batteryPath/current_now") ?: 0L
            raw / 1000
        }

        val chargeType = sysfsReader.readString("$batteryPath/charge_type") ?: ""
        val powerW = Math.abs(currentMa / 1000.0 * voltageV)

        BatteryStatus(
            capacity = level,
            temperatureCelsius = temperature,
            status = status,
            currentMa = currentMa.toInt(),
            voltageV = voltageV,
            technology = technology,
            health = health,
            chargerType = chargeType,
            statusText = statusStr,
            chargerPower = powerW,
            capacityMah = capacityMah,
            timestamp = System.currentTimeMillis()
        )
    }

    @Suppress("PrivateApi")
    private fun readDesignCapacity(): Double {
        return try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val constructor = powerProfileClass.getConstructor(Context::class.java)
            val powerProfile = constructor.newInstance(context)
            val method = powerProfileClass.getMethod("getBatteryCapacity")
            (method.invoke(powerProfile) as? Double) ?: 0.0
        } catch (_: Exception) {
            0.0
        }
    }

    suspend fun setChargingEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
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
