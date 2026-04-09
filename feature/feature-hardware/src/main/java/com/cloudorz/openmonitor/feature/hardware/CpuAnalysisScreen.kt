package com.cloudorz.openmonitor.feature.hardware

import android.os.Build
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudorz.openmonitor.core.model.cpu.CpuClusterStatus
import com.cloudorz.openmonitor.core.ui.R
import com.cloudorz.openmonitor.core.model.cpu.CpuCoreInfo
import com.cloudorz.openmonitor.core.model.cpu.CpuGlobalStatus
import com.cloudorz.openmonitor.core.ui.hapticClickable

@Composable
fun CpuAnalysisScreen(
    viewModel: CpuAnalysisViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        CpuAnalysisContent(cpuStatus = uiState.cpuStatus)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CpuAnalysisContent(cpuStatus: CpuGlobalStatus) {
    val socInfo = cpuStatus.socInfo

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // ── Processor specs card ──────────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.hw_processor),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (socInfo.hardwareId.isNotBlank()) SpecRow(stringResource(R.string.hw_hardware), socInfo.hardwareId)
                    if (socInfo.vendor.isNotBlank()) SpecRow(stringResource(R.string.cpu_manufacturer), socInfo.vendor)
                    if (socInfo.name.isNotBlank()) SpecRow(stringResource(R.string.cpu_market_name), socInfo.name)
                    if (socInfo.fab.isNotBlank()) SpecRow(stringResource(R.string.cpu_process_node), socInfo.fab)
                    SpecRow(stringResource(R.string.cpu_core_count), cpuStatus.coreCount.toString())

                    // CPU types
                    if (socInfo.cpuDescription.isNotBlank()) {
                        SpecRow("CPU", socInfo.cpuDescription)
                    }

                    // Frequency ranges from clusters
                    if (cpuStatus.clusters.isNotEmpty()) {
                        val freqText = cpuStatus.clusters.joinToString("\n") { cluster ->
                            "%.0f MHz - %.0f MHz".format(cluster.minFreqKHz / 1000.0, cluster.maxFreqKHz / 1000.0)
                        }
                        SpecRow(stringResource(R.string.cpu_frequency), freqText)
                    }

                    if (socInfo.architecture.isNotBlank()) SpecRow(stringResource(R.string.hw_architecture), socInfo.architecture)
                    if (socInfo.abi.isNotBlank()) SpecRow("ABI", "${socInfo.abi} (64-bit)")
                    SpecRow(stringResource(R.string.cpu_supported_abis), Build.SUPPORTED_ABIS.joinToString(", "))

                    // CPU Features
                    if (cpuStatus.cpuFeatures.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.cpu_features_label),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.width(100.dp),
                            )
                            FlowRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                cpuStatus.cpuFeatures.forEach { feature ->
                                    Text(
                                        text = feature,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }

                    // ── Expandable: /proc/cpuinfo ──────────────────────────────
                    if (cpuStatus.rawCpuInfo.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(4.dp))
                        ExpandableSection(
                            label = "/proc/cpuinfo",
                            content = cpuStatus.rawCpuInfo.trim(),
                        )
                    }

                    // ── Expandable: CPU frequencies ────────────────────────────
                    if (cpuStatus.clusters.any { it.availableFrequenciesKHz.isNotEmpty() }) {
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(4.dp))
                        val freqContent = buildString {
                            cpuStatus.clusters.forEachIndexed { idx, cluster ->
                                appendLine("Cluster ${idx + 1}:")
                                if (cluster.availableFrequenciesKHz.isNotEmpty()) {
                                    cluster.availableFrequenciesKHz.sorted().forEach { freqKHz ->
                                        appendLine("  %.0f MHz".format(freqKHz / 1000.0))
                                    }
                                } else {
                                    appendLine("  %.0f - %.0f MHz".format(
                                        cluster.minFreqKHz / 1000.0,
                                        cluster.maxFreqKHz / 1000.0,
                                    ))
                                }
                            }
                        }.trim()
                        ExpandableSection(
                            label = "CPU Freq",
                            content = freqContent,
                        )
                    }
                }
            }
        }

        // ── Per-cluster sections ──────────────────────────────────────────────
        val clusters = cpuStatus.clusters
        itemsIndexed(clusters) { index, cluster ->
            ClusterDetailCard(
                clusterIndex = index + 1,
                cluster = cluster,
                cores = cpuStatus.cores.filter { it.coreIndex in cluster.coreIndices },
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ── Expandable Section ──────────────────────────────────────────────────────

@Composable
private fun ExpandableSection(label: String, content: String) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hapticClickable { expanded = !expanded }
                .padding(vertical = 6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.width(100.dp),
            )
            Text(
                text = if (expanded) "▲" else "▼",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (expanded) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

// ── Cluster Card ────────────────────────────────────────────────────────────

@Composable
private fun ClusterDetailCard(
    clusterIndex: Int,
    cluster: CpuClusterStatus,
    cores: List<CpuCoreInfo>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.cpu_cluster_format, clusterIndex),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))

            cores.forEachIndexed { idx, core ->
                if (idx > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                CoreDetailSection(core = core, cluster = cluster)
                if (idx < cores.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                }
            }
        }
    }
}

// ── Core Detail ─────────────────────────────────────────────────────────────

@Composable
private fun CoreDetailSection(
    core: CpuCoreInfo,
    cluster: CpuClusterStatus,
) {
    Text(
        text = "CPU${core.coreIndex}",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(4.dp))

    val archName = core.microarchName
    if (archName != null) {
        SpecRow(stringResource(R.string.cpu_core_type), archName)
    }
    val vendor = core.vendorName
    if (vendor != null) {
        SpecRow(stringResource(R.string.hw_vendor), vendor)
    }
    SpecRow(stringResource(R.string.cpu_cluster_label), cluster.coreIndices.joinToString(prefix = "[", postfix = "]", separator = ", "))
    SpecRow(stringResource(R.string.cpu_max_freq), "%.0f MHz".format(core.maxFreqKHz / 1000.0))
    SpecRow(stringResource(R.string.cpu_min_freq), "%.0f MHz".format(core.minFreqKHz / 1000.0))
    if (cluster.governor.isNotBlank()) SpecRow(stringResource(R.string.hw_governor), cluster.governor)
}

// ── Shared ──────────────────────────────────────────────────────────────────

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.width(100.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
