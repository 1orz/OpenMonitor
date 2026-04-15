package com.cloudorz.openmonitor.ui.user

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.common.PrivilegeMode
import com.cloudorz.openmonitor.core.data.repository.ActivationRepository
import com.cloudorz.openmonitor.core.data.repository.DeviceIdentityRepository
import com.cloudorz.openmonitor.core.model.identity.ActivationState
import com.cloudorz.openmonitor.core.model.identity.DeviceFingerprint
import com.cloudorz.openmonitor.core.model.identity.DeviceIdentity
import com.cloudorz.openmonitor.core.ui.HapticFeedbackManager
import com.cloudorz.openmonitor.core.data.ipc.DaemonClient
import com.cloudorz.openmonitor.core.data.ipc.MonitorLauncher
import com.cloudorz.openmonitor.core.ui.theme.ColorMode
import com.cloudorz.openmonitor.data.repository.ThemeSettingsRepository
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
import kotlinx.coroutines.withContext
import androidx.core.content.edit
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val daemonClient: DaemonClient,
    private val monitorLauncher: MonitorLauncher,
    private val themeRepo: ThemeSettingsRepository,
    private val identityRepository: DeviceIdentityRepository,
    private val activationRepository: ActivationRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    data class ServerStatus(
        val checking: Boolean = false,
        val connected: Boolean = false,
        val checkedOnce: Boolean = false,
    )

    companion object {
        const val KEY_DARK_MODE = "dark_mode"
        private const val SWITCH_TIMEOUT_MS = 15_000L
    }

    private val prefs = context.getSharedPreferences("monitor_settings", Context.MODE_PRIVATE)

    private val _serverStatus = MutableStateFlow(ServerStatus())
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    private val _darkMode = MutableStateFlow(prefs.getInt(KEY_DARK_MODE, 0))
    val darkMode: StateFlow<Int> = _darkMode.asStateFlow()

    private val _hapticEnabled = MutableStateFlow(prefs.getBoolean("haptic_enabled", true))
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    private var refreshJob: Job? = null
    private var adbWatcherJob: Job? = null
    private var screenVisible = false

    init {
        viewModelScope.launch {
            daemonClient.connected.collect { connected ->
                _serverStatus.update {
                    it.copy(connected = connected, checkedOnce = true)
                }
                if (connected && screenVisible) startRefreshLoop()
                if (!connected) stopRefreshLoop()
            }
        }
    }

    fun startObserving() {
        screenVisible = true
        if (daemonClient.connected.value) startRefreshLoop()
    }

    fun stopObserving() {
        screenVisible = false
        stopRefreshLoop()
    }

    private fun startRefreshLoop() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1000)
                _serverStatus.update { it.copy(connected = daemonClient.connected.value) }
            }
        }
    }

    private fun stopRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = null
    }

    fun startAdbWatcher() {
        if (adbWatcherJob?.isActive == true) return
        adbWatcherJob = viewModelScope.launch(Dispatchers.IO) {
            monitorLauncher.ensureRunning()
            while (isActive) {
                delay(3000)
                if (daemonClient.connected.value) break
                monitorLauncher.ensureRunning()
            }
        }
    }

    fun stopAdbWatcher() {
        adbWatcherJob?.cancel()
        adbWatcherJob = null
    }

    /** Runnable `adb shell ...` command the user pastes into their host terminal. */
    fun adbLaunchCommand(): String = "adb shell ${monitorLauncher.adbLaunchCommand()}"

    fun checkServer() {
        if (_serverStatus.value.checking) return
        viewModelScope.launch {
            _serverStatus.update { it.copy(checking = true) }
            monitorLauncher.ensureRunning()
            delay(1000)
            _serverStatus.update {
                it.copy(checking = false, connected = daemonClient.connected.value, checkedOnce = true)
            }
        }
    }

    fun restartServer() {
        if (_serverStatus.value.checking) return
        viewModelScope.launch {
            _serverStatus.update { it.copy(checking = true) }
            monitorLauncher.shutdown()
            delay(500)
            monitorLauncher.ensureRunning()
            delay(1500)
            _serverStatus.update {
                it.copy(checking = false, connected = daemonClient.connected.value, checkedOnce = true)
            }
        }
    }

    fun switchMode(
        oldMode: PrivilegeMode,
        newMode: PrivilegeMode,
        applyNewMode: () -> Unit,
        onComplete: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            _serverStatus.update { it.copy(checking = true) }
            monitorLauncher.shutdown()
            delay(500)
            applyNewMode()
            if (newMode != PrivilegeMode.BASIC) {
                withTimeoutOrNull(SWITCH_TIMEOUT_MS) {
                    monitorLauncher.ensureRunning()
                }
            }
            delay(500)
            val connected = daemonClient.connected.value
            _serverStatus.update { it.copy(checking = false, connected = connected, checkedOnce = true) }
            onComplete(connected || newMode == PrivilegeMode.BASIC)
        }
    }

    @Deprecated("Use setColorMode() instead")
    fun setDarkMode(mode: Int) {
        prefs.edit { putInt(KEY_DARK_MODE, mode) }
        _darkMode.value = mode
    }

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

    // ── Device Identity ──

    private val _fingerprint = MutableStateFlow<DeviceFingerprint?>(null)
    val fingerprint: StateFlow<DeviceFingerprint?> = _fingerprint.asStateFlow()

    fun getCachedIdentity(): DeviceIdentity? = identityRepository.getCachedIdentity()

    fun getCachedActivationState(): ActivationState? = activationRepository.getCachedState()

    fun loadFingerprint() {
        if (_fingerprint.value != null) return
        viewModelScope.launch {
            _fingerprint.value = identityRepository.collectFingerprint()
        }
    }
}
