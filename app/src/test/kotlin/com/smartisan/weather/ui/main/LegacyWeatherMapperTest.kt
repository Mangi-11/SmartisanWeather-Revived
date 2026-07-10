package com.smartisan.weather.ui.main

import com.smartisan.weather.data.model.DailyForecast
import com.smartisan.weather.data.model.HourForecast
import com.smartisan.weather.data.model.Observe
import com.smartisan.weather.data.model.Weather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyWeatherMapperTest {

    @Test
    fun `mapper normalizes API times for original view algorithms`() {
        val source = Weather(
            observe = Observe(tempC = "27", tempF = "80", code = "08"),
            dailyForecast = listOf(
                DailyForecast(
                    date = "202607100000",
                    weekDay = "周五",
                    weatherCodeAm = "08",
                    weatherCodePm = "09",
                    lowTempC = 24,
                    lowTempF = 75,
                    highTempC = 31,
                    highTempF = 87,
                    sunriseAndSunset = "04:56|19:43",
                ),
                DailyForecast(
                    date = "202607110000",
                    weekDay = "周六",
                    sunriseAndSunset = "04:56|19:43",
                ),
            ),
            hourForecast = listOf(
                HourForecast(
                    weatherCode = "01",
                    tempC = 27,
                    tempF = 80,
                    startTime = "20260710100000",
                ),
            ),
        )

        val mapped = source.toLegacyWeather()
        val mappedStart = mapped.hourForecast!!.getmInfo()!!.single().getStartTime()!!

        assertNotEquals("20260710100000", mappedStart)
        assertTrue(mappedStart.toLong() in 1_700_000_000_000L..2_000_000_000_000L)
        assertEquals("24", mapped.observe!!.getLowTempC())
        assertEquals("31", mapped.observe!!.getHighTempC())
        assertEquals("2026-07-10=04:56", mapped.observe!!.getCurSunRise())
        assertEquals("2026-07-11=19:43", mapped.observe!!.getNextSunSet())
        assertEquals("09", mapped.newForecast!!.first().getWeatherCodePm())
    }
}
