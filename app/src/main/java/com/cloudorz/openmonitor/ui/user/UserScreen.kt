package com.cloudorz.openmonitor.ui.user

import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import com.cloudorz.openmonitor.core.ui.hapticClickable
import com.cloudorz.openmonitor.core.ui.hapticClick
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.material.icons.filled.Language
import com.cloudorz.openmonitor.BuildConfig
import com.cloudorz.openmonitor.R
import com.cloudorz.openmonitor.ui.util.UpdateChecker
import com.cloudorz.openmonitor.ui.util.UpdateInfo
import com.cloudorz.openmonitor.core.common.PermissionManager
import com.cloudorz.openmonitor.core.common.PrivilegeMode
import com.cloudorz.openmonitor.ui.component.SettingsDropdownItem
import com.cloudorz.openmonitor.ui.component.SettingsGroup
import com.cloudorz.openmonitor.ui.component.SettingsNavigateItem
import com.cloudorz.openmonitor.ui.component.SettingsSwitchItem
import androidx.compose.material.icons.filled.Favorite
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

@Composable
fun UserScreen(
    permissionManager: PermissionManager,
    onNavigateToLicenses: () -> Unit = {},
    onNavigateToTheme: () -> Unit = {},
    onNavigateToDonate: () -> Unit = {},
    viewModel: UserViewModel = hiltViewModel(),
) {
    val view = LocalView.current
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
            .padding(horizontal = 16.dp),
    ) {
        // ── System ──
        val modeNames = PrivilegeMode.entries.map { @Composable { modeDisplayName(it) } }
        val modeLabels = modeNames.map { @Composable { it() } }

        SettingsGroup(
            title = stringResource(R.string.settings_group_system),
            items = buildList {
                // Mode selector
                add {
                    ModeDropdownItem(
                        currentMode = currentMode,
                        isDetecting = isDetecting,
                        onModeSelected = { mode ->
                            if (currentMode == mode || isDetecting) return@ModeDropdownItem
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
                    )
                }
                // Daemon status
                add {
                    DaemonStatusItem(
                        status = daemonStatus,
                        onCheck = { viewModel.checkDaemon() },
                        onRestart = { viewModel.restartDaemon() },
                    )
                }
            },
        )

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

        // ── Appearance ──
        val hapticEnabled by viewModel.hapticEnabled.collectAsState()
        val pollSettings by viewModel.pollSettings.collectAsState()
        val pollIntervals = listOf(200L, 500L, 1000L, 2000L)
        val pollLabels = pollIntervals.map { if (it < 1000) "${it}ms" else "${it / 1000}s" }
        val pollSelectedIndex = pollIntervals.indexOf(pollSettings.intervalMs).coerceAtLeast(0)

        SettingsGroup(
            title = stringResource(R.string.settings_group_appearance),
            items = listOf(
                {
                    SettingsNavigateItem(
                        title = stringResource(R.string.settings_theme),
                        summary = stringResource(R.string.settings_theme_summary),
                        icon = Icons.Filled.Palette,
                        onClick = { onNavigateToTheme() },
                    )
                },
                { LanguageItem() },
            ),
        )

        // ── Monitor ──
        SettingsGroup(
            title = stringResource(R.string.settings_group_monitor),
            items = listOf(
                {
                    SettingsDropdownItem(
                        title = stringResource(R.string.settings_sampling),
                        icon = Icons.Filled.Speed,
                        items = pollLabels,
                        selectedIndex = pollSelectedIndex,
                        onItemSelected = { viewModel.setPollInterval(pollIntervals[it]) },
                    )
                },
                {
                    SettingsSwitchItem(
                        title = stringResource(R.string.settings_haptic_feedback),
                        icon = Icons.Filled.Vibration,
                        checked = hapticEnabled,
                        onCheckedChange = viewModel::setHapticEnabled,
                    )
                },
            ),
        )

        // ── Donate ──
        SettingsGroup(
            title = stringResource(R.string.donate_title),
            items = listOf {
                SettingsNavigateItem(
                    title = stringResource(R.string.donate_title),
                    summary = stringResource(R.string.donate_summary),
                    icon = Icons.Filled.Favorite,
                    onClick = onNavigateToDonate,
                )
            },
        )

        // ── About ──
        var showAboutDialog by remember { mutableStateOf(false) }
        if (showAboutDialog) {
            AboutDialog(onDismiss = { showAboutDialog = false })
        }

        // null = still checking, non-null = result
        val updateContext = LocalContext.current
        var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
        LaunchedEffect(Unit) {
            updateInfo = UpdateChecker.check(updateContext)
        }

        SettingsGroup(
            title = stringResource(R.string.settings_group_about),
            items = buildList {
                // Version + update status merged into one item
                add {
                    val info = updateInfo
                    val isChecking = info == null
                    val hasUpdate = info?.hasUpdate == true
                    val isError = info?.isError == true

                    val onItemClick: () -> Unit = if (hasUpdate) {
                        { UpdateChecker.openDownload(updateContext, info.downloadUrl) }
                    } else {
                        { showAboutDialog = true }
                    }

                    ListItem(
                        modifier = Modifier.hapticClickable(onClick = onItemClick),
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        ),
                        headlineContent = { Text("OpenMonitor") },
                        supportingContent = {
                            Row {
                                Text(
                                    text = stringResource(R.string.settings_version_current, BuildConfig.VERSION_NAME),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                                Text(
                                    text = "  ·  ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                                Text(
                                    text = when {
                                        isChecking -> stringResource(R.string.settings_detecting)
                                        isError -> stringResource(R.string.settings_check_failed)
                                        else -> stringResource(R.string.settings_version_latest, info.versionName)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        isChecking -> MaterialTheme.colorScheme.outline
                                        isError -> MaterialTheme.colorScheme.error
                                        hasUpdate -> MaterialTheme.colorScheme.primary
                                        else -> Color(0xFF4CAF50)
                                    },
                                )
                            }
                        },
                        leadingContent = {
                            Icon(Icons.Filled.Info, contentDescription = null)
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_github),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                            }
                        },
                    )
                }
                add {
                    SettingsNavigateItem(
                        title = stringResource(R.string.settings_open_source_licenses),
                        icon = Icons.Filled.Gavel,
                        onClick = { onNavigateToLicenses() },
                    )
                }
            },
        )
    }
}

@Composable
private fun ModeDropdownItem(
    currentMode: PrivilegeMode,
    isDetecting: Boolean,
    onModeSelected: (PrivilegeMode) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        modifier = Modifier.clickable(enabled = !isDetecting) {
            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
            expanded = true
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
            supportingColor = MaterialTheme.colorScheme.outline,
        ),
        headlineContent = { Text(stringResource(R.string.settings_switch_mode)) },
        supportingContent = { Text(modeDescription(currentMode)) },
        leadingContent = { Icon(modeIcon(currentMode), contentDescription = null) },
        trailingContent = {
            Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                if (isDetecting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = modeDisplayName(currentMode),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    PrivilegeMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            leadingIcon = { Icon(modeIcon(mode), null, modifier = Modifier.size(20.dp)) },
                            text = {
                                Text(
                                    modeDisplayName(mode),
                                    fontWeight = if (currentMode == mode) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                expanded = false
                                onModeSelected(mode)
                            },
                            enabled = !isDetecting,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun DaemonStatusItem(
    status: UserViewModel.DaemonStatus,
    onCheck: () -> Unit,
    onRestart: () -> Unit,
) {
    val view = LocalView.current
    var showVersionDialog by remember { mutableStateOf(false) }

    // Version match = daemon version equals app version (primary check)
    // Commit match = build-time expected commit matches daemon's actual commit (secondary)
    val appBaseVersion = BuildConfig.VERSION_NAME.substringBefore("-")
    val versionMatch = status.version != null && status.version == appBaseVersion
    val commitMatch = status.expectedCommit != null &&
        status.currentCommit != null &&
        status.currentCommit.contains(status.expectedCommit)

    if (showVersionDialog) {
        VersionInfoDialog(
            appVersion = BuildConfig.VERSION_NAME,
            daemonVersion = status.version ?: "N/A",
            expectedCommit = status.expectedCommit ?: "N/A",
            currentCommit = status.currentCommit ?: "N/A",
            versionMatch = versionMatch,
            commitMatch = commitMatch,
            onDismiss = { showVersionDialog = false },
        )
    }

    val summary = when {
        status.checking -> stringResource(R.string.settings_detecting)
        status.checkedOnce && status.connected -> listOfNotNull(
            stringResource(R.string.settings_running),
            status.runner?.uppercase()?.ifEmpty { null },
            status.uptimeSeconds?.let { formatUptime(it) }?.ifEmpty { null },
        ).joinToString(" · ")
        status.checkedOnce -> stringResource(R.string.settings_not_running)
        else -> stringResource(R.string.settings_not_detected)
    }

    val summaryColor = when {
        status.checking -> MaterialTheme.colorScheme.outline
        status.checkedOnce && status.connected -> Color(0xFF4CAF50)
        status.checkedOnce -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }

    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        ),
        headlineContent = { Text("Daemon") },
        supportingContent = { Text(summary, color = summaryColor) },
        leadingContent = { Icon(Icons.Outlined.Cable, contentDescription = null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (status.checkedOnce && status.connected) {
                    IconButton(
                        onClick = { view.hapticClick(); showVersionDialog = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = if (versionMatch) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (versionMatch) Color(0xFF4CAF50) else Color(0xFFFFA726),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                IconButton(
                    onClick = { view.hapticClick(); if (status.connected) onRestart() else onCheck() },
                    modifier = Modifier.size(32.dp),
                    enabled = !status.checking,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun VersionInfoDialog(
    appVersion: String,
    daemonVersion: String,
    expectedCommit: String,
    currentCommit: String,
    versionMatch: Boolean,
    commitMatch: Boolean,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { view.hapticClick(); onDismiss() }) { Text(stringResource(R.string.settings_confirm)) }
        },
        icon = {
            Icon(
                imageVector = if (versionMatch) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (versionMatch) Color(0xFF4CAF50) else Color(0xFFFFA726),
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(if (versionMatch) stringResource(R.string.settings_version_match) else stringResource(R.string.settings_version_mismatch))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Primary: version comparison
                VersionRow(label = "App:    ", value = appVersion)
                VersionRow(label = "Daemon: ", value = daemonVersion,
                    color = if (versionMatch) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(4.dp))
                // Secondary: commit hash (developer detail)
                VersionRow(label = "Expected: ", value = expectedCommit)
                VersionRow(label = "Current:  ", value = currentCommit,
                    color = if (commitMatch) MaterialTheme.colorScheme.onSurface else Color(0xFFFFA726))
            }
        },
    )
}

@Composable
private fun VersionRow(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = color,
        )
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

private fun modeIcon(mode: PrivilegeMode): ImageVector = when (mode) {
    PrivilegeMode.ROOT -> Icons.Filled.Security
    PrivilegeMode.SHIZUKU -> Icons.Filled.Speed
    PrivilegeMode.ADB -> Icons.Outlined.Cable
    PrivilegeMode.BASIC -> Icons.Filled.Info
}

@Composable
private fun modeDisplayName(mode: PrivilegeMode): String = when (mode) {
    PrivilegeMode.ROOT -> stringResource(R.string.mode_root)
    PrivilegeMode.ADB -> stringResource(R.string.mode_adb)
    PrivilegeMode.SHIZUKU -> stringResource(R.string.mode_shizuku)
    PrivilegeMode.BASIC -> stringResource(R.string.mode_basic)
}

@Composable
private fun modeDescription(mode: PrivilegeMode): String = when (mode) {
    PrivilegeMode.ROOT -> stringResource(R.string.mode_root_description)
    PrivilegeMode.ADB -> stringResource(R.string.mode_adb_description)
    PrivilegeMode.SHIZUKU -> stringResource(R.string.mode_shizuku_description)
    PrivilegeMode.BASIC -> stringResource(R.string.mode_basic_description)
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                view.hapticClick()
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, "https://github.com/1orz/OpenMonitor".toUri()),
                    )
                } catch (_: Exception) {}
            }) {
                Icon(
                    painter = painterResource(R.drawable.ic_github),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("GitHub")
            }
        },
        dismissButton = {
            TextButton(onClick = { view.hapticClick(); onDismiss() }) { Text(stringResource(R.string.settings_close)) }
        },
        title = {
            Text("OpenMonitor")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.app_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                AboutRow("Version", BuildConfig.VERSION_NAME)
                AboutRow("Commit", BuildConfig.GIT_COMMIT)
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
private fun AdbSetupCard(
    binaryPath: String,
    onCheck: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
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
                    text = stringResource(R.string.settings_adb_setup),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.settings_adb_run_cmd),
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
                    view.hapticClick()
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("adb command", adbCommand))
                }) {
                    Text(stringResource(R.string.settings_copy_command))
                }
                OutlinedButton(onClick = { view.hapticClick(); onCheck() }) {
                    Text(stringResource(R.string.settings_check_connection))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_auto_detect_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}


@Composable
private fun LanguageItem() {
    val currentLocale = AppCompatDelegate.getApplicationLocales()
    val currentTag = currentLocale.toLanguageTags().ifEmpty { "" }

    val tags = listOf("", "en", "zh-Hans", "zh-Hant", "ja")
    val labels = listOf(
        stringResource(R.string.settings_language_system),
        "English", "简体中文", "繁體中文", "日本語",
    )
    val selectedIndex = tags.indexOfFirst { tag ->
        if (tag.isEmpty()) currentTag.isEmpty() else currentTag.startsWith(tag)
    }.coerceAtLeast(0)

    SettingsDropdownItem(
        title = stringResource(R.string.settings_language),
        icon = Icons.Filled.Language,
        items = labels,
        selectedIndex = selectedIndex,
        onItemSelected = { index ->
            val locales = if (tags[index].isEmpty()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(tags[index])
            }
            AppCompatDelegate.setApplicationLocales(locales)
        },
    )
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
