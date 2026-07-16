package com.smartisan.weather.appwidget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.smartisan.weather.MainActivity
import com.smartisan.weather.R
import com.smartisan.weather.data.city.CityRepository
import com.smartisan.weather.data.model.WeatherAlert
import com.smartisan.weather.data.settings.WeatherSettings
import com.smartisan.weather.data.weather.WeatherRepository
import com.smartisan.weather.ui.alert.WeatherAlertActivity
import com.smartisan.weather.util.ThemeUtils
import com.smartisan.weather.util.WeatherCodeMapping
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first

internal object WeatherGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val persistedState = getAppWidgetState(
            context,
            PreferencesGlanceStateDefinition,
            id,
        )
        val initialRequest = persistedState.widgetRenderRequest()
        val initialModel = WeatherWidgetDisplayModelLoader.load(
            context = context,
            appWidgetId = appWidgetId,
            updateState = initialRequest.updateState,
            noCacheState = initialRequest.noCacheState,
        )

        provideContent {
            val request = currentState<Preferences>().widgetRenderRequest()
            var displayedRequest by remember { mutableStateOf(initialRequest) }
            var model by remember { mutableStateOf(initialModel) }
            LaunchedEffect(request) {
                if (request != displayedRequest) {
                    model = WeatherWidgetDisplayModelLoader.load(
                        context = context,
                        appWidgetId = appWidgetId,
                        updateState = request.updateState,
                        noCacheState = request.noCacheState,
                    )
                    displayedRequest = request
                }
            }
            WeatherWidgetSurface(
                context = context,
                appWidgetId = appWidgetId,
                size = LocalSize.current,
                model = model,
            )
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        WeatherSettings.getInstance(context).removeWidgetCitySelections(intArrayOf(appWidgetId))
    }
}

internal object WeatherWidgetGlanceState {
    val REVISION = longPreferencesKey("revision")
    val UPDATE_STATE = stringPreferencesKey("update_state")
    val NO_CACHE_STATE = stringPreferencesKey("no_cache_state")
}

private data class WeatherWidgetRenderRequest(
    val revision: Long,
    val updateState: WeatherWidgetUpdateState,
    val noCacheState: WeatherWidgetEmptyState,
)

private sealed interface WeatherWidgetDisplayModel {
    data class Empty(
        val message: String,
    ) : WeatherWidgetDisplayModel

    data class Ready(
        val cityName: String,
        val cityKey: String,
        val backgroundRes: Int,
        val weatherIconRes: Int,
        val condition: String,
        val temperature: String,
        val temperatureRange: String?,
        val aqi: String?,
        val updateText: String,
        val alertText: String?,
        val alert: WeatherAlert,
        val forecast: List<Forecast>,
    ) : WeatherWidgetDisplayModel

    data class Forecast(
        val time: String,
        val iconRes: Int,
        val temperature: String,
    )
}

private object WeatherWidgetDisplayModelLoader {
    suspend fun load(
        context: Context,
        appWidgetId: Int,
        updateState: WeatherWidgetUpdateState,
        noCacheState: WeatherWidgetEmptyState,
    ): WeatherWidgetDisplayModel {
        val settings = WeatherSettings.getInstance(context)
        if (!settings.startupNoticeAccepted.first()) {
            return empty(context, WeatherWidgetEmptyState.SETUP_REQUIRED)
        }

        val cities = CityRepository(context).savedCities.first()
        if (cities.isEmpty()) return empty(context, WeatherWidgetEmptyState.CITY_REQUIRED)
        val selection = settings.readWidgetCitySelections(intArrayOf(appWidgetId))[appWidgetId]
        val city = WeatherWidgetCityResolver.resolve(cities, selection)
            ?: return empty(context, WeatherWidgetEmptyState.CITY_REQUIRED)
        if (WeatherWidgetCityResolver.isStaleSelection(cities, selection)) {
            settings.setWidgetCitySelection(appWidgetId, AUTO_CITY_SELECTION)
        }
        val snapshot = WeatherRepository(context).getCachedWeatherSnapshot(city.locationKey)
            ?: return empty(context, noCacheState)
        val content = WeatherWidgetContentFactory.create(snapshot.weather, settings.readTempUnit())
        val theme = ThemeUtils.getCurTheme(content.code)
        val conditionRes = WeatherCodeMapping.textResMap[content.code] ?: R.string.weather_text_99
        val currentIcon = WeatherCodeMapping.getIcon(content.code, content.isNight)
            .takeIf { it > 0 }
            ?: WeatherCodeMapping.getIcon("99")

        return WeatherWidgetDisplayModel.Ready(
            cityName = city.displayName,
            cityKey = city.locationKey,
            backgroundRes = weatherWidgetBackground(theme.getInfoBgRes()),
            weatherIconRes = currentIcon,
            condition = context.getString(conditionRes),
            temperature = context.getString(
                R.string.weather_widget_temperature,
                content.currentTemperature ?: context.getString(R.string.weather_null),
            ),
            temperatureRange = temperatureRangeText(context, content),
            aqi = aqiText(context, content),
            updateText = updateText(context, snapshot.updatedAtMillis, updateState),
            alertText = snapshot.weather.alert.first?.let { first ->
                context.getString(R.string.weather_alert_tip, first.type, first.level)
            },
            alert = snapshot.weather.alert,
            forecast = content.forecast.mapIndexed { index, slot ->
                WeatherWidgetDisplayModel.Forecast(
                    time = when {
                        index == 0 -> context.getString(R.string.weather_widget_now)
                        slot.hour >= 0 -> context.getString(R.string.weather_widget_hour_format, slot.hour)
                        else -> context.getString(R.string.weather_null)
                    },
                    iconRes = WeatherCodeMapping.getIcon(slot.code, slot.isNight)
                        .takeIf { it > 0 }
                        ?: WeatherCodeMapping.getIcon("99"),
                    temperature = context.getString(
                        R.string.weather_widget_temperature,
                        slot.temperature ?: context.getString(R.string.weather_null),
                    ),
                )
            },
        )
    }

    private fun empty(context: Context, state: WeatherWidgetEmptyState) =
        WeatherWidgetDisplayModel.Empty(
            message = context.getString(
                when (state) {
                    WeatherWidgetEmptyState.SETUP_REQUIRED ->
                        R.string.weather_widget_setup_required
                    WeatherWidgetEmptyState.CITY_REQUIRED -> R.string.weather_widget_setup_city
                    WeatherWidgetEmptyState.WEATHER_LOADING -> R.string.weather_widget_loading
                    WeatherWidgetEmptyState.WEATHER_UNAVAILABLE ->
                        R.string.weather_widget_weather_unavailable
                },
            ),
        )

    private fun temperatureRangeText(context: Context, content: WeatherWidgetContent): String? = when {
        content.highTemperature != null && content.lowTemperature != null -> context.getString(
            R.string.weather_widget_low_high,
            content.lowTemperature,
            content.highTemperature,
        )
        content.highTemperature != null -> context.getString(
            R.string.weather_widget_high,
            content.highTemperature,
        )
        content.lowTemperature != null -> context.getString(
            R.string.weather_widget_low,
            content.lowTemperature,
        )
        else -> null
    }

    private fun aqiText(context: Context, content: WeatherWidgetContent): String? {
        val aqi = content.aqiValue ?: return null
        val level = WeatherCodeMapping.AqiLevel.getLevel(aqi)
        val levelText = context.getString(WeatherCodeMapping.AqiLevel.levelTextRes[level])
        return context.getString(R.string.weather_widget_aqi, aqi, levelText)
    }

    private fun updateText(
        context: Context,
        updatedAtMillis: Long,
        updateState: WeatherWidgetUpdateState,
    ): String {
        val time = updatedAtMillis.takeIf { it > 0L }?.let {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
        }.orEmpty()
        return when (updateState) {
            WeatherWidgetUpdateState.REFRESHING ->
                context.getString(R.string.weather_widget_refreshing)
            WeatherWidgetUpdateState.FAILED ->
                context.getString(R.string.weather_widget_update_failed, time)
            WeatherWidgetUpdateState.IDLE ->
                context.getString(R.string.weather_widget_updated_at, time)
        }
    }
}

@Composable
private fun WeatherWidgetSurface(
    context: Context,
    appWidgetId: Int,
    size: DpSize,
    model: WeatherWidgetDisplayModel,
) {
    val spec = WeatherWidgetLayoutSpec.fromSize(
        widthDp = size.width.value.toInt(),
        heightDp = size.height.value.toInt(),
    )
    val background = when (model) {
        is WeatherWidgetDisplayModel.Empty ->
            weatherWidgetBackground(R.drawable.bg_weather_info_error)
        is WeatherWidgetDisplayModel.Ready -> model.backgroundRes
    }
    val cityKey = (model as? WeatherWidgetDisplayModel.Ready)?.cityKey
    val openWeather = actionStartActivity(openWeatherIntent(context, appWidgetId, cityKey))

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(
                imageProvider = ImageProvider(background),
                contentScale = ContentScale.FillBounds,
            )
            .appWidgetBackground()
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .clickable(openWeather),
    ) {
        when (model) {
            is WeatherWidgetDisplayModel.Empty -> EmptyWidget(model)
            is WeatherWidgetDisplayModel.Ready -> ReadyWidget(
                context = context,
                appWidgetId = appWidgetId,
                model = model,
                spec = spec,
            )
        }
    }
}

private fun weatherWidgetBackground(infoBackgroundRes: Int): Int {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return infoBackgroundRes

    return when (infoBackgroundRes) {
        R.drawable.bg_weather_info_sunny -> R.drawable.weather_widget_background_sunny
        R.drawable.bg_weather_info_cloud -> R.drawable.weather_widget_background_cloud
        R.drawable.bg_weather_info_overcast -> R.drawable.weather_widget_background_overcast
        R.drawable.bg_weather_info_rain -> R.drawable.weather_widget_background_rain
        R.drawable.bg_weather_info_snow -> R.drawable.weather_widget_background_snow
        R.drawable.bg_weather_info_foggy -> R.drawable.weather_widget_background_foggy
        R.drawable.bg_weather_info_haze -> R.drawable.weather_widget_background_haze
        R.drawable.bg_weather_info_sandstorm -> R.drawable.weather_widget_background_sandstorm
        else -> R.drawable.weather_widget_background_error
    }
}

@Composable
private fun EmptyWidget(model: WeatherWidgetDisplayModel.Empty) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = androidx.glance.LocalContext.current.getString(R.string.weather_widget_name),
            style = primaryText(sizeSp = 18, weight = FontWeight.Bold, align = TextAlign.Center),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(7.dp))
        Text(
            text = model.message,
            style = secondaryText(sizeSp = 11, align = TextAlign.Center),
            maxLines = 2,
        )
    }
}

@Composable
private fun ReadyWidget(
    context: Context,
    appWidgetId: Int,
    model: WeatherWidgetDisplayModel.Ready,
    spec: WeatherWidgetLayoutSpec,
) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(spec.outerPaddingDp.dp),
    ) {
        WidgetHeader(context, appWidgetId, model, spec)
        Spacer(GlanceModifier.height(if (spec.isWide) 3.dp else 5.dp))
        if (spec.isWide) {
            WideWeatherBody(
                context = context,
                appWidgetId = appWidgetId,
                model = model,
                spec = spec,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            )
        } else {
            CompactWeatherBody(
                context = context,
                appWidgetId = appWidgetId,
                model = model,
                spec = spec,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            )
        }
    }
}

@Composable
private fun WidgetHeader(
    context: Context,
    appWidgetId: Int,
    model: WeatherWidgetDisplayModel.Ready,
    spec: WeatherWidgetLayoutSpec,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = model.cityName,
            modifier = GlanceModifier.defaultWeight(),
            style = primaryText(sizeSp = if (spec.isWide) 14 else 15, weight = FontWeight.Bold),
            maxLines = 1,
        )
        if (spec.isWide) {
            val alertText = model.alertText
            if (alertText != null && !spec.showMeta) {
                AlertLabel(
                    context = context,
                    appWidgetId = appWidgetId,
                    text = alertText,
                    alert = model.alert,
                    modifier = GlanceModifier.width(112.dp),
                )
            } else {
                Text(
                    text = model.updateText,
                    style = tertiaryText(sizeSp = 9, align = TextAlign.End),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.width(5.dp))
        }
        Box(
            modifier = GlanceModifier
                .size(32.dp)
                .background(
                    imageProvider = ImageProvider(R.drawable.weather_widget_action_background),
                    contentScale = ContentScale.FillBounds,
                )
                .clickable(actionRunCallback<WeatherWidgetRefreshAction>()),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.weather_widget_refresh),
                contentDescription = context.getString(
                    R.string.weather_widget_refresh_content_description,
                ),
                modifier = GlanceModifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CompactWeatherBody(
    context: Context,
    appWidgetId: Int,
    model: WeatherWidgetDisplayModel.Ready,
    spec: WeatherWidgetLayoutSpec,
    modifier: GlanceModifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = model.temperature,
                modifier = GlanceModifier.defaultWeight(),
                style = primaryText(
                    sizeSp = spec.currentTemperatureSp,
                    weight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(model.weatherIconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                contentScale = ContentScale.Crop,
                colorFilter = whiteTint(),
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = model.condition,
                modifier = GlanceModifier.defaultWeight(),
                style = primaryText(sizeSp = 11, weight = FontWeight.Medium),
                maxLines = 1,
            )
            model.temperatureRange?.let {
                Spacer(GlanceModifier.width(5.dp))
                Text(text = it, style = secondaryText(sizeSp = 10), maxLines = 1)
            }
        }
        if (spec.showMeta) {
            Spacer(GlanceModifier.height(4.dp))
            if (model.alertText != null) {
                AlertLabel(
                    context = context,
                    appWidgetId = appWidgetId,
                    text = model.alertText,
                    alert = model.alert,
                    modifier = GlanceModifier.fillMaxWidth().height(18.dp),
                )
            } else {
                Row(
                    modifier = GlanceModifier.fillMaxWidth().height(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = model.aqi.orEmpty(),
                        modifier = GlanceModifier.defaultWeight(),
                        style = tertiaryText(sizeSp = 9),
                        maxLines = 1,
                    )
                    Text(
                        text = model.updateText,
                        style = tertiaryText(sizeSp = 9, align = TextAlign.End),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun WideWeatherBody(
    context: Context,
    appWidgetId: Int,
    model: WeatherWidgetDisplayModel.Ready,
    spec: WeatherWidgetLayoutSpec,
    modifier: GlanceModifier,
) {
    Column(modifier = modifier) {
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            CurrentConditions(
                model = model,
                spec = spec,
                modifier = GlanceModifier.width(spec.currentColumnWidthDp.dp).fillMaxHeight(),
            )
            Spacer(GlanceModifier.width(10.dp))
            ForecastStrip(
                model = model,
                slots = spec.forecastSlots,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            )
        }
        if (spec.showMeta && model.alertText != null) {
            Spacer(GlanceModifier.height(4.dp))
            AlertLabel(
                context = context,
                appWidgetId = appWidgetId,
                text = model.alertText,
                alert = model.alert,
                modifier = GlanceModifier.fillMaxWidth().height(18.dp),
            )
        }
    }
}

@Composable
private fun CurrentConditions(
    model: WeatherWidgetDisplayModel.Ready,
    spec: WeatherWidgetLayoutSpec,
    modifier: GlanceModifier,
) {
    Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = model.temperature,
            modifier = GlanceModifier.fillMaxWidth(),
            style = primaryText(sizeSp = spec.currentTemperatureSp, weight = FontWeight.Bold),
            maxLines = 1,
        )
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(model.weatherIconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                contentScale = ContentScale.Crop,
                colorFilter = whiteTint(),
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = model.condition,
                style = primaryText(sizeSp = 10, weight = FontWeight.Medium),
                maxLines = 1,
            )
            model.temperatureRange?.let {
                Spacer(GlanceModifier.width(6.dp))
                Text(text = it, style = secondaryText(sizeSp = 9), maxLines = 1)
            }
        }
        model.aqi?.let {
            Text(text = it, style = tertiaryText(sizeSp = 9), maxLines = 1)
        }
    }
}

@Composable
private fun ForecastStrip(
    model: WeatherWidgetDisplayModel.Ready,
    slots: Int,
    modifier: GlanceModifier,
) {
    val forecast = model.forecast.take(slots)
    if (forecast.isEmpty()) {
        Box(
            modifier = modifier.background(
                imageProvider = ImageProvider(R.drawable.weather_widget_forecast_panel),
                contentScale = ContentScale.FillBounds,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = androidx.glance.LocalContext.current.getString(
                    R.string.weather_widget_forecast_unavailable,
                ),
                style = tertiaryText(sizeSp = 10, align = TextAlign.Center),
                maxLines = 2,
            )
        }
        return
    }

    Row(
        modifier = modifier
            .background(
                imageProvider = ImageProvider(R.drawable.weather_widget_forecast_panel),
                contentScale = ContentScale.FillBounds,
            )
            .padding(horizontal = 5.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        forecast.forEach { item ->
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.time,
                    style = tertiaryText(sizeSp = 9, align = TextAlign.Center),
                    maxLines = 1,
                )
                Image(
                    provider = ImageProvider(item.iconRes),
                    contentDescription = null,
                    modifier = GlanceModifier.size(24.dp),
                    colorFilter = whiteTint(),
                )
                Text(
                    text = item.temperature,
                    style = primaryText(sizeSp = 10, weight = FontWeight.Medium, align = TextAlign.Center),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AlertLabel(
    context: Context,
    appWidgetId: Int,
    text: String,
    alert: WeatherAlert,
    modifier: GlanceModifier,
) {
    Row(
        modifier = modifier.clickable(
            actionStartActivity(openAlertIntent(context, appWidgetId, alert)),
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.alert_left_icon),
            contentDescription = null,
            modifier = GlanceModifier.size(11.dp),
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(
            text = text,
            modifier = GlanceModifier.defaultWeight(),
            style = secondaryText(sizeSp = 9, weight = FontWeight.Medium),
            maxLines = 1,
        )
        Image(
            provider = ImageProvider(R.drawable.alert_right_icon),
            contentDescription = null,
            modifier = GlanceModifier.width(8.dp).height(12.dp),
        )
    }
}

private fun primaryText(
    sizeSp: Int,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start,
) = TextStyle(
    color = ColorProvider(Color.White),
    fontSize = sizeSp.sp,
    fontWeight = weight,
    textAlign = align,
)

private fun secondaryText(
    sizeSp: Int,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start,
) = TextStyle(
    color = ColorProvider(Color.White.copy(alpha = 0.85f)),
    fontSize = sizeSp.sp,
    fontWeight = weight,
    textAlign = align,
)

private fun tertiaryText(
    sizeSp: Int,
    align: TextAlign = TextAlign.Start,
) = TextStyle(
    color = ColorProvider(Color.White.copy(alpha = 0.65f)),
    fontSize = sizeSp.sp,
    textAlign = align,
)

private fun whiteTint() = ColorFilter.tint(ColorProvider(Color.White))

private fun openWeatherIntent(context: Context, appWidgetId: Int, cityKey: String?): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = widgetUri(appWidgetId, "open", cityKey)
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        cityKey?.let { putExtra(WeatherWidgetProvider.EXTRA_CITY_KEY, it) }
    }

private fun openAlertIntent(
    context: Context,
    appWidgetId: Int,
    alert: WeatherAlert,
): Intent = Intent(context, WeatherAlertActivity::class.java).apply {
    data = widgetUri(appWidgetId, "alert")
    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    putExtra(WeatherAlertActivity.EXTRA_ALERT, alert)
}

private fun widgetUri(appWidgetId: Int, action: String, cityKey: String? = null): Uri =
    Uri.Builder()
        .scheme("smartisanweather")
        .authority("weather-widget")
        .appendPath(appWidgetId.toString())
        .appendPath(action)
        .apply { cityKey?.let { appendQueryParameter("city", it) } }
        .build()

private inline fun <reified T : Enum<T>> String?.enumValueOrDefault(default: T): T =
    this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

private fun Preferences.widgetUpdateState(): WeatherWidgetUpdateState =
    this[WeatherWidgetGlanceState.UPDATE_STATE]
        .enumValueOrDefault(WeatherWidgetUpdateState.IDLE)

private fun Preferences.widgetNoCacheState(): WeatherWidgetEmptyState =
    this[WeatherWidgetGlanceState.NO_CACHE_STATE]
        .enumValueOrDefault(WeatherWidgetEmptyState.WEATHER_LOADING)

private fun Preferences.widgetRenderRequest() = WeatherWidgetRenderRequest(
    revision = this[WeatherWidgetGlanceState.REVISION] ?: 0L,
    updateState = widgetUpdateState(),
    noCacheState = widgetNoCacheState(),
)

class WeatherWidgetRefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters,
    ) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        WeatherWidgetScheduler.requestRefresh(
            context = context,
            appWidgetId = appWidgetId,
            manual = true,
        )
    }
}
