package com.smartisan.weather.ui.components

import android.graphics.drawable.Drawable
import android.content.Context
import android.graphics.ColorMatrixColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewConfiguration
import androidx.annotation.AnyRes
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.smartisan.weather.R
import com.smartisan.weather.custom.NightWeatherActionDrawable
import com.smartisan.weather.util.ThemeUtils

/** Draws the original selector/NinePatch on a Compose canvas, without a View host. */
@Composable
fun rememberWeatherDrawablePainter(
    @DrawableRes resId: Int,
    enabled: Boolean = true,
    pressed: Boolean = false,
    selected: Boolean = false,
    focused: Boolean = false,
    checked: Boolean = false,
): Painter {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val drawable = remember(context, configuration, resId) {
        context.weatherDrawable(resId)
    }
    return rememberWeatherDrawablePainter(drawable, enabled, pressed, selected, focused, checked)
}

/** Also supports resource-preserving wrappers such as the night action-button artwork. */
@Composable
fun rememberWeatherDrawablePainter(
    drawable: Drawable,
    enabled: Boolean = true,
    pressed: Boolean = false,
    selected: Boolean = false,
    focused: Boolean = false,
    checked: Boolean = false,
): Painter {
    val owner = remember(drawable) { DrawableOwner(drawable) }
    val direction = LocalLayoutDirection.current
    SideEffect {
        owner.painter.drawable.state = weatherDrawableState(enabled, pressed, selected, focused, checked)
        owner.painter.drawable.layoutDirection = if (direction == LayoutDirection.Rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
    }
    return owner.painter
}

private fun Context.weatherDrawable(@DrawableRes resId: Int): Drawable {
    val source = requireNotNull(getDrawable(resId)).mutate()
    if (!ThemeUtils.isNightMode(this)) return source
    return when (resId) {
        R.drawable.selector_hot_city_item, R.drawable.selector_location_city_item -> NightWeatherActionDrawable(
            source,
            getColor(R.color.app_surface_color),
            getColor(R.color.app_surface_pressed_color),
            getColor(R.color.app_surface_disabled_color),
            if (resId == R.drawable.selector_location_city_item) getColor(R.color.app_tertiary_text_color) else null,
        )
        R.drawable.search_bar_edit_bg_selector -> source.apply {
            colorFilter = PorterDuffColorFilter(getColor(R.color.app_surface_raised_color), PorterDuff.Mode.MULTIPLY)
        }
        R.drawable.search_bar_left_icon_selector -> source.apply {
            val color = getColor(R.color.search_icon_normal)
            colorFilter = ColorMatrixColorFilter(floatArrayOf(
                0f, 0f, 0f, 0f, android.graphics.Color.red(color).toFloat(),
                0f, 0f, 0f, 0f, android.graphics.Color.green(color).toFloat(),
                0f, 0f, 0f, 0f, android.graphics.Color.blue(color).toFloat(),
                0f, 0f, 0f, 255f / 77f, 0f,
            ))
        }
        else -> source
    }
}

@Composable
fun WeatherDrawable(
    @DrawableRes resId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pressed: Boolean = false,
    selected: Boolean = false,
    checked: Boolean = false,
    contentScale: ContentScale = ContentScale.FillBounds,
) {
    Image(
        painter = rememberWeatherDrawablePainter(resId, enabled, pressed, selected, checked = checked),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

/** Background drawing never lets a drawable's intrinsic size change layout constraints. */
@Composable
fun Modifier.weatherDrawableBackground(
    @DrawableRes resId: Int,
    enabled: Boolean = true,
    pressed: Boolean = false,
    selected: Boolean = false,
): Modifier {
    val painter = rememberWeatherDrawablePainter(resId, enabled, pressed, selected)
    return drawBehind { with(painter) { draw(size) } }
}

@Composable
fun weatherTextSize(@DimenRes resId: Int): TextUnit =
    with(LocalDensity.current) { LocalResources.current.getDimension(resId).toSp() }

@Composable
fun weatherStateColor(
    @AnyRes resId: Int,
    enabled: Boolean = true,
    pressed: Boolean = false,
    selected: Boolean = false,
    focused: Boolean = false,
): Color {
    val resources = LocalResources.current
    val context = LocalContext.current
    val colors = remember(resources, context.theme, resId) { resources.getColorStateList(resId, context.theme) }
    return Color(colors.getColorForState(weatherDrawableState(enabled, pressed, selected, focused), colors.defaultColor))
}

internal fun weatherDrawableState(
    enabled: Boolean,
    pressed: Boolean,
    selected: Boolean,
    focused: Boolean,
    checked: Boolean = false,
): IntArray = intArrayOf(
    if (enabled) android.R.attr.state_enabled else -android.R.attr.state_enabled,
    if (pressed) android.R.attr.state_pressed else -android.R.attr.state_pressed,
    if (selected) android.R.attr.state_selected else -android.R.attr.state_selected,
    if (focused) android.R.attr.state_focused else -android.R.attr.state_focused,
    if (checked) android.R.attr.state_checked else -android.R.attr.state_checked,
)

/** A quick release still renders the platform's pressed-state interval; cancellation is immediate. */
@Composable
fun InteractionSource.collectWeatherPressedAsState(): State<Boolean> {
    val pressed = remember(this) { mutableStateOf(false) }
    LaunchedEffect(this) {
        val active = mutableSetOf<PressInteraction.Press>()
        var release: Job? = null
        interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    release?.cancel()
                    active.add(interaction)
                    pressed.value = true
                }
                is PressInteraction.Release -> if (active.remove(interaction.press) && active.isEmpty()) {
                    release = launch {
                        val start = withFrameNanos { it }
                        val duration = ViewConfiguration.getPressedStateDuration() * 1_000_000L
                        while (withFrameNanos { it } - start < duration) { /* Keep the pressed frame visible. */ }
                        pressed.value = false
                    }
                }
                is PressInteraction.Cancel -> if (active.remove(interaction.press) && active.isEmpty()) {
                    release?.cancel()
                    pressed.value = false
                }
            }
        }
    }
    return pressed
}

private class DrawableOwner(drawable: Drawable) : RememberObserver {
    val painter = WeatherDrawablePainter(drawable)
    override fun onRemembered() = painter.attach()
    override fun onForgotten() = painter.detach()
    override fun onAbandoned() = painter.detach()
}

private class WeatherDrawablePainter(val drawable: Drawable) : Painter(), Drawable.Callback {
    private var invalidation by mutableIntStateOf(0)
    private val handler = Handler(Looper.getMainLooper())
    override val intrinsicSize: Size
        get() = if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
            Size(drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        } else Size.Unspecified

    override fun DrawScope.onDraw() {
        @Suppress("UNUSED_VARIABLE") val generation = invalidation
        drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
        drawIntoCanvas { drawable.draw(it.nativeCanvas) }
    }

    fun attach() {
        drawable.callback = this
        drawable.setVisible(true, true)
    }

    fun detach() {
        drawable.setVisible(false, false)
        drawable.callback = null
        handler.removeCallbacksAndMessages(drawable)
    }

    override fun invalidateDrawable(who: Drawable) { invalidation++ }
    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        handler.postAtTime(what, drawable, `when`)
    }
    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        handler.removeCallbacks(what, drawable)
    }
}
