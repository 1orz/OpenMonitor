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
import com.cloudorz.monitor.core.data.datasource.ChoreographerFpsMonitor
import com.cloudorz.monitor.core.data.datasource.FpsDataSource
import com.cloudorz.monitor.core.data.datasource.FrameMetricsFpsMonitor
import com.cloudorz.monitor.core.data.datasource.AggregatedMonitorDataSource
import com.cloudorz.monitor.core.data.datasource.BatteryDataSource
import com.cloudorz.monitor.core.data.datasource.CpuDataSource
import com.cloudorz.monitor.core.data.datasource.GpuDataSource
import com.cloudorz.monitor.core.data.datasource.ProcessDataSource
import com.cloudorz.monitor.core.data.datasource.ThermalDataSource
import com.cloudorz.monitor.core.model.fps.FpsMethod
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
        const val KEY_FPS_METHOD = "fps_method"
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

    private lateinit var floatWindowManager: FloatWindowManager
    private val choreographerFpsMonitor = ChoreographerFpsMonitor()
    private val frameMetricsFpsMonitor = FrameMetricsFpsMonitor()
    private val dataCollectionJobs = mutableMapOf<String, Job>()

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

    // FPS method state
    val currentFpsMethod = MutableStateFlow(FpsMethod.SURFACE_FLINGER)
    val availableFpsMethods = MutableStateFlow<List<FpsMethod>>(emptyList())
    val hasShellAccess = MutableStateFlow(false)

    override fun onCreate() {
        super.onCreate()
        // Use accessibility service context for overlay if available (no SYSTEM_ALERT_WINDOW needed)
        val windowContext = AccessibilityMonitorService.instance ?: this
        floatWindowManager = FloatWindowManager(windowContext)
        createNotificationChannel()
        updateAvailableFpsMethods()
    }

    private fun updateAvailableFpsMethods() {
        val mode = permissionManager.currentMode.value
        val hasShell = mode == PrivilegeMode.ROOT || mode == PrivilegeMode.ADB || mode == PrivilegeMode.SHIZUKU
        hasShellAccess.value = hasShell
        availableFpsMethods.value = if (hasShell) {
            listOf(FpsMethod.SURFACE_FLINGER, FpsMethod.CHOREOGRAPHER)
        } else {
            listOf(FpsMethod.FRAME_METRICS, FpsMethod.CHOREOGRAPHER)
        }

        // Restore saved method from prefs
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedMethod = prefs.getString(KEY_FPS_METHOD, null)
        if (savedMethod != null) {
            val method = FpsMethod.entries.find { it.name == savedMethod }
            if (method != null && method in availableFpsMethods.value) {
                currentFpsMethod.value = method
            }
        }

        if (currentFpsMethod.value !in availableFpsMethods.value) {
            currentFpsMethod.value = availableFpsMethods.value.first()
        }
    }

    private fun reloadFpsSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedMethod = prefs.getString(KEY_FPS_METHOD, null)
        val method = savedMethod?.let { name -> FpsMethod.entries.find { it.name == name } }
            ?: currentFpsMethod.value

        if (method != currentFpsMethod.value) {
            switchFpsMethod(method)
        }

        // Restart FPS/Mini collection jobs to pick up new interval
        val fpsTypes = listOf(TYPE_FPS, TYPE_MINI)
        for (type in fpsTypes) {
            val job = dataCollectionJobs[type]
            if (job != null && job.isActive) {
                job.cancel()
                dataCollectionJobs[type] = lifecycleScope.launch {
                    collectDataForType(type)
                }
            }
        }
    }

    fun switchFpsMethod(method: FpsMethod) {
        if (method == currentFpsMethod.value) return
        if (method !in availableFpsMethods.value) return

        stopFpsMonitors()
        currentFpsMethod.value = method
        startFpsMonitorForCurrentMethod()

        // Restart FPS collection job if active
        val fpsJob = dataCollectionJobs[TYPE_FPS]
        if (fpsJob != null && fpsJob.isActive) {
            fpsJob.cancel()
            dataCollectionJobs[TYPE_FPS] = lifecycleScope.launch {
                collectDataForType(TYPE_FPS)
            }
        }
    }

    private fun startFpsMonitorForCurrentMethod() {
        when (currentFpsMethod.value) {
            FpsMethod.CHOREOGRAPHER -> choreographerFpsMonitor.start()
            FpsMethod.FRAME_METRICS -> frameMetricsFpsMonitor.start(application)
            FpsMethod.SURFACE_FLINGER -> { /* no pre-start needed */ }
        }
    }

    private fun stopFpsMonitors() {
        choreographerFpsMonitor.stop()
        frameMetricsFpsMonitor.stop()
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
                reloadFpsSettings()
            }
        }

        return START_STICKY
    }

    private fun startFloatService() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit().putBoolean(KEY_SERVICE_ACTIVE, true).apply()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CloudMonitor")
            .setContentText("悬浮监视器运行中")
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

        dataCollectionJobs.values.forEach { it.cancel() }
        dataCollectionJobs.clear()
        floatWindowManager.removeAllWindows()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun addMonitor(type: String) {
        if (floatWindowManager.isWindowActive(type)) return

        // Start FPS monitors if needed
        if (type == TYPE_FPS || type == TYPE_MINI) {
            updateAvailableFpsMethods()
            startFpsMonitorForCurrentMethod()
        }

        // Start data collection
        val job = lifecycleScope.launch {
            collectDataForType(type)
        }
        dataCollectionJobs[type] = job

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

        // Persist enabled monitors
        saveEnabledMonitors()
    }

    private fun removeMonitor(type: String) {
        // Smart FPS cleanup
        if (type == TYPE_FPS || type == TYPE_MINI) {
            val otherFpsNeeded = when (type) {
                TYPE_FPS -> floatWindowManager.isWindowActive(TYPE_MINI)
                TYPE_MINI -> floatWindowManager.isWindowActive(TYPE_FPS)
                else -> false
            }
            if (!otherFpsNeeded) {
                stopFpsMonitors()
            }
        }

        dataCollectionJobs.remove(type)?.cancel()
        floatWindowManager.removeWindow(type)

        // Persist enabled monitors
        saveEnabledMonitors()

        // If no monitors left, stop the service
        if (dataCollectionJobs.isEmpty()) {
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
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val fpsInterval = prefs.getLong(KEY_FPS_INTERVAL, DEFAULT_FPS_INTERVAL)
        val intervalMs = when (type) {
            TYPE_FPS -> fpsInterval
            TYPE_MINI -> fpsInterval
            TYPE_PROCESS, TYPE_THREAD -> 2000L
            else -> 1500L
        }

        while (kotlin.coroutines.coroutineContext.isActive) {
            try {
                when (type) {
                    TYPE_LOAD -> collectLoadData()
                    TYPE_MINI -> collectMiniData()
                    TYPE_FPS -> collectFpsData()
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
        cpuLoad.value = snapshot.cpuLoadPercent
        gpuLoad.value = snapshot.gpuLoadPercent
        cpuTemp.value = snapshot.cpuTempCelsius
        currentMa.value = snapshot.batteryCurrentMa

        val fps = snapshot.fpsData
        if (fps != null) {
            currentFps.value = fps.fps.toDouble()
            currentJank.value = fps.jankCount
        } else if (!hasShellAccess.value) {
            collectBasicFps()
        } else {
            currentFps.value = 0.0
            currentJank.value = 0
        }
    }

    private suspend fun collectFpsData() {
        when (currentFpsMethod.value) {
            FpsMethod.SURFACE_FLINGER -> {
                // Use realtime delta FPS (sysfs → service call 1013 → gfxinfo delta)
                // instead of --latency dump which needs a specific window name
                val realtimeFps = fpsDataSource.getRealtimeFps()
                currentFps.value = realtimeFps.toDouble()
                currentJank.value = 0
            }
            FpsMethod.CHOREOGRAPHER -> {
                currentFps.value = choreographerFpsMonitor.fps.value.toDouble()
                currentJank.value = 0
            }
            FpsMethod.FRAME_METRICS -> {
                val data = frameMetricsFpsMonitor.fpsData.value
                currentFps.value = data.fps.toDouble()
                currentJank.value = data.jankCount
            }
        }
    }

    private fun collectBasicFps() {
        when (currentFpsMethod.value) {
            FpsMethod.FRAME_METRICS -> {
                val data = frameMetricsFpsMonitor.fpsData.value
                currentFps.value = data.fps.toDouble()
                currentJank.value = data.jankCount
            }
            FpsMethod.CHOREOGRAPHER -> {
                currentFps.value = choreographerFpsMonitor.fps.value.toDouble()
                currentJank.value = 0
            }
            else -> {
                currentFps.value = 0.0
                currentJank.value = 0
            }
        }
    }

    private suspend fun collectThermalData() {
        thermalZones.value = thermalDataSource.getAllThermalZones()
    }

    private suspend fun collectProcessData() {
        topProcesses.value = processDataSource.getTopProcesses(8)
    }

    private suspend fun collectThreadData() {
        // Get foreground app PID
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
            // Use top CPU process as approximation for foreground app
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
        stopFpsMonitors()
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
            "悬浮监视器",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "悬浮监视器后台通知"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
