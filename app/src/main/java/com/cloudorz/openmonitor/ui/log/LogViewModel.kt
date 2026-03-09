package com.cloudorz.openmonitor.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.common.AppLogEntry
import com.cloudorz.openmonitor.core.common.AppLogger
import com.cloudorz.openmonitor.core.common.ShellExecutor
import com.cloudorz.openmonitor.core.data.datasource.DaemonLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(
    private val shellExecutor: ShellExecutor,
) : ViewModel() {

    companion object {
        private val DAEMON_LOG_PATH = DaemonLauncher.LOG_PATH
        private const val DAEMON_POLL_INTERVAL_MS = 3_000L
        private const val DAEMON_TAIL_LINES = 300
    }

    /** App-side in-memory logs written via AppLogger. */
    val appLogs: StateFlow<List<AppLogEntry>> = AppLogger.entries

    private val _daemonLogs = MutableStateFlow<List<String>>(emptyList())
    /** Raw log lines from daemon.log (tail ${DAEMON_TAIL_LINES}), refreshed every 3 s. */
    val daemonLogs: StateFlow<List<String>> = _daemonLogs.asStateFlow()

    private val _daemonLogStatus = MutableStateFlow<String?>(null)
    /** Non-null when daemon.log cannot be read (e.g. daemon not started). */
    val daemonLogStatus: StateFlow<String?> = _daemonLogStatus.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                fetchDaemonLogs()
                delay(DAEMON_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun fetchDaemonLogs() {
        val result = shellExecutor.execute(
            "tail -n $DAEMON_TAIL_LINES $DAEMON_LOG_PATH 2>/dev/null"
        )
        if (result.isSuccess && result.stdout.isNotBlank()) {
            _daemonLogs.value = result.stdout.lines().filter { it.isNotBlank() }
            _daemonLogStatus.value = null
        } else if (_daemonLogs.value.isEmpty()) {
            _daemonLogStatus.value = "暂无 daemon 日志（daemon 未运行或 $DAEMON_LOG_PATH 不存在）"
        }
    }

    fun clearAppLogs() = AppLogger.clear()

    fun refreshDaemonLogs() {
        viewModelScope.launch { fetchDaemonLogs() }
    }

    fun clearDaemonLogs() {
        viewModelScope.launch {
            shellExecutor.execute("echo -n > $DAEMON_LOG_PATH 2>/dev/null")
            _daemonLogs.value = emptyList()
            _daemonLogStatus.value = null
        }
    }
}
