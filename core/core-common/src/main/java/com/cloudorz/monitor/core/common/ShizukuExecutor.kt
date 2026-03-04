package com.cloudorz.monitor.core.common

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import javax.inject.Inject

/**
 * [ShellExecutor] implementation that uses the Shizuku service to execute commands
 * with ADB-level (shell UID) privileges without requiring a connected PC.
 *
 * Shizuku acts as a delegated privilege broker: a user grants permission once, and
 * subsequent commands run with elevated permissions via the Shizuku daemon process.
 *
 * All I/O operations are dispatched to [Dispatchers.IO].
 */
class ShizukuExecutor @Inject constructor() : ShellExecutor {

    override val mode: PrivilegeMode = PrivilegeMode.SHIZUKU

    companion object {
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
    }

    override suspend fun execute(command: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(
                arrayOf("sh", "-c", command),
            )
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            CommandResult(
                exitCode = exitCode,
                stdout = stdout,
                stderr = stderr,
            )
        } catch (e: Exception) {
            CommandResult.failure("Shizuku execution failed: ${e.message}")
        }
    }

    override suspend fun executeAsRoot(command: String): CommandResult {
        // Shizuku operates at ADB/shell level, not root. Attempt via su as a best effort.
        return execute("su -c '$command'")
    }

    override suspend fun readFile(path: String): String? {
        val result = execute("cat '$path'")
        return if (result.isSuccess) result.stdout else null
    }

    override suspend fun writeFile(path: String, value: String): Boolean {
        val sanitizedValue = value.replace("'", "'\\''")
        val result = execute("echo '$sanitizedValue' > '$path'")
        return result.isSuccess
    }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val binderAlive = Shizuku.pingBinder()
            if (!binderAlive) return@withContext false

            val permissionGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            permissionGranted
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Requests Shizuku permission if the binder is alive but permission has not yet been granted.
     * The result is delivered asynchronously through Shizuku's permission listener.
     */
    fun requestPermissionIfNeeded() {
        try {
            if (Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
            ) {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            }
        } catch (_: Exception) {
            // Shizuku not available; nothing to request.
        }
    }
}
