package com.cloudorz.openmonitor.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudorz.openmonitor.core.ui.theme.ColorMode
import com.cloudorz.openmonitor.core.ui.theme.LocalUiMode
import com.cloudorz.openmonitor.core.ui.theme.UiMode
import com.cloudorz.openmonitor.ui.user.UserViewModel
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

@Immutable
data class ColorPaletteUiState(
    val colorMode: ColorMode,
    val keyColor: Int,
    val paletteStyle: PaletteStyle,
    val colorSpec: ColorSpec.SpecVersion,
    val uiMode: String,
    val miuixMonet: Boolean,
    val enableBlur: Boolean,
    val enableFloatingBottomBar: Boolean,
    val pageScale: Float,
)

@Immutable
data class ColorPaletteActions(
    val onBack: () -> Unit,
    val onSetColorMode: (ColorMode) -> Unit,
    val onSetKeyColor: (Int) -> Unit,
    val onSetColorStyle: (String) -> Unit,
    val onSetColorSpec: (String) -> Unit,
    val onSetMiuixMonet: (Boolean) -> Unit,
    val onSetEnableBlur: (Boolean) -> Unit,
    val onSetEnableFloatingBottomBar: (Boolean) -> Unit,
    val onSetPageScale: (Float) -> Unit,
)

@Composable
fun ColorPaletteScreen(
    onBack: () -> Unit,
    viewModel: UserViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(),
) {
    val colorModeVal by viewModel.colorMode.collectAsStateWithLifecycle()
    val keyColorVal by viewModel.keyColor.collectAsStateWithLifecycle()
    val colorStyleVal by viewModel.colorStyle.collectAsStateWithLifecycle()
    val colorSpecVal by viewModel.colorSpec.collectAsStateWithLifecycle()
    val uiModeVal by viewModel.uiMode.collectAsStateWithLifecycle()
    val miuixMonetVal by viewModel.miuixMonet.collectAsStateWithLifecycle()
    val enableBlurVal by viewModel.enableBlur.collectAsStateWithLifecycle()
    val enableFloatingBottomBarVal by viewModel.enableFloatingBottomBar.collectAsStateWithLifecycle()
    val pageScaleVal by viewModel.pageScale.collectAsStateWithLifecycle()

    val paletteStyle = try {
        PaletteStyle.valueOf(colorStyleVal)
    } catch (_: Exception) {
        PaletteStyle.TonalSpot
    }
    val colorSpec = try {
        ColorSpec.SpecVersion.valueOf(colorSpecVal)
    } catch (_: Exception) {
        ColorSpec.SpecVersion.Default
    }

    val state = ColorPaletteUiState(
        colorMode = ColorMode.fromValue(colorModeVal),
        keyColor = keyColorVal,
        paletteStyle = paletteStyle,
        colorSpec = colorSpec,
        uiMode = uiModeVal,
        miuixMonet = miuixMonetVal,
        enableBlur = enableBlurVal,
        enableFloatingBottomBar = enableFloatingBottomBarVal,
        pageScale = pageScaleVal,
    )

    val actions = ColorPaletteActions(
        onBack = onBack,
        onSetColorMode = viewModel::setColorMode,
        onSetKeyColor = viewModel::setKeyColor,
        onSetColorStyle = viewModel::setColorStyle,
        onSetColorSpec = viewModel::setColorSpec,
        onSetMiuixMonet = viewModel::setMiuixMonet,
        onSetEnableBlur = viewModel::setEnableBlur,
        onSetEnableFloatingBottomBar = viewModel::setEnableFloatingBottomBar,
        onSetPageScale = viewModel::setPageScale,
    )

    when (LocalUiMode.current) {
        UiMode.Material -> ColorPaletteScreenMaterial(state, actions)
        UiMode.Miuix -> ColorPaletteScreenMiuix(state, actions)
    }
}
