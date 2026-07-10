package com.smartisan.weather

import android.animation.Animator

/**
 * Default no-op implementation of [Animator.AnimatorListener].
 * Subclasses can override only the callbacks they need.
 */
open class AnimatorListenerImpl : Animator.AnimatorListener {
    override fun onAnimationStart(animation: Animator) {}
    override fun onAnimationStart(animation: Animator, isReverse: Boolean) = onAnimationStart(animation)
    override fun onAnimationEnd(animation: Animator) {}
    override fun onAnimationEnd(animation: Animator, isReverse: Boolean) = onAnimationEnd(animation)
    override fun onAnimationCancel(animation: Animator) {}
    override fun onAnimationRepeat(animation: Animator) {}
}
