package com.cloudorz.openmonitor.ui.log

import android.os.Process
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.common.AppLogEntry
import com.cloudorz.openmonitor.core.data.datasource.DaemonLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(
    private val daemonLauncher: DaemonLauncher,
) : ViewModel() {

    companion object {
        private const val APP_LOG_POLL_INTERVAL_MS = 2_000L
        private const val DAEMON_POLL_INTERVAL_MS = 3_000L
        private const val DAEMON_TAIL_LINES = 300
        private const val APP_LOG_MAX_LINES = 500
    }

    private val _appLogs = MutableStateFlow<List<AppLogEntry>>(emptyList())
    /** App-side logs read from logcat for current process. */
    val appLogs: StateFlow<List<AppLogEntry>> = _appLogs.asStateFlow()

    private val _daemonLogs = MutableStateFlow<List<String>>(emptyList())
    /** Raw log lines from daemon.log (tail N), refreshed every 3 s. */
    val daemonLogs: StateFlow<List<String>> = _daemonLogs.asStateFlow()

    private val _daemonLogStatus = MutableStateFlow<String?>(null)
    /** Non-null when daemon.log cannot be read (e.g. daemon not started). */
    val daemonLogStatus: StateFlow<String?> = _daemonLogStatus.asStateFlow()

    /** Daemon log file path derived from DaemonLauncher.dataDir. */
    private val daemonLogPath: String
        get() = "${daemonLauncher.dataDir.absolutePath}/daemon.log"

    init {
        // Poll app logs via logcat
        viewModelScope.launch {
            while (isActive) {
                fetchAppLogs()
                delay(APP_LOG_POLL_INTERVAL_MS)
            }
        }
        // Poll daemon logs from file
        viewModelScope.launch {
            while (isActive) {
                fetchDaemonLogs()
                delay(DAEMON_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun fetchAppLogs() = withContext(Dispatchers.IO) {
        try {
            val pid = Process.myPid()
            // -d: dump and exit, -v threadtime: detailed format, --pid: filter by PID
            val process = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-v", "threadtime", "--pid=$pid", "-t", "$APP_LOG_MAX_LINES")
            )
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val entries = mutableListOf<AppLogEntry>()
            reader.useLines { lines ->
                lines.forEach { line ->
                    parseLogcatLine(line)?.let { entries.add(it) }
                }
            }
            process.waitFor()
            if (entries.isNotEmpty()) {
                _appLogs.value = entries
            }
        } catch (_: Exception) { }
    }

    /**
     * Parses a logcat threadtime line:
     * "03-12 10:23:45.123  1234  5678 D TagName : message text"
     */
    private fun parseLogcatLine(line: String): AppLogEntry? {
        // Skip header lines like "--------- beginning of main"
        if (line.startsWith("-")) return null
        // Format: "MM-DD HH:MM:SS.mmm  PID  TID LEVEL TAG: message"
        val regex = Regex("""^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+\d+\s+\d+\s+([VDIWEF])\s+(.+?)\s*:\s*(.*)$""")
        val match = regex.matchEntire(line) ?: return null
        val (time, level, tag, message) = match.destructured
        return AppLogEntry(
            time = time.substringAfter(' '), // Keep only HH:MM:SS.mmm
            level = level[0],
            tag = tag.trim(),
            message = message,
        )
    }

    private suspend fun fetchDaemonLogs() = withContext(Dispatchers.IO) {
        try {
            val logFile = java.io.File(daemonLogPath)
            if (logFile.exists() && logFile.canRead()) {
                val lines = logFile.readLines()
                    .filter { it.isNotBlank() }
                    .takeLast(DAEMON_TAIL_LINES)
                if (lines.isNotEmpty()) {
                    _daemonLogs.value = lines
                    _daemonLogStatus.value = null
                    return@withContext
                }
            }
            // Fallback: try reading via shell (root access might be needed)
            val process = Runtime.getRuntime().exec(
                arrayOf("sh", "-c", "tail -n $DAEMON_TAIL_LINES '$daemonLogPath' 2>/dev/null")
            )
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = reader.readLines().filter { it.isNotBlank() }
            process.waitFor()
            if (lines.isNotEmpty()) {
                _daemonLogs.value = lines
                _daemonLogStatus.value = null
            } else if (_daemonLogs.value.isEmpty()) {
                _daemonLogStatus.value = "暂无 daemon 日志（daemon 未运行）\n路径: $daemonLogPath"
            }
        } catch (_: Exception) {
            if (_daemonLogs.value.isEmpty()) {
                _daemonLogStatus.value = "暂无 daemon 日志（daemon 未运行）"
            }
        }
    }

    fun clearAppLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Clear logcat for current process
                Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor()
                _appLogs.value = emptyList()
            } catch (_: Exception) { }
        }
    }

    fun refreshDaemonLogs() {
        viewModelScope.launch { fetchDaemonLogs() }
    }

    fun clearDaemonLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logFile = java.io.File(daemonLogPath)
                if (logFile.exists()) logFile.writeText("")
            } catch (_: Exception) { }
            _daemonLogs.value = emptyList()
            _daemonLogStatus.value = null
        }
    }
}
