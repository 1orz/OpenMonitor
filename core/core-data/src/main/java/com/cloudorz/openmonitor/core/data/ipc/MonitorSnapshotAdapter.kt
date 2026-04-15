package com.cloudorz.openmonitor.core.data.ipc

import com.cloudorz.openmonitor.core.model.fps.FpsData
import com.cloudorz.openmonitor.core.model.monitor.MonitorSnapshot
import com.cloudorz.openmonitor.server.ServerSnapshot

/**
 * Translates the server's [ServerSnapshot] (JSON wire format) into the domain
 * [MonitorSnapshot] expected by feature Repositories.
 *
 * We keep the domain model fields (nullable, richer names) separate from the
 * wire format so the server can evolve without churning feature code. Missing
 * sentinel values (`-1` in the snapshot) map to `null` domain fields.
 */
object MonitorSnapshotAdapter {

    fun toDomain(snap: ServerSnapshot): MonitorSnapshot {
        val coreLoads = snap.cpu.load.takeWhile { it >= 0 }.map { it.toDouble() }
        val coreFreqs = snap.cpu.freq.takeWhile { it >= 0 }.map { it }

        val cpuAvg: Double? = if (coreLoads.isEmpty()) null else coreLoads.average()

        return MonitorSnapshot(
            cpuLoadPercent = cpuAvg,
            cpuCoreLoads = coreLoads.takeIf { it.isNotEmpty() },
            cpuCoreFreqs = coreFreqs.takeIf { it.isNotEmpty() },
            gpuLoadPercent = snap.gpu.load.takeIf { it >= 0 }?.toDouble(),
            gpuFreqMhz = snap.gpu.freq.takeIf { it >= 0 },
            cpuTempCelsius = snap.cpu.tempX10.takeIf { it >= 0 }?.let { it / 10.0 },
            batteryCurrentMa = snap.batt.currentMa.takeIf { it != 0 || snap.batt.status != 0 },
            batteryCurrentUa = (snap.batt.currentMa.takeIf { it != 0 }?.toLong() ?: 0L)
                .times(1000L)
                .toInt()
                .takeIf { it != 0 },
            batteryVoltageUv = snap.batt.voltageMv.takeIf { it > 0 }?.let { it * 1000 },
            ddrFreqMbps = snap.mem.ddrMbps.takeIf { it >= 0 },
            fpsData = buildFpsData(snap),
            daemonRunner = "rust-server",
            timestamp = snap.tsNs / 1_000_000L,
        )
    }

    private fun buildFpsData(snap: ServerSnapshot): FpsData? {
        val fps = snap.fps.x100.takeIf { it >= 0 }?.let { it / 100.0 } ?: return null
        return FpsData(
            fps = fps,
            jankCount = snap.fps.jank,
            bigJankCount = snap.fps.bigJank,
            window = snap.fps.layer,
        )
    }
}
