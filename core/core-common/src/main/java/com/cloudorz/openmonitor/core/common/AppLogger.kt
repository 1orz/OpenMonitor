package com.cloudorz.openmonitor.core.common

import android.content.Context
import androidx.core.content.edit
import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import com.elvishew.xlog.printer.AndroidPrinter
import com.elvishew.xlog.printer.Printer
import com.elvishew.xlog.printer.file.FilePrinter
import com.elvishew.xlog.printer.file.backup.FileSizeBackupStrategy2
import com.elvishew.xlog.printer.file.clean.FileLastModifiedCleanStrategy
import com.elvishew.xlog.printer.file.naming.DateFileNameGenerator
import java.io.File

data class AppLogEntry(
    val time: String,
    val level: Char,
    val tag: String,
    val message: String,
)

/**
 * xLog-based logger. Call [init] once from Application.onCreate().
 *
 * - Logcat output via AndroidPrinter (always ALL — developer-facing)
 * - Rolling file logs: one file per day, 5 MB backup, 7-day retention
 * - File writes are hard-filtered by [minFileLevel]: only messages >= that level are stored
 * - [logDir] exposes the directory so LogViewModel can read history files
 */
object AppLogger {

    /** Directory where xLog writes daily log files. Null until [init]. */
    var logDir: File? = null
        private set

    private const val MAX_FILE_SIZE = 5L * 1024 * 1024 // 5 MB
    private const val RETENTION_MS = 7L * 24 * 3600 * 1000 // 7 days
    private const val PREFS_NAME = "monitor_settings"
    private const val KEY_MIN_FILE_LEVEL = "log_min_level"

    /** Current minimum level for file writes. Volatile for thread safety. */
    @Volatile
    var minFileLevel: Int = LogLevel.VERBOSE
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        minFileLevel = prefs.getInt(KEY_MIN_FILE_LEVEL, LogLevel.VERBOSE)

        val dir = File(context.filesDir, "logs").also {
            it.mkdirs()
            logDir = it
        }

        val config = LogConfiguration.Builder()
            .logLevel(LogLevel.ALL)
            .tag("OpenMonitor")
            .build()

        val realFilePrinter = FilePrinter.Builder(dir.absolutePath)
            .fileNameGenerator(DateFileNameGenerator())
            .backupStrategy(FileSizeBackupStrategy2(MAX_FILE_SIZE, 3))
            .cleanStrategy(FileLastModifiedCleanStrategy(RETENTION_MS))
            .build()

        val filteredFilePrinter = LevelFilteredPrinter(realFilePrinter) { minFileLevel }

        XLog.init(config, AndroidPrinter(), filteredFilePrinter)
    }

    /** Update the minimum file log level. Takes effect immediately for new messages. */
    fun setMinLevel(context: Context, level: Int) {
        minFileLevel = level
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putInt(KEY_MIN_FILE_LEVEL, level) }
    }

    /** List all log files sorted by date descending (newest first).
     *  XLog DateFileNameGenerator creates files like "2026-04-04" (no extension).
     *  Backup files are named "2026-04-04_1", "2026-04-04_2", etc. */
    fun listLogFiles(): List<File> {
        val dir = logDir ?: return emptyList()
        val datePattern = Regex("""\d{4}-\d{2}-\d{2}(_\d+)?""")
        return dir.listFiles()
            ?.filter { it.isFile && datePattern.matches(it.name) }
            ?.sortedByDescending { it.name }
            ?: emptyList()
    }

    /**
     * Wraps a [Printer] to drop messages below [minLevelProvider].
     * AndroidPrinter is NOT wrapped — it always logs everything to logcat.
     */
    private class LevelFilteredPrinter(
        private val delegate: Printer,
        private val minLevelProvider: () -> Int,
    ) : Printer {
        override fun println(logLevel: Int, tag: String?, msg: String?) {
            if (logLevel >= minLevelProvider()) {
                delegate.println(logLevel, tag, msg)
            }
        }
    }
}
