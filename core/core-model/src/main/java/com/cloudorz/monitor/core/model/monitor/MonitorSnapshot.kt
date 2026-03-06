package com.cloudorz.monitor.core.model.monitor

import com.cloudorz.monitor.core.model.fps.FpsData

data class MonitorSnapshot(
    val cpuLoadPercent: Double = 0.0,
    val gpuLoadPercent: Double = 0.0,
    val cpuTempCelsius: Double = 0.0,
    val batteryCurrentMa: Int = 0,
    val fpsData: FpsData? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
