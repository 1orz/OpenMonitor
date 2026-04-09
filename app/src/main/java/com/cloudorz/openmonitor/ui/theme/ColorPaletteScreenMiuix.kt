package com.cloudorz.openmonitor.ui.theme

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.cloudorz.openmonitor.R
import com.cloudorz.openmonitor.core.ui.theme.LocalEnableBlur
import com.cloudorz.openmonitor.core.ui.theme.keyColorOptions
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@OptIn(ExperimentalHazeApi::class)
@Composable
fun ColorPaletteScreenMiuix(
    state: ColorPaletteUiState,
    actions: ColorPaletteActions,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlurState = LocalEnableBlur.current
    val hazeState = remember { HazeState() }
    val hazeStyle = if (enableBlurState) {
        HazeStyle(
            backgroundColor = colorScheme.surface,
            tint = HazeTint(colorScheme.surface.copy(0.8f)),
        )
    } else {
        HazeStyle.Unspecified
    }
    val currentColorMode = state.colorMode
    val isDark = currentColorMode.isDark || (currentColorMode.isSystem && isSystemInDarkTheme())

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = if (enableBlurState) {
                    Modifier.hazeEffect(state = hazeState, style = hazeStyle) {
                        blurRadius = 20.dp
                        inputScale = HazeInputScale.Fixed(0.35f)
                        noiseFactor = 0f
                    }
                } else {
                    Modifier
                },
                color = if (enableBlurState) Color.Transparent else colorScheme.surface,
                title = stringResource(R.string.settings_theme),
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.padding(start = 16.dp),
                        onClick = actions.onBack,
                    ) {
                        val layoutDirection = LocalLayoutDirection.current
                        Icon(
                            modifier = Modifier.graphicsLayer {
                                if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                            },
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = colorScheme.onBackground,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        popupHost = { },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .let { if (enableBlurState) it.hazeSource(state = hazeState) else it }
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
            overscrollEffect = null,
        ) {
            item {
                Spacer(modifier = Modifier.height(12.dp))

                // Theme mode tabs
                val themeItems = listOf(
                    stringResource(R.string.settings_theme_mode_system),
                    stringResource(R.string.settings_theme_mode_light),
                    stringResource(R.string.settings_theme_mode_dark),
                )
                val colorModeVal = state.colorMode.value
                TabRow(
                    tabs = themeItems,
                    selectedTabIndex = (if (colorModeVal >= 3) colorModeVal - 3 else colorModeVal).coerceIn(0, 2),
                    onTabSelected = { index ->
                        val mode = if (state.miuixMonet) {
                            com.cloudorz.openmonitor.core.ui.theme.ColorMode.fromValue(index + 3)
                        } else {
                            com.cloudorz.openmonitor.core.ui.theme.ColorMode.fromValue(index)
                        }
                        actions.onSetColorMode(mode)
                    },
                    height = 48.dp,
                )

                // Monet toggle card
                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                ) {
                    SuperSwitch(
                        title = stringResource(R.string.settings_monet),
                        startAction = {
                            Icon(
                                Icons.Rounded.Wallpaper,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                            )
                        },
                        checked = state.miuixMonet,
                        onCheckedChange = { actions.onSetMiuixMonet(it) },
                    )

                    AnimatedVisibility(visible = state.miuixMonet) {
                        Column {
                            val colorItems = listOf(
                                stringResource(R.string.settings_key_color_default),
                                stringResource(R.string.color_red),
                                stringResource(R.string.color_pink),
                                stringResource(R.string.color_purple),
                                stringResource(R.string.color_deep_purple),
                                stringResource(R.string.color_indigo),
                                stringResource(R.string.color_blue),
                                stringResource(R.string.color_cyan),
                                stringResource(R.string.color_teal),
                                stringResource(R.string.color_green),
                                stringResource(R.string.color_yellow),
                                stringResource(R.string.color_amber),
                                stringResource(R.string.color_orange),
                                stringResource(R.string.color_brown),
                                stringResource(R.string.color_blue_grey),
                                stringResource(R.string.color_sakura),
                            )
                            val colorValues = listOf(0) + keyColorOptions
                            SuperDropdown(
                                title = stringResource(R.string.settings_key_color),
                                items = colorItems,
                                startAction = {
                                    Icon(
                                        Icons.Rounded.Colorize,
                                        modifier = Modifier.padding(end = 6.dp),
                                        contentDescription = null,
                                        tint = colorScheme.onBackground,
                                    )
                                },
                                selectedIndex = colorValues.indexOf(state.keyColor).takeIf { it >= 0 } ?: 0,
                                onSelectedIndexChange = { index -> actions.onSetKeyColor(colorValues[index]) },
                            )

                            AnimatedVisibility(visible = state.keyColor != 0) {
                                Column {
                                    val styles = PaletteStyle.entries
                                    SuperDropdown(
                                        title = stringResource(R.string.settings_color_style),
                                        startAction = {
                                            Icon(
                                                Icons.Rounded.Style,
                                                modifier = Modifier.padding(end = 6.dp),
                                                contentDescription = null,
                                                tint = colorScheme.onBackground,
                                            )
                                        },
                                        items = styles.map { it.name },
                                        selectedIndex = styles.indexOfFirst { it.name == state.paletteStyle.name }.coerceAtLeast(0),
                                        onSelectedIndexChange = { index -> actions.onSetColorStyle(styles[index].name) },
                                    )

                                    val specs = ColorSpec.SpecVersion.entries
                                    SuperDropdown(
                                        title = stringResource(R.string.settings_color_spec),
                                        startAction = {
                                            Icon(
                                                Icons.Rounded.DesignServices,
                                                modifier = Modifier.padding(end = 6.dp),
                                                contentDescription = null,
                                                tint = colorScheme.onBackground,
                                            )
                                        },
                                        items = specs.map { it.name },
                                        selectedIndex = specs.indexOfFirst { it.name == state.colorSpec.name }.coerceAtLeast(0),
                                        onSelectedIndexChange = { index -> actions.onSetColorSpec(specs[index].name) },
                                    )
                                }
                            }
                        }
                    }
                }

                // Blur & floating bar settings
                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        SuperSwitch(
                            title = stringResource(R.string.settings_enable_blur),
                            startAction = {
                                Icon(
                                    Icons.Rounded.BlurOn,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = null,
                                    tint = colorScheme.onBackground,
                                )
                            },
                            checked = state.enableBlur,
                            onCheckedChange = { actions.onSetEnableBlur(it) },
                        )
                    }
                    SuperSwitch(
                        title = stringResource(R.string.settings_floating_bottom_bar),
                        startAction = {
                            Icon(
                                Icons.Rounded.CallToAction,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                            )
                        },
                        checked = state.enableFloatingBottomBar,
                        onCheckedChange = { actions.onSetEnableFloatingBottomBar(it) },
                    )
                }

                // Page scale
                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                ) {
                    var sliderValue by remember(state.pageScale) { mutableFloatStateOf(state.pageScale) }
                    SuperArrow(
                        title = stringResource(R.string.settings_page_scale),
                        summary = "${(sliderValue * 100).toInt()}%",
                        startAction = {
                            Icon(
                                Icons.Rounded.AspectRatio,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = null,
                                tint = colorScheme.onBackground,
                            )
                        },
                        onClick = {},
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        valueRange = 0.85f..1.1f,
                        onValueChangeFinished = { actions.onSetPageScale(sliderValue) },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
