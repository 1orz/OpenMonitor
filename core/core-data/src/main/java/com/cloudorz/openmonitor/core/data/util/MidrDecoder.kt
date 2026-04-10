package com.cloudorz.openmonitor.core.data.util

/**
 * Decodes ARM MIDR (Main ID Register) to human-readable microarchitecture names.
 *
 * MIDR layout (32-bit):
 *   [31:24] implementer
 *   [23:20] variant
 *   [19:16] architecture
 *   [15:4]  primary part number
 *   [3:0]   revision
 *
 */
object MidrDecoder {

    /** Extract implementer field (bits 31-24). */
    private fun implementer(midr: Int): Int = (midr ushr 24) and 0xFF

    /** Extract part number (bits 15-4). */
    private fun partNumber(midr: Int): Int = (midr ushr 4) and 0xFFF

    /** Extract variant (bits 23-20). */
    private fun variant(midr: Int): Int = (midr ushr 20) and 0xF

    /**
     * Decode full MIDR register value.
     * Prefer this over the (implementer, part) overload when variant info is available.
     */
    fun decodeFull(midr: Int): String? {
        val impl = implementer(midr)
        val part = partNumber(midr)
        val vari = variant(midr)
        return when (impl) {
            0x41 -> arm(part)
            0x42 -> broadcom(part)
            0x48 -> hisilicon(part)
            0x4E -> nvidia(part)
            0x51 -> qualcomm(midr, part, vari)
            0x53 -> samsung(midr)
            0x69 -> intel(part)
            else -> null
        }
    }

    /** Fallback: decode from implementer + part number only. */
    fun decode(implementer: Int, part: Int): String? = when (implementer) {
        0x41 -> arm(part)
        0x42 -> broadcom(part)
        0x48 -> hisilicon(part)
        0x4E -> nvidia(part)
        0x51 -> qualcommSimple(part)
        0x53 -> samsungSimple(part)
        0x69 -> intel(part)
        else -> null
    }

    // ── ARM (0x41) ──────────────────────────────────────────────────────────

    private fun arm(part: Int): String? = when (part) {
        0xC05 -> "Cortex-A5"
        0xC07 -> "Cortex-A7"
        0xC08 -> "Cortex-A8"
        0xC09 -> "Cortex-A9"
        0xC0C -> "Cortex-A12"
        0xC0D -> "Cortex-A12"
        0xC0E -> "Cortex-A17"
        0xC0F -> "Cortex-A15"
        0xD01 -> "Cortex-A32"
        0xD02 -> "Cortex-A34"
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
        0xD89 -> "Cortex-A520AE"
        0xD8E -> "Neoverse N3"
        else -> null
    }

    // ── Qualcomm (0x51) — needs full MIDR for Oryon/Krait variant ──────────

    private fun qualcomm(midr: Int, part: Int, variant: Int): String? {
        // Oryon: part == 0x001, differentiate by variant bits
        if (part == 0x001) {
            return when (variant) {
                3 -> "Oryon Phoenix L"
                4 -> "Oryon Phoenix M"
                else -> "Oryon"
            }
        }
        // Oryon Gen 3: part == 0x002
        if (part == 0x002) {
            return when (variant) {
                2 -> "Oryon Performance (Gen 3)"
                3 -> "Oryon Prime (Gen 3)"
                else -> "Oryon (Gen 3)"
            }
        }
        // Krait: part == 0x06F, differentiate by variant+revision
        if (part == 0x06F) {
            val varRev = midr and 0x00F0000F
            return when {
                varRev == 0x00000001 || varRev == 0x00000002 -> "Krait 200"
                varRev == 0x00100000 -> "Krait 300"
                varRev == 0x00200000 || varRev == 0x00200001 -> "Krait 400"
                varRev == 0x00300001 -> "Krait 450"
                else -> "Krait"
            }
        }
        // Krait (part 0x04D)
        if (part == 0x04D) {
            val varRev = midr and 0x00F0000F
            return when {
                varRev == 0x00100000 || varRev == 0x00100004 -> "Krait 200"
                varRev == 0x00200000 -> "Krait 300"
                else -> "Krait"
            }
        }
        return qualcommSimple(part)
    }

    private fun qualcommSimple(part: Int): String? = when (part) {
        0x00F, 0x02D -> "Scorpion"
        0x04D -> "Krait"
        0x06F -> "Krait"
        0x201, 0x205 -> "Kryo Gold"
        0x211 -> "Kryo 2nd Gen"
        0x800 -> "Kryo 2xx Gold (A73)"
        0x801 -> "Kryo 2xx Silver (A53)"
        0x802 -> "Kryo 3xx Gold (A75)"
        0x803 -> "Kryo 3xx Silver (A55r0)"
        0x804 -> "Kryo 4xx Gold (A76)"
        0x805 -> "Kryo 4xx Silver (A55)"
        0xC00 -> "Falkor"
        0xC01 -> "Saphira"
        0x001 -> "Oryon"
        0x002 -> "Oryon (Gen 3)"
        else -> null
    }

    // ── Samsung (0x53) ──────────────────────────────────────────────────────

    private fun samsung(midr: Int): String? {
        // Samsung uses part+variant encoding
        val encoded = midr and 0x00F0FFF0
        return when (encoded) {
            0x00100010 -> "Exynos M1"
            0x00100020 -> "Exynos M3"
            0x00100030 -> "Exynos M4"
            0x00100040 -> "Exynos M5"
            0x00400010 -> "Exynos M2"
            else -> samsungSimple(partNumber(midr))
        }
    }

    private fun samsungSimple(part: Int): String? = when (part) {
        0x001 -> "Exynos M1"
        0x002 -> "Exynos M3"
        0x003 -> "Exynos M4"
        0x004 -> "Exynos M5"
        else -> null
    }

    // ── HiSilicon (0x48) ────────────────────────────────────────────────────

    private fun hisilicon(part: Int): String? = when (part) {
        0xD01 -> "TaiShan v110"
        0xD02 -> "TaiShan V120 (prime)"
        0xD03 -> "Cortex-A53"
        0xD04 -> "TaiShan V121 (prime)"
        0xD05 -> "TaiShan V121"
        0xD22 -> "TaiShan V120"
        0xD24 -> "TaiShan V121 (mid)"
        0xD40 -> "Cortex-A76"
        0xD42 -> "Cortex-A510"
        0xD44 -> "Cortex-A510"
        0xD46 -> "Cortex-A510"
        else -> null
    }

    // ── NVIDIA (0x4E) ───────────────────────────────────────────────────────

    private fun nvidia(part: Int): String? = when (part) {
        0x000 -> "Denver"
        0x003 -> "Denver 2"
        0x004 -> "Carmel"
        else -> null
    }

    // ── Broadcom (0x42) ─────────────────────────────────────────────────────

    private fun broadcom(part: Int): String? = when (part) {
        0x00F -> "Brahma B15"
        0x100 -> "Brahma B53"
        else -> null
    }

    // ── Intel (0x69) ────────────────────────────────────────────────────────

    private fun intel(part: Int): String? {
        val family = (part ushr 8) and 0xF
        return if (family == 2 || family == 4 || family == 6) "XScale" else null
    }

    /** Resolve implementer ID to vendor name. */
    fun vendorName(implementer: Int): String? = when (implementer) {
        0x41 -> "ARM"
        0x42 -> "Broadcom"
        0x43 -> "Cavium"
        0x44 -> "DEC"
        0x48 -> "HiSilicon"
        0x4E -> "NVIDIA"
        0x50 -> "APM"
        0x51 -> "Qualcomm"
        0x53 -> "Samsung"
        0x56 -> "Marvell"
        0x61 -> "Apple"
        0x66 -> "Faraday"
        0x69 -> "Intel"
        else -> null
    }

    // ── /proc/cpuinfo parser ────────────────────────────────────────────────

    data class CoreMidrInfo(
        val microarchName: String?,
        val vendorName: String?,
    )

    /** Parse /proc/cpuinfo and return a map of coreIndex → microarch name. */
    fun parseProcCpuInfo(content: String): Map<Int, String> =
        parseProcCpuInfoFull(content).mapValues { it.value.microarchName ?: "" }
            .filterValues { it.isNotEmpty() }

    /** Parse /proc/cpuinfo and return full MIDR info per core. */
    fun parseProcCpuInfoFull(content: String): Map<Int, CoreMidrInfo> {
        val result = mutableMapOf<Int, CoreMidrInfo>()
        var currentCore = -1
        var impl = -1
        var part = -1
        var variant = -1
        var revision = -1

        fun flush() {
            if (currentCore >= 0 && impl >= 0 && part >= 0) {
                val v = if (variant >= 0) variant else 0
                val r = if (revision >= 0) revision else 0
                val fullMidr = (impl shl 24) or (v shl 20) or (0xF shl 16) or (part shl 4) or r
                result[currentCore] = CoreMidrInfo(
                    microarchName = decodeFull(fullMidr),
                    vendorName = vendorName(impl),
                )
            }
        }

        for (line in content.lineSequence()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("processor") -> {
                    flush()
                    currentCore = trimmed.substringAfter(":").trim().toIntOrNull() ?: -1
                    impl = -1; part = -1; variant = -1; revision = -1
                }
                trimmed.startsWith("CPU implementer") -> {
                    val hex = trimmed.substringAfter(":").trim()
                    impl = hex.removePrefix("0x").toIntOrNull(16) ?: -1
                }
                trimmed.startsWith("CPU part") -> {
                    val hex = trimmed.substringAfter(":").trim()
                    part = hex.removePrefix("0x").toIntOrNull(16) ?: -1
                }
                trimmed.startsWith("CPU variant") -> {
                    val hex = trimmed.substringAfter(":").trim()
                    variant = hex.removePrefix("0x").toIntOrNull(16) ?: -1
                }
                trimmed.startsWith("CPU revision") -> {
                    val hex = trimmed.substringAfter(":").trim()
                    revision = hex.removePrefix("0x").toIntOrNull(16)
                        ?: trimmed.substringAfter(":").trim().toIntOrNull() ?: -1
                }
            }
        }
        flush()
        return result
    }
}
