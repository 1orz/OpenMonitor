package com.cloudorz.monitor.feature.overview

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudorz.monitor.core.model.cpu.CpuCoreInfo
import com.cloudorz.monitor.core.model.cpu.CpuGlobalStatus
import com.cloudorz.monitor.core.model.gpu.GpuInfo
import com.cloudorz.monitor.core.model.memory.MemoryInfo
import com.cloudorz.monitor.core.model.memory.SwapInfo
import com.cloudorz.monitor.core.model.process.ProcessInfo
import com.cloudorz.monitor.core.ui.chart.ArcGaugeChart
import com.cloudorz.monitor.core.ui.chart.CpuCoreBarChart
import com.cloudorz.monitor.core.ui.chart.CpuCoreBarData
import com.cloudorz.monitor.core.ui.component.StatCard

// ---------------------------------------------------------------------------
// Helper formatting functions
// ---------------------------------------------------------------------------

/**
 * Formats a value in kilobytes to a human-readable string (KB / MB / GB).
 */
fun formatBytes(kb: Long): String {
    return when {
        kb >= 1_048_576 -> "%.1f GB".format(kb / 1_048_576.0)
        kb >= 1024 -> "%.0f MB".format(kb / 1024.0)
        else -> "$kb KB"
    }
}

/**
 * Formats a frequency value in KHz to a human-readable string (KHz / MHz / GHz).
 */
fun formatFrequency(khz: Long): String {
    return when {
        khz >= 1_000_000 -> "%.2f GHz".format(khz / 1_000_000.0)
        khz >= 1000 -> "${khz / 1000} MHz"
        else -> "$khz KHz"
    }
}

/**
 * Formats an uptime value in seconds to HH:MM:SS.
 */
fun formatUptime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

/**
 * Returns a color based on temperature thresholds:
 * green < 40, yellow 40-60, red > 60.
 */
private fun temperatureColor(celsius: Double): Color {
    return when {
        celsius < 40.0 -> Color(0xFF4CAF50)
        celsius < 60.0 -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }
}

/**
 * Returns a color based on load percentage:
 * green < 50, yellow 50-80, red > 80.
 */
private fun loadColor(percent: Double): Color {
    return when {
        percent < 50.0 -> Color(0xFF4CAF50)
        percent < 80.0 -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }
}

// ---------------------------------------------------------------------------
// Root composable
// ---------------------------------------------------------------------------

@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel = hiltViewModel(),
    onProcessClick: (Int) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    } else {
        OverviewContent(
            uiState = uiState,
            onProcessClick = onProcessClick,
        )
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
        // 1. Memory Card
        MemoryCard(
            memoryInfo = uiState.memoryInfo,
            swapInfo = uiState.swapInfo,
        )

        // 2. GPU Card
        GpuCard(gpuInfo = uiState.gpuInfo)

        // 3. CPU + Process Card
        CpuProcessCard(
            cpuStatus = uiState.cpuStatus,
            topProcesses = uiState.topProcesses,
            onProcessClick = onProcessClick,
        )

        // 4. CPU Core Details
        CpuCoreDetailsCard(cpuStatus = uiState.cpuStatus)

        // 5. Bottom Info Bar
        BottomInfoBar(
            batteryStatus = uiState.batteryStatus,
            cpuStatus = uiState.cpuStatus,
        )

        // Bottom spacing
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------
// 1. Memory Card
// ---------------------------------------------------------------------------

@Composable
private fun MemoryCard(
    memoryInfo: MemoryInfo,
    swapInfo: SwapInfo,
) {
    StatCard(
        title = "Memory",
        icon = Icons.Outlined.Memory,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: Arc gauge
            ArcGaugeChart(
                percentage = memoryInfo.usedPercent.toFloat(),
                size = 110.dp,
                strokeWidth = 10.dp,
                label = "Memory",
                valueText = "%.0f%%".format(memoryInfo.usedPercent),
                modifier = Modifier.padding(end = 16.dp),
            )

            // Right: Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DetailRow(
                    label = "Physical",
                    value = "${formatBytes(memoryInfo.usedKB)} / ${formatBytes(memoryInfo.totalKB)}",
                )
                DetailRow(
                    label = "Available",
                    value = formatBytes(memoryInfo.availableKB),
                )
                DetailRow(
                    label = "Swap",
                    value = "${formatBytes(swapInfo.usedKB)} / ${formatBytes(swapInfo.totalKB)}",
                )
                swapInfo.zram?.let { zram ->
                    DetailRow(
                        label = "ZRam Ratio",
                        value = "%.1fx".format(zram.compressionRatio),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2. GPU Card
// ---------------------------------------------------------------------------

@Composable
private fun GpuCard(gpuInfo: GpuInfo) {
    StatCard(
        title = "GPU",
        icon = Icons.Outlined.DeveloperBoard,
    ) {
        // Model name
        if (gpuInfo.model.isNotEmpty()) {
            Text(
                text = gpuInfo.model,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Frequency
        DetailRow(
            label = "Frequency",
            value = "${gpuInfo.currentFreqMHz} / ${gpuInfo.maxFreqMHz} MHz",
        )
        Spacer(modifier = Modifier.height(4.dp))

        // Load with progress bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Load",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(72.dp),
            )
            LinearProgressIndicator(
                progress = { (gpuInfo.loadPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = loadColor(gpuInfo.loadPercent),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "%.1f%%".format(gpuInfo.loadPercent),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Governor
        if (gpuInfo.governor.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            DetailRow(
                label = "Governor",
                value = gpuInfo.governor,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 3. CPU + Process Card
// ---------------------------------------------------------------------------

@Composable
private fun CpuProcessCard(
    cpuStatus: CpuGlobalStatus,
    topProcesses: List<ProcessInfo>,
    onProcessClick: (Int) -> Unit,
) {
    StatCard(
        title = "CPU & Processes",
        icon = Icons.Outlined.Speed,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Left column: Top 5 processes
            Column(
                modifier = Modifier.weight(0.5f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Top Processes",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (topProcesses.isEmpty()) {
                    Text(
                        text = "No data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    topProcesses.forEach { process ->
                        ProcessRow(
                            process = process,
                            onClick = { onProcessClick(process.pid) },
                        )
                    }
                }
            }

            // Right column: CPU summary
            Column(
                modifier = Modifier.weight(0.5f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "CPU Summary",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (cpuStatus.cpuName.isNotEmpty()) {
                    Text(
                        text = cpuStatus.cpuName,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Load: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "%.1f%%".format(cpuStatus.totalLoadPercent),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = loadColor(cpuStatus.totalLoadPercent),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Temp: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "%.1f\u00B0C".format(cpuStatus.temperatureCelsius),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = temperatureColor(cpuStatus.temperatureCelsius),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Cores: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${cpuStatus.onlineCoreCount} / ${cpuStatus.coreCount}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Avg: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "%.0f MHz".format(cpuStatus.averageFreqMHz),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessRow(
    process: ProcessInfo,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = process.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "%.1f%%".format(process.cpuPercent),
                    style = MaterialTheme.typography.labelSmall,
                    color = loadColor(process.cpuPercent),
                )
                Text(
                    text = formatBytes(process.memKB),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 4. CPU Core Details Card
// ---------------------------------------------------------------------------

@Composable
private fun CpuCoreDetailsCard(cpuStatus: CpuGlobalStatus) {
    StatCard(title = "CPU Cores") {
        if (cpuStatus.cores.isEmpty()) {
            Text(
                text = "No core data available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val coreBarData = cpuStatus.cores.map { core ->
                CpuCoreBarData(
                    coreIndex = core.coreIndex,
                    frequencyMHz = core.currentFreqKHz / 1000,
                    loadPercent = core.loadPercent,
                    isOnline = core.isOnline,
                )
            }

            CpuCoreBarChart(
                cores = coreBarData,
                maxFreqMHz = cpuStatus.cores
                    .maxOfOrNull { it.maxFreqKHz / 1000 }
                    ?: 3000,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Detailed core info grid
            CpuCoreDetailGrid(cores = cpuStatus.cores)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CpuCoreDetailGrid(cores: List<CpuCoreInfo>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        maxItemsInEachRow = 4,
    ) {
        cores.forEach { core ->
            CpuCoreDetailItem(
                core = core,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CpuCoreDetailItem(
    core: CpuCoreInfo,
    modifier: Modifier = Modifier,
) {
    val alpha = if (core.isOnline) 1f else 0.4f
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "Core ${core.coreIndex}",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (core.isOnline) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
        )

        if (core.isOnline) {
            Text(
                text = "%.0f%%".format(core.loadPercent),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = loadColor(core.loadPercent),
            )
            Text(
                text = "${core.currentFreqKHz / 1000} MHz",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = textColor,
            )
            Text(
                text = "${core.minFreqKHz / 1000}-${core.maxFreqKHz / 1000}",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                color = textColor.copy(alpha = 0.6f),
            )
        } else {
            Text(
                text = "OFFLINE",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 5. Bottom Info Bar
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BottomInfoBar(
    batteryStatus: com.cloudorz.monitor.core.model.battery.BatteryStatus,
    cpuStatus: CpuGlobalStatus,
) {
    StatCard(
        title = "System Info",
        icon = Icons.Outlined.BatteryChargingFull,
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Power consumption
            InfoChip(
                label = "Power",
                value = "%.2f W".format(batteryStatus.powerW),
            )

            // Battery level + voltage
            InfoChip(
                label = "Battery",
                value = "${batteryStatus.capacity}%  %.2fV".format(batteryStatus.voltageV),
            )

            // Temperature
            InfoChip(
                label = "Temp",
                value = "%.1f\u00B0C".format(batteryStatus.temperatureCelsius),
                valueColor = temperatureColor(batteryStatus.temperatureCelsius),
            )

            // Android version
            InfoChip(
                label = "Android",
                value = Build.VERSION.RELEASE,
            )

            // Uptime
            InfoChip(
                label = "Uptime",
                value = formatUptime(cpuStatus.uptimeSeconds),
            )
        }
    }
}

@Composable
private fun InfoChip(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = valueColor,
        )
    }
}

// ---------------------------------------------------------------------------
// Shared detail row
// ---------------------------------------------------------------------------

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
