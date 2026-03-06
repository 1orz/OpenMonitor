package com.cloudorz.monitor.feature.floatmonitor

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import com.cloudorz.monitor.core.model.fps.FpsMethod
import com.cloudorz.monitor.service.AccessibilityMonitorService
import com.cloudorz.monitor.service.FloatMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class FloatMonitorUiState(
    val hasOverlayPermission: Boolean = false,
    val hasAccessibilityService: Boolean = false,
    val enabledMonitors: Set<FloatMonitorType> = emptySet(),
    val fpsMethod: FpsMethod = FpsMethod.SURFACE_FLINGER,
    val fpsIntervalMs: Long = FloatMonitorService.DEFAULT_FPS_INTERVAL,
) {
    val canShowOverlay: Boolean get() = hasOverlayPermission || hasAccessibilityService
}

@HiltViewModel
class FloatMonitorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val prefs = context.getSharedPreferences("monitor_settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        FloatMonitorUiState(
            hasOverlayPermission = Settings.canDrawOverlays(context),
            hasAccessibilityService = AccessibilityMonitorService.isEnabled(context),
            enabledMonitors = restoreEnabledMonitors(),
            fpsMethod = restoreFpsMethod(),
            fpsIntervalMs = prefs.getLong(FloatMonitorService.KEY_FPS_INTERVAL, FloatMonitorService.DEFAULT_FPS_INTERVAL),
        )
    )
    val uiState: StateFlow<FloatMonitorUiState> = _uiState

    fun onToggleMonitor(type: FloatMonitorType, enabled: Boolean) {
        if (!_uiState.value.canShowOverlay) {
            refreshPermission()
            return
        }

        if (enabled) {
            startMonitor(type)
        } else {
            stopMonitor(type)
        }

        _uiState.update { state ->
            val newSet = if (enabled) state.enabledMonitors + type else state.enabledMonitors - type
            state.copy(enabledMonitors = newSet)
        }
    }

    fun refreshPermission() {
        val savedMonitors = restoreEnabledMonitors()
        _uiState.update {
            it.copy(
                hasOverlayPermission = Settings.canDrawOverlays(context),
                hasAccessibilityService = AccessibilityMonitorService.isEnabled(context),
                enabledMonitors = savedMonitors,
            )
        }
        // Re-launch saved monitors if service was killed
        if (savedMonitors.isNotEmpty() && _uiState.value.canShowOverlay) {
            ensureMonitorsRunning(savedMonitors)
        }
    }

    private fun ensureMonitorsRunning(monitors: Set<FloatMonitorType>) {
        // Start foreground service first
        context.startForegroundService(FloatMonitorService.startIntent(context))
        // Re-add each monitor (service ignores duplicates via isWindowActive check)
        for (type in monitors) {
            context.startService(FloatMonitorService.addMonitorIntent(context, type.name))
        }
    }

    fun setFpsMethod(method: FpsMethod) {
        prefs.edit().putString(FloatMonitorService.KEY_FPS_METHOD, method.name).apply()
        _uiState.update { it.copy(fpsMethod = method) }
        // Notify running service
        notifyServiceRestart()
    }

    fun setFpsInterval(intervalMs: Long) {
        prefs.edit().putLong(FloatMonitorService.KEY_FPS_INTERVAL, intervalMs).apply()
        _uiState.update { it.copy(fpsIntervalMs = intervalMs) }
        notifyServiceRestart()
    }

    private fun restoreFpsMethod(): FpsMethod {
        val name = prefs.getString(FloatMonitorService.KEY_FPS_METHOD, null) ?: return FpsMethod.SURFACE_FLINGER
        return FpsMethod.entries.find { it.name == name } ?: FpsMethod.SURFACE_FLINGER
    }

    private fun notifyServiceRestart() {
        // Tell running service to reload FPS settings without removing windows
        val enabled = _uiState.value.enabledMonitors
        val hasFps = FloatMonitorType.FPS_RECORDER in enabled || FloatMonitorType.MINI_MONITOR in enabled
        if (hasFps) {
            context.startService(FloatMonitorService.updateFpsSettingsIntent(context))
        }
    }

    private fun restoreEnabledMonitors(): Set<FloatMonitorType> {
        val saved = prefs.getStringSet("enabled_monitors", emptySet()) ?: emptySet()
        return saved.mapNotNull { name ->
            FloatMonitorType.entries.find { it.name == name }
        }.toSet()
    }

    private fun startMonitor(type: FloatMonitorType) {
        val startIntent = FloatMonitorService.startIntent(context)
        context.startForegroundService(startIntent)

        val addIntent = FloatMonitorService.addMonitorIntent(context, type.name)
        context.startService(addIntent)
    }

    private fun stopMonitor(type: FloatMonitorType) {
        val removeIntent = FloatMonitorService.removeMonitorIntent(context, type.name)
        context.startService(removeIntent)
    }
}
