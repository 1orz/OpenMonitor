package com.cloudorz.openmonitor

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.cloudorz.openmonitor.core.common.AppLogger
import com.cloudorz.openmonitor.service.BatteryRecordingService
import com.cloudorz.openmonitor.worker.DatabaseCleanupWorker
import com.cloudorz.openmonitor.worker.MonitorAlertWorker
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class MonitorApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        scheduleDatabaseCleanup()
        scheduleMonitorAlerts()
        startBatteryRecording()
    }

    private fun scheduleDatabaseCleanup() {
        val cleanupRequest = PeriodicWorkRequestBuilder<DatabaseCleanupWorker>(
            1, TimeUnit.DAYS,
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DatabaseCleanupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupRequest,
        )
    }

    private fun scheduleMonitorAlerts() {
        val alertRequest = PeriodicWorkRequestBuilder<MonitorAlertWorker>(
            15, TimeUnit.MINUTES,
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            MonitorAlertWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            alertRequest,
        )
    }

    private fun startBatteryRecording() {
        ContextCompat.startForegroundService(
            this,
            BatteryRecordingService.startIntent(this),
        )
    }

    companion object {
        init {
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER or Shell.FLAG_REDIRECT_STDERR)
                    .setTimeout(15)
            )
        }
    }
}
