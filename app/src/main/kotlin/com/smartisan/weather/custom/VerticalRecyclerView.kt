package com.smartisan.weather.custom

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * 纵向 RecyclerView，高度由内容撑开（AT_MOST），不可纵向滚动。
 *
 * 完整复刻自原版 com.smartisan.weather.custom.VerticalRecyclerView。
 */
class VerticalRecyclerView : RecyclerView {

    constructor(context: Context) : super(context) {
        setNestedScrollingEnabled(false)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        setNestedScrollingEnabled(false)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr) {
        setNestedScrollingEnabled(false)
    }

    override fun canScrollVertically(direction: Int): Boolean = false

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        // 0x1FFFFFFF 为原版写死的 max size，AT_MOST 让子内容撑开高度
        super.onMeasure(
            widthSpec,
            View.MeasureSpec.makeMeasureSpec(0x1FFFFFFF, View.MeasureSpec.AT_MOST),
        )
    }
}
