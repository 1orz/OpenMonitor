package com.cloudorz.monitor.feature.appbias

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cloudorz.monitor.core.database.entity.AppConfigEntity

private data class OrientationOption(val value: Int, val label: String)

private val orientationOptions = listOf(
    OrientationOption(-1, "自动"),
    OrientationOption(0, "横屏"),
    OrientationOption(1, "竖屏"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBiasDetailSheet(
    item: AppBiasItem,
    onDismiss: () -> Unit,
    onSave: (AppConfigEntity) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val config = item.config

    // Mutable state for each config toggle
    var aloneLight by remember { mutableStateOf(config?.aloneLight ?: false) }
    var aloneLightValue by remember { mutableFloatStateOf((config?.aloneLightValue ?: 128).toFloat()) }
    var disNotice by remember { mutableStateOf(config?.disNotice ?: false) }
    var disButton by remember { mutableStateOf(config?.disButton ?: false) }
    var gpsOn by remember { mutableStateOf(config?.gpsOn ?: false) }
    var freeze by remember { mutableStateOf(config?.freeze ?: false) }
    var screenOrientation by remember { mutableIntStateOf(config?.screenOrientation ?: -1) }
    var showMonitor by remember { mutableStateOf(config?.showMonitor ?: false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            // App name header
            Text(
                text = item.appName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = item.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // 独立亮度 (aloneLight)
            ConfigSwitchRow(
                label = "独立亮度",
                checked = aloneLight,
                onCheckedChange = { aloneLight = it },
            )
            if (aloneLight) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "亮度值: ${aloneLightValue.toInt()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = aloneLightValue,
                        onValueChange = { aloneLightValue = it },
                        valueRange = 0f..255f,
                        steps = 0,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 屏蔽通知 (disNotice)
            ConfigSwitchRow(
                label = "屏蔽通知",
                checked = disNotice,
                onCheckedChange = { disNotice = it },
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 屏蔽按键 (disButton)
            ConfigSwitchRow(
                label = "屏蔽按键",
                checked = disButton,
                onCheckedChange = { disButton = it },
            )

            Spacer(modifier = Modifier.height(4.dp))

            // GPS强制开启 (gpsOn)
            ConfigSwitchRow(
                label = "GPS强制开启",
                checked = gpsOn,
                onCheckedChange = { gpsOn = it },
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 冻结应用 (freeze) with warning
            ConfigSwitchRow(
                label = "冻结应用",
                checked = freeze,
                onCheckedChange = { freeze = it },
            )
            if (freeze) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(
                        text = "冻结后应用将无法自行启动，请谨慎使用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 强制屏幕方向 (screenOrientation)
            OrientationSelector(
                selectedOrientation = screenOrientation,
                onOrientationSelected = { screenOrientation = it },
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 显示监视器 (showMonitor)
            ConfigSwitchRow(
                label = "显示监视器",
                checked = showMonitor,
                onCheckedChange = { showMonitor = it },
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Save button
            Button(
                onClick = {
                    onSave(
                        AppConfigEntity(
                            packageName = item.packageName,
                            aloneLight = aloneLight,
                            aloneLightValue = aloneLightValue.toInt(),
                            disNotice = disNotice,
                            disButton = disButton,
                            gpsOn = gpsOn,
                            freeze = freeze,
                            screenOrientation = screenOrientation,
                            showMonitor = showMonitor,
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "保存配置",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ConfigSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrientationSelector(
    selectedOrientation: Int,
    onOrientationSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = orientationOptions
        .firstOrNull { it.value == selectedOrientation }?.label ?: "自动"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "强制屏幕方向",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .width(120.dp)
                    .menuAnchor(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                orientationOptions.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        onClick = {
                            onOrientationSelected(option.value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
