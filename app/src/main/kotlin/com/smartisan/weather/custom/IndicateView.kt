package com.smartisan.weather.custom

import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.smartisan.weather.R
import com.smartisan.weather.util.DebugLog
import kotlin.math.min

/**
 * 城市/页面指示器圆点 View。
 *
 * 完整复刻自原版 com.smartisan.weather.custom.IndicateView，保留全部绘制逻辑：
 * 圆点缩放、居中、选中态、定位城市箭头。
 *
 * 定位城市是否存在由主页面已有状态直接注入，绘制路径不访问数据库。
 */
class IndicateView : View {

    /** 圆点总数（页面数）。 */
    private var totalCount = 0
    /** 当前选中索引。 */
    private var currentIndex = 0
    /** 普通圆点宽度。 */
    private var dotWidth = 0
    /** 箭头宽度。 */
    private var arrowWidth = 0
    /** 圆点间距。 */
    private var spacing = 10
    /** 高亮圆点。 */
    private var highlightBitmap = decode(R.drawable.dot_highlight)
    /** 灰色圆点。 */
    private var greyBitmap = decode(R.drawable.dot_grey)
    /** 选中箭头。 */
    private var arrowSelectedBitmap = decode(R.drawable.weather_arrow_seleted)
    /** 普通箭头。 */
    private var arrowNormalBitmap = decode(R.drawable.weather_arrow_normal)
    /** 圆点源矩形。 */
    private var dotSrcRect: Rect? = null
    /** 箭头源矩形。 */
    private var arrowSrcRect: Rect? = null
    /** 绘制目标矩形。 */
    private var dstRect: RectF = RectF()
    /** 夜间模式下仍沿用原位图轮廓，仅替换为语义色。 */
    private val normalPaint = createTintPaint(R.color.page_indicator_normal)
    private val selectedPaint = createTintPaint(R.color.page_indicator_selected)
    private val useSemanticTint =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    /** 是否显示定位指示。 */
    private var showLocation = true
    /** 是否存在定位城市。 */
    private var hasLocationCity = false

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr) {
        highlightBitmap = decode(R.drawable.dot_highlight)
        greyBitmap = decode(R.drawable.dot_grey)
        arrowSelectedBitmap = decode(R.drawable.weather_arrow_seleted)
        arrowNormalBitmap = decode(R.drawable.weather_arrow_normal)
        val f = highlightBitmap
        val h = arrowSelectedBitmap
        if (f != null && h != null) {
            dotWidth = f.width
            arrowWidth = h.width
            dotSrcRect = Rect(0, 0, f.width, f.height)
            arrowSrcRect = Rect(0, 0, h.width, h.height)
        }
        spacing = (resources.getDimension(R.dimen.vp_bottomdot_marginleft) * 2.0f).toInt()
        dotSrcRect?.let { dstRect = RectF(it) }
    }

    private fun decode(resId: Int) = BitmapFactory.decodeResource(resources, resId)

    private fun createTintPaint(colorRes: Int) = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG,
    ).apply {
        colorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(context, colorRes),
            PorterDuff.Mode.SRC_IN,
        )
    }

    /** 第 0 位且开启定位指示且存在定位城市时，绘制为箭头而非圆点。 */
    private fun isArrow(index: Int): Boolean = index == 0 && showLocation && hasLocationCity

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        DebugLog.log("IndicateView", "onDraw()...")
        if (totalCount < 0) return
        if (currentIndex < 0) currentIndex = 0
        val count = totalCount
        if (count == 0) return

        val totalWidth = arrowWidth + dotWidth * (count - 1) + spacing * count
        val scale = min((width / 1.0f) / totalWidth, 1.0f)
        var x = (width - totalWidth * scale) / 2.0f + (spacing / 1.0f) / 2.0f
        val height = height.toFloat()
        val dotScaledHeight = dotWidth * scale
        val dotTop = (height - dotScaledHeight) / 2.0f
        val dotBottom = dotScaledHeight + dotTop
        val arrowScaledHeight = arrowWidth * scale
        val arrowTop = (height - arrowScaledHeight) / 2.0f
        val arrowBottom = arrowScaledHeight + arrowTop

        var i = 0
        while (i < count) {
            val right: Float
            if (isArrow(i)) {
                right = arrowWidth * scale + x
                dstRect.set(x, arrowTop, right, arrowBottom)
            } else {
                right = dotWidth * scale + x
                dstRect.set(x, dotTop, right, dotBottom)
            }
            val bitmap =
                if (i == 0 && hasLocationCity) {
                    if (currentIndex == 0) arrowSelectedBitmap else arrowNormalBitmap
                } else if (i == currentIndex) {
                    highlightBitmap
                } else {
                    greyBitmap
                }
            val src = if (isArrow(i)) arrowSrcRect else dotSrcRect
            if (bitmap != null && src != null) {
                val paint = if (useSemanticTint) {
                    if (i == currentIndex) selectedPaint else normalPaint
                } else {
                    null
                }
                canvas.drawBitmap(bitmap, src, dstRect, paint)
            }
            x = spacing * scale + right
            i++
        }
    }

    fun setShowLocation(show: Boolean) {
        showLocation = show
    }

    fun setHasLocationCity(hasLocation: Boolean) {
        if (hasLocationCity == hasLocation) return
        hasLocationCity = hasLocation
        invalidate()
    }

    /**
     * @param total 圆点总数
     * @param current 当前选中索引
     */
    fun setState(total: Int, current: Int) {
        DebugLog.log("IndicateView", "all, cur: $total,$current")
        totalCount = total
        currentIndex = if (current >= total) total - 1 else current
        invalidate()
    }
}
