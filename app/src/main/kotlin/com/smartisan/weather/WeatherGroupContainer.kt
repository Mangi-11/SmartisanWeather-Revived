package com.smartisan.weather

import android.content.Context
import android.graphics.drawable.TransitionDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.Interpolator
import android.widget.Scroller
import com.smartisan.weather.bean.SmartisanLocation
import com.smartisan.weather.custom.DrawItem
import com.smartisan.weather.util.ThemeUtils

/**
 * City pager container: a [WeatherSwticher] holding two [WeatherContentViewUtil]
 * pages that the user flings/rubber-bands between. Handles paging touch slop,
 * velocity-based fling, edge rubber-band and bounce-back via a [Scroller].
 *
 * Ported from the decompiled `com.smartisan.weather.WeatherGroupContainer`,
 * including the previously-un-decompiled `onInterceptTouchEvent`/`onTouchEvent`.
 *
 * 页面回调统一转发给宿主 [AbstractController]；容器本身只保留分页、回弹和背景转场职责。
 */
class WeatherGroupContainer : WeatherSwticher, AbstractController {
    @JvmField
    var newThemeType: String? = null

    @JvmField
    var oldThemeType: String? = null

    private lateinit var previousView: WeatherContentViewUtil
    private lateinit var nextView: WeatherContentViewUtil
    private val drawItems = mutableListOf<DrawItem>()
    private var hostController: AbstractController? = null
    private var currentIndex: Int = 0
    private var scrollable: Boolean = true
    private var canRefresh: Boolean = true
    private var bgView: View? = null
    private var activePointerId: Int = -1
    private var velocityTracker: VelocityTracker? = null
    private var lastMotionX: Float = 0f
    private var lastMotionY: Float = 0f
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var touchSlop: Int = 0
    private var velocityX: Int = 0
    private var direction: Int = 0
    private var abortThreshold: Int = 0
    private var maxFlingVelocity: Int = 0
    private var scrollerAdvanced: Boolean = false
    private var isDragging: Boolean = false
    private var ignoreScroll: Boolean = false
    private lateinit var scroller: Scroller
    private var onCurrentItemChanged: ((Int) -> Unit)? = null
    private var onBackgroundChanged: ((Int) -> Unit)? = null

    constructor(context: Context) : super(context) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize()
    }

    private fun initialize() {
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS)
        isFocusable = true
        val viewConfiguration = ViewConfiguration.get(context)
        touchSlop = viewConfiguration.scaledPagingTouchSlop
        val density = context.resources.displayMetrics.density
        maxFlingVelocity = viewConfiguration.scaledMaximumFlingVelocity
        scroller = Scroller(context, QUINTIC_EASE_OUT)
        abortThreshold = (density * 2.0f).toInt()
        mSwitchSlot = resources.getDimensionPixelSize(R.dimen.weather_main_scroll_slot)
    }

    /** Quintic ease-out interpolator (original field `z`). */
    private class QuinticEaseOutInterpolator : Interpolator {
        override fun getInterpolation(f: Float): Float {
            val f2 = f - 1.0f
            return f2 * f2 * f2 * f2 * f2 + 1.0f
        }
    }

    private fun onSecondaryPointerUp(motionEvent: MotionEvent) {
        val actionIndex = motionEvent.actionIndex
        if (motionEvent.getPointerId(actionIndex) == activePointerId) {
            val newIndex = if (actionIndex == 0) 1 else 0
            lastMotionX = motionEvent.getX(newIndex)
            activePointerId = motionEvent.getPointerId(newIndex)
            velocityTracker?.clear()
        }
    }

    private fun fillData(weatherContentViewUtil: WeatherContentViewUtil, type: Int) {
        Log.d(TAG, "fillData  mCurrentIndex - $currentIndex  type - $type")
        if (drawItems.isEmpty()) return
        if (currentIndex < 0) {
            Log.d(TAG, "get index < 0 $currentIndex")
            currentIndex = 0
        }
        if (currentIndex > drawItems.lastIndex) {
            Log.d(TAG, "get index - $currentIndex > data size ${drawItems.size}")
            currentIndex = drawItems.lastIndex
        }
        val drawItem = drawItems[currentIndex]
        weatherContentViewUtil.setHasLocationCity(
            drawItems.any { it.locationData?.sortOrder == 1 },
        )
        if (drawItem.isEmpty || drawItem.weatherData == null ||
            drawItem.weatherData!!.observe == null || !drawItem.isDataComplete()
        ) {
            weatherContentViewUtil.initEmptyView(drawItem, drawItems.size, currentIndex, type)
        } else {
            weatherContentViewUtil.fillViewWithData(drawItem, drawItems.size, currentIndex)
            initBgResource()
        }
    }

    /**
     * Prevent ancestors from stealing a city-page drag without overriding the
     * descendant touch contract implemented by [android.view.ViewGroup].
     */
    private fun disallowAncestorIntercept() {
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    /** Lets a scrollable descendant keep a same-axis gesture before paging starts. */
    private fun canCurrentPageScrollHorizontally(deltaX: Int, x: Int, y: Int): Boolean {
        val page = currentView
        val localX = x + scrollX - page.left
        val localY = y + scrollY - page.top
        if (localX !in 0 until page.width || localY !in 0 until page.height) return false
        return canScrollHorizontally(
            view = page,
            checkView = false,
            deltaX = deltaX,
            x = localX,
            y = localY,
        )
    }

    private fun canScrollHorizontally(
        view: View,
        checkView: Boolean,
        deltaX: Int,
        x: Int,
        y: Int,
    ): Boolean {
        if (view is ViewGroup) {
            val scrolledX = x + view.scrollX
            val scrolledY = y + view.scrollY
            for (index in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(index)
                if (
                    child.visibility == View.VISIBLE &&
                    scrolledX >= child.left && scrolledX < child.right &&
                    scrolledY >= child.top && scrolledY < child.bottom &&
                    canScrollHorizontally(
                        view = child,
                        checkView = true,
                        deltaX = deltaX,
                        x = scrolledX - child.left,
                        y = scrolledY - child.top,
                    )
                ) {
                    return true
                }
            }
        }
        return checkView && view.canScrollHorizontally(-deltaX)
    }

    private fun resetTouchState() {
        isDragging = false
        ignoreScroll = false
        velocityTracker?.let {
            it.recycle()
            velocityTracker = null
        }
    }

    /**
     * Drag handling. Applies the horizontal delta to the current page, with a
     * rubber-band easing when dragging past the first/last page boundary.
     * Returns `false` (the original always returned false).
     */
    private fun performDrag(f: Float): Boolean {
        if (drawItems.isEmpty()) {
            currentView.move(0f)
            return false
        }
        val dx = f - lastMotionX
        lastMotionX = f
        val contentTranslationX = currentView.getContentTranslationX()
        var fAbs = contentTranslationX + dx
        updateDirection(fAbs)
        val clientWidth = getClientWidth()
        val z2 = if (dx <= 0.0f) !(dx >= 0.0f || fAbs >= 0.0f) else fAbs > 0.0f
        if (!z2) {
            currentView.move(fAbs)
            return false
        }
        if (((currentIndex == 0 && direction == -1) ||
                (currentIndex == drawItems.lastIndex && direction == 1)) && z2
        ) {
            val width = clientWidth.toFloat()
            fAbs = contentTranslationX + ((width / (Math.abs(fAbs) * 12.0f + width)) * dx).toInt()
        }
        currentView.move(fAbs)
        return false
    }

    private fun startBgTransition() {
        val oldDrawable = requireNotNull(context.getDrawable(ThemeUtils.getBgRes(oldThemeType)))
        val newBackground = ThemeUtils.getBgRes(newThemeType)
        val newDrawable = requireNotNull(context.getDrawable(newBackground))
        onBackgroundChanged?.invoke(newBackground)
        val transitionDrawable = TransitionDrawable(arrayOf(oldDrawable, newDrawable))
        bgView?.background = transitionDrawable
        postDelayed({
            transitionDrawable.startTransition(400)
        }, 100L)
    }

    private fun updateDirection(f: Float) {
        direction = if (f > 0.0f) -1 else 1
    }

    private fun getClientWidth(): Int = (measuredWidth - paddingLeft) - paddingRight

    /** Sine-shaped helper used to map drag progress to settle distance. */
    private fun sineInterpolate(f: Float): Float {
        return Math.sin((f - 0.5f) * 0.4712389167638204f.toDouble()).toFloat()
    }

    /**
     * Settle the current page after a drag/fling: either advance to the
     * previous/next page (when past [mSwitchSlot]) or bounce back to centre,
     * using a velocity-aware duration.
     */
    private fun settleScroll(velocity: Int) {
        if (drawItems.isEmpty()) {
            currentView.move(0f)
            return
        }
        val currX = if (!scroller.isFinished) {
            if (scrollerAdvanced) scroller.currX else scroller.startX
        } else {
            currentView.getContentTranslationX().toInt()
        }
        if (direction == -1) {
            if (Math.abs(currX) > mSwitchSlot && currentIndex != 0) {
                showPrevious()
                return
            }
        } else if (direction == 1 &&
            Math.abs(currX) > mSwitchSlot &&
            currentIndex != drawItems.lastIndex
        ) {
            showNext()
            return
        }
        if (currX == 0) {
            return
        }
        val clientWidth = getClientWidth()
        val half = clientWidth / 2.0f
        val fA = half + sineInterpolate(Math.min(1.0f, Math.abs(currX) * 1.0f / clientWidth)) * half
        val iAbs = Math.abs(velocity)
        val iMin = Math.min(
            if (iAbs > 0) Math.round(Math.abs(fA / iAbs) * 1000.0f) * 4
            else (((Math.abs(currX) / 1080.0f) + 1.0f) * 100.0f).toInt(),
            600
        )
        scrollerAdvanced = false
        scroller.startScroll(currX, 0, -currX, 0, iMin)
        postInvalidateOnAnimation()
    }

    @get:JvmName("getCurrentContentView")
    private val currentView: WeatherContentViewUtil
        get() = getCurrentView()

    fun canRefreshPageData(): Boolean = canRefresh

    override fun computeScroll() {
        scrollerAdvanced = true
        if (scroller.isFinished || !scroller.computeScrollOffset()) {
            return
        }
        val contentTranslationX = currentView.getContentTranslationX().toInt()
        val currX = scroller.currX
        if (contentTranslationX != currX) {
            if (currentView.getContentTranslationX() == 0.0f) {
                scroller.abortAnimation()
            }
            currentView.move(currX.toFloat())
        }
        postInvalidateOnAnimation()
    }

    fun getCurrentCityId(): String {
        val result = drawItems.getOrNull(currentIndex)?.locationData?.mLocationKey.orEmpty()
        Log.d(TAG, "getCurrentCityId  result - $result")
        return result
    }

    fun getCurrentItem(): Int = currentIndex

    fun getData(): List<DrawItem> = drawItems

    fun initBgResource() {
        var curTheme = ThemeUtils.getCurTheme("00")
        drawItems.getOrNull(currentIndex)?.let { drawItem ->
            if (!drawItem.isEmpty && drawItem.weatherData != null &&
                drawItem.weatherData!!.observe != null && drawItem.isDataComplete() &&
                !TextUtils.isEmpty(drawItem.weatherData!!.observe!!.getCode())
            ) {
                curTheme = ThemeUtils.getCurTheme(drawItem.weatherData!!.observe!!.getCode())
            }
        }
        val view = bgView ?: return
        val background = curTheme.getBgRes()
        view.setBackgroundResource(background)
        onBackgroundChanged?.invoke(background)
    }

    fun initDatas(list: List<DrawItem>) {
        Log.d(TAG, "initDatas")
        drawItems.clear()
        drawItems.addAll(list)
        if (drawItems.isEmpty()) {
            currentIndex = 0
            if (childCount == 0) return
            val cv = currentView
            cv.move(0f)
            cv.visibility = VISIBLE
            cv.setEmptyType(1)
            initBgResource()
            return
        }
        currentIndex = currentIndex.coerceIn(drawItems.indices)
        if (childCount == 0) return
        val cv2 = currentView
        cv2.visibility = VISIBLE
        fillData(cv2, 1)
        initBgResource()
        onCurrentItemChanged?.invoke(currentIndex)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        previousView = findViewById(R.id.group_previous)
        nextView = findViewById(R.id.group_next)
        previousView.setPageController(this)
        nextView.setPageController(this)
    }

    fun onTempChange() {
        Log.d(TAG, "onTempChange")
        val contentView = getChildAt(a) as WeatherContentViewUtil
        val shouldUseFahrenheit = com.smartisan.weather.util.Utility
            .getSystemTemperatureUnit(context) == 2
        if (contentView.mCOrFSwitchView?.isChecked != shouldUseFahrenheit) {
            contentView.playAnimationCorF(shouldUseFahrenheit)
        }
    }

    override fun onInterceptTouchEvent(motionEvent: MotionEvent): Boolean {
        if (!scrollable) {
            Log.d(TAG, "onInterceptTouchEvent  return")
            return true
        }
        val action = motionEvent.action and 0xff
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            resetTouchState()
            return false
        }
        if (action != 0) {
            if (isDragging) {
                return true
            }
            if (ignoreScroll) {
                return false
            }
        }
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                velocityTracker?.clear()
                val x = motionEvent.x
                downX = x
                lastMotionX = x
                val y = motionEvent.y
                downY = y
                lastMotionY = y
                activePointerId = motionEvent.getPointerId(0)
                ignoreScroll = false
                scrollerAdvanced = true
                scroller.computeScrollOffset()
                if (Math.abs(scroller.finalX - scroller.currX) > abortThreshold) {
                    scroller.abortAnimation()
                    isDragging = true
                    disallowAncestorIntercept()
                } else {
                    isDragging = false
                }
            }
            MotionEvent.ACTION_POINTER_UP -> onSecondaryPointerUp(motionEvent)
            MotionEvent.ACTION_MOVE -> {
                val pointerId = activePointerId
                if (pointerId != -1) {
                    val pointerIndex = motionEvent.findPointerIndex(pointerId)
                    if (pointerIndex < 0) return false
                    val dx = motionEvent.getX(pointerIndex) - downX
                    if (!isDragging) {
                        val absDx = Math.abs(dx)
                        val y = motionEvent.getY(pointerIndex)
                        val absDy = Math.abs(y - downY)
                        if (absDx > touchSlop && absDx * 0.5f > absDy) {
                            if (
                                canCurrentPageScrollHorizontally(
                                    deltaX = dx.toInt(),
                                    x = motionEvent.getX(pointerIndex).toInt(),
                                    y = y.toInt(),
                                )
                            ) {
                                ignoreScroll = true
                                velocityTracker?.recycle()
                                velocityTracker = null
                                return false
                            }
                            isDragging = true
                            disallowAncestorIntercept()
                            lastMotionX = if (dx > 0.0f) downX + touchSlop else downX - touchSlop
                            lastMotionY = y
                        } else if (absDy > touchSlop) {
                            ignoreScroll = true
                        }
                    }
                    var interceptIndex = 0
                    if (isDragging && motionEvent.findPointerIndex(activePointerId)
                            .also { interceptIndex = it } >= 0 &&
                        performDrag(motionEvent.getX(interceptIndex))
                    ) {
                        postInvalidateOnAnimation()
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {}
        }
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker!!.addMovement(motionEvent)
        return isDragging
    }

    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        var zB = false
        if (!scrollable) {
            return false
        }
        if (motionEvent.action == MotionEvent.ACTION_DOWN && motionEvent.edgeFlags != 0) {
            return false
        }
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker!!.addMovement(motionEvent)
        val action = motionEvent.action and 0xff
        when (action) {
            MotionEvent.ACTION_DOWN -> scroller.abortAnimation()
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    val tracker = velocityTracker!!
                    tracker.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                    velocityX = tracker.getXVelocity(activePointerId).toInt()
                    settleScroll(velocityX)
                    resetTouchState()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) {
                    val pointerIndex = motionEvent.findPointerIndex(activePointerId)
                    if (pointerIndex >= 0) {
                        val x = motionEvent.getX(pointerIndex)
                        val lastX = lastMotionX
                        val dx = x - lastX
                        val absDx = Math.abs(x - lastX)
                        val y = motionEvent.getY(pointerIndex)
                        val absDy = Math.abs(y - lastMotionY)
                        val slop = touchSlop
                        if (absDx > slop && absDx > absDy) {
                            isDragging = true
                            lastMotionX = if (x - downX > 0.0f) lastMotionX + slop else lastMotionX - slop
                            lastMotionY = y
                            disallowAncestorIntercept()
                            updateDirection(dx)
                        }
                        if (isDragging) {
                            zB = false or performDrag(motionEvent.getX(pointerIndex))
                        }
                    }
                } else {
                    var pointerIndex = 0
                    if (motionEvent.findPointerIndex(activePointerId)
                            .also { pointerIndex = it } >= 0
                    ) {
                        zB = false or performDrag(motionEvent.getX(pointerIndex))
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {}
        }
        if (zB) {
            postInvalidateOnAnimation()
        }
        return true
    }

    override fun openAlerDetailPage() {
        canRefresh = false
        hostController?.openAlerDetailPage()
    }

    override fun openCityLisPage() {
        canRefresh = false
        hostController?.openCityLisPage()
    }

    override fun openParnterDetailPage() {
        canRefresh = false
        hostController?.openParnterDetailPage()
    }

    override fun refreshCurrentCity() {
        hostController?.refreshCurrentCity()
    }

    fun replaceData(success: Boolean, locationKey: String, drawItem: DrawItem?) {
        Log.d(TAG, "replaceData isSuccess - $success  locationkey - $locationKey")
        if (!success) {
            currentView.stopRefresh(null)
        }
        val index = drawItems.indexOfFirst { it.locationData?.mLocationKey == locationKey }
        if (index < 0) return
        if (drawItem != null) {
            drawItems[index] = drawItem
        }
        if (drawItems.getOrNull(currentIndex)?.locationData?.mLocationKey == locationKey) {
            Log.d(TAG, "replaceData update page  mCurrentIndex - $currentIndex")
            currentView.visibility = VISIBLE
            fillData(currentView, 2)
        }
    }

    fun resetPageState() {
        Log.d(TAG, "resetPageState")
        for (i in 0 until 2) {
            (getChildAt(i) as WeatherContentViewUtil).resetPageState()
        }
    }

    fun resetRefreshPageState(z: Boolean) {
        canRefresh = z
    }

    fun setBgView(view: View?) {
        bgView = view
    }

    /**
     * Inject the host Activity controller that child pages use for callbacks.
     */
    fun setController(controller: AbstractController) {
        hostController = controller
    }

    fun setOnCurrentItemChangedListener(listener: ((Int) -> Unit)?) {
        onCurrentItemChanged = listener
    }

    fun setOnBackgroundChangedListener(listener: ((Int) -> Unit)?) {
        onBackgroundChanged = listener
    }

    fun setCurrentItem(i: Int) {
        Log.d(TAG, "setCurrentItem i - $i")
        if (drawItems.isEmpty()) return
        currentIndex = i.coerceIn(drawItems.indices)
        val cv = currentView
        cv.visibility = VISIBLE
        fillData(cv, 1)
        onCurrentItemChanged?.invoke(currentIndex)
    }

    fun setLoadType(locationKey: String, state: Int) {
        Log.d(TAG, "setLoadType  state - $state")
        if (locationKey.isBlank()) return
        drawItems.firstOrNull { it.locationData?.mLocationKey == locationKey }
            ?.setLoadState(state)
        if (drawItems.getOrNull(currentIndex)?.locationData?.mLocationKey != locationKey) return
        if (1 == state) {
            currentView.setEmptyType(1)
        } else if (2 == state) {
            currentView.setEmptyType(2)
        }
    }

    override fun setScrollale(z: Boolean) {
        Log.d(TAG, "setScrollale  canScroll - $z")
        scrollable = z
    }

    override fun showNext() {
        Log.d(TAG, "showNext  mCurrentIndex - $currentIndex  whichChild - $a")
        val i = currentIndex
        if (i + 1 !in drawItems.indices) {
            return
        }
        currentIndex++
        val nextIndex = getNextIndex()
        for (i2 in 0 until mChildCount) {
            val weatherContentViewUtil = getChildAt(i2) as WeatherContentViewUtil
            if (i2 == nextIndex) {
                fillData(weatherContentViewUtil, 2)
                newThemeType = weatherContentViewUtil.getThemeType()
            } else {
                oldThemeType = weatherContentViewUtil.getThemeType()
            }
        }
        mIsThemeChange = newThemeType != oldThemeType
        super.showNext()
        startBgTransition()
        onCurrentItemChanged?.invoke(currentIndex)
    }

    override fun showPrevious() {
        Log.d(TAG, "showPrevious  mCurrentIndex - $currentIndex  whichChild - $a")
        val i = currentIndex
        if (i - 1 !in drawItems.indices) {
            return
        }
        currentIndex--
        val previousIndex = getPreviousIndex()
        for (i2 in 0 until mChildCount) {
            val weatherContentViewUtil = getChildAt(i2) as WeatherContentViewUtil
            if (i2 == previousIndex) {
                fillData(weatherContentViewUtil, 2)
                newThemeType = weatherContentViewUtil.getThemeType()
            } else {
                oldThemeType = weatherContentViewUtil.getThemeType()
            }
        }
        mIsThemeChange = newThemeType != oldThemeType
        super.showPrevious()
        startBgTransition()
        onCurrentItemChanged?.invoke(currentIndex)
    }

    override fun startAddCity(smartisanLocation: SmartisanLocation) {
        canRefresh = false
        hostController?.startAddCity(smartisanLocation)
    }

    companion object {
        const val DIREC_NEXT = 1
        const val DIREC_PREVIOUS = -1
        const val TAG = "Weather_WeatherGroupContainer"

        @JvmField
        var mSwitchSlot: Int = 0

        private val QUINTIC_EASE_OUT = QuinticEaseOutInterpolator()
    }
}
