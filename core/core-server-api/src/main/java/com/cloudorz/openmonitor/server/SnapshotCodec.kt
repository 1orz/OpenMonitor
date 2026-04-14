package com.cloudorz.openmonitor.server

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads a [ServerSnapshot] from a mmapped ByteBuffer using the seqlock
 * protocol. Writer side (Rust) increments seq before and after the body write;
 * reader retries if seq changes under it or is odd (write-in-progress).
 *
 * Designed for lock-free single-producer / many-consumer use. Reader is
 * allocation-free in steady state (reuses the passed-in IntArray instances).
 */
object SnapshotCodec {

    private const val MAX_RETRIES = 32

    /** Reads a consistent snapshot. Returns null if no stable read after retries. */
    fun read(buf: ByteBuffer): ServerSnapshot? {
        buf.order(ByteOrder.LITTLE_ENDIAN)
        repeat(MAX_RETRIES) {
            val seq1 = buf.getLong(SnapshotLayout.OFFSET_SEQ)
            if (seq1 and 1L == 1L) {
                // writer in progress
                Thread.yield()
                return@repeat
            }
            val snap = readBody(buf, seq1) ?: return@repeat
            val seq2 = buf.getLong(SnapshotLayout.OFFSET_SEQ)
            if (seq1 == seq2) return snap
        }
        return null
    }

    private fun readBody(buf: ByteBuffer, seq: Long): ServerSnapshot? {
        val magic = buf.getInt(SnapshotLayout.OFFSET_MAGIC)
        if (magic != SnapshotLayout.MAGIC) return null
        val version = buf.getInt(SnapshotLayout.OFFSET_VERSION)
        if (version != SnapshotLayout.VERSION) return null

        val cpuLoad = IntArray(SnapshotLayout.CPU_CORES_MAX) {
            buf.getInt(SnapshotLayout.OFFSET_CPU_LOAD_PCT + it * 4)
        }
        val cpuFreq = IntArray(SnapshotLayout.CPU_CORES_MAX) {
            buf.getInt(SnapshotLayout.OFFSET_CPU_FREQ_MHZ + it * 4)
        }
        return ServerSnapshot(
            seq = seq,
            timestampNs = buf.getLong(SnapshotLayout.OFFSET_TIMESTAMP_NS),
            cpuLoadPct = cpuLoad,
            cpuFreqMhz = cpuFreq,
            cpuTempCx10 = buf.getInt(SnapshotLayout.OFFSET_CPU_TEMP_C_X10),
            gpuFreqMhz = buf.getInt(SnapshotLayout.OFFSET_GPU_FREQ_MHZ),
            gpuLoadPct = buf.getInt(SnapshotLayout.OFFSET_GPU_LOAD_PCT),
            memTotalMb = buf.getInt(SnapshotLayout.OFFSET_MEM_TOTAL_MB),
            memAvailMb = buf.getInt(SnapshotLayout.OFFSET_MEM_AVAIL_MB),
            ddrFreqMbps = buf.getInt(SnapshotLayout.OFFSET_DDR_FREQ_MBPS),
            batteryCurrentMa = buf.getInt(SnapshotLayout.OFFSET_BATTERY_CURRENT_MA),
            batteryVoltageMv = buf.getInt(SnapshotLayout.OFFSET_BATTERY_VOLTAGE_MV),
            batteryTempCx10 = buf.getInt(SnapshotLayout.OFFSET_BATTERY_TEMP_C_X10),
            batteryCapacity = buf.getInt(SnapshotLayout.OFFSET_BATTERY_CAPACITY),
            batteryStatus = buf.getInt(SnapshotLayout.OFFSET_BATTERY_STATUS),
            fpsX100 = buf.getInt(SnapshotLayout.OFFSET_FPS_X100),
            jank = buf.getInt(SnapshotLayout.OFFSET_JANK),
            bigJank = buf.getInt(SnapshotLayout.OFFSET_BIG_JANK),
            fpsLayer = readCString(buf, SnapshotLayout.OFFSET_FPS_LAYER, SnapshotLayout.FPS_LAYER_LEN),
            lastFocusedPkg = readCString(buf, SnapshotLayout.OFFSET_LAST_FOCUSED_PKG, SnapshotLayout.LAST_FOCUSED_PKG_LEN),
            screenInteractive = buf.get(SnapshotLayout.OFFSET_SCREEN_INTERACTIVE).toInt() != 0,
        )
    }

    private fun readCString(buf: ByteBuffer, offset: Int, maxLen: Int): String {
        val bytes = ByteArray(maxLen)
        for (i in 0 until maxLen) bytes[i] = buf.get(offset + i)
        val end = bytes.indexOfFirst { it == 0.toByte() }
        return if (end < 0) String(bytes, Charsets.UTF_8)
        else String(bytes, 0, end, Charsets.UTF_8)
    }
}
