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
    private val daemonDataSource: DaemonDataSource,
    private val monitorClient: MonitorClient,
) {
    /** Returns FPS data, preferring the Rust server over the Go daemon. */
    suspend fun getDaemonFps(): FpsData? = withContext(Dispatchers.IO) {
        // Prefer the new Rust server when connected.
        if (monitorClient.connected.value) {
            val snap = monitorClient.snapshots.replayCache.firstOrNull()
            if (snap != null) {
                return@withContext MonitorSnapshotAdapter.toDomain(snap).fpsData
            }
        }
        // Fall back to old Go daemon.
        if (!daemonDataSource.isAvailable()) return@withContext null
        daemonDataSource.collectSnapshot()?.fpsData
    }
}
