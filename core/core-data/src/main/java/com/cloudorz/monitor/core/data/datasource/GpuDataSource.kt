package com.cloudorz.monitor.core.data.datasource

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.cloudorz.monitor.core.common.PlatformDetector
import com.cloudorz.monitor.core.common.SysfsReader
import com.cloudorz.monitor.core.model.gpu.GpuInfo
import com.cloudorz.monitor.core.model.gpu.GpuVendor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GpuDataSource @Inject constructor(
    private val sysfsReader: SysfsReader,
    private val platformDetector: PlatformDetector,
    @ApplicationContext private val context: Context
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

        return GpuInfo(
            vendor = GpuVendor.QUALCOMM_ADRENO,
            model = model,
            currentFreqMHz = curFreq,
            minFreqMHz = minFreq,
            maxFreqMHz = maxFreq,
            loadPercent = busy,
            governor = governor,
            glesVersion = glesVersion,
            vulkanVersion = vulkanVersion
        )
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

        return GpuInfo(
            vendor = GpuVendor.ARM_MALI,
            model = "Mali",
            currentFreqMHz = curFreq,
            minFreqMHz = minFreq,
            maxFreqMHz = maxFreq,
            loadPercent = load,
            governor = governor,
            glesVersion = glesVersion,
            vulkanVersion = vulkanVersion
        )
    }

    private fun readGenericGpuInfo(): GpuInfo {
        return GpuInfo(
            vendor = GpuVendor.UNKNOWN,
            glesVersion = glesVersion,
            vulkanVersion = vulkanVersion
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
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
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
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.d(TAG, "readVulkanVersion failed", e)
            ""
        }
    }
}
