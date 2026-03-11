package com.cloudorz.openmonitor.core.model.fps

data class FpsFrameRecord(
    val timestamp: Long,
    val fps: Double,
    val jankCount: Int,
    val bigJankCount: Int,
    val maxFrameTimeMs: Int,
    val frameTimesMs: List<Int>,
    val cpuLoad: Double,
    val cpuTemp: Double,
    val gpuLoad: Double,
    val gpuFreqMhz: Int,
    val batteryCapacity: Int,
    val batteryCurrentMa: Int,
    val batteryTemp: Double,
    val powerW: Double,
    val cpuCoreLoads: List<Double>,
    val cpuCoreFreqsMhz: List<Long>,
)
