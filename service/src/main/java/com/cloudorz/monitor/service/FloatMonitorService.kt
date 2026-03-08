package com.cloudorz.monitor.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.cloudorz.monitor.core.common.PermissionManager
import com.cloudorz.monitor.core.common.PrivilegeMode
import com.cloudorz.monitor.core.data.datasource.FpsDataSource
import com.cloudorz.monitor.core.data.datasource.AggregatedMonitorDataSource
import com.cloudorz.monitor.core.data.datasource.DaemonDataSource
import com.cloudorz.monitor.core.data.datasource.DaemonLauncher
import com.cloudorz.monitor.core.data.datasource.DaemonManager
import com.cloudorz.monitor.core.data.datasource.BatteryDataSource
import com.cloudorz.monitor.core.data.datasource.CpuDataSource
import com.cloudorz.monitor.core.data.datasource.GpuDataSource
import com.cloudorz.monitor.core.data.datasource.ProcessDataSource
import com.cloudorz.monitor.core.data.datasource.ThermalDataSource
import com.cloudorz.monitor.core.model.process.ProcessInfo
import com.cloudorz.monitor.core.model.process.ThreadInfo
import com.cloudorz.monitor.core.model.thermal.ThermalZone
import android.view.WindowManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FloatMonitorService : LifecycleService() {

    companion object {
        private const val TAG = "FloatMonitorService"
        const val CHANNEL_ID = "float_monitor"
        const val NOTIFICATION_ID = 1002

        const val ACTION_START = "com.cloudorz.monitor.service.FLOAT_START"
        const val ACTION_STOP = "com.cloudorz.monitor.service.FLOAT_STOP"
        const val ACTION_ADD_MONITOR = "com.cloudorz.monitor.service.FLOAT_ADD"
        const val ACTION_REMOVE_MONITOR = "com.cloudorz.monitor.service.FLOAT_REMOVE"
        const val ACTION_UPDATE_FPS_SETTINGS = "com.cloudorz.monitor.service.UPDATE_FPS"
        const val EXTRA_MONITOR_TYPE = "monitor_type"

        const val TYPE_LOAD = "LOAD_MONITOR"
        const val TYPE_MINI = "MINI_MONITOR"
        const val TYPE_FPS = "FPS_RECORDER"
        const val TYPE_TEMPERATURE = "TEMPERATURE_MONITOR"
        const val TYPE_PROCESS = "PROCESS_MONITOR"
        const val TYPE_THREAD = "THREAD_MONITOR"

        private const val PREFS_NAME = "monitor_settings"
        private const val KEY_ENABLED_MONITORS = "enabled_monitors"
        private const val KEY_SERVICE_ACTIVE = "float_service_active"
        const val KEY_FPS_INTERVAL = "fps_interval"
        const val DEFAULT_FPS_INTERVAL = 500L

        fun startIntent(context: Context): Intent =
            Intent(context, FloatMonitorService::class.java).apply { action = ACTION_START }

        fun stopIntent(context: Context): Intent =
            Intent(context, FloatMonitorService::class.java).apply { action = ACTION_STOP }

        fun addMonitorIntent(context: Context, monitorType: String): Intent =
            Intent(context, FloatMonitorService::class.java).apply {
                action = ACTION_ADD_MONITOR
                putExtra(EXTRA_MONITOR_TYPE, monitorType)
            }

        fun removeMonitorIntent(context: Context, monitorType: String): Intent =
            Intent(context, FloatMonitorService::class.java).apply {
                action = ACTION_REMOVE_MONITOR
                putExtra(EXTRA_MONITOR_TYPE, monitorType)
            }

        fun updateFpsSettingsIntent(context: Context): Intent =
            Intent(context, FloatMonitorService::class.java).apply {
                action = ACTION_UPDATE_FPS_SETTINGS
            }
    }

    @Inject lateinit var cpuDataSource: CpuDataSource
    @Inject lateinit var gpuDataSource: GpuDataSource
    @Inject lateinit var batteryDataSource: BatteryDataSource
    @Inject lateinit var thermalDataSource: ThermalDataSource
    @Inject lateinit var fpsDataSource: FpsDataSource
    @Inject lateinit var processDataSource: ProcessDataSource
    @Inject lateinit var permissionManager: PermissionManager
    @Inject lateinit var aggregatedMonitorDataSource: AggregatedMonitorDataSource
    @Inject lateinit var daemonLauncher: DaemonLauncher
    @Inject lateinit var daemonDataSource: DaemonDataSource
    @Inject lateinit var daemonManager: DaemonManager

    private lateinit var floatWindowManager: FloatWindowManager
    private val dataCollectionJobs = mutableMapOf<String, Job>()

    // Shared FPS collection: single writer to currentFps
    private var sharedFpsJob: Job? = null

    // Shared data states for composables
    val cpuLoad = MutableStateFlow(0.0)
    val gpuLoad = MutableStateFlow(0.0)
    val memUsed = MutableStateFlow(0.0)
    val batteryLevel = MutableStateFlow(0)
    val cpuTemp = MutableStateFlow(0.0)
    val currentFps = MutableStateFlow(0.0)
    val currentJank = MutableStateFlow(0)
    val thermalZones = MutableStateFlow<List<ThermalZone>>(emptyList())
    val topProcesses = MutableStateFlow<List<ProcessInfo>>(emptyList())
    val topThreads = MutableStateFlow<List<ThreadInfo>>(emptyList())
    val foregroundApp = MutableStateFlow("")
    val currentMa = MutableStateFlow(0)
    val cpuCoreLoads = MutableStateFlow<List<Double>>(emptyList())
    val batteryTemp = MutableStateFlow(0.0)
    val hasShellAccess = MutableStateFlow(false)
    val hasRootAccess = MutableStateFlow(false)
    val fpsInteracting = MutableStateFlow(false)
    private var fpsInteractionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val windowContext = AccessibilityMonitorService.instance ?: this
        floatWindowManager = FloatWindowManager(windowContext)
        createNotificationChannel()
        updateShellAccess()
    }

    private fun updateShellAccess() {
        val mode = permissionManager.currentMode.value
        hasShellAccess.value = mode == PrivilegeMode.ROOT ||
            mode == PrivilegeMode.ADB ||
            mode == PrivilegeMode.SHIZUKU
        hasRootAccess.value = mode == PrivilegeMode.ROOT
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> startFloatService()
            ACTION_STOP -> stopFloatService()
            ACTION_ADD_MONITOR -> {
                val type = intent.getStringExtra(EXTRA_MONITOR_TYPE) ?: return START_STICKY
                addMonitor(type)
            }
            ACTION_REMOVE_MONITOR -> {
                val type = intent.getStringExtra(EXTRA_MONITOR_TYPE) ?: return START_STICKY
                removeMonitor(type)
            }
            ACTION_UPDATE_FPS_SETTINGS -> {
                restartSharedFpsJob()
            }
        }

        return START_STICKY
    }

    private fun startFloatService() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putBoolean(KEY_SERVICE_ACTIVE, true).apply()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.float_notification_title))
            .setContentText(getString(R.string.float_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun stopFloatService() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SERVICE_ACTIVE, false)
            .putStringSet(KEY_ENABLED_MONITORS, emptySet())
            .apply()

        sharedFpsJob?.cancel()
        sharedFpsJob = null
        dataCollectionJobs.values.forEach { it.cancel() }
        dataCollectionJobs.clear()
        floatWindowManager.removeAllWindows()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun addMonitor(type: String) {
        if (floatWindowManager.isWindowActive(type)) return

        // Start shared FPS collection for FPS-consuming monitors
        if (type == TYPE_FPS || type == TYPE_MINI) {
            ensureSharedFpsJob()
        }

        // Start data collection (TYPE_FPS has no per-type job, FPS is handled by shared job)
        if (type != TYPE_FPS) {
            val job = lifecycleScope.launch {
                collectDataForType(type)
            }
            dataCollectionJobs[type] = job
        }

        // Create floating window
        val service = this
        when (type) {
            TYPE_LOAD -> {
                floatWindowManager.addWindow(
                    id = type,
                    width = WindowManager.LayoutParams.WRAP_CONTENT,
                    height = WindowManager.LayoutParams.WRAP_CONTENT,
                    y = 200,
                ) {
                    FloatLoadMonitorContent(service)
                }
            }
            TYPE_MINI -> {
                floatWindowManager.addWindow(
                    id = type,
                    width = WindowManager.LayoutParams.WRAP_CONTENT,
                    height = WindowManager.LayoutParams.WRAP_CONTENT,
                    x = 0,
                    y = 0,
                    centerHorizontal = true,
                    draggable = false,
                    aboveStatusBar = true,
                ) {
                    FloatMiniMonitorContent(service)
                }
            }
            TYPE_FPS -> {
                floatWindowManager.addWindow(
                    id = type,
                    width = WindowManager.LayoutParams.WRAP_CONTENT,
                    height = WindowManager.LayoutParams.WRAP_CONTENT,
                    y = 300,
                    onInteraction = { touching ->
                        if (touching) {
                            fpsInteractionJob?.cancel()
                            fpsInteracting.value = true
                        } else {
                            fpsInteractionJob?.cancel()
                            fpsInteractionJob = lifecycleScope.launch {
                                delay(1000)
                                fpsInteracting.value = false
                            }
                        }
                    },
                ) {
                    FloatFpsContent(service)
                }
            }
            TYPE_TEMPERATURE -> {
                floatWindowManager.addWindow(
                    id = type,
                    width = WindowManager.LayoutParams.WRAP_CONTENT,
                    height = WindowManager.LayoutParams.WRAP_CONTENT,
                    y = 200,
                ) {
                    FloatTemperatureContent(service)
                }
            }
            TYPE_PROCESS -> {
                floatWindowManager.addWindow(
                    id = type,
                    width = WindowManager.LayoutParams.WRAP_CONTENT,
                    height = WindowManager.LayoutParams.WRAP_CONTENT,
                    y = 200,
                ) {
                    FloatProcessContent(service)
                }
            }
            TYPE_THREAD -> {
                floatWindowManager.addWindow(
                    id = type,
                    width = WindowManager.LayoutParams.WRAP_CONTENT,
                    height = WindowManager.LayoutParams.WRAP_CONTENT,
                    y = 200,
                ) {
                    FloatThreadContent(service)
                }
            }
        }

        saveEnabledMonitors()
    }

    private fun removeMonitor(type: String) {
        if (type == TYPE_FPS || type == TYPE_MINI) {
            val otherFpsNeeded = when (type) {
                TYPE_FPS -> floatWindowManager.isWindowActive(TYPE_MINI)
                TYPE_MINI -> floatWindowManager.isWindowActive(TYPE_FPS)
                else -> false
            }
            if (!otherFpsNeeded) {
                sharedFpsJob?.cancel()
                sharedFpsJob = null
            }
        }

        dataCollectionJobs.remove(type)?.cancel()
        floatWindowManager.removeWindow(type)

        saveEnabledMonitors()

        if (floatWindowManager.getActiveWindowIds().isEmpty()) {
            stopFloatService()
        }
    }

    private fun saveEnabledMonitors() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_ENABLED_MONITORS, floatWindowManager.getActiveWindowIds())
            .apply()
    }

    private suspend fun collectDataForType(type: String) {
        val intervalMs = when (type) {
            TYPE_MINI -> 1000L
            TYPE_PROCESS, TYPE_THREAD -> 2000L
            else -> 1500L
        }

        while (kotlin.coroutines.coroutineContext.isActive) {
            try {
                when (type) {
                    TYPE_LOAD -> collectLoadData()
                    TYPE_MINI -> collectMiniData()
                    TYPE_TEMPERATURE -> collectThermalData()
                    TYPE_PROCESS -> collectProcessData()
                    TYPE_THREAD -> collectThreadData()
                }
            } catch (e: Exception) {
                Log.w(TAG, "collectDataForType($type) failed", e)
            }
            delay(intervalMs)
        }
    }

    private suspend fun collectLoadData() {
        val snapshot = aggregatedMonitorDataSource.collectSnapshot()
        cpuLoad.value = snapshot.cpuLoadPercent
        gpuLoad.value = snapshot.gpuLoadPercent
        cpuTemp.value = snapshot.cpuTempCelsius

        val batteryInfo = batteryDataSource.getBatteryStatus()
        batteryLevel.value = batteryInfo.capacity

        memUsed.value = calculateMemoryUsage()
    }

    private suspend fun collectMiniData() {
        val snapshot = aggregatedMonitorDataSource.collectSnapshot()
        // Diagnostic: log when key values are zero/empty (data "disappears")
        if (snapshot.cpuLoadPercent == 0.0 || snapshot.cpuTempCelsius == 0.0 || snapshot.cpuCoreLoads.isEmpty()) {
            Log.e(TAG, "collectMiniData: ANOMALY cpu=%.1f temp=%.1f cores=%d runner=%s".format(
                snapshot.cpuLoadPercent, snapshot.cpuTempCelsius,
                snapshot.cpuCoreLoads.size, snapshot.daemonRunner.ifEmpty { "none" }))
        }
        cpuLoad.value = snapshot.cpuLoadPercent
        gpuLoad.value = snapshot.gpuLoadPercent
        cpuTemp.value = snapshot.cpuTempCelsius
        currentMa.value = snapshot.batteryCurrentMa
        cpuCoreLoads.value = snapshot.cpuCoreLoads

        try {
            batteryTemp.value = batteryDataSource.getBatteryStatus().temperatureCelsius
        } catch (e: Exception) {
            Log.w(TAG, "getBatteryStatus for mini failed", e)
        }
    }

    // ---- Shared FPS collection (daemon only) ----

    private fun ensureSharedFpsJob() {
        if (sharedFpsJob?.isActive == true) return
        sharedFpsJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    val data = fpsDataSource.getDaemonFps()
                    currentFps.value = data?.fps ?: 0.0
                    currentJank.value = data?.jankCount ?: 0
                } catch (e: Exception) {
                    Log.w(TAG, "collectSharedFps failed", e)
                }
                val interval = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getLong(KEY_FPS_INTERVAL, DEFAULT_FPS_INTERVAL)
                delay(interval)
            }
        }
    }

    private fun restartSharedFpsJob() {
        if (sharedFpsJob?.isActive != true) return
        sharedFpsJob?.cancel()
        sharedFpsJob = null
        ensureSharedFpsJob()
    }

    private suspend fun collectThermalData() {
        thermalZones.value = thermalDataSource.getAllThermalZones()
    }

    private suspend fun collectProcessData() {
        topProcesses.value = processDataSource.getTopProcesses(8)
    }

    private suspend fun collectThreadData() {
        val fgPid = getForegroundAppPid()
        if (fgPid != null) {
            val threads = processDataSource.getThreads(fgPid.first)
            topThreads.value = threads.take(15)
            foregroundApp.value = fgPid.second
        } else {
            topThreads.value = emptyList()
            foregroundApp.value = ""
        }
    }

    private suspend fun getForegroundAppPid(): Pair<Int, String>? {
        return try {
            val top = processDataSource.getTopProcesses(1).firstOrNull()
            top?.let { it.pid to it.name }
        } catch (e: Exception) {
            Log.w(TAG, "getForegroundAppPid failed", e)
            null
        }
    }

    private fun calculateMemoryUsage(): Double {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val usedMem = memInfo.totalMem - memInfo.availMem
            return (usedMem.toDouble() / memInfo.totalMem.toDouble()) * 100.0
        } catch (e: Exception) {
            Log.w(TAG, "calculateMemoryUsage failed", e)
            return 0.0
        }
    }

    override fun onDestroy() {
        sharedFpsJob?.cancel()
        floatWindowManager.removeAllWindows()
        dataCollectionJobs.values.forEach { it.cancel() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.float_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.float_channel_desc)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
