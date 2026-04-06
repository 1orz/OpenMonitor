package com.cloudorz.openmonitor.core.model.gpu

enum class GpuVendor(val displayName: String) {
    QUALCOMM_ADRENO("Qualcomm Adreno"),
    ARM_MALI("ARM Mali"),
    IMG_POWERVR("Imagination PowerVR"),
    UNKNOWN("Unknown"),
}

data class GpuInfo(
    val vendor: GpuVendor = GpuVendor.UNKNOWN,
    val model: String = "",
    val currentFreqMHz: Long = 0,
    val minFreqMHz: Long = 0,
    val maxFreqMHz: Long = 0,
    val loadPercent: Double = 0.0,
    val memoryUsedMB: Long = 0,
    val memoryTotalMB: Long = 0,
    val governor: String = "",
    val driverVersion: String = "",
    val glesVersion: String = "",
    val vulkanVersion: String = "",
    // Adreno-specific
    val chipId: String = "",
    val gmemSizeKB: Int = 0,
    // Mali-specific
    val shaderCores: Int = 0,
    val busWidthBits: Int = 0,
    val l2CacheKB: Int = 0,
    // Vulkan full info (JSON from native VulkanInfoBridge)
    val vulkanInfoJson: String = "{}",
    // OpenGL ES full strings (from EGL context via EglHelper)
    val glRenderer: String = "",
    val glVersionFull: String = "",
    val glVendor: String = "",
    val glExtensionsCount: Int = 0,
) {
    val memoryUsagePercent: Double
        get() = if (memoryTotalMB > 0) (memoryUsedMB.toDouble() / memoryTotalMB) * 100.0 else 0.0

    val frequencyUsagePercent: Double
        get() = if (maxFreqMHz > 0) (currentFreqMHz.toDouble() / maxFreqMHz) * 100.0 else 0.0
}
