package com.cloudorz.monitor.service

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Overlay colors
private val BG = Color(0xCC000000)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xB3FFFFFF)
private val Track = Color(0xFF333333)

private val CpuColor = Color(0xFF42A5F5)
private val GpuColor = Color(0xFFAB47BC)
private val MemColor = Color(0xFF66BB6A)
private val BatColor = Color(0xFFFFA726)

// ============================================================================
// Load Monitor — 2x2 gauge grid
// ============================================================================

@Composable
fun FloatLoadMonitorContent(service: FloatMonitorService) {
    val cpu by service.cpuLoad.collectAsState()
    val gpu by service.gpuLoad.collectAsState()
    val mem by service.memUsed.collectAsState()
    val bat by service.batteryLevel.collectAsState()
    val temp by service.cpuTemp.collectAsState()

    Box(
        modifier = Modifier
            .background(BG, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MiniGauge(cpu.toFloat(), "CPU", CpuColor)
                MiniGauge(gpu.toFloat(), "GPU", GpuColor)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MiniGauge(mem.toFloat(), "RAM", MemColor)
                MiniGauge(bat.toFloat(), "BAT", BatColor)
            }
            if (temp > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${"%.1f".format(temp)}\u00B0C",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = tempColor(temp),
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        }
    }
}

// ============================================================================
// Mini Monitor — single compact line
// ============================================================================

@Composable
fun FloatMiniMonitorContent(service: FloatMonitorService) {
    val cpu by service.cpuLoad.collectAsState()
    val gpu by service.gpuLoad.collectAsState()
    val temp by service.cpuTemp.collectAsState()

    Box(
        modifier = Modifier
            .background(BG, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = "CPU:${cpu.toInt()}%  GPU:${gpu.toInt()}%  ${"%.1f".format(temp)}\u00B0C",
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.3.sp,
            ),
        )
    }
}

// ============================================================================
// FPS Recorder — large FPS number
// ============================================================================

@Composable
fun FloatFpsContent(service: FloatMonitorService) {
    val fps by service.currentFps.collectAsState()
    val jank by service.currentJank.collectAsState()

    Box(
        modifier = Modifier
            .background(BG, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${fps.toInt()}",
                style = TextStyle(
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = fpsColor(fps),
                    fontFamily = FontFamily.Monospace,
                ),
            )
            Text(
                text = "FPS",
                style = TextStyle(fontSize = 10.sp, color = TextSecondary, letterSpacing = 1.sp),
            )
            if (jank > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Jank: $jank",
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = Color(0xFFFFC107),
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        }
    }
}

// ============================================================================
// Temperature Monitor — thermal zone list
// ============================================================================

@Composable
fun FloatTemperatureContent(service: FloatMonitorService) {
    val zones by service.thermalZones.collectAsState()

    Box(
        modifier = Modifier
            .background(BG, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Temperature",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp,
                ),
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Filter to only show interesting thermal zones
            val filtered = zones.filter { zone ->
                zone.type.contains("cpu", ignoreCase = true) ||
                    zone.type.contains("gpu", ignoreCase = true) ||
                    zone.type.contains("battery", ignoreCase = true) ||
                    zone.type.contains("soc", ignoreCase = true) ||
                    zone.type.contains("skin", ignoreCase = true) ||
                    zone.type.contains("tsens", ignoreCase = true)
            }.take(10)

            if (filtered.isEmpty()) {
                // Show all zones if filter matched nothing (up to 8)
                zones.take(8).forEach { zone ->
                    ThermalRow(zone.type.ifEmpty { zone.name }, zone.temperatureCelsius)
                }
            } else {
                filtered.forEach { zone ->
                    ThermalRow(zone.type, zone.temperatureCelsius)
                }
            }

            if (zones.isEmpty()) {
                Text(
                    text = "No thermal data",
                    style = TextStyle(fontSize = 11.sp, color = Color(0x80FFFFFF)),
                )
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
            style = TextStyle(fontSize = 11.sp, color = TextPrimary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${"%.1f".format(temp)}\u00B0C",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = tempColor(temp),
                fontFamily = FontFamily.Monospace,
            ),
        )
    }
}

// ============================================================================
// Shared mini gauge
// ============================================================================

@Composable
private fun MiniGauge(percentage: Float, label: String, color: Color) {
    val clamped = percentage.coerceIn(0f, 100f)
    val sweep = remember(clamped) { 240f * (clamped / 100f) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(72.dp)) {
                val strokePx = 6.dp.toPx()
                val pad = strokePx / 2f
                val arcSize = Size(size.width - strokePx, size.height - strokePx)
                val topLeft = Offset(pad, pad)

                drawArc(Track, 150f, 240f, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
                if (sweep > 0f) {
                    drawArc(color, 150f, sweep, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${clamped.toInt()}%",
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary),
                )
                Text(
                    text = label,
                    style = TextStyle(fontSize = 9.sp, color = TextSecondary),
                )
            }
        }
    }
}

// ============================================================================
// Color helpers
// ============================================================================

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
