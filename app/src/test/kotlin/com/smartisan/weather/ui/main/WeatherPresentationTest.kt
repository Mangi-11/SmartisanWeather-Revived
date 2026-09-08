package com.smartisan.weather.ui.main

import com.smartisan.weather.data.model.DailyForecast
import com.smartisan.weather.data.model.HourForecast
import com.smartisan.weather.data.model.Weather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class WeatherPresentationTest {
    @Test
    fun sunEventsAreInsertedOnlyInsideActualForecastAndReplaceMatchingHours() {
        val weather = Weather(
            dailyForecast = listOf(DailyForecast(date = "20260908", sunriseAndSunset = "06:20|18:10")),
            hourForecast = listOf(
                HourForecast(startTime = "202609080600", tempC = 17, tempF = 63),
                HourForecast(startTime = "202609080700", tempC = 18, tempF = 64),
                HourForecast(startTime = "202609081810", tempC = 23, tempF = 73),
                HourForecast(startTime = "202609081900", tempC = 22, tempF = 72),
            ),
        )
        val items = weather.hourItems()
        assertEquals(5, items.size)
        assertEquals(listOf("1000", "1001"), items.filter { it.key.startsWith("sun:") }.map { it.code })
        assertEquals(1, items.count { it.time == LocalDateTime.of(2026, 9, 8, 18, 10) })
        val shorter = weather.copy(hourForecast = weather.hourForecast.take(2)).hourItems()
        assertEquals(listOf("1000"), shorter.filter { it.key.startsWith("sun:") }.map { it.code })
    }

    @Test
    fun offsetsAreConvertedToCityTimeAndMissingSunTimesDoNotCreateEvents() {
        val weather = Weather(timezoneOffsetSeconds = -7 * 3600)
        assertEquals(LocalDateTime.of(2026, 9, 8, 9, 15), parseWeatherLocalTime("2026-09-08T16:15:00Z", weather))
        assertTrue(weather.copy(dailyForecast = listOf(DailyForecast(date = "20260908"))).hourItems().isEmpty())
    }
}
