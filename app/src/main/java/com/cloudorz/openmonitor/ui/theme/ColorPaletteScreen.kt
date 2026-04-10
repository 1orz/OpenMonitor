package com.cloudorz.openmonitor.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudorz.openmonitor.core.ui.theme.ColorMode
import com.cloudorz.openmonitor.ui.user.UserViewModel
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

@Immutable
data class ColorPaletteUiState(
    val colorMode: ColorMode,
    val keyColor: Int,
    val paletteStyle: PaletteStyle,
    val colorSpec: ColorSpec.SpecVersion,
    val pageScale: Float,
)

@Immutable
data class ColorPaletteActions(
    val onBack: () -> Unit,
    val onSetColorMode: (ColorMode) -> Unit,
    val onSetKeyColor: (Int) -> Unit,
    val onSetColorStyle: (String) -> Unit,
    val onSetColorSpec: (String) -> Unit,
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
        pageScale = pageScaleVal,
    )

    val actions = ColorPaletteActions(
        onBack = onBack,
        onSetColorMode = viewModel::setColorMode,
        onSetKeyColor = viewModel::setKeyColor,
        onSetColorStyle = viewModel::setColorStyle,
        onSetColorSpec = viewModel::setColorSpec,
        onSetPageScale = viewModel::setPageScale,
    )

    ColorPaletteScreenMaterial(state, actions)
}
