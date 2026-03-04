package com.cloudorz.monitor.feature.floatmonitor

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import com.cloudorz.monitor.service.FloatMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class FloatMonitorUiState(
    val hasOverlayPermission: Boolean = false,
    val enabledMonitors: Set<FloatMonitorType> = emptySet(),
)

@HiltViewModel
class FloatMonitorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FloatMonitorUiState(
            hasOverlayPermission = Settings.canDrawOverlays(context),
        )
    )
    val uiState: StateFlow<FloatMonitorUiState> = _uiState

    fun onToggleMonitor(type: FloatMonitorType, enabled: Boolean) {
        if (!Settings.canDrawOverlays(context)) {
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
        _uiState.update { it.copy(hasOverlayPermission = Settings.canDrawOverlays(context)) }
    }

    private fun startMonitor(type: FloatMonitorType) {
        // Ensure service is started as foreground
        val startIntent = FloatMonitorService.startIntent(context)
        context.startForegroundService(startIntent)

        // Add specific monitor
        val addIntent = FloatMonitorService.addMonitorIntent(context, type.name)
        context.startService(addIntent)
    }

    private fun stopMonitor(type: FloatMonitorType) {
        val removeIntent = FloatMonitorService.removeMonitorIntent(context, type.name)
        context.startService(removeIntent)
    }
}
