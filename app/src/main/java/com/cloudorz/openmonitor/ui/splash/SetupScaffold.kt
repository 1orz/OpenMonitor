package com.cloudorz.openmonitor.ui.splash

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.cloudorz.openmonitor.R
import com.cloudorz.openmonitor.core.ui.hapticClick
import java.util.Locale

/** Language tags – shared with UserScreen's LanguageItem. */
private val LANGUAGE_TAGS = listOf("", "en", "zh-Hans", "zh-Hant", "ja")
private val LANGUAGE_NATIVE_LABELS = listOf("English", "简体中文", "繁體中文", "日本語")

/**
 * Shared scaffold for the setup flow (Agreement → Permissions → Activation → Mode).
 *
 * Provides a consistent TopAppBar with "OpenMonitor" title and language switcher,
 * plus the step indicator. Each phase renders its content inside [content].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScaffold(
    currentStep: Int,
    content: @Composable (modifier: Modifier) -> Unit,
) {
    val view = LocalView.current
    var languageMenuExpanded by remember { mutableStateOf(false) }

    val appLocales = AppCompatDelegate.getApplicationLocales()
    val selectedIndex = if (appLocales.isEmpty) {
        0 // System
    } else {
        val current = appLocales[0]!!
        LANGUAGE_TAGS.indexOfFirst { tag ->
            if (tag.isEmpty()) return@indexOfFirst false
            val target = Locale.forLanguageTag(tag)
            if (current.language != target.language) return@indexOfFirst false
            target.script.isEmpty() || current.script == target.script
        }.coerceAtLeast(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OpenMonitor") },
                actions = {
                    Box {
                        IconButton(onClick = {
                            view.hapticClick()
                            languageMenuExpanded = true
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Language,
                                contentDescription = "Language",
                            )
                        }
                        val languageLabels = listOf(
                            stringResource(R.string.settings_language_system),
                            *LANGUAGE_NATIVE_LABELS.toTypedArray(),
                        )
                        DropdownMenu(
                            expanded = languageMenuExpanded,
                            onDismissRequest = { languageMenuExpanded = false },
                        ) {
                            languageLabels.forEachIndexed { index, label ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        view.hapticClick()
                                        val locales = if (LANGUAGE_TAGS[index].isEmpty()) {
                                            LocaleListCompat.getEmptyLocaleList()
                                        } else {
                                            LocaleListCompat.forLanguageTags(LANGUAGE_TAGS[index])
                                        }
                                        AppCompatDelegate.setApplicationLocales(locales)
                                        languageMenuExpanded = false
                                    },
                                    trailingIcon = if (index == selectedIndex) {
                                        {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    } else null,
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SetupStepper(currentStep = currentStep, labels = setupStepperLabels())
            content(Modifier.weight(1f))
        }
    }
}
