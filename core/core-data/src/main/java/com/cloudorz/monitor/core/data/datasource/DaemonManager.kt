package com.cloudorz.monitor.core.data.datasource

import android.util.Log
import com.cloudorz.monitor.core.common.PermissionManager
import com.cloudorz.monitor.core.common.PrivilegeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class DaemonState {
    NOT_NEEDED,
    IDLE,
    LAUNCHING,
    RUNNING,
    FAILED,
}

@Singleton
class DaemonManager @Inject constructor(
    private val daemonLauncher: DaemonLauncher,
    private val daemonDataSource: DaemonDataSource,
    private val daemonClient: DaemonClient,
    private val permissionManager: PermissionManager,
) {
    companion object {
        private const val TAG = "DaemonManager"
        private const val HEARTBEAT_INTERVAL_MS = 10_000L
        private const val HEARTBEAT_FAIL_THRESHOLD = 3
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null

    private val _state = MutableStateFlow(DaemonState.IDLE)
    val state: StateFlow<DaemonState> = _state.asStateFlow()

    fun isRunning(): Boolean = _state.value == DaemonState.RUNNING

    fun needsDaemon(): Boolean {
        val mode = permissionManager.currentMode.value
        return mode == PrivilegeMode.ROOT || mode == PrivilegeMode.SHIZUKU
    }

    suspend fun ensureRunning(): DaemonState = withContext(Dispatchers.IO) {
        val mode = permissionManager.currentMode.value
        if (mode == PrivilegeMode.BASIC || mode == PrivilegeMode.ADB) {
            _state.value = DaemonState.NOT_NEEDED
            return@withContext DaemonState.NOT_NEEDED
        }

        // Quick check: already running?
        if (daemonDataSource.isAvailable()) {
            _state.value = DaemonState.RUNNING
            startHeartbeat()
            Log.i(TAG, "daemon already running")
            return@withContext DaemonState.RUNNING
        }

        // Launch
        _state.value = DaemonState.LAUNCHING
        Log.i(TAG, "launching daemon (mode=$mode)")

        val launched = daemonLauncher.ensureRunning()
        if (launched) {
            _state.value = DaemonState.RUNNING
            daemonDataSource.resetDeadState()
            startHeartbeat()
            Log.i(TAG, "daemon launched successfully")
            return@withContext DaemonState.RUNNING
        }

        _state.value = DaemonState.FAILED
        Log.w(TAG, "daemon launch failed")
        DaemonState.FAILED
    }

    private fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = scope.launch {
            var failures = 0
            while (true) {
                delay(HEARTBEAT_INTERVAL_MS)
                val alive = daemonDataSource.isAvailable()
                if (alive) {
                    failures = 0
                    if (_state.value != DaemonState.RUNNING) {
                        _state.value = DaemonState.RUNNING
                    }
                } else {
                    failures++
                    Log.w(TAG, "heartbeat failed ($failures/$HEARTBEAT_FAIL_THRESHOLD)")
                    if (failures >= HEARTBEAT_FAIL_THRESHOLD) {
                        Log.w(TAG, "daemon dead, attempting restart")
                        _state.value = DaemonState.LAUNCHING
                        daemonDataSource.resetDeadState()
                        val restarted = daemonLauncher.ensureRunning()
                        if (restarted) {
                            _state.value = DaemonState.RUNNING
                            failures = 0
                            Log.i(TAG, "daemon restarted successfully")
                        } else {
                            _state.value = DaemonState.FAILED
                            Log.w(TAG, "daemon restart failed, stopping heartbeat")
                            return@launch
                        }
                    }
                }
            }
        }
    }

    /**
     * Gracefully stops the daemon and relaunches it.
     * Flow: daemon-exit (TCP) → pkill fallback → extract fresh binary → launch.
     */
    suspend fun restart(): DaemonState = withContext(Dispatchers.IO) {
        Log.i(TAG, "restart requested")
        stopHeartbeat()
        _state.value = DaemonState.LAUNCHING

        // 1. TCP command exit (no shell Mutex contention, instant)
        try { daemonClient.sendCommand("daemon-exit") } catch (_: Exception) {}
        delay(500)

        // 2. Fallback force kill (only if daemon still alive)
        if (daemonClient.isAlive()) {
            try { daemonLauncher.stop() } catch (_: Exception) {}
            delay(500)
        }

        // 3. Reset state and relaunch
        daemonDataSource.resetDeadState()
        daemonDataSource.invalidate()
        ensureRunning()
    }

    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
}
