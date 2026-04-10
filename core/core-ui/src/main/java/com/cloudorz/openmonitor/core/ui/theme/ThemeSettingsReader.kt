package com.cloudorz.openmonitor.core.ui.theme

import android.content.Context
import android.content.SharedPreferences
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

object ThemeSettingsReader {

    private const val PREFS_NAME = "monitor_settings"

    fun getAppSettings(context: Context): AppSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return getAppSettings(prefs)
    }

    fun getAppSettings(prefs: SharedPreferences): AppSettings {
        // Read color_mode, with fallback to legacy dark_mode key
        val colorModeValue = if (prefs.contains("color_mode")) {
            prefs.getInt("color_mode", ColorMode.SYSTEM.value)
        } else {
            // Migrate from old dark_mode: 0=System, 1=Light, 2=Dark
            prefs.getInt("dark_mode", 0)
        }

        val colorMode = ColorMode.fromValue(colorModeValue)
        val keyColor = prefs.getInt("key_color", 0)
        val paletteStyle = try {
            PaletteStyle.valueOf(prefs.getString("color_style", null) ?: PaletteStyle.TonalSpot.name)
        } catch (_: Exception) {
            PaletteStyle.TonalSpot
        }
        val colorSpec = try {
            ColorSpec.SpecVersion.valueOf(prefs.getString("color_spec", null) ?: ColorSpec.SpecVersion.Default.name)
        } catch (_: Exception) {
            ColorSpec.SpecVersion.Default
        }

        return AppSettings(colorMode, keyColor, paletteStyle, colorSpec)
    }
}
