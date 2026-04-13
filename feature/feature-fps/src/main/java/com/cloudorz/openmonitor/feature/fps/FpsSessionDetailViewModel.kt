package com.cloudorz.openmonitor.feature.fps

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudorz.openmonitor.core.data.CsvExporter
import com.cloudorz.openmonitor.core.data.datasource.DeviceNameSource
import com.cloudorz.openmonitor.core.data.datasource.SocDatabase
import com.cloudorz.openmonitor.core.data.repository.FpsRepository
import com.cloudorz.openmonitor.core.model.fps.FpsFrameRecord
import com.cloudorz.openmonitor.core.model.fps.FpsWatchSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionDeviceInfo(
    val cpuName: String,
    val deviceName: String,
    val osVersion: String,
)

data class FpsSessionDetailState(
    val session: FpsWatchSession? = null,
    val records: List<FpsFrameRecord> = emptyList(),
    val loading: Boolean = true,
    val deviceInfo: SessionDeviceInfo? = null,
)

@HiltViewModel
class FpsSessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val application: Application,
    private val fpsRepository: FpsRepository,
    private val csvExporter: CsvExporter,
    private val socDatabase: SocDatabase,
    private val deviceNameSource: DeviceNameSource,
) : ViewModel() {

    private var sessionId: Long = savedStateHandle.get<String>("sessionId")?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(FpsSessionDetailState())
    val state: StateFlow<FpsSessionDetailState> = _state.asStateFlow()

    private var initialized = false

    init {
        _state.value = _state.value.copy(deviceInfo = buildDeviceInfo())
        if (sessionId > 0L) loadSession()
    }

    private fun buildDeviceInfo(): SessionDeviceInfo {
        val soc = socDatabase.getSocInfo()
        val cpuName = soc.name.ifBlank { Build.HARDWARE }
        val deviceName = deviceNameSource.getDeviceName()
            ?: "${Build.BRAND} ${Build.MODEL}".trim()
        val osVersion = "Android ${Build.VERSION.RELEASE}"
        return SessionDeviceInfo(cpuName = cpuName, deviceName = deviceName, osVersion = osVersion)
    }

    /** Called from composable when sessionId comes from Navigation3 NavKey. */
    fun initSessionId(id: String) {
        val parsed = id.toLongOrNull() ?: return
        if (parsed == sessionId && initialized) return
        sessionId = parsed
        loadSession()
    }

    private fun loadSession() {
        initialized = true
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

    fun getCsvFileName(): String = "fps_session_${sessionId}.csv"

    fun getImageFileName(): String = "fps_session_${sessionId}.png"

    fun writeCsvToUri(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val msg = kotlinx.coroutines.withContext(Dispatchers.IO) {
                try {
                    val resolver = application.contentResolver
                    resolver.openOutputStream(uri)?.use { csvExporter.exportFpsSession(sessionId, it) }
                    application.getString(com.cloudorz.openmonitor.core.ui.R.string.fps_saved_to_downloads, getCsvFileName())
                } catch (e: Exception) {
                    android.util.Log.w("FpsDetailVM", "CSV save failed", e)
                    application.getString(com.cloudorz.openmonitor.core.ui.R.string.fps_save_failed)
                }
            }
            onResult(msg)
        }
    }

fun writeImageToUri(uri: Uri, bitmap: Bitmap, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val msg = kotlinx.coroutines.withContext(Dispatchers.IO) {
                try {
                    val resolver = application.contentResolver
                    resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                    application.getString(com.cloudorz.openmonitor.core.ui.R.string.fps_saved_to_downloads, getImageFileName())
                } catch (e: Exception) {
                    android.util.Log.w("FpsDetailVM", "Screenshot save failed", e)
                    bitmap.recycle()
                    application.getString(com.cloudorz.openmonitor.core.ui.R.string.fps_save_failed)
                }
            }
            onResult(msg)
        }
    }
}
