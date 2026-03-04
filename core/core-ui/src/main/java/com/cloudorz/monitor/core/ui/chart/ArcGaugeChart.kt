package com.cloudorz.monitor.core.ui.chart

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Default sentinel color used to trigger automatic load-based coloring.
 * When [foregroundColor] equals this value, the arc color is derived from
 * the current [percentage] (green / yellow / red).
 */
private val AutoColor = Color.Unspecified

/**
 * Returns a color on the green-yellow-red spectrum based on the given percentage.
 *
 * | Range     | Color  |
 * |-----------|--------|
 * | 0 -- 60   | Green  |
 * | 60 -- 80  | Yellow |
 * | 80 -- 100 | Red    |
 */
private fun percentageColor(percentage: Float): Color = when {
    percentage < 60f -> Color(0xFF4CAF50)
    percentage < 80f -> Color(0xFFFFC107)
    else -> Color(0xFFF44336)
}

/**
 * A circular arc gauge that displays a percentage value as a sweeping arc.
 *
 * The arc spans 240 degrees (from 150 to 390 degrees), leaving an open gap at the
 * bottom.  A background track is drawn first, and a foreground arc fills on top
 * proportionally to [percentage].  The center of the gauge shows [valueText] in a
 * large font and [label] in a smaller font below it.
 *
 * ### Automatic coloring
 * If [foregroundColor] is left at its default ([Color.Unspecified]), the foreground
 * arc color is chosen automatically based on the percentage value:
 * - **Green** for 0 -- 60 %
 * - **Yellow** for 60 -- 80 %
 * - **Red** for 80 -- 100 %
 *
 * ### Animation
 * The arc animates smoothly between percentage values using [animateFloatAsState].
 *
 * @param percentage Value in the range 0 -- 100 that controls arc fill.
 * @param modifier   [Modifier] applied to the root layout.
 * @param size       Overall diameter of the gauge.
 * @param strokeWidth Thickness of the arc stroke.
 * @param backgroundColor Color of the background track arc.
 * @param foregroundColor Explicit arc color, or [Color.Unspecified] for automatic.
 * @param label      Small text displayed below [valueText] (e.g. "Memory").
 * @param valueText  Large text displayed at the center (e.g. "72 %").
 */
@Composable
fun ArcGaugeChart(
    percentage: Float,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = 10.dp,
    backgroundColor: Color = Color(0xFF2A2A2A),
    foregroundColor: Color = AutoColor,
    label: String = "",
    valueText: String = "",
) {
    val clampedPercentage = percentage.coerceIn(0f, 100f)

    // Animate the sweep fraction from 0..1
    val animatedFraction by animateFloatAsState(
        targetValue = clampedPercentage / 100f,
        animationSpec = tween(durationMillis = 600),
        label = "arcGaugeAnimation",
    )

    // Resolve foreground color -- automatic when Unspecified
    val resolvedColor = remember(foregroundColor, clampedPercentage) {
        if (foregroundColor == AutoColor) percentageColor(clampedPercentage) else foregroundColor
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Total arc sweep in degrees
    val totalSweep = 240f
    // Start angle in degrees (measured clockwise from 3-o'clock)
    val startAngle = 150f

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val padding = strokePx / 2f
            val arcSize = Size(
                width = this.size.width - strokePx,
                height = this.size.height - strokePx,
            )
            val topLeft = Offset(padding, padding)

            // Background track
            drawArc(
                color = backgroundColor,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )

            // Foreground arc
            val foregroundSweep = totalSweep * animatedFraction
            if (foregroundSweep > 0f) {
                drawArc(
                    color = resolvedColor,
                    startAngle = startAngle,
                    sweepAngle = foregroundSweep,
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
            verticalArrangement = Arrangement.Center,
        ) {
            if (valueText.isNotEmpty()) {
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value / 5.5f).sp,
                    ),
                    color = textColor,
                    textAlign = TextAlign.Center,
                )
            }
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = (size.value / 10f).sp,
                    ),
                    color = labelColor,
                    textAlign = TextAlign.Center,
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
private fun ArcGaugeChartPreviewLow() {
    ArcGaugeChart(
        percentage = 35f,
        label = "CPU",
        valueText = "35%",
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ArcGaugeChartPreviewMid() {
    ArcGaugeChart(
        percentage = 72f,
        label = "Memory",
        valueText = "72%",
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ArcGaugeChartPreviewHigh() {
    ArcGaugeChart(
        percentage = 95f,
        label = "GPU",
        valueText = "95%",
    )
}
