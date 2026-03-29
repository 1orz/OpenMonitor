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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.delay
import kotlin.math.abs

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
    val cpu by service.cpuLoad.collectAsState()
    val gpu by service.gpuLoad.collectAsState()
    val mem by service.memUsed.collectAsState()
    val bat by service.batteryLevel.collectAsState()
    val temp by service.cpuTemp.collectAsState()
    val coreFreqs by service.cpuCoreFreqs.collectAsState()
    val gpuFreq by service.gpuFreqMhz.collectAsState()

    Box(
        modifier = Modifier
            .width(200.dp)
            .background(BG, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            LoadBar("CPU", cpu?.toFloat(), CpuColor)
            val freqs = coreFreqs
            if (freqs != null && freqs.isNotEmpty()) {
                Text(
                    text = "${freqs.min()}-${freqs.max()} MHz",
                    style = MonoStyle.copy(fontSize = 8.sp, color = TextSecondary),
                    modifier = Modifier.padding(start = 28.dp),
                )
            }
            LoadBar("GPU", gpu?.toFloat(), GpuColor)
            val gpuFreqVal = gpuFreq
            if (gpuFreqVal != null && gpuFreqVal > 0) {
                Text(
                    text = "$gpuFreqVal MHz",
                    style = MonoStyle.copy(fontSize = 8.sp, color = TextSecondary),
                    modifier = Modifier.padding(start = 28.dp),
                )
            }
            LoadBar("RAM", mem?.toFloat(), MemColor)
            LoadBar("BAT", bat.toFloat(), BatColor)
            val tempVal = temp
            val showTemp = tempVal != null && tempVal > 0
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tColor = if (showTemp) tempColor(tempVal) else TextSecondary
                Image(
                    painter = painterResource(R.drawable.ic_temperature),
                    contentDescription = null,
                    modifier = Modifier.size(10.dp),
                    colorFilter = ColorFilter.tint(tColor),
                )
                Text(
                    text = if (showTemp) "${"%.1f".format(tempVal)}\u00B0C" else "--\u00B0C",
                    style = MonoStyle.copy(fontSize = 10.sp, color = tColor),
                )
            }
        }
    }
}

@Composable
private fun LoadBar(label: String, value: Float?, color: Color) {
    val clamped = (value ?: 0f).coerceIn(0f, 100f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MonoStyle.copy(fontSize = 9.sp, color = TextSecondary),
            modifier = Modifier.width(28.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Track),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = (clamped / 100f).coerceIn(0f, 1f))
                    .background(color, RoundedCornerShape(3.dp)),
            )
        }
        Text(
            text = if (value != null) "%3d%%".format(clamped.toInt()) else " --%",
            style = MonoStyle.copy(fontSize = 9.sp, color = TextPrimary),
            modifier = Modifier.width(34.dp),
            textAlign = TextAlign.End,
        )
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
                fpsVal == null -> "   --"
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

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Main FPS display
        Box(
            modifier = Modifier
                .then(
                    if (bgAlpha > 0f) {
                        Modifier.background(Color(0xFF000000).copy(alpha = bgAlpha * 0.35f), shape)
                    } else {
                        Modifier
                    }
                )
                .then(
                    when {
                        isRecording -> Modifier
                            .background(Color(0x22000000), shape)
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
                .padding(horizontal = 6.dp, vertical = 2.dp),
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
            Text(
                text = text,
                style = MonoStyle.copy(fontSize = 18.sp, color = textColor),
                textAlign = TextAlign.End,
            )
        }

        // Duration selection menu (shown on tap when idle)
        AnimatedVisibility(
            visible = showDurationMenu,
            enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.8f),
            exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.8f),
        ) {
            Column {
                Spacer(modifier = Modifier.height(4.dp))
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
        // App icon
        if (icon != null) {
            Image(
                bitmap = icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
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
            .width(180.dp)
            .background(BG, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "THREADS",
                style = MonoStyle.copy(fontSize = 10.sp, color = TextSecondary, letterSpacing = 0.5.sp),
            )
            if (fgApp.isNotEmpty()) {
                Text(
                    text = fgApp,
                    style = TextStyle(fontSize = 8.sp, color = CpuColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

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
                            text = thread.name.ifEmpty { "tid:${thread.tid}" },
                            style = TextStyle(fontSize = 9.sp, color = TextPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "%4.1f%%".format(thread.cpuLoadPercent),
                            style = MonoStyle.copy(
                                fontSize = 9.sp,
                                color = when {
                                    thread.cpuLoadPercent >= 20 -> Color(0xFFF44336)
                                    thread.cpuLoadPercent >= 5 -> Color(0xFFFFC107)
                                    else -> Color(0xFF4CAF50)
                                },
                            ),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = thread.tid.toString(),
                            style = MonoStyle.copy(fontSize = 8.sp, color = TextSecondary),
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

private fun currentColor(mA: Int): Color = when {
    mA > 0 -> Color(0xFF4CAF50)    // charging
    mA == 0 -> CurrentColor
    abs(mA) > 1500 -> Color(0xFFF44336)  // high drain
    abs(mA) > 800 -> Color(0xFFFFC107)   // moderate drain
    else -> CurrentColor
}
