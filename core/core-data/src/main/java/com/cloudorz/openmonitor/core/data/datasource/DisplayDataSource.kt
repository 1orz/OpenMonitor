package com.cloudorz.openmonitor.core.data.datasource

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import com.cloudorz.openmonitor.core.common.SysfsReader
import com.cloudorz.openmonitor.core.model.display.DisplayInfo
import com.cloudorz.openmonitor.core.model.display.DisplayMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Singleton
class DisplayDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sysfsReader: SysfsReader,
) {
    suspend fun getDisplayInfo(): DisplayInfo = withContext(Dispatchers.IO) {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return@withContext DisplayInfo()

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)

        val currentMode = display.mode
        val widthPixels = currentMode.physicalWidth
        val heightPixels = currentMode.physicalHeight
        val refreshRate = currentMode.refreshRate

        // Supported modes
        val modes = display.supportedModes.map { mode ->
            DisplayMode(
                width = mode.physicalWidth,
                height = mode.physicalHeight,
                refreshRate = mode.refreshRate,
            )
        }

        // Max refresh rate from all modes
        val maxRefreshRate = modes.maxOfOrNull { it.refreshRate } ?: refreshRate

        // Physical size and PPI — following DevCheck's exact formula
        val widthInch = widthPixels / metrics.xdpi.toDouble()
        val heightInch = heightPixels / metrics.ydpi.toDouble()
        val diagonalInch = sqrt(widthInch * widthInch + heightInch * heightInch).toFloat()
        val diagonalMM = (diagonalInch * 25.4f + 0.5f).roundToInt()
        val ppi = if (diagonalInch > 0) {
            (sqrt((widthPixels.toDouble() * widthPixels + heightPixels.toDouble() * heightPixels)) / diagonalInch).toInt()
        } else 0

        // Density bucket
        val densityBucket = when {
            metrics.densityDpi <= DisplayMetrics.DENSITY_LOW -> "ldpi"
            metrics.densityDpi <= DisplayMetrics.DENSITY_MEDIUM -> "mdpi"
            metrics.densityDpi <= DisplayMetrics.DENSITY_HIGH -> "hdpi"
            metrics.densityDpi <= DisplayMetrics.DENSITY_XHIGH -> "xhdpi"
            metrics.densityDpi <= DisplayMetrics.DENSITY_XXHIGH -> "xxhdpi"
            else -> "xxxhdpi"
        }

        // Aspect ratio — DevCheck style lookup table
        val aspectRatio = computeAspectRatio(widthPixels, heightPixels)

        // HDR capabilities
        val hdrTypes = mutableListOf<String>()
        var isHdr = false
        val hdrCaps = display.hdrCapabilities
        if (hdrCaps != null) {
            @Suppress("DEPRECATION")
            for (type in hdrCaps.supportedHdrTypes) {
                when (type) {
                    Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> hdrTypes.add("Dolby Vision")
                    Display.HdrCapabilities.HDR_TYPE_HDR10 -> hdrTypes.add("HDR10")
                    Display.HdrCapabilities.HDR_TYPE_HLG -> hdrTypes.add("Hybrid Log-Gamma")
                    Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> hdrTypes.add("HDR10+")
                }
            }
            isHdr = hdrTypes.isNotEmpty()
        }

        // Wide color gamut
        val wideColorGamut = display.isWideColorGamut

        // Panel name — DevCheck reads from sysfs or getprop
        val panelName = readPanelName()

        // Display name
        val displayName = display.name ?: ""

        DisplayInfo(
            widthPixels = widthPixels,
            heightPixels = heightPixels,
            refreshRateHz = refreshRate,
            maxRefreshRateHz = maxRefreshRate,
            supportedModes = modes,
            densityDpi = metrics.densityDpi,
            densityBucket = densityBucket,
            physicalSizeInch = diagonalInch,
            physicalSizeMM = diagonalMM,
            ppi = ppi,
            aspectRatio = aspectRatio,
            hdrCapabilities = hdrTypes,
            isHdr = isHdr,
            wideColorGamut = wideColorGamut,
            panelName = panelName,
            displayName = displayName,
        )
    }

    /** Read display panel name from sysfs or system property (DevCheck approach). */
    private suspend fun readPanelName(): String {
        // Method 1: sysfs panel_info (Qualcomm devices)
        val panelInfoPaths = listOf(
            "/sys/devices/virtual/graphics/fb0/msm_fb_panel_info",
            "/sys/class/graphics/fb0/msm_fb_panel_info",
        )
        for (path in panelInfoPaths) {
            val content = sysfsReader.readString(path)
            if (content != null && content.contains("panel_name")) {
                val idx = content.indexOf("panel_name")
                if (idx >= 0) {
                    val name = content.substring(idx + 11).trim()
                        .split("\n").firstOrNull()?.trim()
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        // Method 2: DRM sysfs paths (newer kernels)
        val drmPaths = listOf(
            "/sys/class/drm/card0-DSI-1/panel_info",
            "/sys/class/drm/card0/device/panel_info",
        )
        for (path in drmPaths) {
            val content = sysfsReader.readString(path)
            if (content != null && content.isNotBlank()) {
                return content.trim().split("\n").firstOrNull()?.trim() ?: ""
            }
        }
        // Method 3: system property
        val prop = getSystemProperty("sys.panel.display")
        if (!prop.isNullOrBlank() && prop.length > 2) return prop
        return ""
    }

    private fun getSystemProperty(key: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", key))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()?.trim()
            reader.close()
            process.waitFor()
            result?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    /** Compute aspect ratio using DevCheck's lookup-table approach. */
    private fun computeAspectRatio(w: Int, h: Int): String {
        if (w == 0 || h == 0) return ""
        val maxDim = maxOf(w, h)
        val minDim = minOf(w, h)

        val ratio = maxDim.toDouble() / minDim
        val ratioStr = "%.3f".format(ratio)

        // DevCheck-style lookup table
        val known = when (ratioStr) {
            "1.000" -> "1:1"
            "1.333" -> "4:3"
            "1.500" -> "3:2"
            "1.600" -> "16:10"
            "1.667" -> "15:9"
            "1.707" -> "17:10"
            "1.778" -> "16:9"
            "1.800" -> "18:10"
            "1.857" -> "13:7"
            "2.000" -> "18:9"
            "2.056" -> "18.5:9"
            "2.111" -> "19:9"
            "2.167" -> "19.5:9"
            "2.200" -> "19.8:9"
            "2.222" -> "20:9"
            "2.333" -> "21:9"
            "2.375" -> "21.375:9"
            "2.400" -> "21.6:9"
            "2.444" -> "22:9"
            else -> null
        }
        if (known != null) return known

        // Fallback: compute GCD-based ratio with classification suffix
        val g = gcd(maxDim, minDim)
        val ratioW = maxDim / g
        val ratioH = minDim / g
        val suffix = when {
            ratio >= 2.0 -> "Full Vision"
            ratio >= 1.9 -> "FHD+"
            ratio >= 1.7 -> "QHD+"
            else -> ""
        }
        return if (suffix.isNotEmpty()) "$ratioW:$ratioH ($suffix)" else "$ratioW:$ratioH"
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
}
