package com.cloudorz.openmonitor.core.data.ipc

import com.cloudorz.openmonitor.core.model.monitor.MonitorSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain-model Flow backed by [MonitorClient].
 *
 * Feature repositories depend on this and project out the fields they care
 * about (`.map { it.cpuLoadPercent }` / `.map { it.fpsData }` / …). The
 * deduplication knob is [distinctUntilChanged] on the feature side — we
 * don't want this data source to hide real-state updates.
 *
 * Replaces the old `AggregatedMonitorDataSource.collectSnapshot()` polling
 * entry point.
 */
@Singleton
class MonitorDataSource @Inject constructor(
    private val monitorClient: MonitorClient,
) {
    /**
     * Hot stream of the latest privileged-mode snapshots. Emits every ~100 ms
     * (driven by [MonitorClient]'s reader loop) as long as the server is
     * alive.
     */
    val snapshots: Flow<MonitorSnapshot> = monitorClient.snapshots
        .map(MonitorSnapshotAdapter::toDomain)

    /** Matches server connectivity — consumers can gate UI to BASIC fallback. */
    val connected: StateFlow<Boolean> = monitorClient.connected

    fun foregroundPackage(): StateFlow<String?> = monitorClient.focusedPackage
    fun screenInteractive(): StateFlow<Boolean> = monitorClient.screenInteractive
}
