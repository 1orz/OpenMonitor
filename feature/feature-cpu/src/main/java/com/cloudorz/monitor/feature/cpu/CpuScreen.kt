package com.cloudorz.monitor.feature.cpu

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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudorz.monitor.core.model.cpu.CpuClusterStatus
import com.cloudorz.monitor.core.model.cpu.CpuGlobalStatus
import com.cloudorz.monitor.core.ui.theme.ChartGreen
import com.cloudorz.monitor.core.ui.theme.ChartRed
import com.cloudorz.monitor.core.ui.theme.ChartYellow

@Composable
fun CpuScreen(
    viewModel: CpuControlViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CpuControlContent(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onGovernorChanged = viewModel::setGovernor,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CpuControlContent(
    uiState: CpuControlUiState,
    onRefresh: () -> Unit,
    onGovernorChanged: (policyIndex: Int, governor: String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "CPU\u63A7\u5236",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "\u5237\u65B0",
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

                // CPU overview header
                CpuOverviewHeader(cpuStatus = uiState.cpuStatus)

                // Cluster cards
                val clusters = uiState.cpuStatus.clusters
                clusters.forEachIndexed { index, cluster ->
                    ClusterCard(
                        cluster = cluster,
                        clusterLabel = getClusterLabel(index, clusters.size),
                        cores = uiState.cpuStatus.cores.filter { it.coreIndex in cluster.coreIndices },
                        onGovernorChanged = { governor ->
                            onGovernorChanged(cluster.clusterIndex, governor)
                        },
                    )
                }

                // Root warning
                RootWarningBanner()

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun CpuOverviewHeader(cpuStatus: CpuGlobalStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = cpuStatus.cpuName.ifEmpty { "CPU" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "\u5728\u7EBF\u6838\u5FC3: ${cpuStatus.onlineCoreCount}/${cpuStatus.coreCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
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
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ClusterCard(
    cluster: CpuClusterStatus,
    clusterLabel: String,
    cores: List<com.cloudorz.monitor.core.model.cpu.CpuCoreInfo>,
    onGovernorChanged: (String) -> Unit,
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
            // Header
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

            // Governor selector
            GovernorSelector(
                currentGovernor = cluster.governor,
                availableGovernors = cluster.availableGovernors,
                onGovernorSelected = onGovernorChanged,
            )

            // Frequency range
            FrequencyRangeSection(cluster = cluster)

            // Core online/offline toggles
            if (cores.isNotEmpty()) {
                Text(
                    text = "\u6838\u5FC3\u72B6\u6001",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    cores.forEach { core ->
                        CoreToggleItem(
                            coreIndex = core.coreIndex,
                            isOnline = core.isOnline,
                            loadPercent = core.loadPercent,
                            currentFreqMHz = core.currentFreqMHz,
                        )
                    }
                }
            }

            // Current status
            CurrentClusterStatus(cluster = cluster, cores = cores)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GovernorSelector(
    currentGovernor: String,
    availableGovernors: List<String>,
    onGovernorSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = "\u8C03\u901F\u5668",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = currentGovernor.ifEmpty { "\u672A\u77E5" },
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                availableGovernors.forEach { governor ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = governor,
                                fontWeight = if (governor == currentGovernor) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            onGovernorSelected(governor)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FrequencyRangeSection(cluster: CpuClusterStatus) {
    val availableFreqs = cluster.availableFrequenciesKHz
    if (availableFreqs.isEmpty()) return

    val minAvailable = availableFreqs.min().toFloat()
    val maxAvailable = availableFreqs.max().toFloat()

    if (minAvailable >= maxAvailable) return

    var sliderRange by remember(cluster.minFreqKHz, cluster.maxFreqKHz) {
        mutableStateOf(cluster.minFreqKHz.toFloat()..cluster.maxFreqKHz.toFloat())
    }

    Column {
        Text(
            text = "\u9891\u7387\u8303\u56F4",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "%.0f MHz".format(sliderRange.start / 1000f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "%.0f MHz".format(sliderRange.endInclusive / 1000f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        RangeSlider(
            value = sliderRange,
            onValueChange = { range -> sliderRange = range },
            valueRange = minAvailable..maxAvailable,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "%.0f MHz".format(minAvailable / 1000f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = "%.0f MHz".format(maxAvailable / 1000f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun CoreToggleItem(
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
            Switch(
                checked = isOnline,
                onCheckedChange = { /* Root operation placeholder */ },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ChartGreen,
                    checkedTrackColor = ChartGreen.copy(alpha = 0.3f),
                ),
            )
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
    cores: List<com.cloudorz.monitor.core.model.cpu.CpuCoreInfo>,
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
                label = "\u5E73\u5747\u8D1F\u8F7D",
                value = "%.1f%%".format(avgLoad),
                valueColor = loadColor(avgLoad),
            )
            StatusItem(
                label = "\u5E73\u5747\u9891\u7387",
                value = "%.0f MHz".format(avgFreq),
                valueColor = MaterialTheme.colorScheme.primary,
            )
            StatusItem(
                label = "\u8C03\u901F\u5668",
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
private fun RootWarningBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = ChartYellow.copy(alpha = 0.15f),
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = ChartYellow,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "\u9700\u8981 Root \u6743\u9650",
                style = MaterialTheme.typography.bodyMedium,
                color = ChartYellow,
            )
        }
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
        0 -> "Cluster $index (Little)"
        totalClusters - 1 -> "Cluster $index (Big)"
        else -> "Cluster $index (Mid)"
    }
}
