package com.smartisan.weather.ui.main

import android.view.ViewConfiguration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smartisan.weather.R
import com.smartisan.weather.data.model.Observe
import com.smartisan.weather.data.model.SavedCity
import com.smartisan.weather.data.model.Weather
import com.smartisan.weather.custom.NightWeatherActionDrawable
import com.smartisan.weather.ui.components.WeatherDrawable
import com.smartisan.weather.ui.components.WeatherText
import com.smartisan.weather.ui.components.collectWeatherPressedAsState
import com.smartisan.weather.ui.components.rememberWeatherDrawablePainter
import com.smartisan.weather.ui.components.weatherDrawableBackground
import com.smartisan.weather.util.ThemeBean
import com.smartisan.weather.util.ThemeUtils
import com.smartisan.weather.util.Utility
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sign

private data class WeatherPage(
    val city: SavedCity?,
    val weather: Weather?,
    val isCelsius: Boolean,
    val loading: Boolean,
    val error: String?,
    val version: Long,
) {
    val key: String get() = city?.locationKey.orEmpty()
}

private data class WeatherDeparture(val fromKey: String, val toKey: String, val offset: Float)

/** Pure Compose weather page; all data mutations and navigation belong to the host. */
@Composable
fun WeatherScreen(
    state: WeatherUiState,
    onSelectCity: (Int) -> Unit,
    onRefresh: () -> Unit,
    onToggleUnit: () -> Unit,
    onAddCity: () -> Unit,
    onManageCities: () -> Unit,
    onOpenAlerts: (SavedCity, Weather) -> Unit,
    onLocate: () -> Unit,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val page = WeatherPage(state.currentCity, state.currentWeather, state.isCelsius, state.isLoading || state.isLocating, state.error, state.currentCity?.let { state.loadVersions[it.locationKey] } ?: 0)
    val theme = ThemeUtils.getCurTheme(page.weather?.themeCode)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current
    val pagingSlop = remember(context) { ViewConfiguration.get(context).scaledPagingTouchSlop.toFloat() }
    val threshold = with(density) { 60.dp.toPx() }
    val fadeDistance = with(density) { 120.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(1) }
    var direction by remember { mutableIntStateOf(1) }
    var departure by remember { mutableStateOf<WeatherDeparture?>(null) }
    var settle by remember { mutableStateOf<Job?>(null) }
    val latestState by rememberUpdatedState(state)
    val latestSelect by rememberUpdatedState(onSelectCity)
    val tracker = remember { VelocityTracker() }
    var nestedPagingEligible by remember { mutableStateOf(true) }
    var nestedPaging by remember { mutableStateOf(false) }

    fun dragPage(delta: Float) {
        val s = latestState
        val proposed = drag + delta
        val boundary = (proposed > 0 && s.currentIndex == 0) || (proposed < 0 && s.currentIndex >= s.cities.lastIndex)
        val outward = (delta > 0 && proposed > 0) || (delta < 0 && proposed < 0)
        drag += if (boundary && outward) width / (abs(proposed) * 12f + width) * delta else delta
    }

    fun settlePage(cancelled: Boolean) {
        settle?.cancel()
        val s = latestState
        val next = s.currentIndex + if (drag < 0) 1 else -1
        if (!cancelled && abs(drag) > threshold && next in s.cities.indices) {
            direction = if (next > s.currentIndex) 1 else -1
            departure = WeatherDeparture(s.currentCity?.locationKey.orEmpty(), s.cities[next].locationKey, drag)
            drag = 0f
            latestSelect(next)
        } else {
            val offset = drag
            val velocity = abs(tracker.calculateVelocity().x)
            val half = width / 2f
            val distance = half + sin((minOf(1f, abs(offset) / width) - 0.5f) * 0.4712389167638204f) * half
            val duration = minOf(if (velocity > 0f) (abs(distance / velocity) * 1000).roundToInt() * 4 else ((abs(offset) / 1080f + 1f) * 100).roundToInt(), 600).coerceAtLeast(1)
            settle = scope.launch {
                animate(offset, 0f, animationSpec = tween(duration, easing = Easing { val t = it - 1f; t * t * t * t * t + 1f })) { value, _ -> drag = value }
            }
        }
    }

    val hourlyBoundaryConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!nestedPaging || source != NestedScrollSource.UserInput || available.x == 0f) return Offset.Zero
                dragPage(available.x)
                return Offset(available.x, 0f)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // A gesture that started inside scrollable hours stays with that list.
                // Starting at its edge uses the same city drag as the original parent interception.
                if (consumed.x != 0f && !nestedPaging) nestedPagingEligible = false
                if (available.x == 0f || !nestedPagingEligible) return Offset.Zero
                settle?.cancel()
                nestedPaging = true
                dragPage(available.x)
                return Offset(available.x, 0f)
            }

        }
    }

    LaunchedEffect(page.key) {
        settle?.cancel()
        drag = 0f
        nestedPaging = false
        val leaving = departure
        if (leaving?.toKey != page.key) departure = null
        else {
            delay(250)
            if (departure == leaving) departure = null
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        settle?.cancel()
        drag = 0f
        nestedPaging = false
        nestedPagingEligible = false
        departure = null
    }
    Box(modifier.fillMaxSize()) {
        Crossfade(theme.getBgRes(), Modifier.fillMaxSize(), animationSpec = tween(400, 100), label = "weather background") { background ->
            Box(Modifier.fillMaxSize().weatherDrawableBackground(background))
        }
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
            BoxWithConstraints(
                Modifier.widthIn(max = 480.dp).fillMaxSize().clipToBounds().testTag("weather_city_pager").onSizeChanged { width = it.width.coerceAtLeast(1) }
                    .nestedScroll(hourlyBoundaryConnection)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            nestedPagingEligible = true
                            var pointer = down.id
                            var previous = down.position
                            var displacement = Offset.Zero
                            var dragging = false
                            var childOwnsGesture = false
                            var released = false
                            tracker.resetTracking()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            try {
                                while (true) {
                                    val initial = awaitPointerEvent(PointerEventPass.Initial)
                                    val cancelledUp = initial.changes.firstOrNull { it.id == pointer }?.let { !it.pressed && it.isConsumed } == true
                                    val event = awaitPointerEvent()
                                    var change = event.changes.firstOrNull { it.id == pointer } ?: break
                                    // Keep observing release/cancel after a LazyRow consumes a drag:
                                    // a zero-velocity nested fling also happens on CANCEL and cannot commit a city.
                                    if (change.isConsumed) childOwnsGesture = true
                                    if (!change.pressed) {
                                        val replacement = event.changes.firstOrNull { it.pressed }
                                        if (replacement == null) {
                                            if (dragging || nestedPaging) settlePage(cancelledUp)
                                            nestedPaging = false
                                            nestedPagingEligible = false
                                            released = true
                                            break
                                        }
                                        pointer = replacement.id
                                        previous = replacement.position
                                        change = replacement
                                        tracker.resetTracking()
                                    }
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    val delta = change.position - previous
                                    displacement += delta
                                    previous = change.position
                                    if (childOwnsGesture) continue
                                    if (!dragging) {
                                        // Original paging slop and 2:1 horizontal intent protect vertical scrolling.
                                        if (abs(displacement.x) > pagingSlop && abs(displacement.x) * 0.5f > abs(displacement.y)) {
                                            settle?.cancel()
                                            dragging = true
                                            dragPage(displacement.x - sign(displacement.x) * pagingSlop)
                                            change.consume()
                                        } else if (abs(displacement.y) > pagingSlop) break
                                    } else {
                                        dragPage(delta.x)
                                        change.consume()
                                    }
                                }
                            } finally {
                                if ((dragging || nestedPaging) && !released) {
                                    nestedPaging = false
                                    nestedPagingEligible = false
                                    settlePage(true)
                                }
                            }
                        }
                    }
                    .semantics {
                        customActions = buildList {
                            if (state.currentIndex > 0) add(CustomAccessibilityAction("上一个城市") { onSelectCity(state.currentIndex - 1); true })
                            if (state.currentIndex < state.cities.lastIndex) add(CustomAccessibilityAction("下一个城市") { onSelectCity(state.currentIndex + 1); true })
                        }
                    },
            ) {
                val compact = maxHeight < 500.dp
                val rootScroll = rememberScrollState()
                Column(if (compact) Modifier.fillMaxSize().verticalScroll(rootScroll) else Modifier.fillMaxSize()) {
                    PageLayer(page, direction, { drag }, fadeDistance, departure, Modifier.fillMaxWidth().height(48.dp)) { snapshot ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            WeatherText(cityTitle(snapshot.city), Modifier.padding(horizontal = 24.dp), color = colorResource(R.color.item_pager_title_tex_color), fontSize = weatherDpTextSize(18f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    WeatherDrawable(R.drawable.line, null, Modifier.align(Alignment.CenterHorizontally).width(dimensionResource(R.dimen.vp_top_box_line_width)))
                    WeatherActions(theme, page.isCelsius, page.loading, page.city != null, onToggleUnit, onRefresh, onAddCity, onManageCities)
                    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).heightIn(min = 206.67.dp)) {
                        Crossfade(theme.getInfoBgRes(), Modifier.matchParentSize(), tween(200), label = "weather card") { resource ->
                            Box(Modifier.fillMaxSize().weatherDrawableBackground(resource).weatherDrawableBackground(R.drawable.weather_top_card_scrim))
                        }
                        PageLayer(page, direction, { drag }, fadeDistance, departure, Modifier.fillMaxWidth().heightIn(min = 206.67.dp)) { snapshot ->
                            if (snapshot.weather?.isComplete == true) {
                                WeatherObservation(snapshot, onOpenAlerts)
                            } else {
                                EmptyWeather(snapshot, onAddCity, onLocate, onRefresh)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().then(if (compact) Modifier.height(360.dp) else Modifier.weight(1f))) {
                        Crossfade(theme.getForecastBgRes(), Modifier.matchParentSize(), tween(200), label = "forecast card") { resource ->
                            Box(Modifier.fillMaxSize().weatherDrawableBackground(resource))
                        }
                        PageLayer(page, direction, { drag }, fadeDistance, departure, Modifier.fillMaxSize()) { snapshot ->
                            snapshot.weather?.takeIf { it.isComplete }?.let { weather ->
                                key(snapshot.key) { WeatherForecast(weather, snapshot.isCelsius, snapshot.version, onOpenSource) }
                            }
                        }
                    }
                    CityIndicators(state.cities, state.currentIndex, onSelectCity)
                }
            }
        }
    }
}

@Composable
private fun PageLayer(
    page: WeatherPage,
    direction: Int,
    drag: () -> Float,
    fadeDistance: Float,
    departure: WeatherDeparture?,
    modifier: Modifier,
    content: @Composable (WeatherPage) -> Unit,
) {
    val distance = with(LocalDensity.current) { 48.dp.roundToPx() }
    val currentPageKey by rememberUpdatedState(page.key)
    val currentDeparture by rememberUpdatedState(departure)
    AnimatedContent(
        page, modifier.graphicsLayer { translationX = drag(); alpha = (1f - abs(drag()) / fadeDistance).coerceIn(0f, 1f) },
        contentKey = { it.key },
        transitionSpec = {
            ((fadeIn(tween(200, 50, WeatherAccelerateDecelerate)) + slideInHorizontally(tween(200, 50, WeatherAccelerateDecelerate)) { distance * direction }) togetherWith
                (fadeOut(tween(200, easing = WeatherAccelerateDecelerate)) + slideOutHorizontally(tween(200, easing = WeatherAccelerateDecelerate)) { -distance * direction }))
                .using(SizeTransform(clip = false))
        }, label = "city content",
    ) { snapshot ->
        Box(Modifier.graphicsLayer {
            val leaving = currentDeparture?.takeIf { it.fromKey == snapshot.key && it.toKey == currentPageKey && snapshot.key != currentPageKey }
            translationX = leaving?.offset ?: 0f
            alpha = leaving?.let { (1f - abs(it.offset) / fadeDistance).coerceIn(0f, 1f) } ?: 1f
        }) { content(snapshot) }
    }
}

@Composable
private fun WeatherActions(theme: ThemeBean, isCelsius: Boolean, loading: Boolean, hasCity: Boolean, onToggleUnit: () -> Unit, onRefresh: () -> Unit, onAddCity: () -> Unit, onManageCities: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth().padding(start = 15.dp, end = 8.dp, top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        WeatherDrawable(theme.getcIconRes(), stringResource(R.string.celsius), Modifier.size(28.dp, 57.33.dp).clickable(remember { MutableInteractionSource() }, indication = null) {
            if (!isCelsius) { haptic.performHapticFeedback(HapticFeedbackType.ToggleOn); onToggleUnit() }
        }, selected = isCelsius)
        TemperatureSwitch(theme, isCelsius) { haptic.performHapticFeedback(HapticFeedbackType.ToggleOn); onToggleUnit() }
        WeatherDrawable(theme.getfIconRes(), stringResource(R.string.fahrenheit), Modifier.size(28.dp, 57.33.dp).clickable(remember { MutableInteractionSource() }, indication = null) {
            if (isCelsius) { haptic.performHapticFeedback(HapticFeedbackType.ToggleOn); onToggleUnit() }
        }, selected = !isCelsius)
        Spacer(Modifier.weight(1f))
        val rotation = remember { Animatable(0f) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        var rotating by remember { mutableStateOf(false) }
        val currentlyLoading by rememberUpdatedState(loading)
        LaunchedEffect(loading, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                rotating = true
                try {
                    if (loading) {
                        do {
                            rotation.snapTo(0f)
                            rotation.animateTo(360f, tween(1_000, easing = LinearEasing))
                        } while (currentlyLoading)
                    } else if (rotation.value % 360f != 0f) {
                        rotation.animateTo(360f, tween(((360f - rotation.value) / 360f * 1_000).roundToInt(), easing = LinearEasing))
                    }
                } finally {
                    rotating = false
                }
            }
        }

        ResourceAction(theme.getRefreshBgRes(), stringResource(R.string.refresh), hasCity && !loading, onRefresh) {
            Image(rememberWeatherDrawablePainter(theme.getRefreshSrcRes()), null, Modifier.fillMaxSize().graphicsLayer { rotationZ = if (rotating) rotation.value else 0f },
                colorFilter = if (ThemeUtils.isNightMode(LocalContext.current)) ColorFilter.tint(colorResource(R.color.weather_action_icon_color)) else null)
        }
        ResourceAction(theme.getAddRes(), stringResource(R.string.add_city), true, onAddCity)
        ResourceAction(theme.getListRes(), stringResource(R.string.display_as_list), hasCity, onManageCities)
    }
}

@Composable
private fun ResourceAction(resource: Int, description: String, enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectWeatherPressedAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val resources = LocalResources.current
    val isRefresh = resource in setOf(R.drawable.selector_weather_refresh_sunny, R.drawable.selector_weather_refresh_rain, R.drawable.selector_weather_refresh_snow, R.drawable.selector_weather_refresh_haze, R.drawable.selector_weather_refresh_sandstorm)
    val painter = if (ThemeUtils.isNightMode(context)) {
        val drawable = remember(resources, context.theme, configuration, resource) {
            NightWeatherActionDrawable(requireNotNull(resources.getDrawable(resource, context.theme)).mutate(),
                resources.getColor(R.color.weather_action_button_normal, context.theme), resources.getColor(R.color.weather_action_button_pressed, context.theme),
                resources.getColor(R.color.weather_action_button_disabled, context.theme), if (isRefresh) null else resources.getColor(R.color.weather_action_icon_color, context.theme))
        }
        rememberWeatherDrawablePainter(drawable, enabled = enabled, pressed = pressed)
    } else rememberWeatherDrawablePainter(resource, enabled = enabled, pressed = pressed)
    Box(Modifier.size(47.33.dp)
        .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description }) {
        Image(painter, null, Modifier.fillMaxSize())
        content()
    }
}

@Composable
private fun TemperatureSwitch(theme: ThemeBean, isCelsius: Boolean, onToggle: () -> Unit) {
    val frame = rememberWeatherDrawablePainter(theme.getFrameIcon())
    val thumb = rememberWeatherDrawablePainter(theme.getSwitchIcon())
    val position = remember { Animatable(if (isCelsius) 0f else 1f) }
    val scope = rememberCoroutineScope()
    var manual by remember { mutableFloatStateOf(Float.NaN) }
    val latestCelsius by rememberUpdatedState(isCelsius)
    val latestToggle by rememberUpdatedState(onToggle)
    LaunchedEffect(isCelsius) { position.animateTo(if (isCelsius) 0f else 1f, tween(115, easing = LinearEasing)) }
    val description = stringResource(if (isCelsius) R.string.celsius_selected else R.string.fahrenheit_selected)
    Canvas(Modifier.size(86.67.dp, 61.33.dp).testTag("weather_temperature_unit")
        .toggleable(!isCelsius, indication = null, interactionSource = remember { MutableInteractionSource() }, role = Role.Switch) { onToggle() }
        .semantics { contentDescription = "温度单位"; stateDescription = description }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { scope.launch { position.stop() }; manual = position.value },
                onHorizontalDrag = { change, delta -> manual = (manual + delta / 39.33.dp.toPx()).coerceIn(0f, 1f); change.consume() },
                onDragCancel = {
                    manual = Float.NaN
                    scope.launch { position.animateTo(if (latestCelsius) 0f else 1f, tween(115, easing = LinearEasing)) }
                },
                onDragEnd = {
                    val targetCelsius = manual < 0.5f
                    manual = Float.NaN
                    if (targetCelsius != latestCelsius) latestToggle()
                    else scope.launch { position.animateTo(if (latestCelsius) 0f else 1f, tween(115, easing = LinearEasing)) }
                },
            )
        }) {
        val progress = if (manual.isNaN()) position.value else manual
        clipRect {
            translate(top = 2.dp.toPx()) {
                with(frame) { draw(Size(size.width, 57.33.dp.toPx())) }
                translate(left = (-39.33f + progress * 39.33f).dp.toPx()) {
                    with(thumb) { draw(Size(126.dp.toPx(), 57.33.dp.toPx())) }
                }
            }
        }
    }
}

@Composable
private fun WeatherObservation(page: WeatherPage, onOpenAlerts: (SavedCity, Weather) -> Unit) {
    val weather = requireNotNull(page.weather)
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 24.dp)) {
        val tempC = weather.observe.tempC.toIntOrNull()
        val tempF = weather.observe.tempF.toIntOrNull()
        if (tempC != null && tempF != null) MainTemperature(tempC, tempF, page.isCelsius, page.version)
        else WeatherText("—", Modifier.height(75.dp), color = Color.White, fontSize = weatherDpTextSize(60f))
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            WeatherText(Utility.getWeatherDescByCode(context, weather.themeCode), color = colorResource(R.color.item_pager_top_box_weather_des_text_color), fontSize = weatherDpTextSize(13.5f), fontWeight = FontWeight.Bold)
            val aqi = weather.observe.aqi.ifBlank { weather.airQuality.aqiValue }
            if (aqi.toIntOrNull() != null) {
                WeatherDrawable(R.drawable.weather_aqi_seperator, null)
                WeatherText(stringResource(R.string.weather_pager_aqi_text) + aqi, color = colorResource(R.color.item_pager_top_box_weather_des_text_color), fontSize = weatherDpTextSize(13.5f), fontWeight = FontWeight.Bold)
                val theme = ThemeUtils.getCurTheme(weather.themeCode)
                Box(Modifier.padding(start = 6.dp).weatherDrawableBackground(theme.getTipsIcon()).heightIn(min = 12.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                    WeatherText(stringResource(Utility.getAQIResId(context, aqi)), color = colorResource(theme.getAqiTextColor()), fontSize = weatherDpTextSize(8f), fontWeight = FontWeight.Bold)
                }
            }
        }
        val alerts = weather.alert.infos.filter { it.content.isNotBlank() }
        if (alerts.isNotEmpty() && page.city != null) {
            var index by remember(alerts) { mutableIntStateOf(0) }
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            LaunchedEffect(alerts, lifecycle) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (isActive && alerts.size > 1) { delay(3_500); index = (index + 1) % alerts.size }
                }
            }
            Row(Modifier.padding(top = 7.dp).clickable { onOpenAlerts(page.city, weather) }, verticalAlignment = Alignment.CenterVertically) {
                WeatherDrawable(R.drawable.alert_left_icon, null, Modifier.size(12.dp))
                AnimatedContent(index, Modifier.weight(1f).height(18.dp).padding(start = 4.dp).clipToBounds(), transitionSpec = { slideInVertically(tween(1_000)) { it } togetherWith slideOutVertically(tween(1_000)) { -it } }, label = "weather alerts") { alertIndex ->
                    val alert = alerts[alertIndex.coerceIn(alerts.indices)]
                    WeatherText(stringResource(R.string.weather_alert, alert.type, alert.level, alert.content), color = Color.White, fontSize = weatherDpTextSize(12f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                WeatherDrawable(R.drawable.alert_right_icon, null, Modifier.size(10.dp, 14.dp))
            }
        }
        val update = parseWeatherLocalTime(weather.observe.pubdate.ifBlank { weather.pubdate }, weather)?.format(DateTimeFormatter.ofPattern("MM/dd HH:mm")) ?: "—"
        WeatherText(stringResource(R.string.weather_pager_update_text, update), Modifier.padding(top = 7.dp), color = colorResource(R.color.item_pager_top_box_weather_update_time_text_color), fontSize = weatherDpTextSize(10f))
    }
}

@Composable
private fun EmptyWeather(page: WeatherPage, onAddCity: () -> Unit, onLocate: () -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 206.67.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (page.loading) {
            LoadingWeather()
        } else if (page.city == null) {
            WeatherText("添加城市，查看天气", color = colorResource(R.color.weather_top_layout_empty_text_color), fontSize = weatherDpTextSize(14f), fontWeight = FontWeight.Bold)
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                WeatherText("定位当前城市", Modifier.clickable(onClick = onLocate).padding(vertical = 12.dp), color = colorResource(R.color.weather_top_layout_empty_text_color))
                WeatherText("添加城市", Modifier.clickable(onClick = onAddCity).padding(vertical = 12.dp), color = colorResource(R.color.weather_top_layout_empty_text_color))
            }
        } else {
            WeatherDrawable(R.drawable.weather_error_icon, null, Modifier.size(30.dp))
            WeatherText(page.error ?: stringResource(R.string.update_failed_network_unavailable1), Modifier.padding(top = 12.dp), color = colorResource(R.color.weather_top_layout_empty_text_color), fontSize = weatherDpTextSize(14f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            WeatherText(stringResource(R.string.update_failed_network_unavailable2), Modifier.clickable(onClick = onRefresh).padding(top = 4.dp, bottom = 12.dp), color = colorResource(R.color.weather_top_layout_empty_text_color), fontSize = weatherDpTextSize(12f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LoadingWeather() {
    val rotation = remember { Animatable(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { rotation.snapTo(0f); rotation.animateTo(360f, tween(1_000, easing = LinearEasing)) }
        }
    }
    WeatherDrawable(R.drawable.loading, "正在加载天气", Modifier.size(30.dp).graphicsLayer { rotationZ = rotation.value })
}

@Composable
private fun CityIndicators(cities: List<SavedCity>, current: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        cities.forEachIndexed { index, city ->
            val resource = if (city.isLocationCity) {
                if (index == current) R.drawable.weather_arrow_seleted else R.drawable.weather_arrow_normal
            } else if (index == current) R.drawable.dot_highlight else R.drawable.dot_grey
            Image(rememberWeatherDrawablePainter(resource), city.displayName,
                Modifier.padding(horizontal = 2.5.dp).size(12.dp).clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(index) },
                colorFilter = if (ThemeUtils.isNightMode(LocalContext.current)) ColorFilter.tint(colorResource(if (index == current) R.color.page_indicator_selected else R.color.page_indicator_normal)) else null)
        }
    }
}

private fun cityTitle(city: SavedCity?): String {
    city ?: return "天气"
    val name = Utility.fristCharToUpdderCase(city.displayName)
    val parent = city.locationParentName.ifBlank { city.province }
    return if (parent.isBlank() || name.equals(parent, true)) name else "$name - ${Utility.fristCharToUpdderCase(parent)}"
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun WeatherScreenPreview() {
    val city = SavedCity(locationKey = "101010100", locationName = "北京")
    WeatherScreen(WeatherUiState(citiesLoaded = true, cities = listOf(city), weathers = mapOf(city.locationKey to Weather(observe = Observe(tempC = "28", tempF = "82", code = "00", aqi = "42")))), {}, {}, {}, {}, {}, { _, _ -> }, {}, {})
}
