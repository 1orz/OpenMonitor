package com.cloudorz.monitor.core.model.battery

enum class BatteryChargingStatus(val displayName: String) {
    CHARGING("Charging"),
    DISCHARGING("Discharging"),
    FULL("Full"),
    NOT_CHARGING("Not Charging"),
    UNKNOWN("Unknown"),
}

data class BatteryStatus(
    val capacity: Int = 0,
    val temperatureCelsius: Double = 0.0,
    val status: BatteryChargingStatus = BatteryChargingStatus.UNKNOWN,
    val currentMa: Int = 0,
    val voltageV: Double = 0.0,
    val healthPercent: Int = 100,
    val technology: String = "",
    val chargerType: String = "",
    val packageName: String = "",
    val screenOn: Boolean = false,
    val mode: String = "",
    val statusText: String = "",
    val chargerPower: Double = 0.0,
    val timestamp: Long = 0,
    val capacityMah: Double = 0.0,
    val health: String = "",
) {
    val powerW: Double
        get() = (currentMa / 1000.0) * voltageV

    val isCharging: Boolean
        get() = status == BatteryChargingStatus.CHARGING

    val isFull: Boolean
        get() = status == BatteryChargingStatus.FULL
}
