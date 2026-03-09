package com.cloudorz.openmonitor.core.data.datasource

import android.content.Context
import android.util.Log
import com.cloudorz.openmonitor.core.common.PrivilegeMode
import com.cloudorz.openmonitor.core.common.ShellExecutor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
        private const val LAUNCH_WAIT_MS = 2_000L
        private const val PING_RETRIES = 3
        private const val LAUNCH_RETRIES = 5
        private const val LAUNCH_RETRY_MS = 600L
        /** Daemon stdout/stderr log path. */
        const val LOG_PATH = "/data/local/tmp/daemon.log"
    }

    /** Path to daemon binary in the APK's native library directory. */
    val binaryPath: String
        get() = "${context.applicationInfo.nativeLibraryDir}/$LIB_NAME"

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
            Log.e(TAG, "daemon version mismatch, upgrading")
            stop()
            delay(500)
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
        try { daemonClient.sendCommand("daemon-exit") } catch (_: Exception) {}
        delay(300)
        if (daemonClient.isAlive()) {
            val cmd = "pkill -f monitor-daemon 2>/dev/null"
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
        if (expectedCommit.isEmpty()) return true
        val resp = daemonClient.sendCommand("daemon-version") ?: return false
        val match = resp.contains(expectedCommit)
        if (!match) Log.e(TAG, "version mismatch: expected=$expectedCommit, got=$resp")
        return match
    }

    /**
     * Launches the daemon from nativeLibraryDir.
     * ROOT:    su -c '<binary>'
     * SHIZUKU: shell exec '<binary>'
     */
    private suspend fun launch(): Boolean {
        val binary = binaryPath
        val cmd = "nohup '$binary' > $LOG_PATH 2>&1 &"
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

    private fun logResult(result: com.cloudorz.openmonitor.core.common.CommandResult, binary: String): Boolean {
        return if (result.isSuccess) {
            Log.e(TAG, "daemon launched via ${shellExecutor.mode} ($binary)")
            true
        } else {
            Log.e(TAG, "daemon launch failed (${shellExecutor.mode}): ${result.stderr}")
            false
        }
    }
}
