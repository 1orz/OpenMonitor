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
import androidx.compose.foundation.lazy.items
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

data class LibraryInfo(
    val name: String,
    val version: String,
    val copyright: String,
    val description: String,
    val license: String,
    val licenseFile: String,
    val url: String,
)

val allLibraries = listOf(
    LibraryInfo(
        name = "Kotlin",
        version = "2.3.20",
        copyright = "Copyright 2000-2020 JetBrains s.r.o. and Kotlin Programming Language contributors",
        description = "The Kotlin programming language",
        license = "Apache-2.0",
        licenseFile = "Kotlin-LICENSE-Apache-2.0.txt",
        url = "https://github.com/JetBrains/kotlin",
    ),
    LibraryInfo(
        name = "Kotlin Coroutines",
        version = "1.10.2",
        copyright = "Copyright 2000-2020 JetBrains s.r.o. and Kotlin Programming Language contributors",
        description = "Kotlin coroutines for asynchronous programming",
        license = "Apache-2.0",
        licenseFile = "KotlinCoroutines-LICENSE-Apache-2.0.txt",
        url = "https://github.com/Kotlin/kotlinx.coroutines",
    ),
    LibraryInfo(
        name = "Jetpack Compose",
        version = "2026.03.01",
        copyright = "Copyright The Android Open Source Project",
        description = "Modern declarative UI toolkit (UI, Material 3, Icons, Foundation)",
        license = "Apache-2.0",
        licenseFile = "JetpackCompose-LICENSE-Apache-2.0.txt",
        url = "https://github.com/androidx/androidx",
    ),
    LibraryInfo(
        name = "AndroidX",
        version = "various",
        copyright = "Copyright The Android Open Source Project",
        description = "Core KTX, AppCompat, Activity, Navigation, Lifecycle, Room, WorkManager",
        license = "Apache-2.0",
        licenseFile = "AndroidX-LICENSE-Apache-2.0.txt",
        url = "https://github.com/androidx/androidx",
    ),
    LibraryInfo(
        name = "Dagger Hilt",
        version = "2.59.2",
        copyright = "Copyright 2012 The Dagger Authors",
        description = "Dependency injection framework for Android",
        license = "Apache-2.0",
        licenseFile = "DaggerHilt-LICENSE-Apache-2.0.txt",
        url = "https://github.com/google/dagger",
    ),
    LibraryInfo(
        name = "libsu",
        version = "6.0.0",
        copyright = "Copyright 2023 John Wu",
        description = "Android root shell library (KernelSU / Magisk / APatch)",
        license = "Apache-2.0",
        licenseFile = "libsu-LICENSE-Apache-2.0.txt",
        url = "https://github.com/topjohnwu/libsu",
    ),
    LibraryInfo(
        name = "Shizuku API",
        version = "13.1.5",
        copyright = "Copyright (c) 2021 RikkaW",
        description = "Use system APIs directly with ADB/root privileges",
        license = "MIT",
        licenseFile = "ShizukuAPI-LICENSE-MIT.txt",
        url = "https://github.com/RikkaApps/Shizuku-API",
    ),
    LibraryInfo(
        name = "Vico",
        version = "3.1.0",
        copyright = "Copyright 2022 by Patryk Goworowski and Patrick Michalik",
        description = "Chart library for Jetpack Compose and Material 3",
        license = "Apache-2.0",
        licenseFile = "Vico-LICENSE-Apache-2.0.txt",
        url = "https://github.com/patrykandpatrick/vico",
    ),
    LibraryInfo(
        name = "XLog",
        version = "1.11.1",
        copyright = "Copyright 2016 Elvis Hew",
        description = "Lightweight and extensible Android logger",
        license = "Apache-2.0",
        licenseFile = "XLog-LICENSE-Apache-2.0.txt",
        url = "https://github.com/elvishew/xLog",
    ),
    LibraryInfo(
        name = "Firebase Android SDK",
        version = "34.11.0",
        copyright = "Copyright The Android Open Source Project",
        description = "Google Analytics for Firebase",
        license = "Apache-2.0",
        licenseFile = "Firebase-LICENSE-Apache-2.0.txt",
        url = "https://github.com/firebase/firebase-android-sdk",
    ),
    LibraryInfo(
        name = "KeyAttestation",
        version = "1.8.4",
        copyright = "Copyright (c) 2021 vvb2060",
        description = "Android key attestation parsing and verification",
        license = "Apache-2.0",
        licenseFile = "KeyAttestation-LICENSE-Apache-2.0.txt",
        url = "https://github.com/vvb2060/KeyAttestation",
    ),
    LibraryInfo(
        name = "Bouncy Castle",
        version = "1.83",
        copyright = "Copyright (c) 2000-2024 The Legion of the Bouncy Castle Inc.",
        description = "Lightweight cryptography APIs and ASN.1 parsing",
        license = "Apache-2.0",
        licenseFile = "BouncyCastle-LICENSE-Apache-2.0.txt",
        url = "https://github.com/bcgit/bc-java",
    ),
    LibraryInfo(
        name = "Guava",
        version = "33.5.0",
        copyright = "Copyright (C) 2010 The Guava Authors",
        description = "Google core libraries for Java (Android variant)",
        license = "Apache-2.0",
        licenseFile = "Guava-LICENSE-Apache-2.0.txt",
        url = "https://github.com/google/guava",
    ),
    LibraryInfo(
        name = "CBOR",
        version = "0.9",
        copyright = "Copyright (c) 2014-2024 Constantin Rack",
        description = "Concise Binary Object Representation (RFC 7049) for Java",
        license = "Apache-2.0",
        licenseFile = "CBOR-LICENSE-Apache-2.0.txt",
        url = "https://github.com/c-rack/cbor-java",
    ),
    LibraryInfo(
        name = "cpuinfo",
        version = "main",
        copyright = "Copyright (c) 2019 Google LLC, 2017-2018 Facebook Inc., 2012-2017 Georgia Institute of Technology, 2010-2012 Marat Dukhan",
        description = "CPU information library (L1/L2/L3 cache, NEON detection)",
        license = "BSD-2-Clause",
        licenseFile = "cpuinfo-LICENSE-BSD-2-Clause.txt",
        url = "https://github.com/pytorch/cpuinfo",
    ),
).sortedBy { it.name.lowercase() }

// ── License list screen ─────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpenSourceLicensesScreen(
    onLicenseClick: (index: Int) -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val licenseCounts = allLibraries.groupBy { it.license }.mapValues { it.value.size }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.licenses_summary, allLibraries.size),
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

        items(allLibraries.size) { index ->
            val lib = allLibraries[index]
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
        // Left: name + version + tag + description
        Column(modifier = Modifier.weight(1f)) {
            // Row 1: name  version  [license tag]
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
            // Row 2: description
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = lib.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Right: Link icon + >
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

fun readLicenseAsset(context: Context, fileName: String): String =
    try {
        context.assets.open("licenses/$fileName").bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        "License file not found: $fileName"
    }

@Composable
fun LicenseDetailScreen(libraryIndex: Int) {
    val lib = allLibraries.getOrNull(libraryIndex) ?: return
    val context = LocalContext.current
    val view = LocalView.current
    val licenseText = remember(lib.licenseFile) { readLicenseAsset(context, lib.licenseFile) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Header card
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
                                "MIT" -> MaterialTheme.colorScheme.tertiaryContainer
                                "BSD-2-Clause" -> MaterialTheme.colorScheme.tertiaryContainer
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

        // Full license text
        Text(
            text = licenseText,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}
