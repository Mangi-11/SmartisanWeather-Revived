package com.smartisan.weather.ui.citylist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.smartisan.weather.R
import com.smartisan.weather.data.model.SavedCity
import com.smartisan.weather.data.model.Weather
import com.smartisan.weather.data.settings.WeatherSettings
import com.smartisan.weather.ui.components.WeatherDrawable
import com.smartisan.weather.ui.components.WeatherScreenFrame
import com.smartisan.weather.ui.components.WeatherTitleBar
import com.smartisan.weather.ui.components.weatherDrawableBackground
import com.smartisan.weather.ui.components.OriginalIconButton
import com.smartisan.weather.ui.components.OriginalTextButton
import com.smartisan.weather.ui.components.PixelText
import com.smartisan.weather.util.WeatherCodeMapping
import com.smartisan.weather.util.enableWeatherEdgeToEdge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant

internal val CityDragEasing = Easing { value ->
    if (value < 0.5f) 2f * value * value else 1f - 2f * (value - 1f) * (value - 1f)
}

@Composable
internal fun CityListScreen(
    state: CityListUiState,
    saving: Boolean,
    onAddCity: () -> Unit,
    onDone: () -> Unit,
    onBeginDrag: (String) -> Unit,
    onMoveCity: (String, String) -> Unit,
    onFinishDrag: () -> Unit,
    onCancelDrag: () -> Unit,
    onRequestDelete: (SavedCity) -> Unit,
    onDismissDelete: () -> Unit,
    onDelete: suspend (SavedCity) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val cities = state.displayCities
    val currentCities by rememberUpdatedState(cities)
    val currentOnMove by rememberUpdatedState(onMoveCity)
    val currentOnCancel by rememberUpdatedState(onCancelDrag)
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var draggedTop by remember { mutableFloatStateOf(0f) }
    var draggedHeight by remember { mutableFloatStateOf(0f) }
    var touchY by remember { mutableFloatStateOf(0f) }
    var dropping by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var viewportWidth by remember { mutableFloatStateOf(1f) }
    val deleteOffset = remember { Animatable(0f) }
    val deleteHeight = remember { Animatable(1f) }
    val busy = saving || draggedKey != null || deleting != null

    fun cancelDrag() {
        if (draggedKey != null) {
            onCancelDrag()
            draggedKey = null
            dropping = false
        }
    }

    fun updateTarget() {
        val key = draggedKey ?: return
        val center = draggedTop + draggedHeight / 2f
        val visible = listState.layoutInfo.visibleItemsInfo
        val target = visible.firstOrNull { center >= it.offset && center < it.offset + it.size }
            ?: visible.firstOrNull()?.takeIf { center < it.offset }
            ?: visible.lastOrNull()?.takeIf { center >= it.offset + it.size }
            ?: return
        if (target.key == key || currentCities.none { it.locationKey == target.key && !it.isLocationCity }) return
        currentOnMove(key, target.key as String)
    }

    fun startDrag(cityKey: String, pointerY: Float) {
        if (busy) return
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == cityKey } ?: return
        if (cities.firstOrNull { it.locationKey == cityKey }?.isLocationCity != false) return
        onBeginDrag(cityKey)
        draggedKey = cityKey
        draggedTop = item.offset.toFloat()
        draggedHeight = item.size.toFloat()
        touchY = item.offset + pointerY
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun drop() {
        val key = draggedKey ?: return
        dropping = true
        scope.launch {
            val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.offset?.toFloat() ?: draggedTop
            animate(draggedTop, target, animationSpec = tween(150, easing = CityDragEasing)) { value, _ -> draggedTop = value }
            if (draggedKey == key) {
                onFinishDrag()
                draggedKey = null
                dropping = false
            }
        }
    }

    DisposableEffect(Unit) { onDispose { currentOnCancel() } }
    BackHandler(enabled = draggedKey != null || deleting != null || saving) { if (!saving && deleting == null) cancelDrag() }
    LaunchedEffect(draggedKey, dropping) {
        if (draggedKey == null || dropping) return@LaunchedEffect
        var lastFrame = withFrameNanos { it }
        while (draggedKey != null && !dropping) {
            val frame = withFrameNanos { it }
            val elapsedMs = ((frame - lastFrame) / 1_000_000f).coerceAtMost(64f)
            lastFrame = frame
            val info = listState.layoutInfo
            val viewport = info.viewportEndOffset - info.viewportStartOffset
            val speed = cityEdgeScrollSpeed(touchY - info.viewportStartOffset, viewport.toFloat())
            if (speed != 0f) {
                listState.scrollBy(speed * elapsedMs)
                updateTarget()
            }
        }
    }
    WeatherScreenFrame(backgroundRes = R.drawable.list_bg) {
        Column(Modifier.fillMaxSize()) {
            WeatherTitleBar(
                stringResource(R.string.city_list),
                leftIcon = R.drawable.standard_icon_common_add_selector,
                leftDescription = stringResource(R.string.add_city),
                onLeft = { if (!busy) onAddCity() },
                rightIcon = R.drawable.standard_icon_complete_selector,
                rightDescription = stringResource(R.string.complete),
                onRight = { if (!busy) onDone() },
                rightEnabled = !busy,
            )
            Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { viewportWidth = it.width.toFloat() }) {
                LazyColumn(Modifier.fillMaxSize().testTag("city_list"), state = listState, userScrollEnabled = !busy) {
                    items(cities, key = SavedCity::locationKey) { city ->
                        val hidden = draggedKey == city.locationKey
                        val isDeleting = deleting == city.locationKey
                        CityRow(
                            city, state.weatherByCity[city.locationKey], state.tempUnit,
                            enabled = !busy,
                            onDelete = { onRequestDelete(city) },
                            modifier = Modifier.testTag("city_row_${city.locationKey}").animateItem(fadeInSpec = null, placementSpec = tween(200, easing = CityDragEasing), fadeOutSpec = null)
                                .height(with(density) { (176.dp.toPx() / 3f * (if (isDeleting) deleteHeight.value else 1f)).coerceAtLeast(1f).toDp() })
                                .graphicsLayer { alpha = if (hidden) 0f else 1f; translationX = if (isDeleting) deleteOffset.value else 0f },
                            handleModifier = Modifier.testTag("drag_${city.locationKey}").cityDragHandle(
                                city.locationKey, enabled = !busy && !city.isLocationCity,
                                onStart = { startDrag(city.locationKey, it) },
                                onDrag = { dy -> if (draggedKey == city.locationKey && !dropping) { draggedTop += dy; touchY += dy; updateTarget() } },
                                onEnd = ::drop,
                                onCancel = ::cancelDrag,
                            ).semantics {
                                contentDescription = "拖动排序 ${city.displayName}"
                                if (!city.isLocationCity && !busy) {
                                    val position = cities.indexOf(city)
                                    customActions = buildList {
                                        fun addMove(label: String, target: SavedCity) {
                                            add(CustomAccessibilityAction(label) { onBeginDrag(city.locationKey); onMoveCity(city.locationKey, target.locationKey); onFinishDrag(); true })
                                        }
                                        cities.getOrNull(position - 1)?.takeUnless(SavedCity::isLocationCity)?.let { addMove("上移", it) }
                                        cities.getOrNull(position + 1)?.let { addMove("下移", it) }
                                    }
                                }
                            },
                        )
                    }
                }
                draggedKey?.let { key ->
                    cities.firstOrNull { it.locationKey == key }?.let { city ->
                        Column(Modifier.fillMaxWidth().clearAndSetSemantics {}.graphicsLayer { translationY = draggedTop - with(density) { (23f / 3f).dp.toPx() } }) {
                            WeatherDrawable(R.drawable.shadow_top, null, Modifier.fillMaxWidth().height((23f / 3f).dp))
                            CityRow(city, state.weatherByCity[city.locationKey], state.tempUnit, enabled = true, onDelete = {}, Modifier.height(with(density) { draggedHeight.toDp() }))
                            WeatherDrawable(R.drawable.shadow_bottom, null, Modifier.fillMaxWidth().height((41f / 3f).dp))
                        }
                    }
                }
            }
        }
    }
    state.showDeleteConfirm?.let { city ->
        DeleteCityDialog(onDismissDelete) {
            onDismissDelete()
            scope.launch {
                deleting = city.locationKey
                deleteHeight.snapTo(1f)
                deleteOffset.snapTo(0f)
                try {
                    deleteOffset.animateTo(viewportWidth, tween(200, easing = Easing { 1f - (1f - it) * (1f - it) }))
                    deleteHeight.animateTo(0f, tween(200, easing = Easing { 1f - (1f - it) * (1f - it) }))
                    onDelete(city)
                } finally {
                    deleting = null
                    deleteOffset.snapTo(0f)
                    deleteHeight.snapTo(1f)
                }
            }
        }
    }
}

/** Velocity is pixels/ms, as in the original drag list; the edge occupies 25% of the viewport. */
internal fun cityEdgeScrollSpeed(touchY: Float, viewportHeight: Float): Float {
    if (viewportHeight <= 0f) return 0f
    val edge = viewportHeight * 0.25f
    return when {
        touchY < edge -> -0.5f * ((edge - touchY) / edge).coerceIn(0f, 1f)
        touchY > viewportHeight - edge -> 0.5f * ((touchY - viewportHeight + edge) / edge).coerceIn(0f, 1f)
        else -> 0f
    }
}

@Composable
private fun Modifier.cityDragHandle(key: String, enabled: Boolean, onStart: (Float) -> Unit, onDrag: (Float) -> Unit, onEnd: () -> Unit, onCancel: () -> Unit): Modifier {
    val canDrag by rememberUpdatedState(enabled)
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    val cancel by rememberUpdatedState(onCancel)
    return pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!canDrag) return@awaitEachGesture
            down.consume()
            start(down.position.y)
            try {
                var held = true
                while (held) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || change.isConsumed) { cancel(); break }
                    if (!change.pressed) { held = false; end() }
                    else { drag(change.positionChange().y); change.consume() }
                }
            } catch (cancelled: CancellationException) {
                cancel()
                throw cancelled
            }
        }
    }
}

@Composable
private fun CityRow(city: SavedCity, weather: Weather?, tempUnit: Int, enabled: Boolean, onDelete: () -> Unit, modifier: Modifier = Modifier, handleModifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val unit = if (tempUnit == WeatherSettings.UNIT_CELSIUS) "°C" else "°F"
    val current = weather?.observe?.let { if (tempUnit == WeatherSettings.UNIT_CELSIUS) it.tempC else it.tempF }.takeUnless { it.isNullOrBlank() || it == "UNKNOWN" }
    val name = city.displayName.replaceFirstChar { it.titlecase() }
    val fullName = if (city.locationParentName.isBlank() || city.displayName.equals(city.locationParentName, true)) name else "$name - ${city.locationParentName.replaceFirstChar { it.titlecase() }}"
    val title = stringResource(R.string.format_cityname, fullName, current.orEmpty(), if (current == null) stringResource(R.string.weather_null) + unit else unit)
    val weatherText = formatCityWeather(resources, weather, tempUnit, unit)
    val twoPixels = with(LocalDensity.current) { 2.toDp() }
    Box(modifier.fillMaxWidth().background(colorResource(R.color.app_surface_color))) {
        OriginalIconButton(R.drawable.selector_weather_list_remove, stringResource(R.string.delete_city), Modifier.align(Alignment.CenterStart).padding(start = 12.dp).size(36.dp), enabled, onDelete)
        Column(Modifier.align(Alignment.CenterStart).padding(start = 60.dp).widthIn(max = 212.dp).semantics(mergeDescendants = true) { contentDescription = "$title，$weatherText" }) {
            PixelText(title, 18.dp, colorResource(R.color.app_primary_text_color), maxLines = 1)
            PixelText(weatherText, 13.5.dp, colorResource(R.color.app_tertiary_text_color), Modifier.padding(top = 2.dp), maxLines = 1)
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            weather?.let { WeatherDrawable(WeatherCodeMapping.getIcon(it.themeCode, isCityNight(it)), null, Modifier.size(32.dp, 40.dp), contentScale = ContentScale.Fit) }
            WeatherDrawable(R.drawable.list_icon_drag, null, handleModifier.size((131f / 3f).dp, (176f / 3f).dp), enabled = enabled && !city.isLocationCity)
        }
        if (city.isLocationCity) Spacer(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(twoPixels).background(colorResource(R.color.app_row_divider_color)))
        Spacer(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(twoPixels).background(colorResource(R.color.app_row_divider_color)))
    }
}

@Composable
private fun DeleteCityDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val context = LocalContext.current
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.enableWeatherEdgeToEdge(context)
            window?.setWindowAnimations(android.R.style.Animation_InputMethod)
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .widthIn(max = 480.dp).fillMaxWidth()
                .clickable(remember { MutableInteractionSource() }, indication = null) {}) {
                Box(Modifier.fillMaxWidth().height(48.dp).weatherDrawableBackground(R.drawable.bottom_sheet_title_bar_bg), contentAlignment = Alignment.Center) {
                    PixelText(stringResource(R.string.whether_delete_city), 13.5.dp, colorResource(R.color.title_or_btn_text_color), Modifier.padding(horizontal = 48.dp), fontWeight = FontWeight.Bold)
                    OriginalIconButton(R.drawable.standard_icon_cancel_selector, stringResource(R.string.cancel), Modifier.align(Alignment.CenterEnd).padding(end = 6.dp).size(36.dp), onClick = onDismiss)
                }
                Spacer(Modifier.fillMaxWidth().height(0.5.dp).background(colorResource(R.color.menu_dialog_divider_color)))
                Box(Modifier.fillMaxWidth().background(colorResource(R.color.app_dialog_surface_color)).navigationBarsPadding().padding(horizontal = 18.dp, vertical = 24.dp)) {
                    OriginalTextButton(stringResource(R.string.delete_city), R.drawable.shrink_long_btn_red_selector, Modifier.fillMaxWidth().height(48.dp), Color.White, onConfirm)
                }
            }
        }
    }
}

private fun formatCityWeather(resources: android.content.res.Resources, weather: Weather?, tempUnit: Int, unit: String): String {
    val observe = weather?.observe
    val first = weather?.dailyForecast?.firstOrNull()
    val low = if (tempUnit == WeatherSettings.UNIT_CELSIUS) observe?.lowTempC?.ifBlank { first?.lowTempC?.toString().orEmpty() } else observe?.lowTempF?.ifBlank { first?.lowTempF?.toString().orEmpty() }
    val high = if (tempUnit == WeatherSettings.UNIT_CELSIUS) observe?.highTempC?.ifBlank { first?.highTempC?.toString().orEmpty() } else observe?.highTempF?.ifBlank { first?.highTempF?.toString().orEmpty() }
    val missing = resources.getString(R.string.weather_null)
    if (observe == null || low.isNullOrBlank() || high.isNullOrBlank()) return resources.getString(R.string.format_weather, missing, missing, unit, missing, unit)
    return resources.getString(R.string.format_weather, resources.getString(WeatherCodeMapping.textResMap[weather.themeCode] ?: R.string.weather_text_99), low, unit, high, unit)
}

private fun isCityNight(weather: Weather): Boolean {
    fun minutes(text: String): Int? {
        val match = Regex("(?:[01]?\\d|2[0-3]):[0-5]\\d").find(text)?.value ?: return null
        val split = match.split(':')
        return split[0].toInt() * 60 + split[1].toInt()
    }
    val explicit = listOfNotNull(minutes(weather.observe.curSunRise), minutes(weather.observe.curSunSet))
    val times = if (explicit.size == 2) explicit else weather.dailyForecast.firstOrNull()?.sunriseAndSunset.orEmpty().split('|').mapNotNull(::minutes).take(2)
    if (times.size != 2) return false
    val now = weather.localTimeAt(Instant.now())
    return now.hour * 60 + now.minute < times[0] || now.hour * 60 + now.minute >= times[1]
}

@Preview(widthDp = 360, heightDp = 720)
@Composable
private fun CityListPreview() {
    CityListScreen(CityListUiState(cities = listOf(SavedCity(locationKey = "101010100", locationName = "北京", sortOrder = 1), SavedCity(locationKey = "101020100", locationName = "上海", sortOrder = 2))), false,
        {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {})
}
