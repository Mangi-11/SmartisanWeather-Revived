package com.smartisan.weather.util

/**
 * 单个天气主题所对应的全部资源 ID，复刻自原版 com.smartisan.weather.bean.../util.ThemeBean。
 *
 * 原版字段为混淆名 a-r（private + 显式 getter/setter）。此处保留 private backing field
 * 与原版完全一致的 getter 命名，themeType(r) 可读写。
 */
class ThemeBean(
    private var a: Int,
    private var b: Int,
    private var c: Int,
    private var d: Int,
    private var e: Int,
    private var f: Int,
    private var g: Int,
    private var h: Int,
    private var i: Int,
    private var j: Int,
    private var k: Int,
    private var l: Int,
    private var m: Int,
    private var n: Int,
    private var o: Int,
    private var p: Int,
    private var q: Int,
) {
    private var r: String? = null

    fun getBgRes(): Int = a

    fun getcIconRes(): Int = b

    fun getfIconRes(): Int = c

    fun getFrameIcon(): Int = d

    fun getSwitchIcon(): Int = e

    fun getAddRes(): Int = f

    fun getRefreshBgRes(): Int = g

    fun getRefreshSrcRes(): Int = h

    fun setRefreshSrcRes(value: Int) {
        h = value
    }

    fun getListRes(): Int = i

    fun getInfoBgRes(): Int = j

    fun getForecastBgRes(): Int = k

    fun getcLableIcon(): Int = l

    fun getfLableIcon(): Int = m

    fun getCheckIcon(): Int = n

    fun getAlertIcon(): Int = o

    fun getTipsIcon(): Int = p

    fun getAqiTextColor(): Int = q

    fun getThemeType(): String? = r

    fun setThemeType(type: String?) {
        r = type
    }
}
