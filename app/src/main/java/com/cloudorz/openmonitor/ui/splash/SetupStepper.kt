package com.cloudorz.openmonitor.ui.splash

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cloudorz.openmonitor.R

/**
 * 引导流程步骤指示器。
 *
 *   ●───●───◉···○···○
 *  协议  权限  激活  模式
 *
 * @param currentStep 当前步骤索引 (0-based)
 * @param totalSteps  总步骤数
 * @param labels      每步的标签文字
 */
@Composable
fun SetupStepper(
    currentStep: Int,
    totalSteps: Int = 4,
    labels: List<String>,
) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until totalSteps) {
            val isCompleted = i < currentStep
            val isCurrent = i == currentStep

            val dotColor by animateColorAsState(
                targetValue = when {
                    isCompleted -> primary
                    isCurrent -> primary
                    else -> outline
                },
                animationSpec = tween(300),
                label = "dotColor$i",
            )

            // Step dot + label column
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f),
            ) {
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Canvas(modifier = Modifier.size(16.dp)) {
                        val radius = size.minDimension / 2
                        when {
                            isCompleted -> {
                                // Filled circle
                                drawCircle(color = dotColor, radius = radius)
                                // Checkmark
                                val cx = size.width / 2
                                val cy = size.height / 2
                                val s = radius * 0.45f
                                drawLine(
                                    color = Color.White,
                                    start = Offset(cx - s, cy),
                                    end = Offset(cx - s * 0.2f, cy + s * 0.7f),
                                    strokeWidth = 2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                )
                                drawLine(
                                    color = Color.White,
                                    start = Offset(cx - s * 0.2f, cy + s * 0.7f),
                                    end = Offset(cx + s, cy - s * 0.5f),
                                    strokeWidth = 2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                )
                            }
                            isCurrent -> {
                                // Outlined circle with inner dot
                                drawCircle(color = dotColor, radius = radius, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                                drawCircle(color = dotColor, radius = radius * 0.4f)
                            }
                            else -> {
                                // Empty outlined circle
                                drawCircle(color = dotColor, radius = radius, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
                            }
                        }
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = labels.getOrElse(i) { "" },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCompleted || isCurrent) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }

            // Connector line between dots (not after the last)
            if (i < totalSteps - 1) {
                val lineColor by animateColorAsState(
                    targetValue = if (i < currentStep) primary else outline,
                    animationSpec = tween(300),
                    label = "lineColor$i",
                )
                Canvas(
                    modifier = Modifier
                        .weight(0.6f)
                        .height(16.dp)
                        .padding(bottom = 18.dp),
                ) {
                    val y = size.height / 2
                    if (i < currentStep) {
                        // Solid line for completed
                        drawLine(
                            color = lineColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    } else {
                        // Dashed line for upcoming
                        drawLine(
                            color = lineColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}

/** Stepper 标签（按流程顺序） */
@Composable
fun setupStepperLabels(): List<String> = listOf(
    stringResource(R.string.stepper_agreement),
    stringResource(R.string.stepper_permissions),
    stringResource(R.string.stepper_activation),
    stringResource(R.string.stepper_mode),
)
