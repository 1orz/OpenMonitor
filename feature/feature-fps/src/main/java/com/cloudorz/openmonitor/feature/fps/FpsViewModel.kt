package com.cloudorz.openmonitor.feature.fps

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.data.CsvExporter
import com.cloudorz.openmonitor.core.data.repository.FpsRepository
import com.cloudorz.openmonitor.core.data.datasource.FpsRecordingManager
import com.cloudorz.openmonitor.core.data.datasource.FpsRecordingState
import com.cloudorz.openmonitor.core.data.datasource.FpsRecordingInfo
import com.cloudorz.openmonitor.core.model.fps.FpsWatchSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
)

@HiltViewModel
class FpsViewModel @Inject constructor(
    private val application: Application,
    private val fpsRepository: FpsRepository,
    private val fpsRecordingManager: FpsRecordingManager,
    private val csvExporter: CsvExporter,
) : ViewModel() {

    private val _selectionMode = MutableStateFlow(false)
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    val uiState: StateFlow<FpsUiState> = combine(
        fpsRepository.getAllSessions().catch { e ->
            android.util.Log.w("FpsVM", "sessions flow error", e)
        },
        fpsRecordingManager.state,
        fpsRecordingManager.info,
        _selectionMode,
        _selectedIds,
    ) { sessions, recState, recInfo, selMode, selIds ->
        FpsUiState(
            sessions = sessions,
            recordingState = recState,
            recordingInfo = recInfo,
            isSelectionMode = selMode,
            selectedIds = selIds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FpsUiState())

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
}
