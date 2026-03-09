package com.cloudorz.openmonitor.core.ui.chart

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Immutable data class representing one CPU core for the bar chart.
 *
 * @property coreIndex   Zero-based core index.
 * @property frequencyMHz Current frequency in MHz.
 * @property loadPercent  Current load in the range 0 -- 100.
 * @property isOnline     Whether the core is online.
 */
@Immutable
data class CpuCoreBarData(
    val coreIndex: Int,
    val frequencyMHz: Long,
    val loadPercent: Double,
    val isOnline: Boolean,
)

/**
 * Returns a load-based color for a CPU core bar.
 *
 * | Range        | Color  |
 * |--------------|--------|
 * | 0 -- 50 %    | Green  |
 * | 50 -- 80 %   | Yellow |
 * | > 80 %       | Red    |
 */
private fun loadColor(loadPercent: Double): Color = when {
    loadPercent < 50.0 -> Color(0xFF4CAF50)
    loadPercent < 80.0 -> Color(0xFFFFC107)
    else -> Color(0xFFF44336)
}

/** Gray used for offline cores. */
private val OfflineGray = Color(0xFF616161)

/**
 * A vertical bar chart that renders one bar per CPU core, with height proportional
 * to the core's load and an in-bar frequency label.
 *
 * Cores are arranged in a flow-row grid with **4 columns per row** so that devices
 * with 8 or more cores wrap naturally.
 *
 * Offline cores are rendered as gray bars with an "OFF" label.
 *
 * @param cores       List of [CpuCoreBarData] entries, one per core.
 * @param modifier    [Modifier] applied to the root layout.
 * @param maxFreqMHz  Theoretical maximum frequency (used for display, not scaling).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CpuCoreBarChart(
    cores: List<CpuCoreBarData>,
    modifier: Modifier = Modifier,
    maxFreqMHz: Long = 3000,
) {
    val textMeasurer = rememberTextMeasurer()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 4,
    ) {
        cores.forEach { core ->
            CoreBar(
                core = core,
                textMeasurer = textMeasurer,
                onSurfaceColor = onSurfaceColor,
                trackColor = surfaceVariantColor,
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp),
            )
        }
    }
}

/**
 * A single animated core bar.
 */
@Composable
private fun CoreBar(
    core: CpuCoreBarData,
    textMeasurer: TextMeasurer,
    onSurfaceColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    val targetLoad = if (core.isOnline) core.loadPercent.toFloat().coerceIn(0f, 100f) else 0f
    val animatedLoad by animateFloatAsState(
        targetValue = targetLoad / 100f,
        animationSpec = tween(durationMillis = 500),
        label = "coreBarAnimation_${core.coreIndex}",
    )

    val barColor = if (core.isOnline) loadColor(core.loadPercent) else OfflineGray
    val freqText = if (core.isOnline) "${core.frequencyMHz}" else "OFF"

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Bar area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCoreBar(
                    animatedFraction = if (core.isOnline) animatedLoad else 1f,
                    barColor = barColor,
                    trackColor = trackColor,
                    freqText = freqText,
                    textMeasurer = textMeasurer,
                    textColor = onSurfaceColor,
                )
            }
        }

        // Core index label below the bar
        Text(
            text = "${core.coreIndex}",
            style = MaterialTheme.typography.labelSmall,
            color = onSurfaceColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * Draws a single vertical bar within the [DrawScope].
 *
 * The bar occupies the full width of the scope and grows upward from the bottom.
 * A rounded-corner rectangle serves as the background track, and a filled
 * rounded-corner rectangle represents the load.
 *
 * The frequency (or "OFF") text is drawn inside the bar near the bottom.
 */
private fun DrawScope.drawCoreBar(
    animatedFraction: Float,
    barColor: Color,
    trackColor: Color,
    freqText: String,
    textMeasurer: TextMeasurer,
    textColor: Color,
) {
    val cornerRadius = CornerRadius(8f, 8f)
    val barWidth = size.width
    val barHeight = size.height

    // Background track
    drawRoundRect(
        color = trackColor,
        topLeft = Offset.Zero,
        size = Size(barWidth, barHeight),
        cornerRadius = cornerRadius,
    )

    // Filled portion (grows from bottom)
    val filledHeight = barHeight * animatedFraction
    if (filledHeight > 0f) {
        drawRoundRect(
            color = barColor,
            topLeft = Offset(0f, barHeight - filledHeight),
            size = Size(barWidth, filledHeight),
            cornerRadius = cornerRadius,
        )
    }

    // Frequency / OFF label at the bottom of the bar
    val textStyle = TextStyle(
        color = textColor,
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
    val measured = textMeasurer.measure(freqText, textStyle)
    val textX = (barWidth - measured.size.width) / 2f
    val textY = barHeight - measured.size.height - 6f
    if (textY > 0f) {
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(textX, textY),
        )
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun CpuCoreBarChartPreview() {
    val cores = listOf(
        CpuCoreBarData(0, 2400, 25.0, true),
        CpuCoreBarData(1, 2800, 65.0, true),
        CpuCoreBarData(2, 1800, 90.0, true),
        CpuCoreBarData(3, 0, 0.0, false),
        CpuCoreBarData(4, 3000, 45.0, true),
        CpuCoreBarData(5, 2200, 78.0, true),
        CpuCoreBarData(6, 1500, 12.0, true),
        CpuCoreBarData(7, 2600, 55.0, true),
    )
    CpuCoreBarChart(
        cores = cores,
        modifier = Modifier.padding(16.dp),
    )
}
