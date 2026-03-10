package com.cloudorz.openmonitor.core.ui.chart

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One segment of the RAM bar, representing a category of memory usage.
 *
 * @property label  Human-readable name (e.g. "Used", "Cached", "Free").
 * @property sizeKB Size of this segment in kilobytes.
 * @property color  Fill color for this segment.
 */
@Immutable
data class RamSegment(
    val label: String,
    val sizeKB: Long,
    val color: Color,
)

/**
 * A horizontal stacked progress bar that visualizes memory (RAM / swap) breakdown.
 *
 * Each [RamSegment] is drawn as a proportionally sized colored rectangle inside a
 * rounded-corner track.  Below the bar a legend row lists every segment with its
 * color swatch, label, and human-readable size.
 *
 * ### Animation
 * Each segment width animates smoothly when data changes.
 *
 * @param segments     Ordered list of memory segments to display.
 * @param modifier     [Modifier] applied to the root layout.
 * @param height       Height of the progress bar.
 * @param cornerRadius Corner radius applied to the bar's clipping shape.
 */
@Composable
fun RamBarView(
    segments: List<RamSegment>,
    modifier: Modifier = Modifier,
    height: Dp = 24.dp,
    cornerRadius: Dp = 12.dp,
) {
    val totalKB = remember(segments) {
        segments.sumOf { it.sizeKB }.coerceAtLeast(1L)
    }

    // Pre-compute fraction targets for animation
    val fractions = segments.map { segment ->
        segment.sizeKB.toFloat() / totalKB.toFloat()
    }

    // Animated fractions
    val animatedFractions = fractions.mapIndexed { index, target ->
        val animated by animateFloatAsState(
            targetValue = target,
            animationSpec = tween(durationMillis = 500),
            label = "ramSegment_$index",
        )
        animated
    }

    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        // Stacked bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(cornerRadius)),
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                // Full track background
                drawRect(color = trackColor)

                // Draw segments left-to-right
                var xOffset = 0f
                animatedFractions.forEachIndexed { index, fraction ->
                    val segmentWidth = size.width * fraction
                    if (segmentWidth > 0f) {
                        drawRect(
                            color = segments[index].color,
                            topLeft = Offset(xOffset, 0f),
                            size = Size(segmentWidth, size.height),
                        )
                    }
                    xOffset += segmentWidth
                }
            }
        }

        // Legend row
        Spacer(modifier = Modifier.height(8.dp))
        RamLegend(segments = segments)
    }
}

/**
 * Formats kilobytes into a human-readable string (KB, MB, GB).
 */
private fun formatSize(sizeKB: Long): String = when {
    sizeKB >= 1_048_576L -> String.format(java.util.Locale.US, "%.1f GB", sizeKB / 1_048_576.0)
    sizeKB >= 1_024L -> String.format(java.util.Locale.US, "%.1f MB", sizeKB / 1_024.0)
    else -> "$sizeKB KB"
}

/**
 * Legend row rendered below the stacked bar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RamLegend(
    segments: List<RamSegment>,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        segments.forEach { segment ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Color swatch
                Canvas(modifier = Modifier.size(10.dp)) {
                    drawCircle(color = segment.color)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${segment.label} ${formatSize(segment.sizeKB)}",
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
private fun RamBarViewPreview() {
    val segments = listOf(
        RamSegment("Used", 4_194_304, Color(0xFF42A5F5)),
        RamSegment("Cached", 1_572_864, Color(0xFF66BB6A)),
        RamSegment("Free", 2_359_296, Color(0xFF424242)),
    )
    RamBarView(
        segments = segments,
        modifier = Modifier.padding(16.dp),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun RamBarViewSwapPreview() {
    val segments = listOf(
        RamSegment("Swap Used", 524_288, Color(0xFFFFA726)),
        RamSegment("Swap Free", 1_572_864, Color(0xFF424242)),
    )
    RamBarView(
        segments = segments,
        modifier = Modifier.padding(16.dp),
    )
}
