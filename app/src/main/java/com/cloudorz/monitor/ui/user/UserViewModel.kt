package com.cloudorz.monitor.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.monitor.core.data.datasource.DaemonClient
import com.cloudorz.monitor.core.data.datasource.DaemonManager
import com.cloudorz.monitor.core.data.datasource.DaemonState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val daemonClient: DaemonClient,
    private val daemonManager: DaemonManager,
) : ViewModel() {

    data class DaemonStatus(
        val checking: Boolean = false,
        val connected: Boolean = false,
        val version: String? = null,
        val checkedOnce: Boolean = false,
    )

    private val _daemonStatus = MutableStateFlow(DaemonStatus())
    val daemonStatus: StateFlow<DaemonStatus> = _daemonStatus.asStateFlow()

    init {
        // Sync with DaemonManager state
        viewModelScope.launch {
            daemonManager.state.collect { state ->
                if (state == DaemonState.RUNNING) {
                    val version = withContext(Dispatchers.IO) {
                        daemonClient.sendCommand("daemon-version")?.trim()
                    }
                    _daemonStatus.value = DaemonStatus(
                        connected = true, version = version, checkedOnce = true,
                    )
                } else if (state == DaemonState.FAILED || state == DaemonState.NOT_NEEDED) {
                    _daemonStatus.value = DaemonStatus(
                        connected = false, checkedOnce = true,
                    )
                }
            }
        }
    }

    fun checkDaemon() {
        if (_daemonStatus.value.checking) return
        viewModelScope.launch {
            _daemonStatus.value = _daemonStatus.value.copy(checking = true)
            val result = daemonManager.ensureRunning()
            val alive = result == DaemonState.RUNNING
            val version = if (alive) {
                withContext(Dispatchers.IO) { daemonClient.sendCommand("daemon-version")?.trim() }
            } else null
            _daemonStatus.value = DaemonStatus(
                checking = false,
                connected = alive,
                version = version,
                checkedOnce = true,
            )
        }
    }

    fun restartDaemon() {
        if (_daemonStatus.value.checking) return
        viewModelScope.launch {
            _daemonStatus.value = _daemonStatus.value.copy(checking = true)
            val result = daemonManager.restart()
            val alive = result == DaemonState.RUNNING
            val version = if (alive) {
                withContext(Dispatchers.IO) { daemonClient.sendCommand("daemon-version")?.trim() }
            } else null
            _daemonStatus.value = DaemonStatus(
                checking = false,
                connected = alive,
                version = version,
                checkedOnce = true,
            )
        }
    }
}
