package com.cloudorz.openmonitor.ui.splash

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.cloudorz.openmonitor.R

@Composable
fun PermissionSetupScreen(onAllGranted: () -> Unit) {
    val context = LocalContext.current
    val packageUri = Uri.parse("package:${context.packageName}")

    // Bump this counter to force recomposition when returning from settings
    var refreshTick by remember { mutableIntStateOf(0) }

    // Re-check permissions every time the screen resumes
    LifecycleResumeEffect(Unit) {
        refreshTick++
        onPauseOrDispose {}
    }

    // ── Permission states ──
    val overlayGranted = remember(refreshTick) { Settings.canDrawOverlays(context) }
    val usageGranted = remember(refreshTick) { hasUsageStatsPermission(context) }
    val batteryGranted = remember(refreshTick) { isIgnoringBatteryOptimizations(context) }
    val notificationGranted = remember(refreshTick) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    val allGranted = overlayGranted && usageGranted && batteryGranted && notificationGranted

    // ── Launchers ──
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshTick++ }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshTick++ }

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
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.perm_setup_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.perm_setup_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── 1. Overlay — direct to app's overlay toggle ──
            PermissionCard(
                icon = Icons.Filled.Layers,
                iconTint = Color(0xFF2196F3),
                title = stringResource(R.string.perm_overlay_title),
                description = stringResource(R.string.perm_overlay_desc),
                granted = overlayGranted,
                onGrant = { settingsLauncher.launch(overlayPermissionIntent(context)) },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── 2. Usage Stats — direct to app's usage access page ──
            PermissionCard(
                icon = Icons.Filled.QueryStats,
                iconTint = Color(0xFFFF9800),
                title = stringResource(R.string.perm_usage_title),
                description = stringResource(R.string.perm_usage_desc),
                granted = usageGranted,
                onGrant = {
                    // Try direct link to this app's usage access page (API 30+)
                    val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri)
                    if (direct.resolveActivity(context.packageManager) != null) {
                        settingsLauncher.launch(direct)
                    } else {
                        // Fallback to general usage access settings list
                        settingsLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── 3. Battery Optimization — system dialog, no settings page ──
            PermissionCard(
                icon = Icons.Filled.BatteryChargingFull,
                iconTint = Color(0xFFE91E63),
                title = stringResource(R.string.perm_battery_title),
                description = stringResource(R.string.perm_battery_desc),
                granted = batteryGranted,
                onGrant = {
                    settingsLauncher.launch(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            packageUri,
                        ),
                    )
                },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── 4. Notification ──
            PermissionCard(
                icon = Icons.Filled.Notifications,
                iconTint = Color(0xFF4CAF50),
                title = stringResource(R.string.perm_notification_title),
                description = stringResource(R.string.perm_notification_desc),
                granted = notificationGranted,
                onGrant = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Start button ──
            Button(
                onClick = onAllGranted,
                modifier = Modifier.fillMaxWidth(),
                enabled = allGranted,
            ) {
                Text(
                    text = stringResource(R.string.perm_start),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            if (!allGranted) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.perm_grant_all_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (granted) Color(0xFF4CAF50) else Color.Transparent,
        animationSpec = tween(300),
        label = "permBorder",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (granted) 0.dp else 2.dp,
        ),
        border = if (granted) BorderStroke(1.5.dp, borderColor) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = iconTint,
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (granted) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = Color(0xFF4CAF50),
                )
            } else {
                FilledTonalButton(onClick = onGrant) {
                    Text(
                        text = stringResource(R.string.perm_go_grant),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

private fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    @Suppress("DEPRECATION")
    val mode = appOps.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName,
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * Returns an Intent that opens this app's overlay permission toggle.
 *
 * Some OEM ROMs (OPPO/ColorOS, OnePlus/OxygenOS, Realme, vivo/OriginOS)
 * redirect ACTION_MANAGE_OVERLAY_PERMISSION to a generic list page instead
 * of the per-app toggle. For these, fall back to the app detail settings
 * which always contains the overlay toggle on the app's own page.
 */
private fun overlayPermissionIntent(context: Context): Intent {
    val packageUri = Uri.parse("package:${context.packageName}")
    val brand = Build.MANUFACTURER.lowercase()
    // These brands are known to redirect the overlay intent to a list page
    val brokenOverlayIntent = brand in setOf(
        "oppo", "oneplus", "realme", "vivo", "iqoo", "oplus",
    )
    return if (brokenOverlayIntent) {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    } else {
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)
    }
}
