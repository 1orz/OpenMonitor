package com.cloudorz.monitor.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            val prefs = context.getSharedPreferences("monitor_settings", Context.MODE_PRIVATE)
            val shouldRestart = prefs.getBoolean("float_service_active", false)
            if (shouldRestart) {
                val startIntent = FloatMonitorService.startIntent(context)
                context.startForegroundService(startIntent)
            }
        }
    }
}
