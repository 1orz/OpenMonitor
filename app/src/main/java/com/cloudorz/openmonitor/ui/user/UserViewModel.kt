package com.cloudorz.openmonitor.ui.user

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.common.PrivilegeMode
import com.cloudorz.openmonitor.core.data.datasource.DaemonClient
import com.cloudorz.openmonitor.core.data.datasource.DaemonLauncher
import com.cloudorz.openmonitor.core.data.datasource.DaemonManager
import com.cloudorz.openmonitor.core.data.datasource.DaemonState
import com.cloudorz.openmonitor.service.FloatMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import androidx.core.content.edit
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val daemonClient: DaemonClient,
    private val daemonManager: DaemonManager,
    private val daemonLauncher: DaemonLauncher,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    data class DaemonStatus(
        val checking: Boolean = false,
        val connected: Boolean = false,
        val version: String? = null,
        val currentCommit: String? = null,
        val expectedCommit: String? = null,
        val runner: String? = null,
        val uptimeSeconds: Long? = null,
        val checkedOnce: Boolean = false,
    )

    data class PollSettings(
        val intervalMs: Long = FloatMonitorService.DEFAULT_POLL_INTERVAL,
    )

    private val prefs = context.getSharedPreferences("monitor_settings", Context.MODE_PRIVATE)

    private val _daemonStatus = MutableStateFlow(DaemonStatus())
    val daemonStatus: StateFlow<DaemonStatus> = _daemonStatus.asStateFlow()

    private val _pollSettings = MutableStateFlow(
        PollSettings(
            intervalMs = prefs.getLong(FloatMonitorService.KEY_POLL_INTERVAL, FloatMonitorService.DEFAULT_POLL_INTERVAL),
        )
    )
    val pollSettings: StateFlow<PollSettings> = _pollSettings.asStateFlow()

    // Daemon log level
    private val _logLevel = MutableStateFlow(
        prefs.getString("daemon_log_level", "warning") ?: "warning"
    )
    val logLevel: StateFlow<String> = _logLevel.asStateFlow()

    /** Daemon binary path for ADB instructions. */
    val daemonBinaryPath: String get() = daemonLauncher.binaryPath

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            daemonManager.state.collect { state ->
                if (state == DaemonState.RUNNING) {
                    _daemonStatus.value = buildStatus(alive = true)
                    sendLogLevel(_logLevel.value)
                    startRefreshLoop()
                } else if (state == DaemonState.FAILED || state == DaemonState.NOT_NEEDED) {
                    stopRefreshLoop()
                    _daemonStatus.value = buildStatus(alive = false)
                }
            }
        }
    }

    private fun startRefreshLoop() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1000)
                val info = fetchDaemonInfo()
                _daemonStatus.update {
                    it.copy(
                        runner = info.runner,
                        uptimeSeconds = info.uptimeSeconds,
                        version = info.version ?: it.version,
                        currentCommit = info.commit ?: it.currentCommit,
                    )
                }
            }
        }
    }

    private fun stopRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = null
    }

    fun checkDaemon() {
        if (_daemonStatus.value.checking) return
        viewModelScope.launch {
            _daemonStatus.value = _daemonStatus.value.copy(checking = true)
            val alive = daemonManager.ensureRunning() == DaemonState.RUNNING
            _daemonStatus.value = buildStatus(alive = alive)
        }
    }

    fun restartDaemon() {
        if (_daemonStatus.value.checking) return
        viewModelScope.launch {
            _daemonStatus.value = _daemonStatus.value.copy(checking = true)
            val alive = daemonManager.restart() == DaemonState.RUNNING
            _daemonStatus.value = buildStatus(alive = alive)
        }
    }

    private suspend fun buildStatus(alive: Boolean): DaemonStatus {
        val info = if (alive) withContext(Dispatchers.IO) { fetchDaemonInfo() } else null
        return DaemonStatus(
            connected = alive,
            version = info?.version,
            currentCommit = info?.commit,
            expectedCommit = daemonLauncher.expectedCommit.ifEmpty { null },
            runner = info?.runner,
            uptimeSeconds = info?.uptimeSeconds,
            checkedOnce = true,
        )
    }

    fun setPollInterval(intervalMs: Long) {
        prefs.edit { putLong(FloatMonitorService.KEY_POLL_INTERVAL, intervalMs) }
        _pollSettings.update { it.copy(intervalMs = intervalMs) }
        try {
            context.startService(FloatMonitorService.updatePollSettingsIntent(context))
        } catch (_: Exception) {}
    }

    fun setDaemonLogLevel(level: String) {
        prefs.edit { putString("daemon_log_level", level) }
        _logLevel.value = level
        sendLogLevel(level)
    }

    /**
     * Switches privilege mode: stops daemon under old mode, restarts under new mode.
     */
    fun switchMode(
        oldMode: PrivilegeMode,
        newMode: PrivilegeMode,
        applyNewMode: () -> Unit,
        onComplete: (DaemonState) -> Unit,
    ) {
        viewModelScope.launch {
            _daemonStatus.update { it.copy(checking = true) }
            val result = daemonManager.switchMode(oldMode, newMode, applyNewMode)
            val alive = result == DaemonState.RUNNING
            _daemonStatus.value = buildStatus(alive = alive)
            onComplete(result)
        }
    }

    private fun sendLogLevel(level: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try { daemonClient.sendCommand("log-level\n$level") } catch (_: Exception) {}
        }
    }

    data class DaemonInfo(
        val version: String? = null,
        val commit: String? = null,
        val runner: String? = null,
        val uptimeSeconds: Long? = null,
    )

    private fun fetchDaemonInfo(): DaemonInfo {
        val raw = daemonClient.sendCommand("ping")?.trim() ?: return DaemonInfo()
        return try {
            val json = JSONObject(raw)
            DaemonInfo(
                version = json.optString("version", "").ifEmpty { null },
                commit = json.optString("commit", "").ifEmpty { null },
                runner = json.optString("runner", "").ifEmpty { null },
                uptimeSeconds = json.optLong("uptime_s", -1).takeIf { it >= 0 },
            )
        } catch (_: Exception) { DaemonInfo() }
    }
}
