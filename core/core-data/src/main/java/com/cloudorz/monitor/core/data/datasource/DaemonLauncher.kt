package com.cloudorz.monitor.core.data.datasource

import android.content.Context
import android.util.Log
import com.cloudorz.monitor.core.common.PrivilegeMode
import com.cloudorz.monitor.core.common.ShellExecutor
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
 * Approach mirrors Scene (VTools): extract binary via Java IO (no shell unzip needed),
 * set executable permission via File.setExecutable(), then launch via the appropriate
 * privilege channel.
 *
 * Path strategy (same as Scene's up.sh):
 *   ROOT    → extract to filesDir (exec-capable, /data partition), run directly.
 *   ADB /
 *   SHIZUKU → extract to externalCacheDir (world-readable, shell can cp from it),
 *             shell cp → /data/local/tmp/, run from there.
 *
 * Log: /data/local/tmp/daemon.log for all modes (shell and root both write there).
 * BASIC mode: not supported, returns false immediately.
 */
@Singleton
class DaemonLauncher @Inject constructor(
    private val shellExecutor: ShellExecutor,
    private val daemonClient: DaemonClient,
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "DaemonLauncher"
        private const val BINARY_NAME = "monitor-daemon"
        private const val ASSET_PATH = "daemon/monitor-daemon"
        private const val COMMIT_ASSET_PATH = "daemon/daemon-commit.txt"
        const val LOG_PATH = "/data/local/tmp/daemon.log"
        private const val LAUNCH_WAIT_MS = 2_000L
        private const val PING_RETRIES = 3
        private const val LAUNCH_RETRIES = 5
        private const val LAUNCH_RETRY_MS = 600L
    }

    /**
     * Path for ROOT mode: filesDir is on /data (exec-capable).
     * Shell (uid=2000) cannot traverse drwx------ filesDir, so ROOT-only.
     */
    private val rootBinaryPath: String
        get() = "${context.filesDir.absolutePath}/$BINARY_NAME"

    /**
     * Staging path for ADB/SHIZUKU: externalCacheDir is world-readable so shell
     * can `cp` from here, even though it is mounted noexec.
     */
    private val stagingPath: String
        get() = "${context.externalCacheDir?.absolutePath ?: context.cacheDir.absolutePath}/$BINARY_NAME"

    /** Final execution path for ADB/SHIZUKU (shell-writable, exec-capable). */
    private val shellBinaryPath = "/data/local/tmp/$BINARY_NAME"

    val binaryPath: String
        get() = if (shellExecutor.mode == PrivilegeMode.ROOT) rootBinaryPath else shellBinaryPath

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
     * If the running daemon's commit doesn't match the bundled version,
     * it is stopped and relaunched with the fresh binary.
     * Returns true if reachable after this call, false if launch failed or unsupported.
     * ADB mode is not supported: AdbExecutor runs as app uid and cannot write to /data/local/tmp/.
     */
    suspend fun ensureRunning(): Boolean = withContext(Dispatchers.IO) {
        if (shellExecutor.mode == PrivilegeMode.BASIC ||
            shellExecutor.mode == PrivilegeMode.ADB) return@withContext false
        if (daemonClient.isAlive()) {
            if (isVersionMatch()) return@withContext true
            // Version mismatch: stop → delete → extract → relaunch
            Log.e(TAG, "daemon version mismatch, upgrading")
            stop()
            delay(500)
            deleteOldBinary()
            delay(200)
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
        Log.e(TAG, "daemon did not respond after launch")
        false
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        // 1. TCP graceful exit (no shell Mutex contention)
        try { daemonClient.sendCommand("daemon-exit") } catch (_: Exception) {}
        delay(300)

        // 2. Fallback: force kill if still alive
        if (daemonClient.isAlive()) {
            val cmd = "pkill -f $BINARY_NAME 2>/dev/null"
            when (shellExecutor.mode) {
                PrivilegeMode.ROOT -> shellExecutor.executeAsRoot(cmd)
                PrivilegeMode.ADB,
                PrivilegeMode.SHIZUKU -> shellExecutor.execute(cmd)
                PrivilegeMode.BASIC -> Unit
            }
        }
    }

    /** Returns true if the running daemon's commit matches the bundled version. */
    fun isVersionMatch(): Boolean {
        if (expectedCommit.isEmpty()) return true // no version file bundled, skip check
        val resp = daemonClient.sendCommand("daemon-version") ?: return false
        val match = resp.contains(expectedCommit)
        if (!match) Log.e(TAG, "version mismatch: expected=$expectedCommit, got=$resp")
        return match
    }

    /**
     * Deletes the old daemon binary from disk so that [launch] extracts a fresh copy from APK.
     * ROOT: app can directly delete filesDir binary.
     * SHIZUKU: /data/local/tmp/ is shell-writable, need shell rm.
     */
    private suspend fun deleteOldBinary() {
        // Delete app-local copy (ROOT path or staging)
        File(rootBinaryPath).delete()
        File(stagingPath).delete()

        // Delete /data/local/tmp/ copy via shell (app uid cannot write there)
        if (shellExecutor.mode == PrivilegeMode.SHIZUKU) {
            shellExecutor.execute("rm -f '$shellBinaryPath'")
        } else if (shellExecutor.mode == PrivilegeMode.ROOT) {
            shellExecutor.executeAsRoot("rm -f '$shellBinaryPath'")
        }
        Log.e(TAG, "deleted old binary")
    }

    // ---- internal ----

    /**
     * Extracts binary from APK assets via Java IO, then launches via privilege channel.
     *
     * ROOT:    extract → filesDir  →  su -c 'binary' (self-daemonizes)
     * SHIZUKU: strategy1: extract → externalCacheDir, shell cp → /data/local/tmp/
     *          strategy2 (fallback): shell unzip -p APK → /data/local/tmp/
     *          (strategy2 handles Android 13+ MIUI where shell cannot access externalCacheDir)
     * ADB:     not supported (AdbExecutor runs as app uid, cannot write /data/local/tmp/)
     *
     * The daemon self-daemonizes (detaches stdio, writes PID file, like nginx),
     * so no nohup/& is needed. Shell returns immediately after exec.
     */
    private suspend fun launch(): Boolean {
        return when (shellExecutor.mode) {
            PrivilegeMode.ROOT -> {
                val dest = extractBinary(rootBinaryPath) ?: return false
                val result = shellExecutor.executeAsRoot("'$dest'")
                logResult(result, dest)
            }
            PrivilegeMode.SHIZUKU -> {
                // Strategy 1: extract to externalCacheDir (world-readable), then shell cp
                val staged = extractBinary(stagingPath)
                if (staged != null) {
                    val r = shellExecutor.execute(
                        "cp '$staged' '$shellBinaryPath'" +
                            " && chmod 755 '$shellBinaryPath'" +
                            " && '$shellBinaryPath'"
                    )
                    if (r.isSuccess) return logResult(r, shellBinaryPath)
                    Log.e(TAG, "cp from staging failed (${r.stderr}), trying APK extraction")
                }
                // Strategy 2: unzip daemon from APK directly (APK path is always world-readable)
                val apkPath = context.packageCodePath
                val result = shellExecutor.execute(
                    "unzip -p '$apkPath' '$ASSET_PATH' > '$shellBinaryPath'" +
                        " && chmod 755 '$shellBinaryPath'" +
                        " && '$shellBinaryPath'"
                )
                logResult(result, shellBinaryPath)
            }
            PrivilegeMode.ADB,
            PrivilegeMode.BASIC -> false
        }
    }

    /**
     * Extracts the daemon binary from APK assets to [destPath] using Java IO.
     * Sets world-readable + world-executable permissions (mirrors Scene's ll.i()).
     * Falls back to a pre-existing binary at [destPath] (dev/test: manually pushed).
     * Returns the resolved path on success, null if no binary is available.
     */
    private fun extractBinary(destPath: String): String? {
        return try {
            val input = context.assets.open(ASSET_PATH)
            val dest = File(destPath)
            dest.parentFile?.mkdirs()
            input.use { ins -> dest.outputStream().use { ins.copyTo(it) } }
            dest.setReadable(true, false)
            dest.setExecutable(true, false)
            Log.e(TAG, "extracted daemon to $destPath")
            destPath
        } catch (e: Exception) {
            Log.e(TAG, "asset not bundled ($ASSET_PATH): ${e.message}")
            // Dev fallback: manually pushed binary
            if (File(destPath).canExecute() || File(shellBinaryPath).exists()) {
                Log.e(TAG, "using pre-existing binary")
                if (File(destPath).exists()) destPath else shellBinaryPath
            } else {
                Log.e(TAG, "no daemon binary available")
                null
            }
        }
    }

    private fun logResult(result: com.cloudorz.monitor.core.common.CommandResult, binary: String): Boolean {
        return if (result.isSuccess) {
            Log.e(TAG, "daemon launched via ${shellExecutor.mode} ($binary)")
            true
        } else {
            Log.e(TAG, "daemon launch failed (${shellExecutor.mode}): ${result.stderr}")
            false
        }
    }
}
