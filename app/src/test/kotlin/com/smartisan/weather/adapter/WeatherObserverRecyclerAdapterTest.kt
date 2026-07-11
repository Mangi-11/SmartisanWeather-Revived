package com.smartisan.weather.adapter

import com.smartisan.weather.bean.HourForecastInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherObserverRecyclerAdapterTest {

    @Test
    fun `hour item is an immutable snapshot of the legacy bean`() {
        val forecast = HourForecastInfo().apply {
            setStartTime("1783742400000")
            setSunDes("日出")
            setTempC(24)
            setTempF(75)
            setWeatherCode("01")
            night = true
        }

        val item = HourForecastItem.from(forecast)
        forecast.apply {
            setStartTime("invalid")
            setSunDes(null)
            setTempC(30)
            setTempF(86)
            setWeatherCode("02")
            night = false
        }

        assertEquals(1_783_742_400_000L, item.startTimeMillis)
        assertEquals("日出", item.sunDescription)
        assertEquals(24, item.temperatureCelsius)
        assertEquals(75, item.temperatureFahrenheit)
        assertEquals("01", item.weatherCode)
        assertEquals(true, item.isNight)
    }

    @Test
    fun `invalid start time remains absent instead of reusing another hour`() {
        val forecast = HourForecastInfo().apply { setStartTime("invalid") }

        assertNull(HourForecastItem.from(forecast).startTimeMillis)
    }
}
