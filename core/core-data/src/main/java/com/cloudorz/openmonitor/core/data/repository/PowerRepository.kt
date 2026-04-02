package com.cloudorz.openmonitor.core.data.repository

import com.cloudorz.openmonitor.core.database.dao.PowerStatDao
import com.cloudorz.openmonitor.core.database.entity.PowerStatRecordEntity
import com.cloudorz.openmonitor.core.database.entity.PowerStatSessionEntity
import com.cloudorz.openmonitor.core.model.battery.PowerStatRecord
import com.cloudorz.openmonitor.core.model.battery.PowerStatSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PowerRepository @Inject constructor(
    private val powerStatDao: PowerStatDao,
) {
    fun getAllSessions(): Flow<List<PowerStatSession>> =
        powerStatDao.getAllSessions().map { list ->
            list.map { it.toModel() }
        }

    @Suppress("UNUSED_PARAMETER")
    suspend fun startSession(startCapacity: Int): Long {
        val entity = PowerStatSessionEntity(
            beginTime = System.currentTimeMillis(),
            endTime = 0L,
            usedPercent = 0,
            avgPowerW = 0.0,
        )
        return powerStatDao.insertSession(entity)
    }

    suspend fun insertRecord(
        sessionId: Long,
        capacity: Int,
        powerW: Double,
        temperature: Double,
        isCharging: Boolean,
        isScreenOn: Boolean,
    ) {
        val now = System.currentTimeMillis()
        powerStatDao.insertRecord(
            PowerStatRecordEntity(
                sessionId = sessionId,
                capacity = capacity,
                isCharging = isCharging,
                startTime = now,
                endTime = now,
                isFuzzy = false,
                ioBytes = 0,
                packageName = "",
                isScreenOn = isScreenOn,
                powerW = powerW,
                temperature = temperature,
            )
        )
    }

    fun getRecordsBySession(sessionId: Long): Flow<List<PowerStatRecord>> =
        powerStatDao.getRecordsBySession(sessionId).map { list ->
            list.map { it.toRecordModel() }
        }

    suspend fun endSession(sessionId: Long, usedPercent: Int, avgPowerW: Double) {
        powerStatDao.updateSessionEndTime(
            sessionId = sessionId,
            endTime = System.currentTimeMillis(),
            usedPercent = usedPercent,
            avgPowerW = avgPowerW,
        )
    }

    suspend fun deleteSession(sessionId: Long) {
        powerStatDao.deleteSession(sessionId)
    }

    suspend fun deleteSessionsByIds(ids: List<Long>) {
        powerStatDao.deleteSessionsByIds(ids)
    }

    suspend fun getRecordsBySessionOnce(sessionId: Long): List<PowerStatRecord> =
        powerStatDao.getRecordsBySessionOnce(sessionId).map { it.toRecordModel() }

    suspend fun getActiveSession(): PowerStatSession? =
        powerStatDao.getActiveSession()?.toModel()

    private fun PowerStatSessionEntity.toModel() = PowerStatSession(
        sessionId = sessionId.toString(),
        beginTime = beginTime,
        endTime = endTime,
        usedPercent = usedPercent.toDouble(),
        avgPowerW = avgPowerW,
    )

    private fun PowerStatRecordEntity.toRecordModel() = PowerStatRecord(
        capacity = capacity,
        isCharging = isCharging,
        startTime = startTime,
        endTime = endTime,
        isFuzzy = isFuzzy,
        ioBytes = ioBytes,
        packageName = packageName,
        isScreenOn = isScreenOn,
        powerW = powerW,
        temperature = temperature,
    )
}
