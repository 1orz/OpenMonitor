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
    val batteryCurrentUa: Int? = null,
    val batteryVoltageUv: Int? = null,
    val batteryPowerMw: Int? = null,
    val fpsData: FpsData? = null,
    val daemonRunner: String = "",  // "root" | "shell" | "" (non-daemon path)
    val timestamp: Long = System.currentTimeMillis(),
) {
    val precisePowerW: Double?
        get() {
            if (batteryCurrentUa != null && batteryVoltageUv != null) {
                return kotlin.math.abs(batteryCurrentUa.toLong() * batteryVoltageUv.toLong()) / 1e12
            }
            if (batteryPowerMw != null) return batteryPowerMw / 1000.0
            if (batteryCurrentMa != null) return null
            return null
        }
}
