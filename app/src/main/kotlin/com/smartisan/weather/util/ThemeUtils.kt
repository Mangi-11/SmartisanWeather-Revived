package com.smartisan.weather.util

import android.content.Context
import android.content.res.Configuration
import android.text.TextUtils
import com.smartisan.weather.R

/**
 * 天气主题资源映射，复刻自原版 com.smartisan.weather.util.ThemeUtils。
 *
 * - a: 主题键（"000","00".."07"）→ ThemeBean（全套资源 ID）
 * - b: 天气代码 → 主题键
 *
 * 原版为 class + static 初始化块，此处以 object 等价表达。
 */
object ThemeUtils {
    private val a: HashMap<String, ThemeBean> = HashMap()
    private val b: HashMap<String, String> = HashMap()

    init {
        a["000"] = ThemeBean(
            R.drawable.drawable_weather_bg_sunny, R.drawable.selector_weather_c_sunny,
            R.drawable.selector_weather_f_sunny, R.drawable.frame_sunny, R.drawable.btn_unpressed_sunny,
            R.drawable.selector_weather_add_city_sunny, R.drawable.selector_weather_refresh_sunny,
            R.drawable.button_refresh_icon_sunny, R.drawable.selector_weather_list_sunny,
            R.drawable.bg_weather_info_error, R.drawable.bg_forecast_sunny, R.drawable.c_sunny,
            R.drawable.f_sunny, R.drawable.selector_weather_checked_sunny, R.drawable.icon_alert_sunny,
            R.drawable.weather_aqi_tips, R.color.weather_aqi_des_sunny_text_color,
        )
        a["00"] = ThemeBean(
            R.drawable.drawable_weather_bg_sunny, R.drawable.selector_weather_c_sunny,
            R.drawable.selector_weather_f_sunny, R.drawable.frame_sunny, R.drawable.btn_unpressed_sunny,
            R.drawable.selector_weather_add_city_sunny, R.drawable.selector_weather_refresh_sunny,
            R.drawable.button_refresh_icon_sunny, R.drawable.selector_weather_list_sunny,
            R.drawable.bg_weather_info_sunny, R.drawable.bg_forecast_sunny, R.drawable.c_sunny,
            R.drawable.f_sunny, R.drawable.selector_weather_checked_sunny, R.drawable.icon_alert_sunny,
            R.drawable.weather_aqi_tips, R.color.weather_aqi_des_sunny_text_color,
        )
        a["01"] = ThemeBean(
            R.drawable.drawable_weather_bg_sunny, R.drawable.selector_weather_c_sunny,
            R.drawable.selector_weather_f_sunny, R.drawable.frame_sunny, R.drawable.btn_unpressed_sunny,
            R.drawable.selector_weather_add_city_sunny, R.drawable.selector_weather_refresh_sunny,
            R.drawable.button_refresh_icon_sunny, R.drawable.selector_weather_list_sunny,
            R.drawable.bg_weather_info_cloud, R.drawable.bg_forecast_sunny, R.drawable.c_sunny,
            R.drawable.f_sunny, R.drawable.selector_weather_checked_sunny, R.drawable.icon_alert_sunny,
            R.drawable.weather_aqi_tips, R.color.weather_aqi_des_sunny_text_color,
        )
        a["02"] = ThemeBean(
            R.drawable.drawable_weather_bg_rain, R.drawable.selector_weather_c_rain,
            R.drawable.selector_weather_f_rain, R.drawable.frame_rain, R.drawable.btn_unpressed_snow,
            R.drawable.selector_weather_add_city_rain, R.drawable.selector_weather_refresh_rain,
            R.drawable.button_refresh_icon_rain, R.drawable.selector_weather_list_rain,
            R.drawable.bg_weather_info_overcast, R.drawable.bg_forecast_rain, R.drawable.c_rain,
            R.drawable.f_sunny, R.drawable.selector_weather_checked_rain, R.drawable.icon_alert_rain,
            R.drawable.weather_aqi_tips, R.color.weather_aqi_des_rain_text_color,
        )
        a["03"] = ThemeBean(
            R.drawable.drawable_weather_bg_rain, R.drawable.selector_weather_c_rain,
            R.drawable.selector_weather_f_rain, R.drawable.frame_rain, R.drawable.btn_unpressed_snow,
            R.drawable.selector_weather_add_city_rain, R.drawable.selector_weather_refresh_rain,
            R.drawable.button_refresh_icon_rain, R.drawable.selector_weather_list_rain,
            R.drawable.bg_weather_info_rain, R.drawable.bg_forecast_rain, R.drawable.c_rain,
            R.drawable.f_sunny, R.drawable.selector_weather_checked_rain, R.drawable.icon_alert_rain,
            R.drawable.weather_aqi_tips, R.color.weather_aqi_des_rain_text_color,
        )
        a["04"] = ThemeBean(
            R.drawable.drawable_weather_bg_snow, R.drawable.selector_weather_c_snow,
            R.drawable.selector_weather_f_snow, R.drawable.frame_snow, R.drawable.btn_unpressed_snow,
            R.drawable.selector_weather_add_city_snow, R.drawable.selector_weather_refresh_snow,
            R.drawable.button_refresh_icon_snow, R.drawable.selector_weather_list_snow,
            R.drawable.bg_weather_info_snow, R.drawable.bg_forecast_snow, R.drawable.c_sunny,
            R.drawable.f_sunny, R.drawable.selector_weather_checked_snow, R.drawable.icon_alert_snow,
            R.drawable.weather_aqi_tips, R.color.weather_aqi_des_snow_text_color,
        )
        a["05"] = ThemeBean(
            R.drawable.drawable_weather_bg_foggy, R.drawable.selector_weather_c_foggy,
            R.drawable.selector_weather_f_foggy, R.drawable.frame_foggy, R.drawable.btn_unpressed_snow,
            R.drawable.selector_weather_add_city_foggy, R.drawable.selector_weather_refresh_rain,
            R.drawable.button_refresh_icon_foggy, R.drawable.selector_weather_list_foggy,
            R.drawable.bg_weather_info_foggy, R.drawable.bg_forecast_foggy, R.drawable.c_sunny,
            R.drawable.f_sunny, R.drawable.selector_weather_checked_foggy, R.drawable.icon_alert_foggy,
            R.drawable.weather_aqi_tips, R.color.weather_aqi_des_foggy_text_color,
        )
        a["06"] = ThemeBean(
            R.drawable.drawable_weather_bg_haze, R.drawable.selector_weather_c_haze,
            R.drawable.selector_weather_f_haze, R.drawable.frame_haze, R.drawable.btn_unpressed_snow,
            R.drawable.selector_weather_add_city_haze, R.drawable.selector_weather_refresh_haze,
            R.drawable.button_refresh_icon_haze, R.drawable.selector_weather_list_haze,
            R.drawable.bg_weather_info_haze, R.drawable.bg_forecast_haze, R.drawable.c_sunny,
            R.drawable.f_sunny, R.drawable.selector_weather_checked_haze, R.drawable.icon_alert_haze,
            R.drawable.weather_aqi_tips, R.color.weather_aqi_des_haze_text_color,
        )
        a["07"] = ThemeBean(
            R.drawable.drawable_weather_bg_sandstorm, R.drawable.selector_weather_c_sandstorm,
            R.drawable.selector_weather_f_sandstorm, R.drawable.frame_sandstorm, R.drawable.btn_unpressed_sunny,
            R.drawable.selector_weather_add_city_sandstorm, R.drawable.selector_weather_refresh_sandstorm,
            R.drawable.button_refresh_icon_sandstorm, R.drawable.selector_weather_list_sandstorm,
            R.drawable.bg_weather_info_sandstorm, R.drawable.bg_forecast_sandstorm, R.drawable.c_sunny,
            R.drawable.f_sunny, R.drawable.selector_weather_checked_sandstorm, R.drawable.icon_alert_sandstorm,
            R.drawable.weather_aqi_tips, R.color.weather_aqi_des_sandstorm_text_color,
        )

        b["00"] = "00"
        b["01"] = "01"
        b["02"] = "02"
        b["03"] = "03"
        b["04"] = "03"
        b["05"] = "03"
        b["06"] = "03"
        b["07"] = "03"
        b["08"] = "03"
        b["09"] = "03"
        b["10"] = "03"
        b["11"] = "03"
        b["12"] = "03"
        b["19"] = "03"
        b["21"] = "03"
        b["22"] = "03"
        b["23"] = "03"
        b["24"] = "03"
        b["25"] = "03"
        b["13"] = "04"
        b["14"] = "04"
        b["15"] = "04"
        b["16"] = "04"
        b["17"] = "04"
        b["26"] = "04"
        b["27"] = "04"
        b["28"] = "04"
        b["18"] = "05"
        b["32"] = "05"
        b["49"] = "05"
        b["57"] = "05"
        b["58"] = "05"
        b["53"] = "06"
        b["54"] = "06"
        b["55"] = "06"
        b["56"] = "06"
        b["20"] = "07"
        b["29"] = "07"
        b["30"] = "07"
        b["31"] = "07"
        b["99"] = "00"
    }

    @JvmStatic
    fun getBgRes(str: String?): Int {
        var s = str
        if (TextUtils.isEmpty(s)) {
            s = "000"
        }
        a[s]!!.setThemeType(s)
        return a[s]!!.getBgRes()
    }

    @JvmStatic
    fun getCurTheme(str: String?): ThemeBean {
        var str2 = ""
        val it: Iterator<String> = b.keys.iterator()
        while (it.hasNext()) {
            if (str == it.next()) {
                str2 = b[str]!!
            }
        }
        if (TextUtils.isEmpty(str2)) {
            str2 = "000"
        }
        a[str2]!!.setThemeType(str2)
        return a[str2]!!
    }

    @JvmStatic
    fun isNightMode(context: Context): Boolean =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
}
