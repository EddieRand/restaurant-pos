package com.restaurantpos.server

import com.restaurantpos.server.ai.formatInsightPeriod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DeepSeekAiInsightClientTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `formats a same-day reporting period without deriving dates in the model`() {
        val from = LocalDate.of(2026, 7, 16).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = LocalDate.of(2026, 7, 17).atStartOfDay(zone).toInstant().toEpochMilli()

        assertEquals("2026-07-16", formatInsightPeriod(from, to, zone))
    }

    @Test
    fun `formats a multi-day reporting period with an inclusive end date`() {
        val from = LocalDate.of(2026, 7, 10).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = LocalDate.of(2026, 7, 17).atStartOfDay(zone).toInstant().toEpochMilli()

        assertEquals("2026-07-10 至 2026-07-16", formatInsightPeriod(from, to, zone))
    }
}
