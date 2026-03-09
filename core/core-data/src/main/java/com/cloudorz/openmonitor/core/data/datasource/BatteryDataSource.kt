package com.cloudorz.openmonitor.core.data.datasource

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.cloudorz.openmonitor.core.common.SysfsReader
import com.cloudorz.openmonitor.core.data.util.MonitorParser
import com.cloudorz.openmonitor.core.model.battery.BatteryChargingStatus
import com.cloudorz.openmonitor.core.model.battery.BatteryStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryDataSource @Inject constructor(
    private val sysfsReader: SysfsReader,
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BatteryDataSource"

        // Comprehensive sysfs paths for battery current (from DevCheck/Scene)
        private val CURRENT_SYSFS_PATHS = arrayOf(
            "/sys/class/power_supply/battery/current_now",
            "/sys/class/power_supply/battery/batt_current_now",
            "/sys/class/power_supply/battery/batt_current_ua_now",
            "/sys/class/power_supply/bms/current_now",
            "/sys/class/power_supply/battery/batt_current",
            "/sys/class/power_supply/battery/batt_chg_current",
            "/sys/class/power_supply/max77843-fuelgauge/current_now",
            "/sys/class/power_supply/max170xx_battery/current_now",
            "/sys/class/power_supply/max77693-fuelgauge/current_now",
            "/sys/class/power_supply/sec-fuelgauge/current_now",
            "/sys/class/power_supply/sec-charger/current_now",
            "/sys/class/power_supply/mtp-fuelgauge/current_now",
            "/sys/devices/platform/htc_battery/power_supply/battery/batt_current_now",
            "/sys/devices/platform/sec-battery/power_supply/battery/current_now",
            "/sys/devices/platform/battery/power_supply/battery/current_now",
            "/sys/devices/platform/ds2784-battery/getcurrent",
        )

        private const val UEVENT_PATH = "/sys/class/power_supply/battery/uevent"
    }
    private val batteryPath = "/sys/class/power_supply/battery"

    // Cached working sysfs path: null=not probed, ""=none found
    @Volatile private var resolvedCurrentPath: String? = null

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

        // current_now: multi-tier fallback (sysfs paths → API → uevent → intent)
        val currentMa = readBatteryCurrent(batteryIntent).toLong()

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
        } catch (e: Exception) {
            Log.d(TAG, "readDesignCapacity via PowerProfile failed", e)
            0.0
        }
    }

    /**
     * Multi-tier battery current reading:
     * 1. Probe sysfs paths (cached after first success)
     * 2. BatteryManager API
     * 3. Parse uevent file
     * 4. Intent broadcast extra
     */
    private suspend fun readBatteryCurrent(batteryIntent: Intent?): Int {
        // Tier 1: Sysfs paths with probing and caching
        val sysfsValue = readCurrentFromSysfs()
        if (sysfsValue != null && sysfsValue != 0) return sysfsValue

        // Tier 2: BatteryManager API
        val apiValue = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (apiValue != Int.MIN_VALUE && apiValue != 0) {
            return MonitorParser.normalizeCurrentToMa(apiValue.toLong())
        }

        // Tier 3: Parse uevent file
        val ueventValue = readCurrentFromUevent()
        if (ueventValue != null && ueventValue != 0) return ueventValue

        // Tier 4: Intent broadcast extra
        if (batteryIntent != null) {
            val intentValue = batteryIntent.getIntExtra("current_now", 0)
            if (intentValue != 0) return MonitorParser.normalizeCurrentToMa(intentValue.toLong())
        }

        return 0
    }

    private suspend fun readCurrentFromSysfs(): Int? {
        val cached = resolvedCurrentPath
        if (cached != null) {
            if (cached.isEmpty()) return null // already probed, none found
            val raw = sysfsReader.readLong(cached) ?: return null
            return MonitorParser.normalizeCurrentToMa(raw)
        }

        // Probe all paths to find the first readable one
        for (path in CURRENT_SYSFS_PATHS) {
            val raw = sysfsReader.readLong(path)
            if (raw != null) {
                resolvedCurrentPath = path
                Log.d(TAG, "Battery current sysfs path resolved: $path")
                return MonitorParser.normalizeCurrentToMa(raw)
            }
        }
        resolvedCurrentPath = "" // mark as probed, none found
        Log.d(TAG, "No readable sysfs path found for battery current")
        return null
    }

    private suspend fun readCurrentFromUevent(): Int? {
        val content = sysfsReader.readString(UEVENT_PATH) ?: return null
        return MonitorParser.parseBatteryCurrentFromUevent(content)
    }

}
