package com.cloudorz.openmonitor.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import com.cloudorz.openmonitor.core.ui.theme.AppSettings
import com.cloudorz.openmonitor.core.ui.theme.ThemeSettingsReader
import com.cloudorz.openmonitor.core.ui.theme.UiMode
import com.cloudorz.openmonitor.data.repository.ThemeSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class ThemeUiState(
    val appSettings: AppSettings,
    val pageScale: Float,
    val enableBlur: Boolean,
    val enableFloatingBottomBar: Boolean,
    val uiMode: UiMode,
)

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val repo: ThemeSettingsRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val prefs = context.getSharedPreferences("monitor_settings", Context.MODE_PRIVATE)

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in OBSERVED_KEYS) {
            _uiState.value = readState()
        }
    }

    private val _uiState = MutableStateFlow(readState())
    val uiState: StateFlow<ThemeUiState> = _uiState.asStateFlow()

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        super.onCleared()
    }

    private fun readState(): ThemeUiState {
        return ThemeUiState(
            appSettings = ThemeSettingsReader.getAppSettings(prefs),
            pageScale = repo.pageScale,
            enableBlur = repo.enableBlur,
            enableFloatingBottomBar = repo.enableFloatingBottomBar,
            uiMode = UiMode.fromValue(repo.uiMode),
        )
    }

    companion object {
        private val OBSERVED_KEYS = setOf(
            "color_mode", "key_color", "color_style", "color_spec",
            "page_scale", "enable_blur", "enable_floating_bottom_bar",
            "ui_mode", "miuix_monet",
        )
    }
}
