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
enum class LogLevelFilter(val char: Char, val label: String, val displayName: String, val priority: Int) {
    VERBOSE('V', "V", "Verbose", 0),
    DEBUG('D', "D", "Debug", 1),
    INFO('I', "I", "Info", 2),
    WARN('W', "W", "Warn", 3),
    ERROR('E', "E", "Error", 4),
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

    private val _filterLevel = MutableStateFlow(LogLevelFilter.INFO)
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

    private val _daemonLogDates = MutableStateFlow<List<String>>(emptyList())
    val daemonLogDates: StateFlow<List<String>> = _daemonLogDates.asStateFlow()

    private val _selectedDaemonDate = MutableStateFlow<String?>(null)
    val selectedDaemonDate: StateFlow<String?> = _selectedDaemonDate.asStateFlow()

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

    fun selectDaemonDate(date: String?) {
        _selectedDaemonDate.value = date
        viewModelScope.launch { fetchDaemonLogs() }
    }

    // ---- Level helpers ----

    private fun levelPriority(c: Char): Int = when (c) {
        'V' -> 0; 'D' -> 1; 'I' -> 2; 'W' -> 3; 'E', 'F' -> 4; else -> 0
    }

    /** Extract level priority from daemon log line.
     *  Daemon format: "15:44:27.814692 [main] message" — no explicit level marker.
     *  Treat unrecognized lines as INFO so they pass the default filter. */
    private fun parseDaemonLineLevel(line: String): Int = when {
        line.contains(" ERROR ") || line.contains(" error:") -> 4
        line.contains(" WARN") || line.contains(" warn:") -> 3
        line.contains(" DEBUG") || line.contains(" debug:") -> 1
        line.contains(" TRACE") || line.contains(" trace:") -> 0
        else -> 2 // default = INFO level so lines are visible at default filter
    }

    // ---- Data loading ----

    private suspend fun refreshLogDates() = withContext(Dispatchers.IO) {
        _logDates.value = AppLogger.listLogFiles().map { it.name }
    }

    private suspend fun fetchAppLogs() = withContext(Dispatchers.IO) {
        val date = _selectedDate.value
        if (date != null) loadFromFile(date) else loadFromLogcat()
        refreshLogDates()
    }

    private fun loadFromFile(date: String) {
        val dir = AppLogger.logDir ?: return
        val file = File(dir, date)
        if (!file.exists()) { _rawAppLogs.value = emptyList(); return }
        _rawAppLogs.value = file.readLines()
            .mapNotNull { parseXLogLine(it) ?: parseXLogPipeLine(it) }
            .takeLast(APP_LOG_MAX_LINES)
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
        // Standard xlog format: "2026-04-04 12:34:56.789 E/TAG: message"
        val regex = Regex("""^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWEF])/(.+?):\s*(.*)$""")
        val match = regex.matchEntire(line) ?: return null
        val (time, level, tag, message) = match.destructured
        return AppLogEntry(time.substringAfter(' '), level[0], tag.trim(), message)
    }

    /** Parse xLog pipe-delimited format: "1775429508398|E|TAG|message" */
    private fun parseXLogPipeLine(line: String): AppLogEntry? {
        val parts = line.split("|", limit = 4)
        if (parts.size < 4) return null
        val millis = parts[0].toLongOrNull() ?: return null
        val level = parts[1].firstOrNull() ?: return null
        if (level !in "VDIWEF") return null
        val tag = parts[2]
        val message = parts[3]
        // Convert millis to HH:MM:SS.mmm
        val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
            .format(java.util.Date(millis))
        return AppLogEntry(time, level, tag, message)
    }

    private fun parseLogcatLine(line: String): AppLogEntry? {
        if (line.startsWith("-")) return null
        val regex = Regex("""^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+\d+\s+\d+\s+([VDIWEF])\s+(.+?)\s*:\s*(.*)$""")
        val match = regex.matchEntire(line) ?: return null
        val (time, level, tag, message) = match.destructured
        return AppLogEntry(time.substringAfter(' '), level[0], tag.trim(), message)
    }

    private fun refreshDaemonLogDates() {
        val files = daemonLauncher.listDaemonLogFiles()
        // Only list archived files (daemon-YYYY-MM-DD.log), not current daemon.log
        _daemonLogDates.value = files
            .filter { it.name != "daemon.log" }
            .map { it.name.removePrefix("daemon-").removeSuffix(".log") }
    }

    private suspend fun fetchDaemonLogs() = withContext(Dispatchers.IO) {
        refreshDaemonLogDates()
        try {
            val selectedDate = _selectedDaemonDate.value
            val logFile = if (selectedDate != null) {
                File(daemonLauncher.dataDir, "daemon-$selectedDate.log")
            } else {
                File(daemonLogPath)
            }
            if (!logFile.exists()) {
                if (_rawDaemonLogs.value.isEmpty()) {
                    _daemonLogStatus.value = if (selectedDate != null) "该日期暂无 daemon 日志"
                    else "暂无 daemon 日志（daemon 未运行）"
                }
                return@withContext
            }

            // For large files, read only the tail portion to avoid OOM
            val lines = tailFile(logFile, DAEMON_TAIL_LINES)
            if (lines.isNotEmpty()) {
                _rawDaemonLogs.value = lines
                _daemonLogStatus.value = null
            } else if (_rawDaemonLogs.value.isEmpty()) {
                _daemonLogStatus.value = "日志为空"
            }
        } catch (_: Exception) {
            if (_rawDaemonLogs.value.isEmpty()) {
                _daemonLogStatus.value = "暂无 daemon 日志（daemon 未运行）"
            }
        }
    }

    /** Read last N lines from a file efficiently using RandomAccessFile. */
    private fun tailFile(file: File, maxLines: Int): List<String> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        try {
            val raf = java.io.RandomAccessFile(file, "r")
            val fileLength = raf.length()
            // Read up to 512KB from end (enough for ~300 lines)
            val readSize = minOf(fileLength, 512L * 1024)
            val startPos = fileLength - readSize
            raf.seek(startPos)
            val bytes = ByteArray(readSize.toInt())
            raf.readFully(bytes)
            raf.close()
            // Skip first partial line if we didn't read from beginning
            val text = String(bytes, Charsets.UTF_8)
            val allLines = text.lines().filter { it.isNotBlank() }
            // If we started mid-file, drop the first (possibly truncated) line
            val lines = if (startPos > 0 && allLines.isNotEmpty()) allLines.drop(1) else allLines
            return lines.takeLast(maxLines)
        } catch (e: Exception) {
            // RandomAccessFile failed — maybe permission issue, try FileInputStream
            return try {
                file.inputStream().bufferedReader().useLines { seq ->
                    val buf = ArrayDeque<String>(maxLines + 1)
                    seq.filter { it.isNotBlank() }.forEach { line ->
                        buf.addLast(line)
                        if (buf.size > maxLines) buf.removeFirst()
                    }
                    buf.toList()
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    // ---- Actions ----

    fun clearAppLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            val date = _selectedDate.value
            if (date != null) {
                val dir = AppLogger.logDir ?: return@launch
                File(dir, date).delete()
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
