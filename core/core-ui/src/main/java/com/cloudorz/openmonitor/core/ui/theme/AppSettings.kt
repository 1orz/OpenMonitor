package com.cloudorz.openmonitor.core.ui.theme

import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

data class AppSettings(
    val colorMode: ColorMode,
    val keyColor: Int,
    val paletteStyle: PaletteStyle,
    val colorSpec: ColorSpec.SpecVersion,
)
