package com.cloudorz.openmonitor.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cloudorz.openmonitor.core.database.dao.ChargeStatDao
import com.cloudorz.openmonitor.core.database.dao.FpsSessionDao
import com.cloudorz.openmonitor.core.database.dao.PowerStatDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class DatabaseCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val powerStatDao: PowerStatDao,
    private val chargeStatDao: ChargeStatDao,
    private val fpsSessionDao: FpsSessionDao,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
        return try {
            powerStatDao.deleteSessionsOlderThan(cutoff)
            chargeStatDao.deleteSessionsOlderThan(cutoff)
            fpsSessionDao.deleteSessionsOlderThan(cutoff)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Database cleanup failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "DatabaseCleanupWorker"
        const val WORK_NAME = "database_cleanup"
        const val RETENTION_DAYS = 30L
    }
}
