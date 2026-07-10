package com.smartisan.weather.util

import android.util.Log
import com.smartisan.weather.BuildConfig

/**
 * 日志工具，复刻自原版 com.smartisan.weather.util.DebugLog。
 *
 * 原版通过 android.os.SystemProperties.getInt("ro.debuggable", 0) 判断是否可调试，
 * Smartisan OS 框架类此处替换为常量开关（DBG），保持与原版一致的日志门控行为。
 */
object DebugLog {

    private val DBG = BuildConfig.DEBUG

    /** 异常日志 tag，原版被多处引用（Utility / HourForecastInfo 等），故公开。 */
    const val TAG_EXCEPTION = "EXCEPTION"

    /** 等级阈值：DBG 时为 2，否则为 3，与原版一致。 */
    private val level = if (DBG) 2 else 3

    fun d(tag: String, msg: String?) {
        if (isLevelLoggable(3)) Log.d(tag, msg ?: "null")
    }

    fun isLevelLoggable(lvl: Int): Boolean = level <= lvl

    fun log(tag: String, msg: String?) {
        if (DBG) Log.d(tag, msg ?: "null")
    }

    fun log(tag: String, msg: String?, tr: Throwable) {
        if (DBG) Log.d(tag, msg ?: "null", tr)
    }

    fun logError(tag: String, msg: String?) {
        if (DBG) Log.e(tag, msg ?: "null")
    }

    fun logError(tag: String, msg: String?, tr: Throwable) {
        if (DBG) Log.e(tag, msg ?: "null", tr)
    }

    fun v(tag: String, msg: String?) {
        if (isLevelLoggable(2)) Log.d(tag, msg ?: "null")
    }
}
