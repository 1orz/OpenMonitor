package com.cloudorz.openmonitor.ui.user

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cloudorz.openmonitor.R

private data class LibraryInfo(
    val name: String,
    val version: String,
    val description: String,
    val license: String,
    val url: String,
)

private val libraries = listOf(
    // Kotlin & Coroutines
    LibraryInfo(
        name = "Kotlin",
        version = "2.3.20",
        description = "The Kotlin programming language",
        license = "Apache-2.0",
        url = "https://github.com/JetBrains/kotlin",
    ),
    LibraryInfo(
        name = "Kotlin Coroutines",
        version = "1.10.2",
        description = "Kotlin coroutines for asynchronous programming",
        license = "Apache-2.0",
        url = "https://github.com/Kotlin/kotlinx.coroutines",
    ),

    // Jetpack Compose
    LibraryInfo(
        name = "Jetpack Compose BOM",
        version = "2026.03.01",
        description = "Modern declarative UI toolkit for Android",
        license = "Apache-2.0",
        url = "https://github.com/androidx/androidx",
    ),
    LibraryInfo(
        name = "Compose Material 3",
        version = "BOM",
        description = "Material Design 3 components for Compose",
        license = "Apache-2.0",
        url = "https://github.com/androidx/androidx",
    ),
    LibraryInfo(
        name = "Compose Material Icons Extended",
        version = "BOM",
        description = "Extended Material Design icons for Compose",
        license = "Apache-2.0",
        url = "https://github.com/androidx/androidx",
    ),

    // AndroidX
    LibraryInfo(
        name = "AndroidX Core KTX",
        version = "1.18.0",
        description = "Kotlin extensions for Android core libraries",
        license = "Apache-2.0",
        url = "https://github.com/androidx/androidx",
    ),
    LibraryInfo(
        name = "AndroidX AppCompat",
        version = "1.7.1",
        description = "Backwards-compatible Android UI components",
        license = "Apache-2.0",
        url = "https://github.com/androidx/androidx",
    ),
    LibraryInfo(
        name = "AndroidX Activity Compose",
        version = "1.13.0",
        description = "Compose integration for AndroidX Activity",
        license = "Apache-2.0",
        url = "https://github.com/androidx/androidx",
    ),
    LibraryInfo(
        name = "AndroidX Navigation Compose",
        version = "2.9.7",
        description = "Navigation component for Jetpack Compose",
        license = "Apache-2.0",
        url = "https://github.com/androidx/androidx",
    ),
    LibraryInfo(
        name = "AndroidX Lifecycle",
        version = "2.10.0",
        description = "Lifecycle-aware components (ViewModel, runtime, service)",
        license = "Apache-2.0",
        url = "https://github.com/androidx/androidx",
    ),
    LibraryInfo(
        name = "AndroidX Room",
        version = "2.8.4",
        description = "SQLite database abstraction layer",
        license = "Apache-2.0",
        url = "https://github.com/androidx/androidx",
    ),
    LibraryInfo(
        name = "AndroidX WorkManager",
        version = "2.11.2",
        description = "Schedule deferrable, asynchronous tasks",
        license = "Apache-2.0",
        url = "https://github.com/androidx/androidx",
    ),

    // Dependency Injection
    LibraryInfo(
        name = "Dagger Hilt",
        version = "2.59.2",
        description = "Dependency injection framework for Android",
        license = "Apache-2.0",
        url = "https://github.com/google/dagger",
    ),

    // Root & Privilege
    LibraryInfo(
        name = "libsu",
        version = "6.0.0",
        description = "Android root shell library (KernelSU/Magisk/APatch)",
        license = "GPL-3.0",
        url = "https://github.com/topjohnwu/libsu",
    ),
    LibraryInfo(
        name = "Shizuku",
        version = "13.1.5",
        description = "Use system APIs directly with ADB/root privileges",
        license = "Apache-2.0",
        url = "https://github.com/RikkaApps/Shizuku-API",
    ),

    // Charts
    LibraryInfo(
        name = "Vico",
        version = "3.1.0",
        description = "Chart library for Jetpack Compose and Material 3",
        license = "Apache-2.0",
        url = "https://github.com/patrykandpatrick/vico",
    ),

    // Logging
    LibraryInfo(
        name = "XLog",
        version = "1.11.1",
        description = "Lightweight and extensible Android logger",
        license = "Apache-2.0",
        url = "https://github.com/nicholasbrailo/xLog",
    ),

    // Firebase
    LibraryInfo(
        name = "Firebase Analytics",
        version = "34.11.0",
        description = "Google Analytics for Firebase",
        license = "Apache-2.0",
        url = "https://github.com/firebase/firebase-android-sdk",
    ),

    // Native
    LibraryInfo(
        name = "cpuinfo",
        version = "main",
        description = "CPU information library (L1/L2/L3 cache, NEON detection)",
        license = "BSD-2-Clause",
        url = "https://github.com/pytorch/cpuinfo",
    ),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpenSourceLicensesScreen() {
    val context = LocalContext.current

    // Group by license type for summary
    val licenseCounts = libraries.groupBy { it.license }.mapValues { it.value.size }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Header summary
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
                        onClick = {},
                        label = { Text("$license ($count)") },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(libraries, key = { it.name }) { lib ->
            var expanded by rememberSaveable(lib.name) { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = lib.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = lib.version,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                    ),
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = lib.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (expanded) Int.MAX_VALUE else 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    AnimatedVisibility(visible = expanded) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SuggestionChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            text = lib.license,
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = when (lib.license) {
                                            "GPL-3.0" -> MaterialTheme.colorScheme.errorContainer
                                            "BSD-2-Clause" -> MaterialTheme.colorScheme.tertiaryContainer
                                            else -> MaterialTheme.colorScheme.secondaryContainer
                                        },
                                    ),
                                )
                                IconButton(
                                    onClick = {
                                        try {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, lib.url.toUri()),
                                            )
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

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = licenseText(lib.license),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

private fun licenseText(license: String): String = when (license) {
    "Apache-2.0" -> """
        Licensed under the Apache License, Version 2.0 (the "License");
        you may not use this file except in compliance with the License.
        You may obtain a copy of the License at

            http://www.apache.org/licenses/LICENSE-2.0

        Unless required by applicable law or agreed to in writing, software
        distributed under the License is distributed on an "AS IS" BASIS,
        WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
        implied. See the License for the specific language governing
        permissions and limitations under the License.
    """.trimIndent()

    "GPL-3.0" -> """
        This program is free software: you can redistribute it and/or
        modify it under the terms of the GNU General Public License as
        published by the Free Software Foundation, either version 3 of
        the License, or (at your option) any later version.

        This program is distributed in the hope that it will be useful,
        but WITHOUT ANY WARRANTY; without even the implied warranty of
        MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
        GNU General Public License for more details.

        You should have received a copy of the GNU General Public License
        along with this program. If not, see <https://www.gnu.org/licenses/>.
    """.trimIndent()

    "BSD-2-Clause" -> """
        Redistribution and use in source and binary forms, with or without
        modification, are permitted provided that the following conditions
        are met:

        1. Redistributions of source code must retain the above copyright
           notice, this list of conditions and the following disclaimer.
        2. Redistributions in binary form must reproduce the above
           copyright notice, this list of conditions and the following
           disclaimer in the documentation and/or other materials provided
           with the distribution.

        THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
        "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
        LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
        FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
    """.trimIndent()

    else -> "See project repository for license details."
}
