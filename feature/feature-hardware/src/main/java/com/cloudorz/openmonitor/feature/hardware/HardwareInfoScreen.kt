package com.cloudorz.openmonitor.feature.hardware

import android.os.Build
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudorz.openmonitor.core.model.cpu.CpuGlobalStatus
import com.cloudorz.openmonitor.core.model.display.DisplayInfo
import com.cloudorz.openmonitor.core.model.gpu.GpuInfo
import com.cloudorz.openmonitor.core.model.memory.MemoryInfo
import com.cloudorz.openmonitor.core.model.memory.SwapInfo
import com.cloudorz.openmonitor.core.model.storage.StorageInfo
import com.cloudorz.openmonitor.core.ui.component.DeviceBrandLogo
import com.cloudorz.openmonitor.core.ui.component.VendorLogo

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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
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
    val socInfo = cpuStatus.socInfo

    SectionCard(title = "处理器", icon = Icons.Outlined.Memory, titleColor = MaterialTheme.colorScheme.primary) {
        // SoC header
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (socInfo.vendor.isNotBlank()) {
                VendorLogo(vendor = socInfo.vendor, size = 48.dp)
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cpuStatus.cpuName.ifEmpty { "Unknown" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Badges
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (socInfo.fab.isNotBlank()) InfoBadge(socInfo.fab)
            InfoBadge("${cpuStatus.coreCount} 核心数")
            InfoBadge("64-bit")
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
                        text = "CPU 配置",
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
        if (socInfo.vendor.isNotBlank()) InfoRow("供应商", socInfo.vendor)
        if (socInfo.hardwareId.isNotBlank()) InfoRow("硬件", socInfo.hardwareId)
        if (socInfo.architecture.isNotBlank()) InfoRow("架构", socInfo.architecture)
        if (socInfo.abi.isNotBlank()) InfoRow("ABI", "${socInfo.abi} (64-bit)")

        // Governor from first cluster
        val governor = cpuStatus.clusters.firstOrNull()?.governor ?: ""
        if (governor.isNotBlank()) InfoRow("调频器", governor)

        Spacer(modifier = Modifier.height(8.dp))

        // CPU Analysis button
        Button(
            onClick = onCpuAnalysisClick,
            modifier = Modifier.fillMaxWidth(0.55f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(Icons.Outlined.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("CPU 分析", fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

// ── GPU Card ────────────────────────────────────────────────────────────────

@Composable
private fun GpuCard(gpuInfo: GpuInfo, onVulkanInfoClick: () -> Unit = {}, onOpenGLInfoClick: () -> Unit = {}) {
    SectionCard(title = "GPU", icon = Icons.Outlined.DeveloperBoard, titleColor = MaterialTheme.colorScheme.error) {
        // GPU header — model name as title (like DevCheck's "Adreno 830")
        val displayModel = gpuInfo.glRenderer.ifBlank { gpuInfo.model.ifEmpty { gpuInfo.vendor.displayName } }
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
        InfoRow("供应商", gpuInfo.vendor.displayName.substringBefore(" "))
        if (gpuInfo.glRenderer.isNotBlank()) {
            InfoRow("渲染器", gpuInfo.glRenderer)
        } else if (gpuInfo.model.isNotEmpty()) {
            InfoRow("渲染器", gpuInfo.model)
        }

        // Chip details
        if (gpuInfo.chipId.isNotBlank()) InfoRow("芯片", gpuInfo.chipId)
        if (gpuInfo.gmemSizeKB > 0) {
            InfoRow("片上存储器", "%.2f MB".format(gpuInfo.gmemSizeKB / 1024.0))
        }

        // Mali-specific
        if (gpuInfo.shaderCores > 0) InfoRow("着色器核心", gpuInfo.shaderCores.toString())
        if (gpuInfo.busWidthBits > 0) InfoRow("总线宽度", "${gpuInfo.busWidthBits} bits")
        if (gpuInfo.l2CacheKB > 0) InfoRow("L2 缓存", "${gpuInfo.l2CacheKB} KB")

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(8.dp))

        // ── Graphics API section ──────────────────────────────────────────
        Text(
            text = "图形 API",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Vulkan
        if (gpuInfo.vulkanVersion.isNotBlank()) {
            InfoRow("Vulkan API 版本", gpuInfo.vulkanVersion)

            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onVulkanInfoClick,
                modifier = Modifier.fillMaxWidth(0.55f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                shape = MaterialTheme.shapes.large,
            ) {
                Text("Vulkan 功能", fontWeight = FontWeight.Medium)
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
            onClick = onOpenGLInfoClick,
            modifier = Modifier.fillMaxWidth(0.55f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.tertiary,
            ),
            shape = MaterialTheme.shapes.large,
        ) {
            Text("OpenGL ES 功能", fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }

        if (gpuInfo.driverVersion.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            InfoRow("驱动版本", gpuInfo.driverVersion)
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
    SectionCard(title = "屏幕", icon = Icons.Outlined.ScreenshotMonitor, titleColor = MaterialTheme.colorScheme.primary) {
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
            InfoRow("当前分辨率", "${displayInfo.widthPixels} x ${displayInfo.heightPixels} @ %.0f Hz".format(displayInfo.refreshRateHz))
        }
        if (displayInfo.physicalSizeInch > 0) {
            InfoRow("屏幕尺寸", "%.2f in / %d mm".format(displayInfo.physicalSizeInch, displayInfo.physicalSizeMM))
        }

        // Supported resolutions (deduplicated)
        val uniqueResolutions = displayInfo.supportedModes
            .map { "${it.width} x ${it.height}" }
            .distinct()
        if (uniqueResolutions.isNotEmpty()) {
            InfoRow("支持的分辨率", uniqueResolutions.joinToString("\n"))
        }

        // Supported refresh rates (deduplicated, sorted)
        val uniqueRefreshRates = displayInfo.supportedModes
            .map { it.refreshRate }
            .distinct()
            .sorted()
        if (uniqueRefreshRates.isNotEmpty()) {
            InfoRow("支持的刷新率", uniqueRefreshRates.joinToString(", ") { "%.0f Hz".format(it) })
        }

        if (displayInfo.aspectRatio.isNotBlank()) InfoRow("宽高比", displayInfo.aspectRatio)
        if (displayInfo.densityDpi > 0) {
            val dpWidth = (displayInfo.widthPixels * 160f / displayInfo.densityDpi).toInt()
            val dpHeight = (displayInfo.heightPixels * 160f / displayInfo.densityDpi).toInt()
            InfoRow("Android 密度 (dpi)", "${displayInfo.densityDpi} dpi (${displayInfo.densityBucket})\n${dpHeight}dp x ${dpWidth}dp")
        }
        InfoRow("广色域", if (displayInfo.wideColorGamut) "是" else "否")

        if (displayInfo.hdrCapabilities.isNotEmpty()) {
            InfoRow("HDR支持", displayInfo.hdrCapabilities.joinToString("\n"))
        }

        // Panel name (DevCheck reads this from sysfs)
        if (displayInfo.panelName.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            InfoRow("面板", displayInfo.panelName)
        }
    }
}

// ── Memory Card ─────────────────────────────────────────────────────────────

@Composable
private fun MemoryCard(memoryInfo: MemoryInfo, swapInfo: SwapInfo) {
    SectionCard(title = "内存", icon = Icons.Outlined.Memory, titleColor = MaterialTheme.colorScheme.primary) {
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
            text = "内存大小",
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
            label = "内存",
            totalLabel = "%.2f GB 总计".format(totalMB / 1024.0),
            usedLabel = "%.2f GB 已使用".format(usedMB / 1024.0),
            freeLabel = "%.2f GB 空闲".format(availMB / 1024.0),
            progress = (memoryInfo.usedPercent / 100.0).toFloat(),
        )

        // ZRAM
        val zram = swapInfo.zram
        if (zram != null && zram.memLimitKB > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            UsageBarSection(
                label = "ZRAM",
                totalLabel = "%.2f GB 总计".format(zram.memLimitKB / 1024.0 / 1024.0),
                usedLabel = "%.2f GB 已使用".format(zram.memUsedKB / 1024.0 / 1024.0),
                freeLabel = "%.2f GB 空闲".format((zram.memLimitKB - zram.memUsedKB) / 1024.0 / 1024.0),
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

    SectionCard(title = "存储", icon = Icons.Outlined.SdStorage, titleColor = MaterialTheme.colorScheme.primary) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("大小", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Text(marketingSize, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (storageInfo.storageType.isNotBlank()) {
                Column {
                    Text("类型", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(storageInfo.storageType, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Usage bar
        val usedGB = internal.usedBytes / 1024.0 / 1024.0 / 1024.0
        val availGB = internal.availableBytes / 1024.0 / 1024.0 / 1024.0
        UsageBarSection(
            label = "%.0f GB 已使用".format(usedGB),
            totalLabel = "%s 总计".format(marketingSize),
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
            text = "内部存储器",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (storageInfo.fileSystem.isNotBlank()) InfoRow("文件系统", storageInfo.fileSystem)
        if (storageInfo.blockSizeBytes > 0) InfoRow("Block 大小", "${storageInfo.blockSizeBytes / 1024} kB")

        // /data partition info
        storageInfo.partitions.firstOrNull { it.path == "/data" }?.let { data ->
            val dataTotalGB = data.totalBytes / 1024.0 / 1024.0 / 1024.0
            InfoRow("/data", "%.0f GB 总计".format(dataTotalGB))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Partition detail button
        Button(
            onClick = onPartitionsClick,
            modifier = Modifier.fillMaxWidth(0.55f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(Icons.Outlined.SdStorage, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("磁盘分区", fontWeight = FontWeight.Medium)
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
