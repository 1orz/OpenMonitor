package com.cloudorz.openmonitor.feature.fps

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudorz.openmonitor.core.data.datasource.FpsRecordingState
import com.cloudorz.openmonitor.core.model.fps.FpsWatchSession
import com.cloudorz.openmonitor.core.ui.R
import com.cloudorz.openmonitor.core.ui.hapticClick
import com.cloudorz.openmonitor.core.ui.theme.ChartGreen
import com.cloudorz.openmonitor.core.ui.theme.ChartRed
import com.cloudorz.openmonitor.core.ui.theme.ChartYellow
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FpsScreen(
    viewModel: FpsViewModel = hiltViewModel(),
    onSessionClick: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    FpsContent(
        uiState = uiState,
        onDeleteSession = viewModel::deleteSession,
        onRenameSession = viewModel::renameSession,
        onExportSession = { sessionId ->
            viewModel.getExportIntent(sessionId) { intent ->
                context.startActivity(intent)
            }
        },
        onToggleSelectionMode = viewModel::toggleSelectionMode,
        onExitSelectionMode = viewModel::exitSelectionMode,
        onToggleSelection = viewModel::toggleSelection,
        onSelectAll = { viewModel.selectAll(uiState.sessions) },
        onDeleteSelected = viewModel::deleteSelected,
        onSessionClick = onSessionClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FpsContent(
    uiState: FpsUiState,
    onDeleteSession: (Long) -> Unit,
    onRenameSession: (Long, String) -> Unit,
    onExportSession: (Long) -> Unit,
    onToggleSelectionMode: () -> Unit,
    onExitSelectionMode: () -> Unit,
    onToggleSelection: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onSessionClick: (String) -> Unit = {},
) {
    val view = LocalView.current
    Column(modifier = Modifier.fillMaxSize()) {
        if (uiState.isSelectionMode) {
            SelectionModeBar(
                selectedCount = uiState.selectedIds.size,
                onSelectAll = onSelectAll,
                onDeleteSelected = onDeleteSelected,
                onExitSelectionMode = onExitSelectionMode,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Recording status card (shown when recording or countdown)
            if (uiState.recordingState != FpsRecordingState.IDLE) {
                item(key = "recording_status") {
                    RecordingStatusCard(uiState)
                }
            }

            if (uiState.sessions.isEmpty() && uiState.recordingState == FpsRecordingState.IDLE) {
                item(key = "empty") {
                    EmptySessionsPlaceholder()
                }
            } else {
                items(
                    items = uiState.sessions,
                    key = { it.sessionId },
                ) { session ->
                    SessionCard(
                        session = session,
                        isSelectionMode = uiState.isSelectionMode,
                        isSelected = uiState.selectedIds.contains(session.sessionId.toLongOrNull() ?: 0L),
                        onDelete = { onDeleteSession(session.sessionId.toLongOrNull() ?: 0L) },
                        onExport = { onExportSession(session.sessionId.toLongOrNull() ?: 0L) },
                        onRename = { newDesc -> onRenameSession(session.sessionId.toLongOrNull() ?: 0L, newDesc) },
                        onClick = {
                            view.hapticClick()
                            val id = session.sessionId.toLongOrNull() ?: 0L
                            if (uiState.isSelectionMode) {
                                onToggleSelection(id)
                            } else {
                                onSessionClick(session.sessionId)
                            }
                        },
                        onLongClick = {
                            view.hapticClick()
                            if (!uiState.isSelectionMode) onToggleSelectionMode()
                            val id = session.sessionId.toLongOrNull() ?: 0L
                            onToggleSelection(id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionModeBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onExitSelectionMode: () -> Unit,
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(R.plurals.fps_selected_count, selectedCount, selectedCount),
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = { view.hapticClick(); onSelectAll() }) {
            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.fps_select_all))
        }
        IconButton(
            onClick = { view.hapticClick(); onDeleteSelected() },
            enabled = selectedCount > 0,
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = if (selectedCount > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }
        IconButton(onClick = { view.hapticClick(); onExitSelectionMode() }) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.fps_cancel))
        }
    }
}

@Composable
private fun RecordingStatusCard(uiState: FpsUiState) {
    val isCountdown = uiState.recordingState == FpsRecordingState.COUNTDOWN
    val isRecording = uiState.recordingState == FpsRecordingState.RECORDING

    val pulseTransition = rememberInfiniteTransition(label = "recPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recPulseAlpha",
    )

    val borderColor = if (isRecording) ChartRed else Color(0xFF2196F3)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = borderColor.copy(alpha = 0.08f),
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Pulsing dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(borderColor.copy(alpha = pulseAlpha)),
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isCountdown) {
                        stringResource(R.string.fps_countdown, uiState.recordingInfo.countdownSeconds)
                    } else {
                        stringResource(R.string.recording)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = borderColor,
                )
                if (isRecording) {
                    val info = uiState.recordingInfo
                    val elapsed = formatDuration(info.elapsedSeconds)
                    val limit = info.durationLimitSeconds
                    val remaining = if (limit > 0) formatDuration(info.remainingSeconds) else null
                    Text(
                        text = if (remaining != null) {
                            stringResource(R.string.fps_recording_progress, elapsed, remaining)
                        } else {
                            elapsed
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (info.avgFps > 0) {
                        Text(
                            text = stringResource(R.string.avg_fps_format, info.avgFps),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySessionsPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 80.dp),
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
                text = stringResource(R.string.fps_start_from_float),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionCard(
    session: FpsWatchSession,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onRename: (String) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val view = LocalView.current
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    @Suppress("AssignedValueIsNeverRead")
    var showRenameDialog by remember { mutableStateOf(false) }
    @Suppress("AssignedValueIsNeverRead")
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Selection checkbox or FPS indicator
            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            } else {
                SessionIcon(session)
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Session info
            Column(modifier = Modifier.weight(1f)) {
                // Title: sessionDesc if present, else appName/packageName
                val title = session.sessionDesc.ifEmpty {
                    session.appName.ifEmpty { session.packageName.ifEmpty { stringResource(R.string.fps_unknown_app) } }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Package name (if title is sessionDesc or appName)
                if (session.sessionDesc.isNotEmpty() || session.appName.isNotEmpty()) {
                    Text(
                        text = session.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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

            // Action buttons (only in normal mode)
            if (!isSelectionMode) {
                IconButton(onClick = { view.hapticClick(); showRenameDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.fps_rename),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                IconButton(onClick = { view.hapticClick(); onExport() }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "CSV",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    )
                }
                IconButton(onClick = { view.hapticClick(); showDeleteConfirm = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }

    // Rename dialog
    if (showRenameDialog) {
        RenameDialog(
            currentName = session.sessionDesc.ifEmpty {
                session.appName.ifEmpty { session.packageName }
            },
            onConfirm = { newName ->
                onRename(newName)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    // Delete confirm dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.fps_delete_confirm_title)) },
            text = { Text(stringResource(R.string.fps_delete_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    view.hapticClick()
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { view.hapticClick(); showDeleteConfirm = false }) {
                    Text(stringResource(R.string.fps_cancel))
                }
            },
        )
    }
}

@Composable
private fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    var text by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fps_rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.fps_session_name)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { view.hapticClick(); onConfirm(text.trim()) },
                enabled = text.trim().isNotEmpty(),
            ) {
                Text(stringResource(R.string.fps_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { view.hapticClick(); onDismiss() }) {
                Text(stringResource(R.string.fps_cancel))
            }
        },
    )
}

@Composable
private fun SessionIcon(session: FpsWatchSession) {
    val context = LocalContext.current
    val appIcon: android.graphics.drawable.Drawable? = remember(session.packageName) {
        if (session.packageName.isEmpty()) null
        else try { context.packageManager.getApplicationIcon(session.packageName) } catch (_: Exception) { null }
    }

    if (appIcon != null) {
        val bitmap = remember(appIcon) {
            if (appIcon is android.graphics.drawable.BitmapDrawable && appIcon.bitmap != null) {
                appIcon.bitmap
            } else {
                val bmp = createBitmap(192, 192, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                appIcon.setBounds(0, 0, 192, 192)
                appIcon.draw(canvas)
                bmp
            }
        }
        Image(
            painter = BitmapPainter(bitmap.asImageBitmap()),
            contentDescription = session.appName,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
        )
    } else {
        // Fallback: FPS circle
        Box(
            modifier = Modifier
                .size(44.dp)
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
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = when {
                    session.avgFps >= 50.0 -> ChartGreen
                    session.avgFps >= 30.0 -> ChartYellow
                    else -> ChartRed
                },
            )
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
