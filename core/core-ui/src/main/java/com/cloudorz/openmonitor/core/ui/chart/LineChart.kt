package com.cloudorz.openmonitor.core.ui.chart

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * One data series rendered as a colored line on the chart.
 *
 * @property label Human-readable name shown in the legend.
 * @property data  Ordered list of Y values; the last element is the newest.
 * @property color Line color for this series.
 */
@Immutable
data class LineChartSeries(
    val label: String,
    val data: List<Float>,
    val color: Color,
)

/**
 * A Canvas-based time-series line chart that supports multiple data series,
 * auto-scaling, grid lines, a legend, and semi-transparent area fill.
 *
 * Data flows from left (oldest) to right (newest).  Each series is drawn as a
 * smooth path with optional cubic-spline interpolation for visual appeal.
 * The area beneath each line receives a vertical gradient fill from the line
 * color to transparent.
 *
 * ### Features
 * - Auto-scales Y axis with 10 % top/bottom padding.
 * - Draws 4 horizontal dashed grid lines with value labels on the left.
 * - Shows the current (rightmost) value for each series next to the line end.
 * - Animates opacity on appearance.
 * - Optional legend row at the bottom.
 *
 * @param dataSeries    List of [LineChartSeries] to draw.
 * @param modifier      [Modifier] applied to the root layout.
 * @param maxDataPoints Maximum number of data points visible on the X axis.
 * @param yAxisLabel    Optional label drawn above the Y axis (e.g. "C", "MHz").
 * @param showGrid            Whether to draw horizontal grid lines.
 * @param showLegend          Whether to show the legend row.
 * @param interactionEnabled  Whether touch crosshair interaction is enabled.
 */
@Composable
fun LineChart(
    dataSeries: List<LineChartSeries>,
    modifier: Modifier = Modifier,
    maxDataPoints: Int = 60,
    yAxisLabel: String = "",
    showGrid: Boolean = true,
    showLegend: Boolean = true,
    interactionEnabled: Boolean = true,
) {
    // Animation: fade-in factor for the whole chart
    val animatedAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 600),
        label = "lineChartAlpha",
    )

    val textMeasurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    // Compute global Y range across all series
    val (globalMin, globalMax) = remember(dataSeries) {
        computeYRange(dataSeries)
    }

    // Touch interaction state
    var touchX by remember { mutableStateOf<Float?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Y-axis unit label
        if (yAxisLabel.isNotEmpty()) {
            Text(
                text = yAxisLabel,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }

        // Chart canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        ) {
            val canvasModifier = if (interactionEnabled) {
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset -> touchX = offset.x },
                            onDragEnd = { touchX = null },
                            onDragCancel = { touchX = null },
                            onHorizontalDrag = { change, _ ->
                                touchX = change.position.x
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { offset ->
                                touchX = offset.x
                                val released = tryAwaitRelease()
                                if (released) touchX = null
                            },
                        )
                    }
            } else {
                Modifier.matchParentSize()
            }

            Canvas(modifier = canvasModifier) {
                val leftPadding = 42f   // space for Y labels
                val rightPadding = 16f
                val topPadding = 8f
                val bottomPadding = 4f

                val chartLeft = leftPadding
                val chartRight = size.width - rightPadding
                val chartTop = topPadding
                val chartBottom = size.height - bottomPadding
                val chartWidth = chartRight - chartLeft
                val chartHeight = chartBottom - chartTop

                if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

                // Grid lines
                if (showGrid) {
                    drawGridLines(
                        gridColor = gridColor,
                        textMeasurer = textMeasurer,
                        labelColor = labelColor,
                        yMin = globalMin,
                        yMax = globalMax,
                        chartLeft = chartLeft,
                        chartRight = chartRight,
                        chartTop = chartTop,
                        chartBottom = chartBottom,
                        lineCount = 4,
                    )
                }

                // Draw each series
                dataSeries.forEach { series ->
                    drawSeries(
                        series = series,
                        maxDataPoints = maxDataPoints,
                        yMin = globalMin,
                        yMax = globalMax,
                        chartLeft = chartLeft,
                        chartRight = chartRight,
                        chartTop = chartTop,
                        chartBottom = chartBottom,
                        chartWidth = chartWidth,
                        chartHeight = chartHeight,
                        alpha = animatedAlpha,
                        textMeasurer = textMeasurer,
                        onSurfaceColor = onSurface,
                    )
                }

                // Crosshair interaction overlay
                val currentTouchX = touchX
                if (currentTouchX != null && dataSeries.isNotEmpty()) {
                    drawCrosshair(
                        touchX = currentTouchX,
                        dataSeries = dataSeries,
                        maxDataPoints = maxDataPoints,
                        yMin = globalMin,
                        yMax = globalMax,
                        chartLeft = chartLeft,
                        chartRight = chartRight,
                        chartTop = chartTop,
                        chartBottom = chartBottom,
                        chartWidth = chartWidth,
                        chartHeight = chartHeight,
                        textMeasurer = textMeasurer,
                    )
                }
            }
        }

        // Legend
        if (showLegend && dataSeries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            ChartLegend(dataSeries = dataSeries)
        }
    }
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/**
 * Computes the global Y-axis range across all series with 10 % padding.
 */
private fun computeYRange(dataSeries: List<LineChartSeries>): Pair<Float, Float> {
    val allValues = dataSeries.flatMap { it.data }
    if (allValues.isEmpty()) return 0f to 100f

    val rawMin = allValues.min()
    val rawMax = allValues.max()
    val range = (rawMax - rawMin).coerceAtLeast(1f)
    val padding = range * 0.1f
    return (rawMin - padding) to (rawMax + padding)
}

/**
 * Draws horizontal dashed grid lines and their Y-axis value labels.
 */
private fun DrawScope.drawGridLines(
    gridColor: Color,
    textMeasurer: TextMeasurer,
    labelColor: Color,
    yMin: Float,
    yMax: Float,
    chartLeft: Float,
    chartRight: Float,
    chartTop: Float,
    chartBottom: Float,
    lineCount: Int,
) {
    val chartHeight = chartBottom - chartTop
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
    val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)

    for (i in 0..lineCount) {
        val fraction = i.toFloat() / lineCount
        val y = chartBottom - fraction * chartHeight
        val value = yMin + fraction * (yMax - yMin)

        // Dashed line
        drawLine(
            color = gridColor,
            start = Offset(chartLeft, y),
            end = Offset(chartRight, y),
            strokeWidth = 1f,
            pathEffect = dashEffect,
        )

        // Value label
        val labelText = formatAxisValue(value)
        val measured = textMeasurer.measure(labelText, labelStyle)
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(
                x = chartLeft - measured.size.width - 6f,
                y = y - measured.size.height / 2f,
            ),
        )
    }
}

/**
 * Formats a Y-axis value for compact display.
 */
private fun formatAxisValue(value: Float): String = when {
    value >= 1000f -> String.format("%.0fk", value / 1000f)
    value == value.toLong().toFloat() -> String.format("%.0f", value)
    else -> String.format("%.1f", value)
}

/**
 * Draws a single data series: line path, area fill, and current-value label.
 */
private fun DrawScope.drawSeries(
    series: LineChartSeries,
    maxDataPoints: Int,
    yMin: Float,
    yMax: Float,
    chartLeft: Float,
    chartRight: Float,
    chartTop: Float,
    chartBottom: Float,
    chartWidth: Float,
    chartHeight: Float,
    alpha: Float,
    textMeasurer: TextMeasurer,
    onSurfaceColor: Color,
) {
    val data = series.data
    if (data.isEmpty()) return

    // Only render the last `maxDataPoints` values
    val visible = if (data.size > maxDataPoints) data.takeLast(maxDataPoints) else data
    val pointCount = visible.size
    if (pointCount < 2) {
        // Single point -- draw a dot
        val yRange = (yMax - yMin).coerceAtLeast(1f)
        val y = chartBottom - ((visible[0] - yMin) / yRange) * chartHeight
        drawCircle(
            color = series.color.copy(alpha = alpha),
            radius = 4f,
            center = Offset(chartRight, y),
        )
        return
    }

    val yRange = (yMax - yMin).coerceAtLeast(1f)
    val stepX = chartWidth / (maxDataPoints - 1).coerceAtLeast(1)

    // Starting X so that rightmost point sits at chartRight
    val startX = chartRight - (pointCount - 1) * stepX

    // Build points
    val points = visible.mapIndexed { index, value ->
        val x = startX + index * stepX
        val y = chartBottom - ((value - yMin) / yRange) * chartHeight
        Offset(x, y)
    }

    // Line path using cubic Bezier for smoothness
    val linePath = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val cpx = (prev.x + curr.x) / 2f
            cubicTo(cpx, prev.y, cpx, curr.y, curr.x, curr.y)
        }
    }

    // Area fill path (close down to bottom)
    val areaPath = Path().apply {
        addPath(linePath)
        lineTo(points.last().x, chartBottom)
        lineTo(points.first().x, chartBottom)
        close()
    }

    // Draw area fill
    drawPath(
        path = areaPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                series.color.copy(alpha = 0.3f * alpha),
                series.color.copy(alpha = 0.0f),
            ),
            startY = chartTop,
            endY = chartBottom,
        ),
        style = Fill,
    )

    // Draw line
    drawPath(
        path = linePath,
        color = series.color.copy(alpha = alpha),
        style = Stroke(
            width = 2.5f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )

    // Current-value dot at rightmost point
    val lastPoint = points.last()
    drawCircle(
        color = series.color.copy(alpha = alpha),
        radius = 4f,
        center = lastPoint,
    )
    // White inner dot
    drawCircle(
        color = Color.White.copy(alpha = 0.9f * alpha),
        radius = 2f,
        center = lastPoint,
    )

    // Current value label
    val lastValue = visible.last()
    val valueLabel = formatAxisValue(lastValue)
    val labelStyle = TextStyle(
        color = onSurfaceColor.copy(alpha = alpha),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val measured = textMeasurer.measure(valueLabel, labelStyle)
    // Position above the dot, nudge left if near right edge
    val labelX = (lastPoint.x - measured.size.width / 2f)
        .coerceIn(chartLeft, chartRight - measured.size.width)
    val labelY = (lastPoint.y - measured.size.height - 6f)
        .coerceAtLeast(chartTop)
    drawText(
        textLayoutResult = measured,
        topLeft = Offset(labelX, labelY),
    )
}

/**
 * Draws crosshair line, highlighted data points, and tooltip when the user touches the chart.
 */
private fun DrawScope.drawCrosshair(
    touchX: Float,
    dataSeries: List<LineChartSeries>,
    maxDataPoints: Int,
    yMin: Float,
    yMax: Float,
    chartLeft: Float,
    chartRight: Float,
    chartTop: Float,
    chartBottom: Float,
    chartWidth: Float,
    chartHeight: Float,
    textMeasurer: TextMeasurer,
) {
    val yRange = (yMax - yMin).coerceAtLeast(1f)
    val stepX = chartWidth / (maxDataPoints - 1).coerceAtLeast(1)

    // Collect per-series info at the touched index
    data class SeriesHit(val label: String, val value: Float, val y: Float, val color: Color)

    val hits = mutableListOf<SeriesHit>()
    var crosshairX = touchX.coerceIn(chartLeft, chartRight)

    for (series in dataSeries) {
        val data = series.data
        if (data.isEmpty()) continue

        val visible = if (data.size > maxDataPoints) data.takeLast(maxDataPoints) else data
        val pointCount = visible.size
        if (pointCount < 2) continue

        val startX = chartRight - (pointCount - 1) * stepX
        val dataIndex = ((crosshairX - startX) / stepX).roundToInt().coerceIn(0, pointCount - 1)

        // Snap crosshair to actual data point X
        crosshairX = startX + dataIndex * stepX

        val value = visible[dataIndex]
        val y = chartBottom - ((value - yMin) / yRange) * chartHeight
        hits.add(SeriesHit(series.label, value, y, series.color))
    }

    if (hits.isEmpty()) return

    // Clamp snapped crosshair within chart bounds
    crosshairX = crosshairX.coerceIn(chartLeft, chartRight)

    // Vertical crosshair line (dashed)
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
    drawLine(
        color = Color.White.copy(alpha = 0.5f),
        start = Offset(crosshairX, chartTop),
        end = Offset(crosshairX, chartBottom),
        strokeWidth = 1f,
        pathEffect = dashEffect,
    )

    // Highlighted dots at intersection points
    for (hit in hits) {
        drawCircle(
            color = hit.color,
            radius = 5f,
            center = Offset(crosshairX, hit.y),
        )
        drawCircle(
            color = Color.White,
            radius = 2.5f,
            center = Offset(crosshairX, hit.y),
        )
    }

    // Tooltip
    val tooltipBg = Color(0xDD1E1E1E)
    val tooltipTextColor = Color.White
    val tooltipStyle = TextStyle(color = tooltipTextColor, fontSize = 10.sp)
    val tooltipPaddingV = 6f
    val tooltipPaddingLeft = 16f  // space for color indicator + gap
    val tooltipPaddingRight = 8f
    val lineSpacing = 2f
    val indicatorRadius = 3.5f

    // Measure all tooltip lines
    val measuredLines = hits.map { hit ->
        val text = "${hit.label}: ${formatAxisValue(hit.value)}"
        text to textMeasurer.measure(text, tooltipStyle)
    }

    val tooltipContentWidth = measuredLines.maxOf { it.second.size.width }
    val tooltipContentHeight = measuredLines.sumOf { it.second.size.height } +
        (lineSpacing * (measuredLines.size - 1)).toInt()
    val tooltipWidth = tooltipContentWidth + tooltipPaddingLeft + tooltipPaddingRight
    val tooltipHeight = tooltipContentHeight + tooltipPaddingV * 2

    // Position tooltip: prefer right of crosshair, flip to left if it would overflow
    val tooltipGap = 8f
    var tooltipX = crosshairX + tooltipGap
    if (tooltipX + tooltipWidth > chartRight) {
        tooltipX = crosshairX - tooltipGap - tooltipWidth
    }
    tooltipX = tooltipX.coerceIn(chartLeft, chartRight - tooltipWidth)

    // Vertical: near top of chart area
    val tooltipY = chartTop.coerceAtLeast(0f)

    // Draw tooltip background
    drawRoundRect(
        color = tooltipBg,
        topLeft = Offset(tooltipX, tooltipY),
        size = Size(tooltipWidth, tooltipHeight),
        cornerRadius = CornerRadius(6f, 6f),
    )

    // Draw color indicators and text lines
    var textY = tooltipY + tooltipPaddingV
    for (i in hits.indices) {
        val (_, measured) = measuredLines[i]
        val lineHeight = measured.size.height

        // Color dot
        drawCircle(
            color = hits[i].color,
            radius = indicatorRadius,
            center = Offset(
                tooltipX + 8f,
                textY + lineHeight / 2f,
            ),
        )

        // Text
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(tooltipX + tooltipPaddingLeft, textY),
        )
        textY += lineHeight + lineSpacing
    }
}

/**
 * Legend row at the bottom of the chart.
 */
@Composable
private fun ChartLegend(
    dataSeries: List<LineChartSeries>,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dataSeries.forEachIndexed { index, series ->
            if (index > 0) {
                Spacer(modifier = Modifier.width(16.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(10.dp)) {
                    drawCircle(color = series.color)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = series.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun LineChartPreviewSingle() {
    val cpuTemps = (1..30).map { 42f + (it % 7) * 3f + (it * 0.5f) }
    LineChart(
        dataSeries = listOf(
            LineChartSeries("CPU Temp", cpuTemps, Color(0xFFEF5350)),
        ),
        yAxisLabel = "\u00B0C",
        modifier = Modifier.padding(16.dp),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun LineChartPreviewMulti() {
    val cpuTemps = (1..30).map { 42f + (it % 7) * 3f + (it * 0.5f) }
    val gpuTemps = (1..30).map { 50f + (it % 5) * 4f + (it * 0.3f) }
    LineChart(
        dataSeries = listOf(
            LineChartSeries("CPU", cpuTemps, Color(0xFF42A5F5)),
            LineChartSeries("GPU", gpuTemps, Color(0xFFAB47BC)),
        ),
        yAxisLabel = "\u00B0C",
        modifier = Modifier.padding(16.dp),
    )
}
