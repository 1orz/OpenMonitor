package com.cloudorz.openmonitor.core.common

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.elvishew.xlog.XLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
        private const val TAG = "ShizukuExecutor"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
        private const val IPC_TIMEOUT_MS = 10_000L
    }

    // Binder alive state — updated by Shizuku's global listeners.
    private val _binderAlive = MutableStateFlow(
        try { Shizuku.pingBinder() } catch (_: Exception) { false }
    )
    val binderAlive: StateFlow<Boolean> = _binderAlive.asStateFlow()

    // Result of the most recent requestPermission() call; null = no result yet.
    private val _permissionResult = MutableStateFlow<Boolean?>(null)
    val permissionResult: StateFlow<Boolean?> = _permissionResult.asStateFlow()

    init {
        XLog.tag(TAG).e("init: registering Shizuku listeners, initialBinder=${_binderAlive.value}")
        val handler = Handler(Looper.getMainLooper())
        try {
            Shizuku.addBinderDeadListener(
                {
                    _binderAlive.value = false
                    XLog.tag(TAG).e("binder DEAD")
                },
                handler,
            )
            Shizuku.addBinderReceivedListener(
                {
                    _binderAlive.value = true
                    XLog.tag(TAG).e("binder RECEIVED → calling bindService()")
                    bindService()
                },
                handler,
            )
            Shizuku.addRequestPermissionResultListener(
                { _, grantResult ->
                    val granted = grantResult == PackageManager.PERMISSION_GRANTED
                    _permissionResult.value = granted
                    XLog.tag(TAG).e("permission result: granted=$granted")
                    if (granted) bindService()
                },
                handler,
            )
            XLog.tag(TAG).e("init: listeners registered OK")
        } catch (e: Exception) {
            XLog.tag(TAG).e("init: addListeners FAILED", e)
        }
    }

    @Volatile
    private var shellService: IShellService? = null

    @Volatile
    private var bound = false

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(
                "com.cloudorz.openmonitor",
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
            XLog.tag(TAG).e("onServiceConnected: name=$name, service=$service, pingBinder=${service?.pingBinder()}")
            if (service != null && service.pingBinder()) {
                shellService = IShellService.Stub.asInterface(service)
                bound = true
                XLog.tag(TAG).e("onServiceConnected: UserService bound OK ✓")
            } else {
                XLog.tag(TAG).e("onServiceConnected: service null or binder dead ✗")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shellService = null
            bound = false
            XLog.tag(TAG).e("onServiceDisconnected: name=$name")
        }
    }

    /**
     * Binds the Shizuku UserService. Call after Shizuku permission is granted.
     * Safe to call multiple times — skips if already bound.
     */
    fun bindService() {
        XLog.tag(TAG).e("bindService: bound=$bound, shellService=${shellService != null}")
        if (bound && shellService != null) {
            XLog.tag(TAG).e("bindService: already bound, skipping")
            return
        }
        try {
            val binderAlive = Shizuku.pingBinder()
            XLog.tag(TAG).e("bindService: pingBinder=$binderAlive")
            if (!binderAlive) {
                XLog.tag(TAG).e("bindService: binder NOT alive, skipping")
                return
            }
            val permResult = Shizuku.checkSelfPermission()
            val granted = permResult == PackageManager.PERMISSION_GRANTED
            XLog.tag(TAG).e("bindService: checkSelfPermission=$permResult, granted=$granted")
            if (!granted) {
                XLog.tag(TAG).e("bindService: permission NOT granted, skipping")
                return
            }
            XLog.tag(TAG).e("bindService: calling Shizuku.bindUserService() now")
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
            XLog.tag(TAG).e("bindService: bindUserService() returned (callback pending)")
        } catch (e: Exception) {
            XLog.tag(TAG).e("bindService: EXCEPTION", e)
        }
    }

    /**
     * Unbinds the Shizuku UserService and kills the service process.
     */
    fun unbindService() {
        try {
            if (bound) {
                Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
            }
        } catch (e: Exception) {
            XLog.tag(TAG).d("unbindService failed", e)
        }
        shellService = null
        bound = false
    }

    val isServiceBound: Boolean get() = bound && shellService != null

    override suspend fun execute(command: String): CommandResult = withContext(Dispatchers.IO) {
        var service = shellService
        if (service == null) {
            XLog.tag(TAG).i("shellService null, calling bindService and waiting...")
            bindService()
            // Wait up to 5s for service binding
            for (i in 1..50) {
                delay(100)
                service = shellService
                if (service != null) break
            }
            if (service == null) {
                XLog.tag(TAG).e("service still null after 5s wait")
                return@withContext CommandResult.failure("Shizuku service not bound yet")
            }
            XLog.tag(TAG).i("shellService connected after waiting")
        }

        try {
            // Wrap Binder IPC in timeout to prevent indefinite blocking
            val raw = withTimeoutOrNull(IPC_TIMEOUT_MS) {
                service.executeCommand(command)
            }
            if (raw == null) {
                shellService = null
                bound = false
                return@withContext CommandResult.failure("Shizuku IPC timeout")
            }
            if (raw.startsWith("ERROR:")) {
                CommandResult.failure(raw.removePrefix("ERROR:"))
            } else {
                parseCommandResult(raw)
            }
        } catch (e: Exception) {
            shellService = null
            bound = false
            CommandResult.failure("Shizuku call failed: ${e.message}")
        }
    }

    override suspend fun executeAsRoot(command: String): CommandResult {
        // Shizuku UserService runs as shell uid — no root available, execute with normal privilege.
        return execute(command)
    }

    override suspend fun readFile(path: String): String? = withContext(Dispatchers.IO) {
        try {
            shellService?.readFileContent(path)
        } catch (e: Exception) {
            XLog.tag(TAG).d("readFile failed: $path", e)
            null
        }
    }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val binderAlive = Shizuku.pingBinder()
            if (!binderAlive) return@withContext false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            XLog.tag(TAG).d("isAvailable check failed", e)
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

    /** Synchronous permission check (no coroutine needed). */
    fun isAvailableSync(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    /** Clear the last permission result so the next grant/deny is observable. */
    fun resetPermissionResult() { _permissionResult.value = null }

    fun requestPermissionIfNeeded() {
        try {
            if (Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
            ) {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            }
        } catch (e: Exception) {
            XLog.tag(TAG).d("requestPermissionIfNeeded failed", e)
        }
    }
}
