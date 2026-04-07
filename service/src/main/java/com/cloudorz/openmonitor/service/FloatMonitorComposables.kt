package com.cloudorz.openmonitor.service

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.isSystemInDarkTheme
import com.cloudorz.openmonitor.core.model.thermal.ThermalZone
import kotlinx.coroutines.delay

/** Modifier that performs haptic feedback on click. */
@Composable
private fun Modifier.hapticClickable(onClick: () -> Unit): Modifier {
    val view = LocalView.current
    return this.clickable {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        onClick()
    }
}

private val BG: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xAA000000) else Color(0xCCF5F5F5)

private val TextPrimary: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFFFFFF) else Color(0xFF1C1C1E)
private val TextSecondary: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xB3FFFFFF) else Color(0x991C1C1E)
private val CpuColor = Color(0xFF42A5F5)
private val GpuColor = Color(0xFFAB47BC)
private val MemColor = Color(0xFF66BB6A)
private val BatColor = Color(0xFFFFA726)
private val CurrentColor = Color(0xFF29B6F6)

private val MonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
)

@Composable
fun FloatLoadMonitorContent(service: FloatMonitorService) {
    val compact by service.loadMonitorCompact.collectAsState()
    val cpu by service.cpuLoad.collectAsState()
    val gpu by service.gpuLoad.collectAsState()
    val mem by service.memUsed.collectAsState()
    val bat by service.batteryLevel.collectAsState()
    val temp by service.cpuTemp.collectAsState()
    val coreLoads by service.cpuCoreLoads.collectAsState()
    val coreFreqs by service.cpuCoreFreqs.collectAsState()
    val gpuFreq by service.gpuFreqMhz.collectAsState()
    val mA by service.currentMa.collectAsState()
    val batTemp by service.batteryTemp.collectAsState()
    val fps by service.currentFps.collectAsState()
    val memTotalMB by service.memTotalMB.collectAsState()
    val memUsedMB by service.memUsedMB.collectAsState()

    if (compact) {
        // 简洁模式：三个圆环横排
        Box(
            modifier = Modifier
                .background(BG, RoundedCornerShape(8.dp))
                .padding(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val maxFreq = coreFreqs?.maxOrNull()
                FloatRingGauge(
                    percentage = (cpu ?: 0.0).toFloat(),
                    color = CpuColor,
                    label = "CPU",
                    size = 40.dp,
                    bottomLabel = if (maxFreq != null) "${maxFreq}MHz" else "",
                )
                FloatRingGauge(
                    percentage = (gpu ?: 0.0).toFloat(),
                    color = GpuColor,
                    label = "GPU",
                    size = 40.dp,
                    bottomLabel = if (gpuFreq != null && gpuFreq!! > 0) "${gpuFreq}MHz" else "0MHz",
                )
                FloatRingGauge(
                    percentage = (mem ?: 0.0).toFloat(),
                    color = MemColor,
                    label = "RAM",
                    size = 40.dp,
                    bottomLabel = if (temp != null && temp!! > 0) "%.1f\u00B0C".format(temp) else "",
                )
            }
        }
    } else {
        // 完整模式：左侧圆环 + 右侧详细文字
        Box(
            modifier = Modifier
                .width(240.dp)
                .background(BG, RoundedCornerShape(10.dp))
                .padding(8.dp),
        ) {
            Row {
                // 左侧三个圆环
                Column(
                    modifier = Modifier.width(58.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val maxFreq = coreFreqs?.maxOrNull()
                    FloatRingGauge(
                        percentage = (cpu ?: 0.0).toFloat(),
                        color = CpuColor,
                        label = "CPU",
                        size = 46.dp,
                        bottomLabel = if (maxFreq != null) "${maxFreq}MHz" else "",
                    )
                    FloatRingGauge(
                        percentage = (gpu ?: 0.0).toFloat(),
                        color = GpuColor,
                        label = "GPU",
                        size = 46.dp,
                        bottomLabel = if (gpuFreq != null && gpuFreq!! > 0) "${gpuFreq}MHz" else "0MHz",
                    )
                    FloatRingGauge(
                        percentage = (mem ?: 0.0).toFloat(),
                        color = MemColor,
                        label = "${(mem ?: 0.0).toInt()}%",
                        size = 46.dp,
                        bottomLabel = if (temp != null && temp!! > 0) "%.1f\u00B0C".format(temp) else "",
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                // 右侧详细信息
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // RAM
                    val memPct = (mem ?: 0.0).toInt()
                    DetailLine("#RAM", "%d%%  %.0fMB/%.0fMB".format(memPct, memUsedMB, memTotalMB), MemColor)
                    // CPU temp
                    val tempVal = temp
                    DetailLine("#CPU", if (tempVal != null && tempVal > 0) "%.1f\u00B0C".format(tempVal) else "--\u00B0C", if (tempVal != null) tempColor(tempVal) else TextSecondary)
                    // Per-core info
                    val cores = coreLoads
                    val freqs = coreFreqs
                    if (!cores.isNullOrEmpty()) {
                        // 分组显示核心
                        val grouped = groupCoresByFreq(freqs)
                        grouped.forEach { (range, freq) ->
                            DetailLine(range, if (freq > 0) "${freq}MHz" else "", TextSecondary)
                        }
                        // 各核心负载
                        val coreTexts = cores.mapIndexed { i, load -> "C$i %d%%".format(load.toInt()) }
                        val rows = coreTexts.chunked(4)
                        rows.forEach { row ->
                            Text(
                                text = row.joinToString("  "),
                                style = MonoStyle.copy(fontSize = 7.sp, color = TextSecondary),
                            )
                        }
                    }
                    // FPS
                    val fpsVal = fps
                    if (fpsVal != null) {
                        DetailLine("#FPS", "%.1f".format(fpsVal), fpsColor(fpsVal))
                    }
                    // Power
                    val mAVal = mA
                    if (mAVal != null) {
                        DetailLine("#PWR", "%dmA".format(mAVal), CurrentColor)
                    }
                    // Battery temp
                    val batTempVal = batTemp
                    if (batTempVal != null && batTempVal > 0) {
                        DetailLine("#BAT", "%.1f\u00B0C  %d%%".format(batTempVal, bat), BatColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MonoStyle.copy(fontSize = 8.sp, color = TextSecondary),
            modifier = Modifier.width(34.dp),
        )
        Text(
            text = value,
            style = MonoStyle.copy(fontSize = 8.sp, color = valueColor),
        )
    }
}

private fun groupCoresByFreq(freqs: List<Int>?): List<Pair<String, Int>> {
    if (freqs.isNullOrEmpty()) return emptyList()
    val result = mutableListOf<Pair<String, Int>>()
    var start = 0
    var currentFreq = freqs[0]
    for (i in 1 until freqs.size) {
        if (freqs[i] != currentFreq) {
            val range = if (start == i - 1) "#$start" else "#$start~${i - 1}"
            result.add(range to currentFreq)
            start = i
            currentFreq = freqs[i]
        }
    }
    val range = if (start == freqs.size - 1) "#$start" else "#$start~${freqs.size - 1}"
    result.add(range to currentFreq)
    return result
}

@Composable
private fun FloatRingGauge(
    percentage: Float,
    color: Color,
    label: String,
    size: Dp = 48.dp,
    strokeWidth: Dp = 4.dp,
    bottomLabel: String = "",
) {
    val clamped = percentage.coerceIn(0f, 100f)
    val gaugeColor = when {
        clamped >= 80 -> Color(0xFFF44336)
        clamped >= 60 -> Color(0xFFFFC107)
        else -> color
    }
    val trackColor = if (isSystemInDarkTheme()) Color(0xFF333333) else Color(0xFFCCCCCC)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(size)) {
                val stroke = strokeWidth.toPx()
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                val topLeft = Offset(stroke / 2, stroke / 2)
                // 背景轨道 (270°弧)
                drawArc(
                    color = trackColor,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
                // 前景填充弧
                drawArc(
                    color = gaugeColor,
                    startAngle = 135f,
                    sweepAngle = 270f * (clamped / 100f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
            // 中心文字
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (label.isNotEmpty()) {
                    Text(
                        text = label,
                        style = MonoStyle.copy(fontSize = 8.sp, color = TextSecondary),
                    )
                }
                Text(
                    text = "%d%%".format(clamped.toInt()),
                    style = MonoStyle.copy(fontSize = 10.sp, color = gaugeColor),
                )
            }
        }
        if (bottomLabel.isNotEmpty()) {
            Text(
                text = bottomLabel,
                style = MonoStyle.copy(fontSize = 7.sp, color = TextSecondary),
            )
        }
    }
}

@Composable
fun FloatMiniMonitorContent(service: FloatMonitorService) {
    val cpu by service.cpuLoad.collectAsState()
    val gpu by service.gpuLoad.collectAsState()
    val temp by service.cpuTemp.collectAsState()
    val fps by service.currentFps.collectAsState()
    val mA by service.currentMa.collectAsState()
    val coreLoads by service.cpuCoreLoads.collectAsState()
    val coreFreqs by service.cpuCoreFreqs.collectAsState()
    val gpuFreq by service.gpuFreqMhz.collectAsState()
    val batTemp by service.batteryTemp.collectAsState()
    val batVoltage by service.batteryVoltage.collectAsState()
    val showCpuFreq by service.miniShowCpuFreq.collectAsState()
    val showGpuFreq by service.miniShowGpuFreq.collectAsState()
    val showNetSpeed by service.miniShowNetSpeed.collectAsState()
    val netSpeedMode by service.miniNetSpeedMode.collectAsState()
    val rxSpeed by service.netRxSpeed.collectAsState()
    val txSpeed by service.netTxSpeed.collectAsState()

    // Cycle battery info: current → temp → power every 3s
    var batCycleIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            batCycleIndex = (batCycleIndex + 1) % 3
        }
    }

    Box(
        modifier = Modifier
            .background(Color(0x66000000), RoundedCornerShape(3.dp))
            .padding(start = 3.dp, end = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // CPU: icon + inline core bar chart + percentage
            Image(
                painter = painterResource(R.drawable.ic_cpu),
                contentDescription = null,
                modifier = Modifier.size(8.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
            val cores = coreLoads
            if (!cores.isNullOrEmpty()) {
                CpuCoreBarChart(
                    coreLoads = cores,
                    modifier = Modifier
                        .width(28.dp)
                        .height(8.dp),
                )
            }
            val cpuVal = cpu
            MiniText(if (cpuVal != null) "%3d%%".format(cpuVal.toInt()) else " --%")
            val freqs = coreFreqs
            if (showCpuFreq && !freqs.isNullOrEmpty()) {
                MiniText("${freqs.max()}M", TextSecondary)
            }

            val gpuVal = gpu
            val gpuFreqVal = gpuFreq
            if (gpuVal != null) {
                val gpuText = if (showGpuFreq && gpuFreqVal != null && gpuFreqVal > 0) {
                    "%d%%/%dM".format(gpuVal.toInt(), gpuFreqVal)
                } else {
                    "%3d%%".format(gpuVal.toInt())
                }
                MiniIconLabel(R.drawable.ic_gpu, gpuText, Color.White)
            }
            val tempVal = temp
            val tempText = if (tempVal != null) "%3.0f\u00B0".format(tempVal) else " --\u00B0"
            MiniIconLabel(R.drawable.ic_temperature, tempText, Color.White)
            val fpsVal = fps
            val fpsText = when {
                fpsVal == null -> " --.-"
                fpsVal > 0.0 -> "%5.1f".format(fpsVal)
                else -> "  0.0"
            }
            MiniIconLabel(R.drawable.ic_frame, fpsText, Color.White)

            // Cycle: battery current → temp → power
            val batTempVal = batTemp
            val mAVal = mA
            val voltage = batVoltage
            when (batCycleIndex) {
                0 -> {
                    val text = if (mAVal != null) "%dmA".format(mAVal) else "--mA"
                    MiniIconLabel(R.drawable.ic_current, text, Color.White)
                }
                1 -> {
                    val text = if (batTempVal != null && batTempVal > 0.0) "%.1f\u00B0".format(batTempVal) else "--\u00B0"
                    MiniIconLabel(R.drawable.ic_current, text, Color.White)
                }
                2 -> {
                    val powerW = if (mAVal != null && voltage > 0) mAVal * voltage / 1000.0 else null
                    val text = if (powerW != null) "%.1fW".format(powerW) else "--W"
                    MiniIconLabel(R.drawable.ic_current, text, Color.White)
                }
            }

            // Network speed
            if (showNetSpeed) {
                if (netSpeedMode == 0) {
                    // Combined mode
                    MiniText("\u2195${formatMiniSpeed(rxSpeed + txSpeed)}", Color(0xFF80DEEA))
                } else {
                    // Split mode
                    MiniText("\u2193${formatMiniSpeed(rxSpeed)}", Color(0xFF66BB6A))
                    MiniText("\u2191${formatMiniSpeed(txSpeed)}", Color(0xFFFF7043))
                }
            }
        }
    }
}

@Composable
private fun MiniIconLabel(iconRes: Int, value: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(8.dp),
            colorFilter = ColorFilter.tint(color),
        )
        Text(
            text = value,
            style = MonoStyle.copy(
                fontSize = 8.sp,
                color = color,
                letterSpacing = 0.sp,
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun formatMiniSpeed(bytesPerSec: Long): String = when {
    bytesPerSec >= 1_000_000L -> "%.1fM".format(bytesPerSec / 1_000_000.0)
    bytesPerSec >= 1_000L -> "%.0fK".format(bytesPerSec / 1_000.0)
    else -> "${bytesPerSec}B"
}

@Composable
private fun MiniText(value: String, color: Color = Color.White) {
    Text(
        text = value,
        style = MonoStyle.copy(fontSize = 8.sp, color = color, letterSpacing = 0.sp),
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun CpuCoreBarChart(coreLoads: List<Double>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val barCount = coreLoads.size
        if (barCount == 0) return@Canvas
        val gap = 1.dp.toPx()
        val totalGaps = gap * (barCount - 1)
        val barWidth = (size.width - totalGaps) / barCount
        val cornerRadius = CornerRadius(1.dp.toPx())

        coreLoads.forEachIndexed { i, load ->
            val clamped = load.coerceIn(0.0, 100.0).toFloat()
            val color = when {
                clamped > 85f -> Color(0xFFF44336)
                clamped > 65f -> Color(0xFFFF9800)
                else -> CpuColor
            }
            val alpha = 0.5f + 0.5f * clamped / 100f
            val barHeight = (size.height * (clamped / 100f)).coerceAtLeast(size.height * 0.05f)
            val x = i * (barWidth + gap)

            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = cornerRadius,
            )
        }
    }
}

@Composable
fun FloatFpsContent(service: FloatMonitorService) {
    val fpsRaw by service.currentFps.collectAsState()
    val fps = fpsRaw
    val recordingState by service.fpsRecordingState.collectAsState()
    val recordingInfo by service.fpsRecordingInfo.collectAsState()
    val showDurationMenu by service.fpsShowDurationMenu.collectAsState()
    val isInteracting by service.fpsInteracting.collectAsState()

    val isRecording = recordingState == com.cloudorz.openmonitor.core.data.datasource.FpsRecordingState.RECORDING
    val isCountdown = recordingState == com.cloudorz.openmonitor.core.data.datasource.FpsRecordingState.COUNTDOWN

    // Shadow background that appears during drag and fades out 1s after release
    val bgAlpha by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(durationMillis = if (isInteracting) 150 else 600),
        label = "dragBgAlpha",
    )

    // Red breathing border animation for recording state
    val breathTransition = rememberInfiniteTransition(label = "breathBorder")
    val breathAlpha by breathTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathAlpha",
    )

    val shape = RoundedCornerShape(6.dp)

    // Use Box to layer the duration menu on top without shifting the FPS display
    Box {
        // Main FPS column — always stays in place
        Column(
            modifier = Modifier.align(Alignment.TopStart),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Main FPS display
            Box(
                modifier = Modifier
                    .then(
                        // Drag shadow: only during touch, NOT during recording
                        if (bgAlpha > 0f && !isRecording && !isCountdown) {
                            Modifier.background(Color(0xFF000000).copy(alpha = bgAlpha * 0.35f), shape)
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        when {
                            isRecording -> Modifier
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFFF44336).copy(alpha = breathAlpha),
                                    shape = shape,
                                )
                            isCountdown -> Modifier
                                .background(Color(0x22000000), shape)
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFF2196F3).copy(alpha = breathAlpha),
                                    shape = shape,
                                )
                            else -> Modifier
                        }
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                val text = when {
                    isCountdown -> "${recordingInfo.countdownSeconds}"
                    fps != null -> "%.1f".format(fps)
                    else -> "--"
                }
                val textColor = when {
                    isCountdown -> Color(0xFF2196F3)
                    fps != null -> fpsColor(fps)
                    else -> TextSecondary
                }
                // Invisible reference text to lock width to exactly 5 monospace chars
                val fpsStyle = MonoStyle.copy(fontSize = 15.sp)
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "000.0",
                        style = fpsStyle.copy(color = Color.Transparent),
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = text,
                        style = fpsStyle.copy(color = textColor),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            // Recording elapsed time indicator
            if (isRecording) {
                val elapsed = recordingInfo.elapsedSeconds
                val limit = recordingInfo.durationLimitSeconds
                val remaining = recordingInfo.remainingSeconds
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (limit > 0) formatCompactDuration(remaining) else formatCompactDuration(elapsed),
                    style = MonoStyle.copy(fontSize = 8.sp, color = Color(0xFFF44336).copy(alpha = 0.9f)),
                )
            }
        }

        // Duration selection menu — floats below and to the end, never shifts the FPS display
        AnimatedVisibility(
            visible = showDurationMenu,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 28.dp),
            enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.8f),
            exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.8f),
        ) {
            Row(
                modifier = Modifier
                    .background(Color(0xDD000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(5, 10, 15, 30).forEach { minutes ->
                    DurationButton(
                        label = "${minutes}m",
                        onClick = { service.onFpsDurationSelected(minutes) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DurationButton(label: String, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (pressed) 1.0f else 0.5f,
        animationSpec = tween(100),
        label = "btnAlpha",
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = alpha * 0.25f))
            .hapticClickable {
                pressed = true
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MonoStyle.copy(
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.7f + alpha * 0.3f),
            ),
        )
    }

    // Reset pressed state after brief highlight
    LaunchedEffect(pressed) {
        if (pressed) {
            delay(200)
            pressed = false
        }
    }
}

private fun formatCompactDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "%d:%02d".format(m, s) else "${s}s"
}

@Composable
fun FloatTemperatureContent(service: FloatMonitorService, showExtended: Boolean = false) {
    val zones by service.thermalZones.collectAsState()

    Box(
        modifier = Modifier
            .width(100.dp)
            .background(BG, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.float_temp_header),
                style = MonoStyle.copy(fontSize = 10.sp, color = TextSecondary, letterSpacing = 0.5.sp),
            )
            Spacer(modifier = Modifier.height(3.dp))

            val categories = remember(zones, showExtended) {
                categorizeThermalZones(zones, showExtended)
            }
            if (categories.isEmpty()) {
                Text(
                    stringResource(R.string.thermal_no_data),
                    style = TextStyle(fontSize = 11.sp, color = Color(0x80FFFFFF)),
                )
            } else {
                categories.forEach { cat ->
                    ThermalRow(thermalCategoryLabel(cat.id), cat.maxTemp)
                }
            }
        }
    }
}

// ---- Thermal zone classification ----

private data class ThermalCategoryInfo(val id: String, val maxTemp: Double)

private val CATEGORY_ORDER = listOf(
    "cpu", "gpu", "npu", "soc", "modem", "battery", "skin",
    "wifi", "camera", "ddr", "video", "charger", "pa",
    "usb", "display", "audio", "storage", "tpu",
)

/** Categories that are hidden by default and only shown when extended mode is enabled. */
private val EXTENDED_CATEGORIES = setOf("usb", "display", "audio", "storage", "tpu")

private fun categorizeThermalZones(
    zones: List<ThermalZone>,
    showExtended: Boolean = false,
): List<ThermalCategoryInfo> {
    if (zones.isEmpty()) return emptyList()
    return zones
        .filter { it.temperatureCelsius > 0 }
        .groupBy { classifyThermalType(it.type) }
        .filterKeys { it != "other" && (showExtended || it !in EXTENDED_CATEGORIES) }
        .map { (cat, list) -> ThermalCategoryInfo(cat, list.maxOf { it.temperatureCelsius }) }
        .sortedBy { CATEGORY_ORDER.indexOf(it.id).let { i -> if (i < 0) 999 else i } }
}

private fun classifyThermalType(type: String): String {
    val t = type.lowercase().trim()
    return when {
        // GPU（先于 CPU 检查，避免 "gpuss" 误匹配 "cpu"）
        t.startsWith("gpuss") || t.contains("gpu") -> "gpu"
        // CPU 各核心：cpu-X-Y-usr / cpu_X_Y 等
        t.matches(Regex("cpu[_-]\\d+[_-]\\d+.*")) -> "cpu"
        t.contains("cpu") -> "cpu"
        // NPU / DSP（含 Hexagon HVX）
        t.startsWith("nspss") || t.startsWith("cdsp") || t.contains("npu") ||
            t.startsWith("q6-hvx") -> "npu"
        // 基带 Modem
        t.startsWith("mdmss") || t.contains("modem") -> "modem"
        // 相机
        t.startsWith("camera") -> "camera"
        // WiFi（含高通 cwlan）
        t.startsWith("wlan") || t.startsWith("cwlan") || t.contains("wifi") -> "wifi"
        // 视频编解码
        t.startsWith("video") -> "video"
        // DDR 内存（含 PoP 封装内存）
        t.startsWith("ddr") || t.contains("dram") || t.startsWith("pop-mem") -> "ddr"
        // 电池 / PMIC / BMS
        t.contains("battery") || t.contains("bms") ||
            (t.startsWith("pm") && t.contains("_tz")) -> "battery"
        // 充电器
        t.contains("charger") -> "charger"
        // 表面温度 / 环境
        t.contains("skin") || t.contains("quiet-therm") || t.contains("xo-therm") ||
            t.contains("shell-therm") || t.contains("back-therm") -> "skin"
        // SoC
        t.startsWith("aoss") || t.contains("soc") || t.contains("exynos") -> "soc"
        // 功放 PA（含 rf-pa、pa-therm 格式）
        t.matches(Regex("pa[_-]\\d+.*")) || t.contains("rf-pa") ||
            t.startsWith("pa-therm") -> "pa"
        // 高通 tsens（归类为 SoC）
        t.contains("tsens") -> "soc"
        // ---- Extended categories (opt-in) ----
        // USB / 连接器
        t.contains("usb") || t.startsWith("conn-therm") -> "usb"
        // Display
        t.contains("display") || t.startsWith("mdp") || t.contains("panel-therm") -> "display"
        // 音频子系统
        t.startsWith("audio") || t.startsWith("lpass") -> "audio"
        // 存储（UFS/eMMC）
        t.startsWith("nvm-therm") || t.contains("emmc-therm") || t.contains("ufs") -> "storage"
        // TPU (Google Tensor)
        t.contains("tpu") -> "tpu"
        else -> "other"
    }
}

@Composable
private fun thermalCategoryLabel(id: String): String = when (id) {
    "cpu" -> "CPU"
    "gpu" -> "GPU"
    "npu" -> "NPU"
    "soc" -> "SoC"
    "ddr" -> "DDR"
    "wifi" -> "WiFi"
    "pa" -> "PA"
    "video" -> "Video"
    "modem" -> "Modem"
    "camera" -> "Cam"
    "usb" -> "USB"
    "display" -> "Disp"
    "audio" -> "Audio"
    "storage" -> "UFS"
    "tpu" -> "TPU"
    "battery" -> stringResource(R.string.thermal_cat_battery)
    "skin" -> stringResource(R.string.thermal_cat_skin)
    "charger" -> stringResource(R.string.thermal_cat_charger)
    else -> id
}

@Composable
private fun ThermalRow(label: String, temp: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(fontSize = 10.sp, color = TextPrimary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${"%.1f".format(temp)}\u00B0C",
            style = MonoStyle.copy(fontSize = 10.sp, color = tempColor(temp)),
        )
    }
}

private val ProcessBG: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xDD1C1C2A) else Color(0xDDFFFFFF)
private val ProcessTextPrimary: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFEEEEEE) else Color(0xFF1A1A1A)
private val ProcessTextSecondary: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFAAAAAA) else Color(0xFF666666)
private val ProcessHeaderIcon = Color(0xFF888888)

@Composable
fun FloatProcessContent(service: FloatMonitorService) {
    val processes by service.topProcesses.collectAsState()
    val hasShell by service.hasShellAccess.collectAsState()
    val filterMode by service.processFilterMode.collectAsState()
    val selectedPid by service.selectedProcessPid.collectAsState()
    val minimized by service.processMinimized.collectAsState()
    val locked by service.processLocked.collectAsState()
    val appIcons by service.processAppIcons.collectAsState()

    val shape = RoundedCornerShape(if (minimized) 6.dp else 10.dp)

    Box(
        modifier = Modifier
            .then(if (minimized) Modifier else Modifier.width(185.dp))
            .background(ProcessBG, shape)
            .padding(
                top = if (minimized) 3.dp else 6.dp,
                bottom = if (minimized) 3.dp else 8.dp,
                start = if (minimized) 6.dp else 8.dp,
                end = if (minimized) 4.dp else 8.dp,
            ),
    ) {
        Column(modifier = if (minimized) Modifier else Modifier.fillMaxWidth()) {
            if (minimized) {
                // 折叠态：紧凑一行 [ 📌 进程管理  ↗ ]
                Row(
                    modifier = Modifier
                        .pointerInput(locked) {
                            if (!locked) {
                                detectDragGestures(
                                    onDrag = { _, d -> service.moveProcessWindow(d.x, d.y) },
                                    onDragEnd = { service.saveProcessWindowPosition() },
                                )
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = null,
                        tint = if (locked) ProcessTextPrimary else ProcessTextSecondary,
                        modifier = Modifier
                            .size(14.dp)
                            .hapticClickable { service.onProcessLockToggle() },
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.float_process_title),
                        style = TextStyle(fontSize = 10.sp, color = ProcessTextPrimary),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.UnfoldMore,
                        contentDescription = null,
                        tint = ProcessTextPrimary,
                        modifier = Modifier
                            .size(14.dp)
                            .hapticClickable { service.onProcessMinimizeToggle() },
                    )
                }
            } else {
                // 展开态完整 header — 可拖拽
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(locked) {
                            if (!locked) {
                                detectDragGestures(
                                    onDrag = { _, d -> service.moveProcessWindow(d.x, d.y) },
                                    onDragEnd = { service.saveProcessWindowPosition() },
                                )
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = null,
                        tint = if (locked) ProcessTextPrimary else ProcessTextSecondary,
                        modifier = Modifier
                            .size(14.dp)
                            .hapticClickable { service.onProcessLockToggle() },
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.float_process_title),
                        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = ProcessTextPrimary),
                        modifier = Modifier.weight(1f),
                    )
                    if (hasShell) {
                        Text(
                            text = when (filterMode) {
                                com.cloudorz.openmonitor.core.model.process.ProcessFilterMode.ALL -> stringResource(R.string.float_filter_all)
                                com.cloudorz.openmonitor.core.model.process.ProcessFilterMode.APP_ONLY -> stringResource(R.string.float_filter_app)
                            },
                            style = TextStyle(fontSize = 9.sp, color = Color(0xFF2196F3)),
                            modifier = Modifier
                                .hapticClickable { service.onProcessFilterToggle() }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.UnfoldLess,
                        contentDescription = null,
                        tint = ProcessTextPrimary,
                        modifier = Modifier
                            .size(14.dp)
                            .hapticClickable { service.onProcessMinimizeToggle() }
                            .padding(1.dp),
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = ProcessTextPrimary,
                        modifier = Modifier
                            .size(14.dp)
                            .hapticClickable { service.onProcessClose() }
                            .padding(1.dp),
                    )
                }

                // 进程列表
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFFE0E0E0)))
                Spacer(modifier = Modifier.height(3.dp))

                if (!hasShell) {
                    Text(stringResource(R.string.float_need_shell), style = TextStyle(fontSize = 10.sp, color = Color(0xFFFFC107)), modifier = Modifier.padding(vertical = 8.dp))
                } else if (processes.isEmpty()) {
                    Text(stringResource(R.string.float_loading), style = TextStyle(fontSize = 10.sp, color = ProcessTextSecondary), modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                    ) {
                        items(processes, key = { it.pid }) { proc ->
                            ProcessRow(
                                name = proc.displayName,
                                cpu = proc.cpuPercent,
                                icon = if (proc.isAndroidApp) appIcons[proc.packageName] else null,
                                isAndroidApp = proc.isAndroidApp,
                                isSelected = proc.pid == selectedPid,
                                onClick = {
                                    if (proc.pid == selectedPid) {
                                        service.onProcessKill(proc.pid)
                                        service.onProcessTapped(proc) // deselect
                                    } else {
                                        service.onProcessTapped(proc)
                                    }
                                },
                            )
                        }
                    }
                    if (selectedPid != null) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(R.string.float_tap_to_kill),
                            style = TextStyle(fontSize = 8.sp, color = Color(0xFFF44336).copy(alpha = 0.7f)),
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessRow(
    name: String,
    cpu: Double,
    icon: Bitmap? = null,
    isAndroidApp: Boolean = true,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    val bgColor = if (isSelected) Color(0x22F44336) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(4.dp))
            .hapticClickable { onClick() }
            .padding(vertical = 3.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        } else if (!isAndroidApp) {
            Image(
                painter = painterResource(R.drawable.ic_system_process),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(Color(0xFF78909C)),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_cpu),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(Color(0xFF999999)),
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        // Name (up to 2 lines)
        Text(
            text = name,
            style = TextStyle(fontSize = 9.sp, color = ProcessTextPrimary),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(6.dp))
        // CPU %
        Text(
            text = if (cpu >= 10) "%.1f%%".format(cpu) else "%.1f%%".format(cpu),
            style = MonoStyle.copy(
                fontSize = 9.sp,
                color = when {
                    cpu >= 20 -> Color(0xFFF44336)
                    cpu >= 5 -> Color(0xFFFFC107)
                    else -> ProcessTextSecondary
                },
            ),
        )
    }
}

@Composable
fun FloatThreadContent(service: FloatMonitorService) {
    val threads by service.topThreads.collectAsState()
    val fgApp by service.foregroundApp.collectAsState()
    val hasShell by service.hasShellAccess.collectAsState()
    val isLoaded by service.isThreadLoaded.collectAsState()

    Box(
        modifier = Modifier
            .width(180.dp)
            .background(BG, RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (fgApp.isNotEmpty()) {
                Text(
                    text = fgApp,
                    style = TextStyle(fontSize = 9.sp, color = CpuColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "CPU% Top15",
                style = MonoStyle.copy(fontSize = 8.sp, color = TextSecondary, letterSpacing = 0.5.sp),
            )
            Spacer(modifier = Modifier.height(3.dp))
            // 表头
            Row(modifier = Modifier.width(164.dp).padding(bottom = 2.dp)) {
                Text("CPU%", style = MonoStyle.copy(fontSize = 7.sp, color = TextSecondary), modifier = Modifier.width(36.dp))
                Text("TID",  style = MonoStyle.copy(fontSize = 7.sp, color = TextSecondary), modifier = Modifier.width(34.dp))
                Text("COMM", style = MonoStyle.copy(fontSize = 7.sp, color = TextSecondary), modifier = Modifier.width(94.dp))
            }

            if (!hasShell) {
                Text(stringResource(R.string.float_need_shell), style = TextStyle(fontSize = 9.sp, color = Color(0xFFFFC107)))
            } else if (!isLoaded) {
                Text(stringResource(R.string.float_loading), style = TextStyle(fontSize = 9.sp, color = TextSecondary))
            } else {
                threads.forEach { thread ->
                    Row(
                        modifier = Modifier
                            .width(164.dp)
                            .padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "%4.1f".format(thread.cpuLoadPercent),
                            style = MonoStyle.copy(
                                fontSize = 8.sp,
                                color = when {
                                    thread.cpuLoadPercent >= 20 -> Color(0xFFF44336)
                                    thread.cpuLoadPercent >= 5 -> Color(0xFFFFC107)
                                    else -> Color(0xFF4CAF50)
                                },
                            ),
                            modifier = Modifier.width(36.dp),
                        )
                        Text(
                            text = thread.tid.toString(),
                            style = MonoStyle.copy(fontSize = 8.sp, color = TextSecondary),
                            modifier = Modifier.width(34.dp),
                        )
                        Text(
                            text = thread.name.ifEmpty { "tid:${thread.tid}" },
                            style = TextStyle(fontSize = 8.sp, color = TextPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(94.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---- Control Panel ----

@Composable
fun FloatControlPanelBackdropContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
    )
}

@Composable
fun FloatControlPanelContent(service: FloatMonitorService) {
    val activeIds by service.activeMonitorIds.collectAsState()
    val miniShowCpuFreq by service.miniShowCpuFreq.collectAsState()
    val miniShowGpuFreq by service.miniShowGpuFreq.collectAsState()
    val tempExtended by service.tempShowExtended.collectAsState()
    val miniShowNetSpeed by service.miniShowNetSpeed.collectAsState()
    val miniNetSpeedMode by service.miniNetSpeedMode.collectAsState()

    data class Btn(val icon: ImageVector, val name: String, val active: Boolean, val onClick: () -> Unit)
    val buttons = listOf(
        Btn(Icons.Filled.Speed,       stringResource(R.string.float_btn_load),   FloatMonitorService.TYPE_LOAD        in activeIds) { service.toggleMonitorFromPanel(FloatMonitorService.TYPE_LOAD) },
        Btn(Icons.Filled.Widgets,     stringResource(R.string.float_btn_mini),   FloatMonitorService.TYPE_MINI        in activeIds) { service.toggleMonitorFromPanel(FloatMonitorService.TYPE_MINI) },
        Btn(Icons.Filled.SportsEsports,"FPS",  FloatMonitorService.TYPE_FPS         in activeIds) { service.toggleMonitorFromPanel(FloatMonitorService.TYPE_FPS) },
        Btn(Icons.Filled.Thermostat,  stringResource(R.string.float_btn_temp),   FloatMonitorService.TYPE_TEMPERATURE in activeIds) { service.toggleMonitorFromPanel(FloatMonitorService.TYPE_TEMPERATURE) },
        Btn(Icons.Filled.Apps,        stringResource(R.string.float_btn_process),   FloatMonitorService.TYPE_PROCESS     in activeIds) { service.toggleMonitorFromPanel(FloatMonitorService.TYPE_PROCESS) },
        Btn(Icons.Filled.AccountTree, stringResource(R.string.float_btn_thread),   FloatMonitorService.TYPE_THREAD      in activeIds) { service.toggleMonitorFromPanel(FloatMonitorService.TYPE_THREAD) },
        Btn(Icons.Filled.Memory,      stringResource(R.string.float_btn_cpu_freq), miniShowCpuFreq) { service.onMiniCpuFreqToggle() },
        Btn(Icons.Filled.Videocam,    stringResource(R.string.float_btn_gpu_freq), miniShowGpuFreq) { service.onMiniGpuFreqToggle() },
        Btn(Icons.Filled.UnfoldMore, stringResource(R.string.float_btn_ext_temp), tempExtended) { service.onTempExtendedToggle() },
        Btn(Icons.Filled.SwapVert,   stringResource(R.string.float_btn_net_speed), miniShowNetSpeed) { service.onMiniNetSpeedToggle() },
        Btn(Icons.Filled.CompareArrows, stringResource(R.string.float_btn_net_split), miniNetSpeedMode == 1) { service.onMiniNetSpeedModeToggle() },
    )

    val panelBg = if (isSystemInDarkTheme()) Color(0xF0222222) else Color(0xF0FFFFFF)
    val panelText = if (isSystemInDarkTheme()) Color(0xFFEEEEEE) else Color(0xFF1A1A1A)

    Box(
        modifier = Modifier
            .width(240.dp)
            .background(panelBg, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Column {
            // 标题 + 关闭
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "OpenMonitor",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = panelText),
                )
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = panelText,
                    modifier = Modifier
                        .size(16.dp)
                        .hapticClickable { service.dismissControlPanel() },
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            // 方形按钮网格（4 列）
            val cols = 4
            buttons.chunked(cols).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.forEach { btn ->
                        PanelToggleButton(
                            icon = btn.icon,
                            name = btn.name,
                            isActive = btn.active,
                            onClick = btn.onClick,
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                        )
                    }
                    repeat(cols - row.size) {
                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun PanelToggleButton(
    icon: ImageVector,
    name: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inactiveBg = if (isSystemInDarkTheme()) Color(0xFF333333) else Color(0xFFE0E0E0)
    val inactiveContent = if (isSystemInDarkTheme()) Color(0xFFAAAAAA) else Color(0xFF888888)
    val bg = if (isActive) Color(0xFF42A5F5) else inactiveBg
    val contentColor = if (isActive) Color.White else inactiveContent

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .hapticClickable { onClick() }
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = name,
                style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Medium, color = contentColor),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

private fun fpsColor(fps: Double): Color = when {
    fps >= 120.0 -> Color(0xFF00E676) // bright green
    fps >= 90.0 -> Color(0xFF66BB6A)  // green
    fps >= 60.0 -> Color(0xFFFFC107)  // amber
    fps >= 30.0 -> Color(0xFFFF9800)  // orange
    else -> Color(0xFFF44336)          // red
}

private fun tempColor(celsius: Double): Color = when {
    celsius < 45.0 -> Color(0xFF4CAF50)
    celsius < 60.0 -> Color(0xFFFFC107)
    else -> Color(0xFFF44336)
}

