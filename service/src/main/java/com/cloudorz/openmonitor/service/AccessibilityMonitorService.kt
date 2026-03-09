package com.cloudorz.openmonitor.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service that provides:
 * 1. TYPE_ACCESSIBILITY_OVERLAY window capability (no SYSTEM_ALERT_WINDOW needed)
 * 2. Foreground app detection via accessibility events
 */
class AccessibilityMonitorService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 200
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrEmpty() && pkg != "android" && !pkg.startsWith("com.android.systemui")) {
                foregroundPackage = pkg
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: AccessibilityMonitorService? = null
            private set

        @Volatile
        var foregroundPackage: String = ""
            private set

        val isActive: Boolean get() = instance != null

        private fun componentName(context: Context): String =
            "${context.packageName}/${AccessibilityMonitorService::class.java.canonicalName}"

        /**
         * Enable this accessibility service via shell command (ADB/Shizuku/Root).
         * Returns true if the service is (now) listed in enabled_accessibility_services.
         */
        suspend fun enableViaShell(
            context: Context,
            executor: com.cloudorz.openmonitor.core.common.ShellExecutor,
        ): Boolean {
            try {
                val cn = componentName(context)
                val getResult = executor.execute("settings get secure enabled_accessibility_services")
                val current = getResult.stdout.trim()
                if (current.contains(cn)) return true

                val newValue = if (current.isEmpty() || current == "null") cn else "$current:$cn"
                val putResult = executor.execute("settings put secure enabled_accessibility_services '$newValue'")
                if (!putResult.isSuccess) return false
                executor.execute("settings put secure accessibility_enabled 1")
                return true
            } catch (_: Exception) {
                return false
            }
        }

        fun isEnabled(context: Context): Boolean {
            val enabledServices = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val componentName = "${context.packageName}/${AccessibilityMonitorService::class.java.canonicalName}"
            return enabledServices.contains(componentName)
        }

        fun openSettings(context: Context) {
            val componentName = ComponentName(
                context.packageName,
                AccessibilityMonitorService::class.java.name,
            ).flattenToString()

            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // AOSP Settings uses this to scroll to & highlight the specific service
                putExtra(":settings:fragment_args_key", componentName)
                putExtra(":settings:show_fragment_args", Bundle().apply {
                    putString(":settings:fragment_args_key", componentName)
                })
            }
            context.startActivity(intent)
        }
    }
}
