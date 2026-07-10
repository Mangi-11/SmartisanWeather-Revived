package com.smartisan.weather.widget

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

/**
 * A composite [Drawable] that draws a shadow [Drawable] behind a target content
 * [Drawable], inset by the given padding. Ported from the Smartisan UI widget
 * used by [ShadowButton] to layer a shadow 9-patch beneath the button background.
 */
class ShadowDrawable(
    private val shadow: Drawable,
    private val target: Drawable,
    private val insetLeftRight: Int = 0,
    private val insetTopBottom: Int = 0,
) : Drawable() {

    override fun getIntrinsicWidth(): Int = target.intrinsicWidth
    override fun getIntrinsicHeight(): Int = target.intrinsicHeight

    override fun getPadding(padding: Rect): Boolean {
        target.getPadding(padding)
        padding.left += insetLeftRight
        padding.right += insetLeftRight
        padding.top += insetTopBottom
        padding.bottom += insetTopBottom
        return true
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        shadow.setBounds(b.left, b.top, b.right, b.bottom)
        shadow.draw(canvas)
        target.setBounds(
            b.left + insetLeftRight,
            b.top + insetTopBottom,
            b.right - insetLeftRight,
            b.bottom - insetTopBottom,
        )
        target.draw(canvas)
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(left, top, right, bottom)
        target.setBounds(
            left + insetLeftRight,
            top + insetTopBottom,
            right - insetLeftRight,
            bottom - insetTopBottom,
        )
    }

    override fun setBounds(bounds: Rect) {
        super.setBounds(bounds)
        target.setBounds(
            bounds.left + insetLeftRight,
            bounds.top + insetTopBottom,
            bounds.right - insetLeftRight,
            bounds.bottom - insetTopBottom,
        )
    }

    override fun setAlpha(alpha: Int) {
        shadow.alpha = alpha
        target.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        shadow.colorFilter = colorFilter
        target.colorFilter = colorFilter
    }

    @Deprecated("Required by the Android Drawable contract")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
