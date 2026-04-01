package com.cloudorz.openmonitor.feature.battery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudorz.openmonitor.core.model.battery.BatteryEstimation
import com.cloudorz.openmonitor.core.model.battery.BatteryStatus
import com.cloudorz.openmonitor.core.ui.R

@Composable
fun BatteryStatusCard(
    battery: BatteryStatus,
    estimation: BatteryEstimation,
    sparklineData: List<Int>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Section title
            Text(
                text = stringResource(R.string.battery_status),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Battery percentage + status
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "${battery.capacity}%",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Column {
                    Text(
                        text = if (battery.isCharging) {
                            stringResource(R.string.battery_charging)
                        } else {
                            stringResource(R.string.battery_discharging)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (battery.isCharging) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    if (estimation.remainingMinutes > 0) {
                        val hours = estimation.remainingMinutes / 60
                        val minutes = estimation.remainingMinutes % 60
                        Text(
                            text = "${hours}${stringResource(R.string.battery_hour)} ${minutes}${stringResource(R.string.battery_minute)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (estimation.drainRatePercentPerMinute != 0.0) {
                        Text(
                            text = "%.2f%%/${stringResource(R.string.battery_minute)}".format(
                                -estimation.drainRatePercentPerMinute,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Real-time metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "%.1f°C".format(battery.temperatureCelsius),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "%.3fV".format(battery.voltageV),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "%.1fW".format(battery.powerW),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Current sparkline
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.battery_current),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${battery.currentMa} mA",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    CurrentSparkline(data = sparklineData)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Info section
            Text(
                text = stringResource(R.string.battery_info),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                InfoItem(
                    label = stringResource(R.string.battery_technology),
                    value = battery.technology.ifEmpty { "Li-ion" },
                )
                InfoItem(
                    label = stringResource(R.string.battery_health),
                    value = battery.health.ifEmpty { "${battery.healthPercent}%" },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Capacity section
            Text(
                text = stringResource(R.string.battery_capacity_health),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                InfoItem(
                    label = stringResource(R.string.battery_design_capacity),
                    value = if (battery.capacityMah > 0) {
                        "%.0f mAh".format(battery.capacityMah)
                    } else {
                        "--"
                    },
                )
                InfoItem(
                    label = stringResource(R.string.battery_current_energy),
                    value = "%.1f Wh".format(
                        battery.capacityMah / 1000.0 * battery.voltageV * battery.capacity / 100.0,
                    ),
                )
            }
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}
