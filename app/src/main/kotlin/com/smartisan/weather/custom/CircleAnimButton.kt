package com.smartisan.weather.custom

import android.animation.Animator
import android.animation.ObjectAnimator
import android.graphics.ColorFilter
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import com.smartisan.weather.AnimatorListenerImpl
import com.smartisan.weather.R

/**
 * Wraps a refresh button (ImageButton + rotating ImageView) and drives a continuous
 * rotation animation that can be started/stopped.
 *
 * Ported from the decompiled Java class `com.smartisan.weather.custom.CircleAnimButton`.
 */
class CircleAnimButton(view: View) {

    private val buttonView: View = view
    private val imageButton: ImageButton? = view.findViewById(R.id.imagebutton_refresh)
    private val refreshIcon: ImageView? = view.findViewById(R.id.imageView_refresh_icon)

    @Volatile
    private var stopRequested: Boolean = false
    private var refreshBgRes: Int = R.drawable.selector_weather_refresh_sunny
    private var refreshBackgroundDecorator: ((Drawable) -> Drawable)? = null
    private var refreshIconFilter: ColorFilter? = null

    private val rotationAnimator: ObjectAnimator? = refreshIcon?.let { icon ->
        ObjectAnimator.ofFloat(icon, "rotation", 0f, 360f).apply {
            interpolator = LinearInterpolator()
            duration = 1000L
            repeatMode = ObjectAnimator.RESTART
            repeatCount = 100
            addListener(object : AnimatorListenerImpl() {
                override fun onAnimationCancel(animation: Animator) {}

                override fun onAnimationEnd(animation: Animator) {
                    stopRequested = false
                    imageButton?.isEnabled = true
                    imageButton?.isClickable = true
                    imageButton?.setBackgroundResource(refreshBgRes)
                    applyRefreshBackgroundDecorator()
                }

                override fun onAnimationRepeat(animation: Animator) {
                    if (stopRequested) {
                        rotationAnimator?.end()
                    }
                }

                override fun onAnimationStart(animation: Animator) {
                    stopRequested = false
                    imageButton?.isEnabled = false
                    imageButton?.isClickable = false
                }
            })
        }
    }

    val button: View?
        get() = imageButton

    val image: View?
        get() = refreshIcon

    fun run() {
        rotationAnimator?.start()
    }

    fun setAlpha(alpha: Float) {
        imageButton?.alpha = alpha
        refreshIcon?.alpha = alpha
    }

    fun setOnClickListener(listener: View.OnClickListener?) {
        imageButton?.setOnClickListener(listener)
    }

    fun setRefreshBgRes(res: Int) {
        refreshBgRes = res
        imageButton?.setBackgroundResource(res)
        applyRefreshBackgroundDecorator()
    }

    fun setRefreshSrcRes(res: Int) {
        refreshIcon?.setBackgroundResource(res)
        applyRefreshIconFilter()
    }

    fun setRefreshBackgroundDecorator(decorator: ((Drawable) -> Drawable)?) {
        refreshBackgroundDecorator = decorator
    }

    fun setRefreshIconFilter(filter: ColorFilter?) {
        refreshIconFilter = filter
        applyRefreshIconFilter()
    }

    private fun applyRefreshBackgroundDecorator() {
        val decorator = refreshBackgroundDecorator ?: return
        val background = imageButton?.background?.mutate() ?: return
        imageButton.background = decorator(background)
    }

    private fun applyRefreshIconFilter() {
        refreshIcon?.background?.mutate()?.let { drawable ->
            if (refreshIconFilter == null) {
                drawable.clearColorFilter()
            } else {
                drawable.colorFilter = refreshIconFilter
            }
        }
    }

    fun setVisibility(visibility: Int) {
        imageButton?.visibility = visibility
        refreshIcon?.visibility = visibility
    }

    fun startAnimation(animation: Animation?) {
        imageButton?.startAnimation(animation)
        refreshIcon?.startAnimation(animation)
    }

    fun stop() {
        stopRequested = true
    }
}
