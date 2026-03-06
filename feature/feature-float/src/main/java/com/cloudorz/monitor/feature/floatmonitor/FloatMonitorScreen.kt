package com.cloudorz.monitor.feature.floatmonitor

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle

@Composable
fun FloatMonitorScreen(
    viewModel: FloatMonitorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh permission and enabled state every time screen resumes
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshPermission()
        }
    }

    Scaffold { paddingValues ->
        FloatMonitorScreenContent(
            hasOverlayPermission = uiState.hasOverlayPermission,
            hasAccessibilityService = uiState.hasAccessibilityService,
            enabledMonitors = uiState.enabledMonitors,
            onToggleMonitor = viewModel::onToggleMonitor,
            onRequestOverlayPermission = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                )
                context.startActivity(intent)
            },
            onRequestAccessibility = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            },
            modifier = Modifier.padding(paddingValues),
        )
    }
}

@Composable
private fun FloatMonitorScreenContent(
    hasOverlayPermission: Boolean,
    hasAccessibilityService: Boolean,
    enabledMonitors: Set<FloatMonitorType>,
    onToggleMonitor: (FloatMonitorType, Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestAccessibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expandedInfo = remember { mutableStateMapOf<FloatMonitorType, Boolean>() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Title
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "悬浮监视器",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        // Monitor type cards
        items(
            items = FloatMonitorType.entries.toList(),
            key = { it.name },
        ) { monitorType ->
            val isEnabled = monitorType in enabledMonitors
            val isExpanded = expandedInfo[monitorType] == true

            MonitorTypeCard(
                monitorType = monitorType,
                isEnabled = isEnabled,
                isExpanded = isExpanded,
                onToggle = { enabled -> onToggleMonitor(monitorType, enabled) },
                onInfoClick = {
                    expandedInfo[monitorType] = !(expandedInfo[monitorType] ?: false)
                },
            )
        }

        // Permission section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            PermissionSection(
                hasOverlayPermission = hasOverlayPermission,
                hasAccessibilityService = hasAccessibilityService,
                onRequestOverlayPermission = onRequestOverlayPermission,
                onRequestAccessibility = onRequestAccessibility,
            )
        }
    }
}

@Composable
private fun MonitorTypeCard(
    monitorType: FloatMonitorType,
    isEnabled: Boolean,
    isExpanded: Boolean,
    onToggle: (Boolean) -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = monitorType.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = monitorType.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(onClick = onInfoClick) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "详细信息",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = monitorTypeInfo(monitorType),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.small,
                        )
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun PermissionSection(
    hasOverlayPermission: Boolean,
    hasAccessibilityService: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestAccessibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canShowOverlay = hasOverlayPermission || hasAccessibilityService

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (canShowOverlay) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            },
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (canShowOverlay) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "权限说明",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "悬浮窗显示支持两种方式：\n" +
                    "1. 无障碍服务（推荐）— 无需悬浮窗权限，同时提供前台应用检测\n" +
                    "2. 悬浮窗权限 — 传统方式，需手动授权",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Accessibility service status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (hasAccessibilityService) "无障碍服务已开启" else "无障碍服务未开启",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (hasAccessibilityService) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
                if (!hasAccessibilityService) {
                    Button(onClick = onRequestAccessibility) {
                        Text(text = "去开启")
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Overlay permission status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (hasOverlayPermission) "悬浮窗权限已授予" else "悬浮窗权限未授予",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (hasOverlayPermission) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
                if (!hasOverlayPermission) {
                    Button(onClick = onRequestOverlayPermission) {
                        Text(text = "去授权")
                    }
                }
            }
        }
    }
}

private fun monitorTypeInfo(type: FloatMonitorType): String = when (type) {
    FloatMonitorType.LOAD_MONITOR ->
        "显示 CPU、GPU、内存、电池的实时负载仪表盘。以 2x2 网格形式呈现四个圆弧仪表，直观展示系统资源使用情况。"

    FloatMonitorType.PROCESS_MONITOR ->
        "显示当前 CPU 占用率最高的 5 个进程列表，包含进程名称和 CPU 使用百分比。帮助快速定位高负载进程。"

    FloatMonitorType.THREAD_MONITOR ->
        "显示当前前台应用中 CPU 占用率最高的线程列表。用于深入分析应用性能瓶颈。"

    FloatMonitorType.MINI_MONITOR ->
        "极简单行模式，显示 CPU%/GPU%/温度/FPS/实时电流。占用屏幕空间最小，适合日常监控。"

    FloatMonitorType.FPS_RECORDER ->
        "实时帧率计数器，大字体显示当前 FPS 值，并统计卡顿 (Jank) 次数。适合游戏和动画性能测试。"

    FloatMonitorType.TEMPERATURE_MONITOR ->
        "显示设备多个温度传感器的实时读数，包括 CPU、GPU、电池等区域温度。用于监控设备散热状况。"
}
