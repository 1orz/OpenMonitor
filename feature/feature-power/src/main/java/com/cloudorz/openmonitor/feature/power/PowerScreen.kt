package com.cloudorz.openmonitor.feature.power

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudorz.openmonitor.core.model.battery.BatteryStatus
import com.cloudorz.openmonitor.core.model.battery.PowerStatRecord
import com.cloudorz.openmonitor.core.model.battery.PowerStatSession
import com.cloudorz.openmonitor.core.ui.R
import com.cloudorz.openmonitor.core.ui.chart.LineChart
import com.cloudorz.openmonitor.core.ui.chart.LineChartSeries
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.LegendItem
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.patrykandpatrick.vico.compose.common.rememberHorizontalLegend
import com.patrykandpatrick.vico.compose.common.vicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val LegendLabelKey = ExtraStore.Key<List<String>>()

@Composable
fun PowerScreen(
    viewModel: PowerViewModel = hiltViewModel(),
    onSessionClick: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    PowerScreenContent(
        uiState = uiState,
        onToggleTracking = viewModel::onToggleTracking,
        onSessionClick = onSessionClick,
        onToggleSessionExpand = viewModel::onToggleSessionExpand,
        onExportSession = { sessionId ->
            viewModel.getExportIntent(sessionId) { intent ->
                context.startActivity(intent)
            }
        },
    )
}

@Composable
private fun PowerScreenContent(
    uiState: PowerUiState,
    onToggleTracking: () -> Unit,
    onSessionClick: (String) -> Unit,
    onToggleSessionExpand: (String) -> Unit,
    onExportSession: (Long) -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            BatteryInfoCard(battery = uiState.currentBattery)
        }

        item {
            BatteryHealthCard(battery = uiState.currentBattery)
        }

        item {
            TrackingButton(
                isTracking = uiState.isTracking,
                onToggle = onToggleTracking,
            )
        }

        // Real-time statistics and charts during tracking
        if (uiState.isTracking && uiState.currentRecords.isNotEmpty()) {
            item {
                PowerStatsCard(
                    battery = uiState.currentBattery,
                    records = uiState.currentRecords,
                    startCapacity = uiState.trackingStartCapacity,
                )
            }

            item {
                PowerChartsSection(records = uiState.currentRecords)
            }
        }

        if (uiState.sessions.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.power_history),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            items(
                items = uiState.sessions,
                key = { it.sessionId },
            ) { session ->
                val isExpanded = uiState.expandedSessionRecords.containsKey(session.sessionId)
                val records = uiState.expandedSessionRecords[session.sessionId]

                SessionItem(
                    session = session,
                    isExpanded = isExpanded,
                    expandedRecords = records,
                    onClick = {
                        if (!session.isActive) {
                            onToggleSessionExpand(session.sessionId)
                        }
                    },
                    onExport = { onExportSession(session.sessionId.toLongOrNull() ?: 0L) },
                )
            }
        } else if (!uiState.isTracking) {
            item {
                EmptySessionsHint()
            }
        }
    }
}

@Composable
private fun BatteryInfoCard(battery: BatteryStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when {
                        battery.isCharging -> Icons.Default.BatteryChargingFull
                        battery.capacity > 20 -> Icons.Default.BatteryFull
                        else -> Icons.Default.BatteryStd
                    },
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = when {
                        battery.isCharging -> Color(0xFF4CAF50)
                        battery.capacity <= 20 -> Color(0xFFF44336)
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.battery_info),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = battery.statusText.ifEmpty { battery.status.displayName },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${battery.capacity}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        battery.capacity <= 20 -> Color(0xFFF44336)
                        battery.capacity <= 50 -> Color(0xFFFF9800)
                        else -> Color(0xFF4CAF50)
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { battery.capacity / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = when {
                    battery.capacity <= 20 -> Color(0xFFF44336)
                    battery.capacity <= 50 -> Color(0xFFFF9800)
                    else -> Color(0xFF4CAF50)
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BatteryDetailItem(
                    label = stringResource(R.string.voltage),
                    value = String.format(Locale.US, "%.2f V", battery.voltageV),
                )
                BatteryDetailItem(
                    label = stringResource(R.string.current),
                    value = "${abs(battery.currentMa)} mA",
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BatteryDetailItem(
                    label = stringResource(R.string.power),
                    value = String.format(Locale.US, "%.2f W", abs(battery.powerW)),
                )
                BatteryDetailItem(
                    label = stringResource(R.string.temperature),
                    value = String.format(Locale.US, "%.1f \u00B0C", battery.temperatureCelsius),
                )
            }

            if (battery.technology.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    BatteryDetailItem(
                        label = stringResource(R.string.technology),
                        value = battery.technology,
                    )
                    if (battery.chargerType.isNotEmpty()) {
                        BatteryDetailItem(
                            label = stringResource(R.string.charger_type),
                            value = battery.chargerType,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryDetailItem(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun BatteryHealthCard(battery: BatteryStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = when {
                        battery.healthPercent >= 80 -> Color(0xFF4CAF50)
                        battery.healthPercent >= 50 -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.battery_health),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${battery.healthPercent}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        battery.healthPercent >= 80 -> Color(0xFF4CAF50)
                        battery.healthPercent >= 50 -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { battery.healthPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = when {
                    battery.healthPercent >= 80 -> Color(0xFF4CAF50)
                    battery.healthPercent >= 50 -> Color(0xFFFF9800)
                    else -> Color(0xFFF44336)
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when {
                    battery.healthPercent >= 90 -> stringResource(R.string.battery_health_good)
                    battery.healthPercent >= 80 -> stringResource(R.string.battery_health_normal)
                    battery.healthPercent >= 50 -> stringResource(R.string.battery_health_degraded)
                    else -> stringResource(R.string.battery_health_poor)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrackingButton(
    isTracking: Boolean,
    onToggle: () -> Unit,
) {
    Button(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        colors = if (isTracking) {
            ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF44336),
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Icon(
            imageVector = if (isTracking) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isTracking) stringResource(R.string.stop_power_tracking) else stringResource(R.string.start_power_tracking),
        )
    }
}

/**
 * Real-time power statistics card shown during active tracking.
 * Displays: avg power, usage time, estimated battery life.
 */
@Composable
private fun PowerStatsCard(
    battery: BatteryStatus,
    records: List<PowerStatRecord>,
    startCapacity: Int,
) {
    val avgPowerW = remember(records) {
        if (records.isEmpty()) 0.0
        else records.map { it.powerW }.average()
    }
    val usedPercent = startCapacity - battery.capacity
    val elapsedMs = if (records.size >= 2) {
        records.last().startTime - records.first().startTime
    } else {
        0L
    }
    val elapsedSeconds = elapsedMs / 1000
    // Estimate battery life: remaining% / drain rate per second
    val estBatteryLifeSeconds = if (usedPercent > 0 && elapsedSeconds > 0) {
        (battery.capacity.toLong() * elapsedSeconds) / usedPercent
    } else if (avgPowerW > 0.1 && battery.capacityMah > 0) {
        // Fallback: capacityMah * voltageV / powerW * 3600 * (remaining / 100)
        val totalWh = battery.capacityMah / 1000.0 * battery.voltageV
        val remainingWh = totalWh * battery.capacity / 100.0
        (remainingWh / avgPowerW * 3600).toLong()
    } else {
        0L
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.power_stats),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatItem(
                    value = String.format(Locale.US, "%.2fW", avgPowerW),
                    label = stringResource(R.string.avg_power),
                    color = MaterialTheme.colorScheme.primary,
                )
                StatItem(
                    value = formatDuration(elapsedSeconds),
                    label = stringResource(R.string.used_time),
                    color = MaterialTheme.colorScheme.primary,
                )
                StatItem(
                    value = if (estBatteryLifeSeconds > 0) formatDuration(estBatteryLifeSeconds) else "--",
                    label = stringResource(R.string.est_battery_life),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    color: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Real-time charts section: Power/Time, Battery Level/Time, Temperature/Time
 */
@Composable
private fun PowerChartsSection(records: List<PowerStatRecord>) {
    val powerData = remember(records) {
        records.map { it.powerW.toFloat() }
    }
    val batteryData = remember(records) {
        records.map { it.capacity.toFloat() }
    }
    val tempData = remember(records) {
        records.map { it.temperature.toFloat() }
    }

    // Power / Time chart
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.power_over_time),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LineChart(
                dataSeries = listOf(
                    LineChartSeries(
                        label = stringResource(R.string.power),
                        data = powerData,
                        color = Color(0xFF2196F3),
                    ),
                ),
                maxDataPoints = 120,
                yAxisLabel = stringResource(R.string.power_w_label),
                showLegend = false,
            )
        }
    }

    // Battery Level / Time chart
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.battery_over_time),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LineChart(
                dataSeries = listOf(
                    LineChartSeries(
                        label = stringResource(R.string.battery_level),
                        data = batteryData,
                        color = Color(0xFF4CAF50),
                    ),
                ),
                maxDataPoints = 120,
                yAxisLabel = stringResource(R.string.battery_pct_label),
                showLegend = false,
            )
        }
    }

    // Temperature / Time chart
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.temp_over_time),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LineChart(
                dataSeries = listOf(
                    LineChartSeries(
                        label = stringResource(R.string.temperature),
                        data = tempData,
                        color = Color(0xFFF44336),
                    ),
                ),
                maxDataPoints = 120,
                yAxisLabel = stringResource(R.string.temp_c_label),
                showLegend = false,
            )
        }
    }
}

@Composable
private fun SessionItem(
    session: PowerStatSession,
    isExpanded: Boolean,
    expandedRecords: List<PowerStatRecord>?,
    onClick: () -> Unit,
    onExport: () -> Unit = {},
) {
    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (session.isActive) Color(0xFFF44336) else MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (session.isActive) {
                            "${dateFormat.format(Date(session.beginTime))} - ${stringResource(R.string.tracking_active)}"
                        } else {
                            "${dateFormat.format(Date(session.beginTime))} - ${dateFormat.format(Date(session.endTime))}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (!session.isActive) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = onExport) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "CSV",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.power_consumed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (session.isActive) "--" else String.format(Locale.US, "%.1f%%", session.usedPercent),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.avg_power),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (session.isActive) "--" else String.format(Locale.US, "%.2f W", session.avgPowerW),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatDuration(session.durationSeconds),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Expandable Vico chart
            AnimatedVisibility(
                visible = isExpanded && expandedRecords != null && expandedRecords.size >= 2,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                if (expandedRecords != null && expandedRecords.size >= 2) {
                    Column {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        PowerSessionVicoChart(records = expandedRecords)
                    }
                }
            }
        }
    }
}

private val PowerColor = Color(0xFF2196F3)
private val BatteryColor = Color(0xFF4CAF50)
private val TempColor = Color(0xFFF44336)

@Composable
private fun PowerSessionVicoChart(records: List<PowerStatRecord>) {
    val powerData = remember(records) { records.map { it.powerW } }
    val batteryData = remember(records) { records.map { it.capacity.toDouble() } }
    val tempData = remember(records) { records.map { it.temperature } }

    val seriesColors = listOf(PowerColor, BatteryColor, TempColor)
    val seriesLabels = listOf(
        stringResource(R.string.session_chart_power),
        stringResource(R.string.session_chart_battery),
        stringResource(R.string.session_chart_temp),
    )

    val modelProducer = remember { CartesianChartModelProducer() }
    val bottomFormatter = remember(records) {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        CartesianValueFormatter { _, value, _ ->
            val index = value.toInt().coerceIn(0, records.size - 1)
            dateFormat.format(Date(records[index].startTime))
        }
    }

    LaunchedEffect(records) {
        if (records.size < 2) return@LaunchedEffect
        modelProducer.runTransaction {
            lineSeries {
                series(powerData)
                series(batteryData)
                series(tempData)
            }
            extras { it[LegendLabelKey] = seriesLabels }
        }
    }

    ProvideVicoTheme(rememberM3VicoTheme()) {
        val legendLabel = rememberTextComponent(
            style = TextStyle(color = vicoTheme.textColor, fontSize = 11.sp),
        )
        val markerLabelColor = MaterialTheme.colorScheme.onSurface
        val guidelineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        val marker = rememberDefaultCartesianMarker(
            label = rememberTextComponent(
                style = TextStyle(color = markerLabelColor, fontSize = 10.sp),
            ),
            indicator = { color -> ShapeComponent(Fill(color), CircleShape) },
            indicatorSize = 6.dp,
            guideline = rememberLineComponent(fill = Fill(guidelineColor), thickness = 1.dp),
        )

        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    LineCartesianLayer.LineProvider.series(
                        seriesColors.map { color ->
                            LineCartesianLayer.rememberLine(
                                fill = LineCartesianLayer.LineFill.single(Fill(color)),
                                areaFill = LineCartesianLayer.AreaFill.single(Fill(color.copy(alpha = 0.08f))),
                            )
                        }
                    ),
                ),
                startAxis = VerticalAxis.rememberStart(
                    valueFormatter = CartesianValueFormatter.decimal(),
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = bottomFormatter,
                ),
                marker = marker,
                markerController = CartesianMarkerController.rememberShowOnPress(),
                legend = rememberHorizontalLegend(
                    items = { extraStore ->
                        extraStore[LegendLabelKey].forEachIndexed { index, label ->
                            add(
                                LegendItem(
                                    icon = ShapeComponent(Fill(seriesColors[index]), CircleShape),
                                    labelComponent = legendLabel,
                                    label = label,
                                )
                            )
                        }
                    },
                    iconSize = 8.dp,
                    iconLabelSpacing = 4.dp,
                    rowSpacing = 4.dp,
                    columnSpacing = 12.dp,
                    padding = Insets(top = 8.dp),
                ),
            ),
            modelProducer = modelProducer,
            modifier = Modifier.fillMaxWidth().height(200.dp),
            scrollState = rememberVicoScrollState(scrollEnabled = true, initialScroll = Scroll.Absolute.Start),
            zoomState = rememberVicoZoomState(zoomEnabled = true, initialZoom = Zoom.Content),
        )
    }
}

@Composable
private fun EmptySessionsHint() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.BatteryStd,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.no_power_records),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.start_tracking_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${totalSeconds}s"
    }
}
