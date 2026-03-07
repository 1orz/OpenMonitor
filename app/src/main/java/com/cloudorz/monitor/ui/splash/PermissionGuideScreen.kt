package com.cloudorz.monitor.ui.splash

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cloudorz.monitor.R
import com.cloudorz.monitor.core.common.PermissionManager
import com.cloudorz.monitor.core.common.PrivilegeMode
import com.cloudorz.monitor.core.data.datasource.DaemonManager
import com.cloudorz.monitor.core.data.datasource.DaemonState

private enum class DetectState { IDLE, DETECTING, DONE, LAUNCHING_DAEMON, DAEMON_FAILED }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PermissionGuideScreen(
    permissionManager: PermissionManager,
    daemonManager: DaemonManager,
    onModeSelected: (PrivilegeMode) -> Unit,
) {
    var selectedMode by remember { mutableStateOf<PrivilegeMode?>(null) }
    var detectState by remember { mutableStateOf(DetectState.IDLE) }
    var detectedMode by remember { mutableStateOf<PrivilegeMode?>(null) }
    // True while waiting for user to respond to Shizuku permission dialog
    var waitingForShizuku by remember { mutableStateOf(false) }

    if (detectState == DetectState.DETECTING) {
        LaunchedEffect(Unit) {
            val best = permissionManager.detectBestMode()
            detectedMode = best
            selectedMode = best
            detectState = DetectState.DONE
        }
    }

    // Daemon launch step: triggered after user confirms ROOT/SHIZUKU
    if (detectState == DetectState.LAUNCHING_DAEMON) {
        LaunchedEffect(Unit) {
            val result = daemonManager.ensureRunning()
            if (result == DaemonState.RUNNING || result == DaemonState.NOT_NEEDED) {
                val mode = selectedMode ?: return@LaunchedEffect
                permissionManager.setMode(mode)
                onModeSelected(mode)
            } else {
                detectState = DetectState.DAEMON_FAILED
            }
        }
    }

    // Observe Shizuku permission dialog result
    val shizukuPermissionResult by permissionManager.shizukuPermissionResult.collectAsStateWithLifecycle()
    LaunchedEffect(shizukuPermissionResult) {
        if (!waitingForShizuku) return@LaunchedEffect
        val granted = shizukuPermissionResult ?: return@LaunchedEffect
        waitingForShizuku = false
        permissionManager.resetShizukuPermissionResult()
        if (granted) {
            // Shizuku authorized — now launch daemon before proceeding
            selectedMode = PrivilegeMode.SHIZUKU
            detectState = DetectState.LAUNCHING_DAEMON
        }
        // If denied: stay on guide screen; user can try again or choose another mode
    }

    // Auto-detection dialog
    if (detectState == DetectState.DETECTING) {
        DetectingDialog()
    }

    // Daemon launching dialog
    if (detectState == DetectState.LAUNCHING_DAEMON) {
        DaemonLaunchingDialog()
    }

    // Daemon launch failed dialog
    if (detectState == DetectState.DAEMON_FAILED) {
        DaemonFailedDialog(
            onRetry = { detectState = DetectState.LAUNCHING_DAEMON },
            onFallbackBasic = {
                detectState = DetectState.IDLE
                permissionManager.setMode(PrivilegeMode.BASIC)
                onModeSelected(PrivilegeMode.BASIC)
            },
        )
    }

    // Shizuku permission waiting dialog
    if (waitingForShizuku) {
        ShizukuWaitingDialog(onCancel = {
            waitingForShizuku = false
            permissionManager.resetShizukuPermissionResult()
        })
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // -- Header: Logo + Title --
            Spacer(modifier = Modifier.height(24.dp))

            Icon(
                imageVector = Icons.Filled.Monitor,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "System Monitor",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.guide_subtitle),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // -- Mode Cards --
            ModeCard(
                iconRes = R.drawable.ic_mode_root,
                title = stringResource(R.string.mode_root_title),
                description = stringResource(R.string.mode_root_desc),
                tagText = stringResource(R.string.mode_root_tag),
                tagColor = Color(0xFF4CAF50),
                isSelected = selectedMode == PrivilegeMode.ROOT,
                onClick = { selectedMode = PrivilegeMode.ROOT },
            )

            Spacer(modifier = Modifier.height(12.dp))

            ModeCard(
                iconRes = R.drawable.ic_mode_shizuku,
                title = stringResource(R.string.mode_shizuku_title),
                description = stringResource(R.string.mode_shizuku_desc),
                tagText = stringResource(R.string.mode_shizuku_tag),
                tagColor = Color(0xFF2196F3),
                isSelected = selectedMode == PrivilegeMode.SHIZUKU,
                onClick = { selectedMode = PrivilegeMode.SHIZUKU },
            )

            Spacer(modifier = Modifier.height(12.dp))

            ModeCard(
                iconRes = R.drawable.ic_mode_basic,
                title = stringResource(R.string.mode_basic_title),
                description = stringResource(R.string.mode_basic_desc),
                tagText = stringResource(R.string.mode_basic_tag),
                tagColor = Color(0xFF9E9E9E),
                isSelected = selectedMode == PrivilegeMode.BASIC,
                onClick = { selectedMode = PrivilegeMode.BASIC },
            )

            Spacer(modifier = Modifier.height(32.dp))

            // -- Auto-detect button --
            Button(
                onClick = { detectState = DetectState.DETECTING },
                modifier = Modifier.fillMaxWidth(),
                enabled = detectState != DetectState.DETECTING,
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.guide_auto_detect),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.guide_manual_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )

            // -- Confirm button (visible when a mode is selected) --
            if (selectedMode != null) {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val mode = selectedMode ?: return@Button
                        if (mode == PrivilegeMode.SHIZUKU) {
                            if (permissionManager.isShizukuAvailableSync()) {
                                // Permission granted — launch daemon before proceeding
                                detectState = DetectState.LAUNCHING_DAEMON
                            } else {
                                permissionManager.resetShizukuPermissionResult()
                                permissionManager.requestShizukuPermission()
                                waitingForShizuku = true
                            }
                        } else if (mode == PrivilegeMode.ROOT) {
                            // ROOT — launch daemon before proceeding
                            detectState = DetectState.LAUNCHING_DAEMON
                        } else {
                            // BASIC/ADB — no daemon needed
                            permissionManager.setMode(mode)
                            onModeSelected(mode)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.guide_confirm, selectedMode!!.displayName),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModeCard(
    iconRes: Int,
    title: String,
    description: String,
    tagText: String,
    tagColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "borderColor",
    )

    val elevation = if (isSelected) 4.dp else 1.dp

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 0.dp,
            color = borderColor,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color.Unspecified,
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = tagColor.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = tagText,
                        style = MaterialTheme.typography.labelSmall,
                        color = tagColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShizukuWaitingDialog(onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) { Text("取消") }
        },
        icon = {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
        },
        title = {
            Text(
                text = "等待 Shizuku 授权",
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = "请在弹出的 Shizuku 授权对话框中点击【允许】。\n\n若 Shizuku 未运行，请先启动 Shizuku 后重试。",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun DetectingDialog() {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        icon = {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
        },
        title = {
            Text(
                text = stringResource(R.string.guide_detecting_title),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.guide_detecting_desc),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun DaemonLaunchingDialog() {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        icon = {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
        },
        title = {
            Text(
                text = stringResource(R.string.daemon_launching),
                textAlign = TextAlign.Center,
            )
        },
    )
}

@Composable
private fun DaemonFailedDialog(
    onRetry: () -> Unit,
    onFallbackBasic: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            Button(onClick = onRetry) {
                Text(stringResource(R.string.daemon_retry))
            }
        },
        dismissButton = {
            TextButton(onClick = onFallbackBasic) {
                Text(stringResource(R.string.daemon_fallback_basic))
            }
        },
        title = {
            Text(
                text = stringResource(R.string.daemon_launch_failed),
                textAlign = TextAlign.Center,
            )
        },
    )
}
