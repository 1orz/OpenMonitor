package com.cloudorz.openmonitor.core.data.datasource

import android.app.Application
import android.os.Build
import com.cloudorz.openmonitor.core.model.cpu.SocInfo
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads socs.json from app assets and resolves SoC marketing info
 * uses contains() + longest-match to find the correct SoC entry.
 */
@Singleton
class SocDatabase @Inject constructor(
    private val application: Application,
    private val deviceNameSource: DeviceNameSource,
) {
    private val entries: Map<String, SocEntry> by lazy { loadDatabase() }

    @Volatile
    private var cachedSocInfo: SocInfo? = null

    fun getSocInfo(): SocInfo {
        cachedSocInfo?.let { return it }
        val info = resolve()
        cachedSocInfo = info
        return info
    }

    private fun resolve(): SocInfo {
        val identifiers = buildHardwareIdentifiers()
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: ""
        val architecture = when {
            abi.startsWith("arm64") -> "ARMv8"
            abi.startsWith("armeabi-v7a") -> "ARMv7"
            abi.startsWith("x86_64") -> "x86_64"
            abi.startsWith("x86") -> "x86"
            else -> ""
        }

        if (entries.isEmpty()) return SocInfo(
            hardwareId = identifiers.firstOrNull() ?: "",
            abi = abi,
            architecture = architecture,
        )

        val deviceName = deviceNameSource.getDeviceName()
        val deviceBrand = Build.BRAND.trim()

        for (id in identifiers) {
            val match = findBestMatch(id)
            if (match != null) {
                return match.toSocInfo(
                    hardwareId = id,
                    abi = abi,
                    architecture = architecture,
                    deviceMarketingName = deviceName,
                    deviceBrand = deviceBrand,
                )
            }
        }

        return SocInfo(
            hardwareId = identifiers.firstOrNull() ?: "",
            abi = abi,
            architecture = architecture,
            deviceMarketingName = deviceName,
            deviceBrand = deviceBrand,
        )
    }

    private fun buildHardwareIdentifiers(): List<String> {
        val ids = mutableListOf<String>()

        // 1. Build.SOC_MODEL (API 31+) — most specific, e.g. "SM8750"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val socModel = Build.SOC_MODEL
            if (socModel.isNotBlank() && socModel != "unknown") {
                ids.add(socModel)
            }
        }

        // 2. Build.HARDWARE — e.g. "walt", "qcom"
        val hw = Build.HARDWARE
        if (hw.isNotBlank() && hw != "unknown" && hw !in ids) {
            ids.add(hw)
        }

        // 3. Build.BOARD — e.g. "walt", often same as HARDWARE
        val board = Build.BOARD
        if (board.isNotBlank() && board != "unknown" && board !in ids) {
            ids.add(board)
        }

        return ids
    }

    /**
     * Finds the best matching SoC entry for the given identifier.
     * pick the longest key match (to avoid "SM" matching when "SM8750" exists).
     */
    private fun findBestMatch(identifier: String): SocEntry? {
        var bestMatch: SocEntry? = null
        var bestKeyLength = 0

        for ((key, entry) in entries) {
            if (identifier.contains(key, ignoreCase = true) && key.length > bestKeyLength) {
                bestMatch = entry
                bestKeyLength = key.length
            }
        }

        return bestMatch
    }

    fun getVendor(): String {
        // Prefer SoC database vendor
        val info = getSocInfo()
        if (info.vendor.isNotBlank()) return info.vendor

        // Fallback: Build.SOC_MANUFACTURER (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mfr = Build.SOC_MANUFACTURER
            if (mfr.isNotBlank() && mfr != "unknown") return mfr
        }

        val hw = (getSocInfo().hardwareId + " " + Build.HARDWARE + " " + Build.BOARD).lowercase()
        return when {
            hw.contains("qualcomm") || hw.contains("qti") || hw.contains("msm") ||
                hw.contains("sdm") || hw.contains("qcom") -> "Qualcomm"
            hw.contains("sm") && hw.matches(Regex(".*\\bsm\\d{4}.*")) -> "Qualcomm"
            hw.contains("mediatek") || hw.contains("mtk") -> "MediaTek"
            hw.matches(Regex(".*\\bmt\\d{4}.*")) -> "MediaTek"
            hw.contains("exynos") || hw.contains("universal") || hw.contains("s5e") -> "Samsung"
            hw.contains("kirin") || hw.contains("hisilicon") -> "HiSilicon"
            hw.contains("tensor") || hw.contains("google") -> "Google"
            hw.contains("unisoc") -> "Unisoc"
            hw.contains("rockchip") || hw.contains("rk") -> "Rockchip"
            else -> ""
        }
    }

    private fun loadDatabase(): Map<String, SocEntry> {
        return try {
            val json = application.assets.open("socs.json").bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val map = mutableMapOf<String, SocEntry>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = root.getJSONObject(key)
                map[key] = SocEntry(
                    vendor = obj.optString("VENDOR", ""),
                    name = obj.optString("NAME", ""),
                    fab = obj.optString("FAB", ""),
                    cpu = obj.optString("CPU", ""),
                    memory = obj.optString("MEMORY", ""),
                    bandwidth = obj.optString("BANDWIDTH", ""),
                    channels = obj.optString("CHANNELS", ""),
                )
            }
            map
        } catch (e: Exception) {
            android.util.Log.w("SocDatabase", "Failed to load socs.json", e)
            emptyMap()
        }
    }

    private data class SocEntry(
        val vendor: String,
        val name: String,
        val fab: String,
        val cpu: String,
        val memory: String,
        val bandwidth: String,
        val channels: String,
    ) {
        fun toSocInfo(
            hardwareId: String,
            abi: String = "",
            architecture: String = "",
            deviceMarketingName: String? = null,
            deviceBrand: String = "",
        ) = SocInfo(
            vendor = vendor,
            name = name,
            fab = fab,
            cpuDescription = cpu,
            memoryType = memory,
            bandwidth = bandwidth,
            channels = channels,
            hardwareId = hardwareId,
            abi = abi,
            architecture = architecture,
            deviceMarketingName = deviceMarketingName,
            deviceBrand = deviceBrand,
        )
    }
}
