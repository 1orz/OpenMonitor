package com.cloudorz.openmonitor.core.model.cpu

data class CpuClusterStatus(
    val clusterIndex: Int = 0,
    val minFreqKHz: Long = 0,
    val maxFreqKHz: Long = 0,
    val governor: String = "",
    val governorParams: Map<String, String> = emptyMap(),
    val availableGovernors: List<String> = emptyList(),
    val availableFrequenciesKHz: List<Long> = emptyList(),
    val coreIndices: List<Int> = emptyList(),
) {
    val minFreqMHz: Double
        get() = minFreqKHz / 1000.0

    val maxFreqMHz: Double
        get() = maxFreqKHz / 1000.0
}
