package com.cloudorz.monitor.core.model.process

data class ThreadInfo(
    val tid: Int = 0,
    val name: String = "",
    val cpuLoadPercent: Double = 0.0,
    val cpuAffinity: String = "",
)
