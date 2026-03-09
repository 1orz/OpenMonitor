package com.cloudorz.openmonitor.core.data.datasource

import com.cloudorz.openmonitor.core.common.CpuNativeInfo
import com.cloudorz.openmonitor.core.common.SysfsReader
import com.cloudorz.openmonitor.core.model.cpu.CpuCacheInfo
import com.cloudorz.openmonitor.core.model.cpu.CpuCoreInfo
import com.cloudorz.openmonitor.core.model.cpu.CpuClusterStatus
import com.cloudorz.openmonitor.core.model.cpu.CpuGlobalStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CpuDataSource @Inject constructor(
    private val sysfsReader: SysfsReader,
    private val cpuNativeInfo: CpuNativeInfo,
) {
    private val cpuBasePath = "/sys/devices/system/cpu"
    private val procStatPath = "/proc/stat"

    suspend fun getCpuCoreCount(): Int = withContext(Dispatchers.IO) {
        val pattern = Regex("cpu\\d+")
        sysfsReader.readString("$cpuBasePath/possible")
            ?.let { possible ->
                val parts = possible.trim().split("-")
                if (parts.size == 2) parts[1].toIntOrNull()?.plus(1) ?: Runtime.getRuntime().availableProcessors()
                else Runtime.getRuntime().availableProcessors()
            } ?: Runtime.getRuntime().availableProcessors()
    }

    suspend fun getCoreInfo(coreIndex: Int): CpuCoreInfo = withContext(Dispatchers.IO) {
        val basePath = "$cpuBasePath/cpu$coreIndex/cpufreq"
        val isOnline = sysfsReader.readInt("$cpuBasePath/cpu$coreIndex/online") != 0 || coreIndex == 0
        CpuCoreInfo(
            coreIndex = coreIndex,
            currentFreqKHz = if (isOnline) sysfsReader.readLong("$basePath/scaling_cur_freq") ?: 0L else 0L,
            minFreqKHz = sysfsReader.readLong("$basePath/cpuinfo_min_freq") ?: 0L,
            maxFreqKHz = sysfsReader.readLong("$basePath/cpuinfo_max_freq") ?: 0L,
            isOnline = isOnline
        )
    }

    suspend fun getCpuLoad(): DoubleArray = withContext(Dispatchers.IO) {
        val stat1 = parseProcStat()
        delay(100)
        val stat2 = parseProcStat()

        val coreCount = stat1.size.coerceAtMost(stat2.size)
        DoubleArray(coreCount) { i ->
            val idle1 = stat1[i][3] + stat1[i][4]
            val idle2 = stat2[i][3] + stat2[i][4]
            val total1 = stat1[i].sum()
            val total2 = stat2[i].sum()
            val totalDiff = (total2 - total1).toDouble()
            val idleDiff = (idle2 - idle1).toDouble()
            if (totalDiff > 0) ((totalDiff - idleDiff) / totalDiff * 100.0).coerceIn(0.0, 100.0)
            else 0.0
        }
    }

    private suspend fun parseProcStat(): List<LongArray> {
        val lines = sysfsReader.readLines(procStatPath)
        return lines
            .filter { it.startsWith("cpu") }
            .map { line ->
                line.trim().split("\\s+".toRegex())
                    .drop(1)
                    .map { it.toLongOrNull() ?: 0L }
                    .toLongArray()
            }
    }

    suspend fun getClusterStatus(policyIndex: Int): CpuClusterStatus? = withContext(Dispatchers.IO) {
        val basePath = "$cpuBasePath/cpufreq/policy$policyIndex"
        val governor = sysfsReader.readString("$basePath/scaling_governor") ?: return@withContext null
        CpuClusterStatus(
            minFreqKHz = sysfsReader.readLong("$basePath/scaling_min_freq") ?: 0L,
            maxFreqKHz = sysfsReader.readLong("$basePath/scaling_max_freq") ?: 0L,
            governor = governor,
            availableGovernors = sysfsReader.readString("$basePath/scaling_available_governors")
                ?.split(" ")?.filter { it.isNotBlank() } ?: emptyList(),
            availableFrequenciesKHz = sysfsReader.readString("$basePath/scaling_available_frequencies")
                ?.split(" ")?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()
        )
    }

    suspend fun getCpuTemperature(): Double = withContext(Dispatchers.IO) {
        // Try common thermal zones for CPU temperature
        for (i in 0..20) {
            val type = sysfsReader.readString("/sys/class/thermal/thermal_zone$i/type") ?: continue
            if (type.contains("cpu", ignoreCase = true) || type.contains("tsens_tz_sensor", ignoreCase = true)) {
                val temp = sysfsReader.readInt("/sys/class/thermal/thermal_zone$i/temp") ?: continue
                return@withContext if (temp > 1000) temp / 1000.0 else temp.toDouble()
            }
        }
        -1.0
    }

    suspend fun getCpuName(): String = withContext(Dispatchers.IO) {
        // Prefer native cpuinfo library for accurate SoC name
        cpuNativeInfo.getCpuName()?.let { return@withContext it }

        // Fallback to /proc/cpuinfo parsing
        val cpuInfo = sysfsReader.readLines("/proc/cpuinfo")
        if (cpuInfo.isEmpty()) return@withContext "Unknown"
        cpuInfo.firstOrNull { it.startsWith("Hardware") }
            ?.substringAfter(":")?.trim()
            ?: cpuInfo.firstOrNull { it.startsWith("model name") }
                ?.substringAfter(":")?.trim()
            ?: "Unknown"
    }

    fun getCacheInfo(): CpuCacheInfo {
        if (!cpuNativeInfo.isAvailable) return CpuCacheInfo()
        return CpuCacheInfo(
            l1dCacheSizes = cpuNativeInfo.getL1dCacheSizes()?.toList() ?: emptyList(),
            l1iCacheSizes = cpuNativeInfo.getL1iCacheSizes()?.toList() ?: emptyList(),
            l2CacheSizes = cpuNativeInfo.getL2CacheSizes()?.toList() ?: emptyList(),
            l3CacheSizes = cpuNativeInfo.getL3CacheSizes()?.toList() ?: emptyList(),
        )
    }

    fun getArmNeon(): Boolean? = cpuNativeInfo.hasArmNeon()

    suspend fun getGlobalStatus(): CpuGlobalStatus {
        val coreCount = getCpuCoreCount()
        val loads = getCpuLoad()
        val cores = (0 until coreCount).map { i ->
            getCoreInfo(i).copy(
                loadPercent = loads.getOrElse(i + 1) { 0.0 }
            )
        }

        val clusterIndices = mutableListOf<Int>()
        for (i in 0..7) {
            if (sysfsReader.readString("$cpuBasePath/cpufreq/policy$i/scaling_governor") != null) {
                clusterIndices.add(i)
            }
        }
        val clusters = clusterIndices.mapNotNull { getClusterStatus(it) }

        return CpuGlobalStatus(
            cpuName = getCpuName(),
            totalLoadPercent = loads.getOrElse(0) { 0.0 },
            temperatureCelsius = getCpuTemperature(),
            uptimeSeconds = getUptime(),
            cores = cores,
            clusters = clusters,
            cacheInfo = getCacheInfo(),
            hasArmNeon = getArmNeon(),
        )
    }

    private suspend fun getUptime(): Long = withContext(Dispatchers.IO) {
        sysfsReader.readString("/proc/uptime")
            ?.split(" ")?.firstOrNull()?.toDoubleOrNull()?.toLong() ?: 0L
    }
}
