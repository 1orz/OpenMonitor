package com.cloudorz.openmonitor.core.model.display

data class DisplayInfo(
    val widthPixels: Int = 0,
    val heightPixels: Int = 0,
    val refreshRateHz: Float = 0f,
    val maxRefreshRateHz: Float = 0f,
    val supportedModes: List<DisplayMode> = emptyList(),
    val densityDpi: Int = 0,
    val densityBucket: String = "",
    val physicalSizeInch: Float = 0f,
    val physicalSizeMM: Int = 0,
    val ppi: Int = 0,
    val aspectRatio: String = "",
    val hdrCapabilities: List<String> = emptyList(),
    val isHdr: Boolean = false,
    val wideColorGamut: Boolean = false,
    val panelName: String = "",
    val displayName: String = "",
)

data class DisplayMode(
    val width: Int,
    val height: Int,
    val refreshRate: Float,
)
