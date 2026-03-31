package com.cloudorz.openmonitor.core.data.util

/**
 * Decodes ARM MIDR (Main ID Register) implementer + part number to
 * a human-readable microarchitecture name.
 *
 * Ported from DevCheck's uu.o() method.
 */
object MidrDecoder {

    fun decode(implementer: Int, part: Int): String? = when (implementer) {
        0x41 -> arm(part)
        0x51 -> qualcomm(part)
        0x53 -> samsung(part)
        0x48 -> hisilicon(part)
        0x4E -> nvidia(part)
        else -> null
    }

    private fun arm(part: Int): String? = when (part) {
        0xC05 -> "Cortex-A5"
        0xC07 -> "Cortex-A7"
        0xC08 -> "Cortex-A8"
        0xC09 -> "Cortex-A9"
        0xC0C -> "Cortex-A12"
        0xC0E -> "Cortex-A17"
        0xC0F -> "Cortex-A15"
        0xD01 -> "Cortex-A32"
        0xD03 -> "Cortex-A53"
        0xD04 -> "Cortex-A35"
        0xD05 -> "Cortex-A55"
        0xD06 -> "Cortex-A65"
        0xD07 -> "Cortex-A57"
        0xD08 -> "Cortex-A72"
        0xD09 -> "Cortex-A73"
        0xD0A -> "Cortex-A75"
        0xD0B -> "Cortex-A76"
        0xD0C -> "Neoverse N1"
        0xD0D -> "Cortex-A77"
        0xD0E -> "Cortex-A76AE"
        0xD40 -> "Neoverse V1"
        0xD41 -> "Cortex-A78"
        0xD42 -> "Cortex-A78AE"
        0xD43 -> "Cortex-A65AE"
        0xD44 -> "Cortex-X1"
        0xD46 -> "Cortex-A510"
        0xD47 -> "Cortex-A710"
        0xD48 -> "Cortex-X2"
        0xD49 -> "Neoverse N2"
        0xD4A -> "Neoverse E1"
        0xD4B -> "Cortex-A78C"
        0xD4C -> "Cortex-X1C"
        0xD4D -> "Cortex-A715"
        0xD4E -> "Cortex-X3"
        0xD4F -> "Neoverse V2"
        0xD80 -> "Cortex-A520"
        0xD81 -> "Cortex-A720"
        0xD82 -> "Cortex-X4"
        0xD84 -> "Neoverse V3"
        0xD85 -> "Cortex-X925"
        0xD87 -> "Cortex-A725"
        0xD8E -> "Neoverse N3"
        else -> null
    }

    private fun qualcomm(part: Int): String? = when (part) {
        0x06F -> "Krait"
        0x201 -> "Kryo Gold"
        0x205 -> "Kryo Silver"
        0x211 -> "Kryo 2nd Gen"
        0x800 -> "Kryo 2xx Gold"
        0x801 -> "Kryo 2xx Silver"
        0x802 -> "Kryo 3xx Gold"
        0x803 -> "Kryo 3xx Silver"
        0x804 -> "Kryo 4xx Gold"
        0x805 -> "Kryo 4xx Silver"
        0x001 -> "Oryon"
        else -> null
    }

    private fun samsung(part: Int): String? = when (part) {
        0x001 -> "Exynos M1"
        0x002 -> "Exynos M3"
        0x003 -> "Exynos M4"
        0x004 -> "Exynos M5"
        else -> null
    }

    private fun hisilicon(part: Int): String? = when (part) {
        0xD01 -> "TaiShan v110"
        0xD02 -> "TaiShan V120"
        0xD40 -> "TaiShan V121"
        else -> null
    }

    private fun nvidia(part: Int): String? = when (part) {
        0x000 -> "Denver"
        0x003 -> "Denver 2"
        0x004 -> "Carmel"
        else -> null
    }

    /** Parse /proc/cpuinfo and return a map of coreIndex → microarch name. */
    fun parseProcCpuInfo(content: String): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        var currentCore = -1
        var implementer = -1
        var part = -1

        fun flush() {
            if (currentCore >= 0 && implementer >= 0 && part >= 0) {
                decode(implementer, part)?.let { result[currentCore] = it }
            }
        }

        for (line in content.lineSequence()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("processor") -> {
                    flush()
                    currentCore = trimmed.substringAfter(":").trim().toIntOrNull() ?: -1
                    implementer = -1
                    part = -1
                }
                trimmed.startsWith("CPU implementer") -> {
                    val hex = trimmed.substringAfter(":").trim()
                    implementer = hex.removePrefix("0x").toIntOrNull(16) ?: -1
                }
                trimmed.startsWith("CPU part") -> {
                    val hex = trimmed.substringAfter(":").trim()
                    part = hex.removePrefix("0x").toIntOrNull(16) ?: -1
                }
            }
        }
        flush()
        return result
    }
}
