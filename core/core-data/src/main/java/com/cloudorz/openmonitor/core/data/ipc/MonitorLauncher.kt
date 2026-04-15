package com.cloudorz.openmonitor.core.data.ipc

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.cloudorz.openmonitor.core.common.PrivilegeMode
import com.cloudorz.openmonitor.core.common.ShellExecutor
import com.elvishew.xlog.XLog
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brings up the privileged [com.cloudorz.openmonitor.server.IMonitorService]
 * using whichever launch path is available:
 *
 *  - **Shizuku**: `Shizuku.bindUserService(args, conn)` — Shizuku's server
 *    process exec's `app_process` targeting the shim's RustEntry class,
 *    which loads libopenmonitor_server.so and enters the Rust main. The
 *    returned IBinder is delivered via [ServiceConnection.onServiceConnected].
 *
 *  - **libsu / root shell**: we `Shell.cmd(...)` the packaged binary. The
 *    binary reverse-publishes its IBinder via [BinderProvider.call] — that
 *    ends up in [MonitorClient.onBinderReceived] on its own.
 *
 * Either way, [MonitorClient] is the single sink for the IBinder. This class
 * only cares about *starting* the server.
 */
@Singleton
class MonitorLauncher @Inject constructor(
    private val shellExecutor: ShellExecutor,
    private val monitorClient: MonitorClient,
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "MonitorLauncher"
        private const val SERVER_BIN = "libopenmonitor-server.so"
        // Must match com.cloudorz.openmonitor.server.RustEntry.
        private const val SHIZUKU_ENTRY_CLASS =
            "com.cloudorz.openmonitor.server.RustEntry"
        private const val SHIZUKU_PROCESS_SUFFIX = ":server"
        private const val USER_SERVICE_VERSION = 1
        // Must match binder_push::SERVICE_NAME in the Rust server.
        private const val SERVICE_MANAGER_NAME = "openmonitor.server"
        private const val DISCOVERY_ATTEMPTS = 10
        private const val DISCOVERY_DELAY_MS = 300L
    }

    private val binaryPath: String
        get() = "${context.applicationInfo.nativeLibraryDir}/$SERVER_BIN"

    private val shizukuConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service == null) {
                XLog.tag(TAG).e("Shizuku onServiceConnected: null binder")
                return
            }
            XLog.tag(TAG).i("Shizuku UserService connected: $name")
            monitorClient.onBinderReceived(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            XLog.tag(TAG).e("Shizuku UserService disconnected: $name")
        }
    }

    /** Ensure a privileged server is running. Idempotent. */
    suspend fun ensureRunning(): Boolean = withContext(Dispatchers.IO) {
        when (shellExecutor.mode) {
            PrivilegeMode.ROOT -> launchViaLibsu()
            PrivilegeMode.SHIZUKU -> launchViaShizuku()
            PrivilegeMode.ADB -> {
                // ADB mode: user manually invokes the binary. Try to discover
                // an already-running server via ServiceManager.
                XLog.tag(TAG).i("ADB mode — attempting server discovery")
                discoverBinder()
                true
            }
            PrivilegeMode.BASIC -> false
        }
    }

    fun shutdown() {
        runCatching { Shizuku.unbindUserService(shizukuArgs(), shizukuConnection, true) }
        runCatching {
            Shell.cmd(
                "pkill -9 -f openmonitor-server 2>/dev/null; " +
                "pkill -9 -f libopenmonitor-server 2>/dev/null"
            ).submit()
        }
    }

    private suspend fun launchViaLibsu(): Boolean {
        val pkg = context.packageName
        val dataDir = context.filesDir.absolutePath
        // The root shell loses our process env. Pass every knob explicitly.
        val cmd = "'$binaryPath' --mode root --data-dir '$dataDir' --app-package '$pkg'"
        val launched = try {
            val result = Shell.cmd(cmd).exec()
            if (result.isSuccess) {
                XLog.tag(TAG).i("server launched via libsu: $binaryPath")
                true
            } else {
                XLog.tag(TAG).e("libsu launch failed: ${result.err.joinToString("\n")}")
                false
            }
        } catch (e: Throwable) {
            XLog.tag(TAG).e("libsu launch threw: ${e.message}")
            false
        }

        if (!launched) return false

        // The binary daemonizes and registers in ServiceManager asynchronously.
        // Poll until the binder appears or we time out.
        discoverBinder()
        return true
    }

    /**
     * Poll ServiceManager for the server binder. The server registers itself
     * as [SERVICE_MANAGER_NAME] via `rsbinder::hub::add_service`.
     */
    private suspend fun discoverBinder() {
        repeat(DISCOVERY_ATTEMPTS) {
            val binder = getServiceBinder(SERVICE_MANAGER_NAME)
            if (binder != null) {
                XLog.tag(TAG).i("discovered server binder via ServiceManager")
                monitorClient.onBinderReceived(binder)
                return
            }
            delay(DISCOVERY_DELAY_MS)
        }
        XLog.tag(TAG).w(
            "ServiceManager discovery timed out — server may use BinderProvider fallback"
        )
    }

    /**
     * Reflective ServiceManager.getService(name). Hidden API, but accessible
     * to root/shell processes and apps with hidden-api bypass.
     */
    @Suppress("PrivateApi")
    private fun getServiceBinder(name: String): IBinder? {
        return try {
            val sm = Class.forName("android.os.ServiceManager")
            val method = sm.getMethod("getService", String::class.java)
            method.invoke(null, name) as? IBinder
        } catch (e: Throwable) {
            // Expected to fail on first few attempts before server is ready.
            null
        }
    }

    private fun launchViaShizuku(): Boolean {
        return try {
            Shizuku.bindUserService(shizukuArgs(), shizukuConnection)
            true
        } catch (e: Throwable) {
            XLog.tag(TAG).e("Shizuku bindUserService threw: ${e.message}")
            false
        }
    }

    private fun shizukuArgs(): Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, SHIZUKU_ENTRY_CLASS)
        )
            .daemon(true)
            .processNameSuffix(SHIZUKU_PROCESS_SUFFIX.removePrefix(":"))
            .debuggable(false)
            .version(USER_SERVICE_VERSION)
}
