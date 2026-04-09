package com.cloudorz.openmonitor.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

/**
 * OpenMonitor application theme with dual UI framework support (Material 3 + MIUIX).
 *
 * Dispatches to [MaterialMonitorTheme] or [MiuixMonitorTheme] based on [uiMode].
 * Supports dynamic color, custom seed colors, AMOLED dark, and palette styles
 * via the [AppSettings] configuration.
 */
@Composable
fun MonitorTheme(
    appSettings: AppSettings? = null,
    uiMode: UiMode = LocalUiMode.current,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val currentAppSettings = appSettings ?: ThemeSettingsReader.getAppSettings(context)

    when (uiMode) {
        UiMode.Material -> MaterialMonitorTheme(
            appSettings = currentAppSettings,
            content = content,
        )
        UiMode.Miuix -> MiuixMonitorTheme(
            appSettings = currentAppSettings,
            content = content,
        )
    }
}

@Composable
@ReadOnlyComposable
fun isInDarkTheme(): Boolean {
    return when (LocalColorMode.current) {
        1, 4 -> false
        2, 5, 6 -> true
        else -> isSystemInDarkTheme()
    }
}
