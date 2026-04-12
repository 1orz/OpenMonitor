package com.cloudorz.openmonitor.feature.fps

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.data.CsvExporter
import com.cloudorz.openmonitor.core.data.repository.FpsRepository
import com.cloudorz.openmonitor.core.data.datasource.FpsRecordingManager
import com.cloudorz.openmonitor.core.data.datasource.FpsRecordingState
import com.cloudorz.openmonitor.core.data.datasource.FpsRecordingInfo
import com.cloudorz.openmonitor.core.model.fps.FpsWatchSession
import com.cloudorz.openmonitor.service.FloatMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class FpsUiState(
    val sessions: List<FpsWatchSession> = emptyList(),
    val recordingState: FpsRecordingState = FpsRecordingState.IDLE,
    val recordingInfo: FpsRecordingInfo = FpsRecordingInfo(),
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val isFpsFloatEnabled: Boolean = false,
)

@HiltViewModel
class FpsViewModel @Inject constructor(
    private val application: Application,
    private val fpsRepository: FpsRepository,
    private val fpsRecordingManager: FpsRecordingManager,
    private val csvExporter: CsvExporter,
) : ViewModel() {

    companion object {
        private const val PREFS_NAME = "monitor_settings"
        private const val KEY_ENABLED_MONITORS = "enabled_monitors"
        private const val FPS_RECORDER_TYPE = "FPS_RECORDER"
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _selectionMode = MutableStateFlow(false)
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _fpsFloatEnabled = MutableStateFlow(isFpsMonitorEnabled())

    private val _toastEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val toastEvent: SharedFlow<Int> = _toastEvent.asSharedFlow()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_ENABLED_MONITORS) {
            _fpsFloatEnabled.value = isFpsMonitorEnabled()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onCleared()
    }

    private val _localState = combine(_selectionMode, _selectedIds, _fpsFloatEnabled) { sel, ids, fps ->
        Triple(sel, ids, fps)
    }

    val uiState: StateFlow<FpsUiState> = combine(
        fpsRepository.getAllSessions().catch { e ->
            android.util.Log.w("FpsVM", "sessions flow error", e)
        },
        fpsRecordingManager.state,
        fpsRecordingManager.info,
        _localState,
    ) { sessions, recState, recInfo, (selMode, selIds, fpsFloat) ->
        FpsUiState(
            sessions = sessions,
            recordingState = recState,
            recordingInfo = recInfo,
            isSelectionMode = selMode,
            selectedIds = selIds,
            isFpsFloatEnabled = fpsFloat,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FpsUiState())

    fun toggleFpsFloat(enabled: Boolean) {
        if (enabled && !Settings.canDrawOverlays(application)) {
            _toastEvent.tryEmit(com.cloudorz.openmonitor.core.ui.R.string.permission_required_toast)
            return
        }

        val context = application.applicationContext
        if (enabled) {
            context.startForegroundService(FloatMonitorService.startIntent(context))
            context.startService(FloatMonitorService.addMonitorIntent(context, FPS_RECORDER_TYPE))
        } else {
            context.startService(FloatMonitorService.removeMonitorIntent(context, FPS_RECORDER_TYPE))
        }

        val currentSet = prefs.getStringSet(KEY_ENABLED_MONITORS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (enabled) currentSet.add(FPS_RECORDER_TYPE) else currentSet.remove(FPS_RECORDER_TYPE)
        prefs.edit { putStringSet(KEY_ENABLED_MONITORS, currentSet) }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            fpsRepository.deleteSession(sessionId)
        }
    }

    fun renameSession(sessionId: Long, desc: String) {
        viewModelScope.launch {
            fpsRepository.renameSession(sessionId, desc)
        }
    }

    fun toggleSelectionMode() {
        val newMode = !_selectionMode.value
        _selectionMode.value = newMode
        if (!newMode) _selectedIds.value = emptySet()
    }

    fun exitSelectionMode() {
        _selectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun toggleSelection(sessionId: Long) {
        _selectedIds.value = _selectedIds.value.let { ids ->
            if (ids.contains(sessionId)) ids - sessionId else ids + sessionId
        }
    }

    fun selectAll(sessions: List<FpsWatchSession>) {
        _selectedIds.value = sessions.mapNotNull { it.sessionId.toLongOrNull() }.toSet()
    }

    fun deleteSelected() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            fpsRepository.deleteSessionsByIds(ids)
            _selectedIds.value = emptySet()
            _selectionMode.value = false
        }
    }

    fun getExportIntent(sessionId: Long, onReady: (Intent) -> Unit) {
        viewModelScope.launch {
            val context = application.applicationContext
            val exportDir = File(context.cacheDir, "export").apply { mkdirs() }
            val file = File(exportDir, "fps_session_${sessionId}.csv")
            file.outputStream().use { csvExporter.exportFpsSession(sessionId, it) }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onReady(Intent.createChooser(intent, null))
        }
    }

    private fun isFpsMonitorEnabled(): Boolean {
        val saved = prefs.getStringSet(KEY_ENABLED_MONITORS, emptySet()) ?: emptySet()
        return FPS_RECORDER_TYPE in saved
    }
}
