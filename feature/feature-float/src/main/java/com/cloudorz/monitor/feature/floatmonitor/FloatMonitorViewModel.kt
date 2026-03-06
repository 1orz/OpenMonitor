package com.cloudorz.monitor.feature.floatmonitor

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
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
        _uiState.update {
            it.copy(
                hasOverlayPermission = Settings.canDrawOverlays(context),
                hasAccessibilityService = AccessibilityMonitorService.isEnabled(context),
                enabledMonitors = restoreEnabledMonitors(),
            )
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
