package com.cloudorz.openmonitor.feature.fps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.patrykandpatrick.vico.compose.common.rememberHorizontalLegend
import com.patrykandpatrick.vico.compose.common.vicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
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
    GPU_FREQ("GPU Freq"),
    CPU_FREQ("CPU Freq"),
    POWER("Power"),
    CURRENT("Current"),
    TEMPERATURE("Temperature"),
}

@Composable
fun FpsSessionDetailScreen(
    sessionId: Long,
    onBack: () -> Unit = {},
    viewModel: FpsSessionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    FpsSessionDetailContent(
        state = state,
        onBack = onBack,
        onExport = {
            viewModel.getExportIntent { intent ->
                context.startActivity(intent)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FpsSessionDetailContent(
    state: FpsSessionDetailState,
    onBack: () -> Unit,
    onExport: () -> Unit = {},
) {
    val session = state.session
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val sectionVisibility = remember {
        mutableStateMapOf<ChartSection, Boolean>().apply { ChartSection.entries.forEach { put(it, true) } }
    }
    var showSectionOptions by remember { mutableStateOf(false) }

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
                actions = {
                    IconButton(onClick = { showSectionOptions = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Chart Options")
                    }
                    IconButton(onClick = onExport) {
                        Icon(Icons.Default.Share, contentDescription = "CSV")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { paddingValues ->
        if (showSectionOptions) {
            SectionOptionsDialog(
                visibility = sectionVisibility,
                onDismiss = { showSectionOptions = false },
            )
        }
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val records = state.records
        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                Text(stringResource(R.string.no_recording_sessions), color = MaterialTheme.colorScheme.outline)
            }
            return@Scaffold
        }

        ProvideVicoTheme(rememberM3VicoTheme()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (session != null) SessionHeaderCard(session, dateFormat)
                StatsGridCard(records)
                if (sectionVisibility[ChartSection.FPS_TEMP] != false) FpsTempChart(records)
                if (sectionVisibility[ChartSection.FRAME_TIME] != false) FrameTimeChart(records)
                if (sectionVisibility[ChartSection.CPU_GPU] != false) CpuGpuUsageChart(records)
                if (sectionVisibility[ChartSection.GPU_FREQ] != false) GpuFreqChart(records)
                if (sectionVisibility[ChartSection.CPU_FREQ] != false) CpuCoreFreqChart(records)
                if (sectionVisibility[ChartSection.POWER] != false) PowerBatteryChart(records)
                if (sectionVisibility[ChartSection.CURRENT] != false) BatteryCurrentChart(records)
                if (sectionVisibility[ChartSection.TEMPERATURE] != false) TemperatureChart(records)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
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
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (appIcon != null) {
                val bitmap = remember(appIcon) { appIcon.toBitmap(96, 96) }
                Image(
                    painter = BitmapPainter(bitmap.asImageBitmap()),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(dateFormat.format(Date(session.beginTime)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (session.packageName.isNotEmpty()) {
                    Text(
                        session.appName.ifEmpty { session.packageName },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (session.appName.isNotEmpty()) {
                        Text(session.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        stringResource(R.string.duration) + ": " + formatDuration(session.durationSeconds),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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
    val totalJank = records.sumOf { it.jankCount }
    val maxTemp = records.maxOfOrNull { it.cpuTemp } ?: 0.0
    val powers = records.map { it.powerW }.filter { it > 0 }
    val avgPower = if (powers.isNotEmpty()) powers.average() else 0.0

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        FlowRow(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 4,
        ) {
            StatCell("MAX", "%.1f".format(maxFps), "FPS", fpsStatColor(maxFps))
            StatCell("MIN", "%.1f".format(minFps), "FPS", fpsStatColor(minFps))
            StatCell("AVG", "%.1f".format(avgFps), "FPS", fpsStatColor(avgFps))
            StatCell("VARIANCE", "%.1f".format(variance), "FPS", MaterialTheme.colorScheme.onSurfaceVariant)
            StatCell("\u226545FPS", "%.1f%%".format(smoothness), "Smoothness", smoothnessColor(smoothness))
            StatCell("5% Low", "%.1f".format(fivePercentLow), "FPS", fpsStatColor(fivePercentLow))
            StatCell("Jank", "$totalJank", "", ChartRed)
            if (maxTemp > 0) StatCell("MAX", "%.1f".format(maxTemp), "Temp", tempStatColor(maxTemp))
            if (avgPower > 0) StatCell("AVG", "%.2f".format(avgPower), "Power(W)", PowerColor)
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
                        onClick = { showSeriesDialog = true },
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
                            .clickable { visibility[label] = !checked },
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.fps_confirm)) } },
    )
}

@Composable
private fun VicoLineChart(
    records: List<FpsFrameRecord>,
    seriesData: List<List<Number>>,
    seriesColors: List<Color>,
    seriesLabels: List<String>,
    yAxisSuffix: String = "",
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
    val marker = rememberDefaultCartesianMarker(
        label = rememberTextComponent(
            style = androidx.compose.ui.text.TextStyle(color = markerLabelColor, fontSize = 10.sp),
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
            startAxis = VerticalAxis.rememberStart(valueFormatter = startFormatter),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomFormatter),
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

    // Build visible series
    val visLabels = mutableListOf<String>()
    val visColors = mutableListOf<Color>()
    val visData = mutableListOf<List<Number>>()

    if (seriesVis["FPS"] != false) {
        visLabels.add("FPS"); visColors.add(FpsColor); visData.add(records.map { it.fps })
    }
    if (hasTemp && seriesVis["TEMP(\u00B0C)"] != false) {
        visLabels.add("TEMP(\u00B0C)"); visColors.add(TempColor); visData.add(records.map { it.cpuTemp })
    }
    if (hasCpu && seriesVis["CPU(%)"] != false) {
        visLabels.add("CPU(%)"); visColors.add(CpuColor); visData.add(records.map { it.cpuLoad })
    }
    if (hasGpu && seriesVis["GPU(%)"] != false) {
        visLabels.add("GPU(%)"); visColors.add(GpuColor); visData.add(records.map { it.gpuLoad })
    }

    if (visData.isEmpty()) return

    ChartCard("FPS", allLabels, allColors, seriesVis) {
        VicoLineChart(records, visData, visColors, visLabels)
    }
}

@Composable
private fun FrameTimeChart(records: List<FpsFrameRecord>) {
    if (records.none { it.maxFrameTimeMs > 0 }) return
    val maxFt = records.maxOfOrNull { it.maxFrameTimeMs } ?: 0
    val avgFt = records.filter { it.maxFrameTimeMs > 0 }.map { it.maxFrameTimeMs }.average()
    val subtitle = "MAX: ${maxFt}ms  AVG: %.1fms".format(avgFt)

    ChartCard(stringResource(R.string.fps_chart_frame_time) + "\n$subtitle") {
        VicoLineChart(
            records, listOf(records.map { it.maxFrameTimeMs }),
            listOf(ChartRed), listOf("FrameTime(ms)"),
            yAxisSuffix = "ms",
        )
    }
}

@Composable
private fun CpuGpuUsageChart(records: List<FpsFrameRecord>) {
    if (records.none { it.cpuLoad > 0.0 }) return

    val allLabels = mutableListOf("CPU(%)")
    val allColors = mutableListOf(CpuColor)
    val hasGpu = records.any { it.gpuLoad > 0.0 }
    if (hasGpu) { allLabels.add("GPU(%)"); allColors.add(GpuColor) }

    val seriesVis = remember { mutableStateMapOf<String, Boolean>() }
    val visLabels = mutableListOf<String>()
    val visColors = mutableListOf<Color>()
    val visData = mutableListOf<List<Number>>()

    if (seriesVis["CPU(%)"] != false) {
        visLabels.add("CPU(%)"); visColors.add(CpuColor); visData.add(records.map { it.cpuLoad })
    }
    if (hasGpu && seriesVis["GPU(%)"] != false) {
        visLabels.add("GPU(%)"); visColors.add(GpuColor); visData.add(records.map { it.gpuLoad })
    }

    if (visData.isEmpty()) return

    ChartCard(stringResource(R.string.fps_chart_cpu_usage), allLabels, allColors, seriesVis) {
        VicoLineChart(records, visData, visColors, visLabels, yAxisSuffix = "%")
    }
}

@Composable
private fun GpuFreqChart(records: List<FpsFrameRecord>) {
    if (records.none { it.gpuFreqMhz > 0 }) return
    val hasUsage = records.any { it.gpuLoad > 0.0 }

    val allLabels = mutableListOf("Freq(MHz)")
    val allColors = mutableListOf(GpuColor)
    if (hasUsage) { allLabels.add("Usage(%)"); allColors.add(Color(0xFF5C6BC0)) }

    val seriesVis = remember { mutableStateMapOf<String, Boolean>() }
    val visLabels = mutableListOf<String>()
    val visColors = mutableListOf<Color>()
    val visData = mutableListOf<List<Number>>()

    if (seriesVis["Freq(MHz)"] != false) {
        visLabels.add("Freq(MHz)"); visColors.add(GpuColor); visData.add(records.map { it.gpuFreqMhz })
    }
    if (hasUsage && seriesVis["Usage(%)"] != false) {
        visLabels.add("Usage(%)"); visColors.add(Color(0xFF5C6BC0)); visData.add(records.map { it.gpuLoad })
    }

    if (visData.isEmpty()) return

    ChartCard(stringResource(R.string.fps_chart_gpu_freq), allLabels, allColors, seriesVis) {
        VicoLineChart(records, visData, visColors, visLabels)
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
            visLabels.add(label)
            visColors.add(CoreColors[i % CoreColors.size])
            visData.add(records.map { it.cpuCoreFreqsMhz.getOrElse(i) { 0L } })
        }
    }

    if (visData.isEmpty()) return

    ChartCard(stringResource(R.string.fps_chart_cpu_freq), allLabels, allColors, seriesVis) {
        VicoLineChart(records, visData, visColors, visLabels, yAxisSuffix = "MHz")
    }
}

@Composable
private fun PowerBatteryChart(records: List<FpsFrameRecord>) {
    val hasPower = records.any { it.powerW > 0.0 }
    val hasBattery = records.any { it.batteryCapacity > 0 }
    if (!hasPower && !hasBattery) return

    val allLabels = mutableListOf<String>()
    val allColors = mutableListOf<Color>()
    if (hasPower) { allLabels.add("Power(W)"); allColors.add(PowerColor) }
    if (hasBattery) { allLabels.add("Capacity(%)"); allColors.add(BatteryColor) }

    val seriesVis = remember { mutableStateMapOf<String, Boolean>() }
    val visLabels = mutableListOf<String>()
    val visColors = mutableListOf<Color>()
    val visData = mutableListOf<List<Number>>()

    if (hasPower && seriesVis["Power(W)"] != false) {
        visLabels.add("Power(W)"); visColors.add(PowerColor); visData.add(records.map { it.powerW })
    }
    if (hasBattery && seriesVis["Capacity(%)"] != false) {
        visLabels.add("Capacity(%)"); visColors.add(BatteryColor); visData.add(records.map { it.batteryCapacity })
    }

    if (visData.isEmpty()) return

    val subtitle = if (hasPower) {
        val pws = records.map { it.powerW }.filter { it > 0 }
        if (pws.isNotEmpty()) "MAX: %.2fW  MIN: %.2fW  AVG: %.2fW".format(pws.max(), pws.min(), pws.average()) else ""
    } else ""

    ChartCard(stringResource(R.string.fps_chart_power) + if (subtitle.isNotEmpty()) "\n$subtitle" else "", allLabels, allColors, seriesVis) {
        VicoLineChart(records, visData, visColors, visLabels)
    }
}

@Composable
private fun BatteryCurrentChart(records: List<FpsFrameRecord>) {
    if (records.none { it.batteryCurrentMa != 0 }) return
    val curData = records.map { it.batteryCurrentMa }
    val subtitle = "MAX: ${curData.max()}mA  MIN: ${curData.min()}mA  AVG: ${curData.average().toInt()}mA"

    ChartCard(stringResource(R.string.fps_chart_battery_current) + "\n$subtitle") {
        VicoLineChart(records, listOf(curData), listOf(CurrentColor), listOf("Current(mA)"), yAxisSuffix = "mA")
    }
}

@Composable
private fun TemperatureChart(records: List<FpsFrameRecord>) {
    val hasCpu = records.any { it.cpuTemp > 0.0 }
    val hasBat = records.any { it.batteryTemp > 0.0 }
    if (!hasCpu && !hasBat) return

    val allLabels = mutableListOf<String>()
    val allColors = mutableListOf<Color>()
    if (hasCpu) { allLabels.add("CPU(\u00B0C)"); allColors.add(TempColor) }
    if (hasBat) { allLabels.add("Battery(\u00B0C)"); allColors.add(BatTempColor) }

    val seriesVis = remember { mutableStateMapOf<String, Boolean>() }
    val visLabels = mutableListOf<String>()
    val visColors = mutableListOf<Color>()
    val visData = mutableListOf<List<Number>>()

    if (hasCpu && seriesVis["CPU(\u00B0C)"] != false) {
        visLabels.add("CPU(\u00B0C)"); visColors.add(TempColor); visData.add(records.map { it.cpuTemp })
    }
    if (hasBat && seriesVis["Battery(\u00B0C)"] != false) {
        visLabels.add("Battery(\u00B0C)"); visColors.add(BatTempColor); visData.add(records.map { it.batteryTemp })
    }

    if (visData.isEmpty()) return

    val cpuTemps = records.map { it.cpuTemp }.filter { it > 0 }
    val subtitle = if (cpuTemps.isNotEmpty()) {
        "MAX: %.1f\u00B0C  MIN: %.1f\u00B0C  AVG: %.1f\u00B0C".format(cpuTemps.max(), cpuTemps.min(), cpuTemps.average())
    } else ""

    ChartCard(
        stringResource(R.string.fps_chart_temperature) + if (subtitle.isNotEmpty()) "\n$subtitle" else "",
        allLabels, allColors, seriesVis,
    ) {
        VicoLineChart(records, visData, visColors, visLabels, yAxisSuffix = "\u00B0C")
    }
}

// ---- Section Options Dialog ----

@Composable
private fun SectionOptionsDialog(
    visibility: MutableMap<ChartSection, Boolean>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chart Options") },
        text = {
            Column {
                ChartSection.entries.forEach { section ->
                    Row(Modifier.fillMaxWidth().clickable { visibility[section] = visibility[section] == false }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = visibility[section] != false, onCheckedChange = { visibility[section] = it })
                        Spacer(Modifier.width(8.dp))
                        Text(section.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.fps_confirm)) } },
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
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}
