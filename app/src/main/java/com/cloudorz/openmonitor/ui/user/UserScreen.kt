package com.cloudorz.openmonitor.ui.user

import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import com.cloudorz.openmonitor.ui.CommunityLinks
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
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

@Composable
fun UserScreen(
    permissionManager: PermissionManager,
    onNavigateToLicenses: () -> Unit = {},
    onNavigateToTheme: () -> Unit = {},
    onNavigateToDonate: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    viewModel: UserViewModel = hiltViewModel(),
) {
    val view = LocalView.current
    val currentMode by permissionManager.currentMode.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var isDetecting by remember { mutableStateOf(false) }
    var shizukuStatus by remember { mutableStateOf(checkShizukuStatus()) }
    val serverStatus by viewModel.serverStatus.collectAsState()
    var showModeDropdown by remember { mutableStateOf(false) }

    DisposableEffect(viewModel) {
        viewModel.startObserving()
        onDispose { viewModel.stopObserving() }
    }

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
                if (currentMode != PrivilegeMode.BASIC) {
                    add {
                        ServerStatusItem(
                            status = serverStatus,
                            canRestart = currentMode != PrivilegeMode.ADB,
                            onCheck = { viewModel.checkServer() },
                            onRestart = { viewModel.restartServer() },
                        )
                    }
                }
            },
        )

        // ADB mode: silent background polling until daemon connects
        if (currentMode != PrivilegeMode.BASIC) {
            DisposableEffect(currentMode) {
                if (currentMode == PrivilegeMode.ADB) {
                    viewModel.startAdbWatcher()
                }
                onDispose { viewModel.stopAdbWatcher() }
            }
        }

        if (currentMode == PrivilegeMode.ADB && !serverStatus.connected) {
            AdbSetupCard(
                binaryPath = "libopenmonitor-server.so",
                onCheck = { viewModel.checkServer() },
            )
        }

        // ── Appearance ──
        val hapticEnabled by viewModel.hapticEnabled.collectAsState()

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
            items = listOf {
                SettingsSwitchItem(
                    title = stringResource(R.string.settings_haptic_feedback),
                    icon = Icons.Filled.Vibration,
                    checked = hapticEnabled,
                    onCheckedChange = viewModel::setHapticEnabled,
                )
            },
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

        // ── Community ──
        val communityContext = LocalContext.current
        SettingsGroup(
            title = stringResource(R.string.settings_group_community),
            items = listOf(
                {
                    ListItem(
                        modifier = Modifier.hapticClickable(onClick = {
                            try {
                                communityContext.startActivity(
                                    Intent(Intent.ACTION_VIEW, CommunityLinks.TELEGRAM_URL.toUri()),
                                )
                            } catch (_: Exception) {}
                        }),
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        ),
                        headlineContent = { Text(stringResource(R.string.join_telegram)) },
                        supportingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = CommunityLinks.TELEGRAM_URL,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = {
                                        val clipboard = communityContext.getSystemService(ClipboardManager::class.java)
                                        clipboard?.setPrimaryClip(ClipData.newPlainText("Telegram", CommunityLinks.TELEGRAM_URL))
                                        Toast.makeText(communityContext, communityContext.getString(R.string.qq_group_copied), Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(20.dp),
                                ) {
                                    Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(14.dp))
                                }
                            }
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_telegram),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                        },
                    )
                },
                {
                    ListItem(
                        modifier = Modifier.hapticClickable(onClick = {
                            try {
                                communityContext.startActivity(
                                    Intent(Intent.ACTION_VIEW, CommunityLinks.QQ_GROUP_URL.toUri()),
                                )
                            } catch (_: Exception) {
                                Toast.makeText(communityContext, communityContext.getString(R.string.qq_not_installed), Toast.LENGTH_SHORT).show()
                            }
                        }),
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        ),
                        headlineContent = { Text(stringResource(R.string.join_qq_group)) },
                        supportingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = CommunityLinks.QQ_GROUP_NUMBER,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = {
                                        val clipboard = communityContext.getSystemService(ClipboardManager::class.java)
                                        clipboard?.setPrimaryClip(ClipData.newPlainText("QQ Group", CommunityLinks.QQ_GROUP_NUMBER))
                                        Toast.makeText(communityContext, communityContext.getString(R.string.qq_group_copied), Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(20.dp),
                                ) {
                                    Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(14.dp))
                                }
                            }
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_qq),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                        },
                    )
                },
                {
                    val qqChannelId = "OpenMonitor1"
                    val qqChannelUrl = "https://pd.qq.com/s/ft7tc3xae"
                    ListItem(
                        modifier = Modifier.hapticClickable(onClick = {
                            try {
                                communityContext.startActivity(
                                    Intent(Intent.ACTION_VIEW, qqChannelUrl.toUri()),
                                )
                            } catch (_: Exception) {
                                val clipboard = communityContext.getSystemService(ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(ClipData.newPlainText("QQ Channel", qqChannelUrl))
                                Toast.makeText(communityContext, communityContext.getString(R.string.qq_channel_copied), Toast.LENGTH_SHORT).show()
                            }
                        }),
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        ),
                        headlineContent = { Text(stringResource(R.string.join_qq_channel)) },
                        supportingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = qqChannelId,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = {
                                        val clipboard = communityContext.getSystemService(ClipboardManager::class.java)
                                        clipboard?.setPrimaryClip(ClipData.newPlainText("QQ Channel", qqChannelId))
                                        Toast.makeText(communityContext, communityContext.getString(R.string.qq_channel_copied), Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(20.dp),
                                ) {
                                    Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(14.dp))
                                }
                            }
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_qq_channel),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null)
                        },
                    )
                },
            ),
        )

        // ── About ──
        // null = still checking, non-null = result
        val updateContext = LocalContext.current
        var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
        LaunchedEffect(Unit) {
            updateInfo = UpdateChecker.check(updateContext)
        }

        SettingsGroup(
            title = stringResource(R.string.settings_group_about),
            items = buildList {
                // Version + update status
                add {
                    val info = updateInfo
                    val isChecking = info == null
                    val hasUpdate = info?.hasUpdate == true
                    val isError = info?.isError == true

                    ListItem(
                        modifier = Modifier.hapticClickable(onClick = {
                            if (hasUpdate) {
                                UpdateChecker.openDownload(updateContext, info.downloadUrl)
                            }
                        }),
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        ),
                        headlineContent = {
                            Text(stringResource(R.string.settings_version_current, BuildConfig.VERSION_NAME))
                        },
                        supportingContent = {
                            Text(
                                text = when {
                                    isChecking -> stringResource(R.string.settings_detecting)
                                    isError -> stringResource(R.string.settings_check_failed)
                                    hasUpdate -> stringResource(R.string.settings_version_latest, info.versionName)
                                    else -> stringResource(R.string.settings_up_to_date)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    isChecking -> MaterialTheme.colorScheme.outline
                                    isError -> MaterialTheme.colorScheme.error
                                    hasUpdate -> MaterialTheme.colorScheme.primary
                                    else -> Color(0xFF4CAF50)
                                },
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Filled.SystemUpdate, contentDescription = null)
                        },
                        trailingContent = if (hasUpdate) {
                            { Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null) }
                        } else null,
                    )
                }
                // About page
                add {
                    SettingsNavigateItem(
                        title = stringResource(R.string.about_title),
                        summary = stringResource(R.string.settings_version_format, BuildConfig.VERSION_NAME),
                        icon = Icons.Filled.Info,
                        onClick = { onNavigateToAbout() },
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
private fun ServerStatusItem(
    status: UserViewModel.ServerStatus,
    canRestart: Boolean,
    onCheck: () -> Unit,
    onRestart: () -> Unit,
) {
    val view = LocalView.current

    val summary = when {
        status.checking -> stringResource(R.string.settings_detecting)
        status.checkedOnce && status.connected -> stringResource(R.string.settings_running)
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
        headlineContent = { Text("Server") },
        supportingContent = { Text(summary, color = summaryColor) },
        leadingContent = { Icon(Icons.Outlined.Cable, contentDescription = null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canRestart || !status.connected) {
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
    val appLocales = AppCompatDelegate.getApplicationLocales()

    val tags = listOf("", "en", "zh-Hans", "zh-Hant", "ja")
    val labels = listOf(
        stringResource(R.string.settings_language_system),
        "English", "简体中文", "繁體中文", "日本語",
    )
    val selectedIndex = if (appLocales.isEmpty) {
        0
    } else {
        val current = appLocales[0]!!
        tags.indexOfFirst { tag ->
            if (tag.isEmpty()) return@indexOfFirst false
            val target = java.util.Locale.forLanguageTag(tag)
            if (current.language != target.language) return@indexOfFirst false
            target.script.isEmpty() || current.script == target.script
        }.coerceAtLeast(0)
    }

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
