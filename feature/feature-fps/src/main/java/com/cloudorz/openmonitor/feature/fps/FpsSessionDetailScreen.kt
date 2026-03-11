package com.cloudorz.openmonitor.feature.fps

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudorz.openmonitor.core.model.fps.FpsFrameRecord
import com.cloudorz.openmonitor.core.model.fps.FpsWatchSession
import com.cloudorz.openmonitor.core.ui.R
import com.cloudorz.openmonitor.core.ui.chart.LineChart
import com.cloudorz.openmonitor.core.ui.chart.LineChartSeries
import com.cloudorz.openmonitor.core.ui.theme.ChartGreen
import com.cloudorz.openmonitor.core.ui.theme.ChartRed
import com.cloudorz.openmonitor.core.ui.theme.ChartYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

private val FpsColor = Color(0xFF42A5F5)
private val TempColor = Color(0xFFFF7043)
private val CpuColor = Color(0xFFAB47BC)
private val GpuColor = Color(0xFF26A69A)
private val PowerColor = Color(0xFF5C6BC0)
private val BatteryColor = Color(0xFF66BB6A)
private val CurrentColor = Color(0xFF29B6F6)

@Composable
fun FpsSessionDetailScreen(
    sessionId: Long,
    onBack: () -> Unit = {},
    viewModel: FpsSessionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    FpsSessionDetailContent(
        state = state,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FpsSessionDetailContent(
    state: FpsSessionDetailState,
    onBack: () -> Unit,
) {
    val session = state.session
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = session?.let {
                            it.sessionDesc.ifEmpty { it.appName.ifEmpty { it.packageName.ifEmpty { stringResource(R.string.fps_recording_title) } } }
                        } ?: stringResource(R.string.fps_recording_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { paddingValues ->
        if (state.loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val records = state.records
        if (records.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.no_recording_sessions),
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Session header
            if (session != null) {
                SessionHeaderCard(session, dateFormat)
            }

            // Stats grid
            StatsGridCard(records)

            // FPS + Temperature chart
            ChartCard(
                title = stringResource(R.string.fps_chart_fps_temp),
            ) {
                val fpsData = remember(records) { records.map { it.fps.toFloat() } }
                val tempData = remember(records) { records.map { it.cpuTemp.toFloat() } }
                val series = mutableListOf(
                    LineChartSeries("FPS", fpsData, FpsColor),
                )
                if (tempData.any { it > 0f }) {
                    series.add(LineChartSeries("Temp(\u00B0C)", tempData, TempColor))
                }
                LineChart(
                    dataSeries = series,
                    modifier = Modifier.fillMaxWidth(),
                    maxDataPoints = records.size.coerceAtLeast(10),
                    showLegend = true,
                )
            }

            // Frame Time chart (max frame time per sample)
            ChartCard(
                title = stringResource(R.string.fps_chart_frame_time),
            ) {
                val ftData = remember(records) { records.map { it.maxFrameTimeMs.toFloat() } }
                LineChart(
                    dataSeries = listOf(
                        LineChartSeries("Max(ms)", ftData, ChartRed),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    maxDataPoints = records.size.coerceAtLeast(10),
                    yAxisLabel = "ms",
                    showLegend = false,
                )
            }

            // CPU Usage chart
            val hasCpuLoad = records.any { it.cpuLoad > 0.0 }
            if (hasCpuLoad) {
                ChartCard(
                    title = stringResource(R.string.fps_chart_cpu_usage),
                ) {
                    val cpuData = remember(records) { records.map { it.cpuLoad.toFloat() } }
                    val gpuData = remember(records) { records.map { it.gpuLoad.toFloat() } }
                    val series = mutableListOf(
                        LineChartSeries("CPU(%)", cpuData, CpuColor),
                    )
                    if (gpuData.any { it > 0f }) {
                        series.add(LineChartSeries("GPU(%)", gpuData, GpuColor))
                    }
                    LineChart(
                        dataSeries = series,
                        modifier = Modifier.fillMaxWidth(),
                        maxDataPoints = records.size.coerceAtLeast(10),
                        yAxisLabel = "%",
                        showLegend = true,
                    )
                }
            }

            // GPU Frequency chart
            val hasGpuFreq = records.any { it.gpuFreqMhz > 0 }
            if (hasGpuFreq) {
                ChartCard(
                    title = stringResource(R.string.fps_chart_gpu_freq),
                ) {
                    val gpuFreq = remember(records) { records.map { it.gpuFreqMhz.toFloat() } }
                    LineChart(
                        dataSeries = listOf(
                            LineChartSeries("GPU(MHz)", gpuFreq, GpuColor),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        maxDataPoints = records.size.coerceAtLeast(10),
                        yAxisLabel = "MHz",
                        showLegend = false,
                    )
                }
            }

            // CPU Core Frequency chart (if available)
            val hasCoreFreqs = records.any { it.cpuCoreFreqsMhz.isNotEmpty() }
            if (hasCoreFreqs) {
                ChartCard(
                    title = stringResource(R.string.fps_chart_cpu_freq),
                ) {
                    val maxCores = records.maxOf { it.cpuCoreFreqsMhz.size }
                    // Group into clusters by detecting frequency differences
                    val clusterColors = listOf(
                        Color(0xFF42A5F5), Color(0xFFFF7043), Color(0xFF66BB6A),
                        Color(0xFFAB47BC), Color(0xFFFFA726),
                    )
                    val series = (0 until maxCores).map { coreIdx ->
                        LineChartSeries(
                            label = "Core $coreIdx",
                            data = records.map { rec ->
                                rec.cpuCoreFreqsMhz.getOrNull(coreIdx)?.toFloat() ?: 0f
                            },
                            color = clusterColors[coreIdx % clusterColors.size],
                        )
                    }
                    LineChart(
                        dataSeries = series,
                        modifier = Modifier.fillMaxWidth(),
                        maxDataPoints = records.size.coerceAtLeast(10),
                        yAxisLabel = "MHz",
                        showLegend = true,
                    )
                }
            }

            // Power + Battery Capacity chart
            val hasPower = records.any { it.powerW > 0.0 }
            val hasBattery = records.any { it.batteryCapacity > 0 }
            if (hasPower || hasBattery) {
                ChartCard(
                    title = stringResource(R.string.fps_chart_power),
                ) {
                    val series = mutableListOf<LineChartSeries>()
                    if (hasPower) {
                        series.add(LineChartSeries(
                            "Power(W)",
                            records.map { it.powerW.toFloat() },
                            PowerColor,
                        ))
                    }
                    if (hasBattery) {
                        series.add(LineChartSeries(
                            "Battery(%)",
                            records.map { it.batteryCapacity.toFloat() },
                            BatteryColor,
                        ))
                    }
                    LineChart(
                        dataSeries = series,
                        modifier = Modifier.fillMaxWidth(),
                        maxDataPoints = records.size.coerceAtLeast(10),
                        showLegend = true,
                    )
                }
            }

            // Battery Current chart
            val hasCurrent = records.any { it.batteryCurrentMa != 0 }
            if (hasCurrent) {
                ChartCard(
                    title = stringResource(R.string.fps_chart_battery_current),
                ) {
                    val currentData = remember(records) { records.map { it.batteryCurrentMa.toFloat() } }
                    LineChart(
                        dataSeries = listOf(
                            LineChartSeries("Current(mA)", currentData, CurrentColor),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        maxDataPoints = records.size.coerceAtLeast(10),
                        yAxisLabel = "mA",
                        showLegend = false,
                    )
                }
            }

            // CPU Temperature chart
            val hasCpuTemp = records.any { it.cpuTemp > 0.0 }
            val hasBatTemp = records.any { it.batteryTemp > 0.0 }
            if (hasCpuTemp || hasBatTemp) {
                ChartCard(
                    title = stringResource(R.string.fps_chart_temperature),
                ) {
                    val series = mutableListOf<LineChartSeries>()
                    if (hasCpuTemp) {
                        series.add(LineChartSeries(
                            "CPU(\u00B0C)",
                            records.map { it.cpuTemp.toFloat() },
                            TempColor,
                        ))
                    }
                    if (hasBatTemp) {
                        series.add(LineChartSeries(
                            "Battery(\u00B0C)",
                            records.map { it.batteryTemp.toFloat() },
                            ChartYellow,
                        ))
                    }
                    LineChart(
                        dataSeries = series,
                        modifier = Modifier.fillMaxWidth(),
                        maxDataPoints = records.size.coerceAtLeast(10),
                        yAxisLabel = "\u00B0C",
                        showLegend = true,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SessionHeaderCard(
    session: FpsWatchSession,
    dateFormat: SimpleDateFormat,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = dateFormat.format(Date(session.beginTime)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            if (session.packageName.isNotEmpty()) {
                Text(
                    text = session.appName.ifEmpty { session.packageName },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (session.appName.isNotEmpty()) {
                    Text(
                        text = session.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.duration) + ": " + formatDuration(session.durationSeconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Mode: ${session.mode}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsGridCard(records: List<FpsFrameRecord>) {
    val fpsList = records.map { it.fps }
    val maxFps = fpsList.maxOrNull() ?: 0.0
    val minFps = fpsList.minOrNull() ?: 0.0
    val avgFps = fpsList.average()
    val variance = if (fpsList.size > 1) {
        val mean = fpsList.average()
        sqrt(fpsList.sumOf { (it - mean) * (it - mean) } / fpsList.size)
    } else 0.0

    // Smoothness: percentage of frames >= 45 FPS
    val smoothness = if (fpsList.isNotEmpty()) {
        fpsList.count { it >= 45.0 } * 100.0 / fpsList.size
    } else 0.0

    // 5% Low: average of bottom 5% fps values
    val fivePercentLow = if (fpsList.size >= 20) {
        val sorted = fpsList.sorted()
        val count = (fpsList.size * 0.05).toInt().coerceAtLeast(1)
        sorted.take(count).average()
    } else {
        fpsList.minOrNull() ?: 0.0
    }

    // Total jank
    val totalJank = records.sumOf { it.jankCount }
    val totalBigJank = records.sumOf { it.bigJankCount }

    // Max temperature
    val maxTemp = records.maxOfOrNull { it.cpuTemp } ?: 0.0

    // Avg power
    val powers = records.map { it.powerW }.filter { it > 0 }
    val avgPower = if (powers.isNotEmpty()) powers.average() else 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 4,
        ) {
            StatCell("MAX", "%.1f".format(maxFps), "FPS", fpsStatColor(maxFps))
            StatCell("MIN", "%.1f".format(minFps), "FPS", fpsStatColor(minFps))
            StatCell("AVG", "%.1f".format(avgFps), "FPS", fpsStatColor(avgFps))
            StatCell("VARIANCE", "%.1f".format(variance), "FPS", MaterialTheme.colorScheme.onSurfaceVariant)
            StatCell("Smoothness", "%.1f%%".format(smoothness), "", smoothnessColor(smoothness))
            StatCell("5% Low", "%.1f".format(fivePercentLow), "FPS", fpsStatColor(fivePercentLow))
            StatCell("Jank", "$totalJank/$totalBigJank", "", if (totalJank > 0) ChartYellow else ChartGreen)
            if (maxTemp > 0) {
                StatCell("MAX Temp", "%.1f".format(maxTemp), "\u00B0C", tempStatColor(maxTemp))
            }
            if (avgPower > 0) {
                StatCell("AVG Power", "%.2f".format(avgPower), "W", PowerColor)
            }
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    unit: String,
    valueColor: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            fontSize = 9.sp,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
        if (unit.isNotEmpty()) {
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

private fun fpsStatColor(fps: Double): Color = when {
    fps >= 50.0 -> ChartGreen
    fps >= 30.0 -> ChartYellow
    else -> ChartRed
}

private fun smoothnessColor(pct: Double): Color = when {
    pct >= 80.0 -> ChartGreen
    pct >= 50.0 -> ChartYellow
    else -> ChartRed
}

private fun tempStatColor(temp: Double): Color = when {
    temp < 45.0 -> ChartGreen
    temp < 60.0 -> ChartYellow
    else -> ChartRed
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> "%d:%02d:%02d".format(hours, minutes, secs)
        minutes > 0 -> "%d:%02d".format(minutes, secs)
        else -> "${secs}s"
    }
}
