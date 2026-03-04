package com.cloudorz.monitor.core.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * [ShellExecutor] implementation that uses [Runtime.getRuntime] to execute shell commands.
 * This executor provides standard shell-level access without root privileges, equivalent
 * to the permissions available through an ADB shell session.
 *
 * All I/O operations are dispatched to [Dispatchers.IO].
 */
class AdbExecutor @Inject constructor() : ShellExecutor {

    override val mode: PrivilegeMode = PrivilegeMode.ADB

    companion object {
        private const val PROCESS_TIMEOUT_SECONDS = 30L
    }

    override suspend fun execute(command: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val completed = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                CommandResult.failure("Command timed out after $PROCESS_TIMEOUT_SECONDS seconds")
            } else {
                CommandResult(
                    exitCode = process.exitValue(),
                    stdout = stdout,
                    stderr = stderr,
                )
            }
        } catch (e: Exception) {
            CommandResult.failure("ADB shell execution failed: ${e.message}")
        }
    }

    override suspend fun executeAsRoot(command: String): CommandResult {
        // ADB executor does not have root access; attempt to run via su.
        return execute("su -c '$command'")
    }

    override suspend fun readFile(path: String): String? = withContext(Dispatchers.IO) {
        // Try direct Java File I/O first for better performance on readable files.
        try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                return@withContext file.readText()
            }
        } catch (_: Exception) {
            // Fall through to shell-based read.
        }

        // Fall back to shell command for files that need elevated permissions.
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "cat '$path'"))
            val output = process.inputStream.bufferedReader().readText()
            val completed = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return@withContext null
            }
            if (process.exitValue() == 0) output else null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun writeFile(path: String, value: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val sanitizedValue = value.replace("'", "'\\''")
                val process = Runtime.getRuntime()
                    .exec(arrayOf("sh", "-c", "echo '$sanitizedValue' > '$path'"))
                val completed = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (!completed) {
                    process.destroyForcibly()
                    false
                } else {
                    process.exitValue() == 0
                }
            } catch (e: Exception) {
                false
            }
        }

    override suspend fun isAvailable(): Boolean = true
}
