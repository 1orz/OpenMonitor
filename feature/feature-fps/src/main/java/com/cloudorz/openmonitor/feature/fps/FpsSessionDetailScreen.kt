package com.cloudorz.openmonitor.feature.fps

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import com.cloudorz.openmonitor.core.ui.hapticClick
import com.cloudorz.openmonitor.core.ui.hapticClickable
import com.cloudorz.openmonitor.core.ui.theme.ChartGreen
import com.cloudorz.openmonitor.core.ui.theme.ChartRed
import com.cloudorz.openmonitor.core.ui.theme.ChartYellow
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
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
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
private val BatTempColor = Color(0xFFFFA726)

private val CoreColors = listOf(
    Color(0xFF42A5F5), Color(0xFFFF7043), Color(0xFF66BB6A),
    Color(0xFFAB47BC), Color(0xFFFFA726), Color(0xFFEF5350),
    Color(0xFF26C6DA), Color(0xFF8D6E63),
)

private val LegendLabelKey = ExtraStore.Key<List<String>>()

private enum class ChartSection(val label: String) {
    FPS_TEMP("FPS"),
    FRAME_TIME("Frame Time"),
    CPU_GPU("CPU / GPU"),
    CPU_CORE_LOAD("CPU Core Load"),
    GPU_FREQ("GPU Freq"),
    CPU_FREQ("CPU Freq"),
    JANK("Jank"),
    POWER("Power"),
    CURRENT("Current"),
    TEMPERATURE("Temperature"),
}

@Composable
fun FpsSessionDetailScreen(
    sessionId: String? = null,
    viewModel: FpsSessionDetailViewModel = hiltViewModel(),
    onProvideTopBarActions: (@Composable () -> Unit) -> Unit = {},
) {
    if (sessionId != null) {
        androidx.compose.runtime.LaunchedEffect(sessionId) { viewModel.initSessionId(sessionId) }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    var showSectionOptions by remember { mutableStateOf(false) }

    val actions: @Composable () -> Unit = {
        IconButton(onClick = { view.hapticClick(); showSectionOptions = true }) {
            Icon(Icons.Default.Tune, contentDescription = "Chart Options")
        }
        IconButton(onClick = {
            view.hapticClick()
            viewModel.getExportIntent { intent ->
                context.startActivity(intent)
            }
        }) {
            Icon(Icons.Default.Share, contentDescription = "Export CSV")
        }
    }
    LaunchedEffect(Unit) { onProvideTopBarActions(actions) }
    DisposableEffect(Unit) { onDispose { onProvideTopBarActions {} } }

    FpsSessionDetailContent(
        state = state,
        showSectionOptions = showSectionOptions,
        onDismissSectionOptions = { showSectionOptions = false },
    )
}

@Composable
private fun FpsSessionDetailContent(
    state: FpsSessionDetailState,
    showSectionOptions: Boolean = false,
    onDismissSectionOptions: () -> Unit = {},
) {
    val session = state.session
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val sectionVisibility = remember {
        mutableStateMapOf<ChartSection, Boolean>().apply { ChartSection.entries.forEach { put(it, true) } }
    }

    if (showSectionOptions) {
        SectionOptionsDialog(
            visibility = sectionVisibility,
            onDismiss = onDismissSectionOptions,
        )
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val records = state.records
    if (records.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(stringResource(R.string.no_recording_sessions), color = MaterialTheme.colorScheme.outline)
        }
        return
    }

    ProvideVicoTheme(rememberM3VicoTheme()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.deviceInfo?.let { DeviceInfoCard(it) }
            if (session != null) SessionHeaderCard(session, dateFormat)
            StatsGridCard(records)
            AppSwitchTimeline(records)
            if (sectionVisibility[ChartSection.FPS_TEMP] != false) FpsTempChart(records)
            if (sectionVisibility[ChartSection.FRAME_TIME] != false) FrameTimeChart(records)
            if (sectionVisibility[ChartSection.CPU_GPU] != false) CpuGpuUsageChart(records)
            if (sectionVisibility[ChartSection.CPU_CORE_LOAD] != false) CpuCoreLoadChart(records)
            if (sectionVisibility[ChartSection.GPU_FREQ] != false) GpuFreqChart(records)
            if (sectionVisibility[ChartSection.CPU_FREQ] != false) CpuCoreFreqChart(records)
            if (sectionVisibility[ChartSection.JANK] != false) JankChart(records)
            if (sectionVisibility[ChartSection.POWER] != false) PowerBatteryChart(records)
            if (sectionVisibility[ChartSection.CURRENT] != false) BatteryCurrentChart(records)
            if (sectionVisibility[ChartSection.TEMPERATURE] != false) TemperatureChart(records)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ---- Device Info Card ----

@Composable
private fun DeviceInfoCard(info: SessionDeviceInfo) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            DeviceInfoCell(stringResource(R.string.fps_detail_platform), info.cpuName)
            DeviceInfoCell(stringResource(R.string.fps_detail_model), info.deviceName)
            DeviceInfoCell(stringResource(R.string.fps_detail_os), info.osVersion)
        }
    }
}

@Composable
private fun DeviceInfoCell(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 120.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---- Session Header ----

@Composable
private fun SessionHeaderCard(session: FpsWatchSession, dateFormat: SimpleDateFormat) {
    val context = LocalContext.current
    val appIcon: Drawable? = remember(session.packageName) {
        if (session.packageName.isEmpty()) null
        else try { context.packageManager.getApplicationIcon(session.packageName) } catch (_: Exception) { null }
    }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            if (appIcon != null) {
                val bitmap = remember(appIcon) { appIcon.toBitmap(192, 192) }
                Image(
                    painter = BitmapPainter(bitmap.asImageBitmap()),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // App name
                Text(
                    session.appName.ifEmpty { session.packageName.ifEmpty { stringResource(R.string.fps_unknown_app) } },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Package name
                if (session.packageName.isNotEmpty()) {
                    Text(
                        session.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                // App version
                if (session.packageVersion.isNotEmpty()) {
                    InfoRow(stringResource(R.string.fps_detail_app_version), session.packageVersion)
                }
                // Duration
                InfoRow(stringResource(R.string.duration), formatDuration(session.durationSeconds))
                // Created time
                InfoRow(stringResource(R.string.fps_detail_created), dateFormat.format(Date(session.beginTime)))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            fontSize = 10.sp,
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }
}

// ---- Stats Grid ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsGridCard(records: List<FpsFrameRecord>) {
    val fpsList = records.map { it.fps }
    val maxFps = fpsList.maxOrNull() ?: 0.0
    val minFps = fpsList.minOrNull() ?: 0.0
    val avgFps = fpsList.average()
    val variance = if (fpsList.size > 1) sqrt(fpsList.sumOf { (it - avgFps) * (it - avgFps) } / fpsList.size) else 0.0
    val smoothness = if (fpsList.isNotEmpty()) fpsList.count { it >= 45.0 } * 100.0 / fpsList.size else 0.0
    val fivePercentLow = if (fpsList.size >= 20) {
        fpsList.sorted().take((fpsList.size * 0.05).toInt().coerceAtLeast(1)).average()
    } else fpsList.minOrNull() ?: 0.0
    val maxTemp = records.maxOfOrNull { it.cpuTemp } ?: 0.0
    val powers = records.map { kotlin.math.abs(it.powerW) }.filter { it > 0 }
    val avgPower = if (powers.isNotEmpty()) powers.average() else 0.0

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        FlowRow(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 4,
        ) {
            StatCell(stringResource(R.string.fps_stat_max), "%.1f".format(maxFps), "FPS", fpsStatColor(maxFps))
            StatCell(stringResource(R.string.fps_stat_min), "%.1f".format(minFps), "FPS", fpsStatColor(minFps))
            StatCell(stringResource(R.string.fps_stat_avg), "%.1f".format(avgFps), "FPS", fpsStatColor(avgFps))
            StatCell(stringResource(R.string.fps_stat_variance), "%.1f".format(variance), "FPS", MaterialTheme.colorScheme.onSurfaceVariant)
            StatCell(stringResource(R.string.fps_stat_smoothness), "%.1f%%".format(smoothness), stringResource(R.string.fps_stat_smoothness_unit), smoothnessColor(smoothness))
            StatCell(stringResource(R.string.fps_stat_low), "%.1f".format(fivePercentLow), "FPS", fpsStatColor(fivePercentLow))
            if (maxTemp > 0) StatCell(stringResource(R.string.fps_stat_max_temp), "%.1f".format(maxTemp), "°C", tempStatColor(maxTemp))
            if (avgPower > 0) StatCell(stringResource(R.string.fps_stat_avg_power), "%.2f".format(avgPower), "W", PowerColor)
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, unit: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontSize = 9.sp)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor)
        if (unit.isNotEmpty()) Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontSize = 9.sp)
    }
}

// ---- App Switch Timeline ----

/**
 * Shows a horizontal timeline of app switches during the recording.
 * Each segment shows the app icon + name and relative time range.
 */
@Composable
private fun AppSwitchTimeline(records: List<FpsFrameRecord>) {
    // Build list of app segments: (packageName, startIndex, endIndex)
    val segments = remember(records) {
        if (records.isEmpty()) return@remember emptyList()
        val result = mutableListOf<Triple<String, Int, Int>>()
        var currentPkg = records.first().packageName
        var startIdx = 0
        for (i in records.indices) {
            val pkg = records[i].packageName
            if (pkg != currentPkg && pkg.isNotEmpty()) {
                if (currentPkg.isNotEmpty()) result.add(Triple(currentPkg, startIdx, i - 1))
                currentPkg = pkg
                startIdx = i
            }
        }
        if (currentPkg.isNotEmpty()) result.add(Triple(currentPkg, startIdx, records.size - 1))
        result
    }

    if (segments.size < 2) return // No app switch — nothing to show

    val context = LocalContext.current
    val startTs = records.first().timestamp

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "App Timeline",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                segments.forEach { (pkg, startIdx, endIdx) ->
                    val icon: Bitmap? = remember(pkg) {
                        try {
                            val drawable = context.packageManager.getApplicationIcon(pkg)
                            drawable.toBitmap(192, 192)
                        } catch (_: Exception) { null }
                    }
                    val appName = remember(pkg) {
                        try {
                            val ai = context.packageManager.getApplicationInfo(pkg, 0)
                            context.packageManager.getApplicationLabel(ai).toString()
                        } catch (_: Exception) { pkg.substringAfterLast('.') }
                    }
                    val fromSec = (records[startIdx].timestamp - startTs) / 1000
                    val toSec = (records[endIdx].timestamp - startTs) / 1000
                    val timeLabel = "${fromSec}s-${toSec}s"

                    Row(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        if (icon != null) {
                            Image(
                                painter = BitmapPainter(icon.asImageBitmap()),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)),
                            )
                        }
                        Column {
                            Text(
                                appName,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                timeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- Vico Chart Core ----

private fun timeFormatter(records: List<FpsFrameRecord>): CartesianValueFormatter {
    if (records.isEmpty()) return CartesianValueFormatter.decimal()
    val startTs = records.first().timestamp
    return CartesianValueFormatter { _, x, _ ->
        val sec = ((records.getOrNull(x.toInt())?.timestamp ?: startTs) - startTs) / 1000
        when {
            sec >= 3600 -> "%d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)
            sec >= 60 -> "%dm%02ds".format(sec / 60, sec % 60)
            else -> "${sec}s"
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    allLabels: List<String>? = null,
    allColors: List<Color>? = null,
    seriesVisibility: SnapshotStateMap<String, Boolean>? = null,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    @Suppress("AssignedValueIsNeverRead")
    var showSeriesDialog by remember { mutableStateOf(false) }

    if (showSeriesDialog && allLabels != null && allColors != null && seriesVisibility != null) {
        SeriesSelectDialog(
            labels = allLabels,
            colors = allColors,
            visibility = seriesVisibility,
            onDismiss = { showSeriesDialog = false },
        )
    }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (allLabels != null && allLabels.size > 1 && seriesVisibility != null) {
                    IconButton(
                        onClick = { view.hapticClick(); showSeriesDialog = true },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Series Options",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SeriesSelectDialog(
    labels: List<String>,
    colors: List<Color>,
    visibility: SnapshotStateMap<String, Boolean>,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fps_chart_series_options)) },
        text = {
            Column {
                labels.forEachIndexed { idx, label ->
                    val checked = visibility[label] != false
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .hapticClickable { visibility[label] = !checked },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { visibility[label] = it })
                        Spacer(Modifier.width(4.dp))
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(colors[idx]),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { view.hapticClick(); onDismiss() }) { Text(stringResource(R.string.fps_confirm)) } },
    )
}

@Composable
private fun VicoLineChart(
    records: List<FpsFrameRecord>,
    seriesData: List<List<Number>>,
    seriesColors: List<Color>,
    seriesLabels: List<String>,
    yAxisSuffix: String = "",
    fixedMaxY: Double? = null,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val bottomFormatter = remember(records) { timeFormatter(records) }
    val startFormatter = if (yAxisSuffix.isNotEmpty()) {
        CartesianValueFormatter { _, value, _ -> "${value.toInt()}$yAxisSuffix" }
    } else {
        CartesianValueFormatter.decimal()
    }

    LaunchedEffect(seriesData) {
        if (seriesData.isEmpty() || seriesData.all { it.isEmpty() }) return@LaunchedEffect
        modelProducer.runTransaction {
            lineSeries { seriesData.forEach { data -> series(data) } }
            extras { it[LegendLabelKey] = seriesLabels }
        }
    }

    val legendLabel = rememberTextComponent(
        style = androidx.compose.ui.text.TextStyle(color = vicoTheme.textColor, fontSize = 11.sp),
    )

    val markerLabelColor = MaterialTheme.colorScheme.onSurface
    val guidelineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val gridLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val marker = rememberDefaultCartesianMarker(
        label = rememberTextComponent(
            style = androidx.compose.ui.text.TextStyle(color = markerLabelColor, fontSize = 10.sp),
        ),
        indicator = { color -> ShapeComponent(Fill(color), CircleShape) },
        indicatorSize = 6.dp,
        guideline = rememberLineComponent(fill = Fill(guidelineColor), thickness = 1.dp),
    )

    val axisLabel = rememberTextComponent(
        style = androidx.compose.ui.text.TextStyle(color = vicoTheme.textColor, fontSize = 8.sp),
    )

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                LineCartesianLayer.LineProvider.series(
                    seriesColors.map { color ->
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(Fill(color)),
                            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 0.75.dp),
                        )
                    }
                ),
                rangeProvider = if (fixedMaxY != null)
                    CartesianLayerRangeProvider.fixed(maxY = fixedMaxY)
                else
                    CartesianLayerRangeProvider.auto(),
            ),
            startAxis = VerticalAxis.rememberStart(
                label = axisLabel,
                valueFormatter = startFormatter,
                guideline = rememberLineComponent(fill = Fill(gridLineColor), thickness = 1.dp),
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = axisLabel,
                valueFormatter = bottomFormatter,
                guideline = null,
            ),
            marker = marker,
            markerController = CartesianMarkerController.rememberToggleOnTap(),
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

// ---- Individual Charts ----

@Composable
private fun FpsTempChart(records: List<FpsFrameRecord>) {
    val hasTemp = records.any { it.cpuTemp > 0.0 }
    val hasCpu = records.any { it.cpuLoad > 0.0 }
    val hasGpu = records.any { it.gpuLoad > 0.0 }

    val allLabels = mutableListOf("FPS")
    val allColors = mutableListOf(FpsColor)
    if (hasTemp) { allLabels.add("TEMP(\u00B0C)"); allColors.add(TempColor) }
    if (hasCpu) { allLabels.add("CPU(%)"); allColors.add(CpuColor) }
    if (hasGpu) { allLabels.add("GPU(%)"); allColors.add(GpuColor) }

    val seriesVis = remember { mutableStateMapOf<String, Boolean>() }

    val visLabels = mutableListOf<String>()
    val visColors = mutableListOf<Color>()
    val visData = mutableListOf<List<Number>>()
    if (seriesVis["FPS"] != false) { visLabels.add("FPS"); visColors.add(FpsColor); visData.add(records.map { it.fps }) }
    if (hasTemp && seriesVis["TEMP(\u00B0C)"] != false) { visLabels.add("TEMP(\u00B0C)"); visColors.add(TempColor); visData.add(records.map { it.cpuTemp }) }
    if (hasCpu && seriesVis["CPU(%)"] != false) { visLabels.add("CPU(%)"); visColors.add(CpuColor); visData.add(records.map { it.cpuLoad }) }
    if (hasGpu && seriesVis["GPU(%)"] != false) { visLabels.add("GPU(%)"); visColors.add(GpuColor); visData.add(records.map { it.gpuLoad }) }

    if (visData.isEmpty()) return

    ChartCard("FPS", allLabels, allColors, seriesVis) {
        key(visLabels.joinToString()) {
            VicoLineChart(records, visData, visColors, visLabels)
        }
    }
}

@Composable
private fun FrameTimeChart(records: List<FpsFrameRecord>) {
    if (records.none { it.maxFrameTimeMs > 0 }) return
    val maxFt = records.maxOfOrNull { it.maxFrameTimeMs } ?: 0
    val avgFt = records.filter { it.maxFrameTimeMs > 0 }.map { it.maxFrameTimeMs }.average()
    val maxLabel = stringResource(R.string.fps_stat_max)
    val avgLabel = stringResource(R.string.fps_stat_avg)
    val frameTimeLabel = stringResource(R.string.fps_series_frame_time)
    val subtitle = "$maxLabel: ${maxFt}ms  $avgLabel: %.1fms".format(avgFt)

    ChartCard(stringResource(R.string.fps_chart_frame_time) + "\n$subtitle") {
        VicoLineChart(
            records, listOf(records.map { it.maxFrameTimeMs }),
            listOf(ChartRed), listOf(frameTimeLabel),
            yAxisSuffix = "ms",
        )
    }
}

@Composable
private fun CpuGpuUsageChart(records: List<FpsFrameRecord>) {
    if (records.none { it.cpuLoad > 0.0 }) return
    val hasGpu = records.any { it.gpuLoad > 0.0 }

    val allLabels = mutableListOf("CPU(%)")
    val allColors = mutableListOf(CpuColor)
    if (hasGpu) { allLabels.add("GPU(%)"); allColors.add(GpuColor) }

    val seriesVis = remember { mutableStateMapOf<String, Boolean>() }

    val visLabels = mutableListOf<String>()
    val visColors = mutableListOf<Color>()
    val visData = mutableListOf<List<Number>>()
    if (seriesVis["CPU(%)"] != false) { visLabels.add("CPU(%)"); visColors.add(CpuColor); visData.add(records.map { it.cpuLoad }) }
    if (hasGpu && seriesVis["GPU(%)"] != false) { visLabels.add("GPU(%)"); visColors.add(GpuColor); visData.add(records.map { it.gpuLoad }) }

    if (visData.isEmpty()) return

    ChartCard(stringResource(R.string.fps_chart_cpu_usage), allLabels, allColors, seriesVis) {
        key(visLabels.joinToString()) {
            VicoLineChart(records, visData, visColors, visLabels, yAxisSuffix = "%", fixedMaxY = 100.0)
        }
    }
}

@Composable
private fun GpuFreqChart(records: List<FpsFrameRecord>) {
    if (records.none { it.gpuFreqMhz > 0 }) return
    val hasUsage = records.any { it.gpuLoad > 0.0 }

    val freqLabel = stringResource(R.string.fps_series_freq)
    val usageLabel = stringResource(R.string.fps_series_usage)

    val allLabels = mutableListOf(freqLabel)
    val allColors = mutableListOf(GpuColor)
    if (hasUsage) { allLabels.add(usageLabel); allColors.add(Color(0xFF5C6BC0)) }

    val seriesVis = remember { mutableStateMapOf<String, Boolean>() }

    val visLabels = mutableListOf<String>()
    val visColors = mutableListOf<Color>()
    val visData = mutableListOf<List<Number>>()
    if (seriesVis[freqLabel] != false) { visLabels.add(freqLabel); visColors.add(GpuColor); visData.add(records.map { it.gpuFreqMhz }) }
    if (hasUsage && seriesVis[usageLabel] != false) { visLabels.add(usageLabel); visColors.add(Color(0xFF5C6BC0)); visData.add(records.map { it.gpuLoad }) }

    if (visData.isEmpty()) return

    ChartCard(stringResource(R.string.fps_chart_gpu_freq), allLabels, allColors, seriesVis) {
        key(visLabels.joinToString()) {
            VicoLineChart(records, visData, visColors, visLabels)
        }
    }
}

@Composable
private fun CpuCoreLoadChart(records: List<FpsFrameRecord>) {
    if (records.none { it.cpuCoreLoads.isNotEmpty() }) return
    val maxCores = records.maxOf { it.cpuCoreLoads.size }
    if (maxCores == 0) return
    val hasTotal = records.any { it.cpuLoad > 0.0 }

    // Total first, then per-core
    val allLabels = mutableListOf<String>()
    val allColors = mutableListOf<Color>()
    if (hasTotal) { allLabels.add("CPU(%)"); allColors.add(CpuColor) }
    for (i in 0 until maxCores) { allLabels.add("Core $i"); allColors.add(CoreColors[i % CoreColors.size]) }

    val seriesVis = remember { mutableStateMapOf<String, Boolean>() }

    val visLabels = mutableListOf<String>()
    val visColors = mutableListOf<Color>()
    val visData = mutableListOf<List<Number>>()
    if (hasTotal && seriesVis["CPU(%)"] != false) {
        visLabels.add("CPU(%)"); visColors.add(CpuColor); visData.add(records.map { it.cpuLoad })
    }
    for (i in 0 until maxCores) {
        val label = "Core $i"
        if (seriesVis[label] != false) {
            visLabels.add(label); visColors.add(CoreColors[i % CoreColors.size])
            visData.add(records.map { it.cpuCoreLoads.getOrElse(i) { 0.0 } })
        }
    }

    if (visData.isEmpty()) return

    ChartCard(stringResource(R.string.fps_chart_cpu_core_load), allLabels, allColors, seriesVis) {
        key(visLabels.joinToString()) {
            VicoLineChart(records, visData, visColors, visLabels, yAxisSuffix = "%", fixedMaxY = 100.0)
        }
    }
}

@Composable
private fun CpuCoreFreqChart(records: List<FpsFrameRecord>) {
    if (records.none { it.cpuCoreFreqsMhz.isNotEmpty() }) return
    val maxCores = records.maxOf { it.cpuCoreFreqsMhz.size }
    if (maxCores == 0) return

    val allLabels = (0 until maxCores).map { "Core $it" }
    val allColors = (0 until maxCores).map { CoreColors[it % CoreColors.size] }
    val seriesVis = remember { mutableStateMapOf<String, Boolean>() }

    val visLabels = mutableListOf<String>()
    val visColors = mutableListOf<Color>()
    val visData = mutableListOf<List<Number>>()
    for (i in 0 until maxCores) {
        val label = "Core $i"
        if (seriesVis[label] != false) {
            visLabels.add(label); visColors.add(CoreColors[i % CoreColors.size])
            visData.add(records.map { it.cpuCoreFreqsMhz.getOrElse(i) { 0L } })
        }
    }

    if (visData.isEmpty()) return

    ChartCard(stringResource(R.string.fps_chart_cpu_freq), allLabels, allColors, seriesVis) {
        key(visLabels.joinToString()) {
            VicoLineChart(records, visData, visColors, visLabels, yAxisSuffix = "MHz")
        }
    }
}

@Composable
private fun JankChart(records: List<FpsFrameRecord>) {
    if (records.none { it.jankCount > 0 || it.bigJankCount > 0 }) return

    // Daemon stores cumulative jank counts since last app switch.
    // Convert to per-window increments so spikes appear at the moment jank occurred.
    fun toDelta(selector: (FpsFrameRecord) -> Int): List<Int> =
        records.indices.map { i ->
            if (i == 0) records[i].let(selector)
            else maxOf(0, records[i].let(selector) - records[i - 1].let(selector))
        }

    val jankDeltas = remember(records) { toDelta { it.jankCount } }
    val bigJankDeltas = remember(records) { toDelta { it.bigJankCount } }
    val hasBig = bigJankDeltas.any { it > 0 }

    val jankLabel = stringResource(R.string.fps_series_jank)
    val bigJankLabel = stringResource(R.string.fps_series_big_jank)
    val totalLabel = stringResource(R.string.fps_stat_total)

    val allLabels = mutableListOf(jankLabel)
    val allColors = mutableListOf(ChartRed)
    if (hasBig) { allLabels.add(bigJankLabel); allColors.add(Color(0xFFD32F2F)) }

    val seriesVis = remember { mutableStateMapOf<String, Boolean>() }

    val visLabels = mutableListOf<String>()
    val visColors = mutableListOf<Color>()
    val visData = mutableListOf<List<Number>>()
    if (seriesVis[jankLabel] != false) { visLabels.add(jankLabel); visColors.add(ChartRed); visData.add(jankDeltas) }
    if (hasBig && seriesVis[bigJankLabel] != false) { visLabels.add(bigJankLabel); visColors.add(Color(0xFFD32F2F)); visData.add(bigJankDeltas) }

    if (visData.isEmpty()) return

    val totalJank = jankDeltas.sum()
    val totalBig = bigJankDeltas.sum()
    val subtitle = if (hasBig) "$totalLabel: $jankLabel=$totalJank  $bigJankLabel=$totalBig" else "$totalLabel: $totalJank"

    ChartCard(
        stringResource(R.string.fps_chart_jank) + "\n$subtitle",
        allLabels, allColors, seriesVis,
    ) {
        key(visLabels.joinToString()) {
            VicoLineChart(records, visData, visColors, visLabels)
        }
    }
}

@Composable
private fun PowerBatteryChart(records: List<FpsFrameRecord>) {
    val hasPower = records.any { it.powerW != 0.0 }
    val hasBattery = records.any { it.batteryCapacity > 0 }
    if (!hasPower && !hasBattery) return

    val powerLabel = stringResource(R.string.fps_series_power)
    val capacityLabel = stringResource(R.string.fps_series_capacity)
    val maxLabel = stringResource(R.string.fps_stat_max)
    val minLabel = stringResource(R.string.fps_stat_min)
    val avgLabel = stringResource(R.string.fps_stat_avg)

    val allLabels = mutableListOf<String>()
    val allColors = mutableListOf<Color>()
    if (hasPower) { allLabels.add(powerLabel); allColors.add(PowerColor) }
    if (hasBattery) { allLabels.add(capacityLabel); allColors.add(BatteryColor) }

    val seriesVis = remember { mutableStateMapOf<String, Boolean>() }

    val visLabels = mutableListOf<String>()
    val visColors = mutableListOf<Color>()
    val visData = mutableListOf<List<Number>>()
    if (hasPower && seriesVis[powerLabel] != false) {
        visLabels.add(powerLabel); visColors.add(PowerColor)
        visData.add(records.map { kotlin.math.abs(it.powerW) })
    }
    if (hasBattery && seriesVis[capacityLabel] != false) { visLabels.add(capacityLabel); visColors.add(BatteryColor); visData.add(records.map { it.batteryCapacity }) }

    if (visData.isEmpty()) return

    val subtitle = if (hasPower) {
        val pws = records.map { kotlin.math.abs(it.powerW) }.filter { it > 0 }
        if (pws.isNotEmpty()) "$maxLabel: %.2fW  $minLabel: %.2fW  $avgLabel: %.2fW".format(pws.max(), pws.min(), pws.average()) else ""
    } else ""

    ChartCard(stringResource(R.string.fps_chart_power) + if (subtitle.isNotEmpty()) "\n$subtitle" else "", allLabels, allColors, seriesVis) {
        key(visLabels.joinToString()) {
            VicoLineChart(records, visData, visColors, visLabels)
        }
    }
}

@Composable
private fun BatteryCurrentChart(records: List<FpsFrameRecord>) {
    if (records.none { it.batteryCurrentMa != 0 }) return
    val curData = records.map { it.batteryCurrentMa }
    val maxLabel = stringResource(R.string.fps_stat_max)
    val minLabel = stringResource(R.string.fps_stat_min)
    val avgLabel = stringResource(R.string.fps_stat_avg)
    val currentLabel = stringResource(R.string.fps_series_current)
    val subtitle = "$maxLabel: ${curData.max()}mA  $minLabel: ${curData.min()}mA  $avgLabel: ${curData.average().toInt()}mA"

    ChartCard(stringResource(R.string.fps_chart_battery_current) + "\n$subtitle") {
        VicoLineChart(records, listOf(curData), listOf(CurrentColor), listOf(currentLabel), yAxisSuffix = "mA")
    }
}

@Composable
private fun TemperatureChart(records: List<FpsFrameRecord>) {
    val hasCpu = records.any { it.cpuTemp > 0.0 }
    val hasBat = records.any { it.batteryTemp > 0.0 }
    if (!hasCpu && !hasBat) return

    val batTempLabel = stringResource(R.string.fps_series_bat_temp)
    val maxLabel = stringResource(R.string.fps_stat_max)
    val minLabel = stringResource(R.string.fps_stat_min)
    val avgLabel = stringResource(R.string.fps_stat_avg)

    val allLabels = mutableListOf<String>()
    val allColors = mutableListOf<Color>()
    if (hasCpu) { allLabels.add("CPU(\u00B0C)"); allColors.add(TempColor) }
    if (hasBat) { allLabels.add(batTempLabel); allColors.add(BatTempColor) }

    val seriesVis = remember { mutableStateMapOf<String, Boolean>() }

    val visLabels = mutableListOf<String>()
    val visColors = mutableListOf<Color>()
    val visData = mutableListOf<List<Number>>()
    if (hasCpu && seriesVis["CPU(\u00B0C)"] != false) { visLabels.add("CPU(\u00B0C)"); visColors.add(TempColor); visData.add(records.map { it.cpuTemp }) }
    if (hasBat && seriesVis[batTempLabel] != false) { visLabels.add(batTempLabel); visColors.add(BatTempColor); visData.add(records.map { it.batteryTemp }) }

    if (visData.isEmpty()) return

    val cpuTemps = records.map { it.cpuTemp }.filter { it > 0 }
    val subtitle = if (cpuTemps.isNotEmpty()) {
        "$maxLabel: %.1f\u00B0C  $minLabel: %.1f\u00B0C  $avgLabel: %.1f\u00B0C".format(cpuTemps.max(), cpuTemps.min(), cpuTemps.average())
    } else ""

    ChartCard(
        stringResource(R.string.fps_chart_temperature) + if (subtitle.isNotEmpty()) "\n$subtitle" else "",
        allLabels, allColors, seriesVis,
    ) {
        key(visLabels.joinToString()) {
            VicoLineChart(records, visData, visColors, visLabels, yAxisSuffix = "\u00B0C")
        }
    }
}

// ---- Section Options Dialog ----

@Composable
private fun SectionOptionsDialog(
    visibility: MutableMap<ChartSection, Boolean>,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chart Options") },
        text = {
            Column {
                ChartSection.entries.forEach { section ->
                    Row(Modifier.fillMaxWidth().hapticClickable { visibility[section] = visibility[section] == false }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = visibility[section] != false, onCheckedChange = { visibility[section] = it })
                        Spacer(Modifier.width(8.dp))
                        Text(section.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { view.hapticClick(); onDismiss() }) { Text(stringResource(R.string.fps_confirm)) } },
    )
}

// ---- Helpers ----

private fun fpsStatColor(fps: Double): Color = when {
    fps >= 50.0 -> ChartGreen; fps >= 30.0 -> ChartYellow; else -> ChartRed
}

private fun smoothnessColor(pct: Double): Color = when {
    pct >= 80.0 -> ChartGreen; pct >= 50.0 -> ChartYellow; else -> ChartRed
}

private fun tempStatColor(temp: Double): Color = when {
    temp < 45.0 -> ChartGreen; temp < 60.0 -> ChartYellow; else -> ChartRed
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    return when {
        h > 0 -> "%d:%02d:%02d".format(h, m, s); m > 0 -> "%d:%02d".format(m, s); else -> "${s}s"
    }
}

private fun Drawable.toBitmap(width: Int, height: Int): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val bmp = createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}
