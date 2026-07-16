package com.smartisan.weather.appwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class WeatherWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherGlanceWidget

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherWidgetScheduler.ensurePeriodicRefresh(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WeatherWidgetScheduler.ensurePeriodicRefresh(context)
        WeatherWidgetScheduler.requestRefresh(context)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        WeatherWidgetScheduler.requestRestore(context, oldWidgetIds, newWidgetIds)
        WeatherWidgetScheduler.ensurePeriodicRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WeatherWidgetScheduler.cancelPeriodicRefresh(context)
    }

    companion object {
        const val EXTRA_CITY_KEY = "com.smartisan.weather.appwidget.extra.CITY_KEY"
    }
}
