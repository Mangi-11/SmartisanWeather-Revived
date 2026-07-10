package com.smartisan.weather.util

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewParent
import android.widget.CheckBox
import android.widget.CompoundButton
import com.smartisan.weather.R

/**
 * A custom slide-switch widget that renders a frame, a colored fill layer (masked via
 * PorterDuff SRC_IN) and a thumb bitmap, animating the thumb between the on/off positions.
 *
 * Ported from the decompiled Java class `com.smartisan.weather.util.SmartisanSwitchEx`.
 *
 * The touch state machine is ported from the original smali, including its short-tap,
 * long-press/drag, cancel-event and pressed-bitmap behaviour.
 *
 * 原版仅用于分析统计的框架调用已经移除，控件只保留绘制、触摸和动画状态机。
 */
class SmartisanSwitchEx @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.checkboxStyle
) : CheckBox(context, attrs, defStyleAttr) {

    /**
     * Posts a [Runnable] to be executed on the next animation frame (16 ms delay).
     */
    class FrameAnimationController private constructor() {
        companion object {
            const val ANIMATION_FRAME_DURATION = 16
            private val handler = FrameHandler()

            private class FrameHandler : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message) {
                    if (msg.what == MSG_FRAME && msg.obj != null) {
                        (msg.obj as Runnable).run()
                    }
                    super.handleMessage(msg)
                }
            }

            fun requestAnimationFrame(runnable: Runnable) {
                val msg = Message.obtain().apply {
                    what = MSG_FRAME
                    obj = runnable
                }
                handler.sendMessageDelayed(msg, ANIMATION_FRAME_DURATION.toLong())
            }

            fun requestFrameDelay(runnable: Runnable, delayMillis: Long) {
                val msg = Message.obtain().apply {
                    what = MSG_FRAME
                    obj = runnable
                }
                handler.sendMessageDelayed(msg, delayMillis)
            }
        }

        init {
            throw UnsupportedOperationException()
        }
    }

    private val animDurationMs: Float = 350.0f
    private val verticalMarginDp: Float = 2.0f
    private val maxAlpha: Int = 255
    private val layerAlpha: Int = 255

    private var checked: Boolean = false
    private var reentrant: Boolean = false
    private var animating: Boolean = false
    private var targetChecked: Boolean = false

    private val paint: Paint = Paint().apply { color = -1 }
    private var parent: ViewParent? = null

    private var bottomBitmap: Bitmap? = null
    private lateinit var thumbOnBitmap: Bitmap
    private lateinit var thumbOffBitmap: Bitmap
    private lateinit var frameBitmap: Bitmap
    private lateinit var currentThumbBitmap: Bitmap

    private lateinit var layerRect: RectF
    private val xfermode: PorterDuffXfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

    private var thumbWidth: Float = 0f
    private var positionOn: Float = 0f       // thumb X center when checked
    private var positionOff: Float = 0f      // thumb X center when unchecked
    private var thumbPosition: Float = 0f    // current thumb center X
    private var drawX: Float = 0f            // thumb draw X = thumbPosition - thumbWidth/2

    private var totalDistance: Float = 0f    // full slide distance in px (~350dp)
    private var verticalMargin: Float = 0f   // top/bottom padding in px (~2dp)

    private var animVelocity: Float = 0f
    private var animCurrent: Float = 0f

    private var touchSlop: Int = 0
    private var tapTimeout: Int = 0
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var dragStartPosition: Float = 0f

    private var externalListener: CompoundButton.OnCheckedChangeListener? = null
    private var internalListener: CompoundButton.OnCheckedChangeListener? = null

    /** Drawable resource ids for the on/off thumb and the frame. */
    private var bgResOn: Int = R.drawable.btn_unpressed_sunny
    private var bgResOff: Int = R.drawable.btn_unpressed_sunny
    private var frameRes: Int = R.drawable.frame_sunny

    init {
        init(context)
    }

    private fun adjustX(centerX: Float): Float = centerX - thumbWidth / 2.0f

    private fun requestParentDisallowIntercept() {
        parent = getParent()
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun init(context: Context) {
        val resources: Resources = context.resources
        tapTimeout = ViewConfiguration.getPressedStateDuration() + ViewConfiguration.getTapTimeout()
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        thumbOnBitmap = BitmapFactory.decodeResource(resources, bgResOn)
        thumbOffBitmap = BitmapFactory.decodeResource(resources, bgResOff)
        frameBitmap = BitmapFactory.decodeResource(resources, frameRes)
        currentThumbBitmap = thumbOffBitmap

        thumbWidth = thumbOnBitmap.width.toFloat()
        positionOn = thumbWidth / 2.0f
        positionOff = frameBitmap.width.toFloat() - (thumbWidth / 2.0f)
        thumbPosition = if (checked) positionOn else positionOff
        drawX = adjustX(thumbPosition)

        val density = resources.displayMetrics.density
        totalDistance = ((animDurationMs * density) + 0.5f).toInt().toFloat()
        verticalMargin = ((density * verticalMarginDp) + 0.5f).toInt().toFloat()

        layerRect = RectF(
            0.0f,
            verticalMargin,
            frameBitmap.width.toFloat(),
            frameBitmap.height.toFloat() + verticalMargin
        )
    }

    /** Starts a slide animation towards [toOn]. */
    private fun animateTo(toOn: Boolean) {
        animating = true
        animVelocity = if (toOn) totalDistance else -totalDistance
        animCurrent = thumbPosition
        AnimationFrameRunnable().run()
    }

    private fun stopAnimating() {
        animating = false
    }

    private fun setThumbPosition(position: Float) {
        thumbPosition = position
        drawX = adjustX(thumbPosition)
        invalidate()
    }

    /** Advances the animation by one frame (16 ms). */
    private fun advanceFrame() {
        animCurrent += (animVelocity * 16.0f) / 1000.0f
        val pos = animCurrent
        when {
            pos <= positionOff -> {
                stopAnimating()
                animCurrent = positionOff
                setCheckedDelayed(false)
            }
            pos >= positionOn -> {
                stopAnimating()
                animCurrent = positionOn
                setCheckedDelayed(true)
            }
        }
        setThumbPosition(animCurrent)
    }

    private fun setCheckedDelayed(value: Boolean) {
        postDelayed({ isChecked = value }, 10L)
    }

    override fun isChecked(): Boolean = checked

    override fun onDraw(canvas: Canvas) {
        canvas.saveLayerAlpha(layerRect, layerAlpha)
        paint.xfermode = xfermode
        bottomBitmap?.let { canvas.drawBitmap(it, drawX, verticalMargin, paint) }
        paint.xfermode = null
        canvas.drawBitmap(frameBitmap, 0.0f, verticalMargin, paint)
        canvas.drawBitmap(currentThumbBitmap, drawX, verticalMargin, paint)
        canvas.restore()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            frameBitmap.width,
            (frameBitmap.height + (verticalMargin * 2.0f)).toInt()
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isClickable) return false
        if (event.action == MotionEvent.ACTION_DOWN && ClickUtil.isFastClick()) return false

        val distanceX = Math.abs(event.x - downX)
        val distanceY = Math.abs(event.y - downY)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                requestParentDisallowIntercept()
                downX = event.x
                downY = event.y
                currentThumbBitmap = thumbOnBitmap
                dragStartPosition = if (checked) positionOn else positionOff
                targetChecked = checked
            }
            MotionEvent.ACTION_MOVE -> {
                thumbPosition = (dragStartPosition + event.x - downX)
                    .coerceIn(positionOff, positionOn)
                targetChecked = thumbPosition > (positionOn - positionOff) / 2f + positionOff
                drawX = adjustX(thumbPosition)
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
                -> {
                currentThumbBitmap = thumbOffBitmap
                val elapsed = event.eventTime - event.downTime
                if (distanceX < touchSlop && distanceY < touchSlop && elapsed < tapTimeout) {
                    if (!post { performClick() }) {
                        performClick()
                    }
                } else {
                    animateTo(targetChecked)
                }
            }
        }
        invalidate()
        return isEnabled
    }

    override fun performClick(): Boolean {
        animateTo(!checked)
        return true
    }

    fun setBgRes(context: Context, onRes: Int, offRes: Int, frameRes: Int) {
        bgResOn = onRes
        bgResOff = offRes
        this.frameRes = frameRes
        init(context)
        invalidate()
    }

    fun setBottomDrawable(res: Int) {
        bottomBitmap = BitmapFactory.decodeResource(resources, res)
        invalidate()
    }

    override fun setChecked(value: Boolean) {
        if (checked != value) {
            checked = value
            // Keep CompoundButton's platform state in sync for accessibility,
            // autofill and UI automation while retaining the original animation state.
            super.setChecked(value)
            thumbPosition = if (checked) positionOn else positionOff
            drawX = adjustX(thumbPosition)
            invalidate()
            if (reentrant) return
            reentrant = true
            externalListener?.onCheckedChanged(this, checked)
            internalListener?.onCheckedChanged(this, checked)
            reentrant = false
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
    }

    override fun setOnCheckedChangeListener(listener: CompoundButton.OnCheckedChangeListener?) {
        externalListener = listener
    }

    /** Additional listener hook (mirrors the original secondary listener field). */
    fun setInternalOnCheckedChangeListener(listener: CompoundButton.OnCheckedChangeListener?) {
        internalListener = listener
    }

    override fun toggle() {
        isChecked = !checked
    }

    /**
     * Per-frame animation driver: advances the thumb and re-queues itself until the
     * animation is stopped.
     */
    private inner class AnimationFrameRunnable : Runnable {
        override fun run() {
            if (animating) {
                advanceFrame()
                FrameAnimationController.requestAnimationFrame(this)
            }
        }
    }

    companion object {
        private const val MSG_FRAME = 1000
        private const val TAG = "SmartisanSwitchEx"
    }
}
