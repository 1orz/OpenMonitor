package com.cloudorz.openmonitor.feature.battery

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudorz.openmonitor.core.model.battery.BatteryChartPoint
import com.cloudorz.openmonitor.core.model.battery.BatteryEstimation
import com.cloudorz.openmonitor.core.model.battery.BatteryStatus
import com.cloudorz.openmonitor.core.ui.R
import com.cloudorz.openmonitor.core.ui.hapticClick
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val CHART_PADDING_LEFT = 40f
private const val CHART_PADDING_RIGHT = 8f
private const val CHART_PADDING_TOP = 8f
private const val ICON_ROW_HEIGHT = 28f
private const val TIME_AXIS_HEIGHT = 20f
private const val CHART_BOTTOM_MARGIN = 4f

@Composable
fun BatteryHistoryChart(
    points: List<BatteryChartPoint>,
    battery: BatteryStatus,
    estimation: BatteryEstimation,
    selectedRange: TimeRange,
    appIcons: Map<String, Bitmap>,
    onRangeSelected: (TimeRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val textMeasurer = rememberTextMeasurer()
    val lineColor = MaterialTheme.colorScheme.primary
    val chargingColor = Color(0xFF4CAF50)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val markerLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val tooltipBg = MaterialTheme.colorScheme.inverseSurface
    val tooltipText = MaterialTheme.colorScheme.inverseOnSurface
    val timeFormat = remember(selectedRange) {
        if (selectedRange == TimeRange.LAST_7D) {
            SimpleDateFormat("MM/dd", Locale.getDefault())
        } else {
            SimpleDateFormat("HH:mm", Locale.getDefault())
        }
    }

    // Gesture state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var selectedIdx by remember { mutableIntStateOf(-1) }

    // Reset gesture state when range changes
    androidx.compose.runtime.LaunchedEffect(selectedRange) {
        scale = 1f
        offsetX = 0f
        selectedIdx = -1
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.battery_usage_history),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${battery.capacity}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Chart canvas with gestures
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clipToBounds(),
            ) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(points) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                // Adjust offset to zoom toward center
                                val chartLeft = CHART_PADDING_LEFT
                                val chartWidth =
                                    size.width - chartLeft - CHART_PADDING_RIGHT
                                val scaledWidth = chartWidth * newScale
                                val maxOffset = 0f
                                val minOffset = -(scaledWidth - chartWidth)
                                // Adjust offset for zoom center
                                val centerX = size.width / 2f
                                val newOffset =
                                    offsetX + (centerX - chartLeft) * (1 - zoom) * scale + pan.x
                                scale = newScale
                                offsetX = newOffset.coerceIn(minOffset, maxOffset)
                            }
                        }
                        .pointerInput(points) {
                            detectTapGestures { tapOffset ->
                                if (points.size < 2) return@detectTapGestures
                                val chartLeft = CHART_PADDING_LEFT
                                val chartRight = size.width - CHART_PADDING_RIGHT
                                val chartWidth = chartRight - chartLeft

                                val timeStart = points.first().timestamp
                                val timeEnd = points.last().timestamp
                                val timeSpan = (timeEnd - timeStart).coerceAtLeast(1L)

                                // Convert tap X to data space
                                val dataX =
                                    (tapOffset.x - chartLeft - offsetX) / scale
                                val tapTime =
                                    timeStart + (dataX / chartWidth * timeSpan).toLong()

                                // Find nearest point
                                var bestIdx = 0
                                var bestDist = Long.MAX_VALUE
                                for (i in points.indices) {
                                    val dist = abs(points[i].timestamp - tapTime)
                                    if (dist < bestDist) {
                                        bestDist = dist
                                        bestIdx = i
                                    }
                                }
                                selectedIdx =
                                    if (selectedIdx == bestIdx) -1 else bestIdx
                            }
                        },
                ) {
                    if (points.isEmpty()) return@Canvas

                    val chartLeft = CHART_PADDING_LEFT
                    val chartRight = size.width - CHART_PADDING_RIGHT
                    val chartTop = CHART_PADDING_TOP
                    val chartBottom =
                        size.height - ICON_ROW_HEIGHT - TIME_AXIS_HEIGHT - CHART_BOTTOM_MARGIN
                    val chartWidth = chartRight - chartLeft
                    val chartHeight = chartBottom - chartTop

                    val timeStart = points.first().timestamp
                    val timeEnd = points.last().timestamp
                    val timeSpan = (timeEnd - timeStart).coerceAtLeast(1L)

                    fun timeToX(ts: Long): Float {
                        val baseX =
                            ((ts - timeStart).toFloat() / timeSpan) * chartWidth
                        return chartLeft + baseX * scale + offsetX
                    }

                    fun capacityToY(cap: Int): Float =
                        chartTop + (1f - cap / 100f) * chartHeight

                    // Grid lines (not affected by horizontal transform)
                    for (pct in listOf(0, 20, 40, 60, 80, 100)) {
                        val y = capacityToY(pct)
                        drawLine(
                            gridColor,
                            Offset(chartLeft, y),
                            Offset(chartRight, y),
                            strokeWidth = 1f,
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "$pct%",
                            topLeft = Offset(0f, y - 6f),
                            style = TextStyle(fontSize = 8.sp, color = labelColor),
                        )
                    }

                    // Draw capacity line with charge/discharge coloring
                    drawCapacityLine(
                        points = points,
                        timeToX = ::timeToX,
                        capacityToY = ::capacityToY,
                        lineColor = lineColor,
                        chargingColor = chargingColor,
                        chartTop = chartTop,
                        chartBottom = chartBottom,
                        clipLeft = chartLeft,
                        clipRight = chartRight,
                    )

                    // App icons row
                    drawAppIcons(
                        points = points,
                        appIcons = appIcons,
                        timeToX = ::timeToX,
                        iconY = chartBottom + CHART_BOTTOM_MARGIN,
                        iconSize = ICON_ROW_HEIGHT - 4f,
                        chartLeft = chartLeft,
                        chartRight = chartRight,
                    )

                    // Time axis labels
                    val timeStyle = TextStyle(fontSize = 8.sp, color = labelColor)
                    val sampleMeasured = textMeasurer.measure("00:00", timeStyle)
                    val timeAxisY =
                        size.height - sampleMeasured.size.height - 2f
                    val labelCount =
                        if (selectedRange == TimeRange.LAST_1H) 4 else 5
                    for (i in 0..labelCount) {
                        val ts = timeStart + (timeSpan * i / labelCount)
                        val x = timeToX(ts)
                        if (x < chartLeft - 20f || x > chartRight + 20f) continue
                        val label = timeFormat.format(Date(ts))
                        drawText(
                            textMeasurer = textMeasurer,
                            text = label,
                            topLeft = Offset(x - 14f, timeAxisY),
                            style = timeStyle,
                        )
                    }

                    // Selected point marker / tooltip
                    if (selectedIdx in points.indices) {
                        val pt = points[selectedIdx]
                        val px = timeToX(pt.timestamp)
                        val py = capacityToY(pt.capacity)

                        if (px in chartLeft..chartRight) {
                            // Vertical dashed line
                            drawLine(
                                color = markerLineColor,
                                start = Offset(px, chartTop),
                                end = Offset(px, chartBottom),
                                strokeWidth = 1.5f,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(8f, 6f),
                                ),
                            )
                            // Highlight dot
                            drawCircle(
                                color = if (pt.isCharging) chargingColor else lineColor,
                                radius = 5f,
                                center = Offset(px, py),
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 2.5f,
                                center = Offset(px, py),
                            )

                            // Tooltip
                            val tooltipTime =
                                SimpleDateFormat(
                                    "HH:mm:ss",
                                    Locale.getDefault(),
                                ).format(Date(pt.timestamp))
                            val tempStr = "%.1f".format(pt.temperatureCelsius)
                            val tooltipStr =
                                "${pt.capacity}%  $tooltipTime\n${pt.currentMa}mA  ${tempStr}°C"
                            val tooltipStyle = TextStyle(
                                fontSize = 9.sp,
                                color = tooltipText,
                            )
                            val measured =
                                textMeasurer.measure(tooltipStr, tooltipStyle)
                            val tooltipW =
                                measured.size.width + 16f
                            val tooltipH =
                                measured.size.height + 12f
                            val tooltipX =
                                (px - tooltipW / 2).coerceIn(
                                    chartLeft,
                                    chartRight - tooltipW,
                                )
                            val tooltipY =
                                (py - tooltipH - 10f).coerceAtLeast(0f)

                            drawRoundRect(
                                color = tooltipBg,
                                topLeft = Offset(tooltipX, tooltipY),
                                size = androidx.compose.ui.geometry.Size(
                                    tooltipW,
                                    tooltipH,
                                ),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                    6f,
                                    6f,
                                ),
                            )
                            drawText(
                                textMeasurer = textMeasurer,
                                text = tooltipStr,
                                topLeft = Offset(
                                    tooltipX + 8f,
                                    tooltipY + 6f,
                                ),
                                style = tooltipStyle,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Summary stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SummaryItem(
                    value = "%.1fWh".format(computeTotalWh(points)),
                    label = stringResource(R.string.battery_total_energy),
                )
                SummaryItem(
                    value = "%.1f°C".format(
                        if (points.isNotEmpty()) points.map { it.temperatureCelsius }.average() else 0.0,
                    ),
                    label = stringResource(R.string.battery_avg_temp),
                )
                SummaryItem(
                    value = "%.3fV".format(battery.voltageV),
                    label = stringResource(R.string.battery_voltage),
                )
                SummaryItem(
                    value = if (battery.isCharging) {
                        stringResource(R.string.battery_charging)
                    } else {
                        stringResource(R.string.battery_not_charging)
                    },
                    label = stringResource(R.string.battery_charge_status),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Estimation stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatItem(
                    value = "%.2fW".format(estimation.avgPowerW),
                    label = stringResource(R.string.battery_avg_power),
                    color = MaterialTheme.colorScheme.primary,
                )
                StatItem(
                    value = formatDuration(estimation.screenOnTimeMs),
                    label = stringResource(R.string.battery_screen_on_time),
                    color = MaterialTheme.colorScheme.secondary,
                )
                StatItem(
                    value = if (estimation.remainingMinutes > 0) {
                        formatDuration(estimation.remainingMinutes * 60_000L)
                    } else {
                        "--"
                    },
                    label = stringResource(R.string.battery_estimated_life),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Time range selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                TimeRange.entries.forEach { range ->
                    FilterChip(
                        selected = selectedRange == range,
                        onClick = { view.hapticClick(); onRangeSelected(range) },
                        label = { Text(range.label) },
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawCapacityLine(
    points: List<BatteryChartPoint>,
    timeToX: (Long) -> Float,
    capacityToY: (Int) -> Float,
    lineColor: Color,
    chargingColor: Color,
    chartTop: Float,
    chartBottom: Float,
    clipLeft: Float,
    clipRight: Float,
) {
    if (points.size < 2) return

    var prevCharging = points.first().isCharging
    var segmentStart = 0

    fun drawSegment(startIdx: Int, endIdx: Int, charging: Boolean) {
        val segPath = Path()
        val segFill = Path()
        val color = if (charging) chargingColor else lineColor

        val x0 = timeToX(points[startIdx].timestamp)
        val y0 = capacityToY(points[startIdx].capacity)
        segPath.moveTo(x0, y0)
        segFill.moveTo(x0, chartBottom)
        segFill.lineTo(x0, y0)

        for (i in startIdx + 1..endIdx.coerceAtMost(points.lastIndex)) {
            val x = timeToX(points[i].timestamp)
            val y = capacityToY(points[i].capacity)
            segPath.lineTo(x, y)
            segFill.lineTo(x, y)
        }

        val lastX = timeToX(points[endIdx.coerceAtMost(points.lastIndex)].timestamp)
        segFill.lineTo(lastX, chartBottom)
        segFill.close()

        // Fill
        drawPath(
            path = segFill,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0.02f)),
                startY = chartTop,
                endY = chartBottom,
            ),
            style = Fill,
        )

        // Line
        drawPath(
            path = segPath,
            color = color,
            style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    for (i in 1 until points.size) {
        if (points[i].isCharging != prevCharging) {
            drawSegment(segmentStart, i, prevCharging)
            segmentStart = i
            prevCharging = points[i].isCharging
        }
    }
    drawSegment(segmentStart, points.lastIndex, prevCharging)
}

private fun DrawScope.drawAppIcons(
    points: List<BatteryChartPoint>,
    appIcons: Map<String, Bitmap>,
    timeToX: (Long) -> Float,
    iconY: Float,
    iconSize: Float,
    chartLeft: Float,
    chartRight: Float,
) {
    if (points.isEmpty() || appIcons.isEmpty()) return

    // Detect app transitions and place icons
    val transitions = mutableListOf<Pair<Long, String>>()
    var lastPkg = ""
    for (p in points) {
        if (p.packageName.isNotEmpty() && p.packageName != lastPkg) {
            transitions.add(p.timestamp to p.packageName)
            lastPkg = p.packageName
        }
    }

    // Filter overlapping icons (minimum spacing)
    val minSpacing = iconSize + 2f
    val placed = mutableListOf<Float>()

    for ((ts, pkg) in transitions) {
        val bitmap = appIcons[pkg] ?: continue
        val x = timeToX(ts) - iconSize / 2
        if (x < chartLeft || x + iconSize > chartRight) continue
        if (placed.any { abs(it - x) < minSpacing }) continue

        placed.add(x)
        val imageBitmap = bitmap.asImageBitmap()

        drawImage(
            image = imageBitmap,
            dstOffset = IntOffset(x.roundToInt(), iconY.roundToInt()),
            dstSize = IntSize(iconSize.roundToInt(), iconSize.roundToInt()),
        )
    }
}

private fun computeTotalWh(points: List<BatteryChartPoint>): Double {
    if (points.size < 2) return 0.0
    var totalWs = 0.0
    for (i in 1 until points.size) {
        val dt = (points[i].timestamp - points[i - 1].timestamp) / 1000.0 // seconds
        totalWs += points[i].powerW * dt
    }
    return totalWs / 3600.0
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h${minutes}m" else "${minutes}m"
}

@Composable
private fun SummaryItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun StatItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
