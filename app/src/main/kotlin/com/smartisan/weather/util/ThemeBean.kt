package com.smartisan.weather.util

/** Immutable resource bundle shared by weather pages and the desktop widget. */
class ThemeBean(
    private val backgroundRes: Int,
    private val celsiusIconRes: Int,
    private val fahrenheitIconRes: Int,
    private val frameIconRes: Int,
    private val switchIconRes: Int,
    private val addIconRes: Int,
    private val refreshBackgroundRes: Int,
    private val refreshIconRes: Int,
    private val listIconRes: Int,
    private val infoBackgroundRes: Int,
    private val forecastBackgroundRes: Int,
    private val celsiusLabelIconRes: Int,
    private val fahrenheitLabelIconRes: Int,
    private val checkedIconRes: Int,
    private val alertIconRes: Int,
    private val aqiTipsIconRes: Int,
    private val aqiTextColorRes: Int,
) {
    fun getBgRes(): Int = backgroundRes
    fun getcIconRes(): Int = celsiusIconRes
    fun getfIconRes(): Int = fahrenheitIconRes
    fun getFrameIcon(): Int = frameIconRes
    fun getSwitchIcon(): Int = switchIconRes
    fun getAddRes(): Int = addIconRes
    fun getRefreshBgRes(): Int = refreshBackgroundRes
    fun getRefreshSrcRes(): Int = refreshIconRes
    fun getListRes(): Int = listIconRes
    fun getInfoBgRes(): Int = infoBackgroundRes
    fun getForecastBgRes(): Int = forecastBackgroundRes
    fun getcLableIcon(): Int = celsiusLabelIconRes
    fun getfLableIcon(): Int = fahrenheitLabelIconRes
    fun getCheckIcon(): Int = checkedIconRes
    fun getAlertIcon(): Int = alertIconRes
    fun getTipsIcon(): Int = aqiTipsIconRes
    fun getAqiTextColor(): Int = aqiTextColorRes
}
