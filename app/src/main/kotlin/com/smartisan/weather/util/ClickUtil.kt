package com.smartisan.weather.util

/**
 * 点击防抖工具。
 *
 * 完整复刻自原版 com.smartisan.weather.util.ClickUtil。
 */
object ClickUtil {

    @JvmField
    var CLICK_GAP: Int = 500

    @JvmField
    var CLICK_GAP_LONG: Int = 800

    private var lastClickTime: Long = 0

    private fun isFast(gap: Int): Boolean {
        val now = System.currentTimeMillis()
        val delta = now - lastClickTime
        if (delta in 1 until gap) {
            return true
        }
        lastClickTime = now
        return false
    }

    @JvmStatic
    fun isFastClick(): Boolean = isFast(CLICK_GAP)

    @JvmStatic
    fun isFastClickLong(): Boolean = isFast(CLICK_GAP_LONG)
}
