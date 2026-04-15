package com.cloudorz.openmonitor.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerSnapshot(
    @SerialName("ts_ns") val tsNs: Long = 0L,
    val cpu: CpuBlock = CpuBlock(),
    val gpu: GpuBlock = GpuBlock(),
    val mem: MemBlock = MemBlock(),
    val batt: BattBlock = BattBlock(),
    val fps: FpsBlock = FpsBlock(),
    val focus: FocusBlock = FocusBlock(),
) {
    // Derived helpers used by the UI — keep these so call sites don't
    // all need to recompute them.
    val cpuTempC: Float get() = if (cpu.tempX10 < 0) Float.NaN else cpu.tempX10 / 10f
    val batteryTempC: Float get() = if (batt.tempX10 < 0) Float.NaN else batt.tempX10 / 10f
    val fpsValue: Float get() = if (fps.x100 < 0) Float.NaN else fps.x100 / 100f
}

@Serializable
data class CpuBlock(
    val load: IntArray = IntArray(16) { -1 },
    val freq: IntArray = IntArray(16) { -1 },
    @SerialName("temp_x10") val tempX10: Int = -1,
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = tempX10
}

@Serializable
data class GpuBlock(
    val load: Int = -1,
    val freq: Int = -1,
)

@Serializable
data class MemBlock(
    @SerialName("total_mb") val totalMb: Int = -1,
    @SerialName("avail_mb") val availMb: Int = -1,
    @SerialName("ddr_mbps") val ddrMbps: Int = -1,
)

@Serializable
data class BattBlock(
    @SerialName("current_ma") val currentMa: Int = 0,
    @SerialName("voltage_mv") val voltageMv: Int = 0,
    @SerialName("temp_x10") val tempX10: Int = -1,
    val capacity: Int = -1,
    val status: Int = 1, // BATTERY_STATUS_UNKNOWN
)

@Serializable
data class FpsBlock(
    val x100: Int = -1,
    val jank: Int = 0,
    @SerialName("big_jank") val bigJank: Int = 0,
    val layer: String = "",
)

@Serializable
data class FocusBlock(
    val pkg: String = "",
    @SerialName("screen_on") val screenOn: Boolean = true,
)
