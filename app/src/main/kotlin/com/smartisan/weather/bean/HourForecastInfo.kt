package com.smartisan.weather.bean

import android.text.TextUtils
import com.smartisan.weather.util.DebugLog
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 单条逐小时预报，复刻自原版 com.smartisan.weather.bean.HourForecastInfo。
 *
 * 原版 code/locationKey/night/startTime/sunDes/temp/tempC/tempF/weatherCode 为 public 字段，
 * publishTime 为 private + getter/setter；同时 startTime/sunDes/weatherCode/tempC/tempF/locationKey
 * 也提供显式 getter/setter。Kotlin 中不能同时拥有 public var 与同名 getter 方法（JVM 签名冲突），
 * 故将需以 .getXxx()/.setXxx() 访问的字段改为 private + 显式方法，保留原版无 getter 的 public 字段
 * （code/night/temp）以 @JvmField 暴露（night 被以字段形式读写）。
 */
class HourForecastInfo : Serializable {
    @JvmField var code: String? = null
    @JvmField var night: Boolean = false
    @JvmField var temp: String? = null

    private var locationKey: String? = null
    private var startTime: String? = null
    private var sunDes: String? = null
    private var tempC: Int = -1
    private var tempF: Int = -1
    private var weatherCode: String? = null
    private var publishTime: String? = null

    fun getLocationKey(): String? = locationKey
    fun setLocationKey(value: String?) { locationKey = value }

    fun getStartTime(): String? = startTime
    fun setStartTime(value: String?) { startTime = value }

    fun getSunDes(): String? = sunDes
    fun setSunDes(value: String?) { sunDes = value }

    fun getTempC(): Int = tempC
    fun setTempC(value: Int) { tempC = value }

    fun getTempF(): Int = tempF
    fun setTempF(value: Int) { tempF = value }

    fun getWeatherCode(): String? = weatherCode
    fun setWeatherCode(value: String?) { weatherCode = value }

    fun getPublishTime(): String? = publishTime
    fun setPublishTime(value: String?) { publishTime = value }

    override fun toString(): String =
        "code - $code\n temp - $temp\n startTime - $startTime\n weatherCode - $weatherCode" +
            "\n tempC - $tempC\n tempF - $tempF"

    companion object {
        @JvmField
        val mTimeZoneGMT8: TimeZone = TimeZone.getTimeZone("GMT+8")

        @JvmField
        val mFormatYMDH: SimpleDateFormat = SimpleDateFormat("yyyyMMddHH", Locale.ROOT)

        @JvmStatic
        fun getDate(str: String?): Date? {
            val sdf = SimpleDateFormat("yyyyMMddHHmm", Locale.ROOT)
            return try {
                sdf.timeZone = mTimeZoneGMT8
                sdf.parse(str!!.substring(0, 12))
            } catch (e: Exception) {
                DebugLog.log(DebugLog.TAG_EXCEPTION, "", e)
                null
            }
        }

        @JvmStatic
        fun getDateOnly(str: String?): Date? {
            val sdf = SimpleDateFormat("yyyyMMdd", Locale.ROOT)
            return try {
                sdf.timeZone = mTimeZoneGMT8
                sdf.parse(str!!.substring(0, 8))
            } catch (e: Exception) {
                DebugLog.log(DebugLog.TAG_EXCEPTION, "", e)
                null
            }
        }

        @JvmStatic
        fun getDateWithHour(str: String?): Date? {
            return try {
                mFormatYMDH.timeZone = mTimeZoneGMT8
                mFormatYMDH.parse(str!!.substring(0, 10))
            } catch (e: Exception) {
                DebugLog.log(DebugLog.TAG_EXCEPTION, "", e)
                null
            }
        }

        @JvmStatic
        fun getDay(str: String?): Int {
            if (str == null || str.length <= 10) return -1
            return str.substring(6, 8).toInt()
        }

        @JvmStatic
        fun getHour(str: String?): Int {
            if (str == null || TextUtils.isEmpty(str) || str.length <= 10) return -1
            return str.substring(8, 10).toInt()
        }

        @JvmStatic
        fun getMonth(str: String?): Int {
            if (str == null || str.length <= 10) return -1
            return str.substring(4, 6).toInt()
        }
    }
}
