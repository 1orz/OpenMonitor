package com.cloudorz.monitor.core.data.repository

import com.cloudorz.monitor.core.database.dao.ChargeStatDao
import com.cloudorz.monitor.core.database.entity.ChargeStatRecordEntity
import com.cloudorz.monitor.core.database.entity.ChargeStatSessionEntity
import com.cloudorz.monitor.core.model.battery.ChargeStatRecord
import com.cloudorz.monitor.core.model.battery.ChargeStatSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChargeRepository @Inject constructor(
    private val chargeStatDao: ChargeStatDao,
) {
    fun getAllSessions(): Flow<List<ChargeStatSession>> =
        chargeStatDao.getAllSessions().map { list ->
            list.map { it.toModel() }
        }

    suspend fun startSession(beginCapacity: Int): Long {
        val entity = ChargeStatSessionEntity(
            beginTime = System.currentTimeMillis(),
            endTime = 0L,
            capacityRatio = beginCapacity,
            capacityWh = 0.0,
        )
        return chargeStatDao.insertSession(entity)
    }

    suspend fun endSession(sessionId: Long, capacityRatio: Int, capacityWh: Double) {
        chargeStatDao.updateSession(
            sessionId = sessionId,
            endTime = System.currentTimeMillis(),
            capacityRatio = capacityRatio,
            capacityWh = capacityWh,
        )
    }

    suspend fun insertRecord(
        sessionId: Long,
        capacity: Int,
        currentMa: Long,
        temperature: Float,
        powerW: Double,
    ) {
        chargeStatDao.insertRecord(
            ChargeStatRecordEntity(
                sessionId = sessionId,
                capacity = capacity,
                currentMa = currentMa,
                temperature = temperature,
                powerW = powerW,
                timestamp = System.currentTimeMillis(),
            )
        )
    }

    fun getRecordsBySession(sessionId: Long): Flow<List<ChargeStatRecord>> =
        chargeStatDao.getRecordsBySession(sessionId).map { list ->
            list.map { it.toModel() }
        }

    suspend fun deleteSession(sessionId: Long) {
        chargeStatDao.deleteSession(sessionId)
    }

    private fun ChargeStatSessionEntity.toModel() = ChargeStatSession(
        sessionId = sessionId.toString(),
        beginTime = beginTime,
        endTime = endTime,
        capacityRatio = capacityRatio.toDouble(),
        capacityWh = capacityWh,
    )

    private fun ChargeStatRecordEntity.toModel() = ChargeStatRecord(
        capacity = capacity,
        currentMa = currentMa.toInt(),
        temperatureCelsius = temperature.toDouble(),
        powerW = powerW,
        timestamp = timestamp,
    )
}
