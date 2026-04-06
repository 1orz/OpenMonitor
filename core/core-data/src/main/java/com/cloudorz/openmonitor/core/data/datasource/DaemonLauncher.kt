package com.cloudorz.openmonitor.core.data.datasource

import android.annotation.SuppressLint
import android.content.Context
import com.elvishew.xlog.XLog
import com.cloudorz.openmonitor.core.common.PrivilegeMode
import com.cloudorz.openmonitor.core.common.ShellExecutor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the lifecycle of the monitor-daemon binary on the device.
 *
 * Binary is packaged as `libmonitor-daemon.so` in the APK's native library directory
 * (like Shizuku's libshizuku.so). This means:
 * - No extraction needed: binary is available at `nativeLibraryDir/libmonitor-daemon.so`
 * - Shell user can read and execute from nativeLibraryDir
 * - ADB mode: user runs `adb shell <nativeLibDir>/libmonitor-daemon.so`
 *
 * ROOT:    su -c '<nativeLibDir>/libmonitor-daemon.so'
 * SHIZUKU: shell exec '<nativeLibDir>/libmonitor-daemon.so'
 * ADB:    user manually runs via adb shell (not auto-launched)
 * BASIC:  no daemon needed
 */
@Singleton
class DaemonLauncher @Inject constructor(
    private val shellExecutor: ShellExecutor,
    private val daemonClient: DaemonClient,
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "DaemonLauncher"
        private const val LIB_NAME = "libmonitor-daemon.so"
        private const val COMMIT_ASSET_PATH = "daemon/daemon-commit.txt"
        private const val DAEMON_DIR_NAME = "daemon"
        private const val LAUNCH_WAIT_MS = 1_500L
        private const val PING_RETRIES = 3
        private const val LAUNCH_RETRIES = 2
        private const val LAUNCH_RETRY_MS = 500L
    }

    /** Path to daemon binary in the APK's native library directory. */
    val binaryPath: String
        get() = "${context.applicationInfo.nativeLibraryDir}/$LIB_NAME"

    /** Directory for daemon PID and log files (inside app's private storage). */
    val dataDir: File by lazy {
        context.filesDir.resolve(DAEMON_DIR_NAME).also { dir ->
            if (!dir.exists()) dir.mkdirs()
            // Ensure both root and shell users can write
            @SuppressLint("SetWorldReadable") dir.setReadable(true, false)
            @SuppressLint("SetWorldWritable") dir.setWritable(true, false)
            dir.setExecutable(true, false)
        }
    }

    /** Expected daemon commit from bundled assets. Empty if no version file. */
    val expectedCommit: String by lazy {
        try {
            context.assets.open(COMMIT_ASSET_PATH).bufferedReader().use {
                it.readLine()?.trim() ?: ""
            }
        } catch (_: Exception) { "" }
    }

    /**
     * Ensures the daemon is running with the correct version.
     * Only works for ROOT and SHIZUKU modes (auto-launch).
     * ADB mode: daemon must be started manually by the user.
     * Returns true if reachable after this call.
     */
    suspend fun ensureRunning(): Boolean = withContext(Dispatchers.IO) {
        if (shellExecutor.mode == PrivilegeMode.BASIC ||
            shellExecutor.mode == PrivilegeMode.ADB) return@withContext false
        if (daemonClient.isAlive()) {
            if (isVersionMatch()) return@withContext true
            XLog.tag(TAG).e("daemon version mismatch, upgrading")
            fullStop()
        }

        var launched = false
        repeat(LAUNCH_RETRIES) {
            if (launched) return@repeat
            launched = launch()
            if (!launched) delay(LAUNCH_RETRY_MS)
        }
        if (!launched) return@withContext false

        delay(LAUNCH_WAIT_MS)
        repeat(PING_RETRIES) {
            if (daemonClient.isAlive()) return@withContext true
            delay(500L)
        }
        XLog.tag(TAG).e("daemon did not respond after launch")
        false
    }

    /**
     * Fully stops any running daemon: graceful TCP exit → force kill → wait for port release.
     * Safe to call even if no daemon is running.
     */
    suspend fun fullStop() = withContext(Dispatchers.IO) {
        // 1. Try graceful TCP shutdown
        try { daemonClient.sendCommand("daemon-exit") } catch (_: Exception) {}
        delay(500)

        // 2. Force kill + cleanup PID files (always run for cleanup)
        if (daemonClient.isAlive()) {
            XLog.tag(TAG).e("daemon still alive after exit command, force killing")
        }
        execCleanup()

        // 3. Wait up to 2s for the daemon to release the port before returning
        repeat(10) {
            if (!daemonClient.isAlive()) return@withContext
            delay(200)
        }
        if (daemonClient.isAlive()) {
            XLog.tag(TAG).e("daemon still alive after fullStop, port may be occupied")
        }
        daemonClient.disconnect()
    }

    /** Returns true if the running daemon's commit matches the bundled version. */
    fun isVersionMatch(): Boolean {
        if (expectedCommit.isEmpty()) return true
        val resp = daemonClient.sendCommand("daemon-version") ?: return false
        val match = resp.contains(expectedCommit)
        if (!match) XLog.tag(TAG).e("version mismatch: expected=$expectedCommit, got=$resp")
        return match
    }

    /**
     * Launches the daemon from nativeLibraryDir.
     * ROOT:    su -c '<binary> --data-dir <dir>'
     * SHIZUKU: shell exec '<binary> --data-dir <dir>'
     */
    /** Rotate daemon.log to daemon-YYYY-MM-DD.log before starting a new session.
     *  Uses shell commands because the file may be owned by root. */
    private suspend fun rotateDaemonLog() {
        try {
            val dir = dataDir.absolutePath
            val logFile = File(dataDir, "daemon.log")
            if (!logFile.exists() || logFile.length() == 0L) return
            val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(java.util.Date(logFile.lastModified()))
            val archiveName = "daemon-$date.log"
            // Use shell to handle root-owned files
            val rotateCmd = "cat '$dir/daemon.log' >> '$dir/$archiveName' 2>/dev/null; " +
                "> '$dir/daemon.log' 2>/dev/null; " +
                "chmod 666 '$dir/$archiveName' 2>/dev/null"
            when (shellExecutor.mode) {
                PrivilegeMode.ROOT -> shellExecutor.executeAsRoot(rotateCmd)
                PrivilegeMode.SHIZUKU,
                PrivilegeMode.ADB -> shellExecutor.execute(rotateCmd)
                PrivilegeMode.BASIC -> {
                    // Try Java-level rotation (only works if app owns the file)
                    val archive = File(dataDir, archiveName)
                    if (archive.exists()) archive.appendBytes(logFile.readBytes())
                    else logFile.renameTo(archive)
                    if (logFile.exists()) logFile.writeText("")
                }
            }
            // Cleanup: remove archives older than 7 days
            val cutoff = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
            dataDir.listFiles()
                ?.filter { it.name.startsWith("daemon-") && it.name.endsWith(".log") && it.lastModified() < cutoff }
                ?.forEach { it.delete() }
        } catch (e: Exception) {
            XLog.tag(TAG).e("rotateDaemonLog failed: ${e.message}")
        }
    }

    /** List daemon log files (current + archives), newest first. */
    fun listDaemonLogFiles(): List<File> {
        val files = mutableListOf<File>()
        val current = File(dataDir, "daemon.log")
        if (current.exists() && current.length() > 0) files.add(current)
        dataDir.listFiles()
            ?.filter { it.name.startsWith("daemon-") && it.name.endsWith(".log") && it.length() > 0 }
            ?.sortedByDescending { it.name }
            ?.let { files.addAll(it) }
        return files
    }

    private suspend fun launch(): Boolean {
        val binary = binaryPath
        val dir = dataDir.absolutePath
        rotateDaemonLog()
        // Daemon self-daemonizes: forks a child with Setsid, redirects stdio to daemon.log,
        // writes PID file, then parent exits. No shell nohup/redirection needed.
        val cmd = "'$binary' --data-dir '$dir'"
        return when (shellExecutor.mode) {
            PrivilegeMode.ROOT -> {
                val result = shellExecutor.executeAsRoot(cmd)
                logResult(result, binary)
            }
            PrivilegeMode.SHIZUKU -> {
                val result = shellExecutor.execute(cmd)
                logResult(result, binary)
            }
            PrivilegeMode.ADB,
            PrivilegeMode.BASIC -> false
        }
    }

    /**
     * Force-kills daemon processes and cleans up PID files.
     */
    private suspend fun execCleanup() {
        val dir = dataDir.absolutePath
        val cmd = "pkill -9 -f libmonitor-daemon 2>/dev/null; " +
            "pkill -9 -f monitor-daemon 2>/dev/null; " +
            "rm -f '$dir/monitor-daemon.pid' 2>/dev/null; " +
            "chmod 777 '$dir' 2>/dev/null"
        when (shellExecutor.mode) {
            PrivilegeMode.ROOT -> shellExecutor.executeAsRoot(cmd)
            PrivilegeMode.SHIZUKU,
            PrivilegeMode.ADB -> shellExecutor.execute(cmd)
            PrivilegeMode.BASIC -> Unit
        }
    }

    private fun logResult(result: com.cloudorz.openmonitor.core.common.CommandResult, binary: String): Boolean {
        return if (result.isSuccess) {
            XLog.tag(TAG).e("daemon launched via ${shellExecutor.mode} ($binary)")
            true
        } else {
            XLog.tag(TAG).e("daemon launch failed (${shellExecutor.mode}): ${result.stderr}")
            false
        }
    }
}
