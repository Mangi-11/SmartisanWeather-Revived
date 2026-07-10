package com.smartisan.weather.ui.main

import com.smartisan.weather.bean.Alert as LegacyAlert
import com.smartisan.weather.bean.AlertInfo as LegacyAlertInfo
import com.smartisan.weather.bean.Allergy as LegacyAllergy
import com.smartisan.weather.bean.HourForecast as LegacyHourForecast
import com.smartisan.weather.bean.HourForecastInfo as LegacyHourForecastInfo
import com.smartisan.weather.bean.NewForecastInfo as LegacyForecastInfo
import com.smartisan.weather.bean.Observe as LegacyObserve
import com.smartisan.weather.bean.SmartisanLocation
import com.smartisan.weather.bean.Weather as LegacyWeather
import com.smartisan.weather.custom.DrawItem
import com.smartisan.weather.data.model.SavedCity
import com.smartisan.weather.data.model.Weather
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Converts the immutable data-layer state into the mutable model used by the original Views. */
internal fun WeatherUiState.toLegacyDrawItems(): ArrayList<DrawItem> =
    cities.mapTo(ArrayList(cities.size)) { city ->
        DrawItem(
            weathers[city.locationKey]?.toLegacyWeather(city.locationKey),
            city.toLegacyLocation(),
        )
    }

/** Converts a saved Room city into the location object expected by the original View layer. */
internal fun SavedCity.toLegacyLocation(): SmartisanLocation = SmartisanLocation().also { location ->
    location.id = id
    location.mLocationKey = locationKey
    location.mLocationName = locationName
    location.mLocationParentName = locationParentName
    location.mCountry = country
    location.mProvince = province
    location.sortOrder = sortOrder
}

/** Converts a modern weather model into the mutable bean expected by the original View layer. */
internal fun Weather.toLegacyWeather(): LegacyWeather = toLegacyWeather(locationKey = null)

private fun Weather.toLegacyWeather(locationKey: String?): LegacyWeather {
    val today = dailyForecast.firstOrNull()
    val tomorrow = dailyForecast.getOrNull(1)
    val todayDate = today?.date?.toIsoDate()
    val tomorrowDate = tomorrow?.date?.toIsoDate()
    val todaySunTimes = today?.sunriseAndSunset.toSunTimes()
    val tomorrowSunTimes = tomorrow?.sunriseAndSunset.toSunTimes()

    val legacyObserve = LegacyObserve().apply {
        setLocationKey(locationKey)
        setTempC(observe.tempC.ifBlank { temp.ifBlank { UNKNOWN_VALUE } })
        setTempF(observe.tempF.ifBlank { UNKNOWN_VALUE })
        setCode(observe.code.ifBlank { weatherCode })
        setWind(observe.wind.ifBlank { windDirection })
        setSpeed(observe.speed.ifBlank { windSpeed })
        setHumidity(observe.humidity.ifBlank { relativeHumidity })
        setCompareC(observe.compareC.ifBlank { UNKNOWN_VALUE })
        setCompareF(observe.compareF.ifBlank { UNKNOWN_VALUE })
        setBodyFeelC(observe.bodyFeelC.ifBlank { realFeelTemp.ifBlank { UNKNOWN_VALUE } })
        setBodyFeelF(observe.bodyFeelF.ifBlank { UNKNOWN_VALUE })
        setAqi(observe.aqi.ifBlank { airQuality.aqiValue })
        setPm2_5(observe.pm25.ifBlank { airQuality.pm25 })
        setPm10(observe.pm10.ifBlank { airQuality.pm10 })
        setO3(observe.o3.ifBlank { airQuality.o3 })
        setNo2(observe.no2.ifBlank { airQuality.no2 })
        setSo2(observe.so2.ifBlank { airQuality.so2 })
        setCo(observe.co.ifBlank { airQuality.co })
        setPrimary(observe.primary)
        setPubdate(observe.pubdate.ifBlank { pubdate })
        setLocalTime(observe.localTime)

        setLowTempC(observe.lowTempC.weatherValueOr { today?.lowTempC?.toString() })
        setLowTempF(observe.lowTempF.weatherValueOr { today?.lowTempF?.toString() })
        setHighTempC(observe.highTempC.weatherValueOr { today?.highTempC?.toString() })
        setHighTempF(observe.highTempF.weatherValueOr { today?.highTempF?.toString() })

        setCurSunRise(
            observe.curSunRise.toLegacySunTime(todayDate)
                ?: todaySunTimes?.first.toLegacySunTime(todayDate),
        )
        setCurSunSet(
            observe.curSunSet.toLegacySunTime(todayDate)
                ?: todaySunTimes?.second.toLegacySunTime(todayDate),
        )
        setNextSunRise(
            observe.nextSunRise.toLegacySunTime(tomorrowDate)
                ?: tomorrowSunTimes?.first.toLegacySunTime(tomorrowDate),
        )
        setNextSunSet(
            observe.nextSunSet.toLegacySunTime(tomorrowDate)
                ?: tomorrowSunTimes?.second.toLegacySunTime(tomorrowDate),
        )
        setCurrentWeekDay(
            observe.currentWeekDay.toLegacyWeekDay()
                ?: today?.weekDay.toLegacyWeekDay()
                ?: today?.date.toLegacyWeekDayFromDate(),
        )
    }

    val legacyForecast = dailyForecast.mapTo(ArrayList(dailyForecast.size)) { forecast ->
        LegacyForecastInfo().apply {
            setLocationKey(locationKey)
            setDate(forecast.date.toLegacyDateMillis())
            setWeekDay(
                forecast.weekDay.toLegacyWeekDay()
                    ?: forecast.date.toLegacyWeekDayFromDate()
                    ?: forecast.weekDay,
            )
            setWeatherCodeAm(forecast.weatherCodeAm)
            setWeatherCodePm(forecast.weatherCodePm)
            setHighCTemp(forecast.highTempC)
            setHighfTemp(forecast.highTempF)
            setLowcTemp(forecast.lowTempC)
            setLowfTemp(forecast.lowTempF)
            setSunriseAndSunset(forecast.sunriseAndSunset)
        }
    }

    val legacyHourly = hourForecast.mapTo(ArrayList(hourForecast.size)) { forecast ->
        LegacyHourForecastInfo().apply {
            code = forecast.code
            temp = forecast.temp
            night = forecast.night
            setLocationKey(locationKey)
            setWeatherCode(forecast.weatherCode)
            setTempC(forecast.tempC)
            setTempF(forecast.tempF)
            setStartTime(forecast.startTime.toLegacyDateMillis())
            setSunDes(forecast.sunDes)
        }
    }

    val legacyAlert = alert.infos.takeIf { it.isNotEmpty() }?.let { infos ->
        LegacyAlert().apply {
            setLocationKey(locationKey)
            setPublishTime(infos.maxOfOrNull { it.publishTime }?.toString())
            setmInfos(
                infos.mapTo(ArrayList(infos.size)) { info ->
                    LegacyAlertInfo().apply {
                        setTypeNumber(info.typeNumber)
                        setLevel(info.level)
                        setContent(info.content)
                        setPublishTime(info.publishTime)
                        setLevelNumber(info.levelNumber)
                        setType(info.type)
                    }
                },
            )
        }
    }

    val legacyAllergy = LegacyAllergy().apply {
        setLocationKey(locationKey)
        setAgContent(allergy.agContent)
        setAgLevel(allergy.agLevel)
        setAgTypeCn(allergy.agTypeCn)
        setUvContent(allergy.uvContent)
        setUvLevel(allergy.uvLevel)
        setUvTypeCn(allergy.uvTypeCn)
    }

    return LegacyWeather().also { legacy ->
        legacy.source = source
        legacy.observe = legacyObserve
        legacy.newForecast = legacyForecast
        legacy.hourForecast = LegacyHourForecast().apply { setmInfo(legacyHourly) }
        legacy.alert = legacyAlert
        legacy.allergy = legacyAllergy
    }
}

private fun String.weatherValueOr(fallback: () -> String?): String =
    takeUnless { it.isBlank() || it.equals(UNKNOWN_VALUE, ignoreCase = true) }
        ?: fallback()?.takeIf { it.isNotBlank() }
        ?: UNKNOWN_VALUE

/**
 * The original Views consume weekday indexes (`0` is Sunday) or a single Chinese weekday
 * character. The data layer currently also emits values such as `周四`, so normalize them here.
 */
private fun String?.toLegacyWeekDay(): String? {
    val value = this?.trim().orEmpty()
    value.toIntOrNull()?.takeIf { it in 0..6 }?.let { return it.toString() }

    val dayCharacter = value
        .removePrefix("星期")
        .removePrefix("周")
        .firstOrNull()
        ?: return null
    val index = WEEKDAY_CHARACTERS.indexOf(dayCharacter)
    return index.takeIf { it >= 0 }?.toString()
}

private fun String?.toLegacyWeekDayFromDate(): String? {
    val date = this.toDate() ?: return null
    return (Calendar.getInstance().apply { time = date }.get(Calendar.DAY_OF_WEEK) - 1).toString()
}

/** Legacy forecast rows expect an epoch-millisecond string, not the API's yyyyMMddHHmm value. */
private fun String.toLegacyDateMillis(): String = toDate()?.time?.toString() ?: this

private fun String?.toIsoDate(): String? {
    val date = this.toDate() ?: return null
    return SimpleDateFormat(ISO_DATE_PATTERN, Locale.ROOT).format(date)
}

private fun String?.toDate(): Date? {
    val value = this?.trim().orEmpty()
    if (value.isEmpty()) return null

    if (value.length == 13 && value.all(Char::isDigit)) {
        return value.toLongOrNull()?.let(::Date)
    }

    val pattern = when {
        DASHED_DATE_REGEX.matches(value) -> ISO_DATE_PATTERN
        value.length >= 12 && value.take(12).all(Char::isDigit) -> COMPACT_DATE_TIME_PATTERN
        value.length >= 8 && value.take(8).all(Char::isDigit) -> COMPACT_DATE_PATTERN
        else -> return null
    }
    val input = when (pattern) {
        COMPACT_DATE_TIME_PATTERN -> value.take(12)
        COMPACT_DATE_PATTERN -> value.take(8)
        else -> value.take(10)
    }
    return runCatching {
        SimpleDateFormat(pattern, Locale.ROOT).apply { isLenient = false }.parse(input)
    }.getOrNull()
}

private fun String?.toSunTimes(): Pair<String, String>? {
    val times = TIME_REGEX.findAll(this.orEmpty()).map { it.value }.take(2).toList()
    return if (times.size == 2) times[0] to times[1] else null
}

/** The original sunrise/sunset helpers require `yyyy-MM-dd=HH:mm`. */
private fun String?.toLegacySunTime(date: String?): String? {
    val value = this?.trim().orEmpty()
    if (value.isEmpty()) return null
    if ('=' in value) return value

    val dateTime = DATE_TIME_REGEX.find(value)
    if (dateTime != null) {
        return buildString {
            append(dateTime.groupValues[1])
            append('-')
            append(dateTime.groupValues[2])
            append('-')
            append(dateTime.groupValues[3])
            append('=')
            append(dateTime.groupValues[4].padStart(2, '0'))
            append(':')
            append(dateTime.groupValues[5])
        }
    }

    val time = TIME_REGEX.find(value)?.value ?: return null
    return date?.let { "$it=${time.padStart(5, '0')}" }
}

private const val UNKNOWN_VALUE = "UNKNOWN"
private const val WEEKDAY_CHARACTERS = "日一二三四五六"
private const val ISO_DATE_PATTERN = "yyyy-MM-dd"
private const val COMPACT_DATE_PATTERN = "yyyyMMdd"
private const val COMPACT_DATE_TIME_PATTERN = "yyyyMMddHHmm"

private val DASHED_DATE_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}.*")
private val TIME_REGEX = Regex("(?:[01]?\\d|2[0-3]):[0-5]\\d")
private val DATE_TIME_REGEX = Regex(
    "(\\d{4})-?(\\d{2})-?(\\d{2})[ T=]?(\\d{1,2}):?([0-5]\\d)",
)
