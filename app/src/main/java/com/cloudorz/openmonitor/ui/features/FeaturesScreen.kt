package com.cloudorz.openmonitor.ui.features

import com.cloudorz.openmonitor.core.ui.hapticClickable
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
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cloudorz.openmonitor.R
import com.cloudorz.openmonitor.ui.navigation.Route

data class FeatureItem(
    val title: String,
    val icon: ImageVector,
    val route: Route,
)

@Composable
fun FeaturesScreen(
    onFeatureClick: (Route) -> Unit,
) {
    val features = listOf(
        FeatureItem(stringResource(R.string.feature_hardware_info), Icons.Outlined.PhoneAndroid, Route.Hardware),
        FeatureItem(stringResource(R.string.feature_battery), Icons.Outlined.BatteryStd, Route.Battery),
        FeatureItem(stringResource(R.string.feature_fps_recording), Icons.Outlined.Speed, Route.Fps),
        FeatureItem(stringResource(R.string.feature_process_monitor), Icons.Outlined.Memory, Route.Process),
        FeatureItem(stringResource(R.string.feature_float_monitor), Icons.Outlined.Layers, Route.FloatMonitor),
        FeatureItem(stringResource(R.string.feature_sensor), Icons.Outlined.Sensors, Route.Sensor),
        FeatureItem(stringResource(R.string.feature_network_monitor), Icons.Outlined.SwapVert, Route.Network),
        FeatureItem(stringResource(R.string.feature_key_attestation), Icons.Outlined.VpnKey, Route.KeyAttestation),
        FeatureItem(stringResource(R.string.feature_debug_log), Icons.Outlined.BugReport, Route.Log),
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
            .hapticClickable(onClick = onClick)
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
