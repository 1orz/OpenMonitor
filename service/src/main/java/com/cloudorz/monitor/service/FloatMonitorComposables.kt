package com.cloudorz.monitor.service

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudorz.monitor.core.model.fps.FpsMethod
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
private val ChipBg = Color(0xFF333333)
private val ChipSelectedBg = Color(0xFF1976D2)

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

    Box(
        modifier = Modifier
            .width(200.dp)
            .background(BG, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            LoadBar("CPU", cpu.toFloat(), CpuColor)
            LoadBar("GPU", gpu.toFloat(), GpuColor)
            LoadBar("RAM", mem.toFloat(), MemColor)
            LoadBar("BAT", bat.toFloat(), BatColor)
            if (temp > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_temperature),
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        colorFilter = ColorFilter.tint(tempColor(temp)),
                    )
                    Text(
                        text = "${"%.1f".format(temp)}\u00B0C",
                        style = MonoStyle.copy(fontSize = 10.sp, color = tempColor(temp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadBar(label: String, value: Float, color: Color) {
    val clamped = value.coerceIn(0f, 100f)
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
            text = "%3d%%".format(clamped.toInt()),
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
    val jank by service.currentJank.collectAsState()
    val hasShell by service.hasShellAccess.collectAsState()
    val mA by service.currentMa.collectAsState()

    Box(
        modifier = Modifier
            .background(Color(0x66000000), RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
            .padding(start = 3.dp, end = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniIconLabel(R.drawable.ic_cpu, "%3d%%".format(cpu.toInt()), CpuColor)
            MiniIconLabel(R.drawable.ic_gpu, "%3d%%".format(gpu.toInt()), GpuColor)
            MiniIconLabel(R.drawable.ic_temperature, "%3.0f\u00B0".format(temp), tempColor(temp))
            val fpsBase = if (fps > 0) "%3d".format(fps.toInt()) else if (hasShell) "  0" else " --"
            val fpsText = if (jank > 0 && fps > 0) "$fpsBase J$jank" else fpsBase
            MiniIconLabel(R.drawable.ic_frame, fpsText, if (hasShell) fpsColor(fps) else TextSecondary)
            MiniIconLabel(R.drawable.ic_current, "${abs(mA)}mA", currentColor(mA))
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
        )
    }
}

@Composable
fun FloatFpsContent(service: FloatMonitorService) {
    val fps by service.currentFps.collectAsState()
    val jank by service.currentJank.collectAsState()
    val currentMethod by service.currentFpsMethod.collectAsState()
    val availableMethods by service.availableFpsMethods.collectAsState()
    val hasShell by service.hasShellAccess.collectAsState()

    Box(
        modifier = Modifier
            .background(BG, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        if (!hasShell) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("FPS", style = MonoStyle.copy(fontSize = 10.sp, color = TextSecondary))
                Text(
                    text = "需要 Shell 权限",
                    style = TextStyle(fontSize = 8.sp, color = Color(0xFFFFC107), fontFamily = FontFamily.Monospace),
                )
                Text(
                    text = "Root/ADB/Shizuku",
                    style = TextStyle(fontSize = 7.sp, color = Color(0x80FFFFFF), fontFamily = FontFamily.Monospace),
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${fps.toInt()}",
                        style = MonoStyle.copy(fontSize = 18.sp, color = fpsColor(fps), lineHeight = 20.sp),
                    )
                    Text(
                        text = if (jank > 0) "J:$jank" else "FPS",
                        style = TextStyle(
                            fontSize = 7.sp,
                            color = if (jank > 0) Color(0xFFFFC107) else TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 8.sp,
                        ),
                    )
                }
                if (availableMethods.size > 1) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        availableMethods.forEach { method ->
                            val isSelected = method == currentMethod
                            Text(
                                text = methodShortName(method),
                                style = TextStyle(
                                    fontSize = 7.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color(0x80FFFFFF),
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 8.sp,
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (isSelected) ChipSelectedBg else ChipBg)
                                    .clickable { service.switchFpsMethod(method) }
                                    .padding(horizontal = 3.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun methodShortName(method: FpsMethod): String = when (method) {
    FpsMethod.SURFACE_FLINGER -> "SF"
    FpsMethod.FRAME_METRICS -> "FM"
    FpsMethod.CHOREOGRAPHER -> "CH"
}

@Composable
fun FloatTemperatureContent(service: FloatMonitorService) {
    val zones by service.thermalZones.collectAsState()

    Box(
        modifier = Modifier
            .width(220.dp)
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

@Composable
fun FloatProcessContent(service: FloatMonitorService) {
    val processes by service.topProcesses.collectAsState()
    val hasShell by service.hasShellAccess.collectAsState()

    Box(
        modifier = Modifier
            .width(240.dp)
            .background(BG, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "TOP PROCESSES",
                style = MonoStyle.copy(fontSize = 10.sp, color = TextSecondary, letterSpacing = 0.5.sp),
            )
            Spacer(modifier = Modifier.height(4.dp))

            if (!hasShell) {
                Text("需要 Shell 权限", style = TextStyle(fontSize = 9.sp, color = Color(0xFFFFC107)))
            } else if (processes.isEmpty()) {
                Text("加载中...", style = TextStyle(fontSize = 9.sp, color = TextSecondary))
            } else {
                processes.forEach { proc ->
                    ProcessRow(
                        name = proc.name.substringAfterLast('/').substringAfterLast(':'),
                        cpu = proc.cpuPercent,
                        memMb = proc.rssKB / 1024.0,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessRow(name: String, cpu: Double, memMb: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = TextStyle(fontSize = 9.sp, color = TextPrimary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "%4.1f%%".format(cpu),
            style = MonoStyle.copy(
                fontSize = 9.sp,
                color = when {
                    cpu >= 20 -> Color(0xFFF44336)
                    cpu >= 5 -> Color(0xFFFFC107)
                    else -> Color(0xFF4CAF50)
                },
            ),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "%3dM".format(memMb.toInt()),
            style = MonoStyle.copy(fontSize = 9.sp, color = TextSecondary),
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
            .width(220.dp)
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
                            text = thread.tid.toString(),
                            style = MonoStyle.copy(fontSize = 8.sp, color = TextSecondary),
                        )
                    }
                }
            }
        }
    }
}

private fun fpsColor(fps: Double): Color = when {
    fps >= 55.0 -> Color(0xFF4CAF50)
    fps >= 30.0 -> Color(0xFFFFC107)
    else -> Color(0xFFF44336)
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
