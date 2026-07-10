package com.smartisan.weather

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout

/**
 * A two-child [FrameLayout] that acts like a ViewSwitcher, cycling between
 * `mChildCount` (default 2) [WeatherContentViewUtil] children and running the
 * content in/out animations when the displayed child changes.
 *
 * Ported from the decompiled `com.smartisan.weather.WeatherSwticher`.
 *
 * Note: [WeatherContentViewUtil] is a sibling custom View that has not been
 * ported yet; this class references it directly as in the original source.
 */
open class WeatherSwticher : FrameLayout {
    /** Index of the currently displayed child (mirrors the original field `a`). */
    @JvmField
    internal var a: Int = 0

    /** Direction flag: `true` when switching to previous, `false` when switching to next. */
    private var b: Boolean = false

    @JvmField
    var mChildCount: Int = 2

    @JvmField
    var mIsThemeChange: Boolean = true

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    /**
     * Runs the content in/out animations for the child being switched to/from.
     * Mirrors the original package-private `b(int)`.
     */
    internal fun b(i: Int) {
        for (i2 in 0 until mChildCount) {
            val viewNonNull = getChildAt(i2)!!
            if (i2 == i) {
                if (viewNonNull is WeatherContentViewUtil) {
                    viewNonNull.clearAnimation()
                    if (b) {
                        viewNonNull.doContentInAnimationBoth(mIsThemeChange, false)
                    } else {
                        viewNonNull.doContentInAnimationBoth(mIsThemeChange, true)
                    }
                }
            } else if (viewNonNull is WeatherContentViewUtil) {
                if (b) {
                    viewNonNull.doContentAnimationOutBoth(mIsThemeChange, true)
                } else {
                    viewNonNull.doContentAnimationOutBoth(mIsThemeChange, false)
                }
            }
        }
    }

    fun getCurrentView(): WeatherContentViewUtil = getChildAt(a) as WeatherContentViewUtil

    fun getNextIndex(): Int {
        val i = a + 1
        val count = mChildCount
        if (i >= count) {
            return 0
        }
        return if (i < 0) count - 1 else i
    }

    fun getPreviousIndex(): Int {
        val i = a - 1
        val count = mChildCount
        if (i >= count) {
            return 0
        }
        return if (i < 0) count - 1 else i
    }

    fun setDisplayedChild(i: Int) {
        a = i
        val count = mChildCount
        if (i >= count) {
            a = 0
        } else if (i < 0) {
            a = count - 1
        }
        val hadFocus = focusedChild != null
        b(a)
        if (hadFocus) {
            requestFocus(View.FOCUS_FORWARD)
        }
    }

    open fun showNext() {
        b = false
        setDisplayedChild(a + 1)
    }

    open fun showPrevious() {
        b = true
        setDisplayedChild(a - 1)
    }
}
