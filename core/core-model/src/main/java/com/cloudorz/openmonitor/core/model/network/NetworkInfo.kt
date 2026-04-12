package com.cloudorz.openmonitor.core.model.network

data class NetworkInfo(
    val isConnected: Boolean = false,
    val networkType: String = "Unknown",
    val activeConnections: List<ConnectionInfo> = emptyList(),
    val wifiInfo: WifiDetail? = null,
    val totalRxBytes: Long = 0L,
    val totalTxBytes: Long = 0L,
    val rxSpeedBytesPerSec: Long = 0L,
    val txSpeedBytesPerSec: Long = 0L,
)

data class ConnectionInfo(
    val type: String,
    val ipv4Addresses: List<String> = emptyList(),
    val ipv6Addresses: List<String> = emptyList(),
    val interfaceName: String = "",
    val isVpn: Boolean = false,
)

data class WifiDetail(
    val ssid: String = "",
    val bssid: String = "",
    val rssi: Int = 0,
    val linkSpeedMbps: Int = 0,
    val frequencyMHz: Int = 0,
    val ipAddress: String = "",
    val needsLocationPermission: Boolean = false,
) {
    val signalLevel: Int
        get() = when {
            rssi >= -50 -> 4
            rssi >= -60 -> 3
            rssi >= -70 -> 2
            rssi >= -80 -> 1
            else -> 0
        }
}
