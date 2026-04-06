package com.cloudorz.openmonitor.core.model.cpu

data class CpuStatus(
    val clusters: List<CpuClusterStatus> = emptyList(),
    val coreOnline: List<Boolean> = emptyList(),
    val gpuMinFreqKHz: Long = 0,
    val gpuMaxFreqKHz: Long = 0,
    val adrenoGovernor: String = "",
    val cpusetBg: String = "",
    val cpusetSysBg: String = "",
    val cpusetFg: String = "",
    val cpusetTop: String = "",
    val cpusetRestricted: String = "",
) {
    val onlineCoreCount: Int
        get() = coreOnline.count { it }

    val totalCoreCount: Int
        get() = coreOnline.size
}

data class CpuGlobalStatus(
    val cpuName: String = "",
    val totalLoadPercent: Double = 0.0,
    val temperatureCelsius: Double = 0.0,
    val uptimeSeconds: Long = 0,
    val cores: List<CpuCoreInfo> = emptyList(),
    val clusters: List<CpuClusterStatus> = emptyList(),
    val cpuStatus: CpuStatus = CpuStatus(),
    val cacheInfo: CpuCacheInfo = CpuCacheInfo(),
    val hasArmNeon: Boolean? = null,
    val socInfo: SocInfo = SocInfo(),
    val cpuFeatures: List<String> = emptyList(),
    val rawCpuInfo: String = "",
) {
    val coreCount: Int
        get() = cores.size

    val onlineCoreCount: Int
        get() = cores.count { it.isOnline }

    val averageFreqMHz: Double
        get() {
            val onlineCores = cores.filter { it.isOnline }
            return if (onlineCores.isEmpty()) 0.0
            else onlineCores.sumOf { it.currentFreqKHz } / (onlineCores.size * 1000.0)
        }
}
