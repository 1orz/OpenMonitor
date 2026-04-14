package com.cloudorz.openmonitor.ui.splash

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cloudorz.openmonitor.R
import com.cloudorz.openmonitor.core.ui.hapticClick
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val AGREEMENT_API_URL = "https://om-api.cloudorz.com/api/v1/agreement"
private const val LOCAL_AGREEMENT_VERSION = 1
private const val FETCH_TIMEOUT_MS = 3_000L
private const val COOLDOWN_SECONDS = 10

private const val PREFS_NAME = "monitor_settings"
private const val KEY_ACCEPTED_VERSION = "agreement_accepted_version"
private const val KEY_PENDING_VERSION = "agreement_pending_version"

data class AgreementData(
    val version: Int,
    val content: String,
)

// ── Locale → agreement resource mapping ──────────────────────────────────────

/**
 * Derives the agreement locale key from the current app locale
 * (set by AppCompatDelegate, same as Settings > Language).
 */
internal fun resolveAgreementLocaleKey(context: Context): String {
    val locale = AppCompatDelegate.getApplicationLocales().let {
        if (!it.isEmpty) it[0]!! else context.resources.configuration.locales[0]
    }
    return when {
        locale.language == "zh" && locale.toLanguageTag().contains("TW", ignoreCase = true) -> "zh_tw"
        locale.language == "zh" && locale.toLanguageTag().contains("Hant", ignoreCase = true) -> "zh_tw"
        locale.language == "zh" -> "zh_cn"
        locale.language == "ja" -> "ja"
        else -> "en"
    }
}

private fun rawResIdForLocaleKey(key: String): Int = when (key) {
    "zh_cn" -> R.raw.agreement_zh_cn
    "zh_tw" -> R.raw.agreement_zh_tw
    "ja" -> R.raw.agreement_ja
    else -> R.raw.agreement_en
}

private fun loadLocalAgreement(context: Context, localeKey: String): AgreementData {
    val content = try {
        context.resources.openRawResource(rawResIdForLocaleKey(localeKey))
            .bufferedReader().use { it.readText() }
    } catch (_: Exception) { "" }
    return AgreementData(LOCAL_AGREEMENT_VERSION, content)
}

private suspend fun fetchRemoteAgreement(localeKey: String): AgreementData? {
    return withTimeoutOrNull(FETCH_TIMEOUT_MS) {
        withContext(Dispatchers.IO) {
            try {
                val conn = URL("$AGREEMENT_API_URL?lang=$localeKey").openConnection() as HttpURLConnection
                conn.connectTimeout = FETCH_TIMEOUT_MS.toInt()
                conn.readTimeout = FETCH_TIMEOUT_MS.toInt()
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val data = JSONObject(json).getJSONObject("data")
                AgreementData(data.getInt("version"), data.getString("content"))
            } catch (_: Exception) { null }
        }
    }
}

// ── Public helpers used by MainActivity ──────────────────────────────────────

suspend fun checkAgreementNeeded(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val accepted = prefs.getInt(KEY_ACCEPTED_VERSION, 0)
    if (accepted == 0) return true
    val pending = prefs.getInt(KEY_PENDING_VERSION, 0)
    return pending > accepted
}

suspend fun backgroundAgreementCheck(context: Context) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val accepted = prefs.getInt(KEY_ACCEPTED_VERSION, 0)
    val localeKey = resolveAgreementLocaleKey(context)
    val remote = fetchRemoteAgreement(localeKey) ?: return
    if (remote.version > accepted) {
        prefs.edit().putInt(KEY_PENDING_VERSION, remote.version).apply()
    }
}

// ── Agreement content (no Scaffold — hosted inside SetupScaffold) ────────────

@Composable
fun AgreementScreen(onAccepted: () -> Unit, onDeclined: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current

    val localeKey = remember { resolveAgreementLocaleKey(context) }
    var agreementData by remember { mutableStateOf<AgreementData?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(localeKey) {
        isLoading = true
        val remote = fetchRemoteAgreement(localeKey)
        agreementData = remote ?: loadLocalAgreement(context, localeKey)
        isLoading = false
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

    var cooldownRemaining by remember { mutableIntStateOf(COOLDOWN_SECONDS) }
    LaunchedEffect(Unit) {
        while (cooldownRemaining > 0) {
            delay(1_000)
            cooldownRemaining--
        }
    }

    val canAccept = hasReachedBottom && cooldownRemaining <= 0 && !isLoading

    Column(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Markdown(
                    content = agreementData?.content ?: "",
                    colors = markdownColor(
                        linkText = MaterialTheme.colorScheme.primary,
                    ),
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
        }

        Surface(tonalElevation = 3.dp) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            view.hapticClick()
                            onDeclined()
                        },
                        modifier = Modifier.weight(0.35f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.agreement_decline),
                            modifier = Modifier.padding(vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Button(
                        onClick = {
                            view.hapticClick()
                            val data = agreementData ?: return@Button
                            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                .edit()
                                .putInt(KEY_ACCEPTED_VERSION, data.version)
                                .remove(KEY_PENDING_VERSION)
                                .apply()
                            onAccepted()
                        },
                        modifier = Modifier.weight(0.65f),
                        enabled = canAccept,
                    ) {
                        Text(
                            text = if (cooldownRemaining > 0) {
                                stringResource(R.string.agreement_accept_countdown, cooldownRemaining)
                            } else {
                                stringResource(R.string.agreement_accept)
                            },
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
