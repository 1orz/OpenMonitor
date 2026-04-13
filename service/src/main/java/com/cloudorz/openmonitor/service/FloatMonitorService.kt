package com.cloudorz.openmonitor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.IBinder
import android.os.PowerManager
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
import com.cloudorz.openmonitor.core.data.datasource.AppInfoResolver
import com.cloudorz.openmonitor.core.data.datasource.ForegroundAppDataSource
import com.cloudorz.openmonitor.core.data.datasource.ProcessDataSource
import com.cloudorz.openmonitor.core.data.datasource.ThermalDataSource
import com.cloudorz.openmonitor.core.database.dao.BatteryRecordDao
import com.cloudorz.openmonitor.core.database.entity.BatteryRecordEntity
import com.cloudorz.openmonitor.core.model.process.ProcessFilterMode
import com.cloudorz.openmonitor.core.model.process.ProcessInfo
import com.cloudorz.openmonitor.core.model.process.ThreadInfo
import com.cloudorz.openmonitor.core.model.thermal.ThermalZone
import android.content.SharedPreferences
import android.content.res.Configuration
import android.view.WindowManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.edit
import kotlin.math.abs
import javax.inject.Inject

@AndroidEntryPoint
class FloatMonitorService : LifecycleService() {

    companion object {
        private const val TAG = "FloatMonitorService"
        const val CHANNEL_ID = "float_monitor_v3"
        const val NOTIFICATION_ID = 1002

        const val ACTION_START = "com.cloudorz.openmonitor.service.FLOAT_START"
        const val ACTION_STOP = "com.cloudorz.openmonitor.service.FLOAT_STOP"
        const val ACTION_ADD_MONITOR = "com.cloudorz.openmonitor.service.FLOAT_ADD"
        const val ACTION_REMOVE_MONITOR = "com.cloudorz.openmonitor.service.FLOAT_REMOVE"
        const val ACTION_SHOW_PANEL = "com.cloudorz.openmonitor.service.FLOAT_SHOW_PANEL"
        const val EXTRA_MONITOR_TYPE = "monitor_type"

        const val TYPE_LOAD = "LOAD_MONITOR"
        const val TYPE_MINI = "MINI_MONITOR"
        const val TYPE_FPS = "FPS_RECORDER"
        const val TYPE_TEMPERATURE = "TEMPERATURE_MONITOR"
        const val TYPE_PROCESS = "PROCESS_MONITOR"
        const val TYPE_THREAD = "THREAD_MONITOR"

        private const val CONTROL_PANEL_ID = "_control_panel"
        private const val CONTROL_PANEL_BACKDROP_ID = "_control_panel_backdrop"

        private const val PREFS_NAME = "monitor_settings"
        private const val KEY_ENABLED_MONITORS = "enabled_monitors"
        private const val KEY_SERVICE_ACTIVE = "float_service_active"
        private const val KEY_MINI_CPU_FREQ = "mini_show_cpu_freq"
        private const val KEY_MINI_GPU_FREQ = "mini_show_gpu_freq"
        private const val BATTERY_SAMPLING_INTERVAL_MS = 30_000L
        private const val POLL_INTERVAL_MS = 1000L
        private const val FPS_POLL_INTERVAL_MS = 500L
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_TEMP_EXTENDED = "temp_extended_categories"
        private const val KEY_MINI_NET_SPEED = "mini_show_net_speed"
        private const val KEY_MINI_NET_SPEED_MODE = "mini_net_speed_mode"

        fun startIntent(context: Context): Intent =
            Intent(context, FloatMonitorService::class.java).apply { action = ACTION_START }

        fun stopIntent(context: Context): Intent =
            Intent(context, FloatMonitorService::class.java).apply { action = ACTION_STOP }

        fun addMonitorIntent(context: Context, monitorType: String): Intent =
            Intent(context, FloatMonitorService::class.java).apply {
                action = ACTION_ADD_MONITOR
                putExtra(EXTRA_MONITOR_TYPE, monitorType)
            }

        fun showPanelIntent(context: Context): Intent =
            Intent(context, FloatMonitorService::class.java).apply { action = ACTION_SHOW_PANEL }

        fun removeMonitorIntent(context: Context, monitorType: String): Intent =
            Intent(context, FloatMonitorService::class.java).apply {
                action = ACTION_REMOVE_MONITOR
                putExtra(EXTRA_MONITOR_TYPE, monitorType)
            }

    }

    @Inject lateinit var cpuDataSource: CpuDataSource
    @Inject lateinit var gpuDataSource: GpuDataSource
    @Inject lateinit var batteryDataSource: BatteryDataSource
    @Inject lateinit var thermalDataSource: ThermalDataSource
    @Inject lateinit var fpsDataSource: FpsDataSource
    @Inject lateinit var processDataSource: ProcessDataSource
    @Inject lateinit var appInfoResolver: AppInfoResolver
    @Inject lateinit var permissionManager: PermissionManager
    @Inject lateinit var aggregatedMonitorDataSource: AggregatedMonitorDataSource
    @Inject lateinit var daemonLauncher: DaemonLauncher
    @Inject lateinit var daemonDataSource: DaemonDataSource
    @Inject lateinit var daemonManager: DaemonManager
    @Inject lateinit var daemonClient: com.cloudorz.openmonitor.core.data.datasource.DaemonClient
    @Inject lateinit var fpsRecordingManager: com.cloudorz.openmonitor.core.data.datasource.FpsRecordingManager
    @Inject lateinit var batteryRecordDao: BatteryRecordDao
    @Inject lateinit var foregroundAppDataSource: ForegroundAppDataSource
    @Inject lateinit var networkDataSource: com.cloudorz.openmonitor.core.data.datasource.NetworkDataSource

    private lateinit var floatWindowManager: FloatWindowManager
    private var restoreJob: Job? = null
    private var batterySamplingJob: Job? = null
    private val dataCollectionJobs = mutableMapOf<String, Job>()

    // Shared FPS collection: single writer to currentFps
    private var sharedFpsJob: Job? = null
    // Shared snapshot collection: LOAD and MINI share one data loop
    private var sharedSnapshotJob: Job? = null

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
    val isThreadLoaded = MutableStateFlow(false)
    val foregroundApp = MutableStateFlow("")
    val currentMa = MutableStateFlow<Int?>(null)
    val cpuCoreLoads = MutableStateFlow<List<Double>?>(null)
    val cpuCoreFreqs = MutableStateFlow<List<Int>?>(null)
    val gpuFreqMhz = MutableStateFlow<Int?>(null)
    val ddrFreqMbps = MutableStateFlow<Int?>(null)
    val batteryTemp = MutableStateFlow<Double?>(null)
    val hasShellAccess = MutableStateFlow(false)
    val hasRootAccess = MutableStateFlow(false)
    val activeMonitorIds = MutableStateFlow<Set<String>>(emptySet())

    // Interactive process float state
    val processFilterMode = MutableStateFlow(ProcessFilterMode.ALL)
    val selectedProcessPid = MutableStateFlow<Int?>(null)
    val processMinimized = MutableStateFlow(false)
    val processLocked = MutableStateFlow(false)
    val processAppIcons = MutableStateFlow<Map<String, Bitmap?>>(emptyMap())
    val fpsInteracting = MutableStateFlow(false)
    val fpsShowDurationMenu = MutableStateFlow(false)
    val fpsRecordingState get() = fpsRecordingManager.state
    val fpsRecordingInfo get() = fpsRecordingManager.info
    private var fpsInteractionJob: Job? = null
    private var fpsDurationMenuJob: Job? = null
    val batteryVoltage = MutableStateFlow(0.0)
    val memTotalMB = MutableStateFlow(0.0)
    val memUsedMB = MutableStateFlow(0.0)
    val loadMonitorCompact = MutableStateFlow(true)

    data class ClusterInfo(val coreIndices: List<Int>, val name: String?)
    val cpuClusters = MutableStateFlow<List<ClusterInfo>>(emptyList())
    val miniShowCpuFreq = MutableStateFlow(false)
    val miniShowGpuFreq = MutableStateFlow(false)
    val tempShowExtended = MutableStateFlow(false)
    val miniShowNetSpeed = MutableStateFlow(false)
    val miniNetSpeedMode = MutableStateFlow(0) // 0=combined, 1=split
    val netRxSpeed = MutableStateFlow(0L)  // bytes/sec
    val netTxSpeed = MutableStateFlow(0L)  // bytes/sec

    // Dark theme for float windows: considers app pref (0=system,1=light,2=dark) + system config
    val floatDarkTheme = MutableStateFlow(false)
    private var prefChangeListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun onMiniCpuFreqToggle() {
        val newVal = !miniShowCpuFreq.value
        miniShowCpuFreq.value = newVal
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { putBoolean(KEY_MINI_CPU_FREQ, newVal) }
    }

    fun onMiniGpuFreqToggle() {
        val newVal = !miniShowGpuFreq.value
        miniShowGpuFreq.value = newVal
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { putBoolean(KEY_MINI_GPU_FREQ, newVal) }
    }

    fun onMiniNetSpeedToggle() {
        val newVal = !miniShowNetSpeed.value
        miniShowNetSpeed.value = newVal
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { putBoolean(KEY_MINI_NET_SPEED, newVal) }
    }

    fun onMiniNetSpeedModeToggle() {
        val newMode = if (miniNetSpeedMode.value == 0) 1 else 0
        miniNetSpeedMode.value = newMode
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { putInt(KEY_MINI_NET_SPEED_MODE, newMode) }
    }

    fun onTempExtendedToggle() {
        val newVal = !tempShowExtended.value
        tempShowExtended.value = newVal
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit { putBoolean(KEY_TEMP_EXTENDED, newVal) }
        lifecycleScope.launch {
            delay(50)
            floatWindowManager.refreshWindowLayout(TYPE_TEMPERATURE)
        }
    }

    fun onLoadMonitorModeToggle() {
        loadMonitorCompact.value = !loadMonitorCompact.value
        lifecycleScope.launch {
            delay(50)
            floatWindowManager.refreshWindowLayout(TYPE_LOAD)
        }
    }

    fun onProcessMinimizeToggle() {
        processMinimized.value = !processMinimized.value
        // Overlay windows with WRAP_CONTENT may not auto-resize when Compose content changes.
        // Post a relayout after Compose recomposes to force the window to remeasure.
        lifecycleScope.launch {
            delay(50)
            floatWindowManager.refreshWindowLayout(TYPE_PROCESS)
        }
    }

    fun onProcessLockToggle() {
        val newLocked = !processLocked.value
        processLocked.value = newLocked
        floatWindowManager.setWindowLocked(TYPE_PROCESS, newLocked)
    }

    fun onProcessClose() {
        removeMonitor(TYPE_PROCESS)
    }

    fun moveProcessWindow(dx: Float, dy: Float) {
        if (processLocked.value) return
        val pos = floatWindowManager.getWindowPosition(TYPE_PROCESS) ?: return
        floatWindowManager.updateWindowPositionBounded(TYPE_PROCESS, pos.first + dx.toInt(), pos.second + dy.toInt())
    }

    fun saveProcessWindowPosition() {
        floatWindowManager.saveWindowPosition(TYPE_PROCESS)
    }

    fun onProcessTapped(process: ProcessInfo) {
        selectedProcessPid.value = if (selectedProcessPid.value == process.pid) null else process.pid
    }

    fun onProcessKill(pid: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            processDataSource.killProcess(pid)
        }
    }

    fun onProcessFilterToggle() {
        processFilterMode.value = when (processFilterMode.value) {
            ProcessFilterMode.ALL -> ProcessFilterMode.APP_ONLY
            ProcessFilterMode.APP_ONLY -> ProcessFilterMode.ALL
        }
    }

    fun showControlPanel() {
        if (floatWindowManager.isWindowActive(CONTROL_PANEL_ID)) {
            dismissControlPanel()
            return
        }

        val service = this

        // Backdrop: full-screen, semi-transparent, clicking dismisses
        floatWindowManager.addWindow(
            id = CONTROL_PANEL_BACKDROP_ID,
            width = WindowManager.LayoutParams.MATCH_PARENT,
            height = WindowManager.LayoutParams.MATCH_PARENT,
            x = 0, y = 0,
            draggable = false,
            onClick = { dismissControlPanel() },
        ) {
            FloatControlPanelBackdropContent()
        }

        // Update active monitor IDs for the panel to observe
        updateActiveMonitorIds()

        // Control panel: screen center, not draggable
        floatWindowManager.addWindow(
            id = CONTROL_PANEL_ID,
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            centerHorizontal = true,
            centerVertical = true,
            draggable = false,
            onClick = { /* absorb clicks so they don't pass to backdrop */ },
            darkTheme = floatDarkTheme,
        ) {
            FloatControlPanelContent(service)
        }
    }

    fun dismissControlPanel() {
        floatWindowManager.removeWindow(CONTROL_PANEL_ID)
        floatWindowManager.removeWindow(CONTROL_PANEL_BACKDROP_ID)
    }

    fun toggleMonitorFromPanel(type: String) {
        if (floatWindowManager.isWindowActive(type)) {
            removeMonitor(type)
        } else {
            addMonitor(type)
        }
        updateActiveMonitorIds()
    }

    private fun updateActiveMonitorIds() {
        activeMonitorIds.value = floatWindowManager.getActiveWindowIds()
            .filter { !it.startsWith("_") }
            .toSet()
    }

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
        floatWindowManager = FloatWindowManager(this)
        createNotificationChannel()
        updateShellAccess()
        floatDarkTheme.value = computeFloatIsDark()
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_DARK_MODE) floatDarkTheme.value = computeFloatIsDark()
        }
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        loadCpuClusters()
    }

    private fun loadCpuClusters() {
        lifecycleScope.launch {
            try {
                val status = cpuDataSource.getGlobalStatus()
                cpuClusters.value = status.clusters.map { cluster ->
                    val archName = cluster.coreIndices.firstNotNullOfOrNull { idx ->
                        status.cores.getOrNull(idx)?.microarchName
                    }
                    ClusterInfo(coreIndices = cluster.coreIndices, name = archName)
                }
            } catch (e: Exception) {
                android.util.Log.d("FloatService", "cluster load failed", e)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        floatDarkTheme.value = computeFloatIsDark()
    }

    private fun computeFloatIsDark(): Boolean {
        val pref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_DARK_MODE, 0)
        return when (pref) {
            1 -> false
            2 -> true
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
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
            ACTION_SHOW_PANEL -> {
                showControlPanel()
            }
        }

        return START_STICKY
    }

    /** 监听配置变化（屏幕旋转），确保悬浮窗不被系统栏遮挡 */
    private val configCallback = object : android.content.ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            // 延迟让 WindowManager metrics 更新完成
            lifecycleScope.launch {
                delay(350)
                floatWindowManager.ensureWindowsWithinBounds()
            }
        }
        override fun onLowMemory() {}
    }

    private fun startFloatService() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        miniShowCpuFreq.value = prefs.getBoolean(KEY_MINI_CPU_FREQ, false)
        miniShowGpuFreq.value = prefs.getBoolean(KEY_MINI_GPU_FREQ, false)
        tempShowExtended.value = prefs.getBoolean(KEY_TEMP_EXTENDED, false)
        miniShowNetSpeed.value = prefs.getBoolean(KEY_MINI_NET_SPEED, false)
        miniNetSpeedMode.value = prefs.getInt(KEY_MINI_NET_SPEED_MODE, 0)
        prefs.edit { putBoolean(KEY_SERVICE_ACTIVE, true) }

        applicationContext.registerComponentCallbacks(configCallback)

        startForeground(
            NOTIFICATION_ID,
            buildMinimalNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        startBatterySampling()

        restoreJob?.cancel()
        restoreJob = lifecycleScope.launch { restoreMonitors() }
    }

    private fun getShowPanelPendingIntent(): PendingIntent {
        val showPanelIntent = Intent(this, FloatMonitorService::class.java)
            .setAction(ACTION_SHOW_PANEL)
        return PendingIntent.getService(
            this, 0, showPanelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildMinimalNotification(): Notification {
        val pendingIntent = getShowPanelPendingIntent()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.float_channel_name))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startBatterySampling() {
        batterySamplingJob?.cancel()
        batterySamplingJob = lifecycleScope.launch {
            while (currentCoroutineContext().isActive) {
                try {
                    val battery = withContext(Dispatchers.IO) { batteryDataSource.getBatteryStatus() }
                    val pkg = withContext(Dispatchers.IO) { foregroundAppDataSource.getForegroundPackage() }
                    val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                    val screenOn = powerManager.isInteractive

                    withContext(Dispatchers.IO) {
                        batteryRecordDao.insert(
                            BatteryRecordEntity(
                                timestamp = System.currentTimeMillis(),
                                capacity = battery.capacity,
                                currentMa = battery.currentMa,
                                voltageV = battery.voltageV,
                                powerW = abs(battery.powerW),
                                temperatureCelsius = battery.temperatureCelsius,
                                isCharging = battery.isCharging,
                                isScreenOn = screenOn,
                                packageName = pkg,
                            ),
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Battery sample failed", e)
                }
                delay(BATTERY_SAMPLING_INTERVAL_MS)
            }
        }
    }

    private suspend fun restoreMonitors() {
        if (!Settings.canDrawOverlays(this@FloatMonitorService)) {
            Log.e(TAG, "no overlay permission, skipping window restore")
            return
        }
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

        batterySamplingJob?.cancel()
        batterySamplingJob = null
        sharedFpsJob?.cancel()
        sharedFpsJob = null
        sharedSnapshotJob?.cancel()
        sharedSnapshotJob = null
        dataCollectionJobs.values.forEach { it.cancel() }
        dataCollectionJobs.clear()
        floatWindowManager.removeAllWindows()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notifyWatchdog(enable: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
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

        // LOAD and MINI share a single snapshot job; FPS uses shared FPS job
        if (type == TYPE_LOAD || type == TYPE_MINI) {
            ensureSharedSnapshotJob()
        } else if (type != TYPE_FPS) {
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
                    onDoubleTap = { removeMonitor(type) },
                    onLongClick = { onLoadMonitorModeToggle() },
                    darkTheme = floatDarkTheme,
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
                    darkTheme = MutableStateFlow(true), // 迷你监视器始终深色背景
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
                    darkTheme = floatDarkTheme,
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
                    onDoubleTap = { removeMonitor(type) },
                    darkTheme = floatDarkTheme,
                ) {
                    FloatTemperatureContent(service, showExtended = service.tempShowExtended.collectAsState().value)
                }
            }
            TYPE_PROCESS -> {
                floatWindowManager.addWindow(
                    id = type,
                    width = WindowManager.LayoutParams.WRAP_CONTENT,
                    height = WindowManager.LayoutParams.WRAP_CONTENT,
                    y = 200,
                    draggable = false,
                    onClick = { /* touchable but not draggable — drag handled in Compose header */ },
                    darkTheme = floatDarkTheme,
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
                    onDoubleTap = { removeMonitor(type) },
                    darkTheme = floatDarkTheme,
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

        // Stop shared snapshot if neither LOAD nor MINI remains
        if (type == TYPE_LOAD || type == TYPE_MINI) {
            val otherSnapshotNeeded = when (type) {
                TYPE_LOAD -> floatWindowManager.isWindowActive(TYPE_MINI)
                TYPE_MINI -> floatWindowManager.isWindowActive(TYPE_LOAD)
                else -> false
            }
            if (!otherSnapshotNeeded) {
                sharedSnapshotJob?.cancel()
                sharedSnapshotJob = null
            }
        }

        dataCollectionJobs.remove(type)?.cancel()
        floatWindowManager.removeWindow(type)

        saveEnabledMonitors()

        val remainingMonitors = floatWindowManager.getActiveWindowIds()
            .filter { !it.startsWith("_") }
        if (remainingMonitors.isEmpty()) {
            notifyWatchdog(false)
        }
    }

    private fun saveEnabledMonitors() {
        val monitorIds = floatWindowManager.getActiveWindowIds()
            .filter { !it.startsWith("_") }
            .toSet()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit { putStringSet(KEY_ENABLED_MONITORS, monitorIds) }
    }

    private suspend fun collectDataForType(type: String) {
        while (currentCoroutineContext().isActive) {
            try {
                when (type) {
                    TYPE_TEMPERATURE -> collectThermalData()
                    TYPE_PROCESS -> collectProcessData()
                    TYPE_THREAD -> collectThreadData()
                }
            } catch (e: Exception) {
                Log.w(TAG, "collectDataForType($type) failed", e)
            }
            val base = POLL_INTERVAL_MS
            val intervalMs = if (type == TYPE_PROCESS || type == TYPE_THREAD) {
                (base * 2).coerceAtLeast(1000L)
            } else {
                base
            }
            delay(intervalMs)
        }
    }

    private fun ensureSharedSnapshotJob() {
        if (sharedSnapshotJob?.isActive == true) return
        sharedSnapshotJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    collectSharedSnapshot()
                } catch (e: Exception) {
                    Log.w(TAG, "shared snapshot failed", e)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun collectSharedSnapshot() {
        val snapshot = aggregatedMonitorDataSource.collectSnapshot()
        cpuLoad.value = snapshot.cpuLoadPercent
        gpuLoad.value = snapshot.gpuLoadPercent
        cpuTemp.value = snapshot.cpuTempCelsius
        cpuCoreFreqs.value = snapshot.cpuCoreFreqs
        cpuCoreLoads.value = snapshot.cpuCoreLoads
        gpuFreqMhz.value = snapshot.gpuFreqMhz
        ddrFreqMbps.value = snapshot.ddrFreqMbps
        currentMa.value = snapshot.batteryCurrentMa

        val batteryInfo = batteryDataSource.getBatteryStatus()
        batteryLevel.value = batteryInfo.capacity
        batteryTemp.value = batteryInfo.temperatureCelsius
        batteryVoltage.value = batteryInfo.voltageV

        val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        memTotalMB.value = memInfo.totalMem / (1024.0 * 1024.0)
        memUsedMB.value = (memInfo.totalMem - memInfo.availMem) / (1024.0 * 1024.0)
        memUsed.value = ((memInfo.totalMem - memInfo.availMem).toDouble() / memInfo.totalMem) * 100.0

        if (miniShowNetSpeed.value) {
            try {
                val netInfo = networkDataSource.getNetworkInfo()
                netRxSpeed.value = netInfo.rxSpeedBytesPerSec
                netTxSpeed.value = netInfo.txSpeedBytesPerSec
            } catch (e: Exception) {
                Log.w(TAG, "getNetworkInfo failed", e)
            }
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
                delay(FPS_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun collectThermalData() {
        thermalZones.value = thermalDataSource.getAllThermalZones()
    }

    private suspend fun collectProcessData() {
        if (processMinimized.value) return // Skip collection when minimized
        val all = processDataSource.getProcessList()
        val filtered = when (processFilterMode.value) {
            ProcessFilterMode.ALL -> all
            ProcessFilterMode.APP_ONLY -> all.filter { it.isUserApp }
        }
        val result = filtered.sortedByDescending { it.cpuPercent }.take(15)
        topProcesses.value = result

        // Load app icons for Android apps
        val iconMap = processAppIcons.value.toMutableMap()
        var changed = false
        result.forEach { proc ->
            if (proc.isAndroidApp && proc.packageName !in iconMap) {
                iconMap[proc.packageName] = appInfoResolver.resolveIcon(proc.packageName)
                changed = true
            }
        }
        if (changed) processAppIcons.value = iconMap
    }

    private suspend fun collectThreadData() {
        val fgPid = getForegroundAppPid()
        if (fgPid != null) {
            val threads = processDataSource.getThreads(fgPid.first)
            topThreads.value = threads.take(15)
            foregroundApp.value = fgPid.second
            isThreadLoaded.value = true
        }
        // Don't reset on failure — keep last known data so UI doesn't flash "loading"
    }

    private suspend fun getForegroundAppPid(): Pair<Int, String>? {
        return try {
            val processes = processDataSource.getProcessList()

            // 1. UsageStatsManager（无需 shell，BASIC 模式可用）
            val fgPackage = withContext(Dispatchers.IO) {
                foregroundAppDataSource.getForegroundPackage()
            }
            if (fgPackage.isNotEmpty()) {
                val match = processes.firstOrNull { it.packageName == fgPackage }
                    ?: processes.firstOrNull { it.name == fgPackage }
                // Also try cmdline prefix match — packageName may be empty for processes whose
                // /proc/status Name was truncated (TASK_COMM_LEN = 15 chars).
                val cmdlineMatch = if (match == null) {
                    processes.firstOrNull { p ->
                        p.cmdline.substringBefore(':').substringBefore(' ').trim() == fgPackage
                    }
                } else null
                val resolved = match ?: cmdlineMatch
                if (resolved != null) return resolved.pid to (resolved.packageName.ifEmpty { fgPackage })
            }
            null
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
        applicationContext.unregisterComponentCallbacks(configCallback)
        prefChangeListener?.let {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(it)
        }
        batterySamplingJob?.cancel()
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
        val manager = getSystemService(NotificationManager::class.java)
        // 删除旧渠道
        manager.deleteNotificationChannel("float_monitor")
        manager.deleteNotificationChannel("float_monitor_v2")
        manager.deleteNotificationChannel("float_monitor_fg")
        manager.deleteNotificationChannel("battery_recording")
        manager.deleteNotificationChannel("monitor_alerts")

        // 用户可见渠道 (IMPORTANCE_LOW) — 无弹窗/声音/振动
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.float_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = getString(R.string.float_channel_desc)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )
    }
}
