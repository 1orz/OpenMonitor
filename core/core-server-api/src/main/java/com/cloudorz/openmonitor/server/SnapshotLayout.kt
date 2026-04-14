package com.cloudorz.openmonitor.server

/**
 * Binary layout of the Snapshot struct written by the Rust server into the
 * shared ashmem region and read by the Kotlin client.
 *
 * ***MUST stay in sync with server-rs/src/shm.rs (#[repr(C)] Snapshot).***
 *
 * Layout (little-endian, packed, 4 KB page aligned):
 *
 *   u32 magic      = 0x4F4D4E54 ("OMNT")
 *   u32 version    = [VERSION]
 *   u64 seq        - seqlock: odd = writing, even = stable
 *   u64 timestamp_ns
 *   i32 cpu_load_pct[16]      (-1 if absent)
 *   i32 cpu_freq_mhz[16]
 *   i32 cpu_temp_c_x10        (°C × 10, -1 unknown)
 *   i32 gpu_freq_mhz
 *   i32 gpu_load_pct
 *   i32 mem_total_mb
 *   i32 mem_avail_mb
 *   i32 ddr_freq_mbps
 *   i32 battery_current_ma
 *   i32 battery_voltage_mv
 *   i32 battery_temp_c_x10
 *   i32 battery_capacity
 *   i32 battery_status        (BatteryManager.BATTERY_STATUS_*)
 *   i32 fps_x100              (fps × 100, -1 unknown)
 *   i32 jank
 *   i32 big_jank
 *   u8  fps_layer[64]         null-terminated UTF-8
 *   u8  last_focused_pkg[128] null-terminated UTF-8
 *   u8  screen_interactive    (0 | 1)
 *   u8  _pad[...]             to SIZE_BYTES
 */
object SnapshotLayout {
    const val MAGIC: Int = 0x4F4D4E54  // 'OMNT' (little-endian on disk)
    const val VERSION: Int = 1

    const val SIZE_BYTES: Int = 4096  // one page

    // --- Offsets (matches Rust #[repr(C)] — hand-computed, asserted by tests) ---
    const val OFFSET_MAGIC: Int = 0
    const val OFFSET_VERSION: Int = 4
    const val OFFSET_SEQ: Int = 8
    const val OFFSET_TIMESTAMP_NS: Int = 16

    const val OFFSET_CPU_LOAD_PCT: Int = 24              // i32 × 16
    const val OFFSET_CPU_FREQ_MHZ: Int = 24 + 16 * 4     // 88
    const val OFFSET_CPU_TEMP_C_X10: Int = 88 + 16 * 4   // 152
    const val OFFSET_GPU_FREQ_MHZ: Int = 156
    const val OFFSET_GPU_LOAD_PCT: Int = 160
    const val OFFSET_MEM_TOTAL_MB: Int = 164
    const val OFFSET_MEM_AVAIL_MB: Int = 168
    const val OFFSET_DDR_FREQ_MBPS: Int = 172
    const val OFFSET_BATTERY_CURRENT_MA: Int = 176
    const val OFFSET_BATTERY_VOLTAGE_MV: Int = 180
    const val OFFSET_BATTERY_TEMP_C_X10: Int = 184
    const val OFFSET_BATTERY_CAPACITY: Int = 188
    const val OFFSET_BATTERY_STATUS: Int = 192
    const val OFFSET_FPS_X100: Int = 196
    const val OFFSET_JANK: Int = 200
    const val OFFSET_BIG_JANK: Int = 204

    const val FPS_LAYER_LEN: Int = 64
    const val LAST_FOCUSED_PKG_LEN: Int = 128

    const val OFFSET_FPS_LAYER: Int = 208
    const val OFFSET_LAST_FOCUSED_PKG: Int = OFFSET_FPS_LAYER + FPS_LAYER_LEN // 272
    const val OFFSET_SCREEN_INTERACTIVE: Int = OFFSET_LAST_FOCUSED_PKG + LAST_FOCUSED_PKG_LEN // 400

    const val CPU_CORES_MAX: Int = 16
}

/** Plain-old-data snapshot read from shared memory. */
data class ServerSnapshot(
    val seq: Long,
    val timestampNs: Long,
    val cpuLoadPct: IntArray,       // [16], -1 = absent
    val cpuFreqMhz: IntArray,       // [16], -1 = absent
    val cpuTempCx10: Int,
    val gpuFreqMhz: Int,
    val gpuLoadPct: Int,
    val memTotalMb: Int,
    val memAvailMb: Int,
    val ddrFreqMbps: Int,
    val batteryCurrentMa: Int,
    val batteryVoltageMv: Int,
    val batteryTempCx10: Int,
    val batteryCapacity: Int,
    val batteryStatus: Int,
    val fpsX100: Int,
    val jank: Int,
    val bigJank: Int,
    val fpsLayer: String,
    val lastFocusedPkg: String,
    val screenInteractive: Boolean,
) {
    val cpuTempC: Float get() = if (cpuTempCx10 < 0) Float.NaN else cpuTempCx10 / 10f
    val batteryTempC: Float get() = if (batteryTempCx10 < 0) Float.NaN else batteryTempCx10 / 10f
    val fps: Float get() = if (fpsX100 < 0) Float.NaN else fpsX100 / 100f

    override fun equals(other: Any?): Boolean {
        // Generated equals skipped because data class with IntArray fields
        // — downstream uses distinctUntilChanged on derived projections.
        return super.equals(other)
    }

    override fun hashCode(): Int = seq.hashCode()
}
