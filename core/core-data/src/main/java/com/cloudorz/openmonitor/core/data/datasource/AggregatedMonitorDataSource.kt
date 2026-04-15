package com.cloudorz.openmonitor.core.data.datasource

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.cloudorz.openmonitor.core.common.PlatformDetector
import com.cloudorz.openmonitor.core.data.ipc.DaemonClient
import com.cloudorz.openmonitor.core.data.ipc.MonitorSnapshotAdapter
import com.cloudorz.openmonitor.core.data.util.MonitorParser
import com.cloudorz.openmonitor.core.model.monitor.MonitorSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AggregatedMonitorDataSource @Inject constructor(
    private val platformDetector: PlatformDetector,
    private val thermalDataSource: ThermalDataSource,
    private val daemonClient: DaemonClient,
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "AggregatedMonitor"
    }

    @Volatile private var prevProcStat: LongArray? = null

    @Volatile private var gpuLoadPath: String? = null
    @Volatile private var gpuLoadPathResolved = false

    private val batteryManager: BatteryManager by lazy {
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }

    @Volatile private var lastSnapshot: MonitorSnapshot? = null

    suspend fun collectSnapshot(): MonitorSnapshot {
        if (daemonClient.connected.value) {
            val snap = daemonClient.snapshots.replayCache.firstOrNull()
            if (snap != null) {
                val result = MonitorSnapshotAdapter.toDomain(snap)
                val enriched = if (result.batteryCurrentMa == null || result.batteryCurrentMa == 0) {
                    result.copy(batteryCurrentMa = getBatteryCurrentFromApi())
                } else result
                lastSnapshot = enriched
                return enriched
            }
        }

        return collectBasic()
    }

    private suspend fun collectBasic(): MonitorSnapshot = withContext(Dispatchers.IO) {
        val cpuLoad: Double? = try {
            parseCpuLoad(File("/proc/stat").readText())
        } catch (e: Exception) {
            Log.d(TAG, "collectBasic: read /proc/stat failed", e)
            null
        }

        val gpuLoad: Double? = try {
            resolveGpuLoadPath()
            val path = gpuLoadPath
            if (path != null) {
                File(path).readText().replace("%", "").trim().toDoubleOrNull()
            } else null
        } catch (e: Exception) {
            Log.d(TAG, "collectBasic: read GPU load failed", e)
            null
        }

        val cpuTemp = try { thermalDataSource.getCpuTemperature() } catch (_: Exception) { null }
        val currentMa = getBatteryCurrentFromApi()

        MonitorSnapshot(
            cpuLoadPercent = cpuLoad,
            gpuLoadPercent = gpuLoad,
            cpuTempCelsius = cpuTemp,
            batteryCurrentMa = currentMa,
            fpsData = null,
        )
    }

    private fun parseCpuLoad(procStatText: String): Double {
        val (load, newValues) = MonitorParser.parseCpuLoad(procStatText, prevProcStat)
        prevProcStat = newValues
        return load
    }

    private fun resolveGpuLoadPath() {
        if (gpuLoadPathResolved) return
        gpuLoadPath = when (platformDetector.gpuType) {
            PlatformDetector.GpuType.ADRENO -> "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"
            PlatformDetector.GpuType.MALI -> "/sys/class/misc/mali0/device/utilization"
            else -> null
        }
        gpuLoadPathResolved = true
    }

    @Suppress("UnspecifiedRegisterReceiverFlag")
    private fun getBatteryCurrentFromApi(): Int? {
        val status = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val currentUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (currentUa != Int.MIN_VALUE && currentUa != 0) {
            val ma = MonitorParser.normalizeCurrentToMa(currentUa.toLong())
            return MonitorParser.ensureCurrentSign(ma, isCharging)
        }
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val raw = intent?.getIntExtra("current_now", 0) ?: 0
            if (raw != 0) {
                val ma = MonitorParser.normalizeCurrentToMa(raw.toLong())
                MonitorParser.ensureCurrentSign(ma, isCharging)
            } else null
        } catch (e: Exception) {
            Log.d(TAG, "getBatteryCurrentFromIntent failed", e)
            null
        }
    }

    @Suppress("UnspecifiedRegisterReceiverFlag")
    private fun getBatteryTempFromApi(): Double? {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tempRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            if (tempRaw != 0) tempRaw / 10.0 else null
        } catch (e: Exception) {
            Log.d(TAG, "getBatteryTempFromApi failed", e)
            null
        }
    }
}
