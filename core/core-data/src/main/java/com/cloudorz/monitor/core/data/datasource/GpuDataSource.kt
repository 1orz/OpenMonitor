package com.cloudorz.monitor.core.data.datasource

import com.cloudorz.monitor.core.common.PlatformDetector
import com.cloudorz.monitor.core.common.SysfsReader
import com.cloudorz.monitor.core.model.gpu.GpuInfo
import com.cloudorz.monitor.core.model.gpu.GpuVendor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GpuDataSource @Inject constructor(
    private val sysfsReader: SysfsReader,
    private val platformDetector: PlatformDetector
) {
    // Qualcomm Adreno paths
    private val adrenoBasePath = "/sys/class/kgsl/kgsl-3d0"

    // ARM Mali paths
    private val maliBasePath = "/sys/class/misc/mali0/device"

    // MediaTek GPU paths
    private val mtkGpuPath = "/proc/gpufreq/gpufreq_var_dump"

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
            governor = governor
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
            governor = governor
        )
    }

    private suspend fun readGenericGpuInfo(): GpuInfo {
        return GpuInfo(vendor = GpuVendor.UNKNOWN)
    }
}
