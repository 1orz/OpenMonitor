package com.cloudorz.openmonitor.ui.splash

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cloudorz.openmonitor.R
import com.cloudorz.openmonitor.core.ui.hapticClick
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography

const val CURRENT_AGREEMENT_VERSION = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgreementScreen(onAccepted: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val markdownContent = remember {
        val locale = AppCompatDelegate.getApplicationLocales().let {
            if (!it.isEmpty) it[0]!! else context.resources.configuration.locales[0]
        }
        val resId = when {
            locale.language == "zh" && locale.toLanguageTag().contains("TW", ignoreCase = true) -> R.raw.agreement_zh_tw
            locale.language == "zh" -> R.raw.agreement_zh_cn
            locale.language == "ja" -> R.raw.agreement_ja
            else -> R.raw.agreement_en
        }
        try {
            context.resources.openRawResource(resId).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            ""
        }
    }

    val scrollState = rememberScrollState()
    val hasReachedBottom by remember {
        derivedStateOf {
            val max = scrollState.maxValue
            when {
                max == Int.MAX_VALUE -> false
                max == 0 -> true
                else -> scrollState.value >= max - 100
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.agreement_title)) },
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Markdown(
                    content = markdownContent,
                    typography = markdownTypography(
                        h1 = MaterialTheme.typography.headlineSmall,
                        h2 = MaterialTheme.typography.titleLarge,
                        h3 = MaterialTheme.typography.titleMedium,
                        h4 = MaterialTheme.typography.titleSmall,
                        h5 = MaterialTheme.typography.bodyLarge,
                        h6 = MaterialTheme.typography.bodyMedium,
                    ),
                )
            }

            Surface(
                tonalElevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!hasReachedBottom) {
                        Text(
                            text = stringResource(R.string.agreement_scroll_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Button(
                        onClick = {
                            view.hapticClick()
                            context.getSharedPreferences("monitor_settings", Context.MODE_PRIVATE)
                                .edit()
                                .putInt("agreement_accepted_version", CURRENT_AGREEMENT_VERSION)
                                .apply()
                            onAccepted()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = hasReachedBottom,
                    ) {
                        Text(
                            text = stringResource(R.string.agreement_accept),
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
