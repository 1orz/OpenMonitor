package com.cloudorz.openmonitor.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.cloudorz.openmonitor.core.database.entity.ChargeStatRecordEntity
import com.cloudorz.openmonitor.core.database.entity.ChargeStatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargeStatDao {

    @Insert
    suspend fun insertSession(entity: ChargeStatSessionEntity): Long

    @Insert
    suspend fun insertRecord(entity: ChargeStatRecordEntity)

    @Query("SELECT * FROM charge_stat_sessions WHERE sessionId = :id")
    fun getSessionById(id: Long): Flow<ChargeStatSessionEntity?>

    @Query("SELECT * FROM charge_stat_sessions ORDER BY beginTime DESC")
    fun getAllSessions(): Flow<List<ChargeStatSessionEntity>>

    @Query("SELECT * FROM charge_stat_records WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getRecordsBySession(sessionId: Long): Flow<List<ChargeStatRecordEntity>>

    @Query("DELETE FROM charge_stat_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query(
        """
        UPDATE charge_stat_sessions
        SET endTime = :endTime, capacityRatio = :capacityRatio, capacityWh = :capacityWh
        WHERE sessionId = :sessionId
        """
    )
    suspend fun updateSession(
        sessionId: Long,
        endTime: Long,
        capacityRatio: Int,
        capacityWh: Double,
    )

    @Transaction
    @Query("DELETE FROM charge_stat_sessions WHERE beginTime < :cutoffTimestamp")
    suspend fun deleteSessionsOlderThan(cutoffTimestamp: Long)

    @Query("SELECT * FROM charge_stat_records WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getRecordsBySessionOnce(sessionId: Long): List<ChargeStatRecordEntity>
}
