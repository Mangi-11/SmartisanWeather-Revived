package com.smartisan.weather.appwidget

import com.smartisan.weather.data.model.AirQuality
import com.smartisan.weather.data.model.DailyForecast
import com.smartisan.weather.data.model.HourForecast
import com.smartisan.weather.data.model.Observe
import com.smartisan.weather.data.model.SavedCity
import com.smartisan.weather.data.model.Weather
import com.smartisan.weather.data.settings.WeatherSettings
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherWidgetModelsTest {
    @Test
    fun automaticSelectionPrefersLocationCity() {
        val regular = city("weathercn:101020100", order = 2)
        val location = city("weathercn:101010100", order = 1)

        assertEquals(
            location.locationKey,
            WeatherWidgetCityResolver.resolve(
                cities = listOf(location, regular),
                selection = AUTO_CITY_SELECTION,
            )?.locationKey,
        )
    }

    @Test
    fun missingFixedSelectionFallsBackToAutomaticCity() {
        val first = city("weathercn:101020100", order = 2)

        assertEquals(
            first.locationKey,
            WeatherWidgetCityResolver.resolve(
                cities = listOf(first),
                selection = "weathercn:999999999",
            )?.locationKey,
        )
        assertEquals(
            true,
            WeatherWidgetCityResolver.isStaleSelection(
                cities = listOf(first),
                selection = "weathercn:999999999",
            ),
        )
        assertNull(WeatherWidgetCityResolver.resolve(emptyList(), AUTO_CITY_SELECTION))
    }

    @Test
    fun contentUsesRequestedTemperatureUnitWithoutInventingMissingValues() {
        val weather = Weather(
            observe = Observe(
                tempC = "25",
                tempF = "77",
                code = "01",
            ),
            dailyForecast = listOf(
                DailyForecast(
                    highTempC = 30,
                    highTempF = 86,
                    lowTempC = 20,
                    lowTempF = 68,
                ),
            ),
            hourForecast = listOf(
                HourForecast(
                    weatherCode = "01",
                    tempC = 24,
                    tempF = 75,
                    startTime = "202607151400",
                    night = false,
                ),
                HourForecast(
                    weatherCode = "02",
                    tempC = -1,
                    tempF = -1,
                    startTime = "invalid",
                    night = true,
                ),
                HourForecast(
                    weatherCode = "02",
                    temp = "-1",
                    tempC = -1,
                    tempF = 30,
                    startTime = "202607151500",
                ),
            ),
            airQuality = AirQuality(aqiValue = "42"),
        )

        val celsius = WeatherWidgetContentFactory.create(
            weather,
            WeatherSettings.UNIT_CELSIUS,
        )
        val fahrenheit = WeatherWidgetContentFactory.create(
            weather,
            WeatherSettings.UNIT_FAHRENHEIT,
        )

        assertEquals("25", celsius.currentTemperature)
        assertEquals("30", celsius.highTemperature)
        assertEquals("20", celsius.lowTemperature)
        assertEquals("24", celsius.forecast[0].temperature)
        assertEquals(14, celsius.forecast[0].hour)
        assertNull(celsius.forecast[1].temperature)
        assertEquals("-1", celsius.forecast[2].temperature)
        assertEquals("77", fahrenheit.currentTemperature)
        assertEquals("86", fahrenheit.highTemperature)
        assertEquals("68", fahrenheit.lowTemperature)
        assertEquals(42, fahrenheit.aqiValue)
    }

    @Test
    fun currentNightStateUsesSunTimesWhenHourlyForecastIsMissing() {
        val weather = Weather(
            observe = Observe(
                tempC = "25",
                tempF = "77",
                code = "00",
                curSunRise = "06:00",
                curSunSet = "18:00",
            ),
        )

        val content = WeatherWidgetContentFactory.create(
            weather = weather,
            tempUnit = WeatherSettings.UNIT_CELSIUS,
            now = LocalTime.of(23, 0),
        )

        assertEquals(true, content.isNight)
    }

    @Test
    fun exactSizeSpecKeepsOneCompositionAndAddsDetailAsSpaceGrows() {
        val compact = WeatherWidgetLayoutSpec.fromSize(widthDp = 160, heightDp = 160)
        val narrowWide = WeatherWidgetLayoutSpec.fromSize(widthDp = 250, heightDp = 148)
        val mediumWide = WeatherWidgetLayoutSpec.fromSize(widthDp = 300, heightDp = 148)
        val wide = WeatherWidgetLayoutSpec.fromSize(widthDp = 360, heightDp = 128)
        val expandedWide = WeatherWidgetLayoutSpec.fromSize(widthDp = 360, heightDp = 148)

        assertEquals(false, compact.isWide)
        assertEquals(true, compact.showMeta)
        assertEquals(true, wide.isWide)
        assertEquals(2, narrowWide.forecastSlots)
        assertEquals(3, mediumWide.forecastSlots)
        assertEquals(4, wide.forecastSlots)
        assertEquals(118, wide.currentColumnWidthDp)
        assertEquals(10, wide.outerPaddingDp)
        assertEquals(12, expandedWide.outerPaddingDp)
        assertEquals(36, wide.currentTemperatureSp)
    }

    private fun city(key: String, order: Int) = SavedCity(
        locationKey = key,
        locationName = key,
        sortOrder = order,
    )
}
