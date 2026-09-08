package com.smartisan.weather.util

import android.content.Context
import android.content.res.Configuration
import com.smartisan.weather.R

/** Immutable resource themes, using the shared weather-code classification. */
object ThemeUtils {
    private val defaultTheme = ThemeBean(
        R.drawable.drawable_weather_bg_sunny, R.drawable.selector_weather_c_sunny,
        R.drawable.selector_weather_f_sunny, R.drawable.frame_sunny, R.drawable.btn_unpressed_sunny,
        R.drawable.selector_weather_add_city_sunny, R.drawable.selector_weather_refresh_sunny,
        R.drawable.button_refresh_icon_sunny, R.drawable.selector_weather_list_sunny,
        R.drawable.bg_weather_info_error, R.drawable.bg_forecast_sunny, R.drawable.c_sunny,
        R.drawable.f_sunny, R.drawable.selector_weather_checked_sunny, R.drawable.icon_alert_sunny,
        R.drawable.weather_aqi_tips, R.color.weather_aqi_des_sunny_text_color,
    )

    private val themesByKey = mapOf(
        "000" to defaultTheme,
        "00" to ThemeBean(
            R.drawable.drawable_weather_bg_sunny, R.drawable.selector_weather_c_sunny,
            R.drawable.selector_weather_f_sunny, R.drawable.frame_sunny, R.drawable.btn_unpressed_sunny,
            R.drawable.selector_weather_add_city_sunny, R.drawable.selector_weather_refresh_sunny,
            R.drawable.button_refresh_icon_sunny, R.drawable.selector_weather_list_sunny,
            R.drawable.bg_weather_info_sunny, R.drawable.bg_forecast_sunny, R.drawable.c_sunny,
            R.drawable.f_sunny, R.drawable.selector_weather_checked_sunny, R.drawable.icon_alert_sunny,
            R.drawable.weather_aqi_tips, R.color.weather_aqi_des_sunny_text_color,
        ),
        "01" to ThemeBean(
            R.drawable.drawable_weather_bg_sunny, R.drawable.selector_weather_c_sunny,
            R.drawable.selector_weather_f_sunny, R.drawable.frame_sunny, R.drawable.btn_unpressed_sunny,
            R.drawable.selector_weather_add_city_sunny, R.drawable.selector_weather_refresh_sunny,
            R.drawable.button_refresh_icon_sunny, R.drawable.selector_weather_list_sunny,
            R.drawable.bg_weather_info_cloud, R.drawable.bg_forecast_sunny, R.drawable.c_sunny,
            R.drawable.f_sunny, R.drawable.selector_weather_checked_sunny, R.drawable.icon_alert_sunny,
            R.drawable.weather_aqi_tips, R.color.weather_aqi_des_sunny_text_color,
        ),
        "02" to ThemeBean(
            R.drawable.drawable_weather_bg_rain, R.drawable.selector_weather_c_rain,
            R.drawable.selector_weather_f_rain, R.drawable.frame_rain, R.drawable.btn_unpressed_snow,
            R.drawable.selector_weather_add_city_rain, R.drawable.selector_weather_refresh_rain,
            R.drawable.button_refresh_icon_rain, R.drawable.selector_weather_list_rain,
            R.drawable.bg_weather_info_overcast, R.drawable.bg_forecast_rain, R.drawable.c_rain,
            R.drawable.f_sunny, R.drawable.selector_weather_checked_rain, R.drawable.icon_alert_rain,
            R.drawable.weather_aqi_tips, R.color.weather_aqi_des_rain_text_color,
        ),
        "03" to ThemeBean(
            R.drawable.drawable_weather_bg_rain, R.drawable.selector_weather_c_rain,
            R.drawable.selector_weather_f_rain, R.drawable.frame_rain, R.drawable.btn_unpressed_snow,
            R.drawable.selector_weather_add_city_rain, R.drawable.selector_weather_refresh_rain,
            R.drawable.button_refresh_icon_rain, R.drawable.selector_weather_list_rain,
            R.drawable.bg_weather_info_rain, R.drawable.bg_forecast_rain, R.drawable.c_rain,
            R.drawable.f_sunny, R.drawable.selector_weather_checked_rain, R.drawable.icon_alert_rain,
            R.drawable.weather_aqi_tips, R.color.weather_aqi_des_rain_text_color,
        ),
        "04" to ThemeBean(
            R.drawable.drawable_weather_bg_snow, R.drawable.selector_weather_c_snow,
            R.drawable.selector_weather_f_snow, R.drawable.frame_snow, R.drawable.btn_unpressed_snow,
            R.drawable.selector_weather_add_city_snow, R.drawable.selector_weather_refresh_snow,
            R.drawable.button_refresh_icon_snow, R.drawable.selector_weather_list_snow,
            R.drawable.bg_weather_info_snow, R.drawable.bg_forecast_snow, R.drawable.c_sunny,
            R.drawable.f_sunny, R.drawable.selector_weather_checked_snow, R.drawable.icon_alert_snow,
            R.drawable.weather_aqi_tips, R.color.weather_aqi_des_snow_text_color,
        ),
        "05" to ThemeBean(
            R.drawable.drawable_weather_bg_foggy, R.drawable.selector_weather_c_foggy,
            R.drawable.selector_weather_f_foggy, R.drawable.frame_foggy, R.drawable.btn_unpressed_snow,
            R.drawable.selector_weather_add_city_foggy, R.drawable.selector_weather_refresh_rain,
            R.drawable.button_refresh_icon_foggy, R.drawable.selector_weather_list_foggy,
            R.drawable.bg_weather_info_foggy, R.drawable.bg_forecast_foggy, R.drawable.c_sunny,
            R.drawable.f_sunny, R.drawable.selector_weather_checked_foggy, R.drawable.icon_alert_foggy,
            R.drawable.weather_aqi_tips, R.color.weather_aqi_des_foggy_text_color,
        ),
        "06" to ThemeBean(
            R.drawable.drawable_weather_bg_haze, R.drawable.selector_weather_c_haze,
            R.drawable.selector_weather_f_haze, R.drawable.frame_haze, R.drawable.btn_unpressed_snow,
            R.drawable.selector_weather_add_city_haze, R.drawable.selector_weather_refresh_haze,
            R.drawable.button_refresh_icon_haze, R.drawable.selector_weather_list_haze,
            R.drawable.bg_weather_info_haze, R.drawable.bg_forecast_haze, R.drawable.c_sunny,
            R.drawable.f_sunny, R.drawable.selector_weather_checked_haze, R.drawable.icon_alert_haze,
            R.drawable.weather_aqi_tips, R.color.weather_aqi_des_haze_text_color,
        ),
        "07" to ThemeBean(
            R.drawable.drawable_weather_bg_sandstorm, R.drawable.selector_weather_c_sandstorm,
            R.drawable.selector_weather_f_sandstorm, R.drawable.frame_sandstorm, R.drawable.btn_unpressed_sunny,
            R.drawable.selector_weather_add_city_sandstorm, R.drawable.selector_weather_refresh_sandstorm,
            R.drawable.button_refresh_icon_sandstorm, R.drawable.selector_weather_list_sandstorm,
            R.drawable.bg_weather_info_sandstorm, R.drawable.bg_forecast_sandstorm, R.drawable.c_sunny,
            R.drawable.f_sunny, R.drawable.selector_weather_checked_sandstorm, R.drawable.icon_alert_sandstorm,
            R.drawable.weather_aqi_tips, R.color.weather_aqi_des_sandstorm_text_color,
        ),
    )

    fun getBgRes(str: String?): Int = (themesByKey[str] ?: defaultTheme).getBgRes()

    fun getCurTheme(str: String?): ThemeBean =
        themesByKey[WeatherCodeMapping.getTheme(str).themeId] ?: defaultTheme

    fun isNightMode(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
}
