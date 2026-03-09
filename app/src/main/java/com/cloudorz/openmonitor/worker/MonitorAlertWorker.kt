package com.cloudorz.openmonitor.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cloudorz.openmonitor.MainActivity
import com.cloudorz.openmonitor.R
import com.cloudorz.openmonitor.core.data.datasource.BatteryDataSource
import com.cloudorz.openmonitor.core.data.datasource.CpuDataSource
import com.cloudorz.openmonitor.core.data.datasource.MemoryDataSource
import com.cloudorz.openmonitor.core.data.datasource.ThermalDataSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MonitorAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val cpuDataSource: CpuDataSource,
    private val thermalDataSource: ThermalDataSource,
    private val memoryDataSource: MemoryDataSource,
    private val batteryDataSource: BatteryDataSource,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            ensureNotificationChannel()
            checkAlerts()
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "MonitorAlert check failed", e)
            Result.retry()
        }
    }

    private suspend fun checkAlerts() {
        var notificationId = NOTIFICATION_ID_BASE

        // CPU temperature > 80
        val cpuTemp = thermalDataSource.getCpuTemperature()
        if (cpuTemp > CPU_TEMP_THRESHOLD) {
            showNotification(
                notificationId++,
                applicationContext.getString(R.string.alert_cpu_temp_title),
                applicationContext.getString(R.string.alert_cpu_temp_body, cpuTemp.toInt()),
            )
        }

        // CPU load > 90%
        val loads = cpuDataSource.getCpuLoad()
        val totalLoad = if (loads.isNotEmpty()) loads[0].toInt() else 0
        if (totalLoad > CPU_LOAD_THRESHOLD) {
            showNotification(
                notificationId++,
                applicationContext.getString(R.string.alert_cpu_load_title),
                applicationContext.getString(R.string.alert_cpu_load_body, totalLoad),
            )
        }

        // Memory > 90%
        val memInfo = memoryDataSource.getMemoryInfo()
        if (memInfo.totalKB > 0) {
            val memUsagePercent = ((memInfo.totalKB - memInfo.availableKB) * 100 / memInfo.totalKB).toInt()
            if (memUsagePercent > MEMORY_THRESHOLD) {
                showNotification(
                    notificationId++,
                    applicationContext.getString(R.string.alert_memory_title),
                    applicationContext.getString(R.string.alert_memory_body, memUsagePercent),
                )
            }
        }

        // Battery temperature > 45
        val batteryStatus = batteryDataSource.getBatteryStatus()
        if (batteryStatus.temperatureCelsius > BATTERY_TEMP_THRESHOLD) {
            showNotification(
                notificationId,
                applicationContext.getString(R.string.alert_battery_temp_title),
                applicationContext.getString(R.string.alert_battery_temp_body, batteryStatus.temperatureCelsius.toInt()),
            )
        }
    }

    private fun ensureNotificationChannel() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.alert_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = applicationContext.getString(R.string.alert_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(id: Int, title: String, body: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_monitor)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, notification)
    }

    companion object {
        private const val TAG = "MonitorAlertWorker"
        const val WORK_NAME = "monitor_alert"
        const val CHANNEL_ID = "monitor_alerts"
        private const val NOTIFICATION_ID_BASE = 2000
        private const val CPU_TEMP_THRESHOLD = 80.0
        private const val CPU_LOAD_THRESHOLD = 90
        private const val MEMORY_THRESHOLD = 90
        private const val BATTERY_TEMP_THRESHOLD = 45.0
    }
}
