package com.cloudorz.openmonitor.core.data.datasource

import com.cloudorz.openmonitor.core.data.ipc.MonitorClient
import com.cloudorz.openmonitor.core.data.ipc.MonitorSnapshotAdapter
import com.cloudorz.openmonitor.core.model.fps.FpsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FpsDataSource @Inject constructor(
    private val monitorClient: MonitorClient,
) {
    suspend fun getDaemonFps(): FpsData? = withContext(Dispatchers.IO) {
        if (!monitorClient.connected.value) return@withContext null
        val snap = monitorClient.snapshots.replayCache.firstOrNull() ?: return@withContext null
        MonitorSnapshotAdapter.toDomain(snap).fpsData
    }
}
