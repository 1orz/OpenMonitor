package com.cloudorz.openmonitor.core.data.ipc

import com.cloudorz.openmonitor.core.model.monitor.MonitorSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain-model Flow backed by [DaemonClient].
 *
 * Feature repositories depend on this and project out the fields they care
 * about (`.map { it.cpuLoadPercent }` / `.map { it.fpsData }` / ...). The
 * deduplication knob is [distinctUntilChanged] on the feature side -- we
 * don't want this data source to hide real-state updates.
 *
 * Replaces the old `AggregatedMonitorDataSource.collectSnapshot()` polling
 * entry point.
 */
@Singleton
class MonitorDataSource @Inject constructor(
    private val daemonClient: DaemonClient,
) {
    /**
     * Hot stream of the latest privileged-mode snapshots. Emits every ~500 ms
     * (driven by [DaemonClient]'s subscription interval) as long as the server
     * is alive.
     */
    val snapshots: Flow<MonitorSnapshot> = daemonClient.snapshots
        .map(MonitorSnapshotAdapter::toDomain)

    /** Matches server connectivity -- consumers can gate UI to BASIC fallback. */
    val connected: StateFlow<Boolean> = daemonClient.connected

    /**
     * Foreground package reported by the daemon (via [ServerSnapshot.focus.pkg]).
     * Returns an empty string when unknown; consumers should treat blank as null.
     */
    fun foregroundPackage(): Flow<String?> = daemonClient.snapshots
        .map { snap -> snap.focus.pkg.takeIf { it.isNotEmpty() } }

    /**
     * Screen-interactive state reported by the daemon (via [ServerSnapshot.focus.screenOn]).
     */
    fun screenInteractive(): Flow<Boolean> = daemonClient.snapshots
        .map { snap -> snap.focus.screenOn }
}
