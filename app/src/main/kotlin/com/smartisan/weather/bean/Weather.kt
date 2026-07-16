package com.smartisan.weather.bean

import java.io.Serializable

/**
 * 完整天气数据，复刻自原版 com.smartisan.weather.bean.Weather。
 *
 * 与 com.smartisan.weather.data.model.Weather（现代数据层不可变模型）并存：
 * 本类位于 bean 包，保留原版可变字段结构，供传统 View 层（DrawItem / Utility 等）使用。
 */
class Weather : Serializable {
    var alert: Alert? = null
    var allergy: Allergy? = null
    var hourForecast: HourForecast? = null
    var newForecast: ArrayList<NewForecastInfo>? = null
    var observe: Observe? = null
    var source: String? = null
    var timezoneOffsetSeconds: Int = 8 * 60 * 60

    fun isDataComplete(): Boolean {
        val obs = observe ?: return false
        return !("UNKNOWN" == obs.getTempC() || "UNKNOWN" == obs.getTempF())
    }

    fun isEmpty(): Boolean {
        val obs = observe ?: return true
        return "UNKNOWN" == obs.getTempC() || "UNKNOWN" == obs.getTempF()
    }

    override fun toString(): String = super.toString()
}
