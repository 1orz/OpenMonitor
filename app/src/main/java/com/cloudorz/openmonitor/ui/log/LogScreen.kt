package com.cloudorz.openmonitor.ui.log

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudorz.openmonitor.core.common.AppLogEntry
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    viewModel: LogViewModel = hiltViewModel(),
    onProvideTopBarActions: (@Composable () -> Unit) -> Unit = {},
) {
    val appLogs by viewModel.appLogs.collectAsStateWithLifecycle()
    val daemonLogs by viewModel.daemonLogs.collectAsStateWithLifecycle()
    val daemonLogStatus by viewModel.daemonLogStatus.collectAsStateWithLifecycle()
    val logDates by viewModel.logDates.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val daemonLogDates by viewModel.daemonLogDates.collectAsStateWithLifecycle()
    val selectedDaemonDate by viewModel.selectedDaemonDate.collectAsStateWithLifecycle()
    val filterLevel by viewModel.filterLevel.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var autoScroll by remember { mutableStateOf(true) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val appListState = rememberLazyListState()
    val daemonListState = rememberLazyListState()

    // Auto-pause when user scrolls up manually
    val appIsAtBottom by remember {
        derivedStateOf {
            val info = appListState.layoutInfo
            if (info.totalItemsCount == 0) true
            else {
                val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
                last.index >= info.totalItemsCount - 2
            }
        }
    }
    val daemonIsAtBottom by remember {
        derivedStateOf {
            val info = daemonListState.layoutInfo
            if (info.totalItemsCount == 0) true
            else {
                val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
                last.index >= info.totalItemsCount - 2
            }
        }
    }

    // Re-enable autoScroll when user scrolls back to bottom
    LaunchedEffect(appIsAtBottom) { if (appIsAtBottom) autoScroll = true }
    LaunchedEffect(daemonIsAtBottom) { if (daemonIsAtBottom) autoScroll = true }

    // Only auto-scroll if user is at the bottom
    LaunchedEffect(appLogs.size) {
        if (autoScroll && appIsAtBottom && selectedTab == 0 && appLogs.isNotEmpty()) {
            appListState.animateScrollToItem(appLogs.size - 1)
        }
    }
    LaunchedEffect(daemonLogs.size) {
        if (autoScroll && daemonIsAtBottom && selectedTab == 1 && daemonLogs.isNotEmpty()) {
            daemonListState.animateScrollToItem(daemonLogs.size - 1)
        }
    }

    // Provide action buttons to shared TopAppBar
    val actions: @Composable () -> Unit = {
        IconButton(onClick = {
            autoScroll = !autoScroll
            if (autoScroll) {
                scope.launch {
                    val state = if (selectedTab == 0) appListState else daemonListState
                    val count = if (selectedTab == 0) appLogs.size else daemonLogs.size
                    if (count > 0) state.animateScrollToItem(count - 1)
                }
            }
        }) {
            Icon(
                imageVector = if (autoScroll) Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle,
                contentDescription = if (autoScroll) "暂停自动滚动" else "恢复自动滚动",
                tint = if (autoScroll) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selectedTab == 1) {
            IconButton(onClick = { viewModel.refreshDaemonLogs() }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "立即刷新")
            }
        }
        IconButton(onClick = {
            val text = if (selectedTab == 0) {
                appLogs.joinToString("\n") { "${it.time} ${levelName(it.level)}/${it.tag}: ${it.message}" }
            } else {
                daemonLogs.joinToString("\n")
            }
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("logs", text))) }
        }) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = "复制全部")
        }
        IconButton(onClick = {
            if (selectedTab == 0) viewModel.clearAppLogs() else viewModel.clearDaemonLogs()
        }) {
            Icon(Icons.Outlined.ClearAll, contentDescription = "清空")
        }
    }
    LaunchedEffect(Unit) { onProvideTopBarActions(actions) }
    DisposableEffect(Unit) { onDispose { onProvideTopBarActions {} } }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("App 日志") },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Daemon 日志") },
            )
        }

        FilterBar(
            dates = if (selectedTab == 0) logDates else daemonLogDates,
            selectedDate = if (selectedTab == 0) selectedDate else selectedDaemonDate,
            onSelectDate = { if (selectedTab == 0) viewModel.selectDate(it) else viewModel.selectDaemonDate(it) },
            filterLevel = filterLevel,
            onFilterLevel = { viewModel.setFilterLevel(it) },
            showDateSelector = true,
            realtimeLabel = if (selectedTab == 0) "实时 (logcat)" else "实时",
        )

        when (selectedTab) {
            0 -> AppLogTab(entries = appLogs, listState = appListState)
            1 -> DaemonLogTab(lines = daemonLogs, status = daemonLogStatus, listState = daemonListState)
        }
    }
}

@Composable
private fun FilterBar(
    dates: List<String>,
    selectedDate: String?,
    onSelectDate: (String?) -> Unit,
    filterLevel: LogLevelFilter,
    onFilterLevel: (LogLevelFilter) -> Unit,
    showDateSelector: Boolean,
    realtimeLabel: String = "实时 (logcat)",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showDateSelector) {
            var expanded by remember { mutableStateOf(false) }
            val label = selectedDate ?: "实时"

            Text(
                "来源:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                Text(
                    text = label,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { expanded = true }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text(realtimeLabel, fontWeight = if (selectedDate == null) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { onSelectDate(null); expanded = false },
                    )
                    dates.forEach { date ->
                        DropdownMenuItem(
                            text = { Text(date, fontWeight = if (date == selectedDate) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { onSelectDate(date); expanded = false },
                        )
                    }
                }
            }
        }

        // Level filter dropdown
        Text(
            "级别:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        var levelExpanded by remember { mutableStateOf(false) }
        Box {
            Text(
                text = filterLevel.displayName,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(levelColor(filterLevel).copy(alpha = 0.2f))
                    .clickable { levelExpanded = true }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = levelColor(filterLevel),
            )
            DropdownMenu(expanded = levelExpanded, onDismissRequest = { levelExpanded = false }) {
                LogLevelFilter.entries.forEach { level ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                level.displayName,
                                color = levelColor(level),
                                fontWeight = if (level == filterLevel) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        onClick = { onFilterLevel(level); levelExpanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun levelColor(level: LogLevelFilter): Color = when (level) {
    LogLevelFilter.VERBOSE -> MaterialTheme.colorScheme.outline
    LogLevelFilter.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    LogLevelFilter.INFO -> MaterialTheme.colorScheme.primary
    LogLevelFilter.WARN -> Color(0xFFFFAB40)
    LogLevelFilter.ERROR -> Color(0xFFFF5252)
}

private fun levelName(c: Char): String = when (c) {
    'V' -> "VERBOSE"
    'D' -> "DEBUG"
    'I' -> "INFO"
    'W' -> "WARN"
    'E' -> "ERROR"
    'F' -> "FATAL"
    else -> c.toString()
}

@Composable
private fun AppLogTab(
    entries: List<AppLogEntry>,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    if (entries.isEmpty()) {
        EmptyState("暂无日志")
        return
    }
    SelectionContainer {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            itemsIndexed(entries) { _, entry ->
                AppLogRow(entry)
            }
        }
    }
}

@Composable
private fun DaemonLogTab(
    lines: List<String>,
    status: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    if (lines.isEmpty()) {
        EmptyState(status ?: "暂无 Daemon 日志")
        return
    }
    SelectionContainer {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            itemsIndexed(lines) { _, line ->
                DaemonLogRow(line)
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppLogRow(entry: AppLogEntry) {
    val color = when (entry.level) {
        'E' -> Color(0xFFFF5252)
        'W' -> Color(0xFFFFAB40)
        'I' -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = "${entry.time} ${levelName(entry.level)}/${entry.tag}: ${entry.message}",
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = color,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DaemonLogRow(line: String) {
    val color = when {
        line.contains(" ERROR ") -> Color(0xFFFF5252)
        line.contains(" WARN  ") -> Color(0xFFFFAB40)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = line,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = color,
        modifier = Modifier.fillMaxWidth(),
    )
}
