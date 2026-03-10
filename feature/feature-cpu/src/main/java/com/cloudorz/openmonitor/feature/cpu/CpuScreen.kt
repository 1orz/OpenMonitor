package com.cloudorz.openmonitor.feature.cpu

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudorz.openmonitor.core.model.cpu.CpuCacheInfo
import com.cloudorz.openmonitor.core.model.cpu.CpuClusterStatus
import com.cloudorz.openmonitor.core.model.cpu.CpuGlobalStatus
import com.cloudorz.openmonitor.core.model.cpu.SocInfo
import com.cloudorz.openmonitor.core.ui.R
import com.cloudorz.openmonitor.core.ui.theme.ChartGreen
import com.cloudorz.openmonitor.core.ui.theme.ChartRed
import com.cloudorz.openmonitor.core.ui.theme.ChartYellow

@Composable
fun CpuScreen(
    viewModel: CpuMonitorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CpuMonitorContent(
        uiState = uiState,
        onRefresh = viewModel::refresh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CpuMonitorContent(
    uiState: CpuMonitorUiState,
    onRefresh: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.cpu_monitor_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { paddingValues ->
        if (uiState.isLoading && uiState.cpuStatus.clusters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                CpuOverviewHeader(cpuStatus = uiState.cpuStatus)

                if (uiState.cpuStatus.cacheInfo.hasData || uiState.cpuStatus.hasArmNeon != null) {
                    CpuCacheCard(
                        cacheInfo = uiState.cpuStatus.cacheInfo,
                        hasArmNeon = uiState.cpuStatus.hasArmNeon,
                    )
                }

                val clusters = uiState.cpuStatus.clusters
                clusters.forEachIndexed { index, cluster ->
                    ClusterCard(
                        cluster = cluster,
                        clusterLabel = getClusterLabel(index, clusters.size),
                        cores = uiState.cpuStatus.cores.filter { it.coreIndex in cluster.coreIndices },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun CpuOverviewHeader(cpuStatus: CpuGlobalStatus) {
    val socInfo = cpuStatus.socInfo

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cpuStatus.cpuName.ifEmpty { "CPU" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (socInfo.hasData && socInfo.vendor.isNotBlank()) {
                        Text(
                            text = socInfo.vendor,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "%.1f%%".format(cpuStatus.totalLoadPercent),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = loadColor(cpuStatus.totalLoadPercent),
                    )
                    Text(
                        text = "%.0f MHz".format(cpuStatus.averageFreqMHz),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // SoC details row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.online_cores_format, cpuStatus.onlineCoreCount, cpuStatus.coreCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                if (socInfo.fab.isNotBlank()) {
                    Text(
                        text = socInfo.fab,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }

            if (socInfo.hasData) {
                SocDetailSection(socInfo = socInfo)
            }
        }
    }
}

@Composable
private fun SocDetailSection(socInfo: SocInfo) {
    Spacer(modifier = Modifier.height(8.dp))

    val detailColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)

    if (socInfo.hardwareId.isNotBlank()) {
        SocDetailRow(
            label = stringResource(R.string.soc_hardware_id),
            value = socInfo.hardwareId,
            valueColor = detailColor,
        )
    }
    if (socInfo.architecture.isNotBlank()) {
        SocDetailRow(
            label = stringResource(R.string.soc_architecture),
            value = socInfo.architecture,
            valueColor = detailColor,
        )
    }
    if (socInfo.abi.isNotBlank()) {
        SocDetailRow(
            label = stringResource(R.string.soc_abi),
            value = socInfo.abi,
            valueColor = detailColor,
        )
    }
    if (socInfo.cpuDescription.isNotBlank()) {
        SocDetailRow(
            label = stringResource(R.string.soc_cpu_config),
            value = socInfo.cpuDescription.replace("\n", " + "),
            valueColor = detailColor,
        )
    }
    if (socInfo.memoryType.isNotBlank()) {
        SocDetailRow(
            label = stringResource(R.string.soc_memory),
            value = socInfo.memoryType,
            valueColor = detailColor,
        )
    }
    if (socInfo.bandwidth.isNotBlank()) {
        SocDetailRow(
            label = stringResource(R.string.soc_bandwidth),
            value = socInfo.bandwidth,
            valueColor = detailColor,
        )
    }
    if (socInfo.channels.isNotBlank()) {
        SocDetailRow(
            label = stringResource(R.string.soc_channels),
            value = socInfo.channels,
            valueColor = detailColor,
        )
    }
}

@Composable
private fun SocDetailRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = valueColor,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClusterCard(
    cluster: CpuClusterStatus,
    clusterLabel: String,
    cores: List<com.cloudorz.openmonitor.core.model.cpu.CpuCoreInfo>,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = clusterLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            GovernorDisplay(
                currentGovernor = cluster.governor,
            )

            FrequencyRangeDisplay(cluster = cluster)

            if (cores.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.core_status),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    cores.forEach { core ->
                        CoreStatusItem(
                            coreIndex = core.coreIndex,
                            isOnline = core.isOnline,
                            loadPercent = core.loadPercent,
                            currentFreqMHz = core.currentFreqMHz,
                        )
                    }
                }
            }

            CurrentClusterStatus(cluster = cluster, cores = cores)
        }
    }
}

@Composable
private fun GovernorDisplay(
    currentGovernor: String,
) {
    Column {
        Text(
            text = stringResource(R.string.governor),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = currentGovernor.ifEmpty { stringResource(R.string.unknown) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FrequencyRangeDisplay(cluster: CpuClusterStatus) {
    val availableFreqs = cluster.availableFrequenciesKHz
    if (availableFreqs.isEmpty()) return

    val minAvailable = availableFreqs.min()
    val maxAvailable = availableFreqs.max()

    if (minAvailable >= maxAvailable) return

    Column {
        Text(
            text = stringResource(R.string.frequency_range),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "%.0f MHz".format(cluster.minFreqKHz / 1000.0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "%.0f MHz".format(cluster.maxFreqKHz / 1000.0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "%.0f MHz".format(minAvailable / 1000.0),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = "%.0f MHz".format(maxAvailable / 1000.0),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun CoreStatusItem(
    coreIndex: Int,
    isOnline: Boolean,
    loadPercent: Double,
    currentFreqMHz: Double,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isOnline) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            },
        ),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Core $coreIndex",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (isOnline) {
                Text(
                    text = "%.0f%%".format(loadPercent),
                    style = MaterialTheme.typography.labelSmall,
                    color = loadColor(loadPercent),
                )
                Text(
                    text = "%.0f MHz".format(currentFreqMHz),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                Text(
                    text = "OFF",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun CurrentClusterStatus(
    cluster: CpuClusterStatus,
    cores: List<com.cloudorz.openmonitor.core.model.cpu.CpuCoreInfo>,
) {
    val onlineCores = cores.filter { it.isOnline }
    val avgLoad = if (onlineCores.isNotEmpty()) onlineCores.sumOf { it.loadPercent } / onlineCores.size else 0.0
    val avgFreq = if (onlineCores.isNotEmpty()) onlineCores.sumOf { it.currentFreqKHz } / (onlineCores.size * 1000.0) else 0.0

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        ),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatusItem(
                label = stringResource(R.string.avg_load),
                value = "%.1f%%".format(avgLoad),
                valueColor = loadColor(avgLoad),
            )
            StatusItem(
                label = stringResource(R.string.avg_frequency),
                value = "%.0f MHz".format(avgFreq),
                valueColor = MaterialTheme.colorScheme.primary,
            )
            StatusItem(
                label = stringResource(R.string.governor),
                value = cluster.governor.ifEmpty { "N/A" },
                valueColor = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun StatusItem(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CpuCacheCard(
    cacheInfo: CpuCacheInfo,
    hasArmNeon: Boolean?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (cacheInfo.hasData) {
                Text(
                    text = stringResource(R.string.cpu_cache_info),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (cacheInfo.l1dSummary.isNotEmpty()) {
                    CacheRow(label = stringResource(R.string.l1d_cache), value = cacheInfo.l1dSummary)
                }
                if (cacheInfo.l1iSummary.isNotEmpty()) {
                    CacheRow(label = stringResource(R.string.l1i_cache), value = cacheInfo.l1iSummary)
                }
                if (cacheInfo.l2Summary.isNotEmpty()) {
                    CacheRow(label = stringResource(R.string.l2_cache), value = cacheInfo.l2Summary)
                }
                if (cacheInfo.l3Summary.isNotEmpty()) {
                    CacheRow(label = stringResource(R.string.l3_cache), value = cacheInfo.l3Summary)
                }
            }

            if (hasArmNeon != null) {
                if (cacheInfo.hasData) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = stringResource(R.string.cpu_features),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CacheRow(
                    label = stringResource(R.string.arm_neon),
                    value = if (hasArmNeon) stringResource(R.string.supported) else stringResource(R.string.not_supported),
                )
            }
        }
    }
}

@Composable
private fun CacheRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun loadColor(loadPercent: Double): androidx.compose.ui.graphics.Color = when {
    loadPercent < 50.0 -> ChartGreen
    loadPercent < 80.0 -> ChartYellow
    else -> ChartRed
}

private fun getClusterLabel(index: Int, totalClusters: Int): String {
    if (totalClusters <= 1) return "Cluster $index"
    return when (index) {
        0 -> "Cluster 0 (Little)"
        totalClusters - 1 -> "Cluster $index (Big)"
        else -> "Cluster $index (Mid)"
    }
}
