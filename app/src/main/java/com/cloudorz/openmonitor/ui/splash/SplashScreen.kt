package com.cloudorz.openmonitor.ui.splash

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cloudorz.openmonitor.R
import com.cloudorz.openmonitor.core.common.PermissionManager
import com.cloudorz.openmonitor.core.common.PrivilegeMode

private enum class RootCheckState { IDLE, CHECKING, GRANTED, DENIED }

@Composable
fun SplashScreen(
    permissionManager: PermissionManager,
    onModeSelected: (PrivilegeMode) -> Unit,
) {
    @Suppress("AssignedValueIsNeverRead")
    var rootCheckState by remember { mutableStateOf(RootCheckState.IDLE) }

    // Root permission check logic
    if (rootCheckState == RootCheckState.CHECKING) {
        LaunchedEffect(Unit) {
            val detected = permissionManager.detectBestMode()
            rootCheckState = if (detected == PrivilegeMode.ROOT) {
                RootCheckState.GRANTED
            } else {
                RootCheckState.DENIED
            }
        }
    }

    // Root check dialog
    when (rootCheckState) {
        RootCheckState.CHECKING -> {
            RootCheckingDialog()
        }
        RootCheckState.GRANTED -> {
            LaunchedEffect(Unit) {
                permissionManager.setMode(PrivilegeMode.ROOT)
                onModeSelected(PrivilegeMode.ROOT)
            }
        }
        RootCheckState.DENIED -> {
            RootDeniedDialog(
                onRetry = { rootCheckState = RootCheckState.CHECKING },
                onFallbackBasic = {
                    permissionManager.setMode(PrivilegeMode.BASIC)
                    onModeSelected(PrivilegeMode.BASIC)
                },
                onDismiss = { rootCheckState = RootCheckState.IDLE },
            )
        }
        else -> {}
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Monitor,
                contentDescription = "OpenMonitor Logo",
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "OpenMonitor",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.splash_select_mode),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.splash_mode_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Root mode button
            Button(
                onClick = { rootCheckState = RootCheckState.CHECKING },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = stringResource(R.string.mode_root_title),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ADB mode button
            OutlinedButton(
                onClick = {
                    permissionManager.setMode(PrivilegeMode.ADB)
                    onModeSelected(PrivilegeMode.ADB)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.mode_adb),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Basic mode button
            OutlinedButton(
                onClick = {
                    permissionManager.setMode(PrivilegeMode.BASIC)
                    onModeSelected(PrivilegeMode.BASIC)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.mode_basic_title),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun RootCheckingDialog() {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        icon = {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
        },
        title = {
            Text(
                text = stringResource(R.string.splash_checking_root),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.splash_grant_root),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun RootDeniedDialog(
    onRetry: () -> Unit,
    onFallbackBasic: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Error,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(text = stringResource(R.string.splash_root_denied))
        },
        text = {
            Text(
                text = stringResource(R.string.splash_root_denied_detail),
            )
        },
        confirmButton = {
            Button(onClick = onRetry) {
                Text(stringResource(R.string.splash_retry))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onFallbackBasic) {
                    Text(stringResource(R.string.splash_use_basic))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.splash_go_back))
                }
            }
        },
    )
}
