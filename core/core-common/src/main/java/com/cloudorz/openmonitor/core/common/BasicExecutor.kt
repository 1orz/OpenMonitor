package com.cloudorz.openmonitor.core.common

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * [ShellExecutor] implementation that uses standard Java File I/O and [Runtime.exec]
 * without any privilege escalation. This executor can only access world-readable files
 * and execute commands available to the app's own UID.
 *
 * This is the fallback executor when no elevated privilege mechanism is available.
 * All I/O operations are dispatched to [Dispatchers.IO].
 */
class BasicExecutor @Inject constructor() : ShellExecutor {

    override val mode: PrivilegeMode = PrivilegeMode.BASIC

    companion object {
        private const val TAG = "BasicExecutor"
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
            CommandResult.failure("Basic shell execution failed: ${e.message}")
        }
    }

    override suspend fun executeAsRoot(command: String): CommandResult {
        // BasicExecutor has no root capability; execute without escalation.
        return execute(command)
    }

    override suspend fun readFile(path: String): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                file.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "readFile failed: $path", e)
            null
        }
    }

    override suspend fun isAvailable(): Boolean = true
}
