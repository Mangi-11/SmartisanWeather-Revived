package com.smartisan.weather.util

import com.smartisan.weather.R

/**
 * 天气代码 → 图标、主题、文字的映射表。
 *
 * 完整复刻自原版 ResMappingUtil.java 和 ThemeUtils.java。
 */
object WeatherCodeMapping {

    /** 天气代码 → 文字资源 ID */
    val textResMap: Map<String, Int> = mapOf(
        "00" to R.string.weather_text_00,
        "01" to R.string.weather_text_01,
        "02" to R.string.weather_text_02,
        "03" to R.string.weather_text_03,
        "04" to R.string.weather_text_04,
        "05" to R.string.weather_text_05,
        "06" to R.string.weather_text_06,
        "07" to R.string.weather_text_07,
        "08" to R.string.weather_text_08,
        "09" to R.string.weather_text_09,
        "10" to R.string.weather_text_10,
        "11" to R.string.weather_text_11,
        "12" to R.string.weather_text_12,
        "13" to R.string.weather_text_13,
        "14" to R.string.weather_text_14,
        "15" to R.string.weather_text_15,
        "16" to R.string.weather_text_16,
        "17" to R.string.weather_text_17,
        "18" to R.string.weather_text_18,
        "19" to R.string.weather_text_19,
        "20" to R.string.weather_text_20,
        "21" to R.string.weather_text_21,
        "22" to R.string.weather_text_22,
        "23" to R.string.weather_text_23,
        "24" to R.string.weather_text_24,
        "25" to R.string.weather_text_25,
        "26" to R.string.weather_text_26,
        "27" to R.string.weather_text_27,
        "28" to R.string.weather_text_28,
        "29" to R.string.weather_text_29,
        "30" to R.string.weather_text_30,
        "31" to R.string.weather_text_31,
        "32" to R.string.weather_text_32,
        "49" to R.string.weather_text_49,
        "53" to R.string.weather_text_53,
        "54" to R.string.weather_text_54,
        "55" to R.string.weather_text_55,
        "56" to R.string.weather_text_56,
        "57" to R.string.weather_text_57,
        "58" to R.string.weather_text_58,
        "99" to R.string.weather_text_99,
        "1000" to R.string.weather_text_1000,
        "1001" to R.string.weather_text_1001,
    )

    /** 天气代码 → 主题 ID（晴/多云/阴/雨/雪/雾/霾/沙尘） */
    private val themeMap: Map<String, String> = mapOf(
        "00" to "00", "01" to "01", "02" to "02", "03" to "03",
        "04" to "03", "05" to "03", "06" to "03", "07" to "03",
        "08" to "03", "09" to "03", "10" to "03", "11" to "03",
        "12" to "03", "19" to "03", "21" to "03", "22" to "03",
        "23" to "03", "24" to "03", "25" to "03",
        "13" to "04", "14" to "04", "15" to "04", "16" to "04",
        "17" to "04", "26" to "04", "27" to "04", "28" to "04",
        "18" to "05", "32" to "05", "49" to "05", "57" to "05", "58" to "05",
        "53" to "06", "54" to "06", "55" to "06", "56" to "06",
        "20" to "07", "29" to "07", "30" to "07", "31" to "07",
        "99" to "00",
    )

    /** 风向代码 → 文字资源 ID */
    val windDirResMap: Map<String, Int> = mapOf(
        "0" to R.string.wind_dir_0,
        "1" to R.string.wind_dir_1,
        "2" to R.string.wind_dir_2,
        "3" to R.string.wind_dir_3,
        "4" to R.string.wind_dir_4,
        "5" to R.string.wind_dir_5,
        "6" to R.string.wind_dir_6,
        "7" to R.string.wind_dir_7,
        "8" to R.string.wind_dir_8,
        "9" to R.string.wind_dir_9,
    )

    /** 天气主题定义 */
    enum class WeatherTheme(
        val themeId: String,
        val bgDrawable: Int,
        val aqiTextColor: Int,
    ) {
        DEFAULT("000", R.drawable.drawable_weather_bg_sunny, R.color.weather_aqi_des_sunny_text_color),
        SUNNY("00", R.drawable.drawable_weather_bg_sunny, R.color.weather_aqi_des_sunny_text_color),
        CLOUDY("01", R.drawable.drawable_weather_bg_sunny, R.color.weather_aqi_des_sunny_text_color),
        OVERCAST("02", R.drawable.drawable_weather_bg_rain, R.color.weather_aqi_des_rain_text_color),
        RAIN("03", R.drawable.drawable_weather_bg_rain, R.color.weather_aqi_des_rain_text_color),
        SNOW("04", R.drawable.drawable_weather_bg_snow, R.color.weather_aqi_des_snow_text_color),
        FOGGY("05", R.drawable.drawable_weather_bg_foggy, R.color.weather_aqi_des_foggy_text_color),
        HAZE("06", R.drawable.drawable_weather_bg_haze, R.color.weather_aqi_des_haze_text_color),
        SANDSTORM("07", R.drawable.drawable_weather_bg_sandstorm, R.color.weather_aqi_des_sandstorm_text_color);

        companion object {
            private val idMap = entries.associateBy { it.themeId }
            fun fromId(id: String): WeatherTheme = idMap[id] ?: DEFAULT
        }
    }

    /** 根据天气代码获取主题 */
    fun getTheme(code: String?): WeatherTheme {
        val safeCode = code?.takeIf { it.isNotBlank() } ?: return WeatherTheme.DEFAULT
        val themeId = themeMap[safeCode] ?: return WeatherTheme.DEFAULT
        return WeatherTheme.fromId(themeId)
    }

    /** 天气代码 → 图标 drawable resource ID */
    data class WeatherIconSet(
        val dayIcon: Int,
        val nightIcon: Int,
        val shadowIcon: Int,
        val nightShadowIcon: Int,
    )

    private val iconMap: Map<String, WeatherIconSet> = mapOf(
        "00" to WeatherIconSet(R.drawable.little_icon_sunny, R.drawable.little_icon_sunny_night, R.drawable.little_icon_sunny_shadow, R.drawable.little_icon_sunny_night_shadow),
        "01" to WeatherIconSet(R.drawable.little_icon_cloudy, R.drawable.little_icon_cloudy_night, R.drawable.little_icon_cloudy_shadow, R.drawable.little_icon_cloudy_night_shadow),
        "02" to WeatherIconSet(R.drawable.little_icon_overcast, R.drawable.little_icon_overcast, R.drawable.little_icon_overcast_shadow, -1),
        "03" to WeatherIconSet(R.drawable.little_icon_shower, R.drawable.little_icon_shower, R.drawable.little_icon_shower_shadow, -1),
        "04" to WeatherIconSet(R.drawable.little_icon_thundershower, R.drawable.little_icon_thundershower_night, R.drawable.little_icon_thundershower_shadow, R.drawable.little_icon_thundershower_night_shadow),
        "05" to WeatherIconSet(R.drawable.little_icon_thundershowerhail, R.drawable.little_icon_thundershowerhail_night, R.drawable.little_icon_thundershowerhail_shadow, R.drawable.little_icon_thundershowerhail_night_shadow),
        "06" to WeatherIconSet(R.drawable.little_icon_icerain, R.drawable.little_icon_icerain, R.drawable.little_icon_icerain_shadow, -1),
        "07" to WeatherIconSet(R.drawable.little_icon_lightrain, R.drawable.little_icon_lightrain, R.drawable.little_icon_lightrain_shadow, -1),
        "08" to WeatherIconSet(R.drawable.little_icon_moderaterain, R.drawable.little_icon_moderaterain, R.drawable.little_icon_moderaterain_shadow, -1),
        "09" to WeatherIconSet(R.drawable.little_icon_heavyrain, R.drawable.little_icon_heavyrain, R.drawable.little_icon_heavyrain_shadow, -1),
        "10" to WeatherIconSet(R.drawable.little_icon_storm, R.drawable.little_icon_storm, R.drawable.little_icon_storm_shadow, -1),
        "11" to WeatherIconSet(R.drawable.little_icon_storm, R.drawable.little_icon_storm, R.drawable.little_icon_storm_shadow, -1),
        "12" to WeatherIconSet(R.drawable.little_icon_storm, R.drawable.little_icon_storm, R.drawable.little_icon_storm_shadow, -1),
        "13" to WeatherIconSet(R.drawable.little_icon_snow, R.drawable.little_icon_snow_night, R.drawable.little_icon_snow_shadow, R.drawable.little_icon_snow_night_shadow),
        "14" to WeatherIconSet(R.drawable.little_icon_lightsnow, R.drawable.little_icon_lightsnow, R.drawable.little_icon_lightsnow_shadow, -1),
        "15" to WeatherIconSet(R.drawable.little_icon_moderatesnow, R.drawable.little_icon_moderatesnow, R.drawable.little_icon_moderatesnow_shadow, -1),
        "16" to WeatherIconSet(R.drawable.little_icon_heavysnow, R.drawable.little_icon_heavysnow, R.drawable.little_icon_heavysnow_shadow, -1),
        "17" to WeatherIconSet(R.drawable.little_icon_heavysnow, R.drawable.little_icon_heavysnow, R.drawable.little_icon_heavysnow_shadow, -1),
        "18" to WeatherIconSet(R.drawable.little_icon_foggy, R.drawable.little_icon_foggy, R.drawable.little_icon_foggy_shadow, -1),
        "19" to WeatherIconSet(R.drawable.little_icon_icerain, R.drawable.little_icon_icerain, R.drawable.little_icon_icerain_shadow, -1),
        "20" to WeatherIconSet(R.drawable.little_icon_sandstorm, R.drawable.little_icon_sandstorm, R.drawable.little_icon_sandstorm_shadow, -1),
        "21" to WeatherIconSet(R.drawable.little_icon_moderaterain, R.drawable.little_icon_moderaterain, R.drawable.little_icon_moderaterain_shadow, -1),
        "22" to WeatherIconSet(R.drawable.little_icon_heavyrain, R.drawable.little_icon_heavyrain, R.drawable.little_icon_heavyrain_shadow, -1),
        "23" to WeatherIconSet(R.drawable.little_icon_storm, R.drawable.little_icon_storm, R.drawable.little_icon_storm_shadow, -1),
        "24" to WeatherIconSet(R.drawable.little_icon_storm, R.drawable.little_icon_storm, R.drawable.little_icon_storm_shadow, -1),
        "25" to WeatherIconSet(R.drawable.little_icon_storm, R.drawable.little_icon_storm, R.drawable.little_icon_storm_shadow, -1),
        "26" to WeatherIconSet(R.drawable.little_icon_moderatesnow, R.drawable.little_icon_moderatesnow, R.drawable.little_icon_moderatesnow_shadow, -1),
        "27" to WeatherIconSet(R.drawable.little_icon_heavysnow, R.drawable.little_icon_heavysnow, R.drawable.little_icon_heavysnow_shadow, -1),
        "28" to WeatherIconSet(R.drawable.little_icon_heavysnow, R.drawable.little_icon_heavysnow, R.drawable.little_icon_heavysnow_shadow, -1),
        "29" to WeatherIconSet(R.drawable.little_icon_haze, R.drawable.little_icon_haze, R.drawable.little_icon_haze_shadow, -1),
        "30" to WeatherIconSet(R.drawable.little_icon_haze, R.drawable.little_icon_haze, R.drawable.little_icon_haze_shadow, -1),
        "31" to WeatherIconSet(R.drawable.little_icon_sandstorm, R.drawable.little_icon_sandstorm, R.drawable.little_icon_sandstorm_shadow, -1),
        "32" to WeatherIconSet(R.drawable.little_icon_foggy, R.drawable.little_icon_foggy, R.drawable.little_icon_foggy_shadow, -1),
        "49" to WeatherIconSet(R.drawable.little_icon_foggy, R.drawable.little_icon_foggy, R.drawable.little_icon_foggy_shadow, -1),
        "53" to WeatherIconSet(R.drawable.little_icon_haze, R.drawable.little_icon_haze, R.drawable.little_icon_haze_shadow, -1),
        "54" to WeatherIconSet(R.drawable.little_icon_haze, R.drawable.little_icon_haze, R.drawable.little_icon_haze_shadow, -1),
        "55" to WeatherIconSet(R.drawable.little_icon_haze, R.drawable.little_icon_haze, R.drawable.little_icon_haze_shadow, -1),
        "56" to WeatherIconSet(R.drawable.little_icon_haze, R.drawable.little_icon_haze, R.drawable.little_icon_haze_shadow, -1),
        "57" to WeatherIconSet(R.drawable.little_icon_foggy, R.drawable.little_icon_foggy, R.drawable.little_icon_foggy_shadow, -1),
        "58" to WeatherIconSet(R.drawable.little_icon_foggy, R.drawable.little_icon_foggy, R.drawable.little_icon_foggy_shadow, -1),
        "99" to WeatherIconSet(R.drawable.little_icon_unknown, R.drawable.little_icon_unknown, R.drawable.little_icon_unknown_shadow, -1),
        "1000" to WeatherIconSet(-1, -1, R.drawable.little_icon_sunrise_shadow, -1),
        "1001" to WeatherIconSet(-1, -1, R.drawable.little_icon_sunset_shadow, -1),
    )

    /** 获取天气图标集 */
    fun getIconSet(code: String?): WeatherIconSet {
        val safeCode = code?.takeIf { it.isNotBlank() } ?: return iconMap["99"]!!
        return iconMap[safeCode] ?: iconMap["99"]!!
    }

    /** 获取天气图标（根据是否夜晚选择 day/night） */
    fun getIcon(code: String?, isNight: Boolean = false): Int {
        val set = getIconSet(code)
        return if (isNight && set.nightIcon != -1) set.nightIcon else set.dayIcon
    }

    /** 获取阴影图标 */
    fun getShadowIcon(code: String?, isNight: Boolean = false): Int {
        val set = getIconSet(code)
        return if (isNight && set.nightShadowIcon != -1) set.nightShadowIcon else set.shadowIcon
    }

    /** 判断天气代码是否未知 */
    fun isUnknown(code: String?): Boolean {
        if (code.isNullOrBlank()) return true
        return code == "99" || !iconMap.containsKey(code)
    }

    /** AQI 等级阈值 */
    object AqiLevel {
        const val LEVEL_0 = 0
        const val LEVEL_1 = 50
        const val LEVEL_2 = 100
        const val LEVEL_3 = 150
        const val LEVEL_4 = 200
        const val LEVEL_5 = 300

        /** AQI 值 → 等级（0-5） */
        fun getLevel(aqi: Int): Int = when {
            aqi <= LEVEL_1 -> 0
            aqi <= LEVEL_2 -> 1
            aqi <= LEVEL_3 -> 2
            aqi <= LEVEL_4 -> 3
            aqi <= LEVEL_5 -> 4
            else -> 5
        }

        /** AQI 等级 → 文字资源 ID */
        val levelTextRes: IntArray = intArrayOf(
            R.string.aqi_good,
            R.string.aqi_moderate,
            R.string.aqi_unhealthy_low,
            R.string.aqi_unhealthy_middle,
            R.string.aqi_unhealthy_hight,
            R.string.aqi_hazardous,
        )

        /** AQI 等级 → AQI 图标资源 ID */
        val levelIconRes: IntArray = intArrayOf(
            R.drawable.weather_air_perfect,
            R.drawable.weather_air_good,
            R.drawable.weather_air_mild_polluted,
            R.drawable.weather_air_moderately_polluted,
            R.drawable.weather_air_severe_pollution,
            R.drawable.weather_air_severe_pollution_most,
        )
    }

    /** 摄氏度转华氏度 */
    fun celsiusToFahrenheit(c: Int): Int = (c * 1.8f + 32f).toInt()

    /** 摄氏度转华氏度（四舍五入） */
    fun celsiusToFahrenheit(c: Float): Int = (c * 1.8f + 32f).roundToInt()

    /** 摄氏度差值转华氏度差值 */
    fun compareC2F(compC: Int): Int =
        if (compC > 0) (compC * 1.8f + 0.5f).toInt() else (compC * 1.8f - 0.5f).toInt()

    /** 华氏度转摄氏度 */
    fun fahrenheitToCelsius(f: Int): Int = ((f - 32) / 1.8f).toInt()

    /** 安全解析整数 */
    fun safeParseInt(s: String?): Int = runCatching {
        s?.trim()?.toInt() ?: 0
    }.getOrDefault(0)

    /** 安全解析整数（可为空） */
    fun safeParseIntOrNull(s: String?): Int? = runCatching {
        s?.trim()?.toInt()
    }.getOrNull()

    private fun Float.roundToInt(): Int = Math.round(this)
}
