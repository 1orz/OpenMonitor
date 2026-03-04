package com.cloudorz.monitor.feature.fps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudorz.monitor.core.ui.theme.ChartGreen
import com.cloudorz.monitor.core.ui.theme.ChartRed
import com.cloudorz.monitor.core.ui.theme.ChartYellow

private val BarGreen = ChartGreen
private val BarYellow = ChartYellow
private val BarRed = ChartRed

private const val BAR_WIDTH_DP = 4
private const val BAR_SPACING_DP = 1
private const val MAX_FRAME_TIME_MS = 100f

/**
 * A bar chart displaying individual frame times.
 *
 * Each frame is represented as a vertical bar with color coding:
 * - Green: <= targetFrameTimeMs
 * - Yellow: <= targetFrameTimeMs * 2
 * - Red: > targetFrameTimeMs * 2
 *
 * Includes horizontal reference lines at 16ms and 33ms.
 * Auto-scrolls to show the latest frames.
 *
 * @param frameTimes Array of frame times in milliseconds.
 * @param modifier Modifier applied to the outer Box.
 * @param targetFrameTimeMs The target frame time for bar coloring (default 16ms).
 */
@Composable
fun FrameTimeChart(
    frameTimes: IntArray,
    modifier: Modifier = Modifier,
    targetFrameTimeMs: Int = 16,
) {
    val textMeasurer = rememberTextMeasurer()
    val scrollState = rememberScrollState()

    val totalBarWidth = (BAR_WIDTH_DP + BAR_SPACING_DP)
    val contentWidthDp = remember(frameTimes.size) {
        (frameTimes.size * totalBarWidth + 60).coerceAtLeast(200)
    }

    // Auto-scroll to the end when new frames arrive
    LaunchedEffect(frameTimes.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box(
        modifier = modifier.height(150.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .horizontalScroll(scrollState),
        ) {
            Canvas(
                modifier = Modifier
                    .width(contentWidthDp.dp)
                    .fillMaxHeight(),
            ) {
                val chartHeight = size.height
                val labelMarginLeft = 40f

                // Draw reference lines
                drawFrameTimeReferenceLines(
                    targetFrameTimeMs = targetFrameTimeMs,
                    chartHeight = chartHeight,
                    chartWidth = size.width,
                    labelMarginLeft = labelMarginLeft,
                    textMeasurer = textMeasurer,
                )

                // Draw bars
                val barWidthPx = BAR_WIDTH_DP.dp.toPx()
                val spacingPx = BAR_SPACING_DP.dp.toPx()

                frameTimes.forEachIndexed { index, frameTime ->
                    val x = labelMarginLeft + index * (barWidthPx + spacingPx)
                    val clampedTime = frameTime.toFloat().coerceIn(0f, MAX_FRAME_TIME_MS)
                    val barHeight = (clampedTime / MAX_FRAME_TIME_MS) * chartHeight
                    val y = chartHeight - barHeight

                    val color = frameTimeColor(frameTime, targetFrameTimeMs)

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(barWidthPx, barHeight),
                        cornerRadius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f),
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFrameTimeReferenceLines(
    targetFrameTimeMs: Int,
    chartHeight: Float,
    chartWidth: Float,
    labelMarginLeft: Float,
    textMeasurer: TextMeasurer,
) {
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)

    val referenceLines = listOf(
        targetFrameTimeMs to "16ms",
        targetFrameTimeMs * 2 + 1 to "33ms",
    )

    referenceLines.forEach { (ms, label) ->
        val clampedMs = ms.toFloat().coerceIn(0f, MAX_FRAME_TIME_MS)
        val y = chartHeight - (clampedMs / MAX_FRAME_TIME_MS) * chartHeight
        val color = if (ms <= targetFrameTimeMs) {
            BarGreen.copy(alpha = 0.5f)
        } else {
            BarRed.copy(alpha = 0.5f)
        }

        drawLine(
            color = color,
            start = Offset(labelMarginLeft, y),
            end = Offset(chartWidth, y),
            strokeWidth = 1f,
            pathEffect = dashEffect,
        )

        val textResult = textMeasurer.measure(
            text = label,
            style = TextStyle(
                color = color.copy(alpha = 0.8f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        drawText(
            textLayoutResult = textResult,
            topLeft = Offset(2f, y - textResult.size.height - 2f),
        )
    }
}

private fun frameTimeColor(frameTimeMs: Int, targetMs: Int): Color = when {
    frameTimeMs <= targetMs -> BarGreen
    frameTimeMs <= targetMs * 2 -> BarYellow
    else -> BarRed
}
