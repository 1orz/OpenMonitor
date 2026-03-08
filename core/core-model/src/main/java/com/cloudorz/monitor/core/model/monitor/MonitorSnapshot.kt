package com.cloudorz.monitor.core.model.monitor

import com.cloudorz.monitor.core.model.fps.FpsData

data class MonitorSnapshot(
    val cpuLoadPercent: Double = 0.0,
    val cpuCoreLoads: List<Double> = emptyList(),
    val gpuLoadPercent: Double = 0.0,
    val gpuFreqMhz: Int = 0,
    val cpuTempCelsius: Double = 0.0,
    val batteryCurrentMa: Int = 0,
    val fpsData: FpsData? = null,
    val daemonRunner: String = "",  // "root" | "shell" | "" (non-daemon path)
    val timestamp: Long = System.currentTimeMillis(),
)
