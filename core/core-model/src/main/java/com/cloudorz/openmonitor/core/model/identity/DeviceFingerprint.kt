package com.cloudorz.openmonitor.core.model.identity

import org.json.JSONObject

/**
 * 设备指纹数据，包含多维度的设备标识信息。
 * 所有字段设计为 nullable，采集不到时为 null。
 */
data class DeviceFingerprint(
    // 主标识符 — MediaDrm Widevine ID 的 SHA-256 哈希（恢复出厂不变）
    val mediaDrmId: String? = null,
    // 主板序列号（需要 ADB/Root 权限，普通权限可能为空）
    val serialNo: String? = null,

    // Build 属性（零权限）
    val model: String = "",
    val board: String = "",
    val hardware: String = "",
    val manufacturer: String = "",
    val brand: String = "",
    val device: String = "",
    val product: String = "",
    val socModel: String = "",
    val socManufacturer: String = "",

    // 硬件特征（零权限）
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val screenDensity: Int = 0,
    val totalRam: Long = 0L,
    val sensorHash: String = "",

    // 运行环境
    val sdkInt: Int = 0,
    val privilegeMode: String = "",
) {
    /**
     * 主标识：优先使用 MediaDrm ID，其次使用硬件组合哈希。
     */
    fun primaryId(): String = mediaDrmId ?: hardwareHash()

    /**
     * 基于 Build 属性 + 硬件特征的组合哈希（不含 MediaDrm ID）。
     * 同型号设备可能相同，仅作辅助匹配。
     */
    fun hardwareHash(): String {
        val raw = buildString {
            append(model); append(board); append(hardware)
            append(manufacturer); append(brand); append(device)
            append(product); append(socModel); append(socManufacturer)
            append(screenWidth); append(screenHeight); append(screenDensity)
            append(totalRam); append(sensorHash)
        }
        return sha256(raw.toByteArray())
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("media_drm_id", mediaDrmId ?: JSONObject.NULL)
        put("serial_no", serialNo ?: JSONObject.NULL)
        put("model", model)
        put("board", board)
        put("hardware", hardware)
        put("manufacturer", manufacturer)
        put("brand", brand)
        put("device", device)
        put("product", product)
        put("soc_model", socModel)
        put("soc_manufacturer", socManufacturer)
        put("screen_width", screenWidth)
        put("screen_height", screenHeight)
        put("screen_density", screenDensity)
        put("total_ram", totalRam)
        put("sensor_hash", sensorHash)
        put("sdk_int", sdkInt)
        put("privilege_mode", privilegeMode)
        put("primary_id", primaryId())
        put("hardware_hash", hardwareHash())
    }

    companion object {
        internal fun sha256(data: ByteArray): String {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            return md.digest(data).joinToString("") { "%02x".format(it) }
        }
    }
}
