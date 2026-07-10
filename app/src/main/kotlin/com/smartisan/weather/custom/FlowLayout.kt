package com.smartisan.weather.custom

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * 流式布局：子 View 按行排列，超出可用宽度自动换行。
 *
 * 完整复刻自原版 com.smartisan.weather.custom.FlowLayout。
 * 原版使用 com.smartisan.appbaselayer.quality.NullSafe.nonNull() 做非空断言，
 * 此处替换为 Kotlin 的非空断言 `!!`。
 */
class FlowLayout : ViewGroup {

    /** 单行高度（行内最高子 View 高度 + 其垂直间距）。 */
    private var lineHeight: Int = 0

    /** 每行最后一个子 View 的索引（用于指示器等）。 */
    private val eachLineLastChildPoi: MutableList<Int> = mutableListOf()

    class LayoutParams(
        @JvmField val mHorizontalSpacing: Int,
        @JvmField val mVerticalSpacing: Int,
    ) : ViewGroup.LayoutParams(0, 0) {
        /** 标记此子 View 是否必须另起一行。 */
        var isNewLine: Boolean = false
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun checkLayoutParams(layoutParams: ViewGroup.LayoutParams?): Boolean =
        layoutParams is LayoutParams

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = LayoutParams(0, 0)

    fun getEachLineLastChildPoi(): List<Int> = eachLineLastChildPoi

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val childCount = childCount
        val width = right - left
        var x = paddingLeft
        var y = paddingTop
        eachLineLastChildPoi.clear()
        for (i in 0 until childCount) {
            val child = getChildAt(i)!!
            if (child.visibility != View.GONE) {
                val measuredWidth = child.measuredWidth
                val measuredHeight = child.measuredHeight
                val lp = child.layoutParams as LayoutParams
                if (lp.isNewLine || x + measuredWidth > width) {
                    x = paddingLeft
                    y += lineHeight
                    eachLineLastChildPoi.add(i - 1)
                }
                child.layout(x, y, x + measuredWidth, measuredHeight + y)
                x += measuredWidth + lp.mHorizontalSpacing
            }
        }
        if (childCount > 0) {
            eachLineLastChildPoi.add(childCount - 1)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var size2 = View.MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
        val childCount = childCount
        var x = paddingLeft
        var y = paddingTop
        val childHeightSpec =
            if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.AT_MOST) {
                View.MeasureSpec.makeMeasureSpec(size2, View.MeasureSpec.AT_MOST)
            } else {
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            }
        var x2 = x
        var maxLineHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)!!
            if (child.visibility != View.GONE) {
                val lp = child.layoutParams as LayoutParams
                child.measure(
                    View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.AT_MOST),
                    childHeightSpec,
                )
                val measuredWidth = child.measuredWidth
                maxLineHeight = maxOf(maxLineHeight, child.measuredHeight + lp.mVerticalSpacing)
                if (lp.isNewLine || x2 + measuredWidth > size) {
                    x2 = paddingLeft
                    y += maxLineHeight
                }
                x2 += measuredWidth + lp.mHorizontalSpacing
            }
        }
        lineHeight = maxLineHeight
        when (View.MeasureSpec.getMode(heightMeasureSpec)) {
            View.MeasureSpec.UNSPECIFIED -> size2 = y + maxLineHeight
            View.MeasureSpec.AT_MOST -> {
                val h = y + maxLineHeight
                if (h < size2) size2 = h
            }
            View.MeasureSpec.EXACTLY -> Unit
        }
        setMeasuredDimension(size, size2)
    }
}
