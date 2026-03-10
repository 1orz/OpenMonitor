package com.cloudorz.openmonitor.feature.fps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudorz.openmonitor.core.ui.theme.ChartGreen
import com.cloudorz.openmonitor.core.ui.theme.ChartRed
import com.cloudorz.openmonitor.core.ui.theme.ChartYellow

private val FpsGreen = ChartGreen
private val FpsYellow = ChartYellow
private val FpsRed = ChartRed

private val ReferenceLines = listOf(30f, 60f, 90f, 120f)

/**
 * A line chart displaying FPS history data.
 *
 * Shows FPS values as a continuous line with color zones based on performance:
 * - Green: >= targetFps
 * - Yellow: >= targetFps / 2
 * - Red: < targetFps / 2
 *
 * Includes horizontal reference lines at 30, 60, 90, and 120 fps and a
 * background gradient that reflects the current zone.
 *
 * @param fpsHistory List of FPS values to display (scrolls right to left).
 * @param modifier Modifier applied to the Canvas.
 * @param targetFps The target FPS for zone coloring (default 60).
 */
@Composable
fun FpsDataChart(
    fpsHistory: List<Float>,
    modifier: Modifier = Modifier,
    targetFps: Float = 60f,
) {
    val textMeasurer = rememberTextMeasurer()
    val maxDisplayFps = remember { 144f }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
    ) {
        val chartWidth = size.width
        val chartHeight = size.height
        val labelMarginLeft = 40f

        val drawableWidth = chartWidth - labelMarginLeft

        // Draw background zone gradient
        drawBackgroundZones(
            targetFps = targetFps,
            maxFps = maxDisplayFps,
            drawableWidth = drawableWidth,
            drawableHeight = chartHeight,
            offsetX = labelMarginLeft,
        )

        // Draw reference lines
        drawReferenceLines(
            referenceValues = ReferenceLines,
            maxFps = maxDisplayFps,
            drawableWidth = drawableWidth,
            drawableHeight = chartHeight,
            offsetX = labelMarginLeft,
            textMeasurer = textMeasurer,
        )

        // Draw FPS line
        if (fpsHistory.size >= 2) {
            drawFpsLine(
                fpsHistory = fpsHistory,
                targetFps = targetFps,
                maxFps = maxDisplayFps,
                drawableWidth = drawableWidth,
                drawableHeight = chartHeight,
                offsetX = labelMarginLeft,
            )
        }
    }
}

private fun DrawScope.drawBackgroundZones(
    targetFps: Float,
    maxFps: Float,
    drawableWidth: Float,
    drawableHeight: Float,
    offsetX: Float,
) {
    val halfTarget = targetFps / 2f

    // Red zone: 0 to halfTarget
    val redTop = drawableHeight - (halfTarget / maxFps) * drawableHeight
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(FpsRed.copy(alpha = 0.05f), FpsRed.copy(alpha = 0.1f)),
            startY = redTop,
            endY = drawableHeight,
        ),
        topLeft = Offset(offsetX, redTop),
        size = androidx.compose.ui.geometry.Size(drawableWidth, drawableHeight - redTop),
    )

    // Yellow zone: halfTarget to targetFps
    val yellowTop = drawableHeight - (targetFps / maxFps) * drawableHeight
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(FpsYellow.copy(alpha = 0.05f), FpsYellow.copy(alpha = 0.08f)),
            startY = yellowTop,
            endY = redTop,
        ),
        topLeft = Offset(offsetX, yellowTop),
        size = androidx.compose.ui.geometry.Size(drawableWidth, redTop - yellowTop),
    )

    // Green zone: targetFps to max
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(FpsGreen.copy(alpha = 0.08f), FpsGreen.copy(alpha = 0.05f)),
            startY = 0f,
            endY = yellowTop,
        ),
        topLeft = Offset(offsetX, 0f),
        size = androidx.compose.ui.geometry.Size(drawableWidth, yellowTop),
    )
}

private fun DrawScope.drawReferenceLines(
    referenceValues: List<Float>,
    maxFps: Float,
    drawableWidth: Float,
    drawableHeight: Float,
    offsetX: Float,
    textMeasurer: TextMeasurer,
) {
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)

    referenceValues.forEach { fps ->
        if (fps > maxFps) return@forEach

        val y = drawableHeight - (fps / maxFps) * drawableHeight
        val color = when {
            fps >= 60f -> FpsGreen.copy(alpha = 0.4f)
            fps >= 30f -> FpsYellow.copy(alpha = 0.4f)
            else -> FpsRed.copy(alpha = 0.4f)
        }

        drawLine(
            color = color,
            start = Offset(offsetX, y),
            end = Offset(offsetX + drawableWidth, y),
            strokeWidth = 1f,
            pathEffect = dashEffect,
        )

        val textResult = textMeasurer.measure(
            text = "${fps.toInt()}",
            style = TextStyle(
                color = color.copy(alpha = 0.8f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        drawText(
            textLayoutResult = textResult,
            topLeft = Offset(2f, y - textResult.size.height / 2f),
        )
    }
}

private fun DrawScope.drawFpsLine(
    fpsHistory: List<Float>,
    targetFps: Float,
    maxFps: Float,
    drawableWidth: Float,
    drawableHeight: Float,
    offsetX: Float,
) {
    val halfTarget = targetFps / 2f
    val pointCount = fpsHistory.size
    val stepX = drawableWidth / (pointCount - 1).coerceAtLeast(1)

    // Build path
    val path = Path()
    val fillPath = Path()

    fpsHistory.forEachIndexed { index, fps ->
        val x = offsetX + index * stepX
        val clampedFps = fps.coerceIn(0f, maxFps)
        val y = drawableHeight - (clampedFps / maxFps) * drawableHeight

        if (index == 0) {
            path.moveTo(x, y)
            fillPath.moveTo(x, drawableHeight)
            fillPath.lineTo(x, y)
        } else {
            path.lineTo(x, y)
            fillPath.lineTo(x, y)
        }
    }

    // Close fill path
    val lastX = offsetX + (pointCount - 1) * stepX
    fillPath.lineTo(lastX, drawableHeight)
    fillPath.close()

    // Draw fill gradient
    val lastFps = fpsHistory.lastOrNull() ?: targetFps
    val lineColor = fpsColor(lastFps, targetFps, halfTarget)

    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(lineColor.copy(alpha = 0.3f), lineColor.copy(alpha = 0.02f)),
        ),
    )

    // Draw line
    drawPath(
        path = path,
        color = lineColor,
        style = Stroke(
            width = 2.5f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )

    // Draw last point indicator
    if (fpsHistory.isNotEmpty()) {
        val lastY = drawableHeight - (lastFps.coerceIn(0f, maxFps) / maxFps) * drawableHeight
        drawCircle(
            color = lineColor,
            radius = 5f,
            center = Offset(lastX, lastY),
        )
        drawCircle(
            color = Color.White,
            radius = 2.5f,
            center = Offset(lastX, lastY),
        )
    }
}

private fun fpsColor(fps: Float, target: Float, halfTarget: Float): Color = when {
    fps >= target -> FpsGreen
    fps >= halfTarget -> FpsYellow
    else -> FpsRed
}
