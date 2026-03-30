package com.cloudorz.openmonitor.core.data.repository

import com.cloudorz.openmonitor.core.data.datasource.AppInfoResolver
import com.cloudorz.openmonitor.core.database.dao.BatteryRecordDao
import com.cloudorz.openmonitor.core.database.entity.BatteryRecordEntity
import com.cloudorz.openmonitor.core.model.battery.AppUsageEntry
import com.cloudorz.openmonitor.core.model.battery.BatteryChartPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryRecordRepository @Inject constructor(
    private val batteryRecordDao: BatteryRecordDao,
    private val appInfoResolver: AppInfoResolver,
) {
    suspend fun insertRecord(record: BatteryRecordEntity) {
        batteryRecordDao.insert(record)
    }

    fun getRecordsForChart(startMs: Long, endMs: Long): Flow<List<BatteryChartPoint>> {
        return batteryRecordDao.getRecordsInRange(startMs, endMs).map { records ->
            records.map { r ->
                BatteryChartPoint(
                    timestamp = r.timestamp,
                    capacity = r.capacity,
                    currentMa = r.currentMa,
                    powerW = r.powerW,
                    temperatureCelsius = r.temperatureCelsius,
                    isCharging = r.isCharging,
                    packageName = r.packageName,
                )
            }
        }
    }

    suspend fun getRecordsOnce(startMs: Long, endMs: Long): List<BatteryRecordEntity> {
        return batteryRecordDao.getRecordsInRangeOnce(startMs, endMs)
    }

    suspend fun getAppUsageBreakdown(startMs: Long, endMs: Long): List<AppUsageEntry> =
        withContext(Dispatchers.Default) {
            batteryRecordDao.getAppUsageSummary(startMs, endMs).map { summary ->
                AppUsageEntry(
                    packageName = summary.packageName,
                    appLabel = appInfoResolver.resolveLabel(summary.packageName)
                        .ifEmpty { summary.packageName },
                    iconBitmap = appInfoResolver.resolveIcon(summary.packageName),
                    avgPowerW = summary.avgPowerW,
                    avgTemp = summary.avgTemp,
                    maxTemp = summary.maxTemp,
                    durationMs = summary.estimatedDurationMs,
                )
            }
        }

    suspend fun deleteOlderThan(cutoffMs: Long) {
        batteryRecordDao.deleteOlderThan(cutoffMs)
    }

    suspend fun getLatestRecord(): BatteryRecordEntity? {
        return batteryRecordDao.getLatestRecord()
    }
}
