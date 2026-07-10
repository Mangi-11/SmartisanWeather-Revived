package com.smartisan.weather.custom

/**
 * 持有天气小图标（日间/夜间 + 阴影）的资源 ID。
 *
 * 完整复刻自原版 com.smartisan.weather.custom.WeatherResId。
 */
class WeatherResId(
    val littleIcon: Int,
    val littleIconNight: Int,
    val littleIconShadow: Int,
    val littleIconNightShadow: Int,
) {
    /** 夜间且存在夜间图标时返回夜间图标，否则返回日间图标。 */
    fun getLittleIcon(isNight: Boolean): Int =
        if (!isNight || littleIconNight <= 0) littleIcon else littleIconNight

    /** 夜间且存在夜间阴影图标时返回夜间阴影图标，否则返回日间阴影图标。 */
    fun getLittleIconShadow(isNight: Boolean): Int =
        if (!isNight || littleIconNightShadow <= 0) littleIconShadow else littleIconNightShadow
}
