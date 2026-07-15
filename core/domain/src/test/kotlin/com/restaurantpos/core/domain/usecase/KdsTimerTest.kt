package com.restaurantpos.core.domain.usecase

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for KDS elapsed-time threshold logic (pure computation, no Android deps).
 */
class KdsTimerTest {

    private val urgentSeconds = 300L
    private val criticalSeconds = 600L

    private fun elapsedSeconds(createdAt: Long, nowMs: Long) = (nowMs - createdAt) / 1_000L

    @Test
    fun `ticket under urgentSeconds is neither urgent nor critical`() {
        val created = 0L
        val elapsed = elapsedSeconds(created, 299_000L)
        assertFalse(elapsed >= urgentSeconds)
        assertFalse(elapsed >= criticalSeconds)
    }

    @Test
    fun `ticket at exactly urgentSeconds triggers urgent`() {
        val elapsed = elapsedSeconds(0L, 300_000L)
        assertTrue(elapsed >= urgentSeconds)
        assertFalse(elapsed >= criticalSeconds)
    }

    @Test
    fun `ticket between urgent and critical is urgent only`() {
        val elapsed = elapsedSeconds(0L, 450_000L)
        assertTrue(elapsed >= urgentSeconds)
        assertFalse(elapsed >= criticalSeconds)
    }

    @Test
    fun `ticket at exactly criticalSeconds triggers critical`() {
        val elapsed = elapsedSeconds(0L, 600_000L)
        assertTrue(elapsed >= urgentSeconds)
        assertTrue(elapsed >= criticalSeconds)
    }

    @Test
    fun `ticket well past criticalSeconds still critical`() {
        val elapsed = elapsedSeconds(0L, 3_600_000L)
        assertTrue(elapsed >= criticalSeconds)
        assertEquals(3600L, elapsed)
    }

    @Test
    fun `formatElapsed shows seconds only below 1 minute`() {
        assertEquals("45s", formatElapsed(45L))
    }

    @Test
    fun `formatElapsed shows minutes and seconds`() {
        assertEquals("7m 30s", formatElapsed(450L))
    }

    @Test
    fun `formatElapsed shows 0s for zero elapsed`() {
        assertEquals("0s", formatElapsed(0L))
    }

    // Mirror of the private formatElapsed in KdsScreen (duplicated here for pure-JVM testing)
    private fun formatElapsed(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }
}
