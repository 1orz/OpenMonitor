package com.cloudorz.openmonitor.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.cloudorz.openmonitor.core.data.datasource.BatteryDataSource
import com.cloudorz.openmonitor.core.data.datasource.ForegroundAppDataSource
import com.cloudorz.openmonitor.core.database.dao.BatteryRecordDao
import com.cloudorz.openmonitor.core.database.entity.BatteryRecordEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class BatteryRecordingService : LifecycleService() {

    @Inject lateinit var batteryDataSource: BatteryDataSource
    @Inject lateinit var foregroundAppDataSource: ForegroundAppDataSource
    @Inject lateinit var batteryRecordDao: BatteryRecordDao

    private var samplingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        startSampling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        samplingJob?.cancel()
        super.onDestroy()
    }

    private fun startSampling() {
        samplingJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    val battery = batteryDataSource.getBatteryStatus()
                    val pkg = foregroundAppDataSource.getForegroundPackage()
                    val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                    val screenOn = powerManager.isInteractive

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
                } catch (e: Exception) {
                    Log.w(TAG, "Battery sample failed", e)
                }
                delay(SAMPLING_INTERVAL_MS)
            }
        }
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.battery_recording_notification_title))
            .setContentText(getString(R.string.battery_recording_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.battery_recording_channel_name),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = getString(R.string.battery_recording_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "BatteryRecordingService"
        const val CHANNEL_ID = "battery_recording"
        const val NOTIFICATION_ID = 1003
        const val SAMPLING_INTERVAL_MS = 30_000L

        fun startIntent(context: Context): Intent =
            Intent(context, BatteryRecordingService::class.java)
    }
}
