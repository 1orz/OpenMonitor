package com.cloudorz.openmonitor.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.cloudorz.openmonitor.core.common.PermissionManager
import com.cloudorz.openmonitor.core.common.PrivilegeMode
import com.cloudorz.openmonitor.core.data.datasource.FpsDataSource
import com.cloudorz.openmonitor.core.data.datasource.AggregatedMonitorDataSource
import com.cloudorz.openmonitor.core.data.datasource.DaemonDataSource
import com.cloudorz.openmonitor.core.data.datasource.DaemonLauncher
import com.cloudorz.openmonitor.core.data.datasource.DaemonManager
import com.cloudorz.openmonitor.core.data.datasource.BatteryDataSource
import com.cloudorz.openmonitor.core.data.datasource.CpuDataSource
import com.cloudorz.openmonitor.core.data.datasource.GpuDataSource
import com.cloudorz.openmonitor.core.data.datasource.ProcessDataSource
import com.cloudorz.openmonitor.core.data.datasource.ThermalDataSource
import com.cloudorz.openmonitor.core.model.process.ProcessInfo
import com.cloudorz.openmonitor.core.model.process.ThreadInfo
import com.cloudorz.openmonitor.core.model.thermal.ThermalZone
import android.view.WindowManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import androidx.core.content.edit
import javax.inject.Inject

@AndroidEntryPoint
class FloatMonitorService : LifecycleService() {

    companion object {
        private const val TAG = "FloatMonitorService"
        const val CHANNEL_ID = "float_monitor"
        const val NOTIFICATION_ID = 1002

        const val ACTION_START = "com.cloudorz.openmonitor.service.FLOAT_START"
        const val ACTION_STOP = "com.cloudorz.openmonitor.service.FLOAT_STOP"
        const val ACTION_ADD_MONITOR = "com.cloudorz.openmonitor.service.FLOAT_ADD"
        const val ACTION_REMOVE_MONITOR = "com.cloudorz.openmonitor.service.FLOAT_REMOVE"
        const val ACTION_UPDATE_POLL_SETTINGS = "com.cloudorz.openmonitor.service.UPDATE_POLL"
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
        const val KEY_POLL_INTERVAL = "poll_interval"
        const val DEFAULT_POLL_INTERVAL = 500L

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

        fun updatePollSettingsIntent(context: Context): Intent =
            Intent(context, FloatMonitorService::class.java).apply {
                action = ACTION_UPDATE_POLL_SETTINGS
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
    @Inject lateinit var daemonClient: com.cloudorz.openmonitor.core.data.datasource.DaemonClient
    @Inject lateinit var shellExecutor: com.cloudorz.openmonitor.core.common.ShellExecutor
    @Inject lateinit var fpsRecordingManager: com.cloudorz.openmonitor.core.data.datasource.FpsRecordingManager

    private lateinit var floatWindowManager: FloatWindowManager
    private var restoreJob: Job? = null
    private val dataCollectionJobs = mutableMapOf<String, Job>()

    // Shared FPS collection: single writer to currentFps
    private var sharedFpsJob: Job? = null

    // Shared data states for composables (null = data not yet available)
    val cpuLoad = MutableStateFlow<Double?>(null)
    val gpuLoad = MutableStateFlow<Double?>(null)
    val memUsed = MutableStateFlow<Double?>(null)
    val batteryLevel = MutableStateFlow(0)
    val cpuTemp = MutableStateFlow<Double?>(null)
    val currentFps = MutableStateFlow<Double?>(null)
    val currentJank = MutableStateFlow(0)
    val thermalZones = MutableStateFlow<List<ThermalZone>>(emptyList())
    val topProcesses = MutableStateFlow<List<ProcessInfo>>(emptyList())
    val topThreads = MutableStateFlow<List<ThreadInfo>>(emptyList())
    val foregroundApp = MutableStateFlow("")
    val currentMa = MutableStateFlow<Int?>(null)
    val cpuCoreLoads = MutableStateFlow<List<Double>?>(null)
    val batteryTemp = MutableStateFlow<Double?>(null)
    val hasShellAccess = MutableStateFlow(false)
    val hasRootAccess = MutableStateFlow(false)
    val fpsInteracting = MutableStateFlow(false)
    val fpsShowDurationMenu = MutableStateFlow(false)
    val fpsRecordingState get() = fpsRecordingManager.state
    val fpsRecordingInfo get() = fpsRecordingManager.info
    private var fpsInteractionJob: Job? = null
    private var fpsDurationMenuJob: Job? = null

    fun onFpsWindowClick() {
        when (fpsRecordingManager.state.value) {
            com.cloudorz.openmonitor.core.data.datasource.FpsRecordingState.IDLE -> {
                // Toggle duration menu
                val show = !fpsShowDurationMenu.value
                fpsShowDurationMenu.value = show
                fpsDurationMenuJob?.cancel()
                if (show) {
                    // Auto-hide after 5s
                    fpsDurationMenuJob = lifecycleScope.launch {
                        delay(5000)
                        fpsShowDurationMenu.value = false
                    }
                }
            }
            com.cloudorz.openmonitor.core.data.datasource.FpsRecordingState.COUNTDOWN -> {
                fpsRecordingManager.stopRecording()
                fpsShowDurationMenu.value = false
            }
            com.cloudorz.openmonitor.core.data.datasource.FpsRecordingState.RECORDING -> {
                fpsRecordingManager.stopRecording()
                fpsShowDurationMenu.value = false
            }
        }
    }

    fun onFpsDurationSelected(minutes: Int) {
        fpsShowDurationMenu.value = false
        fpsDurationMenuJob?.cancel()
        fpsRecordingManager.startRecording(minutes * 60)
    }

    override fun onCreate() {
        super.onCreate()
        val overlayMode = getOverlayMode()
        val windowContext = when (overlayMode) {
            "OVERLAY_ONLY" -> this
            "ACCESSIBILITY_ONLY" -> AccessibilityMonitorService.instance ?: this
            else -> AccessibilityMonitorService.instance ?: this // AUTO
        }
        floatWindowManager = FloatWindowManager(windowContext)
        createNotificationChannel()
        updateShellAccess()
    }

    private fun getOverlayMode(): String =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString("overlay_mode", "AUTO") ?: "AUTO"

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
            ACTION_UPDATE_POLL_SETTINGS -> {
                restartAllJobs()
            }
        }

        return START_STICKY
    }

    private fun startFloatService() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit { putBoolean(KEY_SERVICE_ACTIVE, true) }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.float_notification_title))
            .setContentText(getString(R.string.float_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // Async: ensure accessibility service → restore monitors (cancel previous if re-entered)
        restoreJob?.cancel()
        restoreJob = lifecycleScope.launch { restoreMonitorsWithAccessibility() }
    }

    /**
     * Ensures highest overlay priority by enabling accessibility service first,
     * then restores saved float windows. Falls back to TYPE_APPLICATION_OVERLAY
     * if accessibility is unavailable.
     */
    private suspend fun restoreMonitorsWithAccessibility() {
        val overlayMode = getOverlayMode()

        // 1. Try to enable accessibility service (skip if OVERLAY_ONLY mode)
        if (overlayMode != "OVERLAY_ONLY" && AccessibilityMonitorService.instance == null) {
            val mode = permissionManager.currentMode.value
            if (mode == PrivilegeMode.ROOT || mode == PrivilegeMode.SHIZUKU || mode == PrivilegeMode.ADB) {
                try {
                    val enabled = withContext(Dispatchers.IO) {
                        AccessibilityMonitorService.enableViaShell(this@FloatMonitorService, shellExecutor)
                    }
                    if (enabled) {
                        // Wait for system to bind the accessibility service (up to 3s)
                        val connected = withTimeoutOrNull(3000L) {
                            while (AccessibilityMonitorService.instance == null) {
                                delay(100)
                            }
                            true
                        }
                        if (connected == true) {
                            Log.i(TAG, "accessibility service enabled, upgrading to TYPE_ACCESSIBILITY_OVERLAY")
                        } else {
                            Log.w(TAG, "accessibility service enabled but not connected within timeout")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "failed to enable accessibility service via shell", e)
                }
            }
        }

        // 2. Reinitialize FloatWindowManager based on overlay mode preference
        val accessibilityCtx = AccessibilityMonitorService.instance
        when (overlayMode) {
            "OVERLAY_ONLY" -> {
                // Force standard overlay, never use accessibility context
                if (!Settings.canDrawOverlays(this@FloatMonitorService)) {
                    Log.e(TAG, "OVERLAY_ONLY mode but no overlay permission, skipping")
                    return
                }
                withContext(Dispatchers.Main) { floatWindowManager.removeAllWindows() }
                floatWindowManager = FloatWindowManager(this@FloatMonitorService)
                Log.i(TAG, "FloatWindowManager set to standard overlay (OVERLAY_ONLY mode)")
            }
            "ACCESSIBILITY_ONLY" -> {
                if (accessibilityCtx != null) {
                    withContext(Dispatchers.Main) { floatWindowManager.removeAllWindows() }
                    floatWindowManager = FloatWindowManager(accessibilityCtx)
                    Log.i(TAG, "FloatWindowManager set to accessibility (ACCESSIBILITY_ONLY mode)")
                } else {
                    Log.e(TAG, "ACCESSIBILITY_ONLY mode but service not available, skipping")
                    return
                }
            }
            else -> {
                // AUTO: prefer accessibility
                if (accessibilityCtx != null) {
                    withContext(Dispatchers.Main) { floatWindowManager.removeAllWindows() }
                    floatWindowManager = FloatWindowManager(accessibilityCtx)
                    Log.i(TAG, "FloatWindowManager upgraded to accessibility context (AUTO)")
                } else if (!Settings.canDrawOverlays(this@FloatMonitorService)) {
                    Log.e(TAG, "no accessibility and no overlay permission, skipping window restore")
                    return
                }
            }
        }

        // 3. Restore saved monitors on main thread
        val savedMonitors = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getStringSet(KEY_ENABLED_MONITORS, emptySet()) ?: emptySet()
        withContext(Dispatchers.Main) {
            for (type in savedMonitors) {
                addMonitor(type)
            }
            if (savedMonitors.isNotEmpty()) {
                notifyWatchdog(true)
            }
        }
    }

    private fun stopFloatService() {
        notifyWatchdog(false)

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit {
                putBoolean(KEY_SERVICE_ACTIVE, false)
                putStringSet(KEY_ENABLED_MONITORS, emptySet())
            }

        sharedFpsJob?.cancel()
        sharedFpsJob = null
        dataCollectionJobs.values.forEach { it.cancel() }
        dataCollectionJobs.clear()
        floatWindowManager.removeAllWindows()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notifyWatchdog(enable: Boolean) {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                daemonClient.sendCommand(if (enable) "watchdog-start" else "watchdog-stop")
            } catch (_: Exception) {}
        }
    }

    private fun addMonitor(type: String) {
        if (floatWindowManager.isWindowActive(type)) return

        // Ensure watchdog is running whenever a monitor is added
        notifyWatchdog(true)

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

        // Create floating window (catch BadTokenException to avoid crash loop from watchdog)
        val service = this
        try { when (type) {
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
                    onClick = { onFpsWindowClick() },
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
        } } catch (e: Exception) {
            Log.e(TAG, "addMonitor($type) failed to add window, overlay permission may be revoked", e)
            dataCollectionJobs.remove(type)?.cancel()
            return
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
            .edit { putStringSet(KEY_ENABLED_MONITORS, floatWindowManager.getActiveWindowIds()) }
    }

    private fun getPollInterval(): Long =
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getLong(KEY_POLL_INTERVAL, DEFAULT_POLL_INTERVAL)

    private suspend fun collectDataForType(type: String) {
        while (currentCoroutineContext().isActive) {
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
            // Process/Thread are heavier — use 2x interval (min 1000ms)
            val base = getPollInterval()
            val intervalMs = if (type == TYPE_PROCESS || type == TYPE_THREAD) {
                (base * 2).coerceAtLeast(1000L)
            } else {
                base
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
                delay(getPollInterval())
            }
        }
    }

    /** Restart all active collection jobs with the new poll interval */
    private fun restartAllJobs() {
        // Restart shared FPS job
        if (sharedFpsJob?.isActive == true) {
            sharedFpsJob?.cancel()
            sharedFpsJob = null
            ensureSharedFpsJob()
        }
        // Restart per-type data jobs
        val activeTypes = dataCollectionJobs.keys.toList()
        for (type in activeTypes) {
            dataCollectionJobs.remove(type)?.cancel()
            dataCollectionJobs[type] = lifecycleScope.launch { collectDataForType(type) }
        }
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
            val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
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
