package com.smartisan.weather.util

import android.content.Context
import com.smartisan.weather.R

/** Resource labels and pollutant indicators for the weather presentation. */
object Utility {
    fun fristCharToUpdderCase(str: String): String {
        if (str.isEmpty() || str.any { it in '\u4e00'..'\u9fa5' }) return str
        return str.replaceFirstChar { it.uppercase() }
    }

    fun getAQIResId(@Suppress("UNUSED_PARAMETER") context: Context, str: String?): Int {
        val aqi = str?.toIntOrNull() ?: return R.string.weather_null
        return when {
            aqi < 0 -> R.string.unknow
            aqi <= 50 -> R.string.aqi_good
            aqi <= 100 -> R.string.aqi_moderate
            aqi <= 150 -> R.string.aqi_unhealthy_low
            aqi <= 200 -> R.string.aqi_unhealthy_middle
            aqi <= 300 -> R.string.aqi_unhealthy_hight
            else -> R.string.aqi_hazardous
        }
    }

    fun getCoGrade(str: String?): Int = pollutantIndicator(str, 5, 10, 35, 60, 90)

    fun getNo2Grade(str: String?): Int = pollutantIndicator(str, 100, 200, 700, 1200, 2340)

    fun getO3Grade(str: String?): Int = pollutantIndicator(str, 160, 200, 300, 400, 800)

    fun getPm10Grade(str: String?): Int = pollutantIndicator(str, 50, 150, 250, 350, 420)

    fun getPm2_5Grade(str: String?): Int = pollutantIndicator(str, 35, 75, 115, 150, 250)

    fun getSo2Grade(str: String?): Int = pollutantIndicator(str, 150, 500, 650, 800, 1600)

    /** Preserve the original thresholds while accepting real decimal concentrations. */
    private fun pollutantIndicator(
        text: String?,
        perfectMaximum: Int,
        goodMaximum: Int,
        mildMaximum: Int,
        moderateMaximum: Int,
        severeMaximum: Int,
    ): Int {
        val concentration = text?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
            ?: return android.R.color.transparent
        return when {
            concentration <= perfectMaximum -> R.drawable.weather_air_perfect
            concentration <= goodMaximum -> R.drawable.weather_air_good
            concentration <= mildMaximum -> R.drawable.weather_air_mild_polluted
            concentration <= moderateMaximum -> R.drawable.weather_air_moderately_polluted
            concentration <= severeMaximum -> R.drawable.weather_air_severe_pollution
            else -> R.drawable.weather_air_severe_pollution_most
        }
    }

    fun getDisplayTime(context: Context, j: Long): String =
        when (val age = alertAge(j, System.currentTimeMillis())) {
            null -> ""
            AlertAge.Now -> context.getString(R.string.weather_alert_time_now)
            is AlertAge.Minutes -> context.getString(R.string.weather_alert_display_time_minute, age.count)
            is AlertAge.Hours -> context.getString(R.string.weather_alert_display_time_hour, age.count)
            is AlertAge.Days -> context.getString(R.string.weather_alert_display_time_day, age.count)
        }

    fun getWeatherDescByCode(context: Context, str: String?): String {
        val textRes = WeatherCodeMapping.textResMap[str] ?: R.string.weather_text_99
        return context.getString(textRes)
    }
}
