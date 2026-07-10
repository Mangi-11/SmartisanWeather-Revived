package com.smartisan.weather.custom

import android.text.TextUtils
import com.smartisan.weather.bean.NewForecastInfo
import com.smartisan.weather.bean.SmartisanLocation
import com.smartisan.weather.bean.Weather
import com.smartisan.weather.util.ResMappingUtil
import java.io.Serializable

/**
 * 单城市绘制数据项，复刻自原版 com.smartisan.weather.custom.DrawItem。
 *
 * 持有天气数据、位置信息以及由天气代码映射出的图标资源（WeatherResId）。
 */
class DrawItem : Serializable {
    @JvmField var forecastResId: ArrayList<WeatherResId>? = null
    @JvmField var isEmpty: Boolean = false
    @JvmField var locationData: SmartisanLocation? = null
    @JvmField var resId: WeatherResId? = null
    @JvmField var weatherData: Weather? = null

    private var loadState: Int = 0

    constructor(weather: Weather?, smartisanLocation: SmartisanLocation?) {
        isEmpty = if (weather == null || weather.isEmpty() || smartisanLocation == null ||
            TextUtils.isEmpty(smartisanLocation.mLocationKey)
        ) {
            true
        } else {
            false
        }
        weatherData = weather
        locationData = smartisanLocation
        initRes()
    }

    constructor(z: Boolean) {
        isEmpty = z
    }

    fun getLoadState(): Int = loadState

    fun setLoadState(state: Int) {
        loadState = state
    }

    fun initRes() {
        val weather = weatherData ?: return
        if (weather.observe == null) return
        resId = ResMappingUtil.getWeatherResId(weather.observe!!.getCode())
        forecastResId = ArrayList()
        val newForecast = weather.newForecast ?: return
        val it: Iterator<NewForecastInfo> = newForecast.iterator()
        while (it.hasNext()) {
            forecastResId!!.add(ResMappingUtil.getWeatherResId(it.next().getWeatherCodeAm()))
        }
    }

    fun isDataComplete(): Boolean {
        val weather = weatherData ?: return false
        return weather.isDataComplete()
    }

    override fun toString(): String =
        "DrawItem{weatherData=$weatherData, locationData=$locationData, resId=$resId, " +
            "forecastResId=$forecastResId, isEmpty=$isEmpty, loadState=$loadState}"

    companion object {
        private const val serialVersionUID: Long = 6390569327862277694L
        const val STATE_COMPLETE = 2
        const val STATE_LOADING = 1
    }
}
