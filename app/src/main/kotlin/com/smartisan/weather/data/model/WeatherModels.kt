package com.smartisan.weather.data.model

import java.io.Serializable
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * 天气预警详情。
 *
 * @param typeNumber 预警类型编号
 * @param level 预警等级文本
 * @param content 预警内容
 * @param publishTime 发布时间（epoch 毫秒）
 * @param levelNumber 预警等级编号
 * @param type 预警类型文本
 */
data class AlertInfo(
    val typeNumber: String = "",
    val level: String = "",
    val content: String = "",
    val publishTime: Long = 0L,
    val levelNumber: String = "",
    val type: String = "",
) : Serializable

/**
 * 预警集合，包含一个城市的所有预警信息。
 */
data class WeatherAlert(
    val infos: List<AlertInfo> = emptyList(),
) : Serializable {
    val isEmpty: Boolean get() = infos.isEmpty()
    val first: AlertInfo? get() = infos.firstOrNull()
}

/** 兼容原 View 的生活指数模型；小米天气源只填充紫外线字段。 */
data class Allergy(
    val agContent: String = "",
    val agLevel: String = "",
    val agTypeCn: String = "",
    val uvContent: String = "",
    val uvLevel: String = "",
    val uvTypeCn: String = "",
) : Serializable

/**
 * 当前天气观测数据。
 */
data class Observe(
    val tempC: String = "UNKNOWN",
    val tempF: String = "UNKNOWN",
    val code: String = "",
    val wind: String = "",
    val speed: String = "",
    val humidity: String = "",
    val compareC: String = "UNKNOWN",
    val compareF: String = "UNKNOWN",
    val bodyFeelC: String = "UNKNOWN",
    val bodyFeelF: String = "UNKNOWN",
    val aqi: String = "",
    val pm25: String = "",
    val pm10: String = "",
    val o3: String = "",
    val no2: String = "",
    val so2: String = "",
    val co: String = "",
    val primary: String = "",
    val pubdate: String = "",
    val localTime: String = "",
    val curSunRise: String = "",
    val curSunSet: String = "",
    val nextSunRise: String = "",
    val nextSunSet: String = "",
    val lowTempC: String = "",
    val lowTempF: String = "",
    val highTempC: String = "",
    val highTempF: String = "",
    val currentWeekDay: String = "",
) : Serializable {
    val isComplete: Boolean
        get() = tempC != "UNKNOWN" && tempF != "UNKNOWN"
}

/**
 * 每日天气预报（AM/PM 分裂模式）。
 */
data class DailyForecast(
    val date: String = "",
    val weekDay: String = "",
    val weatherCodeAm: String = "",
    val weatherCodePm: String = "",
    val highTempC: Int = 0,
    val highTempF: Int = 0,
    val lowTempC: Int = 0,
    val lowTempF: Int = 0,
    val sunriseAndSunset: String = "",
) : Serializable

/**
 * 逐小时天气预报。
 */
data class HourForecast(
    val code: String = "",
    val weatherCode: String = "",
    val temp: String = "",
    val tempC: Int = -1,
    val tempF: Int = -1,
    val startTime: String = "",
    val night: Boolean = false,
    val sunDes: String = "",
) : Serializable {
    /** 从 startTime(yyyyMMddHHmm) 提取小时 */
    val hour: Int
        get() = runCatching { startTime.substring(8, 10).toInt() }.getOrDefault(-1)

    /** 从 startTime(yyyyMMddHHmm) 提取日 */
    val day: Int
        get() = runCatching { startTime.substring(6, 8).toInt() }.getOrDefault(-1)
}

/**
 * 空气质量指数。
 */
data class AirQuality(
    val aqiValue: String = "",
    val primary: String = "",
    val pm25: String = "",
    val pm10: String = "",
    val o3: String = "",
    val no2: String = "",
    val so2: String = "",
    val co: String = "",
    val publishTime: String = "",
) : Serializable {
    val aqiInt: Int
        get() = runCatching { aqiValue.trim().toInt() }.getOrDefault(-1)
}

/**
 * 完整天气数据——一个城市一次请求的全部结果。
 */
data class Weather(
    val observe: Observe = Observe(),
    val dailyForecast: List<DailyForecast> = emptyList(),
    val hourForecast: List<HourForecast> = emptyList(),
    val alert: WeatherAlert = WeatherAlert(),
    val allergy: Allergy = Allergy(),
    val airQuality: AirQuality = AirQuality(),
    val source: String = "",
    val weatherCode: String = "",
    val temp: String = "",
    val windDirection: String = "",
    val windSpeed: String = "",
    val relativeHumidity: String = "",
    val compareC: Int = 0,
    val compareF: Int = 0,
    val realFeelTemp: String = "",
    val pubdate: String = "",
    /** 城市相对 UTC 的秒偏移，用于按城市本地时间显示更新时间和昼夜状态。 */
    val timezoneOffsetSeconds: Int = DEFAULT_TIMEZONE_OFFSET_SECONDS,
    /** 数据提供方返回的归因页面；为空时由 UI 使用项目默认说明页。 */
    val attributionUrl: String = "",
) : Serializable {
    val isComplete: Boolean
        get() = observe.isComplete

    val isEmpty: Boolean
        get() = !observe.isComplete

    /** 用于主题判断的天气代码 */
    val themeCode: String
        get() = observe.code.ifEmpty { weatherCode }

    val zoneOffset: ZoneOffset
        get() = runCatching { ZoneOffset.ofTotalSeconds(timezoneOffsetSeconds) }
            .getOrDefault(DEFAULT_ZONE_OFFSET)

    fun localTimeAt(instant: Instant): LocalTime = instant.atOffset(zoneOffset).toLocalTime()
}

/**
 * 搜索到的城市信息。
 */
data class SearchResultCity(
    val cityId: String = "",
    val county: String = "",
    val city: String = "",
    val province: String = "",
    val country: String = "",
    val countyEn: String = "",
    val countyPinyin: String = "",
    val id: String = "",
) : Serializable {
    val isGlobal: Boolean
        get() = cityId.startsWith(GLOBAL_LOCATION_KEY_PREFIX)

    /**
     * 保存后用于主页面标题的上级区域。全球同名城市优先显示州/省或国家，
     * 中国区仍保持原版的“区县 - 城市”层级。
     */
    val displayParentName: String
        get() {
            if (!isGlobal) return city.ifBlank { province }
            return listOf(city, province, country)
                .firstOrNull { candidate ->
                    candidate.isNotBlank() && !candidate.equals(county, ignoreCase = true)
                }
                .orEmpty()
        }

    /** 搜索结果中用于区分全球同名城市的紧凑地域说明。 */
    val searchContext: String
        get() {
            val candidates = if (isGlobal) {
                listOf(city, province, country)
            } else {
                listOf(city, province)
            }
            val unique = mutableListOf<String>()
            candidates.forEach { candidate ->
                if (
                    candidate.isNotBlank() &&
                    !candidate.equals(county, ignoreCase = true) &&
                    unique.none { it.equals(candidate, ignoreCase = true) }
                ) {
                    unique += candidate
                }
            }
            return if (isGlobal) unique.joinToString(", ") else unique.firstOrNull().orEmpty()
        }
}

/**
 * 热门城市。
 */
data class HotCity(
    val cityId: String,
    val city: String,
    val county: String,
    val province: String,
    val country: String,
) : Serializable

/**
 * 已保存的城市（含排序）。
 */
data class SavedCity(
    val id: Int = 0,
    val locationKey: String = "",
    val locationName: String = "",
    val locationParentName: String = "",
    val country: String = "",
    val province: String = "",
    val sortOrder: Int = 0,
) : Serializable {
    val isLocationCity: Boolean get() = sortOrder == 1

    /** 完整显示名，如 "北京, 中国" */
    val displayFullName: String
        get() = listOf(locationName, locationParentName, country)
            .filter { it.isNotBlank() }
            .joinToString(", ")

    /** 简短显示名 */
    val displayName: String
        get() = locationName.ifBlank { locationParentName }
}

private const val GLOBAL_LOCATION_KEY_PREFIX = "accu:"
private const val DEFAULT_TIMEZONE_OFFSET_SECONDS = 8 * 60 * 60
private val DEFAULT_ZONE_OFFSET: ZoneOffset = ZoneOffset.ofHours(8)
