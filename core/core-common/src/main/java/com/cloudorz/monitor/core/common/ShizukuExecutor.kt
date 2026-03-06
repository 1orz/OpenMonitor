package com.cloudorz.monitor.core.common

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ShellExecutor] that uses Shizuku's UserService to execute commands with
 * shell (UID 2000) or root (UID 0) privileges via Binder IPC.
 *
 * The UserService ([ShellUserService]) runs in a separate process launched by
 * Shizuku with elevated privileges. Commands are sent over Binder and executed
 * in that privileged process.
 */
@Singleton
class ShizukuExecutor @Inject constructor() : ShellExecutor {

    override val mode: PrivilegeMode = PrivilegeMode.SHIZUKU

    companion object {
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
    }

    @Volatile
    private var shellService: IShellService? = null

    @Volatile
    private var bound = false

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(
                "com.cloudorz.monitor",
                ShellUserService::class.java.name,
            ),
        )
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(false)
            .version(1)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service != null && service.pingBinder()) {
                shellService = IShellService.Stub.asInterface(service)
                bound = true
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shellService = null
            bound = false
        }
    }

    /**
     * Binds the Shizuku UserService. Call after Shizuku permission is granted.
     * Safe to call multiple times — skips if already bound.
     */
    fun bindService() {
        if (bound && shellService != null) return
        try {
            if (Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            ) {
                Shizuku.bindUserService(userServiceArgs, serviceConnection)
            }
        } catch (_: Exception) {}
    }

    /**
     * Unbinds the Shizuku UserService and kills the service process.
     */
    fun unbindService() {
        try {
            if (bound) {
                Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
            }
        } catch (_: Exception) {}
        shellService = null
        bound = false
    }

    val isServiceBound: Boolean get() = bound && shellService != null

    override suspend fun execute(command: String): CommandResult = withContext(Dispatchers.IO) {
        val service = shellService
        if (service == null) {
            bindService()
            return@withContext CommandResult.failure("Shizuku service not bound yet")
        }

        try {
            val raw = service.executeCommand(command)
            if (raw.startsWith("ERROR:")) {
                CommandResult.failure(raw.removePrefix("ERROR:"))
            } else {
                parseCommandResult(raw)
            }
        } catch (e: Exception) {
            // Service may have died
            shellService = null
            bound = false
            CommandResult.failure("Shizuku call failed: ${e.message}")
        }
    }

    override suspend fun executeAsRoot(command: String): CommandResult {
        return execute("su -c '$command'")
    }

    override suspend fun readFile(path: String): String? = withContext(Dispatchers.IO) {
        try {
            shellService?.readFileContent(path)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun writeFile(path: String, value: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                shellService?.writeFileContent(path, value) ?: false
            } catch (_: Exception) {
                false
            }
        }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val binderAlive = Shizuku.pingBinder()
            if (!binderAlive) return@withContext false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Parses the raw output from [ShellUserService.executeCommand].
     * Format: `exitCode\nSTDOUT_START\n<stdout>\nSTDERR_START\n<stderr>`
     */
    private fun parseCommandResult(raw: String): CommandResult {
        val firstNewline = raw.indexOf('\n')
        if (firstNewline == -1) {
            return CommandResult(exitCode = raw.toIntOrNull() ?: -1, stdout = "", stderr = "")
        }
        val exitCode = raw.substring(0, firstNewline).toIntOrNull() ?: -1
        val rest = raw.substring(firstNewline + 1)

        val stdoutStart = rest.indexOf("STDOUT_START\n")
        val stderrStart = rest.indexOf("\nSTDERR_START\n")

        val stdout = if (stdoutStart != -1 && stderrStart != -1) {
            rest.substring(stdoutStart + "STDOUT_START\n".length, stderrStart)
        } else if (stdoutStart != -1) {
            rest.substring(stdoutStart + "STDOUT_START\n".length)
        } else {
            rest
        }

        val stderr = if (stderrStart != -1) {
            rest.substring(stderrStart + "\nSTDERR_START\n".length)
        } else {
            ""
        }

        return CommandResult(exitCode = exitCode, stdout = stdout.trimEnd(), stderr = stderr.trimEnd())
    }

    fun requestPermissionIfNeeded() {
        try {
            if (Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
            ) {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            }
        } catch (_: Exception) {}
    }
}
