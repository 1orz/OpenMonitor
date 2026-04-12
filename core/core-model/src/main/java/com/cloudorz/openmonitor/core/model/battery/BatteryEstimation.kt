package com.cloudorz.openmonitor.core.model.battery

data class BatteryEstimation(
    val remainingMinutes: Int,
    val drainRatePercentPerMinute: Double,
    val avgPowerW: Double,
    val screenOnTimeMs: Long,
)
