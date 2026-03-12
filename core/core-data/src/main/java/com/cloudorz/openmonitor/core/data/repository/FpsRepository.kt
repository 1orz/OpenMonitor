package com.cloudorz.openmonitor.core.data.repository

import com.cloudorz.openmonitor.core.data.datasource.FpsDataSource
import com.cloudorz.openmonitor.core.data.pollingFlow
import com.cloudorz.openmonitor.core.database.dao.FpsSessionDao
import com.cloudorz.openmonitor.core.database.entity.FpsFrameDataEntity
import com.cloudorz.openmonitor.core.database.entity.FpsSessionEntity
import com.cloudorz.openmonitor.core.model.fps.FpsData
import com.cloudorz.openmonitor.core.model.fps.FpsFrameRecord
import com.cloudorz.openmonitor.core.model.fps.FpsWatchSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FpsRepository @Inject constructor(
    private val fpsDataSource: FpsDataSource,
    private val fpsSessionDao: FpsSessionDao
) {
    fun observeFps(
        intervalMs: Long = 1000L,
    ): Flow<FpsData?> = pollingFlow(intervalMs) {
        fpsDataSource.getDaemonFps()
    }

    suspend fun startSession(packageName: String, appName: String, mode: String = ""): Long {
        return fpsSessionDao.insertSession(
            FpsSessionEntity(
                packageName = packageName,
                appName = appName,
                avgFps = 0.0,
                avgPowerW = 0.0,
                beginTime = System.currentTimeMillis(),
                durationSeconds = 0,
                mode = mode,
                packageVersion = "",
                sessionDesc = "",
                viewSize = ""
            )
        )
    }

    suspend fun recordFrame(sessionId: Long, fpsData: FpsData) {
        fpsSessionDao.insertFrameData(
            FpsFrameDataEntity(
                sessionId = sessionId,
                timestamp = System.currentTimeMillis(),
                fps = fpsData.fps,
                jankCount = fpsData.jankCount,
                bigJankCount = fpsData.bigJankCount,
                maxFrameTimeMs = fpsData.maxFrameTimeMs,
                frameTimesJson = fpsData.frameTimesMs.joinToString(",")
            )
        )
    }

    suspend fun recordFrameRich(
        sessionId: Long,
        fpsData: FpsData,
        cpuLoad: Double,
        cpuTemp: Double,
        gpuLoad: Double,
        gpuFreqMhz: Int,
        batteryCapacity: Int,
        batteryCurrentMa: Int,
        batteryTemp: Double,
        powerW: Double,
        cpuCoreLoads: List<Double>,
        cpuCoreFreqs: List<Long>,
    ) {
        fpsSessionDao.insertFrameData(
            FpsFrameDataEntity(
                sessionId = sessionId,
                timestamp = System.currentTimeMillis(),
                fps = fpsData.fps,
                jankCount = fpsData.jankCount,
                bigJankCount = fpsData.bigJankCount,
                maxFrameTimeMs = fpsData.maxFrameTimeMs,
                frameTimesJson = fpsData.frameTimesMs.joinToString(","),
                cpuLoad = cpuLoad,
                cpuTemp = cpuTemp,
                gpuLoad = gpuLoad,
                gpuFreqMhz = gpuFreqMhz,
                batteryCapacity = batteryCapacity,
                batteryCurrentMa = batteryCurrentMa,
                batteryTemp = batteryTemp,
                powerW = powerW,
                cpuCoreLoadsJson = cpuCoreLoads.joinToString(",") { "%.1f".format(it) },
                cpuCoreFreqsJson = cpuCoreFreqs.joinToString(","),
            )
        )
    }

    suspend fun endSession(sessionId: Long, avgFps: Double, avgPowerW: Double, durationSeconds: Int) {
        fpsSessionDao.updateSession(sessionId, avgFps, avgPowerW, durationSeconds)
    }

    fun getAllSessions(): Flow<List<FpsWatchSession>> =
        fpsSessionDao.getAllSessions().map { entities ->
            entities.map { it.toModel() }
        }

    fun getSessionById(sessionId: Long): Flow<FpsWatchSession?> =
        fpsSessionDao.getSessionById(sessionId).map { it?.toModel() }

    fun getSessionFrames(sessionId: Long): Flow<List<FpsFrameRecord>> =
        fpsSessionDao.getFrameDataBySession(sessionId).map { entities ->
            entities.map { it.toRecord() }
        }

    suspend fun getSessionFramesOnce(sessionId: Long): List<FpsFrameRecord> =
        fpsSessionDao.getFrameDataBySessionOnce(sessionId).map { it.toRecord() }

    suspend fun updateSessionAppInfo(sessionId: Long, packageName: String, appName: String) =
        fpsSessionDao.updateSessionAppInfo(sessionId, packageName, appName)

    suspend fun deleteSession(sessionId: Long) = fpsSessionDao.deleteSession(sessionId)

    suspend fun renameSession(sessionId: Long, desc: String) =
        fpsSessionDao.updateSessionDesc(sessionId, desc)

    suspend fun deleteSessionsByIds(ids: List<Long>) =
        fpsSessionDao.deleteSessionsByIds(ids)

    private fun FpsSessionEntity.toModel() = FpsWatchSession(
        sessionId = sessionId.toString(),
        packageName = packageName,
        appName = appName,
        avgFps = avgFps,
        avgPowerW = avgPowerW,
        beginTime = beginTime,
        durationSeconds = durationSeconds.toLong(),
        mode = mode,
        packageVersion = packageVersion,
        sessionDesc = sessionDesc,
        viewSize = viewSize
    )

    private fun FpsFrameDataEntity.toRecord() = FpsFrameRecord(
        timestamp = timestamp,
        fps = fps,
        jankCount = jankCount,
        bigJankCount = bigJankCount,
        maxFrameTimeMs = maxFrameTimeMs,
        frameTimesMs = if (frameTimesJson.isNotEmpty()) {
            frameTimesJson.split(",").mapNotNull { it.trim().toIntOrNull() }
        } else emptyList(),
        cpuLoad = cpuLoad,
        cpuTemp = cpuTemp,
        gpuLoad = gpuLoad,
        gpuFreqMhz = gpuFreqMhz,
        batteryCapacity = batteryCapacity,
        batteryCurrentMa = batteryCurrentMa,
        batteryTemp = batteryTemp,
        powerW = powerW,
        cpuCoreLoads = if (cpuCoreLoadsJson.isNotEmpty()) {
            cpuCoreLoadsJson.split(",").mapNotNull { it.trim().toDoubleOrNull() }
        } else emptyList(),
        cpuCoreFreqsMhz = if (cpuCoreFreqsJson.isNotEmpty()) {
            cpuCoreFreqsJson.split(",").mapNotNull { it.trim().toLongOrNull() }
        } else emptyList(),
    )
}
