package com.cloudorz.openmonitor.core.model.memory

data class MemoryInfo(
    val totalKB: Long = 0,
    val availableKB: Long = 0,
    val freeKB: Long = 0,
    val buffersKB: Long = 0,
    val cachedKB: Long = 0,
    val swapTotalKB: Long = 0,
    val swapFreeKB: Long = 0,
    val swapCachedKB: Long = 0,
) {
    val usedKB: Long
        get() = totalKB - availableKB

    val usedPercent: Double
        get() = if (totalKB > 0) (usedKB.toDouble() / totalKB) * 100.0 else 0.0

    val totalMB: Double
        get() = totalKB / 1024.0

    val availableMB: Double
        get() = availableKB / 1024.0

    val usedMB: Double
        get() = usedKB / 1024.0

    val swapUsedKB: Long
        get() = swapTotalKB - swapFreeKB

    val swapUsedPercent: Double
        get() = if (swapTotalKB > 0) (swapUsedKB.toDouble() / swapTotalKB) * 100.0 else 0.0
}

data class ZramStats(
    val origDataSizeKB: Long = 0,
    val comprDataSizeKB: Long = 0,
    val memUsedKB: Long = 0,
    val memLimitKB: Long = 0,
    val memUsedMaxKB: Long = 0,
    val abnormal: Boolean = false,
) {
    val compressionRatio: Double
        get() = if (comprDataSizeKB > 0) origDataSizeKB.toDouble() / comprDataSizeKB else 0.0

    val memUsagePercent: Double
        get() = if (memLimitKB > 0) (memUsedKB.toDouble() / memLimitKB) * 100.0 else 0.0
}

data class SwapInfo(
    val totalKB: Long = 0,
    val freeKB: Long = 0,
    val cachedKB: Long = 0,
    val zram: ZramStats? = null,
) {
    val usedKB: Long
        get() = totalKB - freeKB

    val usedPercent: Double
        get() = if (totalKB > 0) (usedKB.toDouble() / totalKB) * 100.0 else 0.0
}
