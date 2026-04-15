package com.cloudorz.openmonitor.core.data.ipc

import android.content.Context
import com.cloudorz.openmonitor.core.common.PrivilegeMode
import com.cloudorz.openmonitor.core.common.ShellExecutor
import com.elvishew.xlog.XLog
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brings up the privileged openmonitor-server binary. All three launch paths
 * now converge on the same contract: *something* exec's the binary with
 * enough privilege to open /dev/binder + read /proc + /sys, the binary
 * daemonizes and writes a 4 KB file-backed shared memory region, and the
 * app mmaps that file read-only.
 *
 *  - **ROOT (libsu)**: [Shell.cmd] exec's the packaged binary via su. shm
 *    lives in the app's private filesDir (server writes it as root, app
 *    reads it as its own uid — same fs permissions).
 *
 *  - **Shizuku**: [ShellExecutor.execute] routes to [ShizukuExecutor] which
 *    runs `sh -c <binary>` inside Shizuku's UserService process (uid 2000
 *    or uid 0 depending on Shizuku's install). shm lives at the shell path
 *    since Shizuku's process has no write access to the app's filesDir.
 *
 *  - **ADB**: the user starts the binary manually from a host shell. Same
 *    shm path as Shizuku.
 *
 * We used to have a Shizuku `bindUserService` path that returned an IBinder
 * from a JNI-backed RustEntry class. That never worked: rsbinder 0.6
 * implements the binder protocol in pure Rust and does not produce AIBinder
 * pointers, so the IBinder handed back through JNI was a null jobject and
 * Shizuku's starter died with `System.exit(1)`. The file-shm pattern
 * sidesteps the AIBinder interop problem entirely.
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
         * Shell-writable shm path used by ADB and Shizuku modes. Derived
         * from `<data_dir>/server/snapshot.shm` with data_dir defaulting
         * to /data/local/tmp/openmonitor in [server-rs/src/main.rs].
         * The file is chmod'd 0644 and the parent dir 0755 so the
         * untrusted_app can mmap it (SELinux allows untrusted_app →
         * shell_data_file:file read on stock AOSP).
         */
        private const val SHELL_SHM_PATH = "/data/local/tmp/openmonitor/server/snapshot.shm"
        private const val DISCOVERY_ATTEMPTS = 10
        private const val DISCOVERY_DELAY_MS = 300L
    }

    val binaryPath: String
        get() = "${context.applicationInfo.nativeLibraryDir}/$SERVER_BIN"

    /** Canonical file path the shell-launched server writes its shm to. */
    val adbShmPath: String get() = SHELL_SHM_PATH

    /**
     * Full adb shell command the user runs on their host to launch the
     * server in ADB mode. Shown verbatim in the UI so copy-paste Just Works.
     */
    fun adbLaunchCommand(): String {
        val pkg = context.packageName
        return "$binaryPath --mode adb --app-package $pkg"
    }

    /** Ensure a privileged server is running. Idempotent. */
    suspend fun ensureRunning(): Boolean = withContext(Dispatchers.IO) {
        when (shellExecutor.mode) {
            PrivilegeMode.ROOT -> launchViaLibsu()
            PrivilegeMode.SHIZUKU -> launchViaShizuku()
            PrivilegeMode.ADB -> discoverShellShm("ADB")
            PrivilegeMode.BASIC -> false
        }
    }

    fun shutdown() {
        // Drop the client-side view of the connection first so the UI flips
        // to "not running" immediately — file-backed shm has no death
        // notification we could rely on.
        monitorClient.disconnect()

        // Synchronous exec (not submit): we must ensure the old server is
        // gone before the caller triggers a new launch, otherwise the fresh
        // process races against the stale file.
        runCatching {
            Shell.cmd(
                "pkill -9 -f libopenmonitor-server 2>/dev/null; " +
                "pkill -9 -f openmonitor-server 2>/dev/null; " +
                "su -c 'pkill -9 -f libopenmonitor-server' 2>/dev/null; " +
                "su -c 'pkill -9 -f openmonitor-server' 2>/dev/null"
            ).exec()
        }
        // Wipe stale shm files so a subsequent launch doesn't briefly
        // connect to ghost data before the new server writes anything.
        runCatching { File(context.filesDir, "server/snapshot.shm").delete() }
        runCatching { File(SHELL_SHM_PATH).delete() }
    }

    private suspend fun launchViaLibsu(): Boolean {
        val shmFile = File(context.filesDir, "server/snapshot.shm")
        // Fast path: server already running (e.g. survived app restart).
        // Skipping the exec avoids the Rust side opening the shm file with
        // O_TRUNC and racing the existing server's mmap.
        if (shmFile.exists() && shmFile.length() > 0) {
            XLog.tag(TAG).i("libsu: server already up, attaching to existing shm")
            monitorClient.connectViaFile(shmFile)
            return true
        }

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

        return waitForShm(shmFile, "libsu")
    }

    /**
     * Shizuku mode — ask Shizuku's [ShellUserService] to exec our binary.
     * The server daemonizes (double-fork → parent exits) so the IPC
     * executeCommand call returns immediately after the first fork; the
     * surviving child runs under Shizuku's uid (2000 or 0) and writes
     * the shm to [SHELL_SHM_PATH].
     *
     * Idempotent: if the shm is already present we skip the exec entirely
     * so we never truncate-race an already-running server.
     */
    private suspend fun launchViaShizuku(): Boolean {
        val shmFile = File(SHELL_SHM_PATH)
        if (shmFile.exists() && shmFile.length() > 0) {
            XLog.tag(TAG).i("Shizuku: server already up, attaching to existing shm")
            monitorClient.connectViaFile(shmFile)
            return true
        }

        val pkg = context.packageName
        val cmd = "'$binaryPath' --mode shizuku --app-package '$pkg'"
        val result = try {
            shellExecutor.execute(cmd)
        } catch (e: Throwable) {
            // The most common cause is CancellationException from the
            // caller's scope (switchMode's withTimeoutOrNull, compose
            // scope tear-down, etc.). The server itself may still have
            // been started by Shizuku — the next retry will pick up the
            // shm via the fast path above.
            XLog.tag(TAG).e("Shizuku exec threw: ${e.message}")
            return false
        }
        if (result.exitCode != 0) {
            XLog.tag(TAG).e("Shizuku launch exit=${result.exitCode} stderr=${result.stderr}")
            return false
        }
        XLog.tag(TAG).i("server launched via Shizuku shell")
        return waitForShm(shmFile, "Shizuku")
    }

    /**
     * ADB / post-launch polling. Shared by ADB (user-started) mode and the
     * Shizuku/libsu paths after they kick off their exec.
     */
    private suspend fun discoverShellShm(label: String): Boolean =
        waitForShm(File(SHELL_SHM_PATH), label)

    private suspend fun waitForShm(shmFile: File, label: String): Boolean {
        XLog.tag(TAG).i("$label: polling ${shmFile.absolutePath}")
        repeat(DISCOVERY_ATTEMPTS) {
            if (shmFile.exists() && shmFile.length() > 0) {
                monitorClient.connectViaFile(shmFile)
                return true
            }
            delay(DISCOVERY_DELAY_MS)
        }
        XLog.tag(TAG).w("$label shm not found after ${DISCOVERY_ATTEMPTS * DISCOVERY_DELAY_MS}ms")
        return false
    }
}
