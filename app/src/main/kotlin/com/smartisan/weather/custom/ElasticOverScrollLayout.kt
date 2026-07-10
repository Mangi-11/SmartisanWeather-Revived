package com.smartisan.weather.custom

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.Interpolator
import android.widget.FrameLayout
import androidx.core.view.NestedScrollingParent3
import androidx.core.view.NestedScrollingParentHelper
import androidx.core.view.ViewCompat
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

/**
 * AndroidX-native vertical edge drag and rebound container used by city search results.
 *
 * The original screen enabled only SmartRefreshLayout's over-scroll drag/bounce features;
 * refresh and load-more were disabled. SmartRefresh's stable 2.x artifact still depends on
 * the removed support-v4 namespace, while its AndroidX rewrite is alpha-only. This focused
 * View keeps the original drag resistance and viscous-fluid rebound without either
 * compatibility library.
 */
class ElasticOverScrollLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), NestedScrollingParent3 {

    private val nestedParentHelper = NestedScrollingParentHelper(this)
    private val reboundInterpolator = ViscousFluidInterpolator()
    private var contentOffset = 0f
    private var totalUnconsumed = 0f
    private var reboundAnimator: ValueAnimator? = null

    override fun onStartNestedScroll(
        child: View,
        target: View,
        axes: Int,
        type: Int,
    ): Boolean {
        val accepts = axes and ViewCompat.SCROLL_AXIS_VERTICAL != 0
        if (accepts) {
            reboundAnimator?.cancel()
            totalUnconsumed = if (contentOffset == 0f) 0f else contentOffset / DRAG_RATE
        }
        return accepts
    }

    override fun onNestedScrollAccepted(
        child: View,
        target: View,
        axes: Int,
        type: Int,
    ) {
        nestedParentHelper.onNestedScrollAccepted(child, target, axes, type)
    }

    override fun onStopNestedScroll(target: View, type: Int) {
        nestedParentHelper.onStopNestedScroll(target, type)
        rebound()
    }

    override fun onNestedPreScroll(
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int,
    ) {
        if (dy * totalUnconsumed > 0f) {
            val consumedY = min(abs(dy.toFloat()), abs(totalUnconsumed)).toInt() *
                if (dy > 0) 1 else -1
            totalUnconsumed -= consumedY
            if (abs(totalUnconsumed) < 1f) totalUnconsumed = 0f
            setContentOffset(calculateOffset(totalUnconsumed))
            consumed[1] += consumedY
        }
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray,
    ) {
        if (dyUnconsumed == 0) return
        totalUnconsumed -= dyUnconsumed
        setContentOffset(calculateOffset(totalUnconsumed))
        consumed[1] += dyUnconsumed
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
    ) {
        onNestedScroll(
            target,
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            type,
            IntArray(2),
        )
    }

    override fun getNestedScrollAxes(): Int = nestedParentHelper.nestedScrollAxes

    private fun setContentOffset(offset: Float) {
        contentOffset = offset
        getChildAt(0)?.translationY = offset
    }

    private fun rebound() {
        reboundAnimator?.cancel()
        if (contentOffset == 0f) return
        reboundAnimator = ValueAnimator.ofFloat(contentOffset, 0f).apply {
            duration = REBOUND_DURATION_MILLIS
            interpolator = reboundInterpolator
            addUpdateListener { animator ->
                setContentOffset(animator.animatedValue as Float)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    reboundAnimator = null
                    totalUnconsumed = 0f
                    setContentOffset(0f)
                }
            })
            start()
        }
    }

    /** SmartRefreshLayout.moveSpinnerInfinitely() in pure over-scroll mode. */
    private fun calculateOffset(rawDistance: Float): Float {
        if (rawDistance == 0f) return 0f
        val density = resources.displayMetrics.density
        val resistanceDistance = maxOf(
            resources.displayMetrics.heightPixels / 2.0,
            height.toDouble(),
        ).coerceAtLeast(1.0)
        val dragged = abs(rawDistance * DRAG_RATE).toDouble()
        val maxDrag = density * if (rawDistance > 0f) {
            HEADER_HEIGHT_DP * MAX_DRAG_RATE
        } else {
            FOOTER_HEIGHT_DP * MAX_DRAG_RATE
        }
        val resisted = maxDrag * (1.0 - 100.0.pow(-dragged / resistanceDistance))
        val offset = min(resisted, dragged).toFloat()
        return if (rawDistance > 0f) offset else -offset
    }

    override fun onDetachedFromWindow() {
        reboundAnimator?.cancel()
        reboundAnimator = null
        totalUnconsumed = 0f
        setContentOffset(0f)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val DRAG_RATE = 0.5f
        const val MAX_DRAG_RATE = 2.5f
        const val HEADER_HEIGHT_DP = 100f
        const val FOOTER_HEIGHT_DP = 60f
        const val REBOUND_DURATION_MILLIS = 250L
    }

    /** Exact interpolator used by the original embedded SmartRefreshLayout. */
    private class ViscousFluidInterpolator : Interpolator {
        override fun getInterpolation(input: Float): Float {
            val interpolated = NORMALIZE * viscousFluid(input)
            return if (interpolated > 0f) interpolated + OFFSET else interpolated
        }

        private companion object {
            fun viscousFluid(input: Float): Float {
                val scaled = input * 8f
                return if (scaled < 1f) {
                    scaled - (1f - kotlin.math.exp(-scaled))
                } else {
                    ((1f - kotlin.math.exp(1f - scaled)) * 0.63212055f) + 0.36787945f
                }
            }

            val NORMALIZE = 1f / viscousFluid(1f)
            val OFFSET = 1f - NORMALIZE * viscousFluid(1f)
        }
    }
}
