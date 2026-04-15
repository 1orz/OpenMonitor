package com.cloudorz.openmonitor.core.common

import com.elvishew.xlog.XLog
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Convenience wrapper around a [ShellExecutor] for reading sysfs/procfs
 * files with typed parsing.
 *
 * Read operations first attempt direct [File] I/O (which works for world-readable
 * files such as `/proc/meminfo`, `/sys/devices/system/cpu/`, etc.) and only fall
 * back to the [ShellExecutor] when direct access fails. This avoids unnecessary
 * root shell usage for publicly readable system files.
 *
 * Failed `/sys/` paths are cached briefly to avoid repeated SELinux audit log
 * spam. `/proc/` paths are never cached — they're almost always world-readable
 * and transient read failures during mode transitions must not block hot-path
 * pollers for any length of time.
 *
 * All operations are dispatched to [Dispatchers.IO].
 */
class SysfsReader @Inject constructor(
    private val executor: ShellExecutor,
) {
    companion object {
        private const val TAG = "SysfsReader"
    }

    private val deniedPaths = ConcurrentHashMap<String, Long>()
    private val denyTtlMs = 15_000L
    private val lastMode = AtomicReference<PrivilegeMode?>(null)

    /**
     * Tries to read the file directly via [File.readText]; returns null on any failure.
     */
    private fun readFileDirect(path: String): String? {
        return try {
            val file = File(path)
            if (file.exists() && file.canRead()) file.readText() else null
        } catch (e: Exception) {
            XLog.tag(TAG).d("readFileDirect failed: $path", e)
            null
        }
    }

    /**
     * Reads the raw content of a file.
     *
     * Strategy by mode and path:
     * - Shell mode (Root/ADB/Shizuku) + /sys/: go directly through shell executor
     *   (avoids SELinux audit from app-process direct reads)
     * - BASIC mode + /sys/: try direct once, cache failure for 60s
     * - /proc/ paths: try direct first (usually world-readable), fall back to shell
     */
    private suspend fun readFileContent(path: String): String? {
        val currentMode = executor.mode
        val previous = lastMode.getAndSet(currentMode)
        if (previous != null && previous != currentMode) {
            // Mode changed: flush any stale denials cached under the old executor
            // (e.g. shell-routed reads that failed mid-switch shouldn't keep
            // blocking direct-I/O reads in the new mode).
            deniedPaths.clear()
        }

        val isSysfs = path.startsWith("/sys/")
        val isProc = path.startsWith("/proc/")

        if (isSysfs) {
            val deniedAt = deniedPaths[path]
            if (deniedAt != null && System.currentTimeMillis() - deniedAt < denyTtlMs) return null
        }

        val hasShell = currentMode != PrivilegeMode.BASIC
        val result = when {
            isSysfs && hasShell -> executor.readFile(path)
            else -> readFileDirect(path) ?: executor.readFile(path)
        }

        if (isSysfs) {
            if (result == null) deniedPaths[path] = System.currentTimeMillis()
            else deniedPaths.remove(path)
        }
        // /proc/ paths intentionally bypass the denial cache — transient
        // failures (mode switch, shell hiccup) must not freeze polling.
        if (!isSysfs && !isProc) {
            // Other paths (unusual — e.g. /data/... in tests): use old behavior.
            if (result == null) deniedPaths[path] = System.currentTimeMillis()
            else deniedPaths.remove(path)
        }
        return result
    }

    /** Flush the denial cache. Called when external signals (mode change,
     *  server restart) make previously-denied paths potentially readable. */
    fun clearDeniedPaths() {
        deniedPaths.clear()
    }

    /**
     * Reads a file and parses its trimmed contents as an [Int].
     *
     * @param path Absolute path to the sysfs/procfs file.
     * @return The parsed integer value, or null if the file cannot be read or parsed.
     */
    suspend fun readInt(path: String): Int? = withContext(Dispatchers.IO) {
        try {
            readFileContent(path)?.trim()?.toIntOrNull()
        } catch (e: Exception) {
            XLog.tag(TAG).d("readInt failed: $path", e)
            null
        }
    }

    /**
     * Reads a file and parses its trimmed contents as a [Long].
     *
     * @param path Absolute path to the sysfs/procfs file.
     * @return The parsed long value, or null if the file cannot be read or parsed.
     */
    suspend fun readLong(path: String): Long? = withContext(Dispatchers.IO) {
        try {
            readFileContent(path)?.trim()?.toLongOrNull()
        } catch (e: Exception) {
            XLog.tag(TAG).d("readLong failed: $path", e)
            null
        }
    }

    /**
     * Reads a file and returns its trimmed contents as a [String].
     *
     * @param path Absolute path to the sysfs/procfs file.
     * @return The trimmed file contents, or null if the file cannot be read.
     */
    suspend fun readString(path: String): String? = withContext(Dispatchers.IO) {
        try {
            readFileContent(path)?.trim()
        } catch (e: Exception) {
            XLog.tag(TAG).d("readString failed: $path", e)
            null
        }
    }

    /**
     * Reads a file and splits its contents into individual lines.
     * Blank trailing lines are excluded.
     *
     * @param path Absolute path to the sysfs/procfs file.
     * @return A list of lines from the file, or an empty list if the file cannot be read.
     */
    suspend fun readLines(path: String): List<String> = withContext(Dispatchers.IO) {
        try {
            readFileContent(path)
                ?.trim()
                ?.split("\n")
                ?.map { it.trim() }
                ?: emptyList()
        } catch (e: Exception) {
            XLog.tag(TAG).d("readLines failed: $path", e)
            emptyList()
        }
    }

}
