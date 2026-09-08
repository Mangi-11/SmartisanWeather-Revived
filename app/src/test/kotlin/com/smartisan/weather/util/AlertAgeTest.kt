package com.smartisan.weather.util

import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertAgeTest {
    @Test
    fun `missing negative and future timestamps have no display age`() {
        val now = epoch("2026-09-08T12:00:00+08:00")
        assertNull(alertAge(0, now))
        assertNull(alertAge(-1, now))
        assertNull(alertAge(now + 1, now))
        assertNull(alertAge(Long.MAX_VALUE, now))
        assertNull(alertAge(now, -1))
    }

    @Test
    fun `age units change only after a complete minute hour or day`() {
        val now = epoch("2026-09-08T12:00:00+08:00")
        assertEquals(AlertAge.Now, alertAge(now, now))
        assertEquals(AlertAge.Now, alertAge(now - 59_999, now))
        assertEquals(AlertAge.Minutes(1), alertAge(now - 60_000, now))
        assertEquals(AlertAge.Minutes(59), alertAge(now - 3_599_999, now))
        assertEquals(AlertAge.Hours(1), alertAge(now - 3_600_000, now))
        assertEquals(AlertAge.Hours(23), alertAge(now - 86_399_999, now))
        assertEquals(AlertAge.Days(1), alertAge(now - 86_400_000, now))
    }

    @Test
    fun `crossing midnight does not round one minute into one hour`() {
        assertEquals(
            AlertAge.Minutes(1),
            alertAge(epoch("2026-09-07T23:59:00+08:00"), epoch("2026-09-08T00:00:00+08:00")),
        )
    }

    @Test
    fun `month and leap day boundaries use real elapsed time`() {
        assertEquals(
            AlertAge.Hours(2),
            alertAge(epoch("2026-08-31T23:00:00+08:00"), epoch("2026-09-01T01:00:00+08:00")),
        )
        assertEquals(
            AlertAge.Days(2),
            alertAge(epoch("2024-02-28T12:00:00Z"), epoch("2024-03-01T12:00:00Z")),
        )
    }

    @Test
    fun `year boundary retains the complete number of elapsed days`() {
        assertEquals(
            AlertAge.Days(2),
            alertAge(epoch("2025-12-31T12:00:00Z"), epoch("2026-01-02T12:00:00Z")),
        )
    }

    @Test
    fun `daylight saving transitions do not change elapsed age`() {
        assertEquals(
            AlertAge.Hours(1),
            alertAge(epoch("2026-03-08T01:30:00-05:00"), epoch("2026-03-08T03:30:00-04:00")),
        )
        assertEquals(
            AlertAge.Hours(1),
            alertAge(epoch("2026-11-01T01:30:00-04:00"), epoch("2026-11-01T01:30:00-05:00")),
        )
    }

    private fun epoch(value: String): Long = OffsetDateTime.parse(value).toInstant().toEpochMilli()
}
