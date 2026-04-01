package com.cloudorz.openmonitor.feature.process

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudorz.openmonitor.core.model.process.ProcessFilterMode
import com.cloudorz.openmonitor.core.model.process.ProcessInfo
import com.cloudorz.openmonitor.core.ui.component.SectionHeader
import com.cloudorz.openmonitor.core.ui.theme.ChartGreen
import com.cloudorz.openmonitor.core.ui.theme.ChartRed
import com.cloudorz.openmonitor.core.ui.theme.ChartYellow

@Composable
fun ProcessScreen(
    viewModel: ProcessViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ProcessScreenContent(
        uiState = uiState,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onSortByChanged = viewModel::onSortByChanged,
        onFilterModeChanged = viewModel::onFilterModeChanged,
        onProcessSelected = viewModel::onProcessSelected,
        modifier = Modifier,
    )

    if (uiState.selectedProcess != null) {
        ProcessDetailSheet(
            process = uiState.selectedProcess!!,
            threads = uiState.threads,
            threadsLoading = uiState.threadsLoading,
            canKill = uiState.canKill,
            onKillProcess = viewModel::killProcess,
            onDismiss = viewModel::onProcessDismissed,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProcessScreenContent(
    uiState: ProcessUiState,
    onSearchQueryChanged: (String) -> Unit,
    onSortByChanged: (SortBy) -> Unit,
    onFilterModeChanged: (ProcessFilterMode) -> Unit,
    onProcessSelected: (ProcessInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Search bar
        ProcessSearchBar(
            query = uiState.searchQuery,
            onQueryChanged = onSearchQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // Filter + Sort chips row
        FilterAndSortChipsRow(
            selectedSort = uiState.sortBy,
            selectedFilter = uiState.filterMode,
            onSortSelected = onSortByChanged,
            onFilterSelected = onFilterModeChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )

        // Process count header
        SectionHeader(
            title = "Processes",
            action = {
                Text(
                    text = "${uiState.filteredProcesses.size} total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        // Loading or list
        AnimatedVisibility(
            visible = uiState.isLoading,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        AnimatedVisibility(
            visible = !uiState.isLoading,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)),
        ) {
            if (uiState.filteredProcesses.isEmpty()) {
                EmptyState(
                    query = uiState.searchQuery,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ProcessList(
                    processes = uiState.filteredProcesses,
                    onProcessClick = onProcessSelected,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ProcessSearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier,
        placeholder = {
            Text(
                text = "Search processes...",
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChanged("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterAndSortChipsRow(
    selectedSort: SortBy,
    selectedFilter: ProcessFilterMode,
    onSortSelected: (SortBy) -> Unit,
    onFilterSelected: (ProcessFilterMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Filter: All / App
        ProcessFilterMode.entries.forEach { mode ->
            FilterChip(
                selected = selectedFilter == mode,
                onClick = { onFilterSelected(mode) },
                label = {
                    Text(
                        text = when (mode) {
                            ProcessFilterMode.ALL -> "All"
                            ProcessFilterMode.APP_ONLY -> "App"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
            )
        }

        // Sort chips
        SortBy.entries.forEach { sort ->
            FilterChip(
                selected = selectedSort == sort,
                onClick = { onSortSelected(sort) },
                label = {
                    Text(
                        text = when (sort) {
                            SortBy.CPU -> "CPU %"
                            SortBy.MEMORY -> "Memory"
                            SortBy.NAME -> "Name"
                            SortBy.PID -> "PID"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun ProcessList(
    processes: List<ProcessInfo>,
    onProcessClick: (ProcessInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            items = processes,
            key = { it.pid },
        ) { process ->
            ProcessListItem(
                process = process,
                onClick = { onProcessClick(process) },
                modifier = Modifier
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ProcessListItem(
    process: ProcessInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // App icon placeholder
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Process name and PID
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = process.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "PID: ${process.pid}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // CPU% with color coding
        Column(
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = "%.1f%%".format(process.cpuPercent),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = cpuColor(process.cpuPercent),
            )
            Text(
                text = "%.1f MB".format(process.rssMB),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(
    query: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (query.isNotEmpty()) "No processes matching \"$query\""
                else "No processes found",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Returns a color based on CPU usage percentage:
 * - Green for < 5%
 * - Yellow for 5-20%
 * - Red for > 20%
 */
private fun cpuColor(cpuPercent: Double): Color {
    return when {
        cpuPercent < 5.0 -> ChartGreen
        cpuPercent <= 20.0 -> ChartYellow
        else -> ChartRed
    }
}
