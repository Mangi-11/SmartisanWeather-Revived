package com.smartisan.weather.ui.search

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

/** Original infinite-drag resistance and viscous 250 ms return, without a View parent. */
@Stable
internal class SearchElasticState(private val density: Float, private val screenHeight: Float, private val scope: CoroutineScope) : NestedScrollConnection {
    var offset by mutableFloatStateOf(0f)
        private set
    var height = 1f
    private var rawDistance = 0f
    private var rebound: Job? = null

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput) rebound?.cancel()
        if (rawDistance == 0f || sign(available.y) == sign(rawDistance)) return Offset.Zero
        val consumed = sign(available.y) * min(abs(available.y), abs(rawDistance))
        rawDistance += consumed
        updateOffset()
        return Offset(0f, consumed)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (available.y == 0f) return Offset.Zero
        rebound?.cancel()
        rawDistance += available.y
        updateOffset()
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (rawDistance == 0f) return Velocity.Zero
        rebound()
        return available
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        rebound()
        return available
    }

    fun onTouchStart() {
        rebound?.cancel()
        rebound = null
    }

    /** Pointer cancellation may not generate a nested fling callback. */
    fun onTouchEnd() = rebound()

    fun dispose() {
        rebound?.cancel()
        rebound = null
        rawDistance = 0f
        offset = 0f
    }

    private fun updateOffset() {
        offset = searchElasticOffset(rawDistance, density, maxOf(screenHeight / 2f, height))
    }

    private fun rebound() {
        rebound?.cancel()
        if (offset == 0f) return
        rebound = scope.launch {
            animate(offset, 0f, animationSpec = tween(250, easing = SearchViscousEasing)) { value, _ ->
                offset = value
                // The animation follows visual pixels. Keep gesture distance at that same
                // position, so a new drag continues here instead of jumping to release distance.
                rawDistance = searchElasticRawDistance(value, density, maxOf(screenHeight / 2f, height))
            }
            rawDistance = 0f
            offset = 0f
        }
    }
}

internal fun searchElasticOffset(rawDistance: Float, density: Float, resistanceDistance: Float): Float {
    if (rawDistance == 0f) return 0f
    val dragged = abs(rawDistance * 0.5f)
    val maxDrag = density * (if (rawDistance > 0f) 100f else 60f) * 2.5f
    val resisted = maxDrag * (1.0 - 100.0.pow(-dragged.toDouble() / resistanceDistance.coerceAtLeast(1f))).toFloat()
    return sign(rawDistance) * min(resisted, dragged)
}

/** Inverse of min(resistedDistance, draggedDistance), retaining the original rebound curve. */
private fun searchElasticRawDistance(offset: Float, density: Float, resistanceDistance: Float): Float {
    if (offset == 0f) return 0f
    val distance = abs(offset).toDouble()
    val maxDrag = density * (if (offset > 0f) 100f else 60f) * 2.5f
    val fraction = (distance / maxDrag).coerceAtMost(Math.nextDown(1.0))
    val resistedDistance = -resistanceDistance.coerceAtLeast(1f) * ln1p(-fraction) / ln(100.0)
    return (sign(offset) * max(distance, resistedDistance) / 0.5).toFloat()
}

internal val SearchViscousEasing: Easing = Easing { input ->
    fun viscous(value: Float): Float {
        val scaled = value * 8f
        return if (scaled < 1f) scaled - (1f - exp(-scaled))
        else (1f - exp(1f - scaled)) * 0.63212055f + 0.36787945f
    }
    val normalize = 1f / viscous(1f)
    val result = normalize * viscous(input)
    if (result > 0f) result + (1f - normalize * viscous(1f)) else result
}

@Composable
internal fun rememberSearchElasticState(): SearchElasticState {
    val density = LocalDensity.current.density
    val screenHeight = LocalConfiguration.current.screenHeightDp * density
    val scope = rememberCoroutineScope()
    val state = remember(density, screenHeight, scope) { SearchElasticState(density, screenHeight, scope) }
    DisposableEffect(state) { onDispose { state.dispose() } }
    return state
}

internal fun Modifier.searchElasticContainer(state: SearchElasticState): Modifier =
    onSizeChanged { state.height = it.height.toFloat() }.nestedScroll(state).pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            state.onTouchStart()
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                } while (event.changes.any { it.pressed })
            } finally {
                state.onTouchEnd()
            }
        }
    }

internal fun Modifier.searchElasticContent(state: SearchElasticState): Modifier =
    graphicsLayer { translationY = state.offset }
