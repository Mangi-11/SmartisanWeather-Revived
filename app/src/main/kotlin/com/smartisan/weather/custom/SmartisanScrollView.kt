package com.smartisan.weather.custom

import android.content.Context
import android.util.AttributeSet
import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.Scroller

/**
 * A vertical scroll container that performs animated programmatic scrolls via [Scroller]
 * with an [OvershootInterpolator].
 *
 * Ported from the decompiled Java class `com.smartisan.weather.custom.SmartisanScrollView`.
 */
class SmartisanScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    interface ScrollEndListener {
        fun onScrollEnd()
    }

    private var scroller: Scroller = Scroller(context, OvershootInterpolator())
    private var scrollEndListener: ScrollEndListener? = null
    private var scrolling: Boolean = false
    private var duration: Int = SCROLL_DURATION

    init {
        setWillNotDraw(false)
    }

    fun abortAnimationIfRunning() {
        if (scroller.isFinished) return
        scroller.abortAnimation()
        scrollEndListener?.onScrollEnd()
        scrolling = false
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrolling = true
            scrollTo(scroller.currX, scroller.currY)
            postInvalidate()
        } else if (scrolling) {
            scrollEndListener?.onScrollEnd()
            scrolling = false
        }
    }

    override fun getBottomFadingEdgeStrength(): Float = 1.0f

    override fun getTopFadingEdgeStrength(): Float = 1.0f

    fun reset() {
        scrolling = false
        scrollTo(0, 0)
        invalidate()
    }

    fun setDuringTime(duration: Int) {
        this.duration = duration
    }

    fun setInterpolator(interpolator: Interpolator) {
        scroller = Scroller(context, interpolator)
    }

    fun setScrollEndListener(listener: ScrollEndListener) {
        this.scrollEndListener = listener
    }

    /**
     * Scrolls down by the combined height of all children except the last one,
     * then animates back up to the original position.
     */
    fun startDownScroll() {
        val childCount = childCount
        var height = 0
        for (i in 0 until childCount - 1) {
            height += (getChildAt(i)?.height ?: 0)
        }
        scrollBy(0, height)
        scroller.startScroll(0, height, 0, -height, duration)
    }

    /**
     * Animates a scroll from the top to the bottom by the combined height of all
     * children except the last one.
     */
    fun startUpScroll() {
        val childCount = childCount
        var height = 0
        for (i in 0 until childCount - 1) {
            height += (getChildAt(i)?.height ?: 0)
        }
        scroller.startScroll(0, 0, 0, height, duration)
        invalidate()
    }

    companion object {
        const val SCROLL_DURATION = 1400
    }
}
