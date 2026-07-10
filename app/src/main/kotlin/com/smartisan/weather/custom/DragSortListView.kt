/*
 * Copyright 2012 Carl Bauer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * This focused Kotlin implementation follows the interaction contract and XML
 * attributes of bauerca/drag-sort-listview, which Smartisan Weather 8.1.3
 * embedded and customized. It intentionally implements only the vertical
 * handle-drag behavior used by the weather city list.
 */
package com.smartisan.weather.custom

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.widget.AdapterView
import android.widget.ListAdapter
import android.widget.ListView
import com.smartisan.weather.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Vertical drag-sort [ListView] used by the city management page.
 *
 * The floating row is a bitmap snapshot of the touched item. The adapter owns
 * the live working order; [DragSortListener.onMove] is called whenever the
 * floating row crosses another item, and stable IDs are used to animate the
 * displaced rows back into place. Dropping and cancellation are deliberately
 * separate so an interrupted gesture never writes a partial order.
 */
open class DragSortListView : ListView {

    interface DragSortListener {
        fun onMove(from: Int, to: Int)

        fun onDrop(from: Int, to: Int)

        fun onCancel(from: Int, to: Int)
    }

    private var inputAdapter: ListAdapter? = null
    private var dragSortListener: DragSortListener? = null
    private var dragPositionValidator: (Int) -> Boolean = { true }

    private var dragHandleId: Int = View.NO_ID
    private var dragEnabled: Boolean = true
    private var dragStartMode: Int = DRAG_START_ON_DOWN
    private var floatAlpha: Float = 1f
    private var dragScrollStart: Float = DEFAULT_DRAG_SCROLL_START
    private var maxDragScrollSpeed: Float = DEFAULT_MAX_DRAG_SCROLL_SPEED
    private var adjustItemAnimationDuration: Long = DEFAULT_ADJUST_ANIMATION_DURATION
    private var dropAnimationDuration: Long = DEFAULT_DROP_ANIMATION_DURATION

    private val touchSlop: Int by lazy(LazyThreadSafetyMode.NONE) {
        ViewConfiguration.get(context).scaledTouchSlop
    }
    private val floatPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val handleBounds = Rect()
    private val dragInterpolator = TimeInterpolator { fraction ->
        if (fraction < INTERPOLATOR_PIVOT) {
            2f * fraction * fraction
        } else {
            val inverse = fraction - 1f
            1f - 2f * inverse * inverse
        }
    }

    private var candidateAdapterPosition = AdapterView.INVALID_POSITION
    private var candidateListPosition = AdapterView.INVALID_POSITION
    private var candidateDownX = 0f
    private var candidateDownY = 0f
    private var candidateTouchOffsetY = 0f

    private var dragging = false
    private var dragStartPosition = AdapterView.INVALID_POSITION
    private var dragCurrentPosition = AdapterView.INVALID_POSITION
    private var dragItemId = AdapterView.INVALID_ROW_ID
    private var floatBitmap: Bitmap? = null
    private var floatPaddingTop = 0
    private var dragRowHeight = 0
    private var floatLeft = 0f
    private var floatTop = 0f
    private var lastMotionY = 0f
    private var autoScrollSpeedPxPerMs = 0f
    private var autoScrollRemainder = 0f
    private var lastAutoScrollUptime = 0L
    private var autoScrollPosted = false
    private var dropAnimator: ValueAnimator? = null

    private var pendingShuffleTops: MutableMap<Long, Float>? = null
    private var shufflePreDrawPosted = false

    private val longPressRunnable = Runnable {
        if (
            candidateAdapterPosition != AdapterView.INVALID_POSITION &&
            dragStartMode == DRAG_START_ON_LONG_PRESS &&
            !dragging
        ) {
            beginDrag(
                candidateAdapterPosition,
                candidateListPosition,
                candidateDownY,
                candidateTouchOffsetY,
            )
        }
    }

    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            autoScrollPosted = false
            if (!dragging || dropAnimator != null || autoScrollSpeedPxPerMs == 0f) return

            val now = SystemClock.uptimeMillis()
            val elapsed = (now - lastAutoScrollUptime).coerceIn(0L, MAX_AUTO_SCROLL_FRAME_MILLIS)
            lastAutoScrollUptime = now
            val exactDelta = autoScrollSpeedPxPerMs * elapsed + autoScrollRemainder
            val delta = exactDelta.roundToInt()
            autoScrollRemainder = exactDelta - delta
            if (delta != 0) {
                scrollListBy(delta)
                updateTargetPosition(lastMotionY)
            }
            scheduleAutoScroll()
        }
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        readAttributes(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr) {
        readAttributes(attrs)
    }

    private fun readAttributes(attrs: AttributeSet?) {
        if (attrs == null) return
        val values = context.obtainStyledAttributes(attrs, R.styleable.DragSortListView)
        try {
            dragHandleId = values.getResourceId(
                R.styleable.DragSortListView_drag_handle_id,
                View.NO_ID,
            )
            dragEnabled = values.getBoolean(
                R.styleable.DragSortListView_drag_enabled,
                true,
            )
            dragStartMode = values.getInt(
                R.styleable.DragSortListView_drag_start_mode,
                DRAG_START_ON_DOWN,
            )
            floatAlpha = values.getFloat(
                R.styleable.DragSortListView_float_alpha,
                1f,
            ).coerceIn(0f, 1f)
            dragScrollStart = values.getFloat(
                R.styleable.DragSortListView_drag_scroll_start,
                DEFAULT_DRAG_SCROLL_START,
            ).coerceIn(MIN_DRAG_SCROLL_START, MAX_DRAG_SCROLL_START)
            maxDragScrollSpeed = values.getFloat(
                R.styleable.DragSortListView_max_drag_scroll_speed,
                DEFAULT_MAX_DRAG_SCROLL_SPEED,
            ).coerceAtLeast(0f)
            adjustItemAnimationDuration = values.getFloat(
                R.styleable.DragSortListView_adjust_item_animation_duration,
                DEFAULT_ADJUST_ANIMATION_DURATION.toFloat(),
            ).toLong().coerceAtLeast(0L)
            dropAnimationDuration = values.getInt(
                R.styleable.DragSortListView_drop_animation_duration,
                DEFAULT_DROP_ANIMATION_DURATION.toInt(),
            ).toLong().coerceAtLeast(0L)
        } finally {
            values.recycle()
        }
    }

    override fun setAdapter(adapter: ListAdapter?) {
        inputAdapter = adapter
        super.setAdapter(adapter)
    }

    fun getInputAdapter(): ListAdapter? = inputAdapter

    fun setDragSortListener(listener: DragSortListener?) {
        dragSortListener = listener
    }

    fun setDragPositionValidator(validator: ((Int) -> Boolean)?) {
        dragPositionValidator = validator ?: { true }
    }

    fun setDragEnabled(enabled: Boolean) {
        if (dragEnabled == enabled) return
        dragEnabled = enabled
        if (!enabled && dragging) finishDrag(cancelled = true, animateDrop = false)
        if (!enabled) clearCandidate()
    }

    fun isDragEnabled(): Boolean = dragEnabled

    fun isDragInProgress(): Boolean = dragging

    fun cancelDrag() {
        if (dragging) finishDrag(cancelled = true, animateDrop = false)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || !dragEnabled) return super.dispatchTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dropAnimator?.cancel()
                clearCandidate()
                prepareCandidate(event)
                if (
                    dragStartMode == DRAG_START_ON_DOWN &&
                    candidateAdapterPosition != AdapterView.INVALID_POSITION &&
                    beginDrag(
                        candidateAdapterPosition,
                        candidateListPosition,
                        event.y,
                        candidateTouchOffsetY,
                    )
                ) {
                    return true
                }
                if (
                    dragStartMode == DRAG_START_ON_LONG_PRESS &&
                    candidateAdapterPosition != AdapterView.INVALID_POSITION
                ) {
                    postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                }
            }

            MotionEvent.ACTION_MOVE -> {
                lastMotionY = event.y
                if (dragging) {
                    handleDragMotion(event)
                    return true
                }
                if (
                    dragStartMode == DRAG_START_ON_MOVE &&
                    candidateAdapterPosition != AdapterView.INVALID_POSITION &&
                    abs(event.y - candidateDownY) > touchSlop
                ) {
                    cancelChildGesture(event)
                    if (
                        beginDrag(
                            candidateAdapterPosition,
                            candidateListPosition,
                            event.y,
                            candidateTouchOffsetY,
                        )
                    ) {
                        handleDragMotion(event)
                        return true
                    }
                } else if (
                    dragStartMode == DRAG_START_ON_LONG_PRESS &&
                    (abs(event.x - candidateDownX) > touchSlop ||
                        abs(event.y - candidateDownY) > touchSlop)
                ) {
                    clearCandidate()
                }
            }

            MotionEvent.ACTION_UP -> {
                clearCandidate()
                if (dragging) {
                    finishDrag(cancelled = false, animateDrop = true)
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                clearCandidate()
                if (dragging) {
                    finishDrag(cancelled = true, animateDrop = false)
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun prepareCandidate(event: MotionEvent) {
        val listPosition = pointToPosition(event.x.toInt(), event.y.toInt())
        val adapter = inputAdapter ?: return
        val adapterPosition = listPosition - headerViewsCount
        if (
            listPosition == AdapterView.INVALID_POSITION ||
            adapterPosition !in 0 until adapter.count ||
            !dragPositionValidator(adapterPosition)
        ) {
            return
        }

        val row = getChildAt(listPosition - firstVisiblePosition) ?: return
        val handle: View? = if (dragHandleId == View.NO_ID) {
            row
        } else {
            row.findViewById(dragHandleId)
        }
        if (handle == null || !handle.isEnabled || handle.visibility != View.VISIBLE) return
        handle.getGlobalVisibleRect(handleBounds)
        if (!handleBounds.contains(event.rawX.toInt(), event.rawY.toInt())) return

        candidateAdapterPosition = adapterPosition
        candidateListPosition = listPosition
        candidateDownX = event.x
        candidateDownY = event.y
        candidateTouchOffsetY = event.y - row.top
        lastMotionY = event.y
    }

    private fun beginDrag(
        adapterPosition: Int,
        listPosition: Int,
        touchY: Float,
        touchOffsetY: Float,
    ): Boolean {
        if (dragging || dropAnimator != null || dragSortListener == null) return false
        val adapter = inputAdapter ?: return false
        if (adapterPosition !in 0 until adapter.count) return false
        val row = getChildAt(listPosition - firstVisiblePosition) ?: return false
        if (row.width <= 0 || row.height <= 0) return false

        val shadowTop = resources.getDrawable(R.drawable.shadow_top, context.theme)
        val shadowBottom = resources.getDrawable(R.drawable.shadow_bottom, context.theme)
        val topShadowHeight = shadowTop.intrinsicHeight.coerceAtLeast(0)
        val bottomShadowHeight = shadowBottom.intrinsicHeight.coerceAtLeast(0)
        val snapshot = Bitmap.createBitmap(
            row.width,
            topShadowHeight + row.height + bottomShadowHeight,
            Bitmap.Config.ARGB_8888,
        )
        val snapshotCanvas = Canvas(snapshot)
        shadowTop.setBounds(0, 0, row.width, topShadowHeight)
        shadowTop.draw(snapshotCanvas)
        snapshotCanvas.save()
        snapshotCanvas.translate(0f, topShadowHeight.toFloat())
        row.draw(snapshotCanvas)
        snapshotCanvas.restore()
        shadowBottom.setBounds(
            0,
            topShadowHeight + row.height,
            row.width,
            snapshot.height,
        )
        shadowBottom.draw(snapshotCanvas)

        dragging = true
        dragStartPosition = adapterPosition
        dragCurrentPosition = adapterPosition
        dragItemId = adapter.getItemId(adapterPosition)
        floatBitmap = snapshot
        floatPaddingTop = topShadowHeight
        dragRowHeight = row.height
        floatLeft = row.left.toFloat()
        floatTop = touchY - touchOffsetY - floatPaddingTop
        lastMotionY = touchY
        floatPaint.alpha = (floatAlpha * PAINT_ALPHA_MAX).roundToInt()
        clearCandidate()

        parent?.requestDisallowInterceptTouchEvent(true)
        setPressed(false)
        onDragStarted(adapterPosition)
        invalidate()
        return true
    }

    private fun handleDragMotion(event: MotionEvent) {
        val bitmap = floatBitmap ?: return
        lastMotionY = event.y
        floatTop = (event.y - candidateTouchOffsetY - floatPaddingTop).coerceIn(
            -bitmap.height.toFloat(),
            height.toFloat(),
        )
        updateTargetPosition(event.y)
        updateAutoScroll(event.y)
        invalidate()
    }

    private fun updateTargetPosition(touchY: Float) {
        val adapter = inputAdapter ?: return
        if (floatBitmap == null) return
        if (adapter.count == 0) return

        val floatCenterY = (floatTop + floatPaddingTop + dragRowHeight / 2f)
            .roundToInt()
            .coerceIn(paddingTop, max(paddingTop, height - paddingBottom - 1))
        var listPosition = pointToPosition(width / 2, floatCenterY)
        if (listPosition == AdapterView.INVALID_POSITION) {
            listPosition = when {
                touchY < paddingTop -> firstVisiblePosition
                touchY > height - paddingBottom -> firstVisiblePosition + childCount - 1
                else -> return
            }
        }

        val proposed = (listPosition - headerViewsCount).coerceIn(0, adapter.count - 1)
        val target = nearestAllowedPosition(proposed)
        if (target == dragCurrentPosition || target == AdapterView.INVALID_POSITION) return

        rememberVisibleItemTops()
        val from = dragCurrentPosition
        dragCurrentPosition = target
        dragSortListener?.onMove(from, target)
        scheduleShuffleAnimation()
    }

    private fun nearestAllowedPosition(proposed: Int): Int {
        val adapter = inputAdapter ?: return AdapterView.INVALID_POSITION
        if (proposed !in 0 until adapter.count) return AdapterView.INVALID_POSITION
        if (dragPositionValidator(proposed)) return proposed

        for (distance in 1 until adapter.count) {
            val towardCurrent = if (proposed < dragCurrentPosition) {
                proposed + distance
            } else {
                proposed - distance
            }
            if (
                towardCurrent in 0 until adapter.count &&
                dragPositionValidator(towardCurrent)
            ) {
                return towardCurrent
            }
        }
        return dragCurrentPosition
    }

    private fun updateAutoScroll(touchY: Float) {
        val availableTop = paddingTop.toFloat()
        val availableBottom = (height - paddingBottom).toFloat()
        val availableHeight = availableBottom - availableTop
        if (availableHeight <= 0f) {
            autoScrollSpeedPxPerMs = 0f
            return
        }

        val edgeSize = availableHeight * dragScrollStart
        autoScrollSpeedPxPerMs = when {
            edgeSize <= 0f -> 0f
            touchY < availableTop + edgeSize -> {
                val depth = ((availableTop + edgeSize - touchY) / edgeSize).coerceIn(0f, 1f)
                -maxDragScrollSpeed * depth
            }

            touchY > availableBottom - edgeSize -> {
                val depth = ((touchY - (availableBottom - edgeSize)) / edgeSize).coerceIn(0f, 1f)
                maxDragScrollSpeed * depth
            }

            else -> 0f
        }
        scheduleAutoScroll()
    }

    private fun scheduleAutoScroll() {
        if (
            !dragging ||
            dropAnimator != null ||
            autoScrollSpeedPxPerMs == 0f ||
            autoScrollPosted
        ) {
            return
        }
        lastAutoScrollUptime = SystemClock.uptimeMillis()
        autoScrollPosted = true
        postOnAnimation(autoScrollRunnable)
    }

    private fun rememberVisibleItemTops() {
        val oldTops = pendingShuffleTops ?: LinkedHashMap<Long, Float>().also {
            pendingShuffleTops = it
        }
        val adapter = inputAdapter ?: return
        for (childIndex in 0 until childCount) {
            val listPosition = firstVisiblePosition + childIndex
            val adapterPosition = listPosition - headerViewsCount
            if (adapterPosition !in 0 until adapter.count) continue
            val child = getChildAt(childIndex) ?: continue
            child.animate().cancel()
            oldTops.putIfAbsent(
                adapter.getItemId(adapterPosition),
                child.top + child.translationY,
            )
        }
    }

    private fun scheduleShuffleAnimation() {
        if (shufflePreDrawPosted) return
        val observer = viewTreeObserver
        if (!observer.isAlive) return
        shufflePreDrawPosted = true
        observer.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (observer.isAlive) observer.removeOnPreDrawListener(this)
                shufflePreDrawPosted = false
                val oldTops = pendingShuffleTops
                pendingShuffleTops = null
                animateVisibleItemsFrom(oldTops.orEmpty())
                return true
            }
        })
    }

    private fun animateVisibleItemsFrom(oldTops: Map<Long, Float>) {
        val adapter = inputAdapter ?: return
        if (oldTops.isEmpty()) return
        for (childIndex in 0 until childCount) {
            val listPosition = firstVisiblePosition + childIndex
            val adapterPosition = listPosition - headerViewsCount
            if (adapterPosition !in 0 until adapter.count) continue
            val id = adapter.getItemId(adapterPosition)
            if (id == dragItemId) continue
            val oldTop = oldTops[id] ?: continue
            val child = getChildAt(childIndex) ?: continue
            val delta = oldTop - child.top
            if (abs(delta) < MIN_ANIMATION_DELTA_PX) continue
            child.animate().cancel()
            child.translationY = delta
            child.animate()
                .translationY(0f)
                .setDuration(adjustItemAnimationDuration)
                .setInterpolator(dragInterpolator)
                .setListener(null)
                .start()
        }
    }

    private fun finishDrag(cancelled: Boolean, animateDrop: Boolean) {
        removeCallbacks(longPressRunnable)
        stopAutoScroll()
        if (!dragging) return

        if (cancelled || !animateDrop || dropAnimationDuration == 0L) {
            completeDrag(cancelled)
            return
        }

        val destinationListPosition = dragCurrentPosition + headerViewsCount
        val destination = getChildAt(destinationListPosition - firstVisiblePosition)
        val destinationTop = destination?.top?.minus(floatPaddingTop)?.toFloat() ?: floatTop
        if (destinationTop == floatTop) {
            completeDrag(cancelled = false)
            return
        }

        dropAnimator = ValueAnimator.ofFloat(floatTop, destinationTop).apply {
            duration = dropAnimationDuration
            interpolator = dragInterpolator
            addUpdateListener { animator ->
                floatTop = animator.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var wasCancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    wasCancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    dropAnimator = null
                    completeDrag(cancelled = wasCancelled)
                }
            })
            start()
        }
    }

    private fun completeDrag(cancelled: Boolean) {
        if (!dragging) return
        dragging = false
        val start = dragStartPosition
        val end = dragCurrentPosition
        val bitmap = floatBitmap
        floatBitmap = null
        dragItemId = AdapterView.INVALID_ROW_ID
        floatPaddingTop = 0
        dragRowHeight = 0
        dragStartPosition = AdapterView.INVALID_POSITION
        dragCurrentPosition = AdapterView.INVALID_POSITION
        parent?.requestDisallowInterceptTouchEvent(false)

        if (cancelled) {
            dragSortListener?.onCancel(start, end)
        } else {
            dragSortListener?.onDrop(start, end)
        }
        onDragStopped(cancelled)
        bitmap?.recycle()
        invalidate()
    }

    private fun stopAutoScroll() {
        autoScrollSpeedPxPerMs = 0f
        autoScrollRemainder = 0f
        lastAutoScrollUptime = 0L
        autoScrollPosted = false
        removeCallbacks(autoScrollRunnable)
    }

    private fun clearCandidate() {
        removeCallbacks(longPressRunnable)
        candidateAdapterPosition = AdapterView.INVALID_POSITION
        candidateListPosition = AdapterView.INVALID_POSITION
    }

    private fun cancelChildGesture(event: MotionEvent) {
        val cancel = MotionEvent.obtain(event)
        cancel.action = MotionEvent.ACTION_CANCEL
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }

    protected open fun onDragStarted(position: Int) = Unit

    protected open fun onDragStopped(cancelled: Boolean) = Unit

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val bitmap = floatBitmap ?: return
        canvas.drawBitmap(bitmap, floatLeft, floatTop, floatPaint)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        stopAutoScroll()
        dropAnimator?.cancel()
        dropAnimator = null
        if (dragging) completeDrag(cancelled = true)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val DRAG_START_ON_DOWN = 0
        const val DRAG_START_ON_MOVE = 1
        const val DRAG_START_ON_LONG_PRESS = 2
        const val DEFAULT_DRAG_SCROLL_START = 0.25f
        const val MIN_DRAG_SCROLL_START = 0.05f
        const val MAX_DRAG_SCROLL_START = 0.5f
        const val DEFAULT_MAX_DRAG_SCROLL_SPEED = 0.5f
        const val DEFAULT_ADJUST_ANIMATION_DURATION = 200L
        const val DEFAULT_DROP_ANIMATION_DURATION = 150L
        const val PAINT_ALPHA_MAX = 255f
        const val MIN_ANIMATION_DELTA_PX = 0.5f
        const val INTERPOLATOR_PIVOT = 0.5f
        const val MAX_AUTO_SCROLL_FRAME_MILLIS = 50L
    }
}
