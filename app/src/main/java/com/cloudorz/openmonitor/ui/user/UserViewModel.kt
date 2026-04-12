package com.cloudorz.openmonitor.ui.user

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.common.PrivilegeMode
import com.cloudorz.openmonitor.core.data.datasource.DaemonClient
import com.cloudorz.openmonitor.core.data.repository.DeviceIdentityRepository
import com.cloudorz.openmonitor.core.model.identity.DeviceFingerprint
import com.cloudorz.openmonitor.core.model.identity.DeviceIdentity
import com.cloudorz.openmonitor.core.ui.HapticFeedbackManager
import com.cloudorz.openmonitor.core.data.datasource.DaemonLauncher
import com.cloudorz.openmonitor.core.data.datasource.DaemonManager
import com.cloudorz.openmonitor.core.data.datasource.DaemonState
import com.cloudorz.openmonitor.core.ui.theme.ColorMode
import com.cloudorz.openmonitor.data.repository.ThemeSettingsRepository
import com.cloudorz.openmonitor.service.FloatMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.json.JSONObject
import androidx.core.content.edit
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val daemonClient: DaemonClient,
    private val daemonManager: DaemonManager,
    private val daemonLauncher: DaemonLauncher,
    private val themeRepo: ThemeSettingsRepository,
    private val identityRepository: DeviceIdentityRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    data class DaemonStatus(
        val checking: Boolean = false,
        val connected: Boolean = false,
        val version: String? = null,
        val currentCommit: String? = null,
        val expectedCommit: String? = null,
        val runner: String? = null,
        val uptimeSeconds: Long? = null,
        val checkedOnce: Boolean = false,
    )

    data class PollSettings(
        val intervalMs: Long = FloatMonitorService.DEFAULT_POLL_INTERVAL,
    )

    companion object {
        const val KEY_DARK_MODE = "dark_mode"
        private const val SWITCH_TIMEOUT_MS = 15_000L
    }

    private val prefs = context.getSharedPreferences("monitor_settings", Context.MODE_PRIVATE)

    private val _daemonStatus = MutableStateFlow(DaemonStatus())
    val daemonStatus: StateFlow<DaemonStatus> = _daemonStatus.asStateFlow()

    private val _pollSettings = MutableStateFlow(
        PollSettings(
            intervalMs = prefs.getLong(FloatMonitorService.KEY_POLL_INTERVAL, FloatMonitorService.DEFAULT_POLL_INTERVAL),
        )
    )
    val pollSettings: StateFlow<PollSettings> = _pollSettings.asStateFlow()

    private val _darkMode = MutableStateFlow(prefs.getInt(KEY_DARK_MODE, 0))
    val darkMode: StateFlow<Int> = _darkMode.asStateFlow()

    private val _hapticEnabled = MutableStateFlow(prefs.getBoolean("haptic_enabled", true))
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    /** Daemon binary path for ADB instructions. */
    val daemonBinaryPath: String get() = daemonLauncher.binaryPath

    private var refreshJob: Job? = null
    private var adbWatcherJob: Job? = null

    init {
        viewModelScope.launch {
            daemonManager.state.collect { state ->
                if (state == DaemonState.RUNNING) {
                    _daemonStatus.value = buildStatus(alive = true)
                    sendLogLevel("debug")
                    startRefreshLoop()
                } else if (state == DaemonState.FAILED || state == DaemonState.NOT_NEEDED) {
                    stopRefreshLoop()
                    _daemonStatus.value = buildStatus(alive = false)
                }
            }
        }
    }

    private fun startRefreshLoop() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1000)
                val info = fetchDaemonInfo()
                _daemonStatus.update {
                    it.copy(
                        runner = info.runner,
                        uptimeSeconds = info.uptimeSeconds,
                        version = info.version ?: it.version,
                        currentCommit = info.commit ?: it.currentCommit,
                    )
                }
            }
        }
    }

    private fun stopRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = null
    }

    /** Silent background polling for ADB mode — checks daemon every 3s until connected. */
    fun startAdbWatcher() {
        if (adbWatcherJob?.isActive == true) return
        adbWatcherJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(3000)
                if (_daemonStatus.value.connected) break
                val alive = daemonClient.isAlive()
                if (alive) {
                    val state = withTimeoutOrNull(5_000L) { daemonManager.ensureRunning() }
                    if (state == DaemonState.RUNNING) {
                        _daemonStatus.value = buildStatus(alive = true)
                        break
                    }
                }
            }
        }
    }

    fun stopAdbWatcher() {
        adbWatcherJob?.cancel()
        adbWatcherJob = null
    }

    fun checkDaemon() {
        if (_daemonStatus.value.checking) return
        viewModelScope.launch {
            _daemonStatus.value = _daemonStatus.value.copy(checking = true)
            val state = withTimeoutOrNull(5_000L) { daemonManager.ensureRunning() }
            _daemonStatus.value = buildStatus(alive = state == DaemonState.RUNNING)
        }
    }

    fun restartDaemon() {
        if (_daemonStatus.value.checking) return
        viewModelScope.launch {
            _daemonStatus.value = _daemonStatus.value.copy(checking = true)
            val state = withTimeoutOrNull(5_000L) { daemonManager.restart() }
            _daemonStatus.value = buildStatus(alive = state == DaemonState.RUNNING)
        }
    }

    private suspend fun buildStatus(alive: Boolean): DaemonStatus {
        val info = if (alive) withContext(Dispatchers.IO) { fetchDaemonInfo() } else null
        return DaemonStatus(
            connected = alive,
            version = info?.version,
            currentCommit = info?.commit,
            expectedCommit = daemonLauncher.expectedCommit.ifEmpty { null },
            runner = info?.runner,
            uptimeSeconds = info?.uptimeSeconds,
            checkedOnce = true,
        )
    }

    @Deprecated("Use setColorMode() instead")
    fun setDarkMode(mode: Int) {
        prefs.edit { putInt(KEY_DARK_MODE, mode) }
        _darkMode.value = mode
    }

    // --- Theme settings ---

    private val _colorMode = MutableStateFlow(themeRepo.colorMode)
    val colorMode: StateFlow<Int> = _colorMode.asStateFlow()

    private val _keyColor = MutableStateFlow(themeRepo.keyColor)
    val keyColor: StateFlow<Int> = _keyColor.asStateFlow()

    private val _colorStyle = MutableStateFlow(themeRepo.colorStyle)
    val colorStyle: StateFlow<String> = _colorStyle.asStateFlow()

    private val _colorSpec = MutableStateFlow(themeRepo.colorSpec)
    val colorSpec: StateFlow<String> = _colorSpec.asStateFlow()

    private val _pageScale = MutableStateFlow(themeRepo.pageScale)
    val pageScale: StateFlow<Float> = _pageScale.asStateFlow()


    fun setColorMode(mode: ColorMode) {
        themeRepo.colorMode = mode.value
        _colorMode.value = mode.value
    }

    fun setKeyColor(color: Int) {
        themeRepo.keyColor = color
        _keyColor.value = color
    }

    fun setColorStyle(style: String) {
        themeRepo.colorStyle = style
        _colorStyle.value = style
    }

    fun setColorSpec(spec: String) {
        themeRepo.colorSpec = spec
        _colorSpec.value = spec
    }

    fun setPageScale(scale: Float) {
        themeRepo.pageScale = scale
        _pageScale.value = scale
    }


    fun setHapticEnabled(enabled: Boolean) {
        HapticFeedbackManager.setEnabled(context, enabled)
        _hapticEnabled.value = enabled
    }

    fun setPollInterval(intervalMs: Long) {
        prefs.edit { putLong(FloatMonitorService.KEY_POLL_INTERVAL, intervalMs) }
        _pollSettings.update { it.copy(intervalMs = intervalMs) }
        try {
            context.startService(FloatMonitorService.updatePollSettingsIntent(context))
        } catch (_: Exception) {}
    }

    /**
     * Switches privilege mode: stops daemon under old mode, restarts under new mode.
     */
    fun switchMode(
        oldMode: PrivilegeMode,
        newMode: PrivilegeMode,
        applyNewMode: () -> Unit,
        onComplete: (DaemonState) -> Unit,
    ) {
        viewModelScope.launch {
            _daemonStatus.update { it.copy(checking = true) }
            val result = withTimeoutOrNull(SWITCH_TIMEOUT_MS) {
                daemonManager.switchMode(oldMode, newMode, applyNewMode)
            } ?: DaemonState.FAILED
            val alive = result == DaemonState.RUNNING
            _daemonStatus.value = buildStatus(alive = alive)
            onComplete(result)
        }
    }

    private fun sendLogLevel(level: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try { daemonClient.sendCommand("log-level\n$level") } catch (_: Exception) {}
        }
    }

    data class DaemonInfo(
        val version: String? = null,
        val commit: String? = null,
        val runner: String? = null,
        val uptimeSeconds: Long? = null,
    )

    private fun fetchDaemonInfo(): DaemonInfo {
        val raw = daemonClient.sendCommand("ping")?.trim() ?: return DaemonInfo()
        return try {
            val json = JSONObject(raw)
            DaemonInfo(
                version = json.optString("version", "").ifEmpty { null },
                commit = json.optString("commit", "").ifEmpty { null },
                runner = json.optString("runner", "").ifEmpty { null },
                uptimeSeconds = json.optLong("uptime_s", -1).takeIf { it >= 0 },
            )
        } catch (_: Exception) { DaemonInfo() }
    }

    // ── Device Identity ──

    private val _fingerprint = MutableStateFlow<DeviceFingerprint?>(null)
    val fingerprint: StateFlow<DeviceFingerprint?> = _fingerprint.asStateFlow()

    fun getCachedIdentity(): DeviceIdentity? = identityRepository.getCachedIdentity()

    fun loadFingerprint() {
        if (_fingerprint.value != null) return
        viewModelScope.launch {
            _fingerprint.value = identityRepository.collectFingerprint()
        }
    }
}
