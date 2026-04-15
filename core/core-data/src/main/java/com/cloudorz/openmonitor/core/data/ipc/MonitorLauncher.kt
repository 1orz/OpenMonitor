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
import java.io.File
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
        /**
         * ADB-mode shared memory path. The Rust server's
         * [SharedSnapshot::create_file] derives the path as
         * `<data_dir>/server/snapshot.shm`; main.rs defaults `data_dir` to
         * `/data/local/tmp/openmonitor` for `--mode adb`. /data/local/tmp is
         * the canonical shell-writable location; the file is chmod'd 0644 so
         * the untrusted_app can mmap it (SELinux allows untrusted_app →
         * shell_data_file:file read on standard AOSP builds).
         */
        private const val ADB_SHM_PATH = "/data/local/tmp/openmonitor/server/snapshot.shm"
        // Must match com.cloudorz.openmonitor.server.RustEntry.
        private const val SHIZUKU_ENTRY_CLASS =
            "com.cloudorz.openmonitor.server.RustEntry"
        private const val SHIZUKU_PROCESS_SUFFIX = ":server"
        private const val USER_SERVICE_VERSION = 1
        private const val DISCOVERY_ATTEMPTS = 10
        private const val DISCOVERY_DELAY_MS = 300L
    }

    val binaryPath: String
        get() = "${context.applicationInfo.nativeLibraryDir}/$SERVER_BIN"

    /** Canonical file path the --mode adb server writes its shm to. */
    val adbShmPath: String get() = ADB_SHM_PATH

    /**
     * Full adb shell command the user runs on their host to launch the server
     * in ADB mode. The `su` fallback is harmless on non-root devices and
     * automatic on rooted ones — this way a single command works for both.
     */
    fun adbLaunchCommand(): String {
        val pkg = context.packageName
        return "$binaryPath --mode adb --app-package $pkg"
    }

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
            PrivilegeMode.ADB -> discoverAdbShm()
            PrivilegeMode.BASIC -> false
        }
    }

    fun shutdown() {
        // Drop the client-side view of the connection first so the UI flips
        // to "not running" immediately — we don't want to wait for the kill
        // command (if any) to land, and the file-backed shm has no death
        // notification we could rely on.
        monitorClient.disconnect()

        runCatching { Shizuku.unbindUserService(shizukuArgs(), shizukuConnection, true) }
        // Synchronous exec (not submit) — we must ensure the old server is
        // gone before the caller triggers a new launch, otherwise the fresh
        // process sees the ashmem/file from the previous instance.
        runCatching {
            Shell.cmd(
                "pkill -9 -f libopenmonitor-server 2>/dev/null; " +
                "pkill -9 -f openmonitor-server 2>/dev/null; " +
                "su -c 'pkill -9 -f libopenmonitor-server' 2>/dev/null; " +
                "su -c 'pkill -9 -f openmonitor-server' 2>/dev/null"
            ).exec()
        }
        // Wipe the stale shared-memory file so a subsequent launch doesn't
        // connect to ghost data before the new server has written anything.
        runCatching { File(context.filesDir, "server/snapshot.shm").delete() }
    }

    private suspend fun launchViaLibsu(): Boolean {
        val pkg = context.packageName
        val dataDir = context.filesDir.absolutePath
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

        // The server creates a file-backed shared memory region that the app
        // mmaps directly — no binder discovery needed, avoids SELinux
        // service_manager restrictions on custom services.
        val shmFile = File(context.filesDir, "server/snapshot.shm")
        repeat(DISCOVERY_ATTEMPTS) {
            if (shmFile.exists() && shmFile.length() > 0) {
                monitorClient.connectViaFile(shmFile)
                XLog.tag(TAG).i("connected via file-backed shm")
                return true
            }
            delay(DISCOVERY_DELAY_MS)
        }
        XLog.tag(TAG).w("shared memory file not found after ${DISCOVERY_ATTEMPTS * DISCOVERY_DELAY_MS}ms")
        return false
    }

    /**
     * ADB mode — the user starts the server manually from a host shell. We
     * just poll the canonical shm path; when it appears (and is written to),
     * map it via [MonitorClient.connectViaFile]. Same file-backed-shm path
     * ROOT mode uses, just at a shell-writable location so the untrusted_app
     * can still mmap it without hitting SELinux `service_manager` rules.
     */
    private suspend fun discoverAdbShm(): Boolean {
        val shmFile = File(ADB_SHM_PATH)
        XLog.tag(TAG).i("ADB mode — polling $ADB_SHM_PATH")
        repeat(DISCOVERY_ATTEMPTS) {
            if (shmFile.exists() && shmFile.length() > 0) {
                monitorClient.connectViaFile(shmFile)
                return true
            }
            delay(DISCOVERY_DELAY_MS)
        }
        XLog.tag(TAG).w("ADB shm not found after ${DISCOVERY_ATTEMPTS * DISCOVERY_DELAY_MS}ms — did the user run the adb command?")
        return false
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
