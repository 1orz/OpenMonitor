package com.cloudorz.openmonitor.data.repository

import android.content.Context
import androidx.core.content.edit
import com.cloudorz.openmonitor.core.ui.theme.ColorMode
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeSettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : ThemeSettingsRepository {

    private val prefs = context.getSharedPreferences("monitor_settings", Context.MODE_PRIVATE)

    init {
        // Migrate legacy dark_mode to color_mode on first run
        if (!prefs.contains("color_mode") && prefs.contains("dark_mode")) {
            val legacy = prefs.getInt("dark_mode", 0)
            prefs.edit { putInt("color_mode", legacy) }
        }
    }

    override var colorMode: Int
        get() = prefs.getInt("color_mode", ColorMode.SYSTEM.value)
        set(value) = prefs.edit { putInt("color_mode", value) }

    override var keyColor: Int
        get() = prefs.getInt("key_color", 0)
        set(value) = prefs.edit { putInt("key_color", value) }

    override var colorStyle: String
        get() = prefs.getString("color_style", PaletteStyle.TonalSpot.name) ?: PaletteStyle.TonalSpot.name
        set(value) = prefs.edit { putString("color_style", value) }

    override var colorSpec: String
        get() = prefs.getString("color_spec", ColorSpec.SpecVersion.Default.name) ?: ColorSpec.SpecVersion.Default.name
        set(value) = prefs.edit { putString("color_spec", value) }

    override var pageScale: Float
        get() = prefs.getFloat("page_scale", 1.0f)
        set(value) = prefs.edit { putFloat("page_scale", value) }
}
