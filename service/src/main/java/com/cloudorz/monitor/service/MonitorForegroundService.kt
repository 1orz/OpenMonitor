package com.cloudorz.monitor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import dagger.hilt.android.AndroidEntryPoint

/**
 * Foreground service that keeps system monitoring active in the background.
 *
 * Creates a persistent notification to satisfy Android's foreground service requirements
 * and provides start/stop control via intent actions.
 */
@AndroidEntryPoint
class MonitorForegroundService : LifecycleService() {

    companion object {
        private const val CHANNEL_ID = "monitor_service"
        private const val CHANNEL_NAME = "Monitor Service"
        private const val NOTIFICATION_ID = 1001

        private const val ACTION_START = "com.cloudorz.monitor.service.ACTION_START"
        private const val ACTION_STOP = "com.cloudorz.monitor.service.ACTION_STOP"

        /**
         * Creates an intent to start the monitoring foreground service.
         */
        fun startIntent(context: Context): Intent {
            return Intent(context, MonitorForegroundService::class.java).apply {
                action = ACTION_START
            }
        }

        /**
         * Creates an intent to stop the monitoring foreground service.
         */
        fun stopIntent(context: Context): Intent {
            return Intent(context, MonitorForegroundService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }

    /**
     * Begins the monitoring session by promoting the service to the foreground
     * with a persistent notification.
     */
    private fun startMonitoring() {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        // TODO: Initialize battery polling, CPU monitoring, etc.
    }

    /**
     * Stops all monitoring tasks and removes the service from the foreground.
     */
    private fun stopMonitoring() {
        // TODO: Cancel all polling coroutines and clean up resources.
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Creates the notification channel required for foreground service notifications
     * on Android O (API 26) and above.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Persistent notification for system monitoring service"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Builds the persistent foreground notification displayed while monitoring is active.
     */
    private fun buildNotification(): Notification {
        // Create a PendingIntent that opens the main activity when the notification is tapped.
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            null
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CloudMonitor")
            .setContentText("系统监控运行中")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
