package com.cloudorz.monitor.core.common

import org.junit.Assert.*
import org.junit.Test

class CommandResultTest {

    @Test
    fun `success factory creates result with exit code 0`() {
        val result = CommandResult.success("output")
        assertEquals(0, result.exitCode)
        assertEquals("output", result.stdout)
        assertEquals("", result.stderr)
    }

    @Test
    fun `success result isSuccess returns true`() {
        val result = CommandResult.success("output")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `failure factory creates result with given exit code`() {
        val result = CommandResult.failure("error msg", exitCode = 127)
        assertEquals(127, result.exitCode)
        assertEquals("", result.stdout)
        assertEquals("error msg", result.stderr)
    }

    @Test
    fun `failure factory defaults to exit code 1`() {
        val result = CommandResult.failure("error")
        assertEquals(1, result.exitCode)
    }

    @Test
    fun `failure result isSuccess returns false`() {
        val result = CommandResult.failure("error")
        assertFalse(result.isSuccess)
    }

    @Test
    fun `outputOrError returns stdout on success`() {
        val result = CommandResult.success("good output")
        assertEquals("good output", result.outputOrError)
    }

    @Test
    fun `outputOrError returns stderr on failure`() {
        val result = CommandResult.failure("bad output")
        assertEquals("bad output", result.outputOrError)
    }

    @Test
    fun `outputOrError returns empty string when both are blank on failure`() {
        val result = CommandResult(exitCode = 1, stdout = "", stderr = "")
        assertEquals("", result.outputOrError)
    }

    @Test
    fun `data class equality works correctly`() {
        val a = CommandResult(exitCode = 0, stdout = "out", stderr = "")
        val b = CommandResult(exitCode = 0, stdout = "out", stderr = "")
        assertEquals(a, b)
    }

    @Test
    fun `data class inequality on different exit codes`() {
        val a = CommandResult(exitCode = 0, stdout = "out", stderr = "")
        val b = CommandResult(exitCode = 1, stdout = "out", stderr = "")
        assertNotEquals(a, b)
    }

    @Test
    fun `constructor stores all fields correctly`() {
        val result = CommandResult(exitCode = 42, stdout = "hello", stderr = "world")
        assertEquals(42, result.exitCode)
        assertEquals("hello", result.stdout)
        assertEquals("world", result.stderr)
    }

    @Test
    fun `exit code 0 is success, non-zero is failure`() {
        assertTrue(CommandResult(0, "", "").isSuccess)
        assertFalse(CommandResult(1, "", "").isSuccess)
        assertFalse(CommandResult(-1, "", "").isSuccess)
        assertFalse(CommandResult(255, "", "").isSuccess)
    }
}
