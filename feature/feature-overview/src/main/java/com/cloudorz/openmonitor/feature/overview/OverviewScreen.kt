package com.cloudorz.openmonitor.feature.overview

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudorz.openmonitor.core.model.cpu.CpuCacheInfo
import com.cloudorz.openmonitor.core.model.cpu.CpuCoreInfo
import com.cloudorz.openmonitor.core.model.cpu.CpuGlobalStatus
import com.cloudorz.openmonitor.core.model.cpu.SocInfo
import com.cloudorz.openmonitor.core.model.gpu.GpuInfo
import com.cloudorz.openmonitor.core.model.memory.MemoryInfo
import com.cloudorz.openmonitor.core.model.memory.SwapInfo
import com.cloudorz.openmonitor.core.model.process.ProcessInfo
import com.cloudorz.openmonitor.core.ui.R
import com.cloudorz.openmonitor.core.ui.chart.ArcGaugeChart
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.style.TextAlign
import com.cloudorz.openmonitor.core.ui.component.DeviceBrandLogo
import com.cloudorz.openmonitor.core.ui.component.StatCard
import com.cloudorz.openmonitor.core.ui.component.VendorLogo

// ---------------------------------------------------------------------------
// Helper functions
// ---------------------------------------------------------------------------

fun formatBytes(kb: Long): String = when {
    kb >= 1_048_576 -> "%.1f GB".format(kb / 1_048_576.0)
    kb >= 1024 -> "%.0f MB".format(kb / 1024.0)
    else -> "$kb KB"
}

fun formatFrequency(khz: Long): String = when {
    khz >= 1_000_000 -> "%.2f GHz".format(khz / 1_000_000.0)
    khz >= 1000 -> "${khz / 1000} MHz"
    else -> "$khz KHz"
}

fun formatUptime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

private fun temperatureColor(celsius: Double): Color = when {
    celsius < 40.0 -> Color(0xFF4CAF50)
    celsius < 60.0 -> Color(0xFFFFC107)
    else -> Color(0xFFF44336)
}

private fun loadColor(percent: Double): Color = when {
    percent < 50.0 -> Color(0xFF4CAF50)
    percent < 80.0 -> Color(0xFFFFC107)
    else -> Color(0xFFF44336)
}

// ---------------------------------------------------------------------------
// Root
// ---------------------------------------------------------------------------

@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel = hiltViewModel(),
    onProcessClick: (Int) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        OverviewContent(uiState = uiState, onProcessClick = onProcessClick)
    }
}

@Composable
private fun OverviewContent(
    uiState: OverviewUiState,
    onProcessClick: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 1. Device / SoC header
        DeviceSocCard(cpuStatus = uiState.cpuStatus)

        // 2. CPU performance
        CpuPerformanceCard(cpuStatus = uiState.cpuStatus)

        // 3. Memory
        MemoryCard(memoryInfo = uiState.memoryInfo, swapInfo = uiState.swapInfo)

        // 4. GPU
        GpuCard(gpuInfo = uiState.gpuInfo)

        // 5. Cache & features (conditional)
        val cache = uiState.cpuStatus.cacheInfo
        val neon = uiState.cpuStatus.hasArmNeon
        if (cache.hasData || neon != null) {
            CacheAndFeaturesCard(cacheInfo = cache, hasArmNeon = neon)
        }

        // 7. System info
        BottomInfoBar(batteryStatus = uiState.batteryStatus, cpuStatus = uiState.cpuStatus)

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------
// 1. Device / SoC Hero Card
// ---------------------------------------------------------------------------

@Composable
private fun DeviceSocCard(cpuStatus: CpuGlobalStatus) {
    val socInfo = cpuStatus.socInfo
    if (!socInfo.hasData && socInfo.deviceBrand.isBlank()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Brand logo (large)
            if (socInfo.deviceBrand.isNotBlank()) {
                DeviceBrandLogo(brand = socInfo.deviceBrand, size = 80.dp)
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                // Device name + fab badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val deviceLabel = socInfo.deviceMarketingName
                        ?.takeIf { it.isNotBlank() }
                        ?: socInfo.deviceBrand.takeIf { it.isNotBlank() }
                    if (deviceLabel != null) {
                        Text(
                            text = deviceLabel,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (socInfo.fab.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = socInfo.fab,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Vendor logo + SoC name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (socInfo.vendor.isNotBlank()) {
                        VendorLogo(vendor = socInfo.vendor, size = 36.dp)
                    }
                    Text(
                        text = cpuStatus.cpuName.ifEmpty { socInfo.name.ifEmpty { "CPU" } },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Key specs
                if (socInfo.hasData) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val specColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    if (socInfo.architecture.isNotBlank()) {
                        SpecRow(label = stringResource(R.string.soc_architecture), value = socInfo.architecture, valueColor = specColor)
                    }
                    if (socInfo.abi.isNotBlank()) {
                        SpecRow(label = stringResource(R.string.soc_abi), value = socInfo.abi, valueColor = specColor)
                    }
                    if (socInfo.cpuDescription.isNotBlank()) {
                        SpecRow(label = stringResource(R.string.soc_cpu_config), value = socInfo.cpuDescription.replace("\n", " + "), valueColor = specColor)
                    }
                    if (socInfo.memoryType.isNotBlank()) {
                        SpecRow(label = stringResource(R.string.soc_memory), value = socInfo.memoryType, valueColor = specColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = valueColor,
        )
    }
}

// ---------------------------------------------------------------------------
// 2. CPU Performance Card
// ---------------------------------------------------------------------------

@Composable
private fun CpuPerformanceCard(cpuStatus: CpuGlobalStatus) {
    StatCard(title = "CPU", icon = Icons.Outlined.Speed) {
        // Summary row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SummaryItem(label = "Load", value = "%.1f%%".format(cpuStatus.totalLoadPercent), valueColor = loadColor(cpuStatus.totalLoadPercent))
            SummaryItem(label = "Temp", value = "%.1f°C".format(cpuStatus.temperatureCelsius), valueColor = temperatureColor(cpuStatus.temperatureCelsius))
            SummaryItem(label = "Avg Freq", value = "%.0f MHz".format(cpuStatus.averageFreqMHz), valueColor = MaterialTheme.colorScheme.primary)
            SummaryItem(label = "Cores", value = "${cpuStatus.onlineCoreCount}/${cpuStatus.coreCount}", valueColor = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (cpuStatus.cores.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                cpuStatus.cores.forEach { core ->
                    CpuCoreProgressRow(core = core)
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun CpuCoreProgressRow(core: CpuCoreInfo) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (core.isOnline) (core.loadPercent / 100.0).toFloat().coerceIn(0f, 1f) else 0f,
        animationSpec = tween(500),
        label = "coreProgress_${core.coreIndex}",
    )
    val barColor = when {
        !core.isOnline -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
        core.loadPercent < 50.0 -> Color(0xFF4CAF50)
        core.loadPercent < 80.0 -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }
    val alpha = if (core.isOnline) 1f else 0.4f

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "${core.coreIndex}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            modifier = Modifier.width(14.dp),
            textAlign = TextAlign.Center,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(RoundedCornerShape(6.dp))
                    .background(barColor),
            )
        }
        if (core.isOnline) {
            Text(
                text = "%.0f%%".format(core.loadPercent),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = barColor,
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.End,
            )
            Text(
                text = "${core.currentFreqKHz / 1000}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.End,
            )
        } else {
            Text(
                text = "OFF",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.width(64.dp),
                textAlign = TextAlign.End,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 3. Memory Card
// ---------------------------------------------------------------------------

@Composable
private fun MemoryCard(memoryInfo: MemoryInfo, swapInfo: SwapInfo) {
    StatCard(title = "Memory", icon = Icons.Outlined.Memory) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ArcGaugeChart(
                percentage = memoryInfo.usedPercent.toFloat(),
                size = 110.dp,
                strokeWidth = 10.dp,
                label = "Memory",
                valueText = "%.0f%%".format(memoryInfo.usedPercent),
                modifier = Modifier.padding(end = 16.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DetailRow(label = "Physical", value = "${formatBytes(memoryInfo.usedKB)} / ${formatBytes(memoryInfo.totalKB)}")
                DetailRow(label = "Available", value = formatBytes(memoryInfo.availableKB))
                DetailRow(label = "Swap", value = "${formatBytes(swapInfo.usedKB)} / ${formatBytes(swapInfo.totalKB)}")
                swapInfo.zram?.let { zram ->
                    DetailRow(label = "ZRam Ratio", value = "%.1fx".format(zram.compressionRatio))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 4. GPU Card
// ---------------------------------------------------------------------------

@Composable
private fun GpuCard(gpuInfo: GpuInfo) {
    StatCard(title = "GPU", icon = Icons.Outlined.DeveloperBoard) {
        if (gpuInfo.model.isNotEmpty()) {
            Text(
                text = gpuInfo.model,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        DetailRow(label = "Frequency", value = "${gpuInfo.currentFreqMHz} / ${gpuInfo.maxFreqMHz} MHz")
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Load", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
            LinearProgressIndicator(
                progress = { (gpuInfo.loadPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = loadColor(gpuInfo.loadPercent),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("%.1f%%".format(gpuInfo.loadPercent), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (gpuInfo.governor.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            DetailRow(label = "Governor", value = gpuInfo.governor)
        }
    }
}

// ---------------------------------------------------------------------------
// 5. Cache & Features Card
// ---------------------------------------------------------------------------

@Composable
private fun CacheAndFeaturesCard(cacheInfo: CpuCacheInfo, hasArmNeon: Boolean?) {
    StatCard(title = stringResource(R.string.cpu_cache_info)) {
        if (cacheInfo.hasData) {
            if (cacheInfo.l1dSummary.isNotEmpty()) DetailRow(label = stringResource(R.string.l1d_cache), value = cacheInfo.l1dSummary)
            if (cacheInfo.l1iSummary.isNotEmpty()) DetailRow(label = stringResource(R.string.l1i_cache), value = cacheInfo.l1iSummary)
            if (cacheInfo.l2Summary.isNotEmpty()) DetailRow(label = stringResource(R.string.l2_cache), value = cacheInfo.l2Summary)
            if (cacheInfo.l3Summary.isNotEmpty()) DetailRow(label = stringResource(R.string.l3_cache), value = cacheInfo.l3Summary)
        }
        if (hasArmNeon != null) {
            if (cacheInfo.hasData) Spacer(modifier = Modifier.height(4.dp))
            DetailRow(
                label = stringResource(R.string.arm_neon),
                value = if (hasArmNeon) stringResource(R.string.supported) else stringResource(R.string.not_supported),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 7. System Info
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BottomInfoBar(
    batteryStatus: com.cloudorz.openmonitor.core.model.battery.BatteryStatus,
    cpuStatus: CpuGlobalStatus,
) {
    StatCard(title = "System Info", icon = Icons.Outlined.BatteryChargingFull) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InfoChip(label = "Power", value = "%.2f W".format(batteryStatus.powerW))
            InfoChip(label = "Battery", value = "${batteryStatus.capacity}%%  %.2fV".format(batteryStatus.voltageV))
            InfoChip(label = "Temp", value = "%.1f\u00B0C".format(batteryStatus.temperatureCelsius), valueColor = temperatureColor(batteryStatus.temperatureCelsius))
            InfoChip(label = "Android", value = Build.VERSION.RELEASE)
            InfoChip(label = "Uptime", value = formatUptime(cpuStatus.uptimeSeconds))
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = valueColor)
    }
}

// ---------------------------------------------------------------------------
// Shared
// ---------------------------------------------------------------------------

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
