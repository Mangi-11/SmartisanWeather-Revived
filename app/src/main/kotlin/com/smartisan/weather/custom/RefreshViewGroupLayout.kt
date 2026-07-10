package com.smartisan.weather.custom

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.RelativeLayout

/**
 * A [RelativeLayout] that disallows touch-intercept from its parents and always
 * consumes touch events, so children can handle the full gesture stream.
 *
 * Ported from the decompiled Java class `com.smartisan.weather.custom.RefreshViewGroupLayout`.
 */
class RefreshViewGroupLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        requestDisallowInterceptTouchEvent(true)
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        super.onTouchEvent(event)
        return true
    }
}
