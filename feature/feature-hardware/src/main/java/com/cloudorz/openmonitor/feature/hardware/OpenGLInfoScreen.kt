package com.cloudorz.openmonitor.feature.hardware

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cloudorz.openmonitor.core.data.datasource.EglHelper

@Composable
fun OpenGLInfoScreen() {
    val glInfo = remember { EglHelper.queryGlStrings() }

    // Parse extensions from full query
    val fullInfo = remember { EglHelper.queryFullGlInfo() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // GL Info card
        item {
            GlSectionCard("OpenGL ES 信息") {
                GlRow("版本", fullInfo.version)
                GlRow("渲染器", fullInfo.renderer)
                GlRow("供应商", fullInfo.vendor)
                GlRow("着色器语言版本", fullInfo.shadingLanguageVersion)
                GlRow("扩展数量", "${fullInfo.extensionsCount}")
            }
        }

        // EGL info
        if (fullInfo.eglVersion.isNotBlank()) {
            item {
                GlSectionCard("EGL 信息") {
                    GlRow("版本", fullInfo.eglVersion)
                    GlRow("供应商", fullInfo.eglVendor)
                    GlRow("Client APIs", fullInfo.eglClientApis)
                }
            }
        }

        // Extensions list
        if (fullInfo.extensions.isNotEmpty()) {
            item {
                GlSectionCard("扩展 (${fullInfo.extensionsCount})") {
                    fullInfo.extensions.forEach { ext ->
                        Text(
                            text = ext,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            }
        }

        // EGL extensions
        if (fullInfo.eglExtensions.isNotEmpty()) {
            item {
                GlSectionCard("EGL 扩展 (${fullInfo.eglExtensions.size})") {
                    fullInfo.eglExtensions.forEach { ext ->
                        Text(
                            text = ext,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun GlSectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun GlRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f),
        )
    }
}
