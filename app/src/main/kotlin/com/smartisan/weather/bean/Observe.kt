package com.smartisan.weather.bean

import java.io.Serializable

/**
 * 当前天气观测数据，复刻自原版 com.smartisan.weather.bean.Observe。
 *
 * 原版字段全部 private，通过显式 getter/setter 暴露。此处保留该结构，
 * 以便调用方以 .getXxx()/.setXxx() 形式访问（与原版及已有适配器代码一致）。
 */
class Observe : Serializable {
    private var aqi: String? = null
    private var bodyFeelC: String? = null
    private var bodyFeelF: String? = null
    private var co: String? = null
    private var code: String? = null
    private var compareC: String? = null
    private var compareF: String? = null
    private var curSunRise: String? = null
    private var curSunSet: String? = null
    private var currentWeekDay: String? = null
    private var highTempC: String? = null
    private var highTempF: String? = null
    private var humidity: String? = null
    private var localTime: String? = null
    private var locationKey: String? = null
    private var lowTempC: String? = null
    private var lowTempF: String? = null
    private var nextSunRise: String? = null
    private var nextSunSet: String? = null
    private var no2: String? = null
    private var o3: String? = null
    private var pm10: String? = null
    private var pm25: String? = null
    private var primary: String? = null
    private var pubdate: String? = null
    private var so2: String? = null
    private var speed: String? = null
    private var tempC: String? = null
    private var tempF: String? = null
    private var wind: String? = null

    fun getAqi(): String? = aqi
    fun setAqi(value: String?) { aqi = value }
    fun getBodyFeelC(): String? = bodyFeelC
    fun setBodyFeelC(value: String?) { bodyFeelC = value }
    fun getBodyFeelF(): String? = bodyFeelF
    fun setBodyFeelF(value: String?) { bodyFeelF = value }
    fun getCo(): String? = co
    fun setCo(value: String?) { co = value }
    fun getCode(): String? = code
    fun setCode(value: String?) { code = value }
    fun getCompareC(): String? = compareC
    fun setCompareC(value: String?) { compareC = value }
    fun getCompareF(): String? = compareF
    fun setCompareF(value: String?) { compareF = value }
    fun getCurSunRise(): String? = curSunRise
    fun setCurSunRise(value: String?) { curSunRise = value }
    fun getCurSunSet(): String? = curSunSet
    fun setCurSunSet(value: String?) { curSunSet = value }
    fun getCurrentWeekDay(): String? = currentWeekDay
    fun setCurrentWeekDay(value: String?) { currentWeekDay = value }
    fun getHighTempC(): String? = highTempC
    fun setHighTempC(value: String?) { highTempC = value }
    fun getHighTempF(): String? = highTempF
    fun setHighTempF(value: String?) { highTempF = value }
    fun getHumidity(): String? = humidity
    fun setHumidity(value: String?) { humidity = value }
    fun getLocalTime(): String? = localTime
    fun setLocalTime(value: String?) { localTime = value }
    fun getLocationKey(): String? = locationKey
    fun setLocationKey(value: String?) { locationKey = value }
    fun getLowTempC(): String? = lowTempC
    fun setLowTempC(value: String?) { lowTempC = value }
    fun getLowTempF(): String? = lowTempF
    fun setLowTempF(value: String?) { lowTempF = value }
    fun getNextSunRise(): String? = nextSunRise
    fun setNextSunRise(value: String?) { nextSunRise = value }
    fun getNextSunSet(): String? = nextSunSet
    fun setNextSunSet(value: String?) { nextSunSet = value }
    fun getNo2(): String? = no2
    fun setNo2(value: String?) { no2 = value }
    fun getO3(): String? = o3
    fun setO3(value: String?) { o3 = value }
    fun getPm10(): String? = pm10
    fun setPm10(value: String?) { pm10 = value }
    fun getPm2_5(): String? = pm25
    fun setPm2_5(value: String?) { pm25 = value }
    fun getPrimary(): String? = primary
    fun setPrimary(value: String?) { primary = value }
    fun getPubdate(): String? = pubdate
    fun setPubdate(value: String?) { pubdate = value }
    fun getSo2(): String? = so2
    fun setSo2(value: String?) { so2 = value }
    fun getSpeed(): String? = speed
    fun setSpeed(value: String?) { speed = value }
    fun getTempC(): String? = tempC
    fun setTempC(value: String?) { tempC = value }
    fun getTempF(): String? = tempF
    fun setTempF(value: String?) { tempF = value }
    fun getWind(): String? = wind
    fun setWind(value: String?) { wind = value }

    /** 原版 isEmpty() 固定返回 false。 */
    fun isEmpty(): Boolean = false

    override fun toString(): String =
        "locationKey - $locationKey\ncode - $code\ntempC - $tempC\ntempF - $tempF\n" +
            "bodyFeelC - $bodyFeelC\nhumidity - $humidity\nwind - $wind\nspeed - $speed\n" +
            "pubdate - $pubdate\nlowc - $lowTempC\nlowF - $lowTempF\nhighC - $highTempC\n" +
            "curSunrise - $curSunRise\ncurSunset- $curSunSet\nnextSunrise - $nextSunRise\n" +
            "nextSunset  - $nextSunSet\nhighF - $highTempF\nprimary - $primary"
}
