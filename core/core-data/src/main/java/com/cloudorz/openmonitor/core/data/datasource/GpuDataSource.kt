package com.cloudorz.openmonitor.core.data.datasource

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.cloudorz.openmonitor.core.common.PlatformDetector
import com.cloudorz.openmonitor.core.common.SysfsReader
import com.cloudorz.openmonitor.core.model.gpu.GpuInfo
import com.cloudorz.openmonitor.core.model.gpu.GpuVendor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GpuDataSource @Inject constructor(
    private val sysfsReader: SysfsReader,
    private val platformDetector: PlatformDetector,
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "GpuDataSource"
    }

    // Qualcomm Adreno paths
    private val adrenoBasePath = "/sys/class/kgsl/kgsl-3d0"

    // ARM Mali paths
    private val maliBasePath = "/sys/class/misc/mali0/device"

    // MediaTek GPU paths
    private val mtkGpuPath = "/proc/gpufreq/gpufreq_var_dump"

    private val glesVersion: String by lazy { readGlesVersion() }
    private val vulkanVersion: String by lazy { readVulkanVersion() }
    private val vulkanInfoJson: String by lazy { VulkanInfoBridge.getVulkanInfoJson() }
    private val glStrings: EglHelper.GlStrings by lazy { EglHelper.queryGlStrings() }

    suspend fun getGpuInfo(): GpuInfo = withContext(Dispatchers.IO) {
        when (platformDetector.gpuType) {
            PlatformDetector.GpuType.ADRENO -> readAdrenoInfo()
            PlatformDetector.GpuType.MALI -> readMaliInfo()
            else -> readGenericGpuInfo()
        }
    }

    private suspend fun readAdrenoInfo(): GpuInfo {
        val curFreq = sysfsReader.readLong("$adrenoBasePath/gpuclk")?.div(1000000) ?: 0L
        val maxFreq = sysfsReader.readLong("$adrenoBasePath/max_gpuclk")?.div(1000000) ?: 0L
        val minFreq = sysfsReader.readLong("$adrenoBasePath/min_gpuclk")?.div(1000000) ?: 0L
        val busy = sysfsReader.readString("$adrenoBasePath/gpu_busy_percentage")
            ?.replace("%", "")?.trim()?.toDoubleOrNull() ?: 0.0
        val governor = sysfsReader.readString("$adrenoBasePath/devfreq/governor") ?: ""
        val model = sysfsReader.readString("$adrenoBasePath/gpu_model") ?: "Adreno"

        // Adreno chip ID — from gpu_model or chipid sysfs
        val chipId = sysfsReader.readString("$adrenoBasePath/chipid")?.trim() ?: ""

        // GMEM size (on-chip memory) — kgsl sysfs
        val gmemSizeKB = readAdrenoGmemSize()

        return GpuInfo(
            vendor = GpuVendor.QUALCOMM_ADRENO,
            model = model,
            currentFreqMHz = curFreq,
            minFreqMHz = minFreq,
            maxFreqMHz = maxFreq,
            loadPercent = busy,
            governor = governor,
            glesVersion = glesVersion,
            vulkanVersion = vulkanVersion,
            vulkanInfoJson = vulkanInfoJson,
            glRenderer = glStrings.renderer,
            glVersionFull = glStrings.version,
            glVendor = glStrings.vendor,
            glExtensionsCount = glStrings.extensionsCount,
            chipId = chipId,
            gmemSizeKB = gmemSizeKB,
        )
    }

    private suspend fun readAdrenoGmemSize(): Int {
        // Try reading from sysfs (some kernels expose this)
        val gmemPaths = listOf(
            "$adrenoBasePath/gmem_size",
            "/sys/kernel/gpu/gmem_size",
        )
        for (path in gmemPaths) {
            val value = sysfsReader.readLong(path)
            if (value != null && value > 0) {
                return (value / 1024).toInt() // bytes → KB
            }
        }
        // Try parsing from debug info
        val debugInfo = sysfsReader.readString("$adrenoBasePath/gpu_model")
        // Some kernels include GMEM in the gpu_model info string
        return 0
    }

    private suspend fun readMaliInfo(): GpuInfo {
        val curFreq = sysfsReader.readLong("$maliBasePath/clock")?.div(1000000) ?: run {
            sysfsReader.readLong("$maliBasePath/devfreq/cur_freq")?.div(1000000) ?: 0L
        }
        val maxFreq = sysfsReader.readLong("$maliBasePath/devfreq/max_freq")?.div(1000000) ?: 0L
        val minFreq = sysfsReader.readLong("$maliBasePath/devfreq/min_freq")?.div(1000000) ?: 0L
        val load = sysfsReader.readString("$maliBasePath/utilization")
            ?.trim()?.toDoubleOrNull() ?: 0.0
        val governor = sysfsReader.readString("$maliBasePath/devfreq/governor") ?: ""

        // Mali GPU model — try multiple paths
        val model = readMaliModel()

        // Mali hardware info
        val shaderCores = readMaliShaderCores()
        val busWidth = readMaliBusWidth()
        val l2CacheKB = readMaliL2Cache()

        return GpuInfo(
            vendor = GpuVendor.ARM_MALI,
            model = model,
            currentFreqMHz = curFreq,
            minFreqMHz = minFreq,
            maxFreqMHz = maxFreq,
            loadPercent = load,
            governor = governor,
            glesVersion = glesVersion,
            vulkanVersion = vulkanVersion,
            vulkanInfoJson = vulkanInfoJson,
            glRenderer = glStrings.renderer,
            glVersionFull = glStrings.version,
            glVendor = glStrings.vendor,
            glExtensionsCount = glStrings.extensionsCount,
            shaderCores = shaderCores,
            busWidthBits = busWidth,
            l2CacheKB = l2CacheKB,
        )
    }

    private suspend fun readMaliModel(): String {
        // Try reading from sysfs gpu_name or product_id
        val gpuNamePaths = listOf(
            "/sys/kernel/gpu/gpu_model",
            "$maliBasePath/gpuinfo",
            "$maliBasePath/gpu_name",
        )
        for (path in gpuNamePaths) {
            val value = sysfsReader.readString(path)
            if (value != null && value.isNotBlank() && value != "0") {
                return value.trim()
            }
        }
        // Try parsing product ID to model name
        val productId = sysfsReader.readString("$maliBasePath/product_id")
        if (productId != null) {
            val id = productId.trim().removePrefix("0x").toIntOrNull(16)
            if (id != null) {
                return maliProductIdToName(id)
            }
        }
        return "Mali"
    }

    private fun maliProductIdToName(productId: Int): String {
        // Mali product ID mapping
        return when (productId and 0xFFFF) {
            in 0x7500..0x75FF -> "Mali-T760"
            in 0x8600..0x86FF -> "Mali-T860"
            in 0x8800..0x88FF -> "Mali-T880"
            in 0x7000..0x70FF -> "Mali-G71"
            in 0x7200..0x72FF -> "Mali-G72"
            in 0x7400..0x74FF -> "Mali-G76"
            in 0x9000..0x90FF -> "Mali-G77"
            in 0x9200..0x92FF -> "Mali-G78"
            in 0x9400..0x94FF -> "Mali-G710"
            in 0xA000..0xA0FF -> "Mali-G715"
            in 0xA200..0xA2FF -> "Mali-G720"
            in 0xB000..0xB0FF -> "Mali-G310"
            in 0xB200..0xB2FF -> "Mali-G510"
            in 0xB400..0xB4FF -> "Mali-G610"
            in 0xB600..0xB6FF -> "Mali-G615"
            in 0xB800..0xB8FF -> "Mali-G620"
            else -> "Mali (0x${productId.toString(16)})"
        }
    }

    private suspend fun readMaliShaderCores(): Int {
        val paths = listOf(
            "$maliBasePath/core_count",
            "$maliBasePath/num_shader_cores",
        )
        for (path in paths) {
            val value = sysfsReader.readInt(path)
            if (value != null && value > 0) return value
        }
        return 0
    }

    private suspend fun readMaliBusWidth(): Int {
        val value = sysfsReader.readInt("$maliBasePath/bus_width")
        return value ?: 0
    }

    private suspend fun readMaliL2Cache(): Int {
        // L2 cache size in bytes, convert to KB
        val paths = listOf(
            "$maliBasePath/l2_cache_size",
            "$maliBasePath/l2_size",
        )
        for (path in paths) {
            val value = sysfsReader.readLong(path)
            if (value != null && value > 0) {
                return (value / 1024).toInt()
            }
        }
        return 0
    }

    private fun readGenericGpuInfo(): GpuInfo {
        return GpuInfo(
            vendor = GpuVendor.UNKNOWN,
            glesVersion = glesVersion,
            vulkanVersion = vulkanVersion,
            vulkanInfoJson = vulkanInfoJson,
            glRenderer = glStrings.renderer,
            glVersionFull = glStrings.version,
            glVendor = glStrings.vendor,
            glExtensionsCount = glStrings.extensionsCount,
        )
    }

    private fun readGlesVersion(): String {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val configInfo = am?.deviceConfigurationInfo
            configInfo?.glEsVersion ?: ""
        } catch (e: Exception) {
            Log.d(TAG, "readGlesVersion failed", e)
            ""
        }
    }

    private fun readVulkanVersion(): String {
        return try {
            val features = context.packageManager.systemAvailableFeatures
            val vulkanFeature = features.firstOrNull {
                it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION
            }
            if (vulkanFeature != null) {
                val major = (vulkanFeature.version shr 22) and 0x3FF
                val minor = (vulkanFeature.version shr 12) and 0x3FF
                val patch = vulkanFeature.version and 0xFFF
                "$major.$minor.$patch"
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.d(TAG, "readVulkanVersion failed", e)
            ""
        }
    }
}
