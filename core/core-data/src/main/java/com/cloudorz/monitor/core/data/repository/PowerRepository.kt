package com.cloudorz.monitor.core.data.repository

import com.cloudorz.monitor.core.database.dao.PowerStatDao
import com.cloudorz.monitor.core.database.entity.PowerStatSessionEntity
import com.cloudorz.monitor.core.model.battery.PowerStatSession
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

    suspend fun startSession(startCapacity: Int): Long {
        val entity = PowerStatSessionEntity(
            beginTime = System.currentTimeMillis(),
            endTime = 0L,
            usedPercent = startCapacity,
            avgPowerW = 0.0,
        )
        return powerStatDao.insertSession(entity)
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

    private fun PowerStatSessionEntity.toModel() = PowerStatSession(
        sessionId = sessionId.toString(),
        beginTime = beginTime,
        endTime = endTime,
        usedPercent = usedPercent.toDouble(),
        avgPowerW = avgPowerW,
    )
}
