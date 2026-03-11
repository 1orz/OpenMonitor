package com.cloudorz.openmonitor.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.cloudorz.openmonitor.core.database.entity.FpsFrameDataEntity
import com.cloudorz.openmonitor.core.database.entity.FpsSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FpsSessionDao {

    @Insert
    suspend fun insertSession(entity: FpsSessionEntity): Long

    @Insert
    suspend fun insertFrameData(entity: FpsFrameDataEntity)

    @Insert
    suspend fun insertFrameDataBatch(list: List<FpsFrameDataEntity>)

    @Query("SELECT * FROM fps_sessions WHERE sessionId = :id")
    fun getSessionById(id: Long): Flow<FpsSessionEntity?>

    @Query("SELECT * FROM fps_sessions ORDER BY beginTime DESC")
    fun getAllSessions(): Flow<List<FpsSessionEntity>>

    @Query("SELECT * FROM fps_frame_data WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getFrameDataBySession(sessionId: Long): Flow<List<FpsFrameDataEntity>>

    @Query("DELETE FROM fps_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query(
        """
        UPDATE fps_sessions
        SET avgFps = :avgFps, avgPowerW = :avgPowerW, durationSeconds = :durationSeconds
        WHERE sessionId = :sessionId
        """
    )
    suspend fun updateSession(
        sessionId: Long,
        avgFps: Double,
        avgPowerW: Double,
        durationSeconds: Int,
    )

    @Transaction
    @Query("DELETE FROM fps_sessions WHERE beginTime < :cutoffTimestamp")
    suspend fun deleteSessionsOlderThan(cutoffTimestamp: Long)

    @Query("SELECT * FROM fps_frame_data WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getFrameDataBySessionOnce(sessionId: Long): List<FpsFrameDataEntity>

    @Query("UPDATE fps_sessions SET sessionDesc = :desc WHERE sessionId = :sessionId")
    suspend fun updateSessionDesc(sessionId: Long, desc: String)

    @Query("DELETE FROM fps_sessions WHERE sessionId IN (:ids)")
    suspend fun deleteSessionsByIds(ids: List<Long>)
}
