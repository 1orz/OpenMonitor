package com.cloudorz.monitor.feature.charge

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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudorz.monitor.core.model.battery.BatteryStatus
import com.cloudorz.monitor.core.model.battery.ChargeStatSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ChargeScreen(
    viewModel: ChargeViewModel = hiltViewModel(),
    onSessionClick: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ChargeScreenContent(
        uiState = uiState,
        onChargingEnabledChanged = viewModel::onChargingEnabledChanged,
        onCurrentLimitChanged = viewModel::onCurrentLimitChanged,
        onNightChargingChanged = viewModel::onNightChargingChanged,
        onTemperatureProtectionChanged = viewModel::onTemperatureProtectionChanged,
        onMaxTemperatureChanged = viewModel::onMaxTemperatureChanged,
        onSessionClick = onSessionClick,
    )
}

@Composable
private fun ChargeScreenContent(
    uiState: ChargeUiState,
    onChargingEnabledChanged: (Boolean) -> Unit,
    onCurrentLimitChanged: (Int) -> Unit,
    onNightChargingChanged: (Boolean) -> Unit,
    onTemperatureProtectionChanged: (Boolean) -> Unit,
    onMaxTemperatureChanged: (Float) -> Unit,
    onSessionClick: (String) -> Unit,
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("充电统计", "充电控制")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                )
            }
        }

        when (selectedTabIndex) {
            0 -> ChargeStatsTab(
                uiState = uiState,
                onSessionClick = onSessionClick,
            )
            1 -> ChargeControlTab(
                uiState = uiState,
                onChargingEnabledChanged = onChargingEnabledChanged,
                onCurrentLimitChanged = onCurrentLimitChanged,
                onNightChargingChanged = onNightChargingChanged,
                onTemperatureProtectionChanged = onTemperatureProtectionChanged,
                onMaxTemperatureChanged = onMaxTemperatureChanged,
            )
        }
    }
}

// ============================================================
// Tab 1: Charge Statistics
// ============================================================

@Composable
private fun ChargeStatsTab(
    uiState: ChargeUiState,
    onSessionClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ChargeInfoCard(battery = uiState.currentBattery)
        }

        item {
            ChargeCurveSection(battery = uiState.currentBattery)
        }

        if (uiState.sessions.isNotEmpty()) {
            item {
                Text(
                    text = "充电历史记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            items(
                items = uiState.sessions,
                key = { it.sessionId },
            ) { session ->
                ChargeSessionItem(
                    session = session,
                    onClick = { onSessionClick(session.sessionId) },
                )
            }
        } else {
            item {
                EmptyChargeSessionsHint()
            }
        }
    }
}

@Composable
private fun ChargeInfoCard(battery: BatteryStatus) {
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
                    imageVector = Icons.Default.BatteryChargingFull,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (battery.isCharging) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "充电信息",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (battery.isCharging) "正在充电" else "未在充电",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (battery.isCharging) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = if (battery.isCharging) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ChargeDetailItem(
                    label = "电流",
                    value = "${abs(battery.currentMa)} mA",
                )
                ChargeDetailItem(
                    label = "电压",
                    value = String.format(Locale.US, "%.2f V", battery.voltageV),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ChargeDetailItem(
                    label = "功率",
                    value = String.format(Locale.US, "%.2f W", abs(battery.powerW)),
                )
                ChargeDetailItem(
                    label = "温度",
                    value = String.format(Locale.US, "%.1f \u00B0C", battery.temperatureCelsius),
                )
            }
        }
    }
}

@Composable
private fun ChargeDetailItem(
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
private fun ChargeCurveSection(battery: BatteryStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "充电曲线",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (battery.isCharging) {
                // Show placeholder chart when charging
                ChargeCurveChart(
                    records = emptyList(), // Real-time records would be collected here
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "充电曲线将在此显示",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "连接充电器后自动开始记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChargeSessionItem(
    session: ChargeStatSession,
    onClick: () -> Unit,
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
                verticalAlignment = Alignment.CenterVertically,
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

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "充电量",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f%%", session.capacityRatio * 100),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column {
                    Text(
                        text = "充入电量",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = String.format(Locale.US, "%.2f Wh", session.capacityWh),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column {
                    Text(
                        text = "时长",
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
private fun EmptyChargeSessionsHint() {
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
                imageVector = Icons.Default.BatteryChargingFull,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "暂无充电记录",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "充电时将自动记录充电数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ============================================================
// Tab 2: Charge Control
// ============================================================

@Composable
private fun ChargeControlTab(
    uiState: ChargeUiState,
    onChargingEnabledChanged: (Boolean) -> Unit,
    onCurrentLimitChanged: (Int) -> Unit,
    onNightChargingChanged: (Boolean) -> Unit,
    onTemperatureProtectionChanged: (Boolean) -> Unit,
    onMaxTemperatureChanged: (Float) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            RootWarningBanner()
        }

        item {
            ChargingToggleCard(
                chargingEnabled = uiState.chargingEnabled,
                onChargingEnabledChanged = onChargingEnabledChanged,
            )
        }

        item {
            CurrentLimitCard(
                currentLimit = uiState.currentLimit,
                onCurrentLimitChanged = onCurrentLimitChanged,
            )
        }

        item {
            NightChargingCard(
                nightChargingEnabled = uiState.nightChargingEnabled,
                onNightChargingChanged = onNightChargingChanged,
            )
        }

        item {
            TemperatureProtectionCard(
                temperatureProtection = uiState.temperatureProtection,
                maxTemperature = uiState.maxTemperature,
                currentTemperature = uiState.currentBattery.temperatureCelsius.toFloat(),
                onTemperatureProtectionChanged = onTemperatureProtectionChanged,
                onMaxTemperatureChanged = onMaxTemperatureChanged,
            )
        }
    }
}

@Composable
private fun RootWarningBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFFFF3E0),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color(0xFFE65100),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "充电控制需要 Root 权限",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE65100),
            )
        }
    }
}

@Composable
private fun ChargingToggleCard(
    chargingEnabled: Boolean,
    onChargingEnabledChanged: (Boolean) -> Unit,
) {
    ControlCard(
        icon = Icons.Default.FlashOn,
        iconTint = if (chargingEnabled) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
        title = "充电开关",
        description = if (chargingEnabled) "充电已启用" else "充电已禁用",
    ) {
        Switch(
            checked = chargingEnabled,
            onCheckedChange = onChargingEnabledChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF4CAF50),
                checkedTrackColor = Color(0xFFA5D6A7),
            ),
        )
    }
}

@Composable
private fun CurrentLimitCard(
    currentLimit: Int,
    onCurrentLimitChanged: (Int) -> Unit,
) {
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
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color(0xFF2196F3),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "充电电流限制",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (currentLimit == 0) "未设置限制" else "限制为 ${currentLimit} mA",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (currentLimit == 0) "无限制" else "${currentLimit} mA",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2196F3),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Slider(
                value = currentLimit.toFloat(),
                onValueChange = { onCurrentLimitChanged(it.roundToInt()) },
                valueRange = 500f..5000f,
                steps = 8, // 500, 1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF2196F3),
                    activeTrackColor = Color(0xFF2196F3),
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "500 mA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "5000 mA",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NightChargingCard(
    nightChargingEnabled: Boolean,
    onNightChargingChanged: (Boolean) -> Unit,
) {
    ControlCard(
        icon = Icons.Default.NightsStay,
        iconTint = if (nightChargingEnabled) Color(0xFF3F51B5) else Color(0xFF9E9E9E),
        title = "夜间充电模式",
        description = if (nightChargingEnabled) {
            "已启用 - 夜间自动降低充电速度以保护电池"
        } else {
            "未启用"
        },
    ) {
        Switch(
            checked = nightChargingEnabled,
            onCheckedChange = onNightChargingChanged,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF3F51B5),
                checkedTrackColor = Color(0xFF9FA8DA),
            ),
        )
    }
}

@Composable
private fun TemperatureProtectionCard(
    temperatureProtection: Boolean,
    maxTemperature: Float,
    currentTemperature: Float,
    onTemperatureProtectionChanged: (Boolean) -> Unit,
    onMaxTemperatureChanged: (Float) -> Unit,
) {
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
                    imageVector = Icons.Default.Thermostat,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (temperatureProtection) Color(0xFFF44336) else Color(0xFF9E9E9E),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "温度保护",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (temperatureProtection) {
                            "当温度超过 ${maxTemperature.roundToInt()}\u00B0C 时停止充电"
                        } else {
                            "未启用"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = temperatureProtection,
                    onCheckedChange = onTemperatureProtectionChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFF44336),
                        checkedTrackColor = Color(0xFFEF9A9A),
                    ),
                )
            }

            if (temperatureProtection) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "最高温度",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "${maxTemperature.roundToInt()}\u00B0C",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF44336),
                    )
                }

                Slider(
                    value = maxTemperature,
                    onValueChange = onMaxTemperatureChanged,
                    valueRange = 35f..45f,
                    steps = 9, // 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFF44336),
                        activeTrackColor = Color(0xFFF44336),
                    ),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "35\u00B0C",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "45\u00B0C",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (currentTemperature >= maxTemperature) {
                        Color(0xFFFFEBEE)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "当前电池温度: %.1f\u00B0C",
                            currentTemperature,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (currentTemperature >= maxTemperature) {
                            Color(0xFFF44336)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (currentTemperature >= maxTemperature) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    action: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = iconTint,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            action()
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
