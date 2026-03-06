package com.cloudorz.monitor.core.model.sensor

data class SensorInfo(
    val name: String = "",
    val type: Int = 0,
    val typeName: String = "",
    val vendor: String = "",
    val version: Int = 0,
    val resolution: Float = 0f,
    val maxRange: Float = 0f,
    val power: Float = 0f,
    val minDelay: Int = 0,
    val values: FloatArray = floatArrayOf(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SensorInfo) return false
        return name == other.name && type == other.type
    }

    override fun hashCode(): Int = 31 * name.hashCode() + type
}
