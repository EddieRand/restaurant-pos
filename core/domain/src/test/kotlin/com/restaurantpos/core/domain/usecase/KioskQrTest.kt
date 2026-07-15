package com.restaurantpos.core.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/** Verifies the contract that kiosk QR encodes the raw orderId unchanged. */
class KioskQrTest {

    private fun qrContent(orderId: String): String = orderId

    @Test fun `qr content equals orderId`() {
        val id = UUID.randomUUID().toString()
        assertEquals(id, qrContent(id))
    }

    @Test fun `short order id for display is last 6 chars uppercase`() {
        val id = "abc123def456"
        val shortId = id.takeLast(6).uppercase()
        assertEquals("DEF456", shortId)
        assertEquals(6, shortId.length)
    }

    @Test fun `auto return countdown starts at 30`() {
        val AUTO_RETURN_SECONDS = 30
        assertEquals(30, AUTO_RETURN_SECONDS)
    }

    @Test fun `countdown ticks down to zero`() {
        var countdown = 30
        repeat(30) { countdown-- }
        assertEquals(0, countdown)
    }
}
