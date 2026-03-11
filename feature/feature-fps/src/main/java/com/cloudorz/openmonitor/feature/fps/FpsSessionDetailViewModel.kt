package com.cloudorz.openmonitor.feature.fps

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.data.CsvExporter
import com.cloudorz.openmonitor.core.data.repository.FpsRepository
import com.cloudorz.openmonitor.core.model.fps.FpsFrameRecord
import com.cloudorz.openmonitor.core.model.fps.FpsWatchSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class FpsSessionDetailState(
    val session: FpsWatchSession? = null,
    val records: List<FpsFrameRecord> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class FpsSessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val application: Application,
    private val fpsRepository: FpsRepository,
    private val csvExporter: CsvExporter,
) : ViewModel() {

    private val sessionId: Long = savedStateHandle.get<String>("sessionId")?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(FpsSessionDetailState())
    val state: StateFlow<FpsSessionDetailState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            fpsRepository.getSessionById(sessionId)
                .catch { e -> android.util.Log.w("FpsDetailVM", "session flow error", e) }
                .collect { session ->
                    _state.value = _state.value.copy(session = session)
                }
        }
        viewModelScope.launch {
            fpsRepository.getSessionFrames(sessionId)
                .catch { e -> android.util.Log.w("FpsDetailVM", "frames flow error", e) }
                .collect { records ->
                    _state.value = _state.value.copy(records = records, loading = false)
                }
        }
    }

    fun getExportIntent(onReady: (Intent) -> Unit) {
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
