package com.cloudorz.openmonitor.ui.user

import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.cloudorz.openmonitor.core.common.PermissionManager
import com.cloudorz.openmonitor.core.common.PrivilegeMode
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

@Composable
fun UserScreen(
    permissionManager: PermissionManager,
    viewModel: UserViewModel = hiltViewModel(),
) {
    val currentMode by permissionManager.currentMode.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var isDetecting by remember { mutableStateOf(false) }
    var shizukuStatus by remember { mutableStateOf(checkShizukuStatus()) }
    val daemonStatus by viewModel.daemonStatus.collectAsState()
    var showModeDropdown by remember { mutableStateOf(false) }

    // Listen for Shizuku binder changes
    DisposableEffect(Unit) {
        val binderListener = Shizuku.OnBinderReceivedListener {
            shizukuStatus = checkShizukuStatus()
        }
        val deadListener = Shizuku.OnBinderDeadListener {
            shizukuStatus = ShizukuStatus.NOT_RUNNING
        }
        val permListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            shizukuStatus = checkShizukuStatus()
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                val oldMode = permissionManager.currentMode.value
                isDetecting = true
                viewModel.switchMode(
                    oldMode, PrivilegeMode.SHIZUKU,
                    applyNewMode = { permissionManager.setMode(PrivilegeMode.SHIZUKU) },
                ) { isDetecting = false }
            }
        }
        try {
            Shizuku.addBinderReceivedListenerSticky(binderListener)
            Shizuku.addBinderDeadListener(deadListener)
            Shizuku.addRequestPermissionResultListener(permListener)
        } catch (_: Exception) {}

        onDispose {
            try {
                Shizuku.removeBinderReceivedListener(binderListener)
                Shizuku.removeBinderDeadListener(deadListener)
                Shizuku.removeRequestPermissionResultListener(permListener)
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        // Mode switch card — Dropdown Menu with icons
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isDetecting) { showModeDropdown = true }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = modeIcon(currentMode),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "切换运行模式",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = modeDisplayName(currentMode),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    if (isDetecting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                DropdownMenu(
                    expanded = showModeDropdown,
                    onDismissRequest = { showModeDropdown = false },
                ) {
                    PrivilegeMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = modeIcon(mode),
                                    contentDescription = null,
                                    tint = if (currentMode == mode)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            text = {
                                Text(
                                    text = modeDisplayName(mode),
                                    fontWeight = if (currentMode == mode) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            trailingIcon = {
                                if (currentMode == mode) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            },
                            onClick = {
                                showModeDropdown = false
                                if (currentMode == mode || isDetecting) return@DropdownMenuItem
                                val oldMode = currentMode
                                when (mode) {
                                    PrivilegeMode.ROOT -> {
                                        isDetecting = true
                                        coroutineScope.launch {
                                            val detected = permissionManager.detectBestMode()
                                            if (detected == PrivilegeMode.ROOT) {
                                                viewModel.switchMode(
                                                    oldMode, PrivilegeMode.ROOT,
                                                    applyNewMode = { permissionManager.setMode(PrivilegeMode.ROOT) },
                                                ) { isDetecting = false }
                                            } else {
                                                isDetecting = false
                                            }
                                        }
                                    }
                                    PrivilegeMode.SHIZUKU -> {
                                        when (shizukuStatus) {
                                            ShizukuStatus.GRANTED -> {
                                                isDetecting = true
                                                viewModel.switchMode(
                                                    oldMode, PrivilegeMode.SHIZUKU,
                                                    applyNewMode = { permissionManager.setMode(PrivilegeMode.SHIZUKU) },
                                                ) { isDetecting = false }
                                            }
                                            ShizukuStatus.NOT_GRANTED -> {
                                                try { Shizuku.requestPermission(1001) } catch (_: Exception) {}
                                            }
                                            ShizukuStatus.NOT_RUNNING -> { }
                                        }
                                    }
                                    PrivilegeMode.ADB -> {
                                        isDetecting = true
                                        viewModel.switchMode(
                                            oldMode, PrivilegeMode.ADB,
                                            applyNewMode = { permissionManager.setMode(PrivilegeMode.ADB) },
                                        ) { isDetecting = false }
                                    }
                                    PrivilegeMode.BASIC -> {
                                        isDetecting = true
                                        viewModel.switchMode(
                                            oldMode, PrivilegeMode.BASIC,
                                            applyNewMode = { permissionManager.setMode(PrivilegeMode.BASIC) },
                                        ) { isDetecting = false }
                                    }
                                }
                            },
                            enabled = !isDetecting,
                        )
                    }
                }
            }
        }

        // ADB mode: silent background polling until daemon connects
        DisposableEffect(currentMode) {
            if (currentMode == PrivilegeMode.ADB) {
                viewModel.startAdbWatcher()
            }
            onDispose { viewModel.stopAdbWatcher() }
        }

        // ADB setup guide (hidden once daemon connects)
        if (currentMode == PrivilegeMode.ADB && !daemonStatus.connected) {
            AdbSetupCard(
                binaryPath = viewModel.daemonBinaryPath,
                onCheck = { viewModel.checkDaemon() },
            )
        }

        // Daemon status card
        DaemonStatusCard(
            status = daemonStatus,
            onCheck = { viewModel.checkDaemon() },
            onRestart = { viewModel.restartDaemon() },
        )

        // Poll interval settings card
        val pollSettings by viewModel.pollSettings.collectAsState()
        PollSettingsCard(
            pollSettings = pollSettings,
            onIntervalSelected = viewModel::setPollInterval,
        )

        // Dark mode settings card
        val darkMode by viewModel.darkMode.collectAsState()
        DarkModeCard(
            darkMode = darkMode,
            onDarkModeSelected = viewModel::setDarkMode,
        )

        // App info card
        var showAboutDialog by remember { mutableStateOf(false) }
        if (showAboutDialog) {
            AboutDialog(onDismiss = { showAboutDialog = false })
        }

        Card(
            onClick = { showAboutDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .weight(1f),
                ) {
                    Text(
                        text = "OpenMonitor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "版本 ${com.cloudorz.openmonitor.BuildConfig.VERSION_NAME} · Author: 1orz · License: GPLv3",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = com.cloudorz.openmonitor.R.drawable.ic_github),
                    contentDescription = "GitHub",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DaemonStatusCard(
    status: UserViewModel.DaemonStatus,
    onCheck: () -> Unit,
    onRestart: () -> Unit,
) {
    var showVersionDialog by remember { mutableStateOf(false) }

    if (showVersionDialog) {
        VersionInfoDialog(
            expected = status.expectedCommit ?: "N/A",
            current = status.currentCommit ?: "N/A",
            isMatch = status.expectedCommit != null &&
                status.currentCommit != null &&
                status.currentCommit.contains(status.expectedCommit),
            onDismiss = { showVersionDialog = false },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Cable,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Daemon",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.width(10.dp))

            when {
                status.checking -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "检测中...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                status.checkedOnce && status.connected -> {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF4CAF50), CircleShape),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val info = listOfNotNull(
                        "运行中",
                        status.runner?.uppercase()?.ifEmpty { null },
                        status.uptimeSeconds?.let { formatUptime(it) }?.ifEmpty { null },
                    ).joinToString(" · ")
                    Text(
                        text = info,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // Version indicator
                    if (status.expectedCommit != null || status.currentCommit != null) {
                        val isMatch = status.expectedCommit != null &&
                            status.currentCommit != null &&
                            status.currentCommit.contains(status.expectedCommit)
                        IconButton(
                            onClick = { showVersionDialog = true },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = if (isMatch) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                contentDescription = null,
                                tint = if (isMatch) Color(0xFF4CAF50) else Color(0xFFFFA726),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                status.checkedOnce -> {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "未运行",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                }
                else -> {
                    Text(
                        text = "未检测",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            IconButton(
                onClick = if (status.connected) onRestart else onCheck,
                modifier = Modifier.size(32.dp),
                enabled = !status.checking,
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VersionInfoDialog(
    expected: String,
    current: String,
    isMatch: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("确定") }
        },
        icon = {
            Icon(
                imageVector = if (isMatch) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (isMatch) Color(0xFF4CAF50) else Color(0xFFFFA726),
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(if (isMatch) "版本一致" else "版本不一致")
        },
        text = {
            Column {
                Row {
                    Text(
                        text = "Expected: ",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = expected,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text(
                        text = "Current:   ",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = current,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
            }
        },
    )
}

private enum class ShizukuStatus {
    NOT_RUNNING, NOT_GRANTED, GRANTED,
}

private fun checkShizukuStatus(): ShizukuStatus {
    return try {
        if (!Shizuku.pingBinder()) {
            ShizukuStatus.NOT_RUNNING
        } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            ShizukuStatus.GRANTED
        } else {
            ShizukuStatus.NOT_GRANTED
        }
    } catch (_: Exception) {
        ShizukuStatus.NOT_RUNNING
    }
}

private fun modeIcon(mode: PrivilegeMode): ImageVector = when (mode) {
    PrivilegeMode.ROOT -> Icons.Filled.Security
    PrivilegeMode.SHIZUKU -> Icons.Filled.Speed
    PrivilegeMode.ADB -> Icons.Outlined.Cable
    PrivilegeMode.BASIC -> Icons.Filled.Info
}

private fun modeDisplayName(mode: PrivilegeMode): String = when (mode) {
    PrivilegeMode.ROOT -> "Root 模式"
    PrivilegeMode.ADB -> "ADB 模式"
    PrivilegeMode.SHIZUKU -> "Shizuku 模式"
    PrivilegeMode.BASIC -> "基础模式"
}

private fun modeDescription(mode: PrivilegeMode): String = when (mode) {
    PrivilegeMode.ROOT -> "Magisk / KernelSU，完整 Root 权限"
    PrivilegeMode.ADB -> "通过 ADB 手动启动 Daemon（FPS/进程等可用）"
    PrivilegeMode.SHIZUKU -> "通过 Shizuku 获取 Shell 权限（推荐）"
    PrivilegeMode.BASIC -> "无特权，帧率监控等功能不可用"
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, "https://github.com/1orz/OpenMonitor".toUri()),
                    )
                } catch (_: Exception) {}
            }) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = com.cloudorz.openmonitor.R.drawable.ic_github),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("GitHub")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        title = {
            Text("OpenMonitor")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AboutRow("Version", com.cloudorz.openmonitor.BuildConfig.VERSION_NAME)
                AboutRow("Commit", com.cloudorz.openmonitor.BuildConfig.GIT_COMMIT)
                AboutRow("Author", "1orz")
                AboutRow("License", "GPLv3")
            }
        },
    )
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PollSettingsCard(
    pollSettings: UserViewModel.PollSettings,
    onIntervalSelected: (Long) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Speed,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "采样设置",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "数据刷新间隔（所有悬浮窗）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(200L, 500L, 1000L, 2000L).forEach { interval ->
                    val label = if (interval < 1000) "${interval}ms" else "${interval / 1000}s"
                    FilterChip(
                        selected = pollSettings.intervalMs == interval,
                        onClick = { onIntervalSelected(interval) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun AdbSetupCard(
    binaryPath: String,
    onCheck: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ADB Daemon 设置",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "在电脑终端执行以下命令启动 Daemon：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            val adbCommand = "adb shell $binaryPath"
            Text(
                text = adbCommand,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("adb command", adbCommand))
                }) {
                    Text("复制命令")
                }
                OutlinedButton(onClick = onCheck) {
                    Text("检测连接")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "启动后将自动检测连接",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun DarkModeCard(
    darkMode: Int,
    onDarkModeSelected: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Brightness4,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "显示模式",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("跟随系统" to 0, "日间" to 1, "深色" to 2).forEach { (label, value) ->
                    FilterChip(
                        selected = darkMode == value,
                        onClick = { onDarkModeSelected(value) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        }
    }
}

private fun formatUptime(seconds: Long): String {
    val d = seconds / 86400
    val h = (seconds % 86400) / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        d > 0 -> "${d}d ${h}h ${m}m"
        h > 0 -> "${h}h ${m}m ${s}s"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}
