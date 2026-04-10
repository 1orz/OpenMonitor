package com.cloudorz.openmonitor.feature.battery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun CurrentSparkline(
    data: List<Int>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val axisColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val markerLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val tooltipBg = MaterialTheme.colorScheme.inverseSurface
    val tooltipTextColor = MaterialTheme.colorScheme.inverseOnSurface
    val textMeasurer = rememberTextMeasurer()

    var scale by remember { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var selectedIdx by remember { mutableIntStateOf(-1) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clipToBounds(),
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(data) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        val labelMarginLeft = 48f
                        val paddingRight = 6f
                        val chartWidth = size.width - labelMarginLeft - paddingRight
                        val scaledWidth = chartWidth * newScale
                        val maxOffset = 0f
                        val minOffset = -(scaledWidth - chartWidth)
                        val centerX = size.width / 2f
                        val newOffset =
                            panOffsetX + (centerX - labelMarginLeft) * (1 - zoom) * scale + pan.x
                        scale = newScale
                        panOffsetX = newOffset.coerceIn(minOffset, maxOffset)
                    }
                }
                .pointerInput(data) {
                    detectTapGestures { tapOffset ->
                        if (data.size < 2) return@detectTapGestures
                        val labelMarginLeft = 48f
                        val paddingRight = 6f
                        val chartWidth =
                            size.width - labelMarginLeft - paddingRight

                        // Convert tap X → data index
                        val dataX =
                            (tapOffset.x - labelMarginLeft - panOffsetX) / scale
                        val idx =
                            (dataX / chartWidth * (data.size - 1)).toInt()
                                .coerceIn(0, data.lastIndex)

                        selectedIdx = if (selectedIdx == idx) -1 else idx
                    }
                },
        ) {
            if (data.size < 2) return@Canvas

            val minVal = data.min().toFloat()
            val maxVal = data.max().toFloat()
            val range = (maxVal - minVal).coerceAtLeast(1f)

            val labelMarginLeft = 48f
            val paddingRight = 6f
            val paddingTop = 6f
            val paddingBottom = 6f

            val chartRight = size.width - paddingRight
            val chartBottom = size.height - paddingBottom
            val chartWidth = chartRight - labelMarginLeft
            val chartHeight = chartBottom - paddingTop

            fun valueToY(v: Float): Float =
                paddingTop + (1f - (v - minVal) / range) * chartHeight

            fun idxToX(i: Int): Float {
                val baseX =
                    (i.toFloat() / (data.size - 1).coerceAtLeast(1)) * chartWidth
                return labelMarginLeft + baseX * scale + panOffsetX
            }

            // Y-axis grid levels
            val gridLevels: List<Float> = if (minVal < 0f && maxVal > 0f) {
                listOf(maxVal, 0f, minVal)
            } else {
                listOf(maxVal, (minVal + maxVal) / 2f, minVal)
            }

            gridLevels.forEach { v ->
                val y = valueToY(v)
                val isZero = v == 0f
                drawLine(
                    color = if (isZero) axisColor else gridColor,
                    start = Offset(labelMarginLeft, y),
                    end = Offset(chartRight, y),
                    strokeWidth = if (isZero) 1.5f else 1f,
                )
                val label = "${v.toInt()}"
                val result = textMeasurer.measure(
                    label,
                    TextStyle(fontSize = 8.sp, color = labelColor),
                )
                drawText(
                    textLayoutResult = result,
                    topLeft = Offset(
                        x = labelMarginLeft - result.size.width - 4f,
                        y = y - result.size.height / 2f,
                    ),
                )
            }

            // Y-axis line
            drawLine(
                axisColor,
                Offset(labelMarginLeft, paddingTop),
                Offset(labelMarginLeft, chartBottom),
                strokeWidth = 1f,
            )
            // X-axis baseline
            drawLine(
                axisColor,
                Offset(labelMarginLeft, chartBottom),
                Offset(chartRight, chartBottom),
                strokeWidth = 1f,
            )

            // Data line
            val path = Path()
            data.forEachIndexed { index, value ->
                val x = idxToX(index)
                val y = valueToY(value.toFloat())
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(
                    width = 2f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )

            // Last point dot
            if (data.isNotEmpty()) {
                val lastX = idxToX(data.lastIndex)
                val lastY = valueToY(data.last().toFloat())
                if (lastX in labelMarginLeft..chartRight) {
                    drawCircle(
                        color = lineColor,
                        radius = 3.dp.toPx(),
                        center = Offset(lastX, lastY),
                    )
                }
            }

            // Selected point marker / tooltip
            if (selectedIdx in data.indices) {
                val value = data[selectedIdx]
                val px = idxToX(selectedIdx)
                val py = valueToY(value.toFloat())

                if (px in labelMarginLeft..chartRight) {
                    // Vertical dashed line
                    drawLine(
                        color = markerLineColor,
                        start = Offset(px, paddingTop),
                        end = Offset(px, chartBottom),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6f, 4f),
                        ),
                    )
                    // Highlight dot
                    drawCircle(
                        color = lineColor,
                        radius = 4f,
                        center = Offset(px, py),
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2f,
                        center = Offset(px, py),
                    )

                    // Tooltip
                    val tooltipStr = "${value} mA"
                    val tooltipStyle = TextStyle(
                        fontSize = 9.sp,
                        color = tooltipTextColor,
                    )
                    val measured =
                        textMeasurer.measure(tooltipStr, tooltipStyle)
                    val tooltipW = measured.size.width + 14f
                    val tooltipH = measured.size.height + 10f
                    val tooltipX =
                        (px - tooltipW / 2).coerceIn(
                            labelMarginLeft,
                            chartRight - tooltipW,
                        )
                    val tooltipY =
                        (py - tooltipH - 8f).coerceAtLeast(0f)

                    drawRoundRect(
                        color = tooltipBg,
                        topLeft = Offset(tooltipX, tooltipY),
                        size = Size(tooltipW, tooltipH),
                        cornerRadius = CornerRadius(6f, 6f),
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = tooltipStr,
                        topLeft = Offset(
                            tooltipX + 7f,
                            tooltipY + 5f,
                        ),
                        style = tooltipStyle,
                    )
                }
            }
        }
    }
}
