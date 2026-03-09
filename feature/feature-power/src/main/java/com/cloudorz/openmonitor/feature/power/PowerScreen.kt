package com.cloudorz.openmonitor.feature.power

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudorz.openmonitor.core.model.battery.BatteryStatus
import com.cloudorz.openmonitor.core.model.battery.PowerStatSession
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.cloudorz.openmonitor.core.ui.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun PowerScreen(
    viewModel: PowerViewModel = hiltViewModel(),
    onSessionClick: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    PowerScreenContent(
        uiState = uiState,
        onToggleTracking = viewModel::onToggleTracking,
        onSessionClick = onSessionClick,
        onExportSession = { sessionId ->
            viewModel.getExportIntent(sessionId) { intent ->
                context.startActivity(intent)
            }
        },
    )
}

@Composable
private fun PowerScreenContent(
    uiState: PowerUiState,
    onToggleTracking: () -> Unit,
    onSessionClick: (String) -> Unit,
    onExportSession: (Long) -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            BatteryInfoCard(battery = uiState.currentBattery)
        }

        item {
            BatteryHealthCard(battery = uiState.currentBattery)
        }

        item {
            TrackingButton(
                isTracking = uiState.isTracking,
                onToggle = onToggleTracking,
            )
        }

        if (uiState.sessions.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.power_history),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            items(
                items = uiState.sessions,
                key = { it.sessionId },
            ) { session ->
                SessionItem(
                    session = session,
                    onClick = { onSessionClick(session.sessionId) },
                    onExport = { onExportSession(session.sessionId.toLongOrNull() ?: 0L) },
                )
            }
        } else {
            item {
                EmptySessionsHint()
            }
        }
    }
}

@Composable
private fun BatteryInfoCard(battery: BatteryStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when {
                        battery.isCharging -> Icons.Default.BatteryChargingFull
                        battery.capacity > 20 -> Icons.Default.BatteryFull
                        else -> Icons.Default.BatteryStd
                    },
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = when {
                        battery.isCharging -> Color(0xFF4CAF50)
                        battery.capacity <= 20 -> Color(0xFFF44336)
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.battery_info),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = battery.statusText.ifEmpty { battery.status.displayName },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${battery.capacity}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        battery.capacity <= 20 -> Color(0xFFF44336)
                        battery.capacity <= 50 -> Color(0xFFFF9800)
                        else -> Color(0xFF4CAF50)
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { battery.capacity / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = when {
                    battery.capacity <= 20 -> Color(0xFFF44336)
                    battery.capacity <= 50 -> Color(0xFFFF9800)
                    else -> Color(0xFF4CAF50)
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BatteryDetailItem(
                    label = stringResource(R.string.voltage),
                    value = String.format(Locale.US, "%.2f V", battery.voltageV),
                )
                BatteryDetailItem(
                    label = stringResource(R.string.current),
                    value = "${abs(battery.currentMa)} mA",
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BatteryDetailItem(
                    label = stringResource(R.string.power),
                    value = String.format(Locale.US, "%.2f W", abs(battery.powerW)),
                )
                BatteryDetailItem(
                    label = stringResource(R.string.temperature),
                    value = String.format(Locale.US, "%.1f \u00B0C", battery.temperatureCelsius),
                )
            }

            if (battery.technology.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    BatteryDetailItem(
                        label = stringResource(R.string.technology),
                        value = battery.technology,
                    )
                    if (battery.chargerType.isNotEmpty()) {
                        BatteryDetailItem(
                            label = stringResource(R.string.charger_type),
                            value = battery.chargerType,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryDetailItem(
    label: String,
    value: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun BatteryHealthCard(battery: BatteryStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = when {
                        battery.healthPercent >= 80 -> Color(0xFF4CAF50)
                        battery.healthPercent >= 50 -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.battery_health),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${battery.healthPercent}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        battery.healthPercent >= 80 -> Color(0xFF4CAF50)
                        battery.healthPercent >= 50 -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { battery.healthPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = when {
                    battery.healthPercent >= 80 -> Color(0xFF4CAF50)
                    battery.healthPercent >= 50 -> Color(0xFFFF9800)
                    else -> Color(0xFFF44336)
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when {
                    battery.healthPercent >= 90 -> stringResource(R.string.battery_health_good)
                    battery.healthPercent >= 80 -> stringResource(R.string.battery_health_normal)
                    battery.healthPercent >= 50 -> stringResource(R.string.battery_health_degraded)
                    else -> stringResource(R.string.battery_health_poor)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrackingButton(
    isTracking: Boolean,
    onToggle: () -> Unit,
) {
    Button(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        colors = if (isTracking) {
            ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF44336),
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Icon(
            imageVector = if (isTracking) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isTracking) stringResource(R.string.stop_power_tracking) else stringResource(R.string.start_power_tracking),
        )
    }
}

@Composable
private fun SessionItem(
    session: PowerStatSession,
    onClick: () -> Unit,
    onExport: () -> Unit = {},
) {
    val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${dateFormat.format(Date(session.beginTime))} - ${dateFormat.format(Date(session.endTime))}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                IconButton(onClick = onExport) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "CSV",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.power_consumed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f%%", session.usedPercent),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.avg_power),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = String.format(Locale.US, "%.2f W", session.avgPowerW),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatDuration(session.durationSeconds),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySessionsHint() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.BatteryStd,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.no_power_records),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.start_tracking_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${totalSeconds}s"
    }
}
