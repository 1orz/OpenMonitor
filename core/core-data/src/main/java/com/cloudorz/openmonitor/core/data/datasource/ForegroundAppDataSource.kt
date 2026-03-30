package com.cloudorz.openmonitor.core.data.datasource

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundAppDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val usageStatsManager by lazy {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    }

    fun getForegroundPackage(): String {
        if (!hasUsageStatsPermission()) return ""
        return getForegroundFromUsageStats() ?: ""
    }

    private fun getForegroundFromUsageStats(): String? {
        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 60_000
        return try {
            val usageEvents = usageStatsManager.queryEvents(beginTime, endTime)
            var lastForeground: String? = null
            val event = UsageEvents.Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastForeground = event.packageName
                }
            }
            lastForeground
        } catch (_: SecurityException) {
            null
        }
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
