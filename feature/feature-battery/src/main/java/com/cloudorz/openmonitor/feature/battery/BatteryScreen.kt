package com.cloudorz.openmonitor.feature.battery

import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.cloudorz.openmonitor.core.ui.hapticClick
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudorz.openmonitor.core.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BatteryScreen(
    viewModel: BatteryViewModel = hiltViewModel(),
    onProvideTopBarActions: (@Composable () -> Unit) -> Unit = {},
) {
    val battery by viewModel.batteryStatus.collectAsStateWithLifecycle()
    val chartData by viewModel.chartData.collectAsStateWithLifecycle()
    val appUsageList by viewModel.appUsageList.collectAsStateWithLifecycle()
    val estimation by viewModel.estimation.collectAsStateWithLifecycle()
    val sparkline by viewModel.currentSparkline.collectAsStateWithLifecycle()
    val selectedRange by viewModel.timeRange.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasUsageStatsPermission.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    // Top bar actions: share/export
    val actions: @Composable () -> Unit = {
        IconButton(onClick = {
            view.hapticClick()
            viewModel.getExportIntent { intent ->
                context.startActivity(intent)
            }
        }) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = stringResource(R.string.battery_export_csv),
            )
        }
    }
    LaunchedEffect(Unit) { onProvideTopBarActions(actions) }
    DisposableEffect(Unit) { onDispose { onProvideTopBarActions {} } }

    // Resolve app icons
    val appIcons = remember { mutableStateMapOf<String, Bitmap>() }
    LaunchedEffect(chartData, appUsageList) {
        withContext(Dispatchers.Default) {
            // Use the icons from appUsageList entries directly
            for (entry in appUsageList) {
                val icon = entry.iconBitmap
                if (icon != null && entry.packageName !in appIcons) {
                    appIcons[entry.packageName] = icon
                }
            }
        }
    }

    // Refresh permission state on resume
    LaunchedEffect(Unit) {
        viewModel.refreshPermissionState()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Section 1: Battery Status Card
        item {
            BatteryStatusCard(
                battery = battery,
                estimation = estimation,
                sparklineData = sparkline,
            )
        }

        // Usage stats permission hint
        if (!hasPermission) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.battery_usage_stats_hint),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            view.hapticClick()
                            context.startActivity(
                                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                },
                            )
                        }) {
                            Text(text = stringResource(R.string.battery_grant_permission))
                        }
                    }
                }
            }
        }

        // Section 2: Battery History Chart
        item {
            BatteryHistoryChart(
                points = chartData,
                battery = battery,
                estimation = estimation,
                selectedRange = selectedRange,
                appIcons = appIcons,
                onRangeSelected = { viewModel.setTimeRange(it) },
            )
        }

        // Section 3: App Usage Breakdown
        item {
            AppUsageList(entries = appUsageList)
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
