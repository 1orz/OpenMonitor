package com.cloudorz.openmonitor.ui.log

import android.os.Process
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.common.AppLogEntry
import com.cloudorz.openmonitor.core.common.AppLogger
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
import java.io.File
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
        private const val APP_LOG_MAX_LINES = 800
    }

    private val _appLogs = MutableStateFlow<List<AppLogEntry>>(emptyList())
    val appLogs: StateFlow<List<AppLogEntry>> = _appLogs.asStateFlow()

    /** Available log date files (newest first) for date picker. */
    private val _logDates = MutableStateFlow<List<String>>(emptyList())
    val logDates: StateFlow<List<String>> = _logDates.asStateFlow()

    /** Currently selected date, null = today (live). */
    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate.asStateFlow()

    private val _daemonLogs = MutableStateFlow<List<String>>(emptyList())
    val daemonLogs: StateFlow<List<String>> = _daemonLogs.asStateFlow()

    private val _daemonLogStatus = MutableStateFlow<String?>(null)
    val daemonLogStatus: StateFlow<String?> = _daemonLogStatus.asStateFlow()

    private val daemonLogPath: String
        get() = "${daemonLauncher.dataDir.absolutePath}/daemon.log"

    init {
        viewModelScope.launch {
            refreshLogDates()
            while (isActive) {
                fetchAppLogs()
                delay(APP_LOG_POLL_INTERVAL_MS)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                fetchDaemonLogs()
                delay(DAEMON_POLL_INTERVAL_MS)
            }
        }
    }

    fun selectDate(date: String?) {
        _selectedDate.value = date
        viewModelScope.launch { fetchAppLogs() }
    }

    private suspend fun refreshLogDates() = withContext(Dispatchers.IO) {
        val files = AppLogger.listLogFiles()
        _logDates.value = files.map { it.nameWithoutExtension }
    }

    private suspend fun fetchAppLogs() = withContext(Dispatchers.IO) {
        val date = _selectedDate.value
        if (date != null) {
            // Historical: read from xLog file
            loadFromFile(date)
        } else {
            // Live: read from logcat (captures all Log.* + XLog.*)
            loadFromLogcat()
        }
        refreshLogDates()
    }

    private fun loadFromFile(date: String) {
        val dir = AppLogger.logDir ?: return
        val file = File(dir, "$date.log")
        if (!file.exists()) {
            _appLogs.value = emptyList()
            return
        }
        val lines = file.readLines()
        val entries = lines.mapNotNull { parseXLogLine(it) }.takeLast(APP_LOG_MAX_LINES)
        _appLogs.value = entries
    }

    private fun loadFromLogcat() {
        try {
            val pid = Process.myPid()
            val process = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-v", "threadtime", "--pid=$pid", "-t", "$APP_LOG_MAX_LINES")
            )
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val entries = mutableListOf<AppLogEntry>()
            reader.useLines { lines ->
                lines.forEach { line -> parseLogcatLine(line)?.let { entries.add(it) } }
            }
            process.waitFor()
            if (entries.isNotEmpty()) {
                _appLogs.value = entries
            }
        } catch (_: Exception) { }
    }

    /**
     * Parses xLog file line format:
     * "2026-03-12 10:23:45.123 D/OpenMonitor: message text"
     */
    private fun parseXLogLine(line: String): AppLogEntry? {
        // xLog default format: "yyyy-MM-dd HH:mm:ss.SSS LEVEL/TAG: message"
        val regex = Regex("""^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWEF])/(.+?):\s*(.*)$""")
        val match = regex.matchEntire(line) ?: return null
        val (time, level, tag, message) = match.destructured
        return AppLogEntry(
            time = time.substringAfter(' '),
            level = level[0],
            tag = tag.trim(),
            message = message,
        )
    }

    /**
     * Parses logcat threadtime line:
     * "03-12 10:23:45.123  PID  TID LEVEL TAG: message"
     */
    private fun parseLogcatLine(line: String): AppLogEntry? {
        if (line.startsWith("-")) return null
        val regex = Regex("""^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+\d+\s+\d+\s+([VDIWEF])\s+(.+?)\s*:\s*(.*)$""")
        val match = regex.matchEntire(line) ?: return null
        val (time, level, tag, message) = match.destructured
        return AppLogEntry(
            time = time.substringAfter(' '),
            level = level[0],
            tag = tag.trim(),
            message = message,
        )
    }

    private suspend fun fetchDaemonLogs() = withContext(Dispatchers.IO) {
        try {
            val logFile = File(daemonLogPath)
            if (logFile.exists() && logFile.canRead()) {
                val lines = logFile.readLines().filter { it.isNotBlank() }.takeLast(DAEMON_TAIL_LINES)
                if (lines.isNotEmpty()) {
                    _daemonLogs.value = lines
                    _daemonLogStatus.value = null
                    return@withContext
                }
            }
            // Fallback: try via shell (daemon dir may be root-owned)
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
            val date = _selectedDate.value
            if (date != null) {
                // Delete historical file
                val dir = AppLogger.logDir ?: return@launch
                File(dir, "$date.log").delete()
                refreshLogDates()
            } else {
                // Clear logcat
                try { Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor() } catch (_: Exception) { }
            }
            _appLogs.value = emptyList()
        }
    }

    fun refreshDaemonLogs() {
        viewModelScope.launch { fetchDaemonLogs() }
    }

    fun clearDaemonLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            try { File(daemonLogPath).also { if (it.exists()) it.writeText("") } } catch (_: Exception) { }
            _daemonLogs.value = emptyList()
            _daemonLogStatus.value = null
        }
    }
}
