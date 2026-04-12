package com.cloudorz.openmonitor.core.ui

import android.content.Context
import android.content.SharedPreferences
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView

/**
 * Centralized haptic feedback manager with global enable/disable control.
 * Initialize once in Application.onCreate, then all haptic calls respect the setting.
 */
object HapticFeedbackManager {
    private const val PREF_KEY = "haptic_enabled"
    @Volatile
    private var _enabled = true
    val enabled: Boolean get() = _enabled

    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("monitor_settings", Context.MODE_PRIVATE)
        _enabled = prefs.getBoolean(PREF_KEY, true)
        listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == PREF_KEY) _enabled = sp.getBoolean(PREF_KEY, true)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun performClick(view: View) {
        if (_enabled) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        _enabled = enabled
        context.getSharedPreferences("monitor_settings", Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_KEY, enabled).apply()
    }
}

/**
 * Perform a light click haptic feedback (respects global setting).
 */
fun View.hapticClick() {
    HapticFeedbackManager.performClick(this)
}

/**
 * Modifier that wraps [clickable] with haptic feedback (respects global setting).
 */
fun Modifier.hapticClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val view = LocalView.current
    this.clickable(enabled = enabled) {
        view.hapticClick()
        onClick()
    }
}
