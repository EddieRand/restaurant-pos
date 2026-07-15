package com.restaurantpos.core.domain.usecase

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Verifies that timezone-aware day boundary calculation is correct.
 * UTC midnight ≠ Asia/Tokyo midnight — there is a 9-hour offset.
 */
class TimeZoneTest {

    private fun dayStartEpoch(epochMs: Long, timeZone: String): Long {
        val zone = ZoneId.of(timeZone)
        val date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone).toLocalDate()
        return date.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    @Test fun `UTC day start is midnight UTC`() {
        // 2024-03-15 10:00:00 UTC = epoch 1710496800000
        val epoch = 1710496800000L
        val dayStart = dayStartEpoch(epoch, "UTC")
        // 2024-03-15 00:00:00 UTC
        assertEquals(1710460800000L, dayStart)
    }

    @Test fun `Asia-Tokyo day start differs from UTC midnight by 9 hours`() {
        // 2024-03-15 10:00:00 JST = 2024-03-15 01:00:00 UTC = epoch 1710464400000
        val epochJst = 1710464400000L
        val utcDayStart = dayStartEpoch(epochJst, "UTC")
        val jstDayStart = dayStartEpoch(epochJst, "Asia/Tokyo")
        // JST midnight = UTC 2024-03-14 15:00:00 = UTC-9h before UTC midnight
        assertEquals(utcDayStart - 9 * 3600_000L, jstDayStart)
    }

    @Test fun `America-New_York day start differs from UTC midnight in winter (EST)`() {
        // 2024-01-15 12:00:00 EST = 2024-01-15 17:00:00 UTC
        // epoch: 2024-01-15 17:00:00 UTC = 1705337600... let's compute carefully
        // 2024-01-15 00:00:00 UTC = 1705276800000
        // 2024-01-15 12:00:00 UTC = 1705320000000
        val epochEst = 1705320000000L  // 2024-01-15 12:00 UTC (= 2024-01-15 07:00 EST)
        val estDayStart = dayStartEpoch(epochEst, "America/New_York")
        val utcDayStart = dayStartEpoch(epochEst, "UTC")
        // EST is UTC-5, so EST midnight = UTC midnight + 5h
        assertEquals(utcDayStart + 5 * 3600_000L, estDayStart)
    }

    @Test fun `day boundary crosses correctly at midnight in timezone`() {
        // 2024-03-15 23:59:59 JST = 2024-03-15 14:59:59 UTC
        val oneMinuteBeforeJstMidnight = 1710514799000L
        // 2024-03-16 00:00:01 JST = 2024-03-15 15:00:01 UTC
        val oneSecondAfterJstMidnight = 1710514801000L

        val dayBefore = dayStartEpoch(oneMinuteBeforeJstMidnight, "Asia/Tokyo")
        val dayAfter = dayStartEpoch(oneSecondAfterJstMidnight, "Asia/Tokyo")

        assertNotEquals("Day boundary should have crossed", dayBefore, dayAfter)
        assertEquals("New day should be 24h after previous", 86_400_000L, dayAfter - dayBefore)
    }
}
