package com.smartisan.weather.appwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

internal object WeatherWidgetScheduler {
    fun ensurePeriodicRefresh(context: Context) {
        if (!hasWidgets(context)) return
        val request = PeriodicWorkRequestBuilder<WeatherWidgetWorker>(
            PERIODIC_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
            PERIODIC_FLEX_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(workerData(appWidgetId = null, manual = false, mode = MODE_REFRESH))
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun requestRefresh(
        context: Context,
        appWidgetId: Int? = null,
        manual: Boolean = false,
    ) {
        if (!hasWidgets(context)) return
        val request = OneTimeWorkRequestBuilder<WeatherWidgetWorker>()
            .setInputData(workerData(appWidgetId, manual, MODE_REFRESH))
            .addTag(WORK_TAG)
            .build()
        val suffix = appWidgetId?.toString() ?: "all"
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "$IMMEDIATE_WORK_PREFIX$suffix",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun requestRender(context: Context) {
        if (!hasWidgets(context)) return
        val request = OneTimeWorkRequestBuilder<WeatherWidgetWorker>()
            .setInputData(
                Data.Builder()
                    .putString(KEY_MODE, MODE_RENDER)
                    .build(),
            )
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            RENDER_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun requestRestore(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val request = OneTimeWorkRequestBuilder<WeatherWidgetWorker>()
            .setInputData(
                Data.Builder()
                    .putString(KEY_MODE, MODE_RESTORE)
                    .putIntArray(KEY_OLD_APP_WIDGET_IDS, oldWidgetIds)
                    .putIntArray(KEY_APP_WIDGET_IDS, newWidgetIds)
                    .build(),
            )
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            RESTORE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancelPeriodicRefresh(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    private fun workerData(appWidgetId: Int?, manual: Boolean, mode: String): Data =
        Data.Builder()
            .putString(KEY_MODE, mode)
            .putInt(KEY_APP_WIDGET_ID, appWidgetId ?: AppWidgetManager.INVALID_APPWIDGET_ID)
            .putBoolean(KEY_MANUAL, manual)
            .build()

    private fun hasWidgets(context: Context): Boolean =
        AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, WeatherWidgetProvider::class.java),
        ).isNotEmpty()

    const val KEY_APP_WIDGET_ID = "app_widget_id"
    const val KEY_APP_WIDGET_IDS = "app_widget_ids"
    const val KEY_OLD_APP_WIDGET_IDS = "old_app_widget_ids"
    const val KEY_MANUAL = "manual"
    const val KEY_MODE = "mode"
    const val MODE_REFRESH = "refresh"
    const val MODE_RENDER = "render"
    const val MODE_RESTORE = "restore"
    private const val PERIODIC_INTERVAL_MINUTES = 30L
    private const val PERIODIC_FLEX_MINUTES = 10L
    private const val PERIODIC_WORK_NAME = "weather-widget-periodic-refresh"
    private const val IMMEDIATE_WORK_PREFIX = "weather-widget-refresh-"
    private const val RENDER_WORK_NAME = "weather-widget-render"
    private const val RESTORE_WORK_NAME = "weather-widget-restore"
    private const val WORK_TAG = "weather-widget"
}
