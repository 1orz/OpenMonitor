package com.cloudorz.openmonitor.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_WATCHDOG_RESTART = "com.cloudorz.openmonitor.WATCHDOG_RESTART"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val shouldRestart = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            ACTION_WATCHDOG_RESTART -> {
                val prefs = context.getSharedPreferences("monitor_settings", Context.MODE_PRIVATE)
                prefs.getBoolean("float_service_active", false)
            }
            else -> false
        }
        if (shouldRestart) {
            val startIntent = FloatMonitorService.startIntent(context)
            context.startForegroundService(startIntent)
        }
    }
}
