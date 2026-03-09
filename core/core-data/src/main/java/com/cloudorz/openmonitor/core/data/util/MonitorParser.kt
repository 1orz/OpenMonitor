package com.cloudorz.openmonitor.core.data.util

/**
 * Pure parsing functions for monitor data. Extracted for testability.
 */
internal object MonitorParser {

    fun parseCpuLoad(procStatOutput: String, prevValues: LongArray?): Pair<Double, LongArray?> {
        if (procStatOutput.isEmpty()) return 0.0 to prevValues

        val cpuLine = procStatOutput.lines().firstOrNull { it.startsWith("cpu ") }
            ?: return 0.0 to prevValues
        val values = cpuLine.trim().split("\\s+".toRegex())
            .drop(1)
            .map { it.toLongOrNull() ?: 0L }
            .toLongArray()

        if (values.size < 5) return 0.0 to prevValues
        if (prevValues == null || prevValues.size < 5) return 0.0 to values

        val idle = values[3] + values[4]
        val prevIdle = prevValues[3] + prevValues[4]
        val total = values.sum()
        val prevTotal = prevValues.sum()
        val totalDiff = (total - prevTotal).toDouble()
        val idleDiff = (idle - prevIdle).toDouble()

        val load = if (totalDiff > 0) {
            ((totalDiff - idleDiff) / totalDiff * 100.0).coerceIn(0.0, 100.0)
        } else 0.0

        return load to values
    }

    fun parseGpuLoad(gpuOutput: String): Double {
        if (gpuOutput.isEmpty()) return 0.0
        return gpuOutput.replace("%", "").trim().toDoubleOrNull() ?: 0.0
    }

    fun parseThermal(thermalOutput: String): Double {
        if (thermalOutput.isEmpty()) return 0.0
        val raw = thermalOutput.trim().toIntOrNull() ?: return 0.0
        return if (raw > 1000) raw / 1000.0 else raw.toDouble()
    }

    fun parseBatteryCurrentFromSysfs(batteryOutput: String): Int? {
        if (batteryOutput.isEmpty()) return null
        val raw = batteryOutput.trim().toLongOrNull() ?: return null
        return normalizeCurrentToMa(raw)
    }

    /**
     * Parses battery current from uevent file content.
     * Looks for POWER_SUPPLY_CURRENT_NOW= line.
     */
    fun parseBatteryCurrentFromUevent(ueventContent: String): Int? {
        if (ueventContent.isEmpty()) return null
        for (line in ueventContent.lines()) {
            if (line.startsWith("POWER_SUPPLY_CURRENT_NOW=")) {
                val raw = line.substringAfter("=").trim().toLongOrNull() ?: continue
                return normalizeCurrentToMa(raw)
            }
        }
        return null
    }

    /**
     * Normalizes raw current value to milliamps.
     * Most sysfs paths report in µA; values >= 10000 are assumed µA.
     */
    fun normalizeCurrentToMa(rawValue: Long): Int {
        val abs = kotlin.math.abs(rawValue)
        return if (abs >= 10000) (rawValue / 1000).toInt() else rawValue.toInt()
    }

    /**
     * Ensures battery current sign matches charging state.
     * Many OEMs (Xiaomi, etc.) return positive values regardless of direction.
     * Convention: positive = charging, negative = discharging.
     */
    fun ensureCurrentSign(currentMa: Int, isCharging: Boolean): Int {
        if (currentMa == 0) return 0
        return if (isCharging) kotlin.math.abs(currentMa) else -kotlin.math.abs(currentMa)
    }
}
