package com.cloudorz.monitor.feature.process

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cloudorz.monitor.core.model.process.ProcessInfo
import com.cloudorz.monitor.core.model.process.ProcessState
import com.cloudorz.monitor.core.model.process.ThreadInfo
import com.cloudorz.monitor.core.ui.component.SectionHeader
import com.cloudorz.monitor.core.ui.component.StatCard
import com.cloudorz.monitor.core.ui.theme.ChartGreen
import com.cloudorz.monitor.core.ui.theme.ChartRed
import com.cloudorz.monitor.core.ui.theme.ChartYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessDetailSheet(
    process: ProcessInfo,
    threads: List<ThreadInfo>,
    showKillConfirmation: Boolean,
    onDismiss: () -> Unit,
    onRequestKill: () -> Unit,
    onConfirmKill: () -> Unit,
    onDismissKillConfirmation: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        ProcessDetailContent(
            process = process,
            threads = threads,
            onKillClick = onRequestKill,
        )
    }

    // Kill confirmation dialog
    if (showKillConfirmation) {
        KillConfirmationDialog(
            processName = process.displayName,
            pid = process.pid,
            onConfirm = onConfirmKill,
            onDismiss = onDismissKillConfirmation,
        )
    }
}

@Composable
private fun ProcessDetailContent(
    process: ProcessInfo,
    threads: List<ThreadInfo>,
    onKillClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        // Header: process name + PID
        ProcessDetailHeader(process = process)

        Spacer(modifier = Modifier.height(16.dp))

        // Process identification card
        StatCard(
            title = "Process Info",
            icon = Icons.Default.Info,
        ) {
            DetailRow(label = "PID", value = process.pid.toString())
            DetailRow(label = "PPID", value = process.ppid.toString())
            DetailRow(label = "User", value = process.user.ifEmpty { "N/A" })
            DetailRow(label = "State") {
                ProcessStateBadge(state = process.state)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Resource usage card
        StatCard(
            title = "Resource Usage",
            icon = Icons.Default.Memory,
        ) {
            DetailRow(
                label = "CPU",
                value = "%.2f%%".format(process.cpuPercent),
                valueColor = cpuColor(process.cpuPercent),
            )
            DetailRow(label = "Memory (RSS)", value = "%.2f MB".format(process.rssMB))
            DetailRow(label = "Memory (Swap)", value = "%.2f MB".format(process.swapKB / 1024.0))
            DetailRow(label = "Memory (Shared)", value = "%.2f MB".format(process.shrKB / 1024.0))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // System details card
        StatCard(
            title = "System Details",
            icon = Icons.Default.Settings,
        ) {
            DetailRow(label = "OOM Adj", value = process.oomAdj.toString())
            DetailRow(label = "OOM Score", value = process.oomScore.toString())
            DetailRow(label = "OOM Score Adj", value = process.oomScoreAdj.toString())
            DetailRow(label = "Context Switches", value = process.ctxtSwitches.toString())
            if (process.cGroup.isNotEmpty()) {
                DetailRow(label = "CGroup", value = process.cGroup)
            }
            if (process.cpuSet.isNotEmpty()) {
                DetailRow(label = "CPUSet", value = process.cpuSet)
            }
            if (process.cpusAllowed.isNotEmpty()) {
                DetailRow(label = "CPUs Allowed", value = process.cpusAllowed)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Command line
        if (process.cmdline.isNotEmpty()) {
            StatCard(title = "Command Line") {
                Text(
                    text = process.cmdline,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Thread list
        SectionHeader(
            title = "Threads",
            action = {
                Text(
                    text = "${threads.size} threads",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (threads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            }
        } else {
            // Use a fixed-height container so LazyColumn works inside the scroll parent
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(
                    items = threads,
                    key = { it.tid },
                ) { thread ->
                    ThreadListItem(thread = thread)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Kill process button
        Button(
            onClick = onKillClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Kill Process",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ProcessDetailHeader(process: ProcessInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = process.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "PID ${process.pid}  |  PPID ${process.ppid}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ProcessStateBadge(
            state = process.state,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun ProcessStateBadge(
    state: ProcessState,
    modifier: Modifier = Modifier,
) {
    val (backgroundColor, textColor) = when (state) {
        ProcessState.RUNNING -> ChartGreen.copy(alpha = 0.15f) to ChartGreen
        ProcessState.SLEEPING -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        ProcessState.ZOMBIE -> ChartRed.copy(alpha = 0.15f) to ChartRed
        ProcessState.DEAD -> ChartRed.copy(alpha = 0.15f) to ChartRed
        ProcessState.STOPPED -> ChartYellow.copy(alpha = 0.15f) to ChartYellow
        ProcessState.TRACING -> ChartYellow.copy(alpha = 0.15f) to ChartYellow
        ProcessState.DISK_SLEEP -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        ProcessState.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = state.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        content()
    }
}

@Composable
private fun ThreadListItem(
    thread: ThreadInfo,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // TID
        Text(
            text = thread.tid.toString(),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
            ),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(56.dp),
        )

        // Thread name
        Text(
            text = thread.name.ifEmpty { "<unnamed>" },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.width(8.dp))

        // CPU load
        Text(
            text = "%.1f%%".format(thread.cpuLoadPercent),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = cpuColor(thread.cpuLoadPercent),
        )
    }
}

@Composable
private fun KillConfirmationDialog(
    processName: String,
    pid: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                text = "Kill Process?",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column {
                Text(
                    text = "Are you sure you want to kill this process?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$processName (PID: $pid)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This action cannot be undone. Killing system processes may cause instability.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Kill")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
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
