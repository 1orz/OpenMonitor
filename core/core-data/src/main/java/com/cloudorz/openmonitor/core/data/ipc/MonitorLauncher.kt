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
 * daemonizes and listens on an AF_UNIX socket, and the app connects via
 * [DaemonClient].
 *
 *  - **ROOT (libsu)**: [Shell.cmd] exec's the packaged binary via su. The
 *    daemon binds its socket inside the app's private filesDir.
 *
 *  - **Shizuku**: [ShellExecutor.execute] routes to [ShizukuExecutor] which
 *    runs `sh -c <binary>` inside Shizuku's UserService process (uid 2000
 *    or uid 0 depending on Shizuku's install). `--data-dir` points to the
 *    app's filesDir; the daemon falls back to `/data/local/tmp/openmonitor`
 *    if it cannot bind there.
 *
 *  - **ADB**: the user starts the binary manually from a host shell. The
 *    app skips exec and goes straight to socket discovery.
 */
@Singleton
class MonitorLauncher @Inject constructor(
    private val shellExecutor: ShellExecutor,
    private val daemonClient: DaemonClient,
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "MonitorLauncher"
        private const val SERVER_BIN = "libopenmonitor-server.so"
        private const val DISCOVERY_ATTEMPTS = 10
        private const val DISCOVERY_DELAY_MS = 300L
    }

    val binaryPath: String
        get() = "${context.applicationInfo.nativeLibraryDir}/$SERVER_BIN"

    /**
     * Full adb shell command the user runs on their host to launch the
     * server in ADB mode. Shown verbatim in the UI so copy-paste Just Works.
     */
    fun adbLaunchCommand(): String {
        val pkg = context.packageName
        return "$binaryPath --mode adb --app-package $pkg"
    }

    /** Ensure a privileged server is running and connected. Idempotent. */
    suspend fun ensureRunning(): Boolean = withContext(Dispatchers.IO) {
        // Fast path: already connected (or can reconnect immediately).
        if (daemonClient.connect()) return@withContext true

        when (shellExecutor.mode) {
            PrivilegeMode.ROOT -> launchViaLibsu()
            PrivilegeMode.SHIZUKU -> launchViaShizuku()
            PrivilegeMode.ADB -> pollConnect("ADB")
            PrivilegeMode.BASIC -> false
        }
    }

    /**
     * Shut down the daemon and clean up socket artifacts.
     *
     * This is a suspend function so callers must invoke it from a coroutine.
     */
    suspend fun shutdown() {
        // Ask the daemon to exit gracefully (best-effort).
        daemonClient.requestExit()
        // Drop local connection so the UI flips to "not running" immediately.
        daemonClient.disconnect()

        // Give the daemon a moment to exit on its own. The accept loop in
        // server-rs polls exit_flag every ~200ms worst-case, so 500ms gives
        // comfortable margin for graceful shutdown.
        delay(500)

        // pkill fallback — ensure the old server is gone before a new launch.
        pkillDaemons()

        // Wipe stale socket files and sentinel files so a subsequent launch
        // doesn't briefly connect to a ghost socket.
        val filesDir = context.filesDir.absolutePath
        val fallbackDir = "/data/local/tmp/openmonitor"
        listOf(
            "$filesDir/openmonitor.sock",
            "$filesDir/sock.path",
            "$fallbackDir/openmonitor.sock",
            "$fallbackDir/sock.path",
        ).forEach { path ->
            runCatching { File(path).delete() }
        }
    }

    /**
     * Kill any lingering daemon processes. Covers upgrade leftovers (v1 -> v2),
     * keystore-rotation mismatches, and mode-switch stragglers. No-op if
     * nothing matches. Called both from [shutdown] (post-graceful fallback)
     * and before exec in [launchViaLibsu]/[launchViaShizuku].
     */
    private fun pkillDaemons() {
        runCatching {
            Shell.cmd(
                "pkill -9 -f libopenmonitor-server 2>/dev/null; " +
                "pkill -9 -f openmonitor-server 2>/dev/null; " +
                "su -c 'pkill -9 -f libopenmonitor-server' 2>/dev/null; " +
                "su -c 'pkill -9 -f openmonitor-server' 2>/dev/null"
            ).exec()
        }
    }

    private suspend fun launchViaLibsu(): Boolean {
        // Kill stale/legacy daemon before exec'ing a fresh one. We only reach
        // here after the fast-path connect() in ensureRunning() already failed,
        // so any surviving process is either incompatible or zombie.
        pkillDaemons()

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

        return pollConnect("libsu")
    }

    /**
     * Shizuku mode — ask Shizuku's [ShellUserService] to exec our binary.
     * The server daemonizes (double-fork -> parent exits) so the IPC
     * executeCommand call returns immediately after the first fork; the
     * surviving child runs under Shizuku's uid (2000 or 0) and binds its
     * socket. `--data-dir` is set to the app's filesDir so the daemon
     * prefers that location; it falls back to `/data/local/tmp/openmonitor`
     * if it cannot bind there.
     */
    private suspend fun launchViaShizuku(): Boolean {
        // Kill stale/legacy daemon before exec'ing a fresh one (same rationale
        // as launchViaLibsu — we only get here when connect() already failed).
        pkillDaemons()

        val pkg = context.packageName
        val dataDir = context.filesDir.absolutePath
        val cmd = "'$binaryPath' --mode shizuku --data-dir '$dataDir' --app-package '$pkg'"
        val result = try {
            shellExecutor.execute(cmd)
        } catch (e: Throwable) {
            XLog.tag(TAG).e("Shizuku exec threw: ${e.message}")
            return false
        }
        if (result.exitCode != 0) {
            XLog.tag(TAG).e("Shizuku launch exit=${result.exitCode} stderr=${result.stderr}")
            return false
        }
        XLog.tag(TAG).i("server launched via Shizuku shell")
        return pollConnect("Shizuku")
    }

    /**
     * Poll [DaemonClient.connect] up to [DISCOVERY_ATTEMPTS] times, waiting
     * [DISCOVERY_DELAY_MS] between each attempt. Returns true on first
     * successful connection.
     */
    private suspend fun pollConnect(label: String): Boolean {
        XLog.tag(TAG).i("$label: polling daemon socket")
        repeat(DISCOVERY_ATTEMPTS) {
            if (daemonClient.connect()) return true
            delay(DISCOVERY_DELAY_MS)
        }
        XLog.tag(TAG).w("$label: daemon not reachable after ${DISCOVERY_ATTEMPTS * DISCOVERY_DELAY_MS}ms")
        return false
    }
}
