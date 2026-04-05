package com.cloudorz.openmonitor.ui.network

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudorz.openmonitor.R
import com.cloudorz.openmonitor.core.model.network.NetworkInfo
import com.cloudorz.openmonitor.core.model.network.WifiDetail

@Composable
fun NetworkScreen(
    viewModel: NetworkViewModel = hiltViewModel(),
) {
    val networkInfo by viewModel.networkInfo.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ConnectionStatusCard(networkInfo = networkInfo)
        }

        item {
            SpeedCard(networkInfo = networkInfo)
        }

        item {
            TrafficCard(networkInfo = networkInfo)
        }

        networkInfo.wifiInfo?.let { wifi ->
            item {
                WifiDetailCard(wifi = wifi)
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(networkInfo: NetworkInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (networkInfo.isConnected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (networkInfo.isConnected) Icons.Outlined.Wifi else Icons.Outlined.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (networkInfo.isConnected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = if (networkInfo.isConnected)
                        stringResource(R.string.network_connected)
                    else
                        stringResource(R.string.network_disconnected),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (networkInfo.isConnected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = networkInfo.networkType,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (networkInfo.isConnected)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun SpeedCard(networkInfo: NetworkInfo) {
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
                text = stringResource(R.string.realtime_speed),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SpeedItem(
                    label = stringResource(R.string.download),
                    speed = networkInfo.rxSpeedBytesPerSec,
                    color = Color(0xFF4CAF50),
                    icon = Icons.Outlined.ArrowDownward,
                )
                SpeedItem(
                    label = stringResource(R.string.upload),
                    speed = networkInfo.txSpeedBytesPerSec,
                    color = Color(0xFF2196F3),
                    icon = Icons.Outlined.ArrowUpward,
                )
            }
        }
    }
}

@Composable
private fun SpeedItem(
    label: String,
    speed: Long,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = color,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatSpeed(speed),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrafficCard(networkInfo: NetworkInfo) {
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
                text = stringResource(R.string.total_traffic),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TrafficItem(
                    label = stringResource(R.string.total_received),
                    bytes = networkInfo.totalRxBytes,
                )
                TrafficItem(
                    label = stringResource(R.string.total_sent),
                    bytes = networkInfo.totalTxBytes,
                )
            }
        }
    }
}

@Composable
private fun TrafficItem(label: String, bytes: Long) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatTrafficBytes(bytes),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun WifiDetailCard(wifi: WifiDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.SignalCellularAlt,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.wifi_details),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            WifiDetailRow(label = "SSID", value = wifi.ssid)
            WifiDetailRow(label = "BSSID", value = wifi.bssid)
            WifiDetailRow(label = stringResource(R.string.signal_strength), value = "${wifi.rssi} dBm (${wifi.signalLevel}/4)")
            WifiDetailRow(label = stringResource(R.string.link_speed), value = "${wifi.linkSpeedMbps} Mbps")
            WifiDetailRow(label = stringResource(R.string.frequency), value = "${wifi.frequencyMHz} MHz")
            WifiDetailRow(label = stringResource(R.string.ip_address), value = wifi.ipAddress)
        }
    }
}

@Composable
private fun WifiDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatSpeed(bytesPerSec: Long): String = when {
    bytesPerSec >= 1_000_000L -> "%.1f MB/s".format(bytesPerSec / 1_000_000.0)
    bytesPerSec >= 1_000L -> "%.0f KB/s".format(bytesPerSec / 1_000.0)
    else -> "$bytesPerSec B/s"
}

private fun formatTrafficBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> "%.2f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
