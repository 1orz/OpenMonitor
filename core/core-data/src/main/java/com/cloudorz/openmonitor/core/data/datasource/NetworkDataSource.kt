package com.cloudorz.openmonitor.core.data.datasource

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.cloudorz.openmonitor.core.model.network.ConnectionInfo
import com.cloudorz.openmonitor.core.model.network.NetworkInfo
import com.cloudorz.openmonitor.core.model.network.WifiDetail
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
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
        val activeNetwork = connectivityManager.activeNetwork
        val activeCaps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val isConnected = activeCaps != null

        // Primary network type (for backward compat)
        val networkType = getTransportType(activeCaps)

        // Enumerate all connected networks
        val connections = buildActiveConnections()

        // Traffic stats
        val totalRx = TrafficStats.getTotalRxBytes()
        val totalTx = TrafficStats.getTotalTxBytes()
        val now = System.currentTimeMillis()

        var rxSpeed = 0L
        var txSpeed = 0L
        if (lastTimestamp in 1..<now) {
            val elapsed = (now - lastTimestamp) / 1000.0
            if (elapsed > 0) {
                rxSpeed = ((totalRx - lastRxBytes) / elapsed).toLong().coerceAtLeast(0)
                txSpeed = ((totalTx - lastTxBytes) / elapsed).toLong().coerceAtLeast(0)
            }
        }
        lastRxBytes = totalRx
        lastTxBytes = totalTx
        lastTimestamp = now

        // WiFi detail (only when WiFi is one of the active connections)
        val hasWifi = connections.any { it.type == "WiFi" }
        val wifi = if (hasWifi) getWifiDetail() else null

        NetworkInfo(
            isConnected = isConnected,
            networkType = networkType,
            activeConnections = connections,
            wifiInfo = wifi,
            totalRxBytes = totalRx,
            totalTxBytes = totalTx,
            rxSpeedBytesPerSec = rxSpeed,
            txSpeedBytesPerSec = txSpeed,
        )
    }

    private fun buildActiveConnections(): List<ConnectionInfo> {
        // Aggregate IP addresses by transport type to handle CLAT (464XLAT):
        // Real cellular interface (rmnet_data*) has IPv6 only,
        // CLAT virtual interface (v4-rmnet_data*) has the IPv4 — both report as "Cellular".
        data class AggEntry(
            val type: String,
            val isVpn: Boolean,
            val ipv4: MutableList<String> = mutableListOf(),
            val ipv6: MutableList<String> = mutableListOf(),
            val interfaces: MutableList<String> = mutableListOf(),
        )

        val map = linkedMapOf<String, AggEntry>()

        try {
            @Suppress("DEPRECATION")
            val allNetworks = connectivityManager.allNetworks
            for (network in allNetworks) {
                val caps = connectivityManager.getNetworkCapabilities(network) ?: continue
                val linkProps = connectivityManager.getLinkProperties(network)
                val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                val type = getTransportType(caps)
                val key = if (isVpn) "VPN" else type

                val entry = map.getOrPut(key) { AggEntry(type = type, isVpn = isVpn) }

                val ifName = linkProps?.interfaceName ?: ""
                if (ifName.isNotEmpty() && ifName !in entry.interfaces) {
                    entry.interfaces.add(ifName)
                }

                // Collect addresses from LinkProperties
                linkProps?.linkAddresses?.forEach { la ->
                    when (la.address) {
                        is Inet4Address -> {
                            val addr = la.address.hostAddress ?: ""
                            if (addr.isNotEmpty() && addr !in entry.ipv4) entry.ipv4.add(addr)
                        }
                        is Inet6Address -> {
                            val addr = la.address.hostAddress ?: ""
                            if (addr.isNotEmpty() && !addr.startsWith("fe80") && addr !in entry.ipv6) {
                                entry.ipv6.add(addr)
                            }
                        }
                    }
                }
                // CLAT (464XLAT): IPv4 is on a virtual interface (v4-rmnet_data*)
                // that doesn't appear in LinkProperties. Check NetworkInterface directly.
                if (ifName.isNotEmpty() && entry.ipv4.isEmpty()) {
                    collectClatIpv4(ifName, entry.ipv4)
                }
            }
        } catch (e: Exception) {
            Log.w("NetworkDataSource", "Failed to enumerate networks", e)
        }

        return map.values.map { entry ->
            ConnectionInfo(
                type = entry.type,
                ipv4Addresses = entry.ipv4,
                ipv6Addresses = entry.ipv6,
                interfaceName = entry.interfaces.joinToString(", "),
                isVpn = entry.isVpn,
            )
        }
    }

    /**
     * Look up CLAT (464XLAT) IPv4 address from v4-<ifName> network interface.
     * On IPv6-only carriers, Android creates a virtual v4-rmnet_data* interface with a translated IPv4.
     */
    private fun collectClatIpv4(ifName: String, target: MutableList<String>) {
        try {
            // CLAT interface is named "v4-<base_interface>"
            val clatIf = NetworkInterface.getByName("v4-$ifName") ?: return
            for (addr in clatIf.inetAddresses) {
                if (addr is Inet4Address) {
                    val ip = addr.hostAddress ?: continue
                    if (ip.isNotEmpty() && ip !in target) target.add(ip)
                }
            }
        } catch (_: Exception) { }
    }

    private fun getTransportType(caps: NetworkCapabilities?): String = when {
        caps == null -> "Disconnected"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
        else -> "Other"
    }

    @Suppress("DEPRECATION")
    private fun getWifiDetail(): WifiDetail? {
        return try {
            val info = wifiManager.connectionInfo ?: return null

            val rawSsid = info.ssid?.removeSurrounding("\"") ?: ""
            val rawBssid = info.bssid ?: ""

            // Detect fake values returned when location permission is missing
            val isFakeSsid = rawSsid.isEmpty() || rawSsid == "<unknown ssid>" || rawSsid == "0x"
            val isFakeBssid = rawBssid == "02:00:00:00:00:00" || rawBssid.isEmpty()

            val hasLocationPerm = hasLocationPermission()

            // Get IP from LinkProperties for more reliable result
            val ipFromLinkProps = getWifiIpFromLinkProperties()

            @Suppress("DEPRECATION")
            val legacyIp = if (info.ipAddress != 0) {
                android.text.format.Formatter.formatIpAddress(info.ipAddress)
            } else ""

            WifiDetail(
                ssid = if (isFakeSsid) "" else rawSsid,
                bssid = if (isFakeBssid) "" else rawBssid,
                rssi = info.rssi,
                linkSpeedMbps = info.linkSpeed,
                frequencyMHz = info.frequency,
                ipAddress = ipFromLinkProps.ifEmpty { legacyIp },
                needsLocationPermission = isFakeSsid && !hasLocationPerm,
            )
        } catch (e: SecurityException) {
            Log.w("NetworkDataSource", "ACCESS_WIFI_STATE permission denied", e)
            null
        }
    }

    private fun getWifiIpFromLinkProperties(): String {
        return try {
            val activeNetwork = connectivityManager.activeNetwork ?: return ""
            val linkProps = connectivityManager.getLinkProperties(activeNetwork) ?: return ""
            linkProps.linkAddresses
                .firstOrNull { it.address is Inet4Address }
                ?.address?.hostAddress ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun hasLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: NEARBY_WIFI_DEVICES or FINE_LOCATION
            ContextCompat.checkSelfPermission(context, "android.permission.NEARBY_WIFI_DEVICES") ==
                PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }
}
