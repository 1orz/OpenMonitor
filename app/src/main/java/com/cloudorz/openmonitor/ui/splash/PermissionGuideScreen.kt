package com.cloudorz.openmonitor.ui.splash

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.BorderStroke
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
import com.cloudorz.openmonitor.R
import com.cloudorz.openmonitor.core.ui.hapticClick
import androidx.compose.ui.platform.LocalView
import com.cloudorz.openmonitor.ui.component.AppIcon
import com.cloudorz.openmonitor.core.common.PermissionManager
import com.cloudorz.openmonitor.core.common.PrivilegeMode
import com.cloudorz.openmonitor.core.common.RootFrameworkDetector
import com.cloudorz.openmonitor.core.common.ShizukuVariantDetector
import com.cloudorz.openmonitor.core.data.ipc.MonitorLauncher
import kotlinx.coroutines.launch

private enum class GuideState { PROBING, DETECTED, LAUNCHING_DAEMON, DAEMON_FAILED }

@Composable
fun PermissionGuideScreen(
    permissionManager: PermissionManager,
    monitorLauncher: MonitorLauncher,
    onModeSelected: (PrivilegeMode) -> Unit,
) {
    val view = LocalView.current
    var guideState by remember { mutableStateOf(GuideState.PROBING) }
    var selectedMode by remember { mutableStateOf<PrivilegeMode?>(null) }
    var rootResult by remember { mutableStateOf(RootFrameworkDetector.Result()) }
    var shizukuResult by remember { mutableStateOf(ShizukuVariantDetector.Result()) }
    var waitingForShizuku by remember { mutableStateOf(false) }

    // Start parallel detection immediately on first composition
    LaunchedEffect(Unit) {
        val (root, shizuku) = permissionManager.detectParallel()
        rootResult = root
        shizukuResult = shizuku
        // Auto-select best recommendation so the Confirm button is ready
        selectedMode = when {
            root.isAvailable   -> PrivilegeMode.ROOT
            shizuku.isRunning  -> PrivilegeMode.SHIZUKU
            else               -> PrivilegeMode.BASIC
        }
        guideState = GuideState.DETECTED
    }

    // Server launch step
    if (guideState == GuideState.LAUNCHING_DAEMON) {
        LaunchedEffect(Unit) {
            val mode = selectedMode ?: return@LaunchedEffect
            permissionManager.setMode(mode)
            if (mode == PrivilegeMode.BASIC) {
                onModeSelected(mode)
                return@LaunchedEffect
            }
            val ok = withTimeoutOrNull(8_000L) { monitorLauncher.ensureRunning() }
            if (ok != null) {
                onModeSelected(mode)
            } else {
                guideState = GuideState.DAEMON_FAILED
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
            selectedMode = PrivilegeMode.SHIZUKU
            guideState = GuideState.LAUNCHING_DAEMON
        }
    }

    // Dialogs
    if (guideState == GuideState.LAUNCHING_DAEMON) {
        DaemonLaunchingDialog()
    }
    if (guideState == GuideState.DAEMON_FAILED) {
        DaemonFailedDialog(
            onRetry = { guideState = GuideState.LAUNCHING_DAEMON },
            onFallbackBasic = {
                guideState = GuideState.DETECTED
                permissionManager.setMode(PrivilegeMode.BASIC)
                onModeSelected(PrivilegeMode.BASIC)
            },
        )
    }
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            AppIcon(size = 72.dp)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "OpenMonitor",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (guideState == GuideState.PROBING) {
                    stringResource(R.string.guide_probing)
                } else {
                    stringResource(R.string.guide_select_mode)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(32.dp))

            when (guideState) {
                GuideState.PROBING -> {
                    repeat(4) {
                        ProbingCard()
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                else -> {
                    val recommended: PrivilegeMode? = when {
                        rootResult.isAvailable  -> PrivilegeMode.ROOT
                        shizukuResult.isRunning -> PrivilegeMode.SHIZUKU
                        else                    -> null
                    }
                    val badgeLabel = stringResource(R.string.guide_badge_recommended)

                    // ROOT card – show framework-specific icon when detected
                    val rootIcon = when (rootResult.framework) {
                        RootFrameworkDetector.Framework.MAGISK   -> R.drawable.ic_mode_magisk
                        RootFrameworkDetector.Framework.KERNELSU -> R.drawable.ic_mode_kernelsu
                        RootFrameworkDetector.Framework.APATCH   -> R.drawable.ic_mode_apatch
                        null -> R.drawable.ic_mode_root
                    }
                    DetectionCard(
                        iconRes = rootIcon,
                        title = stringResource(R.string.mode_root_title),
                        subtitle = buildRootSubtitle(rootResult),
                        tagText = stringResource(R.string.mode_root_tag),
                        tagColor = Color(0xFF4CAF50),
                        badge = if (recommended == PrivilegeMode.ROOT) badgeLabel else null,
                        isSelected = selectedMode == PrivilegeMode.ROOT,
                        onClick = { selectedMode = PrivilegeMode.ROOT },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // SHIZUKU card
                    DetectionCard(
                        iconRes = R.drawable.ic_mode_shizuku,
                        title = stringResource(R.string.mode_shizuku_title),
                        subtitle = buildShizukuSubtitle(shizukuResult),
                        tagText = stringResource(R.string.mode_shizuku_tag),
                        tagColor = Color(0xFF2196F3),
                        badge = if (recommended == PrivilegeMode.SHIZUKU) badgeLabel else null,
                        isSelected = selectedMode == PrivilegeMode.SHIZUKU,
                        onClick = { selectedMode = PrivilegeMode.SHIZUKU },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // ADB card
                    DetectionCard(
                        iconRes = R.drawable.ic_mode_adb,
                        title = stringResource(R.string.mode_adb),
                        subtitle = stringResource(R.string.mode_adb_subtitle),
                        tagText = stringResource(R.string.mode_shizuku_tag),
                        tagColor = Color(0xFFFF9800),
                        badge = null,
                        isSelected = selectedMode == PrivilegeMode.ADB,
                        onClick = { selectedMode = PrivilegeMode.ADB },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // BASIC card
                    DetectionCard(
                        iconRes = R.drawable.ic_mode_basic,
                        title = stringResource(R.string.mode_basic_title),
                        subtitle = stringResource(R.string.mode_basic_subtitle),
                        tagText = stringResource(R.string.mode_basic_tag),
                        tagColor = Color(0xFF9E9E9E),
                        badge = null,
                        isSelected = selectedMode == PrivilegeMode.BASIC,
                        onClick = { selectedMode = PrivilegeMode.BASIC },
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Confirm button
                    val mode = selectedMode
                    if (mode != null) {
                        Button(
                            onClick = {
                                view.hapticClick()
                                when (mode) {
                                    PrivilegeMode.SHIZUKU -> {
                                        if (permissionManager.isShizukuAvailableSync()) {
                                            guideState = GuideState.LAUNCHING_DAEMON
                                        } else {
                                            permissionManager.resetShizukuPermissionResult()
                                            permissionManager.requestShizukuPermission()
                                            waitingForShizuku = true
                                        }
                                    }
                                    PrivilegeMode.ROOT -> {
                                        guideState = GuideState.LAUNCHING_DAEMON
                                    }
                                    PrivilegeMode.ADB -> {
                                        permissionManager.setMode(mode)
                                        guideState = GuideState.LAUNCHING_DAEMON
                                    }
                                    PrivilegeMode.BASIC -> {
                                        permissionManager.setMode(mode)
                                        onModeSelected(mode)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.guide_use_mode, mode.displayName),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun buildRootSubtitle(result: RootFrameworkDetector.Result): String {
    // Strip any leading 'v' from versionName (e.g. "v3.2.4" → "v3.2.4", not "vv3.2.4")
    val ver = result.version?.let { " v${it.trimStart('v', 'V')}" } ?: ""
    val frameworkName = when (result.framework) {
        RootFrameworkDetector.Framework.MAGISK   -> "Magisk$ver"
        RootFrameworkDetector.Framework.KERNELSU -> "KernelSU$ver"
        RootFrameworkDetector.Framework.APATCH   -> "APatch$ver"
        null -> null
    }
    return when {
        frameworkName != null  -> stringResource(R.string.mode_root_detected, frameworkName)
        result.hasSuBinary     -> stringResource(R.string.mode_root_su_detected)
        else                   -> stringResource(R.string.mode_root_not_detected)
    }
}

@Composable
private fun buildShizukuSubtitle(result: ShizukuVariantDetector.Result): String {
    if (!result.isRunning) {
        return if (result.isShizukuInstalled) {
            stringResource(R.string.mode_shizuku_installed_not_running)
        } else {
            stringResource(R.string.mode_shizuku_not_installed)
        }
    }
    val ver = result.version?.let { "v${it.trimStart('v', 'V')}" } ?: ""
    val authLabel = when (result.authMethod) {
        ShizukuVariantDetector.AuthMethod.ROOT -> stringResource(R.string.mode_shizuku_auth_root)
        ShizukuVariantDetector.AuthMethod.ADB  -> stringResource(R.string.mode_shizuku_auth_adb)
        null -> ""
    }
    val parts = listOfNotNull(ver.takeIf { it.isNotEmpty() }, authLabel.takeIf { it.isNotEmpty() })
    val suffix = if (parts.isNotEmpty()) " · ${parts.joinToString(" · ")}" else ""
    val base = stringResource(R.string.mode_shizuku_running) + suffix
    return if (result.isAuthorized) base
    else "$base（${stringResource(R.string.mode_shizuku_pending)}）"
}

// ── Probing skeleton card ─────────────────────────────────────────────────────

@Composable
private fun ProbingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            )
            Text(
                text = stringResource(R.string.settings_detecting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

// ── Detection result card ─────────────────────────────────────────────────────

@Composable
private fun DetectionCard(
    iconRes: Int,
    title: String,
    subtitle: String,
    tagText: String,
    tagColor: Color,
    badge: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "borderColor",
    )

    Card(
        onClick = { view.hapticClick(); onClick() },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (badge != null) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
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

// ── Dialogs ───────────────────────────────────────────────────────────────────

@Composable
private fun ShizukuWaitingDialog(onCancel: () -> Unit) {
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { view.hapticClick(); onCancel() }) { Text(stringResource(android.R.string.cancel)) }
        },
        icon = {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
        },
        title = {
            Text(text = stringResource(R.string.shizuku_wait_title), textAlign = TextAlign.Center)
        },
        text = {
            Text(
                text = stringResource(R.string.shizuku_wait_desc),
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
            Text(text = stringResource(R.string.daemon_launching), textAlign = TextAlign.Center)
        },
    )
}

@Composable
private fun DaemonFailedDialog(
    onRetry: () -> Unit,
    onFallbackBasic: () -> Unit,
) {
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            Button(onClick = { view.hapticClick(); onRetry() }) { Text(stringResource(R.string.daemon_retry)) }
        },
        dismissButton = {
            TextButton(onClick = { view.hapticClick(); onFallbackBasic() }) {
                Text(stringResource(R.string.daemon_fallback_basic))
            }
        },
        title = {
            Text(text = stringResource(R.string.daemon_launch_failed), textAlign = TextAlign.Center)
        },
    )
}
