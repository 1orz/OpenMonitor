package com.cloudorz.monitor.core.common

import android.os.Build
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects the SoC vendor/model and GPU type/model of the device.
 * Results are lazily computed and cached after the first access.
 */
@Singleton
class PlatformDetector @Inject constructor(
    private val sysfsReader: SysfsReader
) {
    enum class SocVendor {
        QUALCOMM, MEDIATEK, SAMSUNG_EXYNOS, GOOGLE_TENSOR, UNKNOWN
    }

    enum class GpuType {
        ADRENO, MALI, IMG_POWERVR, UNKNOWN
    }

    val socVendor: SocVendor by lazy { detectSocInternal().first }
    val socModel: String by lazy { detectSocInternal().second }
    val gpuType: GpuType by lazy { detectGpuInternal().first }
    val gpuModel: String by lazy { detectGpuInternal().second }

    fun detectSoc(): Pair<SocVendor, String> = detectSocInternal()
    fun detectGpu(): Pair<GpuType, String> = detectGpuInternal()

    private fun detectSocInternal(): Pair<SocVendor, String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manufacturer = Build.SOC_MANUFACTURER.lowercase()
            val model = Build.SOC_MODEL
            if (manufacturer.isNotBlank() && manufacturer != "unknown") {
                val vendor = classifySocVendor(manufacturer)
                if (vendor != SocVendor.UNKNOWN) return vendor to model
            }
        }

        val cpuInfoHardware = try {
            File("/proc/cpuinfo").readLines()
                .firstOrNull { it.startsWith("Hardware", ignoreCase = true) }
                ?.substringAfter(":")?.trim()
        } catch (_: Exception) { null }

        if (cpuInfoHardware != null) {
            val vendor = classifySocVendor(cpuInfoHardware.lowercase())
            if (vendor != SocVendor.UNKNOWN) return vendor to cpuInfoHardware
        }

        val hw = Build.HARDWARE
        if (hw.isNotBlank()) return classifySocVendor(hw.lowercase()) to hw

        return SocVendor.UNKNOWN to "Unknown"
    }

    private fun classifySocVendor(id: String): SocVendor = when {
        id.contains("qcom") || id.contains("qualcomm") || id.contains("snapdragon") ||
            id.contains("msm") || id.contains("sdm") || id.contains("sm8") || id.contains("sm7") -> SocVendor.QUALCOMM
        id.contains("mediatek") || id.contains("mt6") || id.contains("mt8") -> SocVendor.MEDIATEK
        id.contains("exynos") || id.contains("samsung") || id.contains("s5e") -> SocVendor.SAMSUNG_EXYNOS
        id.contains("tensor") || id.contains("google") || id.contains("gs1") || id.contains("gs2") -> SocVendor.GOOGLE_TENSOR
        else -> SocVendor.UNKNOWN
    }

    private fun detectGpuInternal(): Pair<GpuType, String> {
        if (File("/sys/class/kgsl/").exists()) {
            val model = try {
                File("/sys/class/kgsl/kgsl-3d0/gpu_model").readText().trim().ifBlank { null }
            } catch (_: Exception) { null }
            return GpuType.ADRENO to (model ?: "Adreno")
        }

        if (File("/sys/module/mali/").exists() || File("/sys/module/mali_kbase/").exists()) {
            return GpuType.MALI to "Mali"
        }

        if (File("/proc/gpufreq/").exists() || File("/proc/gpufreqv2/").exists()) {
            return GpuType.MALI to "Mali (MediaTek)"
        }

        if (File("/sys/module/pvrsrvkm/").exists()) {
            return GpuType.IMG_POWERVR to "PowerVR"
        }

        return GpuType.UNKNOWN to "Unknown"
    }
}
