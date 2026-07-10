package com.smartisan.weather.custom

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import com.smartisan.weather.R

/**
 * A [FrameLayout] that animates between a Celsius child (index 0) and a Fahrenheit
 * child (index 1) using slide-in / slide-out translate animations.
 *
 * Ported from the decompiled Java class `com.smartisan.weather.custom.WeatherTempAnimView`.
 */
class WeatherTempAnimView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var inAnimation: Animation? = null
    private var outAnimation: Animation? = null

    private fun showChild(targetIndex: Int, animate: Boolean) {
        val childCount = childCount
        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            if (i == targetIndex) {
                if (animate) {
                    inAnimation?.let { anim ->
                        child.clearAnimation()
                        child.visibility = VISIBLE
                        child.startAnimation(anim)
                    }
                }
                child.visibility = VISIBLE
            } else if (animate && outAnimation != null && child.visibility == VISIBLE) {
                child.startAnimation(outAnimation)
                child.postDelayed({
                    child.clearAnimation()
                    child.visibility = GONE
                }, 300L)
            } else {
                if (child.animation === inAnimation) {
                    child.clearAnimation()
                }
                child.visibility = GONE
            }
        }
    }

    fun showCView(animate: Boolean) {
        inAnimation = AnimationUtils.loadAnimation(context, R.anim.weather_temp_in_f2c)
        outAnimation = AnimationUtils.loadAnimation(context, R.anim.weather_temp_out_f2c)
        showChild(0, animate)
    }

    fun showFView(animate: Boolean) {
        inAnimation = AnimationUtils.loadAnimation(context, R.anim.weather_temp_in_c2f)
        outAnimation = AnimationUtils.loadAnimation(context, R.anim.weather_temp_out_c2f)
        showChild(1, animate)
    }
}
