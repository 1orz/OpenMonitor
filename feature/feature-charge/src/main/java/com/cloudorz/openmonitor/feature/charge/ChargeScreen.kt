package com.cloudorz.openmonitor.feature.charge

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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.cloudorz.openmonitor.core.ui.R
import com.cloudorz.openmonitor.core.model.battery.BatteryStatus
import com.cloudorz.openmonitor.core.model.battery.ChargeStatRecord
import com.cloudorz.openmonitor.core.model.battery.ChargeStatSession
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
fun ChargeScreen(
    viewModel: ChargeViewModel = hiltViewModel(),
    onSessionClick: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ChargeScreenContent(
        uiState = uiState,
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
private fun ChargeScreenContent(
    uiState: ChargeUiState,
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
            ChargeInfoCard(battery = uiState.currentBattery)
        }

        item {
            ChargeCurveSection(
                battery = uiState.currentBattery,
                records = uiState.currentRecords,
            )
        }

        if (uiState.sessions.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.charge_history),
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

                ChargeSessionItem(
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
        } else {
            item {
                EmptyChargeSessionsHint()
            }
        }
    }
}

@Composable
private fun ChargeInfoCard(battery: BatteryStatus) {
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
                    imageVector = Icons.Default.BatteryChargingFull,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (battery.isCharging) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.charge_info),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (battery.isCharging) stringResource(R.string.charging) else stringResource(R.string.not_charging),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (battery.isCharging) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = if (battery.isCharging) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ChargeDetailItem(
                    label = stringResource(R.string.current),
                    value = "${abs(battery.currentMa)} mA",
                )
                ChargeDetailItem(
                    label = stringResource(R.string.voltage),
                    value = String.format(Locale.US, "%.2f V", battery.voltageV),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ChargeDetailItem(
                    label = stringResource(R.string.power),
                    value = String.format(Locale.US, "%.2f W", abs(battery.powerW)),
                )
                ChargeDetailItem(
                    label = stringResource(R.string.temperature),
                    value = String.format(Locale.US, "%.1f \u00B0C", battery.temperatureCelsius),
                )
            }
        }
    }
}

@Composable
private fun ChargeDetailItem(
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
private fun ChargeCurveSection(
    battery: BatteryStatus,
    records: List<ChargeChartPoint> = emptyList(),
) {
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
                text = stringResource(R.string.charge_curve),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (battery.isCharging) {
                ChargeCurveChart(
                    records = records,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.charge_curve_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.charge_curve_auto_start),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChargeSessionItem(
    session: ChargeStatSession,
    isExpanded: Boolean,
    expandedRecords: List<ChargeStatRecord>?,
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (session.isActive) {
                        "${dateFormat.format(Date(session.beginTime))} - ${stringResource(R.string.charging)}"
                    } else {
                        "${dateFormat.format(Date(session.beginTime))} - ${dateFormat.format(Date(session.endTime))}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (!session.isActive) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onExport) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "CSV",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
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
                        text = stringResource(R.string.charge_amount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = String.format(Locale.US, "%.0f%%", session.capacityRatio),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.energy_charged),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = String.format(Locale.US, "%.2f Wh", session.capacityWh),
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
                        ChargeSessionVicoChart(records = expandedRecords)
                    }
                }
            }
        }
    }
}

private val CapacityColor = Color(0xFF4CAF50)
private val CurrentColor = Color(0xFF2196F3)
private val TempColor = Color(0xFFF44336)

@Composable
private fun ChargeSessionVicoChart(records: List<ChargeStatRecord>) {
    val capacityData = remember(records) { records.map { it.capacity.toDouble() } }
    val currentData = remember(records) { records.map { it.currentMa.toDouble() } }
    val tempData = remember(records) { records.map { it.temperatureCelsius } }

    val seriesColors = listOf(CapacityColor, CurrentColor, TempColor)
    val seriesLabels = listOf(
        stringResource(R.string.session_chart_capacity),
        stringResource(R.string.session_chart_current),
        stringResource(R.string.session_chart_temp),
    )

    val modelProducer = remember { CartesianChartModelProducer() }
    val bottomFormatter = remember(records) {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        CartesianValueFormatter { _, value, _ ->
            val index = value.toInt().coerceIn(0, records.size - 1)
            dateFormat.format(Date(records[index].timestamp))
        }
    }

    LaunchedEffect(records) {
        if (records.size < 2) return@LaunchedEffect
        modelProducer.runTransaction {
            lineSeries {
                series(capacityData)
                series(currentData)
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
private fun EmptyChargeSessionsHint() {
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
                imageVector = Icons.Default.BatteryChargingFull,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.no_charge_records),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.charge_auto_record_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${totalSeconds}s"
    }
}
