package com.cloudorz.openmonitor

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.cloudorz.openmonitor.core.common.AppLogger
import com.cloudorz.openmonitor.core.ui.HapticFeedbackManager
import com.cloudorz.openmonitor.worker.DatabaseCleanupWorker
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
        HapticFeedbackManager.init(this)
        scheduleDatabaseCleanup()
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

    companion object {
        init {
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(15)
            )
        }
    }
}
