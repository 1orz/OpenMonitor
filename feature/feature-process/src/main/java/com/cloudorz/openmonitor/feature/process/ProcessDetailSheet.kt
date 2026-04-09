package com.cloudorz.openmonitor.feature.process

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import com.cloudorz.openmonitor.core.ui.hapticClick
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudorz.openmonitor.core.model.process.ProcessInfo
import com.cloudorz.openmonitor.core.model.process.ProcessState
import com.cloudorz.openmonitor.core.model.process.ThreadInfo
import com.cloudorz.openmonitor.core.ui.R
import com.cloudorz.openmonitor.core.ui.component.SectionHeader
import com.cloudorz.openmonitor.core.ui.component.StatCard
import com.cloudorz.openmonitor.core.ui.theme.ChartGreen
import com.cloudorz.openmonitor.core.ui.theme.ChartRed
import com.cloudorz.openmonitor.core.ui.theme.ChartYellow

@Composable
fun ProcessDetailScreen(
    onBack: () -> Unit,
    pid: String? = null,
    viewModel: ProcessDetailViewModel = hiltViewModel(),
) {
    if (pid != null) {
        androidx.compose.runtime.LaunchedEffect(pid) { viewModel.initPid(pid) }
    }
    val process by viewModel.process.collectAsStateWithLifecycle()
    val threads by viewModel.threads.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val killed by viewModel.killed.collectAsStateWithLifecycle()

    if (killed) {
        onBack()
        return
    }

    if (loading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    } else if (process == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.process_not_found),
                color = MaterialTheme.colorScheme.outline,
            )
        }
    } else {
        ProcessDetailContent(
            process = process!!,
            threads = threads,
            onKill = { viewModel.killProcess() },
        )
    }
}

@Composable
private fun ProcessDetailContent(
    process: ProcessInfo,
    threads: List<ThreadInfo>,
    threadsLoading: Boolean = false,
    onKill: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        ProcessDetailHeader(process = process, onKill = onKill)

        Spacer(modifier = Modifier.height(16.dp))

        StatCard(
            title = stringResource(R.string.process_info),
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

        StatCard(
            title = stringResource(R.string.process_resource_usage),
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

        StatCard(
            title = stringResource(R.string.process_system_details),
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

        if (process.cmdline.isNotEmpty()) {
            StatCard(title = stringResource(R.string.process_command_line)) {
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

        SectionHeader(
            title = stringResource(R.string.process_threads),
            action = {
                Text(
                    text = stringResource(R.string.process_threads_count, threads.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (threadsLoading) {
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
        } else if (threads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.process_no_threads),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        } else {
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
    }
}

@Composable
private fun ProcessDetailHeader(
    process: ProcessInfo,
    onKill: (() -> Unit)? = null,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val appIcon = remember(process.packageName) {
        if (process.isAndroidApp) {
            try {
                val drawable = context.packageManager.getApplicationIcon(process.packageName)
                if (drawable is BitmapDrawable && drawable.bitmap.width >= 192) {
                    drawable.bitmap
                } else {
                    Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888).also { bmp ->
                        val canvas = Canvas(bmp)
                        drawable.setBounds(0, 0, 192, 192)
                        drawable.draw(canvas)
                    }
                }
            } catch (_: Exception) { null }
        } else null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconShape = RoundedCornerShape(12.dp)
        if (appIcon != null) {
            Image(
                bitmap = appIcon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(iconShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(iconShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = process.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "PID ${process.pid}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "  |  ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                ProcessStateBadge(state = process.state)
            }
        }

        if (onKill != null) {
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = { view.hapticClick(); onKill() },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text(stringResource(R.string.process_kill))
            }
        }
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
        Text(
            text = thread.tid.toString(),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
            ),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(56.dp),
        )

        Text(
            text = thread.name.ifEmpty { stringResource(R.string.process_unnamed_thread) },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "%.1f%%".format(thread.cpuLoadPercent),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = cpuColor(thread.cpuLoadPercent),
        )
    }
}

private fun cpuColor(cpuPercent: Double): Color {
    return when {
        cpuPercent < 5.0 -> ChartGreen
        cpuPercent <= 20.0 -> ChartYellow
        else -> ChartRed
    }
}
