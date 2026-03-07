package com.cloudorz.monitor.ui.user

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cloudorz.monitor.core.common.PermissionManager
import com.cloudorz.monitor.core.common.PrivilegeMode
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
                permissionManager.setMode(PrivilegeMode.SHIZUKU)
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

        // Current mode card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .weight(1f),
                ) {
                    Text(
                        text = "当前运行模式",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = modeDisplayName(currentMode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (currentMode != PrivilegeMode.BASIC) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        // Mode switch card
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
                Text(
                    text = "切换运行模式",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                PrivilegeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = currentMode == mode,
                            onClick = {
                                if (currentMode == mode || isDetecting) return@RadioButton
                                when (mode) {
                                    PrivilegeMode.ROOT -> {
                                        isDetecting = true
                                        coroutineScope.launch {
                                            val detected = permissionManager.detectBestMode()
                                            if (detected != PrivilegeMode.ROOT) {
                                                permissionManager.setMode(currentMode)
                                            }
                                            isDetecting = false
                                        }
                                    }
                                    PrivilegeMode.SHIZUKU -> {
                                        when (shizukuStatus) {
                                            ShizukuStatus.GRANTED -> {
                                                permissionManager.setMode(PrivilegeMode.SHIZUKU)
                                            }
                                            ShizukuStatus.NOT_GRANTED -> {
                                                try { Shizuku.requestPermission(1001) } catch (_: Exception) {}
                                            }
                                            ShizukuStatus.NOT_RUNNING -> {
                                                // Show instructions (handled below)
                                            }
                                        }
                                    }
                                    else -> permissionManager.setMode(mode)
                                }
                            },
                            enabled = !isDetecting,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = modeDisplayName(mode),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (currentMode == mode) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = modeDescription(mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }

                if (isDetecting) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = "正在检测权限...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Shizuku setup guide
        ShizukuSetupCard(shizukuStatus)

        // Daemon connectivity card
        DaemonStatusCard(
            status = daemonStatus,
            onCheck = { viewModel.checkDaemon() },
        )

        // App info card
        Card(
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
                        text = "CloudMonitor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "版本 1.0.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShizukuSetupCard(status: ShizukuStatus) {
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
                Text(
                    text = "Shizuku 状态",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                val (statusText, statusColor) = when (status) {
                    ShizukuStatus.GRANTED -> "已授权" to MaterialTheme.colorScheme.primary
                    ShizukuStatus.NOT_GRANTED -> "未授权" to MaterialTheme.colorScheme.error
                    ShizukuStatus.NOT_RUNNING -> "未运行" to MaterialTheme.colorScheme.outline
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            when (status) {
                ShizukuStatus.GRANTED -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Shizuku 服务已连接，可使用 Shell 权限功能",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ShizukuStatus.NOT_GRANTED -> {
                    Text(
                        text = "Shizuku 服务正在运行，但本 App 尚未获得授权。\n请在上方选择 Shizuku 模式以请求授权。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ShizukuStatus.NOT_RUNNING -> {
                    Text(
                        text = "帧率监控等功能需要 Shell 权限，推荐通过 Shizuku 获取：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "步骤 1: 安装 Shizuku",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "从 Google Play 或 GitHub 安装 Shizuku App",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "步骤 2: 启动 Shizuku（任选一种）",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "方式 A — 无线调试：",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "设置 → 开发者选项 → 无线调试 → 配对\n在 Shizuku App 中配对后启动",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "方式 B — ADB 命令：",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "步骤 3: 回到本 App，选择 Shizuku 模式并授权",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = context.packageManager.getLaunchIntentForPackage(
                                    "moe.shizuku.privileged.api",
                                )
                                if (intent != null) {
                                    context.startActivity(intent)
                                } else {
                                    // Open Play Store
                                    val storeIntent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("market://details?id=moe.shizuku.privileged.api"),
                                    )
                                    context.startActivity(storeIntent)
                                }
                            } catch (_: Exception) {}
                        },
                    ) {
                        Text("打开/安装 Shizuku")
                    }
                }
            }
        }
    }
}

@Composable
private fun DaemonStatusCard(
    status: UserViewModel.DaemonStatus,
    onCheck: () -> Unit,
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
                    imageVector = Icons.Outlined.Cable,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Daemon 连接状态",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (status.checkedOnce) {
                    val (label, color) = if (status.connected) {
                        "已连接" to MaterialTheme.colorScheme.primary
                    } else {
                        "未连接" to MaterialTheme.colorScheme.error
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (status.checkedOnce && status.connected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (status.version != null) "版本：${status.version}" else "Daemon 运行中",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else if (status.checkedOnce && !status.connected) {
                Text(
                    text = "Daemon 未运行。ROOT / Shizuku 模式下打开悬浮监视器会自动启动。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Text(
                    text = "点击「手动检测」查看 Daemon 连通性（端口 127.0.0.1:9876）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onCheck,
                    enabled = !status.checking,
                ) {
                    if (status.checking) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("检测中...")
                    } else {
                        Text("手动检测")
                    }
                }
            }
        }
    }
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

private fun modeDisplayName(mode: PrivilegeMode): String = when (mode) {
    PrivilegeMode.ROOT -> "Root 模式"
    PrivilegeMode.ADB -> "ADB 模式"
    PrivilegeMode.SHIZUKU -> "Shizuku 模式"
    PrivilegeMode.BASIC -> "基础模式"
}

private fun modeDescription(mode: PrivilegeMode): String = when (mode) {
    PrivilegeMode.ROOT -> "Magisk / KernelSU，完整 Root 权限"
    PrivilegeMode.ADB -> "已弃用 — 请使用 Shizuku 模式"
    PrivilegeMode.SHIZUKU -> "通过 Shizuku 获取 Shell 权限（推荐）"
    PrivilegeMode.BASIC -> "无特权，帧率监控等功能不可用"
}
