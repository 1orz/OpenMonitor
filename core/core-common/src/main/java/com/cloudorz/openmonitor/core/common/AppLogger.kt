package com.cloudorz.openmonitor.core.common

import android.content.Context
import com.elvishew.xlog.LogConfiguration
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import com.elvishew.xlog.printer.AndroidPrinter
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
 * - Logcat output via AndroidPrinter
 * - Rolling file logs: one file per day, 5 MB backup, 7-day retention
 * - [logDir] exposes the directory so LogViewModel can read history files
 */
object AppLogger {

    /** Directory where xLog writes daily log files. Null until [init]. */
    var logDir: File? = null
        private set

    private const val MAX_FILE_SIZE = 5L * 1024 * 1024 // 5 MB
    private const val RETENTION_MS = 7L * 24 * 3600 * 1000 // 7 days

    fun init(context: Context) {
        val dir = File(context.filesDir, "logs").also {
            it.mkdirs()
            logDir = it
        }

        val config = LogConfiguration.Builder()
            .logLevel(LogLevel.ALL)
            .tag("OpenMonitor")
            .build()

        val filePrinter = FilePrinter.Builder(dir.absolutePath)
            .fileNameGenerator(DateFileNameGenerator())
            .backupStrategy(FileSizeBackupStrategy2(MAX_FILE_SIZE, 3))
            .cleanStrategy(FileLastModifiedCleanStrategy(RETENTION_MS))
            .build()

        XLog.init(config, AndroidPrinter(), filePrinter)
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
}
