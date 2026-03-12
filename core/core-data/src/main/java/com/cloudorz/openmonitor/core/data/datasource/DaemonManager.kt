package com.cloudorz.openmonitor.core.data.datasource

import com.elvishew.xlog.XLog
import com.cloudorz.openmonitor.core.common.PermissionManager
import com.cloudorz.openmonitor.core.common.PrivilegeMode
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

    /** Whether daemon is useful for the current mode. */
    fun needsDaemon(): Boolean {
        val mode = permissionManager.currentMode.value
        return mode == PrivilegeMode.ROOT || mode == PrivilegeMode.SHIZUKU || mode == PrivilegeMode.ADB
    }

    /** Whether daemon can be auto-launched (ROOT/SHIZUKU only). */
    fun canAutoLaunchDaemon(): Boolean {
        val mode = permissionManager.currentMode.value
        return mode == PrivilegeMode.ROOT || mode == PrivilegeMode.SHIZUKU
    }

    suspend fun ensureRunning(): DaemonState = withContext(Dispatchers.IO) {
        val mode = permissionManager.currentMode.value

        // BASIC: no daemon needed
        if (mode == PrivilegeMode.BASIC) {
            _state.value = DaemonState.NOT_NEEDED
            return@withContext DaemonState.NOT_NEEDED
        }

        // ADB: daemon must be started manually, just check availability
        if (mode == PrivilegeMode.ADB) {
            if (daemonDataSource.isAvailable()) {
                _state.value = DaemonState.RUNNING
                startHeartbeat()
                XLog.tag(TAG).e("ADB mode: daemon already running")
                return@withContext DaemonState.RUNNING
            }
            _state.value = DaemonState.IDLE // Not FAILED — user hasn't started it yet
            XLog.tag(TAG).e("ADB mode: daemon not running (user must start manually)")
            return@withContext DaemonState.IDLE
        }

        // ROOT/SHIZUKU: auto-launch
        if (daemonDataSource.isAvailable()) {
            if (daemonLauncher.isVersionMatch()) {
                _state.value = DaemonState.RUNNING
                startHeartbeat()
                XLog.tag(TAG).e("daemon already running")
                return@withContext DaemonState.RUNNING
            }
            XLog.tag(TAG).e("daemon alive but version mismatch, will upgrade")
        }

        _state.value = DaemonState.LAUNCHING
        XLog.tag(TAG).e("launching daemon (mode=$mode)")

        val launched = daemonLauncher.ensureRunning()
        if (launched) {
            _state.value = DaemonState.RUNNING
            daemonDataSource.resetDeadState()
            startHeartbeat()
            XLog.tag(TAG).e("daemon launched successfully")
            return@withContext DaemonState.RUNNING
        }

        _state.value = DaemonState.FAILED
        XLog.tag(TAG).e("daemon launch failed")
        DaemonState.FAILED
    }

    /**
     * Handles mode transition: stops daemon under OLD mode, sets new mode, relaunches.
     * [applyNewMode] callback sets permissionManager.currentMode before relaunch.
     */
    suspend fun switchMode(
        oldMode: PrivilegeMode,
        newMode: PrivilegeMode,
        applyNewMode: () -> Unit,
    ): DaemonState = withContext(Dispatchers.IO) {
        XLog.tag(TAG).e("switchMode: $oldMode → $newMode")
        val oldAutoLaunch = oldMode == PrivilegeMode.ROOT || oldMode == PrivilegeMode.SHIZUKU
        val newAutoLaunch = newMode == PrivilegeMode.ROOT || newMode == PrivilegeMode.SHIZUKU

        // Stop daemon under old mode first (uses old executor)
        if (oldAutoLaunch || _state.value == DaemonState.RUNNING) {
            stopDaemon()
        }

        // Switch executor to new mode BEFORE launching
        applyNewMode()
        daemonDataSource.resetDeadState()
        daemonDataSource.invalidate()

        if (!newAutoLaunch) {
            if (newMode == PrivilegeMode.ADB) return@withContext ensureRunning()
            _state.value = DaemonState.NOT_NEEDED
            return@withContext DaemonState.NOT_NEEDED
        }

        // Launch under new mode
        ensureRunning()
    }

    /**
     * Gracefully stops the daemon and relaunches it.
     */
    suspend fun restart(): DaemonState = withContext(Dispatchers.IO) {
        XLog.tag(TAG).e("restart requested")
        stopDaemon()
        daemonDataSource.resetDeadState()
        daemonDataSource.invalidate()
        ensureRunning()
    }

    /** Fully stops the daemon: graceful exit → force kill → cleanup PID files. */
    private suspend fun stopDaemon() {
        stopHeartbeat()
        _state.value = DaemonState.LAUNCHING
        daemonLauncher.fullStop()
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
                    XLog.tag(TAG).e("heartbeat failed ($failures/$HEARTBEAT_FAIL_THRESHOLD)")
                    if (failures >= HEARTBEAT_FAIL_THRESHOLD) {
                        XLog.tag(TAG).e("daemon dead, attempting restart")
                        _state.value = DaemonState.LAUNCHING
                        daemonDataSource.resetDeadState()
                        if (canAutoLaunchDaemon()) {
                            val restarted = daemonLauncher.ensureRunning()
                            if (restarted) {
                                _state.value = DaemonState.RUNNING
                                failures = 0
                                XLog.tag(TAG).e("daemon restarted successfully")
                            } else {
                                _state.value = DaemonState.FAILED
                                XLog.tag(TAG).e("daemon restart failed, stopping heartbeat")
                                return@launch
                            }
                        } else {
                            // ADB mode: can't auto-restart
                            _state.value = DaemonState.FAILED
                            XLog.tag(TAG).e("ADB mode: daemon lost, user must restart manually")
                            return@launch
                        }
                    }
                }
            }
        }
    }

    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
}
