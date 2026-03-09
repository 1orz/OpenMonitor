package com.cloudorz.openmonitor.feature.charge

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cloudorz.openmonitor.core.ui.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChargeChartPoint(
    val timestamp: Long,
    val capacity: Int,      // 0-100%
    val currentMa: Long,
    val temperature: Float,
)

private val CapacityColor = Color(0xFF4CAF50)
private val CurrentColor = Color(0xFF2196F3)
private val TemperatureColor = Color(0xFFF44336)
private val GridColor = Color(0x33888888)
private val AxisLabelColor = Color(0xFF888888)

private const val PADDING_LEFT = 56f
private const val PADDING_RIGHT = 56f
private const val PADDING_TOP = 16f
private const val PADDING_BOTTOM = 40f
private const val GRID_LINE_COUNT = 5

@Composable
fun ChargeCurveChart(
    records: List<ChargeChartPoint>,
    modifier: Modifier = Modifier,
) {
    if (records.size < 2) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.charge_curve_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.charge_curve_will_show),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.charge_curve_min_points),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val sortedRecords = remember(records) { records.sortedBy { it.timestamp } }

    val minTime = sortedRecords.first().timestamp
    val maxTime = sortedRecords.last().timestamp
    val timeRange = (maxTime - minTime).coerceAtLeast(1L)

    val maxCurrent = remember(sortedRecords) {
        sortedRecords.maxOf { it.currentMa }.coerceAtLeast(1L)
    }
    val minTemperature = remember(sortedRecords) {
        sortedRecords.minOf { it.temperature }.coerceAtLeast(0f)
    }
    val maxTemperature = remember(sortedRecords) {
        sortedRecords.maxOf { it.temperature }.coerceAtLeast(minTemperature + 1f)
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val legendCapacity = stringResource(R.string.legend_capacity)
    val legendCurrent = stringResource(R.string.legend_current)
    val legendTemperature = stringResource(R.string.legend_temperature)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp),
    ) {
        val chartLeft = PADDING_LEFT
        val chartRight = size.width - PADDING_RIGHT
        val chartTop = PADDING_TOP
        val chartBottom = size.height - PADDING_BOTTOM
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        // Draw grid lines
        drawGridLines(
            chartLeft = chartLeft,
            chartRight = chartRight,
            chartTop = chartTop,
            chartBottom = chartBottom,
            chartHeight = chartHeight,
        )

        // Draw left Y axis labels (Capacity %)
        drawLeftAxisLabels(
            chartLeft = chartLeft,
            chartTop = chartTop,
            chartHeight = chartHeight,
        )

        // Draw right Y axis labels (Current mA)
        drawRightAxisLabels(
            chartRight = chartRight,
            chartTop = chartTop,
            chartHeight = chartHeight,
            maxCurrent = maxCurrent,
        )

        // Draw X axis labels (time)
        drawTimeAxisLabels(
            sortedRecords = sortedRecords,
            minTime = minTime,
            timeRange = timeRange,
            chartLeft = chartLeft,
            chartWidth = chartWidth,
            chartBottom = chartBottom,
            timeFormat = timeFormat,
        )

        // Draw capacity line (green)
        drawDataLine(
            sortedRecords = sortedRecords,
            minTime = minTime,
            timeRange = timeRange,
            chartLeft = chartLeft,
            chartWidth = chartWidth,
            chartTop = chartTop,
            chartHeight = chartHeight,
            color = CapacityColor,
            strokeWidth = 3f,
            valueMapper = { it.capacity / 100f },
        )

        // Draw current line (blue)
        drawDataLine(
            sortedRecords = sortedRecords,
            minTime = minTime,
            timeRange = timeRange,
            chartLeft = chartLeft,
            chartWidth = chartWidth,
            chartTop = chartTop,
            chartHeight = chartHeight,
            color = CurrentColor,
            strokeWidth = 2f,
            valueMapper = { it.currentMa.toFloat() / maxCurrent.toFloat() },
        )

        // Draw temperature line (red, dashed)
        drawDataLine(
            sortedRecords = sortedRecords,
            minTime = minTime,
            timeRange = timeRange,
            chartLeft = chartLeft,
            chartWidth = chartWidth,
            chartTop = chartTop,
            chartHeight = chartHeight,
            color = TemperatureColor,
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f)),
            valueMapper = { (it.temperature - minTemperature) / (maxTemperature - minTemperature) },
        )

        // Draw legend
        drawLegend(
            chartLeft = chartLeft,
            chartTop = chartTop,
            capacityLabel = legendCapacity,
            currentLabel = legendCurrent,
            temperatureLabel = legendTemperature,
        )
    }
}

private fun DrawScope.drawGridLines(
    chartLeft: Float,
    chartRight: Float,
    chartTop: Float,
    chartBottom: Float,
    chartHeight: Float,
) {
    for (i in 0..GRID_LINE_COUNT) {
        val y = chartTop + chartHeight * i / GRID_LINE_COUNT
        drawLine(
            color = GridColor,
            start = Offset(chartLeft, y),
            end = Offset(chartRight, y),
            strokeWidth = 1f,
        )
    }
    // Draw chart border
    drawLine(
        color = GridColor,
        start = Offset(chartLeft, chartTop),
        end = Offset(chartLeft, chartBottom),
        strokeWidth = 1f,
    )
    drawLine(
        color = GridColor,
        start = Offset(chartRight, chartTop),
        end = Offset(chartRight, chartBottom),
        strokeWidth = 1f,
    )
}

private fun DrawScope.drawLeftAxisLabels(
    chartLeft: Float,
    chartTop: Float,
    chartHeight: Float,
) {
    val paint = android.graphics.Paint().apply {
        color = CapacityColor.hashCode()
        textSize = 24f
        textAlign = android.graphics.Paint.Align.RIGHT
    }
    for (i in 0..GRID_LINE_COUNT) {
        val value = 100 - (100 * i / GRID_LINE_COUNT)
        val y = chartTop + chartHeight * i / GRID_LINE_COUNT
        drawContext.canvas.nativeCanvas.drawText(
            "${value}%",
            chartLeft - 8f,
            y + 8f,
            paint,
        )
    }
}

private fun DrawScope.drawRightAxisLabels(
    chartRight: Float,
    chartTop: Float,
    chartHeight: Float,
    maxCurrent: Long,
) {
    val paint = android.graphics.Paint().apply {
        color = CurrentColor.hashCode()
        textSize = 24f
        textAlign = android.graphics.Paint.Align.LEFT
    }
    for (i in 0..GRID_LINE_COUNT) {
        val value = maxCurrent - (maxCurrent * i / GRID_LINE_COUNT)
        val y = chartTop + chartHeight * i / GRID_LINE_COUNT
        drawContext.canvas.nativeCanvas.drawText(
            "${value}mA",
            chartRight + 8f,
            y + 8f,
            paint,
        )
    }
}

private fun DrawScope.drawTimeAxisLabels(
    sortedRecords: List<ChargeChartPoint>,
    minTime: Long,
    timeRange: Long,
    chartLeft: Float,
    chartWidth: Float,
    chartBottom: Float,
    timeFormat: SimpleDateFormat,
) {
    val paint = android.graphics.Paint().apply {
        color = AxisLabelColor.hashCode()
        textSize = 22f
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val labelCount = 4.coerceAtMost(sortedRecords.size)
    for (i in 0 until labelCount) {
        val index = i * (sortedRecords.size - 1) / (labelCount - 1).coerceAtLeast(1)
        val record = sortedRecords[index]
        val x = chartLeft + chartWidth * ((record.timestamp - minTime).toFloat() / timeRange)
        drawContext.canvas.nativeCanvas.drawText(
            timeFormat.format(Date(record.timestamp)),
            x,
            chartBottom + 28f,
            paint,
        )
    }
}

private fun DrawScope.drawDataLine(
    sortedRecords: List<ChargeChartPoint>,
    minTime: Long,
    timeRange: Long,
    chartLeft: Float,
    chartWidth: Float,
    chartTop: Float,
    chartHeight: Float,
    color: Color,
    strokeWidth: Float,
    pathEffect: PathEffect? = null,
    valueMapper: (ChargeChartPoint) -> Float,
) {
    val path = Path()
    sortedRecords.forEachIndexed { index, record ->
        val x = chartLeft + chartWidth * ((record.timestamp - minTime).toFloat() / timeRange)
        val normalizedValue = valueMapper(record).coerceIn(0f, 1f)
        val y = chartTop + chartHeight * (1f - normalizedValue)
        if (index == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth * density,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = pathEffect,
        ),
    )
}

private fun DrawScope.drawLegend(
    chartLeft: Float,
    chartTop: Float,
    capacityLabel: String,
    currentLabel: String,
    temperatureLabel: String,
) {
    val legendY = chartTop + 4f
    val paint = android.graphics.Paint().apply {
        textSize = 22f
        textAlign = android.graphics.Paint.Align.LEFT
    }

    // Capacity legend
    var legendX = chartLeft + 8f
    drawLine(
        color = CapacityColor,
        start = Offset(legendX, legendY),
        end = Offset(legendX + 20f, legendY),
        strokeWidth = 3f,
    )
    paint.color = CapacityColor.hashCode()
    drawContext.canvas.nativeCanvas.drawText(capacityLabel, legendX + 24f, legendY + 6f, paint)
    legendX += 80f

    // Current legend
    drawLine(
        color = CurrentColor,
        start = Offset(legendX, legendY),
        end = Offset(legendX + 20f, legendY),
        strokeWidth = 2f,
    )
    paint.color = CurrentColor.hashCode()
    drawContext.canvas.nativeCanvas.drawText(currentLabel, legendX + 24f, legendY + 6f, paint)
    legendX += 80f

    // Temperature legend
    drawLine(
        color = TemperatureColor,
        start = Offset(legendX, legendY),
        end = Offset(legendX + 20f, legendY),
        strokeWidth = 1.5f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 3f)),
    )
    paint.color = TemperatureColor.hashCode()
    drawContext.canvas.nativeCanvas.drawText(temperatureLabel, legendX + 24f, legendY + 6f, paint)
}
