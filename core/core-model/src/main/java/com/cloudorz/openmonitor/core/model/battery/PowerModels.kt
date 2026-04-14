package com.cloudorz.openmonitor.core.model.battery

data class PowerStatRecord(
    val capacity: Int = 0,
    val isCharging: Boolean = false,
    val startTime: Long = 0,
    val endTime: Long = 0,
    val isFuzzy: Boolean = false,
    val ioBytes: Long = 0,
    val packageName: String = "",
    val isScreenOn: Boolean = false,
    val powerW: Double = 0.0,
    val temperature: Double = 0.0,
) {
    val durationMs: Long
        get() = endTime - startTime

    val durationSeconds: Long
        get() = durationMs / 1000
}

data class PowerStatSession(
    val sessionId: String = "",
    val beginTime: Long = 0,
    val endTime: Long = 0,
    val usedPercent: Double = 0.0,
    val avgPowerW: Double = 0.0,
) {
    /** Whether this session is still actively tracking. */
    val isActive: Boolean
        get() = endTime == 0L

    val durationMs: Long
        get() = if (isActive) {
            System.currentTimeMillis() - beginTime
        } else {
            endTime - beginTime
        }

    val durationSeconds: Long
        get() = durationMs / 1000
}
