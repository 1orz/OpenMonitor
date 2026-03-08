package com.cloudorz.monitor.feature.fps

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudorz.monitor.core.ui.R
import com.cloudorz.monitor.core.model.fps.FpsData
import com.cloudorz.monitor.core.model.fps.FpsWatchSession
import com.cloudorz.monitor.core.ui.theme.ChartGreen
import com.cloudorz.monitor.core.ui.theme.ChartRed
import com.cloudorz.monitor.core.ui.theme.ChartYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FpsScreen(
    viewModel: FpsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    FpsContent(
        uiState = uiState,
        onStartRecording = { viewModel.startRecording() },
        onStopRecording = viewModel::stopRecording,
        onDeleteSession = viewModel::deleteSession,
        onExportSession = { sessionId ->
            viewModel.getExportIntent(sessionId) { intent ->
                context.startActivity(intent)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FpsContent(
    uiState: FpsUiState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onDeleteSession: (Long) -> Unit,
    onExportSession: (Long) -> Unit = {},
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(stringResource(R.string.tab_realtime_recording), stringResource(R.string.tab_session_history))

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.fps_recording_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }

            when (selectedTab) {
                0 -> RealtimeRecordingTab(
                    uiState = uiState,
                    onStartRecording = onStartRecording,
                    onStopRecording = onStopRecording,
                )
                1 -> SessionHistoryTab(
                    sessions = uiState.sessions,
                    onDeleteSession = onDeleteSession,
                    onExportSession = onExportSession,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tab 1: Real-time recording
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RealtimeRecordingTab(
    uiState: FpsUiState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    // Daemon 未运行时显示占位提示
    if (!uiState.hasDaemon) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🔒",
                    fontSize = 48.sp,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.shell_permission_required),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.fps_shell_permission_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                listOf(stringResource(R.string.fps_mode_root), stringResource(R.string.fps_mode_shizuku)).forEach { mode ->
                    Text(
                        text = "• $mode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .padding(vertical = 2.dp),
                    )
                }
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Large FPS counter
            FpsCounterDisplay(
                fps = uiState.currentFps?.fps,
                isRecording = uiState.isRecording,
            )

            // FPS chart
            if (uiState.fpsHistory.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.fps_trend),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FpsDataChart(
                            fpsHistory = uiState.fpsHistory,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // Frame time chart
            if (uiState.currentFps != null && uiState.currentFps.frameTimesMs.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.frame_time),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FrameTimeChart(
                            frameTimes = uiState.currentFps.frameTimesMs,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // Stats row
            if (uiState.currentFps != null) {
                FpsStatsRow(fpsData = uiState.currentFps)
            }

            // Overlay metrics
            if (uiState.isRecording) {
                OverlayMetricsRow(
                    cpuLoad = uiState.cpuLoad,
                    temperature = uiState.temperature,
                    batteryLevel = uiState.batteryLevel,
                )
            }

            // Space for FAB
            Spacer(modifier = Modifier.height(80.dp))
        }

        // Record button
        RecordButton(
            isRecording = uiState.isRecording,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun FpsCounterDisplay(
    fps: Double?,
    isRecording: Boolean,
) {
    val displayFps = fps ?: 0.0
    val fpsColor by animateColorAsState(
        targetValue = when {
            !isRecording || fps == null -> MaterialTheme.colorScheme.outline
            displayFps >= 50.0 -> ChartGreen
            displayFps >= 30.0 -> ChartYellow
            else -> ChartRed
        },
        animationSpec = tween(300),
        label = "fpsColorAnimation",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (isRecording && fps != null) "%.1f".format(displayFps) else "--",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = fpsColor,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "FPS",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            if (isRecording) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    val pulseTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseAlpha by pulseTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "pulseAlpha",
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ChartRed.copy(alpha = pulseAlpha)),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.recording),
                        style = MaterialTheme.typography.labelMedium,
                        color = ChartRed.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FpsStatsRow(fpsData: FpsData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatItem(
                label = "Jank",
                value = "${fpsData.jankCount}",
                color = if (fpsData.jankCount > 0) ChartYellow else ChartGreen,
            )
            StatItem(
                label = "Big Jank",
                value = "${fpsData.bigJankCount}",
                color = if (fpsData.bigJankCount > 0) ChartRed else ChartGreen,
            )
            StatItem(
                label = stringResource(R.string.max_frame_time),
                value = "${fpsData.maxFrameTimeMs}ms",
                color = when {
                    fpsData.maxFrameTimeMs > 32 -> ChartRed
                    fpsData.maxFrameTimeMs > 16 -> ChartYellow
                    else -> ChartGreen
                },
            )
            StatItem(
                label = stringResource(R.string.frame_count),
                value = "${fpsData.frameCount}",
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun OverlayMetricsRow(
    cpuLoad: Double,
    temperature: Double,
    batteryLevel: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MetricItem(
                icon = Icons.Default.Memory,
                label = "CPU",
                value = "%.1f%%".format(cpuLoad),
                tint = when {
                    cpuLoad > 80.0 -> ChartRed
                    cpuLoad > 50.0 -> ChartYellow
                    else -> ChartGreen
                },
            )
            MetricItem(
                icon = Icons.Default.Thermostat,
                label = stringResource(R.string.temperature),
                value = "%.1f\u00B0C".format(temperature),
                tint = when {
                    temperature > 45.0 -> ChartRed
                    temperature > 38.0 -> ChartYellow
                    else -> ChartGreen
                },
            )
            MetricItem(
                icon = Icons.Default.BatteryFull,
                label = stringResource(R.string.battery_level),
                value = "$batteryLevel%",
                tint = when {
                    batteryLevel < 20 -> ChartRed
                    batteryLevel < 50 -> ChartYellow
                    else -> ChartGreen
                },
            )
        }
    }
}

@Composable
private fun MetricItem(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun RecordButton(
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scaleTransition = rememberInfiniteTransition(label = "recordScale")
    val scale by scaleTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recordButtonScale",
    )

    FloatingActionButton(
        onClick = if (isRecording) onStopRecording else onStartRecording,
        modifier = modifier
            .size(72.dp)
            .then(if (isRecording) Modifier.scale(scale) else Modifier)
            .border(
                width = if (isRecording) 3.dp else 0.dp,
                color = if (isRecording) ChartRed.copy(alpha = 0.5f) else Color.Transparent,
                shape = CircleShape,
            ),
        containerColor = if (isRecording) ChartRed else MaterialTheme.colorScheme.primary,
        contentColor = Color.White,
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 8.dp,
        ),
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
            contentDescription = if (isRecording) stringResource(R.string.stop_recording) else stringResource(R.string.start_recording),
            modifier = Modifier.size(32.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Tab 2: Session history
// ---------------------------------------------------------------------------

@Composable
private fun SessionHistoryTab(
    sessions: List<FpsWatchSession>,
    onDeleteSession: (Long) -> Unit,
    onExportSession: (Long) -> Unit = {},
) {
    if (sessions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.no_recording_sessions),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = stringResource(R.string.start_recording_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        ) {
            items(
                items = sessions,
                key = { it.sessionId },
            ) { session ->
                SessionCard(
                    session = session,
                    onDelete = { onDeleteSession(session.sessionId.toLongOrNull() ?: 0L) },
                    onExport = { onExportSession(session.sessionId.toLongOrNull() ?: 0L) },
                )
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: FpsWatchSession,
    onDelete: () -> Unit,
    onExport: () -> Unit = {},
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // FPS indicator
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            session.avgFps >= 50.0 -> ChartGreen.copy(alpha = 0.15f)
                            session.avgFps >= 30.0 -> ChartYellow.copy(alpha = 0.15f)
                            else -> ChartRed.copy(alpha = 0.15f)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "%.0f".format(session.avgFps),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        session.avgFps >= 50.0 -> ChartGreen
                        session.avgFps >= 30.0 -> ChartYellow
                        else -> ChartRed
                    },
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Session info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.appName.ifEmpty { session.packageName },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (session.appName.isNotEmpty()) {
                    Text(
                        text = session.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.avg_fps_format, session.avgFps),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                    Text(
                        text = formatDuration(session.durationSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
                Text(
                    text = dateFormat.format(Date(session.beginTime)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                )
            }

            IconButton(onClick = onExport) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "CSV",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                )
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> "%d:%02d:%02d".format(hours, minutes, secs)
        minutes > 0 -> "%d:%02d".format(minutes, secs)
        else -> "${secs}s"
    }
}
