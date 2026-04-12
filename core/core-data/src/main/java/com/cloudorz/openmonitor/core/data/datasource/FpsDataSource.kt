package com.cloudorz.openmonitor.core.data.datasource

import com.cloudorz.openmonitor.core.model.fps.FpsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FpsDataSource @Inject constructor(
    private val daemonDataSource: DaemonDataSource,
) {
    /** Returns FPS data from the Go daemon, or null if unavailable. */
    suspend fun getDaemonFps(): FpsData? = withContext(Dispatchers.IO) {
        if (!daemonDataSource.isAvailable()) return@withContext null
        daemonDataSource.collectSnapshot()?.fpsData
    }
}
