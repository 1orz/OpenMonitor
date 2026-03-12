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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject

/** Log level priority for display filtering. */
enum class LogLevelFilter(val char: Char, val label: String, val priority: Int) {
    VERBOSE('V', "V", 0),
    DEBUG('D', "D", 1),
    INFO('I', "I", 2),
    WARN('W', "W", 3),
    ERROR('E', "E", 4),
}

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

    // ---- Raw (unfiltered) data ----

    private val _rawAppLogs = MutableStateFlow<List<AppLogEntry>>(emptyList())
    private val _rawDaemonLogs = MutableStateFlow<List<String>>(emptyList())

    // ---- Display filter ----

    private val _filterLevel = MutableStateFlow(LogLevelFilter.VERBOSE)
    val filterLevel: StateFlow<LogLevelFilter> = _filterLevel.asStateFlow()

    /** Filtered app logs — only entries >= filterLevel are shown. */
    val appLogs: StateFlow<List<AppLogEntry>> = combine(_rawAppLogs, _filterLevel) { logs, level ->
        if (level == LogLevelFilter.VERBOSE) logs
        else logs.filter { levelPriority(it.level) >= level.priority }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Filtered daemon logs — only lines containing level >= filterLevel are shown. */
    val daemonLogs: StateFlow<List<String>> = combine(_rawDaemonLogs, _filterLevel) { lines, level ->
        if (level == LogLevelFilter.VERBOSE) lines
        else lines.filter { parseDaemonLineLevel(it) >= level.priority }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Other state ----

    private val _logDates = MutableStateFlow<List<String>>(emptyList())
    val logDates: StateFlow<List<String>> = _logDates.asStateFlow()

    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate.asStateFlow()

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

    fun setFilterLevel(level: LogLevelFilter) {
        _filterLevel.value = level
    }

    fun selectDate(date: String?) {
        _selectedDate.value = date
        viewModelScope.launch { fetchAppLogs() }
    }

    // ---- Level helpers ----

    private fun levelPriority(c: Char): Int = when (c) {
        'V' -> 0; 'D' -> 1; 'I' -> 2; 'W' -> 3; 'E', 'F' -> 4; else -> 0
    }

    /** Extract level priority from daemon log line (e.g. " ERROR ", " WARN  ", " INFO  ", " DEBUG "). */
    private fun parseDaemonLineLevel(line: String): Int = when {
        line.contains(" ERROR ") -> 4
        line.contains(" WARN") -> 3
        line.contains(" INFO") -> 2
        line.contains(" DEBUG") -> 1
        else -> 0
    }

    // ---- Data loading ----

    private suspend fun refreshLogDates() = withContext(Dispatchers.IO) {
        _logDates.value = AppLogger.listLogFiles().map { it.nameWithoutExtension }
    }

    private suspend fun fetchAppLogs() = withContext(Dispatchers.IO) {
        val date = _selectedDate.value
        if (date != null) loadFromFile(date) else loadFromLogcat()
        refreshLogDates()
    }

    private fun loadFromFile(date: String) {
        val dir = AppLogger.logDir ?: return
        val file = File(dir, "$date.log")
        if (!file.exists()) { _rawAppLogs.value = emptyList(); return }
        _rawAppLogs.value = file.readLines().mapNotNull { parseXLogLine(it) }.takeLast(APP_LOG_MAX_LINES)
    }

    private fun loadFromLogcat() {
        try {
            val pid = Process.myPid()
            val process = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-v", "threadtime", "--pid=$pid", "-t", "$APP_LOG_MAX_LINES")
            )
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val entries = mutableListOf<AppLogEntry>()
            reader.useLines { lines -> lines.forEach { line -> parseLogcatLine(line)?.let { entries.add(it) } } }
            process.waitFor()
            if (entries.isNotEmpty()) _rawAppLogs.value = entries
        } catch (_: Exception) { }
    }

    private fun parseXLogLine(line: String): AppLogEntry? {
        val regex = Regex("""^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWEF])/(.+?):\s*(.*)$""")
        val match = regex.matchEntire(line) ?: return null
        val (time, level, tag, message) = match.destructured
        return AppLogEntry(time.substringAfter(' '), level[0], tag.trim(), message)
    }

    private fun parseLogcatLine(line: String): AppLogEntry? {
        if (line.startsWith("-")) return null
        val regex = Regex("""^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+\d+\s+\d+\s+([VDIWEF])\s+(.+?)\s*:\s*(.*)$""")
        val match = regex.matchEntire(line) ?: return null
        val (time, level, tag, message) = match.destructured
        return AppLogEntry(time.substringAfter(' '), level[0], tag.trim(), message)
    }

    private suspend fun fetchDaemonLogs() = withContext(Dispatchers.IO) {
        try {
            val logFile = File(daemonLogPath)
            if (logFile.exists() && logFile.canRead()) {
                val lines = logFile.readLines().filter { it.isNotBlank() }.takeLast(DAEMON_TAIL_LINES)
                if (lines.isNotEmpty()) {
                    _rawDaemonLogs.value = lines
                    _daemonLogStatus.value = null
                    return@withContext
                }
            }
            val process = Runtime.getRuntime().exec(
                arrayOf("sh", "-c", "tail -n $DAEMON_TAIL_LINES '$daemonLogPath' 2>/dev/null")
            )
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = reader.readLines().filter { it.isNotBlank() }
            process.waitFor()
            if (lines.isNotEmpty()) {
                _rawDaemonLogs.value = lines
                _daemonLogStatus.value = null
            } else if (_rawDaemonLogs.value.isEmpty()) {
                _daemonLogStatus.value = "暂无 daemon 日志（daemon 未运行）\n路径: $daemonLogPath"
            }
        } catch (_: Exception) {
            if (_rawDaemonLogs.value.isEmpty()) {
                _daemonLogStatus.value = "暂无 daemon 日志（daemon 未运行）"
            }
        }
    }

    // ---- Actions ----

    fun clearAppLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            val date = _selectedDate.value
            if (date != null) {
                val dir = AppLogger.logDir ?: return@launch
                File(dir, "$date.log").delete()
                refreshLogDates()
            } else {
                try { Runtime.getRuntime().exec(arrayOf("logcat", "-c")).waitFor() } catch (_: Exception) { }
            }
            _rawAppLogs.value = emptyList()
        }
    }

    fun refreshDaemonLogs() {
        viewModelScope.launch { fetchDaemonLogs() }
    }

    fun clearDaemonLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            try { File(daemonLogPath).also { if (it.exists()) it.writeText("") } } catch (_: Exception) { }
            _rawDaemonLogs.value = emptyList()
            _daemonLogStatus.value = null
        }
    }
}
