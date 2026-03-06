package com.cloudorz.monitor.core.data.datasource

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import com.cloudorz.monitor.core.model.network.NetworkInfo
import com.cloudorz.monitor.core.model.network.WifiDetail
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Suppress("DEPRECATION")
    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var lastRxBytes: Long = 0L
    private var lastTxBytes: Long = 0L
    private var lastTimestamp: Long = 0L

    suspend fun getNetworkInfo(): NetworkInfo = withContext(Dispatchers.IO) {
        val network = connectivityManager.activeNetwork
        val caps = network?.let { connectivityManager.getNetworkCapabilities(it) }
        val isConnected = caps != null

        val networkType = when {
            caps == null -> "Disconnected"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Other"
        }

        val totalRx = TrafficStats.getTotalRxBytes()
        val totalTx = TrafficStats.getTotalTxBytes()
        val now = System.currentTimeMillis()

        var rxSpeed = 0L
        var txSpeed = 0L
        if (lastTimestamp > 0 && now > lastTimestamp) {
            val elapsed = (now - lastTimestamp) / 1000.0
            if (elapsed > 0) {
                rxSpeed = ((totalRx - lastRxBytes) / elapsed).toLong().coerceAtLeast(0)
                txSpeed = ((totalTx - lastTxBytes) / elapsed).toLong().coerceAtLeast(0)
            }
        }
        lastRxBytes = totalRx
        lastTxBytes = totalTx
        lastTimestamp = now

        val wifi = if (networkType == "WiFi") getWifiDetail() else null

        NetworkInfo(
            isConnected = isConnected,
            networkType = networkType,
            wifiInfo = wifi,
            totalRxBytes = totalRx,
            totalTxBytes = totalTx,
            rxSpeedBytesPerSec = rxSpeed,
            txSpeedBytesPerSec = txSpeed,
        )
    }

    @Suppress("DEPRECATION")
    private fun getWifiDetail(): WifiDetail? {
        return try {
            val info = wifiManager.connectionInfo ?: return null
            WifiDetail(
                ssid = info.ssid?.removeSurrounding("\"") ?: "",
                bssid = info.bssid ?: "",
                rssi = info.rssi,
                linkSpeedMbps = info.linkSpeed,
                frequencyMHz = info.frequency,
                ipAddress = Formatter.formatIpAddress(info.ipAddress),
            )
        } catch (e: SecurityException) {
            Log.w("NetworkDataSource", "ACCESS_WIFI_STATE permission denied", e)
            null
        }
    }
}
