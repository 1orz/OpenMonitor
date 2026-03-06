package com.cloudorz.monitor.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.cloudorz.monitor.core.database.entity.PowerStatRecordEntity
import com.cloudorz.monitor.core.database.entity.PowerStatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PowerStatDao {

    @Insert
    suspend fun insertSession(entity: PowerStatSessionEntity): Long

    @Insert
    suspend fun insertRecord(entity: PowerStatRecordEntity)

    @Query("SELECT * FROM power_stat_sessions WHERE sessionId = :id")
    fun getSessionById(id: Long): Flow<PowerStatSessionEntity?>

    @Query("SELECT * FROM power_stat_sessions ORDER BY beginTime DESC")
    fun getAllSessions(): Flow<List<PowerStatSessionEntity>>

    @Query("SELECT * FROM power_stat_records WHERE sessionId = :sessionId ORDER BY startTime ASC")
    fun getRecordsBySession(sessionId: Long): Flow<List<PowerStatRecordEntity>>

    @Query("DELETE FROM power_stat_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query(
        """
        UPDATE power_stat_sessions
        SET endTime = :endTime, usedPercent = :usedPercent, avgPowerW = :avgPowerW
        WHERE sessionId = :sessionId
        """
    )
    suspend fun updateSessionEndTime(
        sessionId: Long,
        endTime: Long,
        usedPercent: Int,
        avgPowerW: Double,
    )

    @Transaction
    @Query("DELETE FROM power_stat_sessions WHERE beginTime < :cutoffTimestamp")
    suspend fun deleteSessionsOlderThan(cutoffTimestamp: Long)

    @Query("SELECT * FROM power_stat_records WHERE sessionId = :sessionId ORDER BY startTime ASC")
    suspend fun getRecordsBySessionOnce(sessionId: Long): List<PowerStatRecordEntity>
}
