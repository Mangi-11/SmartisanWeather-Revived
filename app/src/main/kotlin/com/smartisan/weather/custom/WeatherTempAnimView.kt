package com.smartisan.weather.custom

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import com.smartisan.weather.R

/**
 * Animates between Celsius and Fahrenheit children while safely settling
 * interrupted or recycled views into their latest unit state.
 */
class WeatherTempAnimView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var displayedChildIndex = 0
    private var animationGeneration = 0

    private fun showChild(
        targetIndex: Int,
        animate: Boolean,
        incomingAnimationRes: Int,
        outgoingAnimationRes: Int,
    ) {
        displayedChildIndex = targetIndex
        val generation = ++animationGeneration
        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            child.clearAnimation()
            if (i == targetIndex) {
                child.visibility = VISIBLE
                if (animate) {
                    child.startAnimation(AnimationUtils.loadAnimation(context, incomingAnimationRes))
                }
            } else if (animate && child.visibility == VISIBLE) {
                val outgoingAnimation = AnimationUtils.loadAnimation(context, outgoingAnimationRes)
                outgoingAnimation.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) = Unit

                    override fun onAnimationRepeat(animation: Animation?) = Unit

                    override fun onAnimationEnd(animation: Animation?) {
                        if (generation == animationGeneration) {
                            child.clearAnimation()
                            child.visibility = GONE
                        }
                    }
                })
                child.startAnimation(outgoingAnimation)
            } else {
                child.visibility = GONE
            }
        }
    }

    fun showCView(animate: Boolean) {
        showChild(
            targetIndex = 0,
            animate = animate,
            incomingAnimationRes = R.anim.weather_temp_in_f2c,
            outgoingAnimationRes = R.anim.weather_temp_out_f2c,
        )
    }

    fun showFView(animate: Boolean) {
        showChild(
            targetIndex = 1,
            animate = animate,
            incomingAnimationRes = R.anim.weather_temp_in_c2f,
            outgoingAnimationRes = R.anim.weather_temp_out_c2f,
        )
    }

    override fun onDetachedFromWindow() {
        animationGeneration++
        for (i in 0 until childCount) {
            getChildAt(i)?.run {
                clearAnimation()
                visibility = if (i == displayedChildIndex) VISIBLE else GONE
            }
        }
        super.onDetachedFromWindow()
    }
}
