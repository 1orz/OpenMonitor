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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.delay

private val BG = Color(0xCC000000)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xB3FFFFFF)
private val Track = Color(0xFF333333)
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
                .padding(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val maxFreq = coreFreqs?.maxOrNull()
                FloatRingGauge(
                    percentage = (cpu ?: 0.0).toFloat(),
                    color = CpuColor,
                    label = "CPU",
                    bottomLabel = if (maxFreq != null) "${maxFreq}MHz" else "",
                )
                FloatRingGauge(
                    percentage = (gpu ?: 0.0).toFloat(),
                    color = GpuColor,
                    label = "GPU",
                    bottomLabel = if (gpuFreq != null && gpuFreq!! > 0) "${gpuFreq}MHz" else "0MHz",
                )
                FloatRingGauge(
                    percentage = (mem ?: 0.0).toFloat(),
                    color = MemColor,
                    label = "",
                    bottomLabel = if (temp != null && temp!! > 0) "%.1f\u00B0C".format(temp) else "",
                )
            }
        }
    } else {
        // 完整模式：左侧圆环 + 右侧详细文字
        Box(
            modifier = Modifier
                .width(280.dp)
                .background(BG, RoundedCornerShape(10.dp))
                .padding(10.dp),
        ) {
            Row {
                // 左侧三个圆环
                Column(
                    modifier = Modifier.width(70.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val maxFreq = coreFreqs?.maxOrNull()
                    FloatRingGauge(
                        percentage = (cpu ?: 0.0).toFloat(),
                        color = CpuColor,
                        label = "CPU",
                        size = 56.dp,
                        bottomLabel = if (maxFreq != null) "${maxFreq}MHz" else "",
                    )
                    FloatRingGauge(
                        percentage = (gpu ?: 0.0).toFloat(),
                        color = GpuColor,
                        label = "GPU",
                        size = 56.dp,
                        bottomLabel = if (gpuFreq != null && gpuFreq!! > 0) "${gpuFreq}MHz" else "0MHz",
                    )
                    FloatRingGauge(
                        percentage = (mem ?: 0.0).toFloat(),
                        color = MemColor,
                        label = "${(mem ?: 0.0).toInt()}%",
                        size = 56.dp,
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
                    if (cores != null && cores.isNotEmpty()) {
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
    if (freqs == null || freqs.isEmpty()) return emptyList()
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(size)) {
                val stroke = strokeWidth.toPx()
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                val topLeft = Offset(stroke / 2, stroke / 2)
                // 背景轨道 (270°弧)
                drawArc(
                    color = Track,
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
    val hasShell by service.hasShellAccess.collectAsState()
    val mA by service.currentMa.collectAsState()
    val coreLoads by service.cpuCoreLoads.collectAsState()
    val coreFreqs by service.cpuCoreFreqs.collectAsState()
    val gpuFreq by service.gpuFreqMhz.collectAsState()
    val batTemp by service.batteryTemp.collectAsState()

    // Alternate between battery current and battery temp every 3s
    var showBatTemp by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            showBatTemp = !showBatTemp
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
            if (cores != null && cores.isNotEmpty()) {
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
            if (freqs != null && freqs.isNotEmpty()) {
                MiniText("${freqs.max()}M", TextSecondary)
            }

            val gpuVal = gpu
            val gpuFreqVal = gpuFreq
            if (gpuVal != null) {
                val gpuText = if (gpuFreqVal != null && gpuFreqVal > 0) {
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

            // Alternating: battery current ↔ battery temp (same icon & color)
            val batTempVal = batTemp
            val mAVal = mA
            if (showBatTemp && batTempVal != null && batTempVal > 0.0) {
                MiniIconLabel(R.drawable.ic_current, "%.1f\u00B0".format(batTempVal), Color.White)
            } else if (mAVal != null) {
                MiniIconLabel(R.drawable.ic_current, "%dmA".format(mAVal), Color.White)
            } else {
                MiniIconLabel(R.drawable.ic_current, "--mA", Color.White)
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
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
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
            .clickable {
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
fun FloatTemperatureContent(service: FloatMonitorService) {
    val zones by service.thermalZones.collectAsState()

    Box(
        modifier = Modifier
            .width(160.dp)
            .background(BG, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "TEMPERATURE",
                style = MonoStyle.copy(fontSize = 10.sp, color = TextSecondary, letterSpacing = 0.5.sp),
            )
            Spacer(modifier = Modifier.height(4.dp))

            val filtered = zones.filter { zone ->
                zone.type.contains("cpu", ignoreCase = true) ||
                    zone.type.contains("gpu", ignoreCase = true) ||
                    zone.type.contains("battery", ignoreCase = true) ||
                    zone.type.contains("soc", ignoreCase = true) ||
                    zone.type.contains("skin", ignoreCase = true) ||
                    zone.type.contains("tsens", ignoreCase = true)
            }.take(10)

            val display = filtered.ifEmpty { zones.take(8) }
            if (display.isEmpty()) {
                Text("No thermal data", style = TextStyle(fontSize = 11.sp, color = Color(0x80FFFFFF)))
            } else {
                display.forEach { zone ->
                    ThermalRow(zone.type.ifEmpty { zone.name }, zone.temperatureCelsius)
                }
            }
        }
    }
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

// ---- Process Monitor (Scene-style) ----

private val ProcessBG = Color(0xDDFFFFFF)
private val ProcessTextPrimary = Color(0xFF1A1A1A)
private val ProcessTextSecondary = Color(0xFF666666)
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
                    Image(
                        painter = painterResource(R.drawable.dialog_pin),
                        contentDescription = null,
                        modifier = Modifier
                            .size(14.dp)
                            .alpha(if (locked) 1f else 0.3f)
                            .clickable { service.onProcessLockToggle() },
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "进程管理",
                        style = TextStyle(fontSize = 10.sp, color = ProcessTextPrimary),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Image(
                        painter = painterResource(R.drawable.dialog_maximize),
                        contentDescription = null,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { service.onProcessMinimizeToggle() },
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
                    Image(
                        painter = painterResource(R.drawable.dialog_pin),
                        contentDescription = null,
                        modifier = Modifier
                            .size(14.dp)
                            .alpha(if (locked) 1f else 0.3f)
                            .clickable { service.onProcessLockToggle() },
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "进程管理",
                        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = ProcessTextPrimary),
                        modifier = Modifier.weight(1f),
                    )
                    if (hasShell) {
                        Text(
                            text = when (filterMode) {
                                com.cloudorz.openmonitor.core.model.process.ProcessFilterMode.ALL -> "全部"
                                com.cloudorz.openmonitor.core.model.process.ProcessFilterMode.APP_ONLY -> "应用"
                            },
                            style = TextStyle(fontSize = 9.sp, color = Color(0xFF2196F3)),
                            modifier = Modifier
                                .clickable { service.onProcessFilterToggle() }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                    Image(
                        painter = painterResource(R.drawable.dialog_minimize),
                        contentDescription = null,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { service.onProcessMinimizeToggle() }
                            .padding(1.dp),
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Image(
                        painter = painterResource(R.drawable.dialog_close),
                        contentDescription = null,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { service.onProcessClose() }
                            .padding(1.dp),
                    )
                }

                // 进程列表
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFFE0E0E0)))
                Spacer(modifier = Modifier.height(3.dp))

                if (!hasShell) {
                    Text("需要 Shell 权限", style = TextStyle(fontSize = 10.sp, color = Color(0xFFFFC107)), modifier = Modifier.padding(vertical = 8.dp))
                } else if (processes.isEmpty()) {
                    Text("加载中...", style = TextStyle(fontSize = 10.sp, color = ProcessTextSecondary), modifier = Modifier.padding(vertical = 8.dp))
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
                                onClick = { service.onProcessTapped(proc) },
                            )
                        }
                    }
                    if (selectedPid != null) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "再次点击结束",
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
            .clickable { onClick() }
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

    Box(
        modifier = Modifier
            .width(210.dp)
            .background(BG, RoundedCornerShape(8.dp))
            .padding(10.dp),
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
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
                Text("CPU%", style = MonoStyle.copy(fontSize = 7.sp, color = TextSecondary), modifier = Modifier.width(36.dp))
                Text("TID", style = MonoStyle.copy(fontSize = 7.sp, color = TextSecondary), modifier = Modifier.width(42.dp))
                Text("COMM", style = MonoStyle.copy(fontSize = 7.sp, color = TextSecondary))
            }

            if (!hasShell) {
                Text("需要 Shell 权限", style = TextStyle(fontSize = 9.sp, color = Color(0xFFFFC107)))
            } else if (threads.isEmpty()) {
                Text("加载中...", style = TextStyle(fontSize = 9.sp, color = TextSecondary))
            } else {
                threads.forEach { thread ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
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
                            modifier = Modifier.width(42.dp),
                        )
                        Text(
                            text = thread.name.ifEmpty { "tid:${thread.tid}" },
                            style = TextStyle(fontSize = 8.sp, color = TextPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
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
            .fillMaxHeight()
            .background(Color(0x55000000)),
    )
}

@Composable
fun FloatControlPanelContent(service: FloatMonitorService) {
    val activeIds by service.activeMonitorIds.collectAsState()

    val monitorTypes = listOf(
        FloatMonitorService.TYPE_LOAD to "负载",
        FloatMonitorService.TYPE_MINI to "迷你",
        FloatMonitorService.TYPE_FPS to "FPS",
        FloatMonitorService.TYPE_TEMPERATURE to "温度",
        FloatMonitorService.TYPE_PROCESS to "进程",
        FloatMonitorService.TYPE_THREAD to "线程",
    )

    Box(
        modifier = Modifier
            .width(220.dp)
            .background(Color(0xF0FFFFFF), RoundedCornerShape(12.dp))
            .padding(14.dp),
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
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1A1A1A)),
                )
                Image(
                    painter = painterResource(R.drawable.dialog_close),
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { service.dismissControlPanel() },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            // 2x3 toggle grid
            val rows = monitorTypes.chunked(3)
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { (typeId, name) ->
                        val isActive = typeId in activeIds
                        PanelToggleButton(
                            name = name,
                            isActive = isActive,
                            onClick = { service.toggleMonitorFromPanel(typeId) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // 填充不满的列
                    repeat(3 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PanelToggleButton(
    name: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (isActive) Color(0xFF42A5F5) else Color(0xFFE0E0E0)
    val textColor = if (isActive) Color.White else Color(0xFF888888)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = textColor),
        )
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

