package com.smartisan.weather.appwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smartisan.weather.data.settings.WeatherSettings

internal class WeatherWidgetWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        when (inputData.getString(WeatherWidgetScheduler.KEY_MODE)) {
            WeatherWidgetScheduler.MODE_RENDER -> {
                WeatherWidgetUpdater.renderCached(applicationContext)
                return Result.success()
            }

            WeatherWidgetScheduler.MODE_RESTORE -> {
                val oldIds = inputData.getIntArray(
                    WeatherWidgetScheduler.KEY_OLD_APP_WIDGET_IDS,
                ) ?: intArrayOf()
                val newIds = inputData.getIntArray(
                    WeatherWidgetScheduler.KEY_APP_WIDGET_IDS,
                ) ?: intArrayOf()
                WeatherSettings.getInstance(applicationContext)
                    .remapWidgetCitySelections(oldIds, newIds)
                WeatherWidgetUpdater.renderCached(applicationContext, newIds)
                WeatherWidgetScheduler.requestRefresh(applicationContext)
                return Result.success()
            }
        }

        val appWidgetId = inputData.getInt(
            WeatherWidgetScheduler.KEY_APP_WIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        val requestedIds = appWidgetId
            .takeUnless { it == AppWidgetManager.INVALID_APPWIDGET_ID }
            ?.let(::intArrayOf)
        WeatherWidgetUpdater.refresh(
            context = applicationContext,
            requestedIds = requestedIds,
            manual = inputData.getBoolean(WeatherWidgetScheduler.KEY_MANUAL, false),
        )
        return Result.success()
    }
}
