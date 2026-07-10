package com.smartisan.weather.custom

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.animation.TranslateAnimation
import android.widget.AbsListView

/**
 * 城市列表 ListView，复刻自原版 com.smartisan.weather.custom.WeatherListView。
 *
 * 拖拽排序由本地 Kotlin [DragSortListView] 承载，触摸入口、浮动行、边缘滚动、
 * 交换动画和落下/取消状态与原版保持同一套职责边界；本类继续负责原版额外的
 * 删除滑出与列表收拢动画。
 *
 * Smartisan OS 框架依赖替换说明：
 * - com.smartisan.appbaselayer.quality.NullSafe.nonNull(View) → 直接判空 (?: continue)
 * - com.smartisan.weather.adapter.CityListAdapter.getItemId → 通过 adapter.getItemId
 * - PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID (0xFF) → 内联常量 [DEFAULT_VALUE]
 *
 * 注：原版 animateRemoval 的 OnPreDraw 实现未被 jadx 成功反编译
 * （方法体抛出 UnsupportedOperationException），此处按删除动画的常规意图实现：
 * 将被删除项折叠为 1px 高度后，对剩余可见项做 translationY 回弹动画填补空隙，
 * 动画结束后回调 onAnimationEnd。
 */
class WeatherListView : DragSortListView {

    interface OnDragEventListener {
        fun onStartDrag(position: Int)
        fun onStopDrag()
    }

    interface OnOverScrolledListener {
        fun onOverScrolled(scrollX: Int, scrollY: Int, isSlidingUp: Boolean, isSlidingDown: Boolean)
    }

    private var focusView: View? = null
    private var focusPosition: Int = -1
    private var onOverScrolledListener: OnOverScrolledListener? = null
    private var onDragEventListener: OnDragEventListener? = null

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        focusPosition = -1
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr) {
        focusPosition = -1
    }

    override fun onOverScrolled(
        scrollX: Int,
        scrollY: Int,
        isClamped: Boolean,
        isSlidingUp: Boolean,
    ) {
        super.onOverScrolled(scrollX, scrollY, isClamped, isSlidingUp)
        onOverScrolledListener?.onOverScrolled(scrollX, scrollY, isClamped, isSlidingUp)
    }

    fun setOnOverScrollListener(listener: OnOverScrolledListener?) {
        onOverScrolledListener = listener
    }

    fun setOnDragEventListener(listener: OnDragEventListener?) {
        onDragEventListener = listener
    }

    override fun onDragStarted(position: Int) {
        onDragEventListener?.onStartDrag(position)
    }

    override fun onDragStopped(cancelled: Boolean) {
        onDragEventListener?.onStopDrag()
    }

    fun setFocusView(position: Int) {
        focusPosition = position
        focusView = if (position < 0) {
            null
        } else {
            getChildAt(position - firstVisiblePosition + headerViewsCount)
        }
    }

    fun setFocusView(view: View?) {
        focusView = view
        if (view == null) {
            focusPosition = -1
        }
    }

    /**
     * 播放删除项的横向滑出动画，结束后触发 [animateRemoval] 收拢剩余项。
     */
    fun playDeleteItemAnimation(listener: Animation.AnimationListener?) {
        val view = focusView
        if (view == null) {
            listener?.onAnimationStart(null)
            listener?.onAnimationEnd(null)
            return
        }
        val anim = TranslateAnimation(view.x, view.width.toFloat(), 0f, 0f)
        anim.duration = 200L
        anim.interpolator = DecelerateInterpolator()
        anim.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
                listener?.onAnimationStart(null)
            }

            override fun onAnimationRepeat(animation: Animation?) {}

            override fun onAnimationEnd(animation: Animation?) {
                animateRemoval(listener, view, focusPosition)
            }
        })
        view.startAnimation(anim)
    }

    /**
     * 将 [view] 折叠为 1px，并在下一次 OnPreDraw 时对剩余可见项做回弹平移动画，
     * 动画结束后回调 [listener].onAnimationEnd。
     *
     * @param position 原版传入的焦点位置（原版未在可见反编译代码中使用，保留以兼容签名）。
     */
    fun animateRemoval(listener: Animation.AnimationListener?, view: View, position: Int) {
        val topMap = HashMap<Long, Int>()
        val firstVisiblePosition = firstVisiblePosition
        val adapter = getInputAdapter()
        val childCount = childCount
        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            if (child !== view) {
                val pos = firstVisiblePosition + i - headerViewsCount
                if (pos == -1) {
                    topMap[1023L] = child.top
                } else if (pos != count - footerViewsCount - headerViewsCount) {
                    val id = adapter?.getItemId(pos) ?: DEFAULT_VALUE
                    topMap[id] = child.top
                } else {
                    topMap[DEFAULT_VALUE] = child.top
                }
            }
        }
        view.layoutParams = AbsListView.LayoutParams(MATCH_PARENT, 1)
        val viewTreeObserver = viewTreeObserver
        viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                viewTreeObserver.removeOnPreDrawListener(this)
                val childCountNow = childCount
                for (i in 0 until childCountNow) {
                    val child = getChildAt(i) ?: continue
                    if (child === view) continue
                    val pos = firstVisiblePosition + i - headerViewsCount
                    val id = when {
                        pos == -1 -> 1023L
                        pos != count - footerViewsCount - headerViewsCount ->
                            adapter?.getItemId(pos) ?: DEFAULT_VALUE
                        else -> DEFAULT_VALUE
                    }
                    val oldTop = topMap[id] ?: continue
                    val newTop = child.top
                    val delta = oldTop - newTop
                    if (delta != 0) {
                        child.translationY = delta.toFloat()
                        child.animate()
                            .translationY(0f)
                            .setDuration(200L)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                    }
                }
                listener?.onAnimationEnd(null)
                return true
            }
        })
    }

    companion object {
        /** 原版取自 PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID (0xFF)。 */
        const val DEFAULT_VALUE: Long = 255L

        private const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
