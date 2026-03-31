package com.cloudorz.openmonitor.feature.battery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CurrentSparkline(
    data: List<Int>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val axisColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp),
    ) {
        if (data.size < 2) return@Canvas

        val minVal = data.min().toFloat()
        val maxVal = data.max().toFloat()
        val range = (maxVal - minVal).coerceAtLeast(1f)

        val labelMarginLeft = 48f
        val paddingRight = 6f
        val paddingTop = 6f
        val paddingBottom = 6f

        val chartLeft = labelMarginLeft
        val chartRight = size.width - paddingRight
        val chartTop = paddingTop
        val chartBottom = size.height - paddingBottom
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        fun valueToY(v: Float): Float =
            chartTop + (1f - (v - minVal) / range) * chartHeight

        val stepX = chartWidth / (data.size - 1).coerceAtLeast(1)

        // Y-axis grid levels: top / zero-or-mid / bottom
        val gridLevels: List<Float> = if (minVal < 0f && maxVal > 0f) {
            listOf(maxVal, 0f, minVal)
        } else {
            listOf(maxVal, (minVal + maxVal) / 2f, minVal)
        }

        gridLevels.forEach { v ->
            val y = valueToY(v)
            val isZero = v == 0f
            // Grid line
            drawLine(
                color = if (isZero) axisColor else gridColor,
                start = Offset(chartLeft, y),
                end = Offset(chartRight, y),
                strokeWidth = if (isZero) 1.5f else 1f,
            )
            // Y-axis label (right-aligned to left margin)
            val label = "${v.toInt()}"
            val result = textMeasurer.measure(label, TextStyle(fontSize = 8.sp, color = labelColor))
            drawText(
                textLayoutResult = result,
                topLeft = Offset(
                    x = labelMarginLeft - result.size.width - 4f,
                    y = y - result.size.height / 2f,
                ),
            )
        }

        // Y-axis line
        drawLine(axisColor, Offset(chartLeft, chartTop), Offset(chartLeft, chartBottom), strokeWidth = 1f)
        // X-axis baseline
        drawLine(axisColor, Offset(chartLeft, chartBottom), Offset(chartRight, chartBottom), strokeWidth = 1f)

        // Data line
        val path = Path()
        data.forEachIndexed { index, value ->
            val x = chartLeft + index * stepX
            val y = valueToY(value.toFloat())
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Last point dot
        if (data.isNotEmpty()) {
            val lastX = chartLeft + (data.size - 1) * stepX
            val lastY = valueToY(data.last().toFloat())
            drawCircle(color = lineColor, radius = 3.dp.toPx(), center = Offset(lastX, lastY))
        }
    }
}
