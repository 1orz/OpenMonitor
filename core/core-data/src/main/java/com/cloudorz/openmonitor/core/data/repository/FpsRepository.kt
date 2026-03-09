package com.cloudorz.openmonitor.core.data.repository

import com.cloudorz.openmonitor.core.data.datasource.FpsDataSource
import com.cloudorz.openmonitor.core.data.pollingFlow
import com.cloudorz.openmonitor.core.database.dao.FpsSessionDao
import com.cloudorz.openmonitor.core.database.entity.FpsFrameDataEntity
import com.cloudorz.openmonitor.core.database.entity.FpsSessionEntity
import com.cloudorz.openmonitor.core.model.fps.FpsData
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

    suspend fun endSession(sessionId: Long, avgFps: Double, avgPowerW: Double, durationSeconds: Int) {
        fpsSessionDao.updateSession(sessionId, avgFps, avgPowerW, durationSeconds)
    }

    fun getAllSessions(): Flow<List<FpsWatchSession>> =
        fpsSessionDao.getAllSessions().map { entities ->
            entities.map { it.toModel() }
        }

    suspend fun deleteSession(sessionId: Long) = fpsSessionDao.deleteSession(sessionId)

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
}
