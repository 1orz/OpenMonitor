package com.cloudorz.openmonitor.ui.log

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.R
import com.cloudorz.openmonitor.core.common.AppLogEntry
import com.cloudorz.openmonitor.core.common.AppLogger
import com.elvishew.xlog.LogLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

enum class LogLevelFilter(val char: Char, val label: String, val displayName: String, val priority: Int, val xlogLevel: Int) {
    VERBOSE('V', "V", "Verbose", 0, LogLevel.VERBOSE),
    DEBUG('D', "D", "Debug", 1, LogLevel.DEBUG),
    INFO('I', "I", "Info", 2, LogLevel.INFO),
    WARN('W', "W", "Warn", 3, LogLevel.WARN),
    ERROR('E', "E", "Error", 4, LogLevel.ERROR);

    companion object {
        fun fromXLogLevel(level: Int): LogLevelFilter = when {
            level <= LogLevel.VERBOSE -> VERBOSE
            level <= LogLevel.DEBUG -> DEBUG
            level <= LogLevel.INFO -> INFO
            level <= LogLevel.WARN -> WARN
            else -> ERROR
        }
    }
}

@HiltViewModel
class LogViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    companion object {
        private const val APP_LOG_POLL_INTERVAL_MS = 2_000L
        private const val DATE_REFRESH_INTERVAL_MS = 60_000L
        private const val APP_LOG_TAIL_LINES = 800

        private val XLOG_LINE_REGEX = Regex("""^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWEF])/(.+?):\s*(.*)$""")
    }

    private val _rawAppLogs = MutableStateFlow<List<AppLogEntry>>(emptyList())
    private val _rawServerLogs = MutableStateFlow<List<String>>(emptyList())

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _filterLevel = MutableStateFlow(
        LogLevelFilter.fromXLogLevel(AppLogger.minFileLevel),
    )
    val filterLevel: StateFlow<LogLevelFilter> = _filterLevel.asStateFlow()

    val appLogs: StateFlow<List<AppLogEntry>> = combine(_rawAppLogs, _filterLevel) { logs, level ->
        if (level == LogLevelFilter.VERBOSE) logs
        else logs.filter { levelPriority(it.level) >= level.priority }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val daemonLogs: StateFlow<List<String>> = combine(_rawServerLogs, _filterLevel) { lines, level ->
        if (level == LogLevelFilter.VERBOSE) lines
        else lines.filter { parseDaemonLineLevel(it) >= level.priority }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _logDates = MutableStateFlow<List<String>>(emptyList())
    val logDates: StateFlow<List<String>> = _logDates.asStateFlow()

    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate.asStateFlow()

    private val _daemonLogStatus = MutableStateFlow<Int?>(null)
    val daemonLogStatus: StateFlow<Int?> = _daemonLogStatus.asStateFlow()

    private val _daemonLogDates = MutableStateFlow<List<String>>(emptyList())
    val daemonLogDates: StateFlow<List<String>> = _daemonLogDates.asStateFlow()

    private val _selectedDaemonDate = MutableStateFlow<String?>(null)
    val selectedDaemonDate: StateFlow<String?> = _selectedDaemonDate.asStateFlow()

    private val serverLogDir: File
        get() = context.filesDir.resolve("server")

    private var pollingJob: kotlinx.coroutines.Job? = null

    fun startObserving() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            launch {
                while (isActive) {
                    refreshLogDates()
                    delay(DATE_REFRESH_INTERVAL_MS)
                }
            }
            launch {
                while (isActive) {
                    if (!_paused.value) fetchAppLogs()
                    delay(APP_LOG_POLL_INTERVAL_MS)
                }
            }
            launch {
                while (isActive) {
                    if (!_paused.value) fetchServerLogs()
                    delay(3_000L)
                }
            }
        }
    }

    fun stopObserving() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun togglePause() {
        _paused.value = !_paused.value
        if (!_paused.value) {
            viewModelScope.launch { fetchAppLogs() }
            viewModelScope.launch { fetchServerLogs() }
        }
    }

    fun setFilterLevel(level: LogLevelFilter) {
        _filterLevel.value = level
        AppLogger.setMinLevel(context, level.xlogLevel)
    }

    fun selectDate(date: String?) {
        _selectedDate.value = date
        viewModelScope.launch { fetchAppLogs() }
    }

    fun selectDaemonDate(date: String?) {
        _selectedDaemonDate.value = date
        viewModelScope.launch { fetchServerLogs() }
    }

    private fun levelPriority(c: Char): Int = when (c) {
        'V' -> 0; 'D' -> 1; 'I' -> 2; 'W' -> 3; 'E', 'F' -> 4; else -> 0
    }

    private fun parseDaemonLineLevel(line: String): Int = when {
        line.contains(" ERROR ") || line.contains(" error:") -> 4
        line.contains(" WARN") || line.contains(" warn:") -> 3
        line.contains(" DEBUG") || line.contains(" debug:") -> 1
        line.contains(" TRACE") || line.contains(" trace:") -> 0
        else -> 2
    }

    private suspend fun refreshLogDates() = withContext(Dispatchers.IO) {
        _logDates.value = AppLogger.listLogFiles().map { it.name }
    }

    private suspend fun fetchAppLogs() = withContext(Dispatchers.IO) {
        val date = _selectedDate.value
        val targetDate = date ?: LocalDate.now().toString()
        loadAppLogFile(targetDate)
    }

    private fun loadAppLogFile(date: String) {
        val dir = AppLogger.logDir ?: return
        val file = File(dir, date)
        if (!file.exists()) {
            _rawAppLogs.value = emptyList()
            return
        }
        val lines = tailFile(file, APP_LOG_TAIL_LINES)
        _rawAppLogs.value = lines.mapNotNull { parseXLogLine(it) ?: parseXLogPipeLine(it) }
    }

    private fun parseXLogLine(line: String): AppLogEntry? {
        val match = XLOG_LINE_REGEX.matchEntire(line) ?: return null
        val (time, level, tag, message) = match.destructured
        return AppLogEntry(time.substringAfter(' '), level[0], tag.trim(), message)
    }

    private fun parseXLogPipeLine(line: String): AppLogEntry? {
        val parts = line.split("|", limit = 4)
        if (parts.size < 4) return null
        val millis = parts[0].toLongOrNull() ?: return null
        val level = parts[1].firstOrNull() ?: return null
        if (level !in "VDIWEF") return null
        val tag = parts[2]
        val message = parts[3]
        val time = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
            .format(java.util.Date(millis))
        return AppLogEntry(time, level, tag, message)
    }

    private suspend fun fetchServerLogs() = withContext(Dispatchers.IO) {
        refreshServerLogDates()
        try {
            val selectedDate = _selectedDaemonDate.value
            val logFile = if (selectedDate != null) {
                File(serverLogDir, "server-$selectedDate.log")
            } else {
                File(serverLogDir, "server.log")
            }
            if (!logFile.exists()) {
                if (_rawServerLogs.value.isEmpty()) {
                    _daemonLogStatus.value = if (selectedDate != null) R.string.log_daemon_no_date
                    else R.string.log_daemon_not_running
                }
                return@withContext
            }

            val lines = tailFile(logFile, 300)
            if (lines.isNotEmpty()) {
                _rawServerLogs.value = lines
                _daemonLogStatus.value = null
            } else if (_rawServerLogs.value.isEmpty()) {
                _daemonLogStatus.value = R.string.log_daemon_log_empty
            }
        } catch (_: Exception) {
            if (_rawServerLogs.value.isEmpty()) {
                _daemonLogStatus.value = R.string.log_daemon_not_running
            }
        }
    }

    private fun refreshServerLogDates() {
        val dir = serverLogDir
        if (!dir.exists()) {
            _daemonLogDates.value = emptyList()
            return
        }
        _daemonLogDates.value = dir.listFiles()
            ?.filter { it.name.startsWith("server-") && it.name.endsWith(".log") }
            ?.map { it.name.removePrefix("server-").removeSuffix(".log") }
            ?.sortedDescending()
            ?: emptyList()
    }

    private fun tailFile(file: File, maxLines: Int): List<String> {
        if (!file.exists() || file.length() == 0L) return emptyList()
        try {
            val raf = java.io.RandomAccessFile(file, "r")
            val fileLength = raf.length()
            val readSize = minOf(fileLength, 512L * 1024)
            val startPos = fileLength - readSize
            raf.seek(startPos)
            val bytes = ByteArray(readSize.toInt())
            raf.readFully(bytes)
            raf.close()
            val text = String(bytes, Charsets.UTF_8)
            val allLines = text.lines().filter { it.isNotBlank() }
            val lines = if (startPos > 0 && allLines.isNotEmpty()) allLines.drop(1) else allLines
            return lines.takeLast(maxLines)
        } catch (_: Exception) {
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

    fun clearAppLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            val date = _selectedDate.value
            val dir = AppLogger.logDir ?: return@launch
            val targetDate = date ?: LocalDate.now().toString()
            val file = File(dir, targetDate)
            if (file.exists()) {
                if (date != null) {
                    file.delete()
                    refreshLogDates()
                } else {
                    file.writeText("")
                }
            }
            _rawAppLogs.value = emptyList()
        }
    }

    fun refreshDaemonLogs() {
        viewModelScope.launch { fetchServerLogs() }
    }

    fun clearDaemonLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            val selectedDate = _selectedDaemonDate.value
            if (selectedDate != null) {
                val archiveFile = File(serverLogDir, "server-$selectedDate.log")
                archiveFile.delete()
                refreshServerLogDates()
            } else {
                try { File(serverLogDir, "server.log").also { if (it.exists()) it.writeText("") } } catch (_: Exception) { }
            }
            _rawServerLogs.value = emptyList()
            _daemonLogStatus.value = null
        }
    }
}
