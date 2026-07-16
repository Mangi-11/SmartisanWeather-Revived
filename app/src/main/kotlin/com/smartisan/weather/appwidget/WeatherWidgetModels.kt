package com.smartisan.weather.appwidget

import com.smartisan.weather.data.model.SavedCity
import com.smartisan.weather.data.model.Weather
import com.smartisan.weather.data.settings.WeatherSettings
import java.time.Instant
import java.time.LocalTime

internal const val AUTO_CITY_SELECTION = "@auto-location"

internal object WeatherWidgetCityResolver {
    fun resolve(cities: List<SavedCity>, selection: String?): SavedCity? {
        val automatic = cities.firstOrNull(SavedCity::isLocationCity) ?: cities.firstOrNull()
        if (selection.isNullOrBlank() || selection == AUTO_CITY_SELECTION) return automatic
        return cities.firstOrNull { it.locationKey == selection } ?: automatic
    }

    fun isStaleSelection(cities: List<SavedCity>, selection: String?): Boolean =
        !selection.isNullOrBlank() &&
            selection != AUTO_CITY_SELECTION &&
            cities.none { it.locationKey == selection }
}

internal enum class WeatherWidgetUpdateState {
    IDLE,
    REFRESHING,
    FAILED,
}

internal enum class WeatherWidgetEmptyState {
    SETUP_REQUIRED,
    CITY_REQUIRED,
    WEATHER_LOADING,
    WEATHER_UNAVAILABLE,
}

/** A single composition whose rhythm scales with the exact space assigned by the launcher. */
internal data class WeatherWidgetLayoutSpec(
    val isWide: Boolean,
    val outerPaddingDp: Int,
    val currentTemperatureSp: Int,
    val currentColumnWidthDp: Int,
    val forecastSlots: Int,
    val showMeta: Boolean,
) {
    companion object {
        fun fromSize(widthDp: Int, heightDp: Int): WeatherWidgetLayoutSpec {
            val wide = widthDp >= 250
            val roomy = heightDp >= 148
            return WeatherWidgetLayoutSpec(
                isWide = wide,
                outerPaddingDp = if (widthDp < 145 || heightDp < 130) 10 else 12,
                currentTemperatureSp = when {
                    wide -> 36
                    roomy -> 42
                    else -> 36
                },
                currentColumnWidthDp = (widthDp * 0.33f).toInt().coerceIn(108, 132),
                forecastSlots = when {
                    widthDp >= 330 -> 4
                    widthDp >= 280 -> 3
                    else -> 2
                },
                showMeta = heightDp >= 138,
            )
        }
    }
}

internal data class WeatherWidgetForecastSlot(
    val code: String,
    val temperature: String?,
    val hour: Int,
    val isNight: Boolean,
)

internal data class WeatherWidgetContent(
    val code: String,
    val currentTemperature: String?,
    val highTemperature: String?,
    val lowTemperature: String?,
    val isNight: Boolean,
    val aqiValue: Int?,
    val forecast: List<WeatherWidgetForecastSlot>,
)

internal object WeatherWidgetContentFactory {
    fun create(
        weather: Weather,
        tempUnit: Int,
        now: Instant = Instant.now(),
    ): WeatherWidgetContent {
        val isCelsius = tempUnit == WeatherSettings.UNIT_CELSIUS
        val daily = weather.dailyForecast.firstOrNull()
        val observe = weather.observe
        val forecast = weather.hourForecast.take(MAX_FORECAST_SLOTS).map { hour ->
            val temperature = if (isCelsius) hour.tempC else hour.tempF
            WeatherWidgetForecastSlot(
                code = hour.weatherCode.ifBlank { hour.code },
                temperature = temperature
                    .takeIf {
                        it != UNKNOWN_HOURLY_TEMPERATURE || hour.temp.isNotBlank()
                    }
                    ?.toString(),
                hour = hour.hour,
                isNight = hour.night,
            )
        }

        return WeatherWidgetContent(
            code = weather.themeCode,
            currentTemperature = (if (isCelsius) observe.tempC else observe.tempF)
                .temperatureOrNull(),
            highTemperature = (if (isCelsius) observe.highTempC else observe.highTempF)
                .temperatureOrNull()
                ?: daily?.let { if (isCelsius) it.highTempC else it.highTempF }?.toString(),
            lowTemperature = (if (isCelsius) observe.lowTempC else observe.lowTempF)
                .temperatureOrNull()
                ?: daily?.let { if (isCelsius) it.lowTempC else it.lowTempF }?.toString(),
            isNight = isNight(weather, weather.localTimeAt(now)),
            aqiValue = weather.airQuality.aqiInt.takeIf { it >= 0 }
                ?: observe.aqi.trim().toIntOrNull()?.takeIf { it >= 0 },
            forecast = forecast,
        )
    }

    private fun String.temperatureOrNull(): String? =
        trim().takeUnless { it.isEmpty() || it.equals(UNKNOWN_TEMPERATURE, ignoreCase = true) }

    private fun isNight(weather: Weather, now: LocalTime): Boolean {
        val observeSunTimes = listOf(
            weather.observe.curSunRise,
            weather.observe.curSunSet,
        ).mapNotNull(::extractTimeInMinutes)
        val sunTimes = if (observeSunTimes.size == 2) {
            observeSunTimes
        } else {
            TIME_REGEX.findAll(weather.dailyForecast.firstOrNull()?.sunriseAndSunset.orEmpty())
                .mapNotNull { extractTimeInMinutes(it.value) }
                .take(2)
                .toList()
        }
        if (sunTimes.size != 2) return weather.hourForecast.firstOrNull()?.night == true

        val nowInMinutes = now.hour * MINUTES_PER_HOUR + now.minute
        return nowInMinutes < sunTimes[0] || nowInMinutes >= sunTimes[1]
    }

    private fun extractTimeInMinutes(value: String): Int? {
        val match = TIME_REGEX.find(value) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        return hour * MINUTES_PER_HOUR + minute
    }

    private const val MAX_FORECAST_SLOTS = 4
    private const val MINUTES_PER_HOUR = 60
    private const val UNKNOWN_TEMPERATURE = "UNKNOWN"
    private const val UNKNOWN_HOURLY_TEMPERATURE = -1
    private val TIME_REGEX = Regex("(?:^|\\D)([01]?\\d|2[0-3]):([0-5]\\d)(?:$|\\D)")
}
