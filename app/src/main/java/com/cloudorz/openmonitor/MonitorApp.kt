package com.cloudorz.openmonitor

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.cloudorz.openmonitor.core.common.AppLogger
import com.cloudorz.openmonitor.core.data.repository.DeviceIdentityRepository
import com.cloudorz.openmonitor.core.data.util.ApiEncryptor
import com.cloudorz.openmonitor.core.data.util.ApiSigner
import com.cloudorz.openmonitor.core.ui.HapticFeedbackManager
import com.cloudorz.openmonitor.worker.DatabaseCleanupWorker
import com.elvishew.xlog.XLog
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class MonitorApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var identityRepository: DeviceIdentityRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        AppLogger.init(this)
        super.onCreate()
        ApiSigner.hmacKey = BuildConfig.API_HMAC_KEY
        ApiEncryptor.serverPublicKeyBase64 = BuildConfig.API_EC_PUBLIC_KEY
        HapticFeedbackManager.init(this)
        scheduleDatabaseCleanup()
        scheduleDeviceIdentify()
    }

    /**
     * 每次启动都异步采集设备指纹并上报，不阻塞启动流程。
     * UUID 会缓存到本地，后续捐助等功能直接读取。
     */
    private fun scheduleDeviceIdentify() {
        appScope.launch {
            identityRepository.identify().fold(
                onSuccess = { identity ->
                    XLog.tag("DeviceIdentity").i(
                        "Device identified: uuid=${identity.uuid}, isNew=${identity.isNew}"
                    )
                },
                onFailure = { e ->
                    XLog.tag("DeviceIdentity").w("Device identify failed on startup", e)
                },
            )
        }
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
