package com.cloudorz.openmonitor.core.data.datasource

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.cloudorz.openmonitor.core.common.PlatformDetector
import com.cloudorz.openmonitor.core.common.PrivilegeMode
import com.cloudorz.openmonitor.core.common.ShellExecutor
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
    private val shellExecutor: ShellExecutor,
    private val platformDetector: PlatformDetector,
    private val daemonDataSource: DaemonDataSource,
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "AggregatedMonitor"
    }

    // CPU cross-cycle delta state (BASIC mode)
    @Volatile private var prevProcStat: LongArray? = null

    // Cached GPU load sysfs path (BASIC mode)
    @Volatile private var gpuLoadPath: String? = null
    @Volatile private var gpuLoadPathResolved = false

    private val batteryManager: BatteryManager by lazy {
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }

    /** Last successful daemon snapshot — returned on transient failures to avoid UI flicker. */
    @Volatile private var lastDaemonSnapshot: MonitorSnapshot? = null

    suspend fun collectSnapshot(): MonitorSnapshot = when (shellExecutor.mode) {
        PrivilegeMode.ROOT,
        PrivilegeMode.ADB,
        PrivilegeMode.SHIZUKU -> collectFromDaemon()
        PrivilegeMode.BASIC   -> collectBasic()
    }

    /**
     * ROOT / SHIZUKU / ADB: daemon is the sole data source.
     * Android API supplements battery current (BatteryManager).
     * On transient daemon failure, returns last cached snapshot to avoid UI flicker.
     */
    private suspend fun collectFromDaemon(): MonitorSnapshot = withContext(Dispatchers.IO) {
        if (daemonDataSource.isAvailable()) {
            val snap = daemonDataSource.collectSnapshot()
            if (snap != null) {
                val rawUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                val currentMa = if (rawUa != Int.MIN_VALUE) {
                    val ma = MonitorParser.normalizeCurrentToMa(rawUa.toLong())
                    val status = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                    MonitorParser.ensureCurrentSign(ma, isCharging)
                } else 0
                val result = snap.copy(batteryCurrentMa = currentMa)
                lastDaemonSnapshot = result
                return@withContext result
            }
            Log.e(TAG, "collectFromDaemon: snapshot null")
        } else {
            Log.e(TAG, "collectFromDaemon: daemon NOT available")
        }

        // Daemon dead (3+ consecutive failures) — kill residual, DaemonManager will restart
        if (daemonDataSource.isDead()) {
            shellExecutor.execute("killall monitor-daemon 2>/dev/null || true")
            daemonDataSource.resetDeadState()
            Log.e(TAG, "collectFromDaemon: daemon dead, killed residual")
        }

        // Transient failure → return last cached; never connected → empty default
        lastDaemonSnapshot ?: MonitorSnapshot()
    }

    // ---- BASIC mode fallback ----

    private suspend fun collectBasic(): MonitorSnapshot = withContext(Dispatchers.IO) {
        val cpuLoad = try {
            parseCpuLoad(File("/proc/stat").readText())
        } catch (e: Exception) {
            Log.d(TAG, "collectBasic: read /proc/stat failed", e)
            0.0
        }

        val gpuLoad = try {
            resolveGpuLoadPath()
            val path = gpuLoadPath
            if (path != null) {
                File(path).readText().replace("%", "").trim().toDoubleOrNull() ?: 0.0
            } else 0.0
        } catch (e: Exception) {
            Log.d(TAG, "collectBasic: read GPU load failed", e)
            0.0
        }

        val temp = getBatteryTempFromApi()
        val currentMa = getBatteryCurrentFromApi()

        MonitorSnapshot(
            cpuLoadPercent = cpuLoad,
            gpuLoadPercent = gpuLoad,
            cpuTempCelsius = temp,
            batteryCurrentMa = currentMa,
            fpsData = null,
        )
    }

    // ---- CPU load (BASIC mode, cross-cycle delta) ----

    private fun parseCpuLoad(procStatText: String): Double {
        val (load, newValues) = MonitorParser.parseCpuLoad(procStatText, prevProcStat)
        prevProcStat = newValues
        return load
    }

    // ---- Lazy resolvers ----

    private fun resolveGpuLoadPath() {
        if (gpuLoadPathResolved) return
        gpuLoadPath = when (platformDetector.gpuType) {
            PlatformDetector.GpuType.ADRENO -> "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage"
            PlatformDetector.GpuType.MALI -> "/sys/class/misc/mali0/device/utilization"
            else -> null
        }
        gpuLoadPathResolved = true
    }

    // ---- Android API fallbacks ----

    @Suppress("UnspecifiedRegisterReceiverFlag")
    private fun getBatteryCurrentFromApi(): Int {
        val status = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        // BatteryManager API
        val currentUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (currentUa != Int.MIN_VALUE && currentUa != 0) {
            val ma = MonitorParser.normalizeCurrentToMa(currentUa.toLong())
            return MonitorParser.ensureCurrentSign(ma, isCharging)
        }
        // Intent broadcast fallback
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val raw = intent?.getIntExtra("current_now", 0) ?: 0
            if (raw != 0) {
                val ma = MonitorParser.normalizeCurrentToMa(raw.toLong())
                MonitorParser.ensureCurrentSign(ma, isCharging)
            } else 0
        } catch (e: Exception) {
            Log.d(TAG, "getBatteryCurrentFromIntent failed", e)
            0
        }
    }

    @Suppress("UnspecifiedRegisterReceiverFlag")
    private fun getBatteryTempFromApi(): Double {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tempRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            tempRaw / 10.0
        } catch (e: Exception) {
            Log.d(TAG, "getBatteryTempFromApi failed", e)
            0.0
        }
    }
}
