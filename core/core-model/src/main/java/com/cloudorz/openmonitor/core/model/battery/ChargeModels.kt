package com.cloudorz.openmonitor.core.model.battery

data class ChargeStatRecord(
    val capacity: Int = 0,
    val currentMa: Int = 0,
    val temperatureCelsius: Double = 0.0,
    val powerW: Double = 0.0,
    val timestamp: Long = 0,
)

data class ChargeStatSession(
    val sessionId: String = "",
    val beginTime: Long = 0,
    val endTime: Long = 0,
    val capacityRatio: Double = 0.0,
    val capacityWh: Double = 0.0,
) {
    val durationMs: Long
        get() = endTime - beginTime

    val durationSeconds: Long
        get() = durationMs / 1000
}
