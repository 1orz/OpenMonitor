package com.cloudorz.openmonitor.ui.user

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cloudorz.openmonitor.R
import com.cloudorz.openmonitor.core.ui.hapticClick
import com.cloudorz.openmonitor.core.ui.hapticClickable
import org.json.JSONArray
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.CircularProgressIndicator

data class LibraryInfo(
    val name: String,
    val version: String,
    val copyright: String,
    val description: String,
    val license: String,
    val licenseUrl: String,
    val url: String,
)

fun loadLibraries(context: Context): List<LibraryInfo> {
    val json = context.assets.open("licenses/libraries.json").bufferedReader().use { it.readText() }
    val array = JSONArray(json)
    return (0 until array.length()).map { i ->
        val obj = array.getJSONObject(i)
        LibraryInfo(
            name = obj.getString("name"),
            version = obj.getString("version"),
            copyright = obj.getString("copyright"),
            description = obj.getString("description"),
            license = obj.getString("license"),
            licenseUrl = obj.getString("licenseUrl"),
            url = obj.getString("url"),
        )
    }
}

val allLibraries: List<LibraryInfo>
    @Composable get() {
        val context = LocalContext.current
        return remember { loadLibraries(context) }
    }

// ── License list screen ─────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpenSourceLicensesScreen(
    onLicenseClick: (index: Int) -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val libraries = allLibraries
    val licenseCounts = libraries.groupBy { it.license }.mapValues { it.value.size }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.licenses_summary, libraries.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                licenseCounts.forEach { (license, count) ->
                    SuggestionChip(
                        onClick = { view.hapticClick() },
                        label = { Text("$license ($count)") },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(libraries.size) { index ->
            val lib = libraries[index]
            if (index > 0) HorizontalDivider()
            LibraryRow(
                lib = lib,
                onLinkClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, lib.url.toUri()))
                    } catch (_: Exception) {}
                },
                onDetailClick = { onLicenseClick(index) },
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun LibraryRow(
    lib: LibraryInfo,
    onLinkClick: () -> Unit,
    onDetailClick: () -> Unit,
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hapticClickable(onClick = onDetailClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = lib.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = lib.version,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = lib.license,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (lib.license) {
                        "MIT", "BSD-2-Clause" -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    modifier = Modifier
                        .background(
                            color = when (lib.license) {
                                "MIT", "BSD-2-Clause" -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.secondaryContainer
                            },
                            shape = MaterialTheme.shapes.extraSmall,
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = lib.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(
            onClick = { view.hapticClick(); onLinkClick() },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── License detail screen ───────────────────────────────────────────────────

@Composable
fun LicenseDetailScreen(libraryIndex: Int) {
    val libraries = allLibraries
    val lib = libraries.getOrNull(libraryIndex) ?: return
    val context = LocalContext.current
    val view = LocalView.current
    var licenseText by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf(false) }

    LaunchedEffect(lib.licenseUrl) {
        licenseText = withContext(Dispatchers.IO) {
            try {
                URL(lib.licenseUrl).readText()
            } catch (_: Exception) {
                loadError = true
                null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = lib.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = lib.version,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = lib.copyright,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SuggestionChip(
                        onClick = { view.hapticClick() },
                        label = { Text(lib.license) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = when (lib.license) {
                                "MIT", "BSD-2-Clause" -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.secondaryContainer
                            },
                        ),
                    )
                    IconButton(
                        onClick = {
                            view.hapticClick()
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, lib.url.toUri()))
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            licenseText != null -> Text(
                text = licenseText!!,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            loadError -> Text(
                text = "Failed to load license. Visit ${lib.licenseUrl}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            else -> CircularProgressIndicator(
                modifier = Modifier.padding(32.dp).align(Alignment.CenterHorizontally),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
