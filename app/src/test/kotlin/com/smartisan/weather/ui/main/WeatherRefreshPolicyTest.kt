package com.smartisan.weather.ui.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherRefreshPolicyTest {

    @Test
    fun `fresh cache skips automatic network refresh`() {
        val now = 1_800_000_000_000L

        assertFalse(
            shouldRefreshWeatherCache(
                updatedAtMillis = now - AUTOMATIC_WEATHER_REFRESH_INTERVAL_MILLIS + 1L,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `expired missing or future cache refreshes automatically`() {
        val now = 1_800_000_000_000L

        assertTrue(
            shouldRefreshWeatherCache(
                updatedAtMillis = now - AUTOMATIC_WEATHER_REFRESH_INTERVAL_MILLIS,
                nowMillis = now,
            ),
        )
        assertTrue(shouldRefreshWeatherCache(updatedAtMillis = 0L, nowMillis = now))
        assertTrue(shouldRefreshWeatherCache(updatedAtMillis = now + 1L, nowMillis = now))
    }
}
