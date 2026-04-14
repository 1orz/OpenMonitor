package com.cloudorz.openmonitor.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

@Composable
fun MonitorTheme(
    appSettings: AppSettings? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val currentAppSettings = appSettings ?: ThemeSettingsReader.getAppSettings(context)

    MaterialMonitorTheme(
        appSettings = currentAppSettings,
        content = content,
    )
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
