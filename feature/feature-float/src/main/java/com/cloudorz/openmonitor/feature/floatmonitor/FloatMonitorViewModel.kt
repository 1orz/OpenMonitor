package com.cloudorz.openmonitor.feature.floatmonitor

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import com.cloudorz.openmonitor.service.FloatMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class FloatMonitorUiState(
    val hasOverlayPermission: Boolean = false,
    val enabledMonitors: Set<FloatMonitorType> = emptySet(),
) {
    val canShowOverlay: Boolean get() = hasOverlayPermission
}

@HiltViewModel
class FloatMonitorViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    companion object {
        private const val PREFS_NAME = "monitor_settings"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _snackbarEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val snackbarEvent: SharedFlow<Int> = _snackbarEvent.asSharedFlow()

    private val _uiState = MutableStateFlow(
        FloatMonitorUiState(
            hasOverlayPermission = Settings.canDrawOverlays(context),
            enabledMonitors = restoreEnabledMonitors(),
        )
    )
    val uiState: StateFlow<FloatMonitorUiState> = _uiState

    fun onToggleMonitor(type: FloatMonitorType, enabled: Boolean) {
        if (!_uiState.value.canShowOverlay) {
            refreshPermission()
            _snackbarEvent.tryEmit(com.cloudorz.openmonitor.core.ui.R.string.permission_required_toast)
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
                enabledMonitors = savedMonitors,
            )
        }

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
