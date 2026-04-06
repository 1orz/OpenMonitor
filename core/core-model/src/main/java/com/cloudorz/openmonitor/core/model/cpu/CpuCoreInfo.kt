package com.cloudorz.openmonitor.core.model.cpu

data class CpuCoreInfo(
    val coreIndex: Int,
    val currentFreqKHz: Long = 0,
    val minFreqKHz: Long = 0,
    val maxFreqKHz: Long = 0,
    val loadPercent: Double = 0.0,
    val isOnline: Boolean = true,
    val microarchName: String? = null,
    val vendorName: String? = null,
) {
    val currentFreqMHz: Double
        get() = currentFreqKHz / 1000.0

    val minFreqMHz: Double
        get() = minFreqKHz / 1000.0

    val maxFreqMHz: Double
        get() = maxFreqKHz / 1000.0
}
