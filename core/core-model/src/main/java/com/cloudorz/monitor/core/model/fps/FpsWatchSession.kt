package com.cloudorz.monitor.core.model.fps

data class FpsWatchSession(
    val sessionId: String = "",
    val packageName: String = "",
    val appName: String = "",
    val avgFps: Double = 0.0,
    val avgPowerW: Double = 0.0,
    val beginTime: Long = 0,
    val durationSeconds: Long = 0,
    val mode: String = "",
    val packageVersion: String = "",
    val sessionDesc: String = "",
    val viewSize: String = "",
) {
    val endTime: Long
        get() = beginTime + (durationSeconds * 1000)
}
