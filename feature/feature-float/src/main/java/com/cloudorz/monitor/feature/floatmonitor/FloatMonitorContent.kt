package com.cloudorz.monitor.feature.floatmonitor

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
import com.cloudorz.monitor.core.model.process.ProcessInfo
import com.cloudorz.monitor.core.model.thermal.ThermalZone

// Common overlay colors
private val OverlayBackground = Color(0xCC000000)
private val OverlayTextPrimary = Color(0xFFFFFFFF)
private val OverlayTextSecondary = Color(0xB3FFFFFF)
private val OverlayTextMuted = Color(0x80FFFFFF)

// Gauge colors
private val CpuColor = Color(0xFF42A5F5)
private val GpuColor = Color(0xFFAB47BC)
private val MemColor = Color(0xFF66BB6A)
private val BatColor = Color(0xFFFFA726)
private val GaugeTrack = Color(0xFF333333)

// Temperature thresholds
private val TempNormal = Color(0xFF4CAF50)
private val TempWarning = Color(0xFFFFC107)
private val TempCritical = Color(0xFFF44336)

// ============================================================================
// Load Monitor -- compact 2x2 gauge grid
// ============================================================================

@Composable
fun LoadMonitorContent(
    cpuLoad: Double,
    gpuLoad: Double,
    memUsed: Double,
    batteryLevel: Int,
) {
    Box(
        modifier = Modifier
            .width(200.dp)
            .background(OverlayBackground, RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Row 1: CPU + GPU
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MiniGauge(
                    percentage = cpuLoad.toFloat(),
                    label = "CPU",
                    color = CpuColor,
                )
                MiniGauge(
                    percentage = gpuLoad.toFloat(),
                    label = "GPU",
                    color = GpuColor,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            // Row 2: MEM + BAT
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MiniGauge(
                    percentage = memUsed.toFloat(),
                    label = "RAM",
                    color = MemColor,
                )
                MiniGauge(
                    percentage = batteryLevel.toFloat(),
                    label = "BAT",
                    color = BatColor,
                )
            }
        }
    }
}

@Composable
private fun MiniGauge(
    percentage: Float,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val clamped = percentage.coerceIn(0f, 100f)
    val sweepAngle = remember(clamped) { 240f * (clamped / 100f) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(72.dp)) {
                val strokePx = 6.dp.toPx()
                val padding = strokePx / 2f
                val arcSize = Size(
                    width = size.width - strokePx,
                    height = size.height - strokePx,
                )
                val topLeft = Offset(padding, padding)

                // Background track
                drawArc(
                    color = GaugeTrack,
                    startAngle = 150f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )

                // Foreground arc
                if (sweepAngle > 0f) {
                    drawArc(
                        color = color,
                        startAngle = 150f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                }
            }

            // Center text
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${clamped.toInt()}%",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = OverlayTextPrimary,
                    ),
                )
                Text(
                    text = label,
                    style = TextStyle(
                        fontSize = 9.sp,
                        color = OverlayTextSecondary,
                    ),
                )
            }
        }
    }
}

// ============================================================================
// Process Monitor -- top 5 processes
// ============================================================================

@Composable
fun ProcessMonitorContent(
    processes: List<ProcessInfo>,
) {
    Box(
        modifier = Modifier
            .width(200.dp)
            .background(OverlayBackground, RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Top CPU Processes",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = OverlayTextSecondary,
                    letterSpacing = 0.5.sp,
                ),
            )
            Spacer(modifier = Modifier.height(4.dp))

            val topProcesses = processes.take(5)
            topProcesses.forEachIndexed { index, process ->
                ProcessRow(
                    rank = index + 1,
                    process = process,
                )
                if (index < topProcesses.lastIndex) {
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }

            if (topProcesses.isEmpty()) {
                Text(
                    text = "No processes",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = OverlayTextMuted,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ProcessRow(
    rank: Int,
    process: ProcessInfo,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$rank.",
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = OverlayTextMuted,
                fontFamily = FontFamily.Monospace,
            ),
            modifier = Modifier.width(16.dp),
        )
        Text(
            text = process.displayName,
            style = TextStyle(
                fontSize = 11.sp,
                color = OverlayTextPrimary,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "%.1f%%".format(process.cpuPercent),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = cpuPercentColor(process.cpuPercent),
                fontFamily = FontFamily.Monospace,
            ),
        )
    }
}

// ============================================================================
// Mini Monitor -- single compact line
// ============================================================================

@Composable
fun MiniMonitorContent(
    cpuLoad: Double,
    gpuLoad: Double,
    temperature: Double,
) {
    Box(
        modifier = Modifier
            .background(OverlayBackground, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = "CPU:${cpuLoad.toInt()}%  GPU:${gpuLoad.toInt()}%  ${
                "%.1f".format(temperature)
            }\u00B0C",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = OverlayTextPrimary,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.3.sp,
            ),
        )
    }
}

// ============================================================================
// FPS Recorder -- large FPS number + jank count
// ============================================================================

@Composable
fun FpsRecorderContent(
    fps: Double,
    jank: Int,
) {
    Box(
        modifier = Modifier
            .width(100.dp)
            .background(OverlayBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "${fps.toInt()}",
                style = TextStyle(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = fpsColor(fps),
                    fontFamily = FontFamily.Monospace,
                ),
            )
            Text(
                text = "FPS",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = OverlayTextSecondary,
                    letterSpacing = 1.sp,
                ),
            )
            if (jank > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Jank: $jank",
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = TempWarning,
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        }
    }
}

// ============================================================================
// Temperature Monitor -- compact list of thermal zones
// ============================================================================

@Composable
fun TemperatureMonitorContent(
    zones: List<ThermalZone>,
) {
    Box(
        modifier = Modifier
            .width(200.dp)
            .background(OverlayBackground, RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Temperature",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = OverlayTextSecondary,
                    letterSpacing = 0.5.sp,
                ),
            )
            Spacer(modifier = Modifier.height(4.dp))

            if (zones.isEmpty()) {
                Text(
                    text = "No thermal data",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = OverlayTextMuted,
                    ),
                )
            } else {
                zones.forEachIndexed { index, zone ->
                    ThermalZoneRow(zone = zone)
                    if (index < zones.lastIndex) {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThermalZoneRow(
    zone: ThermalZone,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = zone.type.ifEmpty { zone.name },
            style = TextStyle(
                fontSize = 11.sp,
                color = OverlayTextPrimary,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${"%.1f".format(zone.temperatureCelsius)}\u00B0C",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = tempColor(zone.temperatureCelsius),
                fontFamily = FontFamily.Monospace,
            ),
        )
    }
}

// ============================================================================
// Color helpers
// ============================================================================

private fun cpuPercentColor(cpuPercent: Double): Color = when {
    cpuPercent < 5.0 -> Color(0xFF4CAF50)
    cpuPercent <= 20.0 -> Color(0xFFFFC107)
    else -> Color(0xFFF44336)
}

private fun fpsColor(fps: Double): Color = when {
    fps >= 55.0 -> Color(0xFF4CAF50)
    fps >= 30.0 -> Color(0xFFFFC107)
    else -> Color(0xFFF44336)
}

private fun tempColor(celsius: Double): Color = when {
    celsius < ThermalZone.WARNING_THRESHOLD_CELSIUS -> TempNormal
    celsius < ThermalZone.CRITICAL_THRESHOLD_CELSIUS -> TempWarning
    else -> TempCritical
}
