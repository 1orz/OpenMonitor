package com.cloudorz.openmonitor.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cloudorz.openmonitor.core.database.entity.BatteryRecordEntity
import kotlinx.coroutines.flow.Flow

data class AppUsageSummary(
    val packageName: String,
    val recordCount: Int,
    val avgPowerW: Double,
    val avgTemp: Double,
    val maxTemp: Double,
    val firstSeen: Long,
    val lastSeen: Long,
) {
    val estimatedDurationMs: Long get() = recordCount * 30_000L
}

@Dao
interface BatteryRecordDao {

    @Insert
    suspend fun insert(record: BatteryRecordEntity)

    @Query("SELECT * FROM battery_records WHERE timestamp BETWEEN :startMs AND :endMs ORDER BY timestamp ASC")
    fun getRecordsInRange(startMs: Long, endMs: Long): Flow<List<BatteryRecordEntity>>

    @Query("SELECT * FROM battery_records WHERE timestamp BETWEEN :startMs AND :endMs ORDER BY timestamp ASC")
    suspend fun getRecordsInRangeOnce(startMs: Long, endMs: Long): List<BatteryRecordEntity>

    @Query(
        """
        SELECT packageName,
               COUNT(*) as recordCount,
               AVG(powerW) as avgPowerW,
               AVG(temperatureCelsius) as avgTemp,
               MAX(temperatureCelsius) as maxTemp,
               MIN(timestamp) as firstSeen,
               MAX(timestamp) as lastSeen
        FROM battery_records
        WHERE timestamp BETWEEN :startMs AND :endMs
          AND packageName != ''
        GROUP BY packageName
        ORDER BY recordCount DESC
        """,
    )
    suspend fun getAppUsageSummary(startMs: Long, endMs: Long): List<AppUsageSummary>

    @Query("DELETE FROM battery_records WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long)

    @Query("SELECT * FROM battery_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestRecord(): BatteryRecordEntity?

    @Query("SELECT DISTINCT packageName FROM battery_records WHERE timestamp BETWEEN :startMs AND :endMs AND packageName != ''")
    suspend fun getDistinctPackages(startMs: Long, endMs: Long): List<String>
}
