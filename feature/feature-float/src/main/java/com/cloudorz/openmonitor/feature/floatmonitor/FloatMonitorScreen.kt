package com.cloudorz.openmonitor.feature.floatmonitor

import android.annotation.SuppressLint
import android.content.Intent
import androidx.core.net.toUri
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.cloudorz.openmonitor.core.ui.R

@Composable
fun FloatMonitorScreen(
    viewModel: FloatMonitorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Refresh permission and enabled state every time screen resumes
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshPermission()
        }
    }

    // context.getString is required here — stringResource() is not available inside LaunchedEffect
    @SuppressLint("LocalContextGetResourceValueCall")
    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { msgRes ->
            snackbarHostState.showSnackbar(context.getString(msgRes))
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
    ) { paddingValues ->
        FloatMonitorScreenContent(
            hasOverlayPermission = uiState.hasOverlayPermission,
            canShowOverlay = uiState.canShowOverlay,
            enabledMonitors = uiState.enabledMonitors,
            onToggleMonitor = viewModel::onToggleMonitor,
            onRequestOverlayPermission = {
                try {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${context.packageName}".toUri(),
                        )
                    )
                } catch (_: Exception) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            "package:${context.packageName}".toUri(),
                        )
                    )
                }
            },
            modifier = Modifier.padding(paddingValues),
        )
    }
}

@Composable
private fun FloatMonitorScreenContent(
    hasOverlayPermission: Boolean,
    canShowOverlay: Boolean,
    enabledMonitors: Set<FloatMonitorType>,
    onToggleMonitor: (FloatMonitorType, Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit,
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
                    text = stringResource(R.string.float_monitor_title),
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
                switchEnabled = canShowOverlay,
                onToggle = { enabled -> onToggleMonitor(monitorType, enabled) },
                onInfoClick = {
                    expandedInfo[monitorType] = !(expandedInfo[monitorType] ?: false)
                },
            )
        }

        // Permission section
        item {
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            PermissionSection(
                hasOverlayPermission = hasOverlayPermission,
                onRequestOverlayPermission = onRequestOverlayPermission,
            )
        }
    }
}

@Composable
private fun MonitorTypeCard(
    monitorType: FloatMonitorType,
    isEnabled: Boolean,
    isExpanded: Boolean,
    switchEnabled: Boolean,
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
                        text = stringResource(monitorType.displayNameRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(monitorType.descriptionRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(onClick = onInfoClick) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.detail_info),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    enabled = switchEnabled,
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(monitorType.infoRes),
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
    onRequestOverlayPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasOverlayPermission) {
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
                    tint = if (hasOverlayPermission) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.permission_desc_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.permission_desc_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (hasOverlayPermission) stringResource(R.string.overlay_granted) else stringResource(R.string.overlay_not_granted),
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
                        Text(text = stringResource(R.string.go_authorize))
                    }
                }
            }
        }
    }
}
