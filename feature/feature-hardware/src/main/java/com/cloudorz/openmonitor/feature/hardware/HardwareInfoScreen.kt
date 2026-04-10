package com.cloudorz.openmonitor.feature.hardware

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.ScreenshotMonitor
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudorz.openmonitor.core.model.cpu.CpuGlobalStatus
import com.cloudorz.openmonitor.core.ui.R
import com.cloudorz.openmonitor.core.model.display.DisplayInfo
import com.cloudorz.openmonitor.core.model.gpu.GpuInfo
import com.cloudorz.openmonitor.core.model.memory.MemoryInfo
import com.cloudorz.openmonitor.core.model.memory.SwapInfo
import com.cloudorz.openmonitor.core.model.storage.StorageInfo
import com.cloudorz.openmonitor.core.ui.component.DeviceBrandLogo
import com.cloudorz.openmonitor.core.ui.component.VendorLogo
import com.cloudorz.openmonitor.core.ui.hapticClick
import androidx.compose.ui.platform.LocalView

@Composable
fun HardwareInfoScreen(
    onCpuAnalysisClick: () -> Unit = {},
    onVulkanInfoClick: () -> Unit = {},
    onOpenGLInfoClick: () -> Unit = {},
    onPartitionsClick: () -> Unit = {},
    viewModel: HardwareInfoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.isLoading && uiState.cpuStatus.clusters.isEmpty()) {
        SkeletonLoadingContent()
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }
            item { ProcessorCard(cpuStatus = uiState.cpuStatus, onCpuAnalysisClick = onCpuAnalysisClick) }
            item { GpuCard(gpuInfo = uiState.gpuInfo, onVulkanInfoClick = onVulkanInfoClick, onOpenGLInfoClick = onOpenGLInfoClick) }
            item { DisplayCard(displayInfo = uiState.displayInfo) }
            item { MemoryCard(memoryInfo = uiState.memoryInfo, swapInfo = uiState.swapInfo) }
            item { StorageCard(storageInfo = uiState.storageInfo, onPartitionsClick = onPartitionsClick) }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

// ── Processor Card ──────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProcessorCard(cpuStatus: CpuGlobalStatus, onCpuAnalysisClick: () -> Unit) {
    val view = LocalView.current
    val socInfo = cpuStatus.socInfo

    SectionCard(title = stringResource(R.string.hw_processor), icon = Icons.Outlined.Memory, titleColor = MaterialTheme.colorScheme.primary) {
        // SoC header
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (socInfo.vendor.isNotBlank()) {
                VendorLogo(vendor = socInfo.vendor, size = 48.dp)
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                if (cpuStatus.cpuName.isNotEmpty()) {
                    Text(
                        text = cpuStatus.cpuName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    ShimmerBox(widthFraction = 0.7f, height = 22.dp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Badges
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (socInfo.fab.isNotBlank()) InfoBadge(socInfo.fab)
            InfoBadge(stringResource(R.string.hw_core_count, cpuStatus.coreCount))
            InfoBadge(stringResource(R.string.hw_bit_64))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // CPU configuration
        if (socInfo.cpuDescription.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.small,
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.hw_cpu_config),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    socInfo.cpuDescription.split("\n").forEachIndexed { index, line ->
                        if (line.isNotBlank()) {
                            val clusterColors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary,
                                Color(0xFF66BB6A),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        .background(clusterColors.getOrElse(index) { MaterialTheme.colorScheme.onSurface }),
                                )
                                Spacer(modifier = Modifier.width(8.dp))

                                // Format cluster line with frequency info from clusters
                                val cluster = cpuStatus.clusters.getOrNull(index)
                                val freqRange = if (cluster != null) {
                                    "    %.0f-%.0f MHz".format(
                                        cluster.minFreqKHz / 1000.0,
                                        cluster.maxFreqKHz / 1000.0,
                                    )
                                } else ""
                                Text(
                                    text = line.trim() + freqRange,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (index < socInfo.cpuDescription.split("\n").lastIndex) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Detail rows
        if (socInfo.vendor.isNotBlank()) InfoRow(stringResource(R.string.hw_vendor), socInfo.vendor)
        if (socInfo.hardwareId.isNotBlank()) InfoRow(stringResource(R.string.hw_hardware), socInfo.hardwareId)
        if (socInfo.architecture.isNotBlank()) InfoRow(stringResource(R.string.hw_architecture), socInfo.architecture)
        if (socInfo.abi.isNotBlank()) InfoRow("ABI", "${socInfo.abi} (64-bit)")

        // Governor from first cluster
        val governor = cpuStatus.clusters.firstOrNull()?.governor ?: ""
        if (governor.isNotBlank()) InfoRow(stringResource(R.string.hw_governor), governor)

        Spacer(modifier = Modifier.height(8.dp))

        // CPU Analysis button
        Button(
            onClick = { view.hapticClick(); onCpuAnalysisClick() },
            modifier = Modifier.fillMaxWidth(0.55f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(Icons.Outlined.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.hw_cpu_analysis), fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

// ── GPU Card ────────────────────────────────────────────────────────────────

@Composable
private fun GpuCard(gpuInfo: GpuInfo, onVulkanInfoClick: () -> Unit = {}, onOpenGLInfoClick: () -> Unit = {}) {
    val view = LocalView.current
    SectionCard(title = "GPU", icon = Icons.Outlined.DeveloperBoard, titleColor = MaterialTheme.colorScheme.error) {
        // GPU header — model name as title
        val rawModel = gpuInfo.glRenderer.ifBlank { gpuInfo.model.ifEmpty { gpuInfo.vendor.displayName } }
        val displayModel = rawModel
            .replace("(TM)", "™")
            .replace("(R)", "®")
            .replace("(tm)", "™")
            .replace("(r)", "®")
        Text(
            text = displayModel,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        // Subtitle with chip version (like "Adreno 830v2")
        if (gpuInfo.chipId.isNotBlank()) {
            Text(
                text = gpuInfo.chipId,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Key info in rows
        InfoRow(stringResource(R.string.hw_vendor), gpuInfo.vendor.displayName.substringBefore(" "))
        if (gpuInfo.glRenderer.isNotBlank()) {
            InfoRow(stringResource(R.string.hw_renderer), gpuInfo.glRenderer)
        } else if (gpuInfo.model.isNotEmpty()) {
            InfoRow(stringResource(R.string.hw_renderer), gpuInfo.model)
        }

        // Chip details
        if (gpuInfo.chipId.isNotBlank()) InfoRow(stringResource(R.string.hw_chip), gpuInfo.chipId)
        if (gpuInfo.gmemSizeKB > 0) {
            InfoRow(stringResource(R.string.hw_on_chip_memory), "%.2f MB".format(gpuInfo.gmemSizeKB / 1024.0))
        }

        // Mali-specific
        if (gpuInfo.shaderCores > 0) InfoRow(stringResource(R.string.hw_shader_cores), gpuInfo.shaderCores.toString())
        if (gpuInfo.busWidthBits > 0) InfoRow(stringResource(R.string.hw_bus_width), "${gpuInfo.busWidthBits} bits")
        if (gpuInfo.l2CacheKB > 0) InfoRow(stringResource(R.string.hw_l2_cache), "${gpuInfo.l2CacheKB} KB")

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(8.dp))

        // ── Graphics API section ──────────────────────────────────────────
        Text(
            text = stringResource(R.string.hw_graphics_api),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Vulkan
        if (gpuInfo.vulkanVersion.isNotBlank()) {
            InfoRow(stringResource(R.string.hw_vulkan_api_version), gpuInfo.vulkanVersion)

            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = { view.hapticClick(); onVulkanInfoClick() },
                modifier = Modifier.fillMaxWidth(0.55f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(stringResource(R.string.hw_vulkan_features), fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // OpenGL ES
        Text(
            text = "OpenGL",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        if (gpuInfo.glVersionFull.isNotBlank()) {
            Text(
                text = gpuInfo.glVersionFull,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else if (gpuInfo.glesVersion.isNotBlank()) {
            Text(
                text = formatGlesVersion(gpuInfo.glesVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = { view.hapticClick(); onOpenGLInfoClick() },
            modifier = Modifier.fillMaxWidth(0.55f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.tertiary,
            ),
            shape = MaterialTheme.shapes.large,
        ) {
            Text(stringResource(R.string.hw_opengl_es_features), fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }

        if (gpuInfo.driverVersion.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            InfoRow(stringResource(R.string.hw_driver_version), gpuInfo.driverVersion)
        }
    }
}

private fun formatGlesVersion(raw: String): String {
    val version = raw.toIntOrNull() ?: return raw
    val major = version shr 16
    val minor = version and 0xFFFF
    return "OpenGL ES $major.$minor"
}

// ── Display Card ────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisplayCard(displayInfo: DisplayInfo) {
    SectionCard(title = stringResource(R.string.hw_display), icon = Icons.Outlined.ScreenshotMonitor, titleColor = MaterialTheme.colorScheme.primary) {
        // Resolution header
        Text(
            text = "${displayInfo.widthPixels} x ${displayInfo.heightPixels}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (displayInfo.maxRefreshRateHz > 0 && displayInfo.ppi > 0) {
            Text(
                text = "%.0f Hz \u2022 %d ppi".format(displayInfo.maxRefreshRateHz, displayInfo.ppi),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Badges
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (displayInfo.physicalSizeInch > 0) InfoBadge("%.2f\"".format(displayInfo.physicalSizeInch))
            if (displayInfo.densityBucket.isNotBlank()) InfoBadge(displayInfo.densityBucket)
            if (displayInfo.isHdr) InfoBadge("HDR")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Detail rows
        if (displayInfo.widthPixels > 0) {
            InfoRow(stringResource(R.string.hw_current_resolution), "${displayInfo.widthPixels} x ${displayInfo.heightPixels} @ %.0f Hz".format(displayInfo.refreshRateHz))
        }
        if (displayInfo.physicalSizeInch > 0) {
            InfoRow(stringResource(R.string.hw_screen_size), "%.2f in / %d mm".format(displayInfo.physicalSizeInch, displayInfo.physicalSizeMM))
        }

        // Supported resolutions (deduplicated)
        val uniqueResolutions = displayInfo.supportedModes
            .map { "${it.width} x ${it.height}" }
            .distinct()
        if (uniqueResolutions.isNotEmpty()) {
            InfoRow(stringResource(R.string.hw_supported_resolutions), uniqueResolutions.joinToString("\n"))
        }

        // Supported refresh rates (deduplicated, sorted)
        val uniqueRefreshRates = displayInfo.supportedModes
            .map { it.refreshRate }
            .distinct()
            .sorted()
        if (uniqueRefreshRates.isNotEmpty()) {
            InfoRow(stringResource(R.string.hw_supported_refresh_rates), uniqueRefreshRates.joinToString(", ") { "%.0f Hz".format(it) })
        }

        if (displayInfo.aspectRatio.isNotBlank()) InfoRow(stringResource(R.string.hw_aspect_ratio), displayInfo.aspectRatio)
        if (displayInfo.densityDpi > 0) {
            val dpWidth = (displayInfo.widthPixels * 160f / displayInfo.densityDpi).toInt()
            val dpHeight = (displayInfo.heightPixels * 160f / displayInfo.densityDpi).toInt()
            InfoRow(stringResource(R.string.hw_android_density), "${displayInfo.densityDpi} dpi (${displayInfo.densityBucket})\n${dpHeight}dp x ${dpWidth}dp")
        }
        InfoRow(stringResource(R.string.hw_wide_color_gamut), if (displayInfo.wideColorGamut) stringResource(R.string.hw_yes) else stringResource(R.string.hw_no))

        if (displayInfo.hdrCapabilities.isNotEmpty()) {
            InfoRow(stringResource(R.string.hw_hdr_support), displayInfo.hdrCapabilities.joinToString("\n"))
        }

        // Panel name
        if (displayInfo.panelName.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            InfoRow(stringResource(R.string.hw_panel), displayInfo.panelName)
        }
    }
}

// ── Memory Card ─────────────────────────────────────────────────────────────

@Composable
private fun MemoryCard(memoryInfo: MemoryInfo, swapInfo: SwapInfo) {
    SectionCard(title = stringResource(R.string.hw_memory), icon = Icons.Outlined.Memory, titleColor = MaterialTheme.colorScheme.primary) {
        // RAM size
        val totalGB = memoryInfo.totalKB / 1024.0 / 1024.0
        val ramLabel = when {
            totalGB >= 15.5 -> "16 GB"
            totalGB >= 11.5 -> "12 GB"
            totalGB >= 7.5 -> "8 GB"
            totalGB >= 5.5 -> "6 GB"
            totalGB >= 3.5 -> "4 GB"
            else -> "%.1f GB".format(totalGB)
        }
        Text(
            text = stringResource(R.string.hw_memory_size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(
            text = ramLabel,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // RAM usage bar
        val totalMB = memoryInfo.totalKB / 1024.0
        val usedMB = memoryInfo.usedKB / 1024.0
        val availMB = memoryInfo.availableKB / 1024.0
        UsageBarSection(
            label = stringResource(R.string.hw_memory),
            totalLabel = stringResource(R.string.hw_total_format, "%.2f GB".format(totalMB / 1024.0)),
            usedLabel = stringResource(R.string.hw_used_format, "%.2f GB".format(usedMB / 1024.0)),
            freeLabel = stringResource(R.string.hw_free_format, "%.2f GB".format(availMB / 1024.0)),
            progress = (memoryInfo.usedPercent / 100.0).toFloat(),
        )

        // ZRAM
        val zram = swapInfo.zram
        if (zram != null && zram.memLimitKB > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            UsageBarSection(
                label = "ZRAM",
                totalLabel = stringResource(R.string.hw_total_format, "%.2f GB".format(zram.memLimitKB / 1024.0 / 1024.0)),
                usedLabel = stringResource(R.string.hw_used_format, "%.2f GB".format(zram.memUsedKB / 1024.0 / 1024.0)),
                freeLabel = stringResource(R.string.hw_free_format, "%.2f GB".format((zram.memLimitKB - zram.memUsedKB) / 1024.0 / 1024.0)),
                progress = (zram.memUsagePercent / 100.0).toFloat(),
            )
        }
    }
}

@Composable
private fun UsageBarSection(
    label: String,
    totalLabel: String,
    usedLabel: String,
    freeLabel: String,
    progress: Float,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(totalLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
    Spacer(modifier = Modifier.height(4.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(usedLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Text(freeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
    }
    Spacer(modifier = Modifier.height(4.dp))
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(MaterialTheme.shapes.small),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
    )
}

// ── Storage Card ────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorageCard(storageInfo: StorageInfo, onPartitionsClick: () -> Unit = {}) {
    val view = LocalView.current
    val internal = storageInfo.internalStorage
    val totalGB = internal.totalBytes / 1024.0 / 1024.0 / 1024.0
    // Round to marketing size
    val marketingSize = when {
        totalGB >= 900 -> "1 TB"
        totalGB >= 450 -> "512 GB"
        totalGB >= 200 -> "256 GB"
        totalGB >= 100 -> "128 GB"
        totalGB >= 50 -> "64 GB"
        else -> "%.0f GB".format(totalGB)
    }

    SectionCard(title = stringResource(R.string.hw_storage), icon = Icons.Outlined.SdStorage, titleColor = MaterialTheme.colorScheme.primary) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(stringResource(R.string.hw_size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Text(marketingSize, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (storageInfo.storageType.isNotBlank()) {
                Column {
                    Text(stringResource(R.string.hw_type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(storageInfo.storageType, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Usage bar
        val usedGB = internal.usedBytes / 1024.0 / 1024.0 / 1024.0
        val availGB = internal.availableBytes / 1024.0 / 1024.0 / 1024.0
        UsageBarSection(
            label = stringResource(R.string.hw_used_format, "%.0f GB".format(usedGB)),
            totalLabel = stringResource(R.string.hw_total_format, marketingSize),
            usedLabel = "",
            freeLabel = "",
            progress = (internal.usedPercent / 100.0).toFloat(),
        )

        // Partition breakdown
        storageInfo.partitions.forEach { part ->
            val partUsedGB = part.usedBytes / 1024.0 / 1024.0 / 1024.0
            Spacer(modifier = Modifier.height(2.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(part.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                Text("%.2f GB".format(partUsedGB), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(8.dp))

        // Internal storage details
        Text(
            text = stringResource(R.string.hw_internal_storage),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (storageInfo.fileSystem.isNotBlank()) InfoRow(stringResource(R.string.hw_file_system), storageInfo.fileSystem)
        if (storageInfo.blockSizeBytes > 0) InfoRow(stringResource(R.string.hw_block_size), "${storageInfo.blockSizeBytes / 1024} kB")

        // /data partition info
        storageInfo.partitions.firstOrNull { it.path == "/data" }?.let { data ->
            val dataTotalGB = data.totalBytes / 1024.0 / 1024.0 / 1024.0
            InfoRow("/data", stringResource(R.string.hw_total_format, "%.0f GB".format(dataTotalGB)))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Partition detail button
        Button(
            onClick = { view.hapticClick(); onPartitionsClick() },
            modifier = Modifier.fillMaxWidth(0.55f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(Icons.Outlined.SdStorage, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.hw_partitions), fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

// ── Shared Components ───────────────────────────────────────────────────────

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    titleColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit,
) {
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
                color = titleColor,
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun InfoBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
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

// ── Skeleton Loading ───────────────────────────────────────────────────────

@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_translate",
    )
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 200f, 0f),
        end = Offset(translateAnim, 0f),
    )
}

@Composable
private fun ShimmerBox(
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
    height: Dp = 14.dp,
) {
    val brush = shimmerBrush()
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(MaterialTheme.shapes.small)
            .background(brush),
    )
}

@Composable
private fun SkeletonLoadingContent() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }
        // Processor skeleton
        item { SkeletonProcessorCard() }
        // GPU skeleton
        item { SkeletonGpuCard() }
        // Display skeleton
        item { SkeletonSimpleCard(lineCount = 5) }
        // Memory skeleton
        item { SkeletonMemoryCard() }
        // Storage skeleton
        item { SkeletonSimpleCard(lineCount = 4) }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun SkeletonProcessorCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title
            ShimmerBox(widthFraction = 0.3f, height = 14.dp)
            Spacer(modifier = Modifier.height(12.dp))
            // SoC name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(shimmerBrush()),
                )
                Spacer(modifier = Modifier.width(12.dp))
                ShimmerBox(widthFraction = 0.5f, height = 22.dp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Badges
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBox(modifier = Modifier.width(60.dp), widthFraction = 1f, height = 24.dp)
                ShimmerBox(modifier = Modifier.width(80.dp), widthFraction = 1f, height = 24.dp)
                ShimmerBox(modifier = Modifier.width(50.dp), widthFraction = 1f, height = 24.dp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            // CPU config block
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.small,
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    ShimmerBox(widthFraction = 0.35f, height = 12.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    repeat(3) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(shimmerBrush()),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            ShimmerBox(widthFraction = 0.7f, height = 14.dp)
                        }
                        if (it < 2) Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // Info rows
            repeat(4) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ShimmerBox(modifier = Modifier.weight(0.4f), widthFraction = 0.6f, height = 12.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    ShimmerBox(modifier = Modifier.weight(0.6f), widthFraction = 0.8f, height = 12.dp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Button
            ShimmerBox(widthFraction = 0.55f, height = 40.dp)
        }
    }
}

@Composable
private fun SkeletonGpuCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ShimmerBox(widthFraction = 0.15f, height = 14.dp)
            Spacer(modifier = Modifier.height(12.dp))
            ShimmerBox(widthFraction = 0.45f, height = 22.dp)
            Spacer(modifier = Modifier.height(4.dp))
            ShimmerBox(widthFraction = 0.3f, height = 14.dp)
            Spacer(modifier = Modifier.height(12.dp))
            repeat(3) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ShimmerBox(modifier = Modifier.weight(0.4f), widthFraction = 0.5f, height = 12.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    ShimmerBox(modifier = Modifier.weight(0.6f), widthFraction = 0.6f, height = 12.dp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerBox(widthFraction = 0.25f, height = 14.dp)
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerBox(widthFraction = 0.55f, height = 40.dp)
        }
    }
}

@Composable
private fun SkeletonMemoryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ShimmerBox(widthFraction = 0.25f, height = 14.dp)
            Spacer(modifier = Modifier.height(12.dp))
            ShimmerBox(widthFraction = 0.2f, height = 12.dp)
            Spacer(modifier = Modifier.height(4.dp))
            ShimmerBox(widthFraction = 0.15f, height = 22.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ShimmerBox(modifier = Modifier.weight(0.5f), widthFraction = 0.6f, height = 12.dp)
                ShimmerBox(modifier = Modifier.weight(0.5f), widthFraction = 0.5f, height = 12.dp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            ShimmerBox(widthFraction = 1f, height = 10.dp)
        }
    }
}

@Composable
private fun SkeletonSimpleCard(lineCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ShimmerBox(widthFraction = 0.3f, height = 14.dp)
            Spacer(modifier = Modifier.height(12.dp))
            ShimmerBox(widthFraction = 0.5f, height = 22.dp)
            Spacer(modifier = Modifier.height(12.dp))
            repeat(lineCount) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ShimmerBox(modifier = Modifier.weight(0.4f), widthFraction = 0.5f, height = 12.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    ShimmerBox(modifier = Modifier.weight(0.6f), widthFraction = 0.7f, height = 12.dp)
                }
            }
        }
    }
}
