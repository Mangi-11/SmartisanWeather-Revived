package com.smartisan.weather.bean

import java.io.Serializable

/**
 * 每日天气预报（AM/PM 分裂），复刻自原版 com.smartisan.weather.bean.NewForecastInfo。
 * 原版字段 private + 显式 getter/setter。
 */
class NewForecastInfo : Serializable {
    private var date: String? = null
    private var getTime: String? = null
    private var highcTemp: Int = 0
    private var highfTemp: Int = 0
    private var locationKey: String? = null
    private var lowcTemp: Int = 0
    private var lowfTemp: Int = 0
    private var sunriseAndSunset: String? = null
    private var weatherCodeAm: String? = null
    private var weatherCodePm: String? = null
    private var weekDay: String? = null

    fun getDate(): String? = date
    fun setDate(value: String?) { date = value }
    fun getGetTime(): String? = getTime
    fun setGetTime(value: String?) { getTime = value }
    fun getHighCTemp(): Int = highcTemp
    fun setHighCTemp(value: Int) { highcTemp = value }
    fun getHighfTemp(): Int = highfTemp
    fun setHighfTemp(value: Int) { highfTemp = value }
    fun getLocationKey(): String? = locationKey
    fun setLocationKey(value: String?) { locationKey = value }
    fun getLowcTemp(): Int = lowcTemp
    fun setLowcTemp(value: Int) { lowcTemp = value }
    fun getLowfTemp(): Int = lowfTemp
    fun setLowfTemp(value: Int) { lowfTemp = value }
    fun getSunriseAndSunset(): String? = sunriseAndSunset
    fun setSunriseAndSunset(value: String?) { sunriseAndSunset = value }
    fun getWeatherCodeAm(): String? = weatherCodeAm
    fun setWeatherCodeAm(value: String?) { weatherCodeAm = value }
    fun getWeatherCodePm(): String? = weatherCodePm
    fun setWeatherCodePm(value: String?) { weatherCodePm = value }
    fun getWeekDay(): String? = weekDay
    fun setWeekDay(value: String?) { weekDay = value }
}
