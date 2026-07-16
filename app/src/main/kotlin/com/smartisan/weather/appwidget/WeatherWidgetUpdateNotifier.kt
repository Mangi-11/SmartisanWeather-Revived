package com.smartisan.weather.appwidget

import android.content.Context

/** 让数据层只发出“组件数据已变化”信号，不承担 Glance 渲染或网络调度。 */
internal object WeatherWidgetUpdateNotifier {
    fun notifyDataChanged(context: Context, requestRefresh: Boolean = false) {
        val appContext = context.applicationContext
        WeatherWidgetScheduler.requestRender(appContext)
        if (requestRefresh) {
            WeatherWidgetScheduler.requestRefresh(appContext)
        }
    }
}
