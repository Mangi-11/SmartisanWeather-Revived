package com.smartisan.weather.custom

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * 逐小时预报横向 RecyclerView。
 *
 * 完整复刻自原版 com.smartisan.weather.custom.WeatherHourForecastView。
 * - 关闭嵌套滚动
 * - 不可横向滚动（交给父 ViewPager 处理滑动）
 * - onMeasure 将宽度指定为 AT_MOST(0x1FFFFFFF)，让内容决定宽度
 */
class WeatherHourForecastView : RecyclerView {

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

    override fun canScrollHorizontally(direction: Int): Boolean = false

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        // 0x1FFFFFFF 为原版写死的 max size，AT_MOST 让子内容撑开宽度
        super.onMeasure(
            View.MeasureSpec.makeMeasureSpec(0x1FFFFFFF, View.MeasureSpec.AT_MOST),
            heightSpec,
        )
    }
}
