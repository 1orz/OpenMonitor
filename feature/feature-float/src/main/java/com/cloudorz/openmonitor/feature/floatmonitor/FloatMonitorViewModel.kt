package com.cloudorz.openmonitor.feature.floatmonitor

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.common.PermissionManager
import com.cloudorz.openmonitor.core.common.PrivilegeMode
import com.cloudorz.openmonitor.service.AccessibilityMonitorService
import com.cloudorz.openmonitor.service.FloatMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OverlayMode {
    AUTO,
    OVERLAY_ONLY,
    ACCESSIBILITY_ONLY,
}

data class FloatMonitorUiState(
    val hasOverlayPermission: Boolean = false,
    val hasAccessibilityService: Boolean = false,
    val enabledMonitors: Set<FloatMonitorType> = emptySet(),
    val overlayMode: OverlayMode = OverlayMode.AUTO,
) {
    val canShowOverlay: Boolean get() = when (overlayMode) {
        OverlayMode.AUTO -> hasOverlayPermission || hasAccessibilityService
        OverlayMode.OVERLAY_ONLY -> hasOverlayPermission
        OverlayMode.ACCESSIBILITY_ONLY -> hasAccessibilityService
    }
    val bothPermissionsGranted: Boolean get() = hasOverlayPermission && hasAccessibilityService
}

@HiltViewModel
class FloatMonitorViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val permissionManager: PermissionManager,
) : ViewModel() {

    companion object {
        private const val TAG = "FloatMonitorVM"
        private const val PREFS_NAME = "monitor_settings"
        private const val KEY_OVERLAY_MODE = "overlay_mode"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _snackbarEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val snackbarEvent: SharedFlow<Int> = _snackbarEvent.asSharedFlow()

    private val _uiState = MutableStateFlow(
        FloatMonitorUiState(
            hasOverlayPermission = Settings.canDrawOverlays(context),
            hasAccessibilityService = AccessibilityMonitorService.isEnabled(context),
            enabledMonitors = restoreEnabledMonitors(),
            overlayMode = restoreOverlayMode(),
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

    fun onOverlayModeChanged(mode: OverlayMode) {
        prefs.edit { putString(KEY_OVERLAY_MODE, mode.name) }
        _uiState.update { it.copy(overlayMode = mode) }

        // If monitors are running, restart the service to apply the new mode
        val activeMonitors = _uiState.value.enabledMonitors
        if (activeMonitors.isNotEmpty()) {
            // Stop current service
            context.startService(FloatMonitorService.stopIntent(context))
            // Restart with new mode after a brief delay
            viewModelScope.launch {
                delay(500)
                ensureMonitorsRunning(activeMonitors)
            }
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

        // Auto-enable accessibility via shell if no overlay capability
        if (!_uiState.value.canShowOverlay) {
            val mode = permissionManager.currentMode.value
            if (mode != PrivilegeMode.BASIC && _uiState.value.overlayMode != OverlayMode.OVERLAY_ONLY) {
                viewModelScope.launch { tryAutoEnableAccessibility(savedMonitors) }
            }
            return
        }

        // Re-launch saved monitors if service was killed
        if (savedMonitors.isNotEmpty() && _uiState.value.canShowOverlay) {
            ensureMonitorsRunning(savedMonitors)
        }
    }

    private suspend fun tryAutoEnableAccessibility(savedMonitors: Set<FloatMonitorType>) {
        try {
            val executor = permissionManager.getExecutor()
            val success = AccessibilityMonitorService.enableViaShell(context, executor)
            if (success) {
                delay(1500) // Wait for system to activate the service
                val enabled = AccessibilityMonitorService.isEnabled(context)
                _uiState.update { it.copy(hasAccessibilityService = enabled) }
                if (enabled && savedMonitors.isNotEmpty()) {
                    ensureMonitorsRunning(savedMonitors)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-enable accessibility failed", e)
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

    private fun restoreOverlayMode(): OverlayMode {
        val name = prefs.getString(KEY_OVERLAY_MODE, OverlayMode.AUTO.name) ?: OverlayMode.AUTO.name
        return try {
            OverlayMode.valueOf(name)
        } catch (_: Exception) {
            OverlayMode.AUTO
        }
    }

    private fun startMonitor(type: FloatMonitorType) {
        val startIntent = FloatMonitorService.startIntent(context)
        context.startForegroundService(startIntent)

        val addIntent = FloatMonitorService.addMonitorIntent(context, type.name)
        context.startService(addIntent)
    }

    fun tryEnableAccessibility(onManualRequired: () -> Unit) {
        val mode = permissionManager.currentMode.value
        if (mode == PrivilegeMode.BASIC) {
            onManualRequired()
            return
        }
        viewModelScope.launch {
            try {
                val executor = permissionManager.getExecutor()
                val success = AccessibilityMonitorService.enableViaShell(context, executor)
                if (success) {
                    delay(1500)
                    val enabled = AccessibilityMonitorService.isEnabled(context)
                    _uiState.update { it.copy(hasAccessibilityService = enabled) }
                    if (!enabled) onManualRequired()
                } else {
                    onManualRequired()
                }
            } catch (_: Exception) {
                onManualRequired()
            }
        }
    }

    private fun stopMonitor(type: FloatMonitorType) {
        val removeIntent = FloatMonitorService.removeMonitorIntent(context, type.name)
        context.startService(removeIntent)
    }
}
