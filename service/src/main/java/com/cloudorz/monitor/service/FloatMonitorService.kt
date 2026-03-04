package com.cloudorz.monitor.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.cloudorz.monitor.core.data.datasource.BatteryDataSource
import com.cloudorz.monitor.core.data.datasource.CpuDataSource
import com.cloudorz.monitor.core.data.datasource.FpsDataSource
import com.cloudorz.monitor.core.data.datasource.GpuDataSource
import com.cloudorz.monitor.core.data.datasource.ThermalDataSource
import com.cloudorz.monitor.core.model.thermal.ThermalZone
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
        const val CHANNEL_ID = "float_monitor"
        const val NOTIFICATION_ID = 1002

        const val ACTION_START = "com.cloudorz.monitor.service.FLOAT_START"
        const val ACTION_STOP = "com.cloudorz.monitor.service.FLOAT_STOP"
        const val ACTION_ADD_MONITOR = "com.cloudorz.monitor.service.FLOAT_ADD"
        const val ACTION_REMOVE_MONITOR = "com.cloudorz.monitor.service.FLOAT_REMOVE"
        const val EXTRA_MONITOR_TYPE = "monitor_type"

        // Monitor type constants (match FloatMonitorType enum names)
        const val TYPE_LOAD = "LOAD_MONITOR"
        const val TYPE_MINI = "MINI_MONITOR"
        const val TYPE_FPS = "FPS_RECORDER"
        const val TYPE_TEMPERATURE = "TEMPERATURE_MONITOR"

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
    }

    @Inject lateinit var cpuDataSource: CpuDataSource
    @Inject lateinit var gpuDataSource: GpuDataSource
    @Inject lateinit var batteryDataSource: BatteryDataSource
    @Inject lateinit var thermalDataSource: ThermalDataSource
    @Inject lateinit var fpsDataSource: FpsDataSource

    private lateinit var floatWindowManager: FloatWindowManager
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

    override fun onCreate() {
        super.onCreate()
        floatWindowManager = FloatWindowManager(this)
        createNotificationChannel()
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
        }

        return START_STICKY
    }

    private fun startFloatService() {
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
        dataCollectionJobs.values.forEach { it.cancel() }
        dataCollectionJobs.clear()
        floatWindowManager.removeAllWindows()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun addMonitor(type: String) {
        if (floatWindowManager.isWindowActive(type)) return

        // Start data collection for this monitor type
        val job = lifecycleScope.launch {
            collectDataForType(type)
        }
        dataCollectionJobs[type] = job

        // Create the floating window with Compose content
        val service = this
        when (type) {
            TYPE_LOAD -> {
                floatWindowManager.addWindow(id = type, width = 420, height = 360, y = 200) {
                    FloatLoadMonitorContent(service)
                }
            }
            TYPE_MINI -> {
                floatWindowManager.addWindow(id = type, width = 600, height = 80, y = 100) {
                    FloatMiniMonitorContent(service)
                }
            }
            TYPE_FPS -> {
                floatWindowManager.addWindow(id = type, width = 220, height = 260, y = 300) {
                    FloatFpsContent(service)
                }
            }
            TYPE_TEMPERATURE -> {
                floatWindowManager.addWindow(id = type, width = 420, height = 500, y = 200) {
                    FloatTemperatureContent(service)
                }
            }
            else -> {
                // For PROCESS_MONITOR and THREAD_MONITOR, use mini as fallback
                floatWindowManager.addWindow(id = type, width = 600, height = 80, y = 100) {
                    FloatMiniMonitorContent(service)
                }
            }
        }
    }

    private suspend fun collectDataForType(type: String) {
        val intervalMs = when (type) {
            TYPE_FPS -> 500L
            TYPE_MINI -> 1000L
            else -> 1500L
        }

        while (kotlin.coroutines.coroutineContext.isActive) {
            try {
                when (type) {
                    TYPE_LOAD -> collectLoadData()
                    TYPE_MINI -> collectMiniData()
                    TYPE_FPS -> collectFpsData()
                    TYPE_TEMPERATURE -> collectThermalData()
                    else -> collectMiniData()
                }
            } catch (_: Exception) {
                // Ignore errors, continue polling
            }
            delay(intervalMs)
        }
    }

    private suspend fun collectLoadData() {
        // CPU load (index 0 is overall)
        val loads = cpuDataSource.getCpuLoad()
        cpuLoad.value = loads.getOrElse(0) { 0.0 }

        // GPU load
        val gpuInfo = gpuDataSource.getGpuInfo()
        gpuLoad.value = gpuInfo.loadPercent

        // Memory: read /proc/meminfo directly
        val batteryInfo = batteryDataSource.getBatteryStatus()
        batteryLevel.value = batteryInfo.capacity

        // CPU temp
        cpuTemp.value = thermalDataSource.getCpuTemperature().let { if (it < 0) 0.0 else it }

        // Memory usage percentage
        memUsed.value = calculateMemoryUsage()
    }

    private suspend fun collectMiniData() {
        val loads = cpuDataSource.getCpuLoad()
        cpuLoad.value = loads.getOrElse(0) { 0.0 }

        val gpuInfo = gpuDataSource.getGpuInfo()
        gpuLoad.value = gpuInfo.loadPercent

        cpuTemp.value = thermalDataSource.getCpuTemperature().let { if (it < 0) 0.0 else it }
    }

    private suspend fun collectFpsData() {
        val window = fpsDataSource.getCurrentWindow()
        val fpsData = fpsDataSource.getFpsFromSurfaceFlinger(window)
        if (fpsData != null) {
            currentFps.value = fpsData.fps.toDouble()
            currentJank.value = fpsData.jankCount
        }
    }

    private suspend fun collectThermalData() {
        thermalZones.value = thermalDataSource.getAllThermalZones()
    }

    private suspend fun calculateMemoryUsage(): Double {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val usedMem = memInfo.totalMem - memInfo.availMem
            return (usedMem.toDouble() / memInfo.totalMem.toDouble()) * 100.0
        } catch (_: Exception) {
            return 0.0
        }
    }

    private fun removeMonitor(type: String) {
        dataCollectionJobs.remove(type)?.cancel()
        floatWindowManager.removeWindow(type)

        // If no monitors left, stop the service
        if (dataCollectionJobs.isEmpty()) {
            stopFloatService()
        }
    }

    override fun onDestroy() {
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
