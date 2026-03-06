package com.cloudorz.monitor.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.cloudorz.monitor.MainActivity
import com.cloudorz.monitor.R
import java.io.File

class MonitorWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, MonitorWidgetProvider::class.java)
            )
            onUpdate(context, manager, ids)
        }
    }

    companion object {
        private const val TAG = "MonitorWidgetProvider"
        const val ACTION_REFRESH = "com.cloudorz.monitor.WIDGET_REFRESH"

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_monitor)

            // Read CPU load from /proc/stat
            val cpuLoad = readCpuLoad()
            views.setTextViewText(
                R.id.widget_cpu_value,
                if (cpuLoad >= 0) "${cpuLoad}%" else "--%"
            )

            // Read CPU temperature
            val temp = readCpuTemperature()
            views.setTextViewText(
                R.id.widget_temp_value,
                if (temp > 0) "${temp}°C" else "--°C"
            )

            // Read memory usage
            val memPercent = readMemoryUsagePercent()
            views.setTextViewText(
                R.id.widget_mem_value,
                if (memPercent >= 0) "${memPercent}%" else "--%"
            )

            // Click to open app
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_cpu_value, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_temp_value, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_mem_value, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun readCpuLoad(): Int {
            return try {
                val lines1 = File("/proc/stat").readLines()
                val cpu1 = parseCpuLine(lines1.first { it.startsWith("cpu ") })
                Thread.sleep(200)
                val lines2 = File("/proc/stat").readLines()
                val cpu2 = parseCpuLine(lines2.first { it.startsWith("cpu ") })

                val idle1 = cpu1[3] + cpu1[4]
                val idle2 = cpu2[3] + cpu2[4]
                val total1 = cpu1.sum()
                val total2 = cpu2.sum()
                val totalDiff = (total2 - total1).toDouble()
                val idleDiff = (idle2 - idle1).toDouble()
                if (totalDiff > 0) ((totalDiff - idleDiff) / totalDiff * 100).toInt().coerceIn(0, 100)
                else -1
            } catch (e: Exception) {
                Log.d(TAG, "readCpuLoad failed", e)
                -1
            }
        }

        private fun parseCpuLine(line: String): LongArray {
            return line.trim().split("\\s+".toRegex())
                .drop(1)
                .map { it.toLongOrNull() ?: 0L }
                .toLongArray()
        }

        private fun readCpuTemperature(): Int {
            return try {
                for (i in 0..20) {
                    val typeFile = File("/sys/class/thermal/thermal_zone$i/type")
                    if (!typeFile.exists() || !typeFile.canRead()) continue
                    val type = typeFile.readText().trim()
                    if (type.contains("cpu", ignoreCase = true) ||
                        type.contains("tsens_tz_sensor", ignoreCase = true) ||
                        type.contains("mtktscpu", ignoreCase = true)
                    ) {
                        val tempFile = File("/sys/class/thermal/thermal_zone$i/temp")
                        if (!tempFile.canRead()) continue
                        val raw = tempFile.readText().trim().toIntOrNull() ?: continue
                        return if (raw > 1000) raw / 1000 else raw
                    }
                }
                -1
            } catch (e: Exception) {
                Log.d(TAG, "readCpuTemperature failed", e)
                -1
            }
        }

        private fun readMemoryUsagePercent(): Int {
            return try {
                val lines = File("/proc/meminfo").readLines()
                val map = mutableMapOf<String, Long>()
                for (line in lines) {
                    val parts = line.split(":")
                    if (parts.size == 2) {
                        val key = parts[0].trim()
                        val value = parts[1].trim().split("\\s+".toRegex())[0].toLongOrNull() ?: 0L
                        map[key] = value
                    }
                }
                val total = map["MemTotal"] ?: return -1
                val available = map["MemAvailable"] ?: return -1
                if (total > 0) ((total - available) * 100 / total).toInt().coerceIn(0, 100) else -1
            } catch (e: Exception) {
                Log.d(TAG, "readMemoryUsagePercent failed", e)
                -1
            }
        }
    }
}
