package com.cloudorz.openmonitor.feature.charge

data class ChargeChartPoint(
    val timestamp: Long,
    val capacity: Int,      // 0-100%
    val currentMa: Long,
    val temperature: Float,
)
