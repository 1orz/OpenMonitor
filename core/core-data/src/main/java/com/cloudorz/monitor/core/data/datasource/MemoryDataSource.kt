package com.cloudorz.monitor.core.data.datasource

import com.cloudorz.monitor.core.common.SysfsReader
import com.cloudorz.monitor.core.model.memory.MemoryInfo
import com.cloudorz.monitor.core.model.memory.SwapInfo
import com.cloudorz.monitor.core.model.memory.ZramStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryDataSource @Inject constructor(
    private val sysfsReader: SysfsReader
) {
    suspend fun getMemoryInfo(): MemoryInfo = withContext(Dispatchers.IO) {
        val lines = sysfsReader.readLines("/proc/meminfo") ?: return@withContext MemoryInfo()
        val map = mutableMapOf<String, Long>()
        for (line in lines) {
            val parts = line.split(":")
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim().split("\\s+".toRegex())[0].toLongOrNull() ?: 0L
                map[key] = value
            }
        }

        MemoryInfo(
            totalKB = map["MemTotal"] ?: 0L,
            availableKB = map["MemAvailable"] ?: 0L,
            freeKB = map["MemFree"] ?: 0L,
            buffersKB = map["Buffers"] ?: 0L,
            cachedKB = map["Cached"] ?: 0L,
            swapTotalKB = map["SwapTotal"] ?: 0L,
            swapFreeKB = map["SwapFree"] ?: 0L,
            swapCachedKB = map["SwapCached"] ?: 0L
        )
    }

    suspend fun getZramStats(): ZramStats? = withContext(Dispatchers.IO) {
        val mmStat = sysfsReader.readString("/sys/block/zram0/mm_stat") ?: return@withContext null
        val parts = mmStat.trim().split("\\s+".toRegex())
        if (parts.size < 5) return@withContext null

        ZramStats(
            origDataSizeKB = (parts[0].toLongOrNull() ?: 0L) / 1024,
            comprDataSizeKB = (parts[1].toLongOrNull() ?: 0L) / 1024,
            memUsedKB = (parts[2].toLongOrNull() ?: 0L) / 1024,
            memLimitKB = (parts[3].toLongOrNull() ?: 0L) / 1024,
            memUsedMaxKB = (parts[4].toLongOrNull() ?: 0L) / 1024
        )
    }

    suspend fun getSwapInfo(): SwapInfo = withContext(Dispatchers.IO) {
        val memInfo = getMemoryInfo()
        val zram = getZramStats()
        SwapInfo(
            totalKB = memInfo.swapTotalKB,
            freeKB = memInfo.swapFreeKB,
            cachedKB = memInfo.swapCachedKB,
            zram = zram
        )
    }
}
