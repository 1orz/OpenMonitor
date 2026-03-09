package com.cloudorz.monitor.ui.user

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.monitor.core.data.datasource.DaemonClient
import com.cloudorz.monitor.core.data.datasource.DaemonLauncher
import com.cloudorz.monitor.core.data.datasource.DaemonManager
import com.cloudorz.monitor.core.data.datasource.DaemonState
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

    init {
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
            }
        }
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

    fun setPollInterval(intervalMs: Long) {
        prefs.edit().putLong(FloatMonitorService.KEY_POLL_INTERVAL, intervalMs).apply()
        _pollSettings.update { it.copy(intervalMs = intervalMs) }
        try {
            context.startService(FloatMonitorService.updatePollSettingsIntent(context))
        } catch (_: Exception) {}
    }

    private fun fetchVersionInfo(): Pair<String?, String?> {
        val raw = daemonClient.sendCommand("daemon-version")?.trim() ?: return null to null
        val commit = try {
            JSONObject(raw).optString("commit", "").ifEmpty { null }
        } catch (_: Exception) { null }
        return raw to commit
    }
}
