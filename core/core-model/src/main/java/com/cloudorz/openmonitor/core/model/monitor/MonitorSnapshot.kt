package com.cloudorz.openmonitor.core.model.monitor

import com.cloudorz.openmonitor.core.model.fps.FpsData

data class MonitorSnapshot(
    val cpuLoadPercent: Double? = null,
    val cpuCoreLoads: List<Double>? = null,
    val cpuCoreFreqs: List<Int>? = null,
    val gpuLoadPercent: Double? = null,
    val gpuFreqMhz: Int? = null,
    val cpuTempCelsius: Double? = null,
    val batteryCurrentMa: Int? = null,
    val fpsData: FpsData? = null,
    val daemonRunner: String = "",  // "root" | "shell" | "" (non-daemon path)
    val timestamp: Long = System.currentTimeMillis(),
)
