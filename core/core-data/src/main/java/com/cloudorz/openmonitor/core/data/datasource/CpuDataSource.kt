package com.cloudorz.openmonitor.core.data.datasource

import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.cloudorz.openmonitor.core.common.CpuNativeInfo
import com.cloudorz.openmonitor.core.common.SysfsReader
import com.cloudorz.openmonitor.core.data.util.MidrDecoder
import com.cloudorz.openmonitor.core.model.cpu.CpuCacheInfo
import com.cloudorz.openmonitor.core.model.cpu.CpuCoreInfo
import com.cloudorz.openmonitor.core.model.cpu.CpuClusterStatus
import com.cloudorz.openmonitor.core.model.cpu.CpuGlobalStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CpuDataSource @Inject constructor(
    private val sysfsReader: SysfsReader,
    private val cpuNativeInfo: CpuNativeInfo,
    private val socDatabase: SocDatabase,
) {
    private val cpuBasePath = "/sys/devices/system/cpu"
    private val procStatPath = "/proc/stat"

    suspend fun getCpuCoreCount(): Int = withContext(Dispatchers.IO) {
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
        if (stat1.isEmpty()) return@withContext fallbackOwnProcessLoad()

        delay(100)
        val stat2 = parseProcStat()
        if (stat2.isEmpty()) return@withContext fallbackOwnProcessLoad()

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
        // Direct File I/O first — /proc/stat is world-readable on stock Android
        // and this bypasses SysfsReader's denial cache entirely for this hot
        // path. Shell fallback handles OEMs that lock it down in BASIC mode.
        val raw = try {
            File(procStatPath).readText()
        } catch (e: Exception) {
            Log.d("CpuDataSource", "/proc/stat direct read failed: ${e.message}")
            sysfsReader.readLines(procStatPath).joinToString("\n")
        }
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence()
            .filter { it.startsWith("cpu") }
            .map { line ->
                line.trim().split("\\s+".toRegex())
                    .drop(1)
                    .map { it.toLongOrNull() ?: 0L }
                    .toLongArray()
            }
            .toList()
    }

    // Last-resort Android-API fallback when /proc/stat is completely denied
    // (some hardened OEM builds). Uses own-process CPU time sampled over a
    // short interval and fans the single aggregate figure out across all
    // cores so the progress bars at least animate. This is an approximation:
    // the returned percentages reflect *our* process's CPU, not the system's,
    // and will stay near zero when the app is idle in the background.
    private suspend fun fallbackOwnProcessLoad(): DoubleArray {
        val coreCount = getCpuCoreCount().coerceAtLeast(1)
        val tCpu1 = Process.getElapsedCpuTime()
        val tWall1 = SystemClock.elapsedRealtime()
        delay(150)
        val tCpu2 = Process.getElapsedCpuTime()
        val tWall2 = SystemClock.elapsedRealtime()
        val wallDiff = (tWall2 - tWall1).coerceAtLeast(1)
        val cpuDiff = (tCpu2 - tCpu1).coerceAtLeast(0)
        val loadPercent = (cpuDiff.toDouble() / wallDiff / coreCount * 100.0).coerceIn(0.0, 100.0)
        // Index 0 is the aggregate, 1..N are per-core; we have no per-core
        // breakdown here so just replicate the aggregate.
        return DoubleArray(coreCount + 1) { loadPercent }
    }

    suspend fun getClusterStatus(policyIndex: Int): CpuClusterStatus? = withContext(Dispatchers.IO) {
        val basePath = "$cpuBasePath/cpufreq/policy$policyIndex"
        val governor = sysfsReader.readString("$basePath/scaling_governor") ?: return@withContext null

        // Parse related_cpus or affected_cpus to get core indices for this cluster
        val coreIndices = parseCpuRange(
            sysfsReader.readString("$basePath/related_cpus")
                ?: sysfsReader.readString("$basePath/affected_cpus")
                ?: ""
        )

        CpuClusterStatus(
            clusterIndex = policyIndex,
            minFreqKHz = sysfsReader.readLong("$basePath/scaling_min_freq") ?: 0L,
            maxFreqKHz = sysfsReader.readLong("$basePath/scaling_max_freq") ?: 0L,
            governor = governor,
            availableGovernors = sysfsReader.readString("$basePath/scaling_available_governors")
                ?.split(" ")?.filter { it.isNotBlank() } ?: emptyList(),
            availableFrequenciesKHz = sysfsReader.readString("$basePath/scaling_available_frequencies")
                ?.split(" ")?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList(),
            coreIndices = coreIndices,
        )
    }

    /** Parse "0 1 2 3" or "0-5" style CPU list into int list. */
    private fun parseCpuRange(raw: String): List<Int> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        val result = mutableListOf<Int>()
        for (part in trimmed.split("\\s+|-".toRegex())) {
            part.trim().toIntOrNull()?.let { result.add(it) }
        }
        if (result.isEmpty() && trimmed.contains("-")) {
            // Handle "0-5" range format
            val parts = trimmed.split("-")
            if (parts.size == 2) {
                val start = parts[0].trim().toIntOrNull() ?: return emptyList()
                val end = parts[1].trim().toIntOrNull() ?: return emptyList()
                return (start..end).toList()
            }
        }
        return result.distinct().sorted()
    }

    suspend fun getCpuTemperature(): Double? = withContext(Dispatchers.IO) {
        // Read thermal zone files directly (world-readable) to avoid shell executor contention.
        for (i in 0..30) {
            val zonePath = "/sys/class/thermal/thermal_zone$i"
            val type = try {
                java.io.File("$zonePath/type").readText().trim()
            } catch (_: Exception) { continue }
            if (type.contains("cpu", ignoreCase = true) ||
                type.contains("tsens_tz_sensor", ignoreCase = true) ||
                type.contains("mtktscpu", ignoreCase = true)
            ) {
                val temp = try {
                    java.io.File("$zonePath/temp").readText().trim().toIntOrNull()
                } catch (_: Exception) { null } ?: continue
                val celsius = if (temp > 1000) temp / 1000.0 else temp.toDouble()
                if (celsius > -40 && celsius < 200) return@withContext celsius
            }
        }
        null
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

    private suspend fun readRawCpuInfo(): String = withContext(Dispatchers.IO) {
        try {
            File("/proc/cpuinfo").readText()
        } catch (e: Exception) {
            ""
        }
    }

    private fun getMidrFullMap(cpuInfoContent: String): Map<Int, MidrDecoder.CoreMidrInfo> {
        return try {
            MidrDecoder.parseProcCpuInfoFull(cpuInfoContent)
        } catch (e: Exception) {
            Log.d("CpuDataSource", "MIDR parse failed", e)
            emptyMap()
        }
    }

    private fun parseCpuFeatures(cpuInfoContent: String): List<String> {
        val line = cpuInfoContent.lineSequence().firstOrNull { it.startsWith("Features") }
            ?: return emptyList()
        return line.substringAfter(":").trim().split("\\s+".toRegex())
    }

    suspend fun getCpuFeatures(): List<String> = withContext(Dispatchers.IO) {
        try {
            parseCpuFeatures(File("/proc/cpuinfo").readText())
        } catch (e: Exception) {
            Log.d("CpuDataSource", "CPU features parse failed", e)
            emptyList()
        }
    }

    suspend fun getGlobalStatus(): CpuGlobalStatus {
        val coreCount = getCpuCoreCount()
        val loads = getCpuLoad()
        val rawCpuInfo = readRawCpuInfo()
        val midrFullMap = getMidrFullMap(rawCpuInfo)
        val cores = (0 until coreCount).map { i ->
            val midrInfo = midrFullMap[i]
            getCoreInfo(i).copy(
                loadPercent = loads.getOrElse(i + 1) { 0.0 },
                microarchName = midrInfo?.microarchName,
                vendorName = midrInfo?.vendorName,
            )
        }

        val clusterIndices = mutableListOf<Int>()
        for (i in 0..7) {
            if (sysfsReader.readString("$cpuBasePath/cpufreq/policy$i/scaling_governor") != null) {
                clusterIndices.add(i)
            }
        }
        val clusters = clusterIndices.mapNotNull { getClusterStatus(it) }

        val socInfo = socDatabase.getSocInfo()

        return CpuGlobalStatus(
            cpuName = if (socInfo.hasData) socInfo.name else getCpuName(),
            totalLoadPercent = loads.getOrElse(0) { 0.0 },
            temperatureCelsius = getCpuTemperature(),
            uptimeSeconds = getUptime(),
            cores = cores,
            clusters = clusters,
            cacheInfo = getCacheInfo(),
            hasArmNeon = getArmNeon(),
            socInfo = socInfo,
            cpuFeatures = parseCpuFeatures(rawCpuInfo),
            rawCpuInfo = rawCpuInfo,
        )
    }

    private suspend fun getUptime(): Long = withContext(Dispatchers.IO) {
        sysfsReader.readString("/proc/uptime")
            ?.split(" ")?.firstOrNull()?.toDoubleOrNull()?.toLong() ?: 0L
    }
}
