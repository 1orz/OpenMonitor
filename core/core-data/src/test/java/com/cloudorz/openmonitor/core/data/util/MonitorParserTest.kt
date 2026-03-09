package com.cloudorz.openmonitor.core.data.util

import org.junit.Assert.*
import org.junit.Test

class MonitorParserTest {

    // ---- parseCpuLoad ----

    @Test
    fun `parseCpuLoad returns 0 for empty input`() {
        val (load, _) = MonitorParser.parseCpuLoad("", null)
        assertEquals(0.0, load, 0.001)
    }

    @Test
    fun `parseCpuLoad returns 0 on first call without prev values`() {
        val procStat = "cpu  10132153 290696 3084719 46828483 16683 0 25195 0 0 0"
        val (load, newValues) = MonitorParser.parseCpuLoad(procStat, null)
        assertEquals(0.0, load, 0.001)
        assertNotNull(newValues)
        assertEquals(10, newValues!!.size)
    }

    @Test
    fun `parseCpuLoad calculates correct percentage with two samples`() {
        val stat1 = "cpu  10132153 290696 3084719 46828483 16683 0 25195 0 0 0"
        val (_, values1) = MonitorParser.parseCpuLoad(stat1, null)

        // Second sample with increased values
        val stat2 = "cpu  10132253 290696 3084819 46828683 16683 0 25295 0 0 0"
        val (load, _) = MonitorParser.parseCpuLoad(stat2, values1)
        assertTrue("CPU load should be > 0, was $load", load > 0.0)
        assertTrue("CPU load should be <= 100, was $load", load <= 100.0)
    }

    @Test
    fun `parseCpuLoad calculates 100 percent when no idle change`() {
        val prev = longArrayOf(100, 0, 0, 500, 0, 0, 0, 0, 0, 0)
        // All increase goes to user (index 0), idle (index 3) stays same
        val stat = "cpu  200 0 0 500 0 0 0 0 0 0"
        val (load, _) = MonitorParser.parseCpuLoad(stat, prev)
        assertEquals(100.0, load, 0.001)
    }

    @Test
    fun `parseCpuLoad calculates 0 percent when only idle changes`() {
        val prev = longArrayOf(100, 0, 0, 500, 0, 0, 0, 0, 0, 0)
        // All increase goes to idle (index 3)
        val stat = "cpu  100 0 0 600 0 0 0 0 0 0"
        val (load, _) = MonitorParser.parseCpuLoad(stat, prev)
        assertEquals(0.0, load, 0.001)
    }

    @Test
    fun `parseCpuLoad handles malformed input`() {
        val (load, _) = MonitorParser.parseCpuLoad("not valid data", null)
        assertEquals(0.0, load, 0.001)
    }

    @Test
    fun `parseCpuLoad handles input with no cpu line`() {
        val (load, prev) = MonitorParser.parseCpuLoad("cpu0 123 456 789\ncpu1 111 222 333", null)
        assertEquals(0.0, load, 0.001)
        assertNull(prev)
    }

    @Test
    fun `parseCpuLoad handles multiline proc stat`() {
        val procStat = """
            cpu  10132153 290696 3084719 46828483 16683 0 25195 0 0 0
            cpu0 2503157 72820 771525 11714388 4170 0 12520 0 0 0
            cpu1 2513980 72578 771135 11714142 4156 0 5765 0 0 0
        """.trimIndent()
        val (load, values) = MonitorParser.parseCpuLoad(procStat, null)
        assertEquals(0.0, load, 0.001)
        assertNotNull(values)
    }

    @Test
    fun `parseCpuLoad returns 0 when total diff is zero`() {
        val prev = longArrayOf(100, 0, 0, 500, 0, 0, 0, 0, 0, 0)
        val stat = "cpu  100 0 0 500 0 0 0 0 0 0"
        val (load, _) = MonitorParser.parseCpuLoad(stat, prev)
        assertEquals(0.0, load, 0.001)
    }

    @Test
    fun `parseCpuLoad preserves prev values on empty input`() {
        val prev = longArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val (_, returnedPrev) = MonitorParser.parseCpuLoad("", prev)
        assertArrayEquals(prev, returnedPrev)
    }

    // ---- parseGpuLoad ----

    @Test
    fun `parseGpuLoad parses percentage string`() {
        assertEquals(75.0, MonitorParser.parseGpuLoad("75%"), 0.001)
    }

    @Test
    fun `parseGpuLoad parses plain number`() {
        assertEquals(42.0, MonitorParser.parseGpuLoad("42"), 0.001)
    }

    @Test
    fun `parseGpuLoad returns 0 for empty string`() {
        assertEquals(0.0, MonitorParser.parseGpuLoad(""), 0.001)
    }

    @Test
    fun `parseGpuLoad returns 0 for invalid string`() {
        assertEquals(0.0, MonitorParser.parseGpuLoad("abc"), 0.001)
    }

    @Test
    fun `parseGpuLoad handles whitespace around value`() {
        assertEquals(88.0, MonitorParser.parseGpuLoad("  88%  "), 0.001)
    }

    @Test
    fun `parseGpuLoad handles decimal value`() {
        assertEquals(55.5, MonitorParser.parseGpuLoad("55.5"), 0.001)
    }

    // ---- parseThermal ----

    @Test
    fun `parseThermal converts millidegrees to degrees`() {
        assertEquals(45.0, MonitorParser.parseThermal("45000"), 0.001)
    }

    @Test
    fun `parseThermal handles regular degrees`() {
        assertEquals(45.0, MonitorParser.parseThermal("45"), 0.001)
    }

    @Test
    fun `parseThermal returns 0 for empty string`() {
        assertEquals(0.0, MonitorParser.parseThermal(""), 0.001)
    }

    @Test
    fun `parseThermal returns 0 for non-numeric`() {
        assertEquals(0.0, MonitorParser.parseThermal("disabled"), 0.001)
    }

    @Test
    fun `parseThermal handles boundary at 1000`() {
        assertEquals(1000.0, MonitorParser.parseThermal("1000"), 0.001)
    }

    @Test
    fun `parseThermal handles value just above 1000`() {
        assertEquals(1.001, MonitorParser.parseThermal("1001"), 0.001)
    }

    @Test
    fun `parseThermal handles whitespace`() {
        assertEquals(37.0, MonitorParser.parseThermal("  37000  "), 0.001)
    }

    // ---- parseBatteryCurrentFromSysfs ----

    @Test
    fun `parseBatteryCurrentFromSysfs converts microamps to milliamps`() {
        assertEquals(1500, MonitorParser.parseBatteryCurrentFromSysfs("1500000"))
    }

    @Test
    fun `parseBatteryCurrentFromSysfs returns null for empty`() {
        assertNull(MonitorParser.parseBatteryCurrentFromSysfs(""))
    }

    @Test
    fun `parseBatteryCurrentFromSysfs returns null for invalid`() {
        assertNull(MonitorParser.parseBatteryCurrentFromSysfs("N/A"))
    }

    @Test
    fun `parseBatteryCurrentFromSysfs handles negative current`() {
        assertEquals(-1500, MonitorParser.parseBatteryCurrentFromSysfs("-1500000"))
    }

    @Test
    fun `parseBatteryCurrentFromSysfs handles zero`() {
        assertEquals(0, MonitorParser.parseBatteryCurrentFromSysfs("0"))
    }

    @Test
    fun `parseBatteryCurrentFromSysfs handles whitespace`() {
        assertEquals(2000, MonitorParser.parseBatteryCurrentFromSysfs("  2000000  "))
    }

    @Test
    fun `parseBatteryCurrentFromSysfs treats small values as milliamps`() {
        // Values < 10000 are assumed to already be in mA
        assertEquals(500, MonitorParser.parseBatteryCurrentFromSysfs("500"))
    }

    // ---- parseBatteryCurrentFromUevent ----

    @Test
    fun `parseBatteryCurrentFromUevent extracts current from uevent`() {
        val uevent = """
            POWER_SUPPLY_STATUS=Discharging
            POWER_SUPPLY_HEALTH=Good
            POWER_SUPPLY_PRESENT=1
            POWER_SUPPLY_CAPACITY=75
            POWER_SUPPLY_VOLTAGE_NOW=3850000
            POWER_SUPPLY_CURRENT_NOW=1500000
            POWER_SUPPLY_TEMP=310
            POWER_SUPPLY_TECHNOLOGY=Li-ion
        """.trimIndent()
        assertEquals(1500, MonitorParser.parseBatteryCurrentFromUevent(uevent))
    }

    @Test
    fun `parseBatteryCurrentFromUevent returns null for empty`() {
        assertNull(MonitorParser.parseBatteryCurrentFromUevent(""))
    }

    @Test
    fun `parseBatteryCurrentFromUevent returns null when no current field`() {
        val uevent = """
            POWER_SUPPLY_STATUS=Discharging
            POWER_SUPPLY_VOLTAGE_NOW=3850000
        """.trimIndent()
        assertNull(MonitorParser.parseBatteryCurrentFromUevent(uevent))
    }

    @Test
    fun `parseBatteryCurrentFromUevent handles negative current`() {
        val uevent = "POWER_SUPPLY_CURRENT_NOW=-2500000"
        assertEquals(-2500, MonitorParser.parseBatteryCurrentFromUevent(uevent))
    }

    @Test
    fun `parseBatteryCurrentFromUevent handles small milliamp values`() {
        val uevent = "POWER_SUPPLY_CURRENT_NOW=800"
        assertEquals(800, MonitorParser.parseBatteryCurrentFromUevent(uevent))
    }

    // ---- normalizeCurrentToMa ----

    @Test
    fun `normalizeCurrentToMa converts microamps to milliamps`() {
        assertEquals(1500, MonitorParser.normalizeCurrentToMa(1500000))
    }

    @Test
    fun `normalizeCurrentToMa keeps small values as milliamps`() {
        assertEquals(500, MonitorParser.normalizeCurrentToMa(500))
    }

    @Test
    fun `normalizeCurrentToMa handles negative microamps`() {
        assertEquals(-1500, MonitorParser.normalizeCurrentToMa(-1500000))
    }

    @Test
    fun `normalizeCurrentToMa handles boundary at 10000`() {
        // 10000 is the boundary: >= 10000 treated as µA
        assertEquals(10, MonitorParser.normalizeCurrentToMa(10000))
    }

    @Test
    fun `normalizeCurrentToMa handles just below boundary`() {
        // 9999 < 10000, treated as mA directly
        assertEquals(9999, MonitorParser.normalizeCurrentToMa(9999))
    }

    @Test
    fun `normalizeCurrentToMa handles zero`() {
        assertEquals(0, MonitorParser.normalizeCurrentToMa(0))
    }
}
