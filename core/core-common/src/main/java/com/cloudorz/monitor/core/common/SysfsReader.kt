package com.cloudorz.monitor.core.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Convenience wrapper around a [ShellExecutor] for reading and writing sysfs/procfs
 * files with typed parsing.
 *
 * This class is the primary interface that data-collection modules use to interact
 * with kernel-exposed system files (e.g., CPU frequencies, thermal zones, battery info).
 * It delegates all I/O to the provided [ShellExecutor], which determines the privilege
 * level used for access.
 *
 * All operations are dispatched to [Dispatchers.IO].
 */
class SysfsReader @Inject constructor(
    private val executor: ShellExecutor,
) {

    /**
     * Reads a file and parses its trimmed contents as an [Int].
     *
     * @param path Absolute path to the sysfs/procfs file.
     * @return The parsed integer value, or null if the file cannot be read or parsed.
     */
    suspend fun readInt(path: String): Int? = withContext(Dispatchers.IO) {
        try {
            executor.readFile(path)?.trim()?.toIntOrNull()
        } catch (e: Exception) {
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
            executor.readFile(path)?.trim()?.toLongOrNull()
        } catch (e: Exception) {
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
            executor.readFile(path)?.trim()
        } catch (e: Exception) {
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
            executor.readFile(path)
                ?.trim()
                ?.split("\n")
                ?.map { it.trim() }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Writes a string value to a file.
     *
     * @param path Absolute path to the sysfs/procfs file.
     * @param value The string value to write.
     * @return True if the write succeeded, false otherwise.
     */
    suspend fun writeValue(path: String, value: String): Boolean = withContext(Dispatchers.IO) {
        try {
            executor.writeFile(path, value)
        } catch (e: Exception) {
            false
        }
    }
}
