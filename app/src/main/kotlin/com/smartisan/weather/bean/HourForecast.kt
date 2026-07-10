package com.smartisan.weather.bean

import java.io.Serializable

/**
 * 逐小时预报集合，复刻自原版 com.smartisan.weather.bean.HourForecast。
 *
 * 原版 mGetTime/mInfo/mPublishTime 为 public 字段，并提供 addInfo/getmInfo。
 * 此处 mInfo 以 private + getmInfo()/setmInfo() 暴露（供 .getmInfo() 调用），
 * mGetTime/mPublishTime 无 getter 需求，保留为 public 字段。
 */
class HourForecast : Serializable {
    @JvmField var mGetTime: String? = null
    @JvmField var mPublishTime: String? = null

    private var mInfo: MutableList<HourForecastInfo>? = null

    fun getmInfo(): MutableList<HourForecastInfo>? = mInfo

    fun setmInfo(value: MutableList<HourForecastInfo>?) {
        mInfo = value
    }

    fun addInfo(info: HourForecastInfo) {
        if (mInfo == null) {
            mInfo = ArrayList()
        }
        mInfo!!.add(info)
    }
}
