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
import androidx.compose.ui.unit.dp

@Composable
fun CurrentSparkline(
    data: List<Int>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
    ) {
        if (data.size < 2) return@Canvas

        val minVal = data.min().toFloat()
        val maxVal = data.max().toFloat()
        val range = (maxVal - minVal).coerceAtLeast(1f)

        val stepX = size.width / (data.size - 1).coerceAtLeast(1)
        val padding = 4f

        val path = Path()
        data.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - padding -
                ((value - minVal) / range) * (size.height - padding * 2)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        // Draw current value dot
        if (data.isNotEmpty()) {
            val lastX = (data.size - 1) * stepX
            val lastY = size.height - padding -
                ((data.last() - minVal) / range) * (size.height - padding * 2)
            drawCircle(
                color = lineColor,
                radius = 3.dp.toPx(),
                center = Offset(lastX, lastY),
            )
        }
    }
}
