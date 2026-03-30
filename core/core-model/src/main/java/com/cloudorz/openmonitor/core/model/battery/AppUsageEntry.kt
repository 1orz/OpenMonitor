package com.cloudorz.openmonitor.core.model.battery

import android.graphics.Bitmap

data class AppUsageEntry(
    val packageName: String,
    val appLabel: String,
    val iconBitmap: Bitmap?,
    val avgPowerW: Double,
    val avgTemp: Double,
    val maxTemp: Double,
    val durationMs: Long,
)
