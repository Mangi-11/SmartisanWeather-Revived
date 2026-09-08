package com.smartisan.weather.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartisan.weather.R
import com.smartisan.weather.custom.TemperatureDrawableResources
import com.smartisan.weather.ui.components.WeatherText
import com.smartisan.weather.ui.components.rememberWeatherDrawablePainter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

internal val WeatherDecelerate = Easing { 1f - (1f - it) * (1f - it) }
internal val WeatherCubicDecelerate = Easing { 1f - (1f - it) * (1f - it) * (1f - it) }
internal val WeatherAccelerateDecelerate = Easing { ((kotlin.math.cos((it + 1f) * Math.PI) / 2.0) + 0.5).toFloat() }
internal val WeatherOvershoot = Easing {
    val t = it - 1f
    t * t * (1.5f * t + 0.5f) + 1f
}

@Composable
internal fun weatherDpTextSize(value: Float) = with(LocalDensity.current) { value.dp.toSp() }

/** Original 300 ms C/F translations; rapid reversals cancel into the latest unit. */
@Composable
internal fun SmallTemperature(
    celsius: String,
    fahrenheit: String,
    isCelsius: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    size: Float = 13.5f,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
      AnimatedContent(
        targetState = isCelsius,
        modifier = Modifier.clipToBounds(),
        contentAlignment = Alignment.Center,
        transitionSpec = {
            val direction = if (targetState) -1 else 1
            (slideInVertically(tween(300, easing = WeatherCubicDecelerate)) { it * direction } togetherWith
                slideOutVertically(tween(300, easing = WeatherCubicDecelerate)) { -it * direction })
                .using(SizeTransform(clip = true))
        },
        label = "forecast temperature unit",
    ) { c ->
        WeatherText(if (c) celsius else fahrenheit, color = color, fontSize = weatherDpTextSize(size), fontWeight = FontWeight.Bold, maxLines = 1)
      }
    }
}

/**
 * Main digits keep the APK artwork and its -2 dp tracking. A single cancellable
 * timeline drives the rolling strip, unit fade and unchanged-value shake.
 */
@Composable
internal fun MainTemperature(
    tempC: Int,
    tempF: Int,
    isCelsius: Boolean,
    refreshVersion: Long,
    modifier: Modifier = Modifier,
) {
    val target = if (isCelsius) tempC else tempF
    var previous by remember { mutableIntStateOf(target) }
    var previousUnit by remember { mutableStateOf(isCelsius) }
    var previousVersion by remember { mutableStateOf(refreshVersion) }
    var start by remember { mutableIntStateOf(target) }
    var end by remember { mutableIntStateOf(target) }
    var rolling by remember { mutableStateOf(false) }
    var animating by remember { mutableStateOf(false) }
    var blurryStrip by remember { mutableStateOf(false) }
    var rollingUp by remember { mutableStateOf(true) }
    val progress = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    val unitAlpha = remember { Animatable(1f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(target, isCelsius, refreshVersion, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val oldValue = previous
            val unitChanged = previousUnit != isCelsius
            val completedRefresh = previousVersion != refreshVersion
            previous = target
            previousUnit = isCelsius
            previousVersion = refreshVersion
            shake.snapTo(0f)
            animating = true
            try {
                if (oldValue != target) {
                    start = oldValue
                    end = target
                    blurryStrip = unitChanged && abs(target - oldValue) >= 2
                    rollingUp = !isCelsius
                    rolling = true
                    unitAlpha.snapTo(0f)
                    progress.snapTo(0f)
                    progress.animateTo(1f, tween(1_400, easing = WeatherOvershoot))
                    rolling = false
                    unitAlpha.animateTo(1f, tween(if (unitChanged) 300 else 50, easing = WeatherDecelerate))
                } else {
                    rolling = false
                    unitAlpha.snapTo(1f)
                    if (completedRefresh && !unitChanged) {
                        delay(70)
                        shake.animateTo(-5f, tween(125, easing = WeatherAccelerateDecelerate))
                        shake.animateTo(5f, tween(250, easing = WeatherAccelerateDecelerate))
                        shake.animateTo(0f, tween(250, easing = WeatherDecelerate))
                    }
                }
            } finally {
                // A stopped host or a newer value must never leave a clipped strip or partial unit behind.
                rolling = false
                animating = false
            }
        }
    }

    val painters = (0..9).map { rememberWeatherDrawablePainter(TemperatureDrawableResources.digit(it)) }
    val firstScrollPainters = (0..9).map { rememberWeatherDrawablePainter(TemperatureDrawableResources.digit(it, 1)) }
    val secondScrollPainters = (0..9).map { rememberWeatherDrawablePainter(TemperatureDrawableResources.digit(it, 2)) }
    val blurryPainters = (1..3).map { rememberWeatherDrawablePainter(TemperatureDrawableResources.blurry(it)) }
    val minus = rememberWeatherDrawablePainter(TemperatureDrawableResources.minus())
    val firstScrollMinus = rememberWeatherDrawablePainter(TemperatureDrawableResources.minus(1))
    val secondScrollMinus = rememberWeatherDrawablePainter(TemperatureDrawableResources.minus(2))
    val unit = rememberWeatherDrawablePainter(if (isCelsius) R.drawable.anim_c else R.drawable.anim_f)
    val density = LocalDensity.current
    fun digitWidth(value: Int): Float = abs(value).toString().length * 34f + if (value < 0) 22f else 0f
    val width = max(digitWidth(start), max(digitWidth(end), digitWidth(target))) + 36f
    val temperatureDescription = stringResource(if (isCelsius) R.string.weather_c_icon_description else R.string.weather_f_icon_description, target)

    Canvas(
        modifier.width(width.dp).height(75.dp).clipToBounds().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .testTag("weather_main_temperature").semantics { contentDescription = temperatureDescription },
    ) {
        val height = 75.dp.toPx()
        fun drawNumber(value: Int, y: Float, style: Int = 0) {
            if (y >= height || y <= -height) return
            val digitPainters = when (style) { 1 -> firstScrollPainters; 2 -> secondScrollPainters; else -> painters }
            val signPainter = when (style) { 1 -> firstScrollMinus; 2 -> secondScrollMinus; else -> minus }
            var x = 0f
            if (value < 0) {
                translate(left = x, top = y) { with(signPainter) { draw(Size(22.dp.toPx(), height)) } }
                x += 22.dp.toPx()
            }
            abs(value).toString().forEach { char ->
                x -= 2.dp.toPx()
                translate(left = x, top = y) { with(digitPainters[char.digitToInt()]) { draw(Size(36.dp.toPx(), height)) } }
                x += 36.dp.toPx()
            }
        }
        if (rolling && blurryStrip) {
            // Original unit strip: three sharp/blurred numbers, two 575 dp motion
            // images, then three settling numbers. Its travel is independent of ΔT.
            val first = if (rollingUp) start else end
            val last = if (rollingUp) end else start
            val step = if (last > first) 1 else -1
            val scroll = (if (rollingUp) progress.value else 1f - progress.value) * 1525.dp.toPx()
            drawNumber(first, -scroll)
            drawNumber(first + step, height - scroll, 1)
            drawNumber(first + step * 2, height * 2 - scroll, 2)
            val blurHeight = 575.dp.toPx()
            listOf(first + step * 2, last - step * 2).forEachIndexed { index, value ->
                val y = height * 3 + index * blurHeight - scroll
                if (y < height && y + blurHeight > 0) {
                    val count = abs(value).toString().length.coerceIn(1, 3)
                    val painter = blurryPainters[count - 1]
                    translate(top = y) { with(painter) { draw(Size(painter.intrinsicSize.width, blurHeight)) } }
                }
            }
            drawNumber(last - step * 2, 1375.dp.toPx() - scroll, 2)
            drawNumber(last - step, 1450.dp.toPx() - scroll, 1)
            drawNumber(last, 1525.dp.toPx() - scroll)
        } else if (rolling) {
            val steps = abs(end - start)
            val direction = if (end >= start) 1 else -1
            val position = progress.value * steps
            val center = floor(position).toInt()
            // Only the visible part of the number strip is painted, even for C/F's long journey.
            for (step in (center - 1)..(center + 1)) {
                if (step in 0..steps) drawNumber(start + direction * step, (step - position) * height)
            }
        } else {
            drawNumber(target, with(density) { (if (animating) shake.value else 0f).dp.toPx() })
        }
        if (rolling) {
            drawRect(Brush.verticalGradient(0f to Color.Transparent, (20f / 75f) to Color.Black, (55f / 75f) to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
        }
        translate(left = digitWidth(target).dp.toPx() - 2.dp.toPx()) {
            with(unit) { draw(Size(36.dp.toPx(), height), alpha = if (animating) unitAlpha.value else 1f) }
        }
    }
}
