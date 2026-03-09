package com.cloudorz.openmonitor.core.common

import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap
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
 * Failed paths are cached to avoid repeated SELinux audit log spam.
 *
 * All operations are dispatched to [Dispatchers.IO].
 */
class SysfsReader @Inject constructor(
    private val executor: ShellExecutor,
) {
    companion object {
        private const val TAG = "SysfsReader"
    }

    // Cache paths that failed to read — avoids repeated SELinux denials.
    // Entries expire after 60s so mode changes eventually take effect.
    private val deniedPaths = ConcurrentHashMap<String, Long>()
    private val denyTtlMs = 60_000L

    /**
     * Tries to read the file directly via [File.readText]; returns null on any failure.
     */
    private fun readFileDirect(path: String): String? {
        return try {
            val file = File(path)
            if (file.exists() && file.canRead()) file.readText() else null
        } catch (e: Exception) {
            Log.d(TAG, "readFileDirect failed: $path", e)
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
        val deniedAt = deniedPaths[path]
        if (deniedAt != null && System.currentTimeMillis() - deniedAt < denyTtlMs) return null

        val isSysfs = path.startsWith("/sys/")
        val hasShell = executor.mode != PrivilegeMode.BASIC

        val result = when {
            isSysfs && hasShell -> executor.readFile(path)
            else -> readFileDirect(path) ?: executor.readFile(path)
        }

        if (result == null) {
            deniedPaths[path] = System.currentTimeMillis()
        } else {
            deniedPaths.remove(path)
        }
        return result
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
            Log.d(TAG, "readInt failed: $path", e)
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
            Log.d(TAG, "readLong failed: $path", e)
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
            Log.d(TAG, "readString failed: $path", e)
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
            Log.d(TAG, "readLines failed: $path", e)
            emptyList()
        }
    }

}
