package com.cloudorz.monitor.ui.features

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cloudorz.monitor.ui.navigation.FeatureRoute

data class FeatureItem(
    val title: String,
    val icon: ImageVector,
    val route: String,
)

@Composable
fun FeaturesScreen(
    onFeatureClick: (String) -> Unit,
) {
    val features = listOf(
        FeatureItem("CPU 信息", Icons.Outlined.Tune, FeatureRoute.CPU),
        FeatureItem("耗电统计", Icons.Outlined.ElectricBolt, FeatureRoute.POWER),
        FeatureItem("充电统计", Icons.Outlined.BatteryChargingFull, FeatureRoute.CHARGE),
        FeatureItem("帧率记录", Icons.Outlined.Speed, FeatureRoute.FPS),
        FeatureItem("进程监控", Icons.Outlined.Memory, FeatureRoute.PROCESS),
        FeatureItem("悬浮监视器", Icons.Outlined.Layers, FeatureRoute.FLOAT),
        FeatureItem("存储信息", Icons.Outlined.Storage, FeatureRoute.STORAGE),
        FeatureItem("传感器", Icons.Outlined.Sensors, FeatureRoute.SENSOR),
        FeatureItem("网络监控", Icons.Outlined.NetworkCheck, FeatureRoute.NETWORK),
        FeatureItem("调试日志", Icons.Outlined.BugReport, FeatureRoute.LOG),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(features, key = { it.route }) { feature ->
            FeatureRow(
                feature = feature,
                onClick = { onFeatureClick(feature.route) },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        }
    }
}

@Composable
private fun FeatureRow(
    feature: FeatureItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = feature.icon,
            contentDescription = feature.title,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = feature.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
