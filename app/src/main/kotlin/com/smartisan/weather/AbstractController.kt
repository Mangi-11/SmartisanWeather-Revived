package com.smartisan.weather

import com.smartisan.weather.bean.SmartisanLocation

/**
 * 主界面控制器接口，复刻自原版 com.smartisan.weather.AbstractController。
 * 由宿主 Activity/Fragment 实现，供 View 层回调跳转与刷新。
 */
interface AbstractController {
    fun openAlerDetailPage()

    fun openCityLisPage()

    fun openParnterDetailPage()

    fun refreshCurrentCity()

    fun setScrollale(z: Boolean)

    fun startAddCity(smartisanLocation: SmartisanLocation)
}
