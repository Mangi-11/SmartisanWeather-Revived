package com.smartisan.weather.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.smartisan.weather.R
import com.smartisan.weather.bean.HourForecast
import com.smartisan.weather.bean.HourForecastInfo
import com.smartisan.weather.bean.SmartisanLocation
import com.smartisan.weather.bean.Weather
import com.smartisan.weather.data.settings.WeatherSettings
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs

/**
 * 通用工具集，复刻自原版 com.smartisan.weather.util.Utility。
 *
 * Smartisan OS 框架依赖替换说明：
 * - SettingsSmt（系统温度单位设置键/常量）→ 内联常量 TEMPERATURE_UNIT_CELSIUS / TEMPERATURE_UNIT_KEY
 * - NullSafe.nonNull(ConnectivityManager) → 标准可空类型转换
 * 温度单位统一由 [WeatherSettings] 的 DataStore 管理。
 */
object Utility {
    const val TEXT_TEMP_TYPE_C = "°C"
    const val TEXT_TEMP_TYPE_F = "°F"

    @JvmField
    var isDebugingUnknownIcon: Boolean = false

    private var a: Pattern? = null
    private val b = arrayOf("日", "一", "二", "三", "四", "五", "六")
    @JvmStatic
    fun TempC2TempF(f: Float): Float =
        DecimalFormat("##0.0", DecimalFormatSymbols(Locale.CHINA))
            .format(f * 1.8f + 32f).toFloat()

    @JvmStatic
    fun TempF2TempC(i: Int): Int = Math.floor(((i - 32) / 1.8f).toDouble()).toInt()

    /** 解析 "yyyy-MM-dd=HH:mm" 为当日时间毫秒；失败返回 -1。 */
    private fun parseSunTime(str: String): Long =
        try {
            SimpleDateFormat("yyyy-MM-dd=HH:mm", Locale.ROOT).apply {
                isLenient = false
            }.parse(str)?.time ?: -1L
        } catch (e: Exception) {
            DebugLog.log(DebugLog.TAG_EXCEPTION, "Sun time parsing failed", e)
            -1L
        }

    @JvmStatic
    fun fristCharToUpdderCase(str: String): String {
        if (!isAllEngChars(str)) return str
        return str.substring(0, 1).uppercase() + str.substring(1)
    }

    @JvmStatic
    fun getAQIResId(context: Context, str: String?): Int {
        return try {
            val i = str!!.toInt()
            if (i < 0 || i > 50) {
                if (i <= 50 || i > 100) {
                    if (i <= 100 || i > 150) {
                        if (i <= 150 || i > 200) {
                            if (i <= 200 || i > 300) {
                                if (i > 300) R.string.aqi_hazardous else R.string.unknow
                            } else R.string.aqi_unhealthy_hight
                        } else R.string.aqi_unhealthy_middle
                    } else R.string.aqi_unhealthy_low
                } else R.string.aqi_moderate
            } else R.string.aqi_good
        } catch (e: Exception) {
            R.string.weather_null
        }
    }

    @JvmStatic
    fun getCoGrade(str: String?): Int {
        val i = str?.toIntOrNull() ?: 0
        return if (i < 0 || i > 5) {
            if (i <= 5 || i > 10) {
                if (i <= 10 || i > 35) {
                    if (i <= 35 || i > 60) {
                        if (i <= 60 || i > 90) {
                            if (i > 90) R.drawable.weather_air_severe_pollution_most
                            else R.drawable.weather_air_perfect
                        } else R.drawable.weather_air_severe_pollution
                    } else R.drawable.weather_air_moderately_polluted
                } else R.drawable.weather_air_mild_polluted
            } else R.drawable.weather_air_good
        } else R.drawable.weather_air_perfect
    }

    @JvmStatic
    fun getNo2Grade(str: String?): Int {
        val i = str?.toIntOrNull() ?: 0
        return if (i < 0 || i > 100) {
            if (i <= 100 || i > 200) {
                if (i <= 200 || i > 700) {
                    if (i <= 700 || i > 1200) {
                        if (i <= 1200 || i > 2340) {
                            if (i > 2340) R.drawable.weather_air_severe_pollution_most
                            else R.drawable.weather_air_perfect
                        } else R.drawable.weather_air_severe_pollution
                    } else R.drawable.weather_air_moderately_polluted
                } else R.drawable.weather_air_mild_polluted
            } else R.drawable.weather_air_good
        } else R.drawable.weather_air_perfect
    }

    @JvmStatic
    fun getO3Grade(str: String?): Int {
        val i = str?.toIntOrNull() ?: 0
        return if (i < 0 || i > 160) {
            if (i <= 160 || i > 200) {
                if (i <= 200 || i > 300) {
                    if (i <= 300 || i > 400) {
                        if (i <= 400 || i > 800) {
                            if (i > 90) R.drawable.weather_air_severe_pollution_most
                            else R.drawable.weather_air_perfect
                        } else R.drawable.weather_air_severe_pollution
                    } else R.drawable.weather_air_moderately_polluted
                } else R.drawable.weather_air_mild_polluted
            } else R.drawable.weather_air_good
        } else R.drawable.weather_air_perfect
    }

    @JvmStatic
    fun getPm10Grade(str: String?): Int {
        val i = str?.toIntOrNull() ?: 0
        return if (i < 0 || i > 50) {
            if (i <= 50 || i > 150) {
                if (i <= 150 || i > 250) {
                    if (i <= 250 || i > 350) {
                        if (i <= 350 || i > 420) {
                            if (i > 420) R.drawable.weather_air_severe_pollution_most
                            else R.drawable.weather_air_perfect
                        } else R.drawable.weather_air_severe_pollution
                    } else R.drawable.weather_air_moderately_polluted
                } else R.drawable.weather_air_mild_polluted
            } else R.drawable.weather_air_good
        } else R.drawable.weather_air_perfect
    }

    @JvmStatic
    fun getPm2_5Grade(str: String?): Int {
        val i = str?.toIntOrNull() ?: 0
        return if (i < 0 || i > 35) {
            if (i <= 35 || i > 75) {
                if (i <= 75 || i > 115) {
                    if (i <= 115 || i > 150) {
                        if (i <= 150 || i > 250) {
                            if (i > 250) R.drawable.weather_air_severe_pollution_most
                            else R.drawable.weather_air_perfect
                        } else R.drawable.weather_air_severe_pollution
                    } else R.drawable.weather_air_moderately_polluted
                } else R.drawable.weather_air_mild_polluted
            } else R.drawable.weather_air_good
        } else R.drawable.weather_air_perfect
    }

    @JvmStatic
    fun getSo2Grade(str: String?): Int {
        val i = str?.toIntOrNull() ?: 0
        return if (i < 0 || i > 150) {
            if (i <= 150 || i > 500) {
                if (i <= 500 || i > 650) {
                    if (i <= 650 || i > 800) {
                        if (i <= 800 || i > 1600) {
                            if (i > 1600) R.drawable.weather_air_severe_pollution_most
                            else R.drawable.weather_air_perfect
                        } else R.drawable.weather_air_severe_pollution
                    } else R.drawable.weather_air_moderately_polluted
                } else R.drawable.weather_air_mild_polluted
            } else R.drawable.weather_air_good
        } else R.drawable.weather_air_perfect
    }

    @JvmStatic
    fun getDefaultDeviceTitleView(context: Context, str: String): View {
        val textView = TextView(context)
        textView.text = str
        textView.maxLines = 1
        textView.ellipsize = TextUtils.TruncateAt.END
        textView.setTextSize(
            TypedValue.COMPLEX_UNIT_DIP,
            context.resources.getInteger(R.integer.weather_title_bar_text_dp_value).toFloat(),
        )
        textView.paintFlags = 33
        textView.setTextColor(ContextCompat.getColor(context, R.color.weather_title_text_color))
        return textView
    }

    @JvmStatic
    fun getDisplayTime(context: Context, j: Long): String {
        if (j <= 0) return ""
        val now = System.currentTimeMillis()
        return try {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = now
            val calendar2 = Calendar.getInstance()
            calendar2.timeInMillis = j
            val i = calendar.get(Calendar.DATE) - calendar2.get(Calendar.DATE)
            val i2 = calendar.get(Calendar.HOUR_OF_DAY) - calendar2.get(Calendar.HOUR_OF_DAY)
            val i3 = calendar.get(Calendar.MINUTE) - calendar2.get(Calendar.MINUTE)
            if (i > 0) {
                if (abs(i2) > 24) {
                    context.getString(R.string.weather_alert_display_time_day).format(i)
                } else {
                    context.getString(R.string.weather_alert_display_time_hour).format(i2 + 24)
                }
            } else if (i2 > 0) {
                context.getString(R.string.weather_alert_display_time_hour).format(i2)
            } else if (i3 > 0) {
                context.getString(R.string.weather_alert_display_time_minute).format(i3)
            } else {
                context.getString(R.string.weather_alert_time_now)
            }
        } catch (e: Exception) {
            DebugLog.log(DebugLog.TAG_EXCEPTION, "Alert display time formatting failed", e)
            ""
        }
    }

    @JvmStatic
    fun getHighlightText(str: String?, str2: String, color: Int): SpannableStringBuilder {
        val ssb = SpannableStringBuilder(str2)
        val lower = str2.lowercase()
        if (str.isNullOrEmpty()) return ssb
        val lower2 = str.lowercase()
        if (lower.indexOf(lower2) < 0) return ssb
        var start = -1
        val length = lower2.length
        while (true) {
            val idx = lower.indexOf(lower2, start)
            val end = idx + length
            if (idx >= 0 && idx < str2.length) {
                ssb.setSpan(ForegroundColorSpan(color), idx, end, 33)
            }
            if (idx < 0) return ssb
            start = end
        }
    }

    @JvmStatic
    fun getJSONData(str: String, str2: String): String {
        return try {
            val obj = JSONObject()
            obj.put(str, str2)
            obj.toString()
        } catch (e: JSONException) {
            DebugLog.log(DebugLog.TAG_EXCEPTION, "", e)
            ""
        }
    }

    @JvmStatic
    fun getJSONData(locations: Array<SmartisanLocation?>?): String {
        return try {
            val arr = JSONArray()
            val obj = JSONObject()
            if (locations != null && locations.isNotEmpty()) {
                for (loc in locations) {
                    if (loc != null) {
                        val o = JSONObject()
                        o.put("id", loc.mLocationKey)
                        o.put("area", loc.mLocationName)
                        o.put("city", loc.mLocationParentName)
                        arr.put(o)
                    }
                }
            }
            obj.put("cities", arr)
            obj.toString()
        } catch (e: JSONException) {
            DebugLog.log(DebugLog.TAG_EXCEPTION, "", e)
            ""
        }
    }

    @JvmStatic
    fun getLocaleWeekday(context: Context, str: String): String {
        var s = str
        if (s.length != 1) {
            return context.getString(R.string.weather_null)
        }
        if (!Character.isDigit(s[0])) {
            for (i in b.indices) {
                if (s == b[i]) {
                    s = i.toString()
                }
            }
        }
        val stringArray = context.resources.getStringArray(R.array.weekday_arrays)
        val idx = s.toInt()
        return if (idx < stringArray.size) stringArray[idx] else context.getString(R.string.weather_null)
    }

    @JvmStatic
    fun getSystemTemperatureUnit(context: Context): Int =
        WeatherSettings.getInstance(context).tempUnit.value

    @JvmStatic
    fun getTempUnitDescription(str: String, context: Context): String {
        return if (str.contains("°C")) {
            str.replace("°C", context.getString(R.string.celsius))
        } else if (str.contains("°F")) {
            str.replace("°F", context.getString(R.string.fahrenheit))
        } else {
            str
        }
    }

    @JvmStatic
    fun getTodayDateString(): String = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())

    @JvmStatic
    fun getWeatherDescByCode(context: Context, str: String?): String {
        var code = str
        if (ResMappingUtil.isUnknown(code)) {
            code = "99"
        }
        val textRes = WeatherCodeMapping.textResMap[code] ?: R.string.weather_text_99
        return context.getString(textRes)
    }

    @JvmStatic
    fun insertSunriseAndSunset(context: Context, weather: Weather?) {
        if (weather == null || weather.hourForecast == null || weather.observe == null) return
        val info = weather.hourForecast!!.getmInfo() ?: return
        if (info.isEmpty()) return
        val obs = weather.observe!!
        if (TextUtils.isEmpty(obs.getCurSunRise()) || TextUtils.isEmpty(obs.getCurSunSet()) ||
            TextUtils.isEmpty(obs.getNextSunRise()) || TextUtils.isEmpty(obs.getNextSunSet())
        ) {
            return
        }
        // API 响应或缓存可能缺少某个逐小时起始时间。日出/日落插值属于增强信息，
        // 不能因为一条可选字段为空而让整个主页面崩溃。
        val startTimes = info.map { forecast ->
            forecast.getStartTime()?.toLongOrNull() ?: return
        }
        var jA = parseSunTime(obs.getCurSunRise()!!)
        var jA2 = parseSunTime(obs.getCurSunSet()!!)
        var jA3 = parseSunTime(obs.getNextSunRise()!!)
        val jA4 = parseSunTime(obs.getNextSunSet()!!)
        if (jA == -1L || jA2 == -1L || jA3 == -1L || jA4 == -1L) return

        var i = 0
        val j = startTimes[0]
        var z3 = true
        var z: Boolean
        if (j <= jA || j > jA2) {
            if (j > jA2) {
                jA2 = jA4
            } else {
                jA3 = jA
            }
            z = true
        } else {
            z = false
        }
        val hourForecast = HourForecast()
        while (i < info.size) {
            val jLongValue = startTimes[i]
            if (z) {
                if (jLongValue < jA3 || jLongValue > jA2) {
                    info[i].night = z3
                }
            } else if (jLongValue < jA3 && jLongValue > jA2) {
                info[i].night = z3
            }
            val i2 = i + 1
            val z2: Boolean
            if (i2 >= info.size) {
                z2 = z
            } else {
                if (!TextUtils.isEmpty(info[i].getSunDes()) || !TextUtils.isEmpty(info[i2].getSunDes())) {
                    return
                }
                val jLongValue2 = startTimes[i2]
                z2 = z
                if (jA3 <= jLongValue || jA3 >= jLongValue2) {
                    if (jA3 == jLongValue) {
                        info[i].setSunDes(getWeatherDescByCode(context, context.getString(R.string.weather_sunrise_code)))
                    } else if (jA3 == jLongValue2) {
                        info[i2].setSunDes(getWeatherDescByCode(context, context.getString(R.string.weather_sunrise_code)))
                    }
                    if (jA2 > jLongValue && jA2 < jLongValue2) {
                        val hfi = HourForecastInfo()
                        hfi.setSunDes(getWeatherDescByCode(context, context.getString(R.string.weather_sunset_code)))
                        hfi.setStartTime(jA2.toString())
                        hfi.setWeatherCode(context.getString(R.string.weather_sunset_code))
                        hourForecast.addInfo(info[i])
                        hourForecast.addInfo(hfi)
                    } else if (jA2 == jLongValue) {
                        info[i].setSunDes(getWeatherDescByCode(context, context.getString(R.string.weather_sunset_code)))
                    } else if (jA2 == jLongValue2) {
                        info[i2].setSunDes(getWeatherDescByCode(context, context.getString(R.string.weather_sunset_code)))
                    }
                } else {
                    val hfi2 = HourForecastInfo()
                    hfi2.setSunDes(getWeatherDescByCode(context, context.getString(R.string.weather_sunrise_code)))
                    hfi2.setStartTime(jA3.toString())
                    hfi2.setWeatherCode(context.getString(R.string.weather_sunrise_code))
                    hourForecast.addInfo(info[i])
                    hourForecast.addInfo(hfi2)
                }
                i = i2
                z = z2
                z3 = true
            }
            hourForecast.addInfo(info[i])
            i = i2
            z = z2
            z3 = true
        }
        weather.hourForecast = hourForecast
    }

    @JvmStatic
    fun isAllEngChars(str: String?): Boolean {
        if (TextUtils.isEmpty(str)) return false
        val chars = str!!.toCharArray()
        val length = chars.size
        var count = 0
        for (ch in chars) {
            if (ch.code < 19968 || ch.code > 40869) {
                count++
            }
        }
        return count == length
    }

    @JvmStatic
    fun isCollectionEmpty(collection: Collection<*>?): Boolean = (collection?.size ?: 0) == 0

    @JvmStatic
    fun isDigitalOnly(str: String?): Boolean {
        if (TextUtils.isEmpty(str)) return false
        if (a == null) {
            a = Pattern.compile("^-?\\d+\$")
        }
        return a!!.matcher(str!!).matches()
    }

    @JvmStatic
    fun isLocalCity(loc: SmartisanLocation?): Boolean = loc?.sortOrder == 1

    @JvmStatic
    fun isNetworkConnected(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    @JvmStatic
    fun isNight(weather: Weather?): Boolean = isNight(weather, Date(System.currentTimeMillis()))

    @JvmStatic
    fun isNight(weather: Weather?, date: Date): Boolean {
        if (weather == null) return false
        return try {
            if (weather.newForecast == null || TextUtils.isEmpty(weather.observe?.getCurSunRise()) ||
                TextUtils.isEmpty(weather.observe?.getCurSunSet())
            ) {
                return false
            }
            val obs = weather.observe!!
            val calendar = Calendar.getInstance().apply { time = date }
            val hours = calendar.get(Calendar.HOUR_OF_DAY)
            val minutes = calendar.get(Calendar.MINUTE)
            val str = obs.getCurSunRise()!!.split("=")[1]
            val str2 = obs.getCurSunSet()!!.split("=")[1]
            val idx = str.indexOf(":")
            val idx2 = str2.indexOf(":")
            if (TextUtils.isEmpty(str) || TextUtils.isEmpty(str2) || !str.contains(":") ||
                idx >= str.length || !str2.contains(":") || idx2 >= str2.length
            ) {
                return false
            }
            val h1 = str.substring(0, idx).toInt()
            val m1 = str.substring(idx + 1).toInt()
            val h2 = str2.substring(0, idx2).toInt()
            val m2 = str2.substring(idx2 + 1).toInt()
            if (hours > h1 && hours < h2) return false
            if (hours == h1) {
                if (minutes >= m1) return false
            } else if (hours == h2 && minutes < m2) {
                return false
            }
            true
        } catch (e: Exception) {
            DebugLog.log(DebugLog.TAG_EXCEPTION, "Time range parsing failed", e)
            false
        }
    }

    @JvmStatic
    fun isNumeric(str: String?): Boolean {
        if (TextUtils.isEmpty(str)) return false
        return Pattern.compile("[0-9]*").matcher(str!!).matches()
    }

    @JvmStatic
    fun setSystemTemperatureUnit(context: Context, unit: Int) {
        WeatherSettings.getInstance(context).setTempUnitAsync(unit)
    }

}
