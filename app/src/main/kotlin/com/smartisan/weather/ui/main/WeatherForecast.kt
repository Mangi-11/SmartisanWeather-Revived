package com.smartisan.weather.ui.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartisan.weather.R
import com.smartisan.weather.data.model.DailyForecast
import com.smartisan.weather.data.model.Weather
import com.smartisan.weather.ui.components.WeatherDrawable
import com.smartisan.weather.ui.components.WeatherText
import com.smartisan.weather.ui.components.rememberWeatherDrawablePainter
import com.smartisan.weather.util.ResMappingUtil
import com.smartisan.weather.util.ThemeUtils
import com.smartisan.weather.util.Utility
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal fun parseForecastDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
        ?: runCatching { LocalDate.parse(value.take(8), DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()

internal fun parseWeatherLocalTime(value: String, weather: Weather): LocalDateTime? =
    value.toLongOrNull()?.takeIf { value.length == 13 }
        ?.let { Instant.ofEpochMilli(it).atOffset(weather.zoneOffset).toLocalDateTime() }
        ?: runCatching { OffsetDateTime.parse(value).atZoneSameInstant(weather.zoneOffset).toLocalDateTime() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyyMMddHHmm")) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value) }.getOrNull()

internal data class WeatherHourItem(
    val key: String,
    val time: LocalDateTime?,
    val code: String,
    val celsius: String,
    val fahrenheit: String,
    val night: Boolean,
)

/** Adds only real sun events within the supplied forecast interval, in the city's local time. */
internal fun Weather.hourItems(): List<WeatherHourItem> {
    val rows = hourForecast.mapIndexed { index, hour ->
        WeatherHourItem(
            key = "hour:$index:${hour.startTime}",
            time = parseWeatherLocalTime(hour.startTime, this),
            code = hour.weatherCode.ifBlank { hour.code },
            celsius = hour.sunDes.ifBlank { hour.tempC.degree() },
            fahrenheit = hour.sunDes.ifBlank { hour.tempF.degree() },
            night = hour.night,
        )
    }
    val start = rows.mapNotNull { it.time }.minOrNull() ?: return rows
    val end = rows.mapNotNull { it.time }.maxOrNull() ?: return rows
    val events = dailyForecast.flatMap { day ->
        val date = parseForecastDate(day.date) ?: return@flatMap emptyList()
        day.sunriseAndSunset.split('|').take(2).mapIndexedNotNull { index, value ->
            val time = runCatching { LocalTime.parse(value.take(5)) }.getOrNull() ?: return@mapIndexedNotNull null
            val local = date.atTime(time)
            if (local < start || local > end) return@mapIndexedNotNull null
            WeatherHourItem("sun:$local:$index", local, if (index == 0) "1000" else "1001",
                if (index == 0) "日出" else "日落", if (index == 0) "日出" else "日落", false)
        }
    }
    val eventTimes = events.mapTo(mutableSetOf()) { it.time }
    return (rows.filterNot { it.time in eventTimes } + events).sortedBy { it.time ?: LocalDateTime.MAX }
}

@Composable
internal fun WeatherForecast(
    weather: Weather,
    isCelsius: Boolean,
    refreshVersion: Long,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val labelColor = colorResource(R.color.item_pager_content_text_color)
    val valueColor = colorResource(R.color.item_pager_content_text_content_color)
    val hourlyColor = colorResource(R.color.item_pager_content_forcast_hour_item_view_text_color)
    val days = remember(weather.dailyForecast) { weather.dailyForecast.distinctBy { it.date }.sortedBy { it.date } }
    val hours = remember(weather) { weather.hourItems() }
    val today = days.firstOrNull()
    val scroll = rememberScrollState()
    val hourlyScroll = rememberLazyListState()
    var displayedVersion by remember { mutableStateOf(refreshVersion) }
    LaunchedEffect(refreshVersion) {
        if (displayedVersion != refreshVersion) {
            displayedVersion = refreshVersion
            scroll.scrollTo(0)
            hourlyScroll.scrollToItem(0)
        }
    }
    Column(modifier.fillMaxSize().forecastFadingEdges(scroll).verticalScroll(scroll)) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 27.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                ForecastText(today?.let { weekday(it, locale) }.orEmpty(), labelColor)
                WeatherDrawable(R.drawable.weather_today_line, null)
                ForecastText(stringResource(R.string.weather_current_day_text), labelColor)
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                SmallTemperature(today?.lowTempC.degree(), today?.lowTempF.degree(), isCelsius, valueColor, Modifier.weight(1f))
                SmallTemperature(today?.highTempC.degree(), today?.highTempF.degree(), isCelsius, valueColor, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(15.dp))
        ForecastDivider()
        LazyRow(state = hourlyScroll, contentPadding = PaddingValues(horizontal = 15.dp), modifier = Modifier.fillMaxWidth().height(108.dp).testTag("weather_hourly_forecast")) {
            items(hours, key = { it.key }) { item ->
                Column(Modifier.width(48.dp).height(108.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                        ForecastText(item.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "—", hourlyColor, 12f)
                    }
                    ForecastIcon(item.code, item.night, Modifier.size(30.dp, 48.dp))
                    val sunDescription = when (item.code) {
                        "1000" -> stringResource(R.string.weather_text_1000)
                        "1001" -> stringResource(R.string.weather_text_1001)
                        else -> null
                    }
                    SmallTemperature(sunDescription ?: item.celsius, sunDescription ?: item.fahrenheit, isCelsius, hourlyColor, size = 12f)
                }
            }
        }
        ForecastDivider()
        Spacer(Modifier.height(15.dp))
        days.forEach { day ->
            Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                val date = parseForecastDate(day.date)?.let {
                    stringResource(R.string.weather_forecast_date, "%02d".format(locale, it.monthValue), "%02d".format(locale, it.dayOfMonth))
                }.orEmpty()
                ForecastText(stringResource(R.string.weather_pager_week_and_date_text, date, weekday(day, locale)), labelColor, modifier = Modifier.weight(1f))
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(36.dp), contentAlignment = Alignment.Center) {
                        ForecastIcon(day.weatherCodeAm, false, Modifier.requiredSize(30.dp, 48.dp))
                    }
                    SmallTemperature(day.lowTempC.degree(), day.lowTempF.degree(), isCelsius, valueColor, Modifier.weight(1f))
                    SmallTemperature(day.highTempC.degree(), day.highTempF.degree(), isCelsius, valueColor, Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ForecastDivider()
        Spacer(Modifier.height(12.dp))
        val observe = weather.observe
        val humidity = observe.humidity.ifBlank { weather.relativeHumidity }.takeIf { it.isNotBlank() }?.let { "$it%" } ?: "—"
        val wind = observe.wind.ifBlank { weather.windDirection }
        val windLabel = if (wind.toIntOrNull() != null) stringResource(ResMappingUtil.getWindDirRedId(wind)) else wind.ifBlank { "风力" }
        val windLevel = stringResource(R.string.weather_forecast_wind_level)
        val speed = observe.speed.ifBlank { weather.windSpeed }.takeIf { it.isNotBlank() }?.let { it + windLevel } ?: "—"
        MetricRow(stringResource(R.string.relative_Humidity), humidity, windLabel, speed, labelColor, valueColor)
        Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                ForecastText(stringResource(R.string.real_feel_temp), labelColor, modifier = Modifier.weight(1f))
                SmallTemperature(observe.bodyFeelC.knownDegree("℃"), observe.bodyFeelF.knownDegree("℉"), isCelsius, valueColor)
                Spacer(Modifier.width(24.dp))
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (weather.allergy.uvLevel.isNotBlank()) {
                    ForecastText(stringResource(R.string.weather_forecast_ultraviolet_radiation), labelColor, modifier = Modifier.weight(1f))
                    ForecastText(weather.allergy.uvLevel, valueColor)
                }
            }
        }
        val pollutants = listOf(
            Triple("PM10", observe.pm10.ifBlank { weather.airQuality.pm10 }, Utility::getPm10Grade),
            Triple("PM2.5", observe.pm25.ifBlank { weather.airQuality.pm25 }, Utility::getPm2_5Grade),
            Triple("NO₂", observe.no2.ifBlank { weather.airQuality.no2 }, Utility::getNo2Grade),
            Triple("O₃", observe.o3.ifBlank { weather.airQuality.o3 }, Utility::getO3Grade),
            Triple("SO₂", observe.so2.ifBlank { weather.airQuality.so2 }, Utility::getSo2Grade),
            Triple("CO", observe.co.ifBlank { weather.airQuality.co }, Utility::getCoGrade),
        )
        if (pollutants.any { it.second.isNotBlank() }) {
            Spacer(Modifier.height(12.dp))
            ForecastDivider()
            Spacer(Modifier.height(12.dp))
            pollutants.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().height(36.dp).padding(start = 14.dp, end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    pair.forEachIndexed { index, (label, value, grade) ->
                        Row(Modifier.weight(1f).padding(end = if (index == 0) 24.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (value.isNotBlank()) WeatherDrawable(grade(value), null, Modifier.size(10.dp), contentScale = ContentScale.Fit)
                            else Spacer(Modifier.width(10.dp))
                            ForecastText(label, labelColor, modifier = Modifier.weight(1f))
                            ForecastText(value.ifBlank { "—" }, valueColor)
                        }
                    }
                }
            }
        }
        WeatherText(
            weather.source.ifBlank { "天气数据：小米天气" },
            Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, indication = null) { onOpenSource(weather.attributionUrl) }.padding(horizontal = 24.dp, vertical = 24.dp),
            color = valueColor, fontSize = weatherDpTextSize(10f), textAlign = TextAlign.Center,
        )
    }
}

/** Masks only the scrolling content, retaining the original card's untouched artwork. */
private fun Modifier.forecastFadingEdges(scroll: ScrollState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val edge = minOf(18.dp.toPx(), size.height / 2f)
        val top = minOf(scroll.value.toFloat(), edge)
        val bottom = minOf((scroll.maxValue - scroll.value).coerceAtLeast(0).toFloat(), edge)
        if (top > 0f) {
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = top),
                size = Size(size.width, top), blendMode = BlendMode.DstIn,
            )
        }
        if (bottom > 0f) {
            drawRect(
                Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - bottom, endY = size.height),
                topLeft = Offset(0f, size.height - bottom), size = Size(size.width, bottom), blendMode = BlendMode.DstIn,
            )
        }
    }

@Composable
private fun MetricRow(label1: String, value1: String, label2: String, value2: String, labelColor: Color, valueColor: Color) {
    Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).padding(end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            ForecastText(label1, labelColor, modifier = Modifier.weight(1f))
            ForecastText(value1, valueColor)
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            ForecastText(label2, labelColor, modifier = Modifier.weight(1f))
            ForecastText(value2, valueColor)
        }
    }
}

@Composable
private fun ForecastText(text: String, color: Color, size: Float = 13.5f, modifier: Modifier = Modifier) {
    WeatherText(text, modifier, color = color, fontSize = weatherDpTextSize(size), fontWeight = FontWeight.Bold, maxLines = 1)
}

@Composable
private fun ForecastDivider() {
    Spacer(Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(0.67.dp).background(colorResource(R.color.item_pager_seperation_color)))
}

@Composable
internal fun ForecastIcon(code: String, night: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val res = ResMappingUtil.getWeatherResId(code).getLittleIconShadow(night)
    Image(
        painter = rememberWeatherDrawablePainter(res), contentDescription = Utility.getWeatherDescByCode(context, code),
        modifier = modifier, contentScale = ContentScale.Fit,
        colorFilter = if (ThemeUtils.isNightMode(context)) ColorFilter.tint(colorResource(R.color.forecast_icon_tint)) else null,
    )
}

private fun Int?.degree(): String = if (this == null || this == Int.MAX_VALUE) "—" else "$this°"
private fun String.knownDegree(suffix: String): String = toFloatOrNull()?.let { "${this}$suffix" } ?: "—"
private fun weekday(day: DailyForecast, locale: Locale): String = parseForecastDate(day.date)?.dayOfWeek?.getDisplayName(TextStyle.FULL, locale) ?: day.weekDay
