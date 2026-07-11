package com.smartisan.weather.data.weather

import com.smartisan.weather.data.model.AirQuality
import com.smartisan.weather.data.model.Allergy
import com.smartisan.weather.data.model.AlertInfo
import com.smartisan.weather.data.model.DailyForecast
import com.smartisan.weather.data.model.HourForecast
import com.smartisan.weather.data.model.Observe
import com.smartisan.weather.data.model.SearchResultCity
import com.smartisan.weather.data.model.Weather
import com.smartisan.weather.data.model.WeatherAlert
import com.smartisan.weather.util.ResMappingUtil
import com.smartisan.weather.util.WeatherCodeMapping
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/** Pure parser and protocol mapping for Xiaomi's China weather responses. */
internal object XiaomiWeatherParser {

    private val apiOffset: ZoneOffset = ZoneOffset.ofHours(8)
    private val compactDateTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuuMMddHHmm", Locale.ROOT)
    private val dayNames = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

    fun parseWeather(json: String): Weather? {
        return runCatching {
            val root = JSONObject(json)
            val current = root.optJSONObject("current") ?: return null
            val currentPublishTime = parseApiDateTime(current.text("pubTime"))
            val parsedDaily = parseDaily(
                root.optJSONObject("forecastDaily"),
                currentPublishTime,
            )
            val airQuality = parseAirQuality(root.optJSONObject("aqi"))
            val observe = parseObserve(
                root = root,
                current = current,
                currentPublishTime = currentPublishTime,
                daily = parsedDaily.forecasts,
                airQuality = airQuality,
            )
            val hourly = parseHourly(
                root.optJSONObject("forecastHourly"),
                currentPublishTime,
                parsedDaily.sunTimes,
            )
            val alerts = parseAlerts(root.optJSONArray("alerts"))
            val uvIndex = current.text("uvIndex").ifBlank { parseIndex(root, "uvIndex") }
            val source = parseSource(root, airQuality)

            Weather(
                observe = observe,
                dailyForecast = parsedDaily.forecasts,
                hourForecast = hourly,
                alert = WeatherAlert(alerts),
                allergy = Allergy(uvLevel = uvIndex),
                airQuality = airQuality,
                source = source,
                weatherCode = observe.code,
                temp = observe.tempC,
                windDirection = observe.wind,
                windSpeed = observe.speed,
                relativeHumidity = observe.humidity,
                realFeelTemp = observe.bodyFeelC,
                pubdate = observe.pubdate,
            )
        }.getOrNull()
    }

    /** Search and geo endpoints share the same top-level array shape. */
    fun parseLocationResults(json: String): List<SearchResultCity> {
        val results = JSONArray(json)
        return buildList(results.length()) {
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                if (item.optInt("status", -1) != 0) continue

                val locationKey = item.text("locationKey").ifBlank { item.text("key") }
                if (!locationKey.startsWith(WEATHERCN_PREFIX)) continue
                val cityId = locationKey.removePrefix(WEATHERCN_PREFIX)
                if (!cityId.matches(CHINA_CITY_ID)) continue

                val name = conciseAdministrativeName(item.text("name"))
                if (name.isBlank()) continue
                val hierarchy = parseAffiliation(name, item.text("affiliation"))
                add(
                    SearchResultCity(
                        cityId = cityId,
                        county = name,
                        city = hierarchy.city,
                        province = hierarchy.province,
                        country = hierarchy.country,
                        id = cityId,
                    ),
                )
            }
        }.distinctBy(SearchResultCity::cityId)
    }

    /**
     * Xiaomi and the restored Smartisan views largely follow the same China weather code family,
     * but mapping remains semantic rather than numeric (`20` means different dust conditions).
     * Unsupported extension codes deliberately become unknown instead of borrowing a visually
     * incorrect condition.
     */
    fun toSmartisanWeatherCode(rawCode: String?): String {
        val value = rawCode?.trim()?.toIntOrNull() ?: return UNKNOWN_WEATHER_CODE
        return when {
            value in 0..19 -> value.toString().padStart(2, '0')
            value == 20 -> "31" // 小米 20=沙尘暴；原 View 的 31 才是沙尘暴
            value in 21..31 -> value.toString()
            value == 35 -> "18" // 轻雾 → 雾
            value == 49 || value in 53..58 -> value.toString()
            value == 99 -> UNKNOWN_WEATHER_CODE
            else -> UNKNOWN_WEATHER_CODE
        }
    }

    /** Degrees clockwise from north → original Smartisan wind direction code. */
    fun windDirectionCode(rawDegrees: String?): String {
        val degrees = rawDegrees?.trim()?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it in 0.0..360.0 }
            ?: return "0"
        return when {
            degrees >= 337.5 || degrees < 22.5 -> "8"
            degrees < 67.5 -> "1"
            degrees < 112.5 -> "2"
            degrees < 157.5 -> "3"
            degrees < 202.5 -> "4"
            degrees < 247.5 -> "5"
            degrees < 292.5 -> "6"
            else -> "7"
        }
    }

    /** km/h → Beaufort level; the restored View adds the localized “level” suffix itself. */
    fun beaufortLevel(rawKilometersPerHour: String?): String {
        val speed = rawKilometersPerHour?.trim()?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?: return ""
        return when {
            speed < 1.0 -> "0"
            speed < 6.0 -> "1"
            speed < 12.0 -> "2"
            speed < 20.0 -> "3"
            speed < 29.0 -> "4"
            speed < 39.0 -> "5"
            speed < 50.0 -> "6"
            speed < 62.0 -> "7"
            speed < 75.0 -> "8"
            speed < 89.0 -> "9"
            speed < 103.0 -> "10"
            speed < 118.0 -> "11"
            else -> "12"
        }
    }

    private fun parseObserve(
        root: JSONObject,
        current: JSONObject,
        currentPublishTime: OffsetDateTime?,
        daily: List<DailyForecast>,
        airQuality: AirQuality,
    ): Observe {
        val temperature = current.measureInt("temperature")
        val feelsLike = current.measureInt("feelsLike")
        val currentWind = current.optJSONObject("wind")
        val direction = currentWind?.optJSONObject("direction")?.text("value")
        val speed = currentWind?.optJSONObject("speed")?.text("value")
        val updateInstant = root.text("updateTime").toLongOrNull()
            ?.takeIf { it > 0L }
            ?.let(Instant::ofEpochMilli)
        val publishedAt = currentPublishTime?.toInstant() ?: updateInstant
        val localDate = currentPublishTime?.toLocalDate()
            ?: updateInstant?.atOffset(apiOffset)?.toLocalDate()
        val localDayStart = localDate
            ?.atStartOfDay()
            ?.toInstant(currentPublishTime?.offset ?: apiOffset)
            ?.toEpochMilli()
            ?.toString()
            .orEmpty()
        val today = localDate?.let { date -> daily.forecastFor(date) } ?: daily.firstOrNull()
        val tomorrow = localDate?.plusDays(1)?.let { date -> daily.forecastFor(date) }
        val todaySun = today?.sunriseAndSunset.toSunTimes()
        val tomorrowSun = tomorrow?.sunriseAndSunset.toSunTimes()

        return Observe(
            tempC = temperature?.toString() ?: UNKNOWN_TEMPERATURE,
            tempF = temperature?.let(WeatherCodeMapping::celsiusToFahrenheit)?.toString()
                ?: UNKNOWN_TEMPERATURE,
            code = toSmartisanWeatherCode(current.text("weather")),
            wind = windDirectionCode(direction),
            speed = beaufortLevel(speed),
            humidity = current.measureText("humidity"),
            compareC = UNKNOWN_TEMPERATURE,
            compareF = UNKNOWN_TEMPERATURE,
            bodyFeelC = feelsLike?.toString() ?: UNKNOWN_TEMPERATURE,
            bodyFeelF = feelsLike?.let(WeatherCodeMapping::celsiusToFahrenheit)?.toString()
                ?: UNKNOWN_TEMPERATURE,
            aqi = airQuality.aqiValue,
            pm25 = airQuality.pm25,
            pm10 = airQuality.pm10,
            o3 = airQuality.o3,
            no2 = airQuality.no2,
            so2 = airQuality.so2,
            co = airQuality.co,
            primary = airQuality.primary,
            pubdate = publishedAt?.toEpochMilli()?.toString().orEmpty(),
            localTime = localDayStart,
            curSunRise = todaySun?.first.orEmpty(),
            curSunSet = todaySun?.second.orEmpty(),
            nextSunRise = tomorrowSun?.first.orEmpty(),
            nextSunSet = tomorrowSun?.second.orEmpty(),
            lowTempC = today?.lowTempC?.toString().orEmpty(),
            lowTempF = today?.lowTempF?.toString().orEmpty(),
            highTempC = today?.highTempC?.toString().orEmpty(),
            highTempF = today?.highTempF?.toString().orEmpty(),
            currentWeekDay = today?.weekDay.orEmpty(),
        )
    }

    private fun parseDaily(
        daily: JSONObject?,
        currentPublishTime: OffsetDateTime?,
    ): ParsedDaily {
        if (daily == null || !daily.hasSuccessfulStatus()) return ParsedDaily()
        val weatherNode = daily.optJSONObject("weather")?.takeIf { it.hasSuccessfulStatus() }
        val temperatureNode = daily.optJSONObject("temperature")?.takeIf { it.hasSuccessfulStatus() }
        val weatherValues = weatherNode?.optJSONArray("value") ?: return ParsedDaily()
        val temperatureValues = temperatureNode?.optJSONArray("value") ?: return ParsedDaily()
        val sunValues = daily.optJSONObject("sunRiseSet")
            ?.takeIf { it.hasSuccessfulStatus() }
            ?.optJSONArray("value")
        val baseDate = parseApiDateTime(daily.text("pubTime"))?.toLocalDate()
            ?: currentPublishTime?.toLocalDate()
            ?: return ParsedDaily()
        val count = minOf(weatherValues.length(), temperatureValues.length())
        val forecasts = ArrayList<DailyForecast>(count)
        val sunTimes = LinkedHashMap<LocalDate, SunTimes>(count)

        for (index in 0 until count) {
            val weather = weatherValues.optJSONObject(index) ?: continue
            val temperature = temperatureValues.optJSONObject(index) ?: continue
            val high = temperature.text("from").toWeatherInt() ?: continue
            val low = temperature.text("to").toWeatherInt() ?: continue
            val date = baseDate.plusDays(index.toLong())
            val codeAm = toSmartisanWeatherCode(weather.text("from"))
            val codePm = weather.text("to")
                .takeIf(String::isNotBlank)
                ?.let(::toSmartisanWeatherCode)
                ?: codeAm
            val sun = parseSunTimes(sunValues?.optJSONObject(index))
            if (sun != null) sunTimes[date] = sun

            forecasts += DailyForecast(
                date = date.format(COMPACT_DATE) + "0000",
                weekDay = dayNames[date.dayOfWeek.value % dayNames.size],
                weatherCodeAm = codeAm,
                weatherCodePm = codePm,
                highTempC = high,
                highTempF = WeatherCodeMapping.celsiusToFahrenheit(high),
                lowTempC = low,
                lowTempF = WeatherCodeMapping.celsiusToFahrenheit(low),
                sunriseAndSunset = sun?.let {
                    "${it.sunrise.format(CLOCK_TIME)}|${it.sunset.format(CLOCK_TIME)}"
                }.orEmpty(),
            )
        }
        return ParsedDaily(forecasts, sunTimes)
    }

    private fun parseHourly(
        hourly: JSONObject?,
        currentPublishTime: OffsetDateTime?,
        sunTimes: Map<LocalDate, SunTimes>,
    ): List<HourForecast> {
        if (hourly == null || !hourly.hasSuccessfulStatus()) return emptyList()
        val weatherNode = hourly.optJSONObject("weather")?.takeIf { it.hasSuccessfulStatus() }
        val temperatureNode = hourly.optJSONObject("temperature")?.takeIf { it.hasSuccessfulStatus() }
        val weatherValues = weatherNode?.optJSONArray("value") ?: return emptyList()
        val temperatureValues = temperatureNode?.optJSONArray("value") ?: return emptyList()
        val windValues = hourly.optJSONObject("wind")
            ?.takeIf { it.hasSuccessfulStatus() }
            ?.optJSONArray("value")
        val baseTime = parseApiDateTime(temperatureNode.text("pubTime"))
            ?: parseApiDateTime(weatherNode.text("pubTime"))
            ?: currentPublishTime
            ?: return emptyList()
        val count = minOf(weatherValues.length(), temperatureValues.length())
        val forecasts = ArrayList<HourForecast>(count)

        for (index in 0 until count) {
            val temperature = temperatureValues.opt(index).jsonText().toWeatherInt() ?: continue
            val code = toSmartisanWeatherCode(weatherValues.opt(index).jsonText())
            val windTime = windValues?.optJSONObject(index)?.text("datetime")
                ?.let(::parseApiDateTime)
            val time = windTime ?: baseTime.plusHours(index.toLong())
            val localTime = time.toLocalTime()
            val sun = sunTimes[time.toLocalDate()]
            val isNight = sun != null &&
                (localTime.isBefore(sun.sunrise) || !localTime.isBefore(sun.sunset))

            forecasts += HourForecast(
                code = code,
                weatherCode = code,
                temp = temperature.toString(),
                tempC = temperature,
                tempF = WeatherCodeMapping.celsiusToFahrenheit(temperature),
                startTime = time.format(compactDateTime),
                night = isNight,
            )
        }
        return forecasts
    }

    private fun parseAirQuality(aqi: JSONObject?): AirQuality {
        if (aqi == null || !aqi.hasSuccessfulStatus()) return AirQuality()
        return AirQuality(
            aqiValue = aqi.text("aqi"),
            primary = aqi.text("primary"),
            pm25 = aqi.text("pm25"),
            pm10 = aqi.text("pm10"),
            o3 = aqi.text("o3"),
            no2 = aqi.text("no2"),
            so2 = aqi.text("so2"),
            co = aqi.text("co"),
            publishTime = aqi.text("pubTime"),
        )
    }

    private fun parseAlerts(alerts: JSONArray?): List<AlertInfo> {
        if (alerts == null) return emptyList()
        return buildList(alerts.length()) {
            for (index in 0 until alerts.length()) {
                val alert = alerts.optJSONObject(index) ?: continue
                val detail = alert.text("detail").ifBlank { alert.text("title") }
                if (detail.isBlank()) continue
                add(
                    AlertInfo(
                        level = alert.text("level"),
                        content = detail,
                        publishTime = parseApiDateTime(alert.text("pubTime"))
                            ?.toInstant()
                            ?.toEpochMilli()
                            ?: 0L,
                        levelNumber = alertLevelNumber(alert.text("level")),
                        type = alert.text("type"),
                    ),
                )
            }
        }.sortedByDescending(AlertInfo::publishTime)
    }

    private fun parseSource(root: JSONObject, airQuality: AirQuality): String {
        val sources = linkedSetOf("小米天气")
        root.optJSONObject("brandInfo")
            ?.optJSONArray("brands")
            ?.let { brands ->
                for (index in 0 until brands.length()) {
                    brands.optJSONObject(index)
                        ?.optJSONObject("names")
                        ?.text("zh_CN")
                        ?.takeIf(String::isNotBlank)
                        ?.let(sources::add)
                }
            }
        if (airQuality.aqiValue.isNotBlank()) {
            root.optJSONObject("aqi")?.text("src")
                ?.takeIf(String::isNotBlank)
                ?.let(sources::add)
        }
        return sources.joinToString("、")
    }

    private fun parseIndex(root: JSONObject, type: String): String {
        val indices = root.optJSONObject("indices")?.optJSONArray("indices") ?: return ""
        for (index in 0 until indices.length()) {
            val item = indices.optJSONObject(index) ?: continue
            if (item.text("type") == type) return item.text("value")
        }
        return ""
    }

    private fun parseSunTimes(value: JSONObject?): SunTimes? {
        val sunrise = parseApiDateTime(value?.text("from").orEmpty())?.toLocalTime()
            ?: return null
        val sunset = parseApiDateTime(value?.text("to").orEmpty())?.toLocalTime()
            ?: return null
        return SunTimes(sunrise, sunset)
    }

    private fun parseAffiliation(name: String, affiliation: String): LocationHierarchy {
        val parents = affiliation.split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .map(::conciseAdministrativeName)
        val country = parents.lastOrNull().orEmpty().ifBlank { "中国" }
        val administrativeParents = parents.dropLast(1)
        if (administrativeParents.isEmpty()) {
            val province = name.takeIf { it in DIRECT_CONTROLLED_MUNICIPALITIES }.orEmpty()
            return LocationHierarchy(city = name, province = province, country = country)
        }

        val immediateParent = administrativeParents.first()
        val province = administrativeParents.last()
        val city = when {
            immediateParent in DIRECT_CONTROLLED_MUNICIPALITIES -> immediateParent
            administrativeParents.size >= 2 -> immediateParent
            else -> name
        }
        return LocationHierarchy(city = city, province = province, country = country)
    }

    private fun conciseAdministrativeName(rawName: String): String {
        val name = rawName.trim()
        val explicitlyMapped = ResMappingUtil.getCitySimpleName(name).orEmpty()
        if (explicitlyMapped.isNotBlank() && explicitlyMapped != name) return explicitlyMapped
        return when {
            name.length > 2 && name.endsWith("省") -> name.dropLast(1)
            name.length > 2 && name.endsWith("市") -> name.dropLast(1)
            name.length > 2 && name.endsWith("县") -> name.dropLast(1)
            name.length > 2 && name.endsWith("区") && !name.endsWith("特区") -> name.dropLast(1)
            else -> name
        }
    }

    private fun alertLevelNumber(level: String): String = when (level.removeSuffix("色")) {
        "蓝" -> "01"
        "黄" -> "02"
        "橙", "橘", "橘黄" -> "03"
        "红" -> "04"
        else -> "05"
    }

    private fun parseApiDateTime(value: String): OffsetDateTime? {
        if (value.isBlank()) return null
        return runCatching { OffsetDateTime.parse(value) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value).atOffset(apiOffset) }.getOrNull()
    }

    private fun JSONObject.measureText(name: String): String =
        optJSONObject(name)?.text("value").orEmpty()

    private fun JSONObject.measureInt(name: String): Int? = measureText(name).toWeatherInt()

    private fun JSONObject.text(name: String): String = opt(name).jsonText()

    private fun JSONObject.hasSuccessfulStatus(): Boolean =
        !has("status") || isNull("status") || optInt("status", -1) == 0

    private fun Any?.jsonText(): String = when (this) {
        null, JSONObject.NULL -> ""
        is String -> trim()
        else -> toString().trim()
    }

    private fun String.toWeatherInt(): Int? =
        toDoubleOrNull()?.takeIf(Double::isFinite)?.roundToInt()

    private fun String?.toSunTimes(): Pair<String, String>? {
        val values = this?.split('|').orEmpty()
        return if (values.size == 2 && values.all(String::isNotBlank)) {
            values[0] to values[1]
        } else {
            null
        }
    }

    private fun List<DailyForecast>.forecastFor(date: LocalDate): DailyForecast? {
        val compactDate = date.format(COMPACT_DATE)
        return firstOrNull { it.date.startsWith(compactDate) }
    }

    private data class ParsedDaily(
        val forecasts: List<DailyForecast> = emptyList(),
        val sunTimes: Map<LocalDate, SunTimes> = emptyMap(),
    )

    private data class SunTimes(
        val sunrise: LocalTime,
        val sunset: LocalTime,
    )

    private data class LocationHierarchy(
        val city: String,
        val province: String,
        val country: String,
    )

    private const val WEATHERCN_PREFIX = "weathercn:"
    private const val UNKNOWN_TEMPERATURE = "UNKNOWN"
    private const val UNKNOWN_WEATHER_CODE = "99"
    private val CHINA_CITY_ID = Regex("\\d{9}")
    private val COMPACT_DATE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
    private val CLOCK_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private val DIRECT_CONTROLLED_MUNICIPALITIES = setOf("北京", "上海", "天津", "重庆")
}
