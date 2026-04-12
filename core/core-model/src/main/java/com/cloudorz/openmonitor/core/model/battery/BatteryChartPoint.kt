package com.cloudorz.openmonitor.core.model.battery

data class BatteryChartPoint(
    val timestamp: Long,
    val capacity: Int,
    val currentMa: Int,
    val powerW: Double,
    val temperatureCelsius: Double,
    val isCharging: Boolean,
    val packageName: String,
)
