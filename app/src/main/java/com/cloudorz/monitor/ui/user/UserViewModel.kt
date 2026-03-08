package com.cloudorz.monitor.ui.user

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.monitor.core.common.PermissionManager
import com.cloudorz.monitor.core.common.PrivilegeMode
import com.cloudorz.monitor.core.data.datasource.DaemonClient
import com.cloudorz.monitor.core.data.datasource.DaemonLauncher
import com.cloudorz.monitor.core.data.datasource.DaemonManager
import com.cloudorz.monitor.core.data.datasource.DaemonState
import com.cloudorz.monitor.core.model.fps.FpsMethod
import com.cloudorz.monitor.service.FloatMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val daemonClient: DaemonClient,
    private val daemonManager: DaemonManager,
    private val daemonLauncher: DaemonLauncher,
    private val permissionManager: PermissionManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    data class DaemonStatus(
        val checking: Boolean = false,
        val connected: Boolean = false,
        val version: String? = null,
        val currentCommit: String? = null,
        val expectedCommit: String? = null,
        val checkedOnce: Boolean = false,
    )

    data class FpsSettings(
        val method: FpsMethod = FpsMethod.SURFACE_FLINGER,
        val intervalMs: Long = FloatMonitorService.DEFAULT_FPS_INTERVAL,
        val availableMethods: List<FpsMethod> = emptyList(),
    )

    private val prefs = context.getSharedPreferences("monitor_settings", Context.MODE_PRIVATE)

    private val _daemonStatus = MutableStateFlow(DaemonStatus())
    val daemonStatus: StateFlow<DaemonStatus> = _daemonStatus.asStateFlow()

    private val _fpsSettings = MutableStateFlow(
        FpsSettings(
            method = restoreFpsMethod(),
            intervalMs = prefs.getLong(FloatMonitorService.KEY_FPS_INTERVAL, FloatMonitorService.DEFAULT_FPS_INTERVAL),
        )
    )
    val fpsSettings: StateFlow<FpsSettings> = _fpsSettings.asStateFlow()

    init {
        // Sync with DaemonManager state
        viewModelScope.launch {
            daemonManager.state.collect { state ->
                if (state == DaemonState.RUNNING) {
                    val versionInfo = withContext(Dispatchers.IO) { fetchVersionInfo() }
                    _daemonStatus.value = DaemonStatus(
                        connected = true,
                        version = versionInfo.first,
                        currentCommit = versionInfo.second,
                        expectedCommit = daemonLauncher.expectedCommit.ifEmpty { null },
                        checkedOnce = true,
                    )
                } else if (state == DaemonState.FAILED || state == DaemonState.NOT_NEEDED) {
                    _daemonStatus.value = DaemonStatus(
                        connected = false,
                        expectedCommit = daemonLauncher.expectedCommit.ifEmpty { null },
                        checkedOnce = true,
                    )
                }
                updateAvailableMethods()
            }
        }
        // React to permission mode changes
        viewModelScope.launch {
            permissionManager.currentMode.collect { updateAvailableMethods() }
        }
        updateAvailableMethods()
    }

    fun checkDaemon() {
        if (_daemonStatus.value.checking) return
        viewModelScope.launch {
            _daemonStatus.value = _daemonStatus.value.copy(checking = true)
            val result = daemonManager.ensureRunning()
            val alive = result == DaemonState.RUNNING
            val versionInfo = if (alive) withContext(Dispatchers.IO) { fetchVersionInfo() } else null
            _daemonStatus.value = DaemonStatus(
                checking = false,
                connected = alive,
                version = versionInfo?.first,
                currentCommit = versionInfo?.second,
                expectedCommit = daemonLauncher.expectedCommit.ifEmpty { null },
                checkedOnce = true,
            )
        }
    }

    fun restartDaemon() {
        if (_daemonStatus.value.checking) return
        viewModelScope.launch {
            _daemonStatus.value = _daemonStatus.value.copy(checking = true)
            val result = daemonManager.restart()
            val alive = result == DaemonState.RUNNING
            val versionInfo = if (alive) withContext(Dispatchers.IO) { fetchVersionInfo() } else null
            _daemonStatus.value = DaemonStatus(
                checking = false,
                connected = alive,
                version = versionInfo?.first,
                currentCommit = versionInfo?.second,
                expectedCommit = daemonLauncher.expectedCommit.ifEmpty { null },
                checkedOnce = true,
            )
        }
    }

    fun setFpsMethod(method: FpsMethod) {
        prefs.edit().putString(FloatMonitorService.KEY_FPS_METHOD, method.name).apply()
        _fpsSettings.update { it.copy(method = method) }
        notifyServiceFpsSettingsChanged()
    }

    fun setFpsInterval(intervalMs: Long) {
        prefs.edit().putLong(FloatMonitorService.KEY_FPS_INTERVAL, intervalMs).apply()
        _fpsSettings.update { it.copy(intervalMs = intervalMs) }
        notifyServiceFpsSettingsChanged()
    }

    private fun updateAvailableMethods() {
        val mode = permissionManager.currentMode.value
        val hasDaemon = daemonManager.isRunning()
        val hasShell = mode == PrivilegeMode.ROOT || mode == PrivilegeMode.ADB || mode == PrivilegeMode.SHIZUKU
        val methods = buildList {
            if (hasDaemon) add(FpsMethod.DAEMON)
            if (hasShell) add(FpsMethod.SURFACE_FLINGER)
            add(FpsMethod.FRAME_METRICS)
            add(FpsMethod.CHOREOGRAPHER)
        }
        _fpsSettings.update { current ->
            val validMethod = if (current.method in methods) current.method else methods.first()
            current.copy(availableMethods = methods, method = validMethod)
        }
    }

    private fun restoreFpsMethod(): FpsMethod {
        val name = prefs.getString(FloatMonitorService.KEY_FPS_METHOD, null)
            ?: return FpsMethod.SURFACE_FLINGER
        return FpsMethod.entries.find { it.name == name } ?: FpsMethod.SURFACE_FLINGER
    }

    private fun notifyServiceFpsSettingsChanged() {
        try {
            context.startService(FloatMonitorService.updateFpsSettingsIntent(context))
        } catch (_: Exception) {}
    }

    /** Returns (raw response, commit hash) from daemon-version command. */
    private fun fetchVersionInfo(): Pair<String?, String?> {
        val raw = daemonClient.sendCommand("daemon-version")?.trim() ?: return null to null
        val commit = try {
            JSONObject(raw).optString("commit", "").ifEmpty { null }
        } catch (_: Exception) { null }
        return raw to commit
    }
}
