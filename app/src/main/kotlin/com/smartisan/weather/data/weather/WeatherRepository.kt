package com.smartisan.weather.data.weather

import android.content.Context
import com.smartisan.weather.appwidget.WeatherWidgetUpdateNotifier
import com.smartisan.weather.data.city.CityRepository
import com.smartisan.weather.data.model.SavedCity
import com.smartisan.weather.data.model.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 天气数据仓库——协调 API 客户端与本地缓存。
 */
class WeatherRepository(context: Context) {

    private val appContext = context.applicationContext
    private val apiClient = WeatherApiClient.getInstance()
    private val cityRepo = CityRepository(appContext)

    /** 获取天气数据：先返回缓存，再尝试刷新 */
    suspend fun getWeatherWithCache(cityKey: String): Weather? = withContext(Dispatchers.IO) {
        // 尝试从 API 获取
        val weather = apiClient.fetchWeather(cityKey)
        if (weather != null && weather.isComplete) {
            // 缓存到数据库
            val json = weatherToJson(weather)
            cityRepo.cacheWeather(cityKey, json)
            WeatherWidgetUpdateNotifier.notifyDataChanged(appContext)
            weather
        } else {
            // 返回缓存
            cityRepo.getCachedWeather(cityKey)?.let { jsonToWeather(it) }
        }
    }

    /**
     * 仅获取天气数据（不读缓存）。
     *
     * 小组件自己的批量刷新会在全部城市处理完后统一渲染，因此可关闭中途通知。
     */
    suspend fun fetchWeather(
        cityKey: String,
        notifyWidgets: Boolean = true,
    ): Weather? = withContext(Dispatchers.IO) {
        val weather = apiClient.fetchWeather(cityKey)
        if (weather != null && weather.isComplete) {
            val json = weatherToJson(weather)
            cityRepo.cacheWeather(cityKey, json)
            if (notifyWidgets) {
                WeatherWidgetUpdateNotifier.notifyDataChanged(appContext)
            }
        }
        weather
    }

    /** 仅读取缓存 */
    suspend fun getCachedWeather(cityKey: String): Weather? = withContext(Dispatchers.IO) {
        cityRepo.getCachedWeather(cityKey)?.let { jsonToWeather(it) }
    }

    /** 读取缓存及其真实写入时间，避免小组件把响应发布时间误当成本地更新时间。 */
    suspend fun getCachedWeatherSnapshot(cityKey: String): CachedWeatherSnapshot? =
        withContext(Dispatchers.IO) {
            val record = cityRepo.getCachedWeatherRecord(cityKey) ?: return@withContext null
            val weather = jsonToWeather(record.json) ?: return@withContext null
            CachedWeatherSnapshot(
                weather = weather,
                updatedAtMillis = record.updatedAtMillis,
            )
        }

    companion object {
        /** 简单的 JSON 序列化（避免引入 Gson） */
        fun weatherToJson(weather: Weather): String {
            val o = org.json.JSONObject()
            o.put("provider", CACHE_PROVIDER)
            o.put("source", weather.source)
            o.put("weatherCode", weather.weatherCode)
            o.put("temp", weather.temp)
            o.put("windDirection", weather.windDirection)
            o.put("windSpeed", weather.windSpeed)
            o.put("relativeHumidity", weather.relativeHumidity)
            o.put("compareC", weather.compareC)
            o.put("compareF", weather.compareF)
            o.put("realFeelTemp", weather.realFeelTemp)
            o.put("pubdate", weather.pubdate)
            o.put("timezoneOffsetSeconds", weather.timezoneOffsetSeconds)
            o.put("attributionUrl", weather.attributionUrl)

            val obs = org.json.JSONObject()
            obs.put("tempC", weather.observe.tempC)
            obs.put("tempF", weather.observe.tempF)
            obs.put("code", weather.observe.code)
            obs.put("wind", weather.observe.wind)
            obs.put("speed", weather.observe.speed)
            obs.put("humidity", weather.observe.humidity)
            obs.put("compareC", weather.observe.compareC)
            obs.put("compareF", weather.observe.compareF)
            obs.put("bodyFeelC", weather.observe.bodyFeelC)
            obs.put("bodyFeelF", weather.observe.bodyFeelF)
            obs.put("aqi", weather.observe.aqi)
            obs.put("pm25", weather.observe.pm25)
            obs.put("pm10", weather.observe.pm10)
            obs.put("o3", weather.observe.o3)
            obs.put("no2", weather.observe.no2)
            obs.put("so2", weather.observe.so2)
            obs.put("co", weather.observe.co)
            obs.put("primary", weather.observe.primary)
            obs.put("pubdate", weather.observe.pubdate)
            obs.put("localTime", weather.observe.localTime)
            obs.put("curSunRise", weather.observe.curSunRise)
            obs.put("curSunSet", weather.observe.curSunSet)
            obs.put("nextSunRise", weather.observe.nextSunRise)
            obs.put("nextSunSet", weather.observe.nextSunSet)
            obs.put("lowTempC", weather.observe.lowTempC)
            obs.put("lowTempF", weather.observe.lowTempF)
            obs.put("highTempC", weather.observe.highTempC)
            obs.put("highTempF", weather.observe.highTempF)
            obs.put("currentWeekDay", weather.observe.currentWeekDay)
            o.put("observe", obs)

            val dailyArr = org.json.JSONArray()
            weather.dailyForecast.forEach { f ->
                val d = org.json.JSONObject()
                d.put("date", f.date)
                d.put("weekDay", f.weekDay)
                d.put("weatherCodeAm", f.weatherCodeAm)
                d.put("weatherCodePm", f.weatherCodePm)
                d.put("highTempC", f.highTempC)
                d.put("highTempF", f.highTempF)
                d.put("lowTempC", f.lowTempC)
                d.put("lowTempF", f.lowTempF)
                d.put("sunriseAndSunset", f.sunriseAndSunset)
                dailyArr.put(d)
            }
            o.put("dailyForecast", dailyArr)

            val hourArr = org.json.JSONArray()
            weather.hourForecast.forEach { h ->
                val d = org.json.JSONObject()
                d.put("code", h.code)
                d.put("weatherCode", h.weatherCode)
                d.put("temp", h.temp)
                d.put("tempC", h.tempC)
                d.put("tempF", h.tempF)
                d.put("startTime", h.startTime)
                d.put("night", h.night)
                d.put("sunDes", h.sunDes)
                hourArr.put(d)
            }
            o.put("hourForecast", hourArr)

            val allergy = org.json.JSONObject()
            allergy.put("agContent", weather.allergy.agContent)
            allergy.put("agLevel", weather.allergy.agLevel)
            allergy.put("agTypeCn", weather.allergy.agTypeCn)
            allergy.put("uvContent", weather.allergy.uvContent)
            allergy.put("uvLevel", weather.allergy.uvLevel)
            allergy.put("uvTypeCn", weather.allergy.uvTypeCn)
            o.put("allergy", allergy)

            val alertArr = org.json.JSONArray()
            weather.alert.infos.forEach { a ->
                val d = org.json.JSONObject()
                d.put("typeNumber", a.typeNumber)
                d.put("level", a.level)
                d.put("content", a.content)
                d.put("publishTime", a.publishTime)
                d.put("levelNumber", a.levelNumber)
                d.put("type", a.type)
                alertArr.put(d)
            }
            o.put("alert", alertArr)

            val air = org.json.JSONObject()
            air.put("aqiValue", weather.airQuality.aqiValue)
            air.put("primary", weather.airQuality.primary)
            air.put("pm25", weather.airQuality.pm25)
            air.put("pm10", weather.airQuality.pm10)
            air.put("o3", weather.airQuality.o3)
            air.put("no2", weather.airQuality.no2)
            air.put("so2", weather.airQuality.so2)
            air.put("co", weather.airQuality.co)
            air.put("publishTime", weather.airQuality.publishTime)
            o.put("airQuality", air)

            return o.toString()
        }

        fun jsonToWeather(json: String): Weather? = try {
            val o = org.json.JSONObject(json)
            check(o.optString("provider") == CACHE_PROVIDER) {
                "Weather cache belongs to an obsolete provider"
            }
            val obs = o.optJSONObject("observe")
            val observe = if (obs != null) {
                com.smartisan.weather.data.model.Observe(
                    tempC = obs.optString("tempC", "UNKNOWN"),
                    tempF = obs.optString("tempF", "UNKNOWN"),
                    code = obs.optString("code"),
                    wind = obs.optString("wind"),
                    speed = obs.optString("speed"),
                    humidity = obs.optString("humidity"),
                    compareC = obs.optString("compareC", "UNKNOWN"),
                    compareF = obs.optString("compareF", "UNKNOWN"),
                    bodyFeelC = obs.optString("bodyFeelC", "UNKNOWN"),
                    bodyFeelF = obs.optString("bodyFeelF", "UNKNOWN"),
                    aqi = obs.optString("aqi"),
                    pm25 = obs.optString("pm25"),
                    pm10 = obs.optString("pm10"),
                    o3 = obs.optString("o3"),
                    no2 = obs.optString("no2"),
                    so2 = obs.optString("so2"),
                    co = obs.optString("co"),
                    primary = obs.optString("primary"),
                    pubdate = obs.optString("pubdate"),
                    localTime = obs.optString("localTime"),
                    curSunRise = obs.optString("curSunRise"),
                    curSunSet = obs.optString("curSunSet"),
                    nextSunRise = obs.optString("nextSunRise"),
                    nextSunSet = obs.optString("nextSunSet"),
                    lowTempC = obs.optString("lowTempC"),
                    lowTempF = obs.optString("lowTempF"),
                    highTempC = obs.optString("highTempC"),
                    highTempF = obs.optString("highTempF"),
                    currentWeekDay = obs.optString("currentWeekDay"),
                )
            } else com.smartisan.weather.data.model.Observe()

            val dailyList = mutableListOf<com.smartisan.weather.data.model.DailyForecast>()
            o.optJSONArray("dailyForecast")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val d = arr.optJSONObject(i) ?: continue
                    dailyList.add(
                        com.smartisan.weather.data.model.DailyForecast(
                            date = d.optString("date"),
                            weekDay = d.optString("weekDay"),
                            weatherCodeAm = d.optString("weatherCodeAm"),
                            weatherCodePm = d.optString("weatherCodePm"),
                            highTempC = d.optInt("highTempC"),
                            highTempF = d.optInt("highTempF"),
                            lowTempC = d.optInt("lowTempC"),
                            lowTempF = d.optInt("lowTempF"),
                            sunriseAndSunset = d.optString("sunriseAndSunset"),
                        )
                    )
                }
            }

            val hourList = mutableListOf<com.smartisan.weather.data.model.HourForecast>()
            o.optJSONArray("hourForecast")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val d = arr.optJSONObject(i) ?: continue
                    hourList.add(
                        com.smartisan.weather.data.model.HourForecast(
                            code = d.optString("code"),
                            weatherCode = d.optString("weatherCode"),
                            temp = d.optString("temp"),
                            tempC = d.optInt("tempC", -1),
                            tempF = d.optInt("tempF", -1),
                            startTime = d.optString("startTime"),
                            night = d.optBoolean("night"),
                            sunDes = d.optString("sunDes"),
                        )
                    )
                }
            }

            val allergyObj = o.optJSONObject("allergy")
            val allergy = if (allergyObj != null) {
                com.smartisan.weather.data.model.Allergy(
                    agContent = allergyObj.optString("agContent"),
                    agLevel = allergyObj.optString("agLevel"),
                    agTypeCn = allergyObj.optString("agTypeCn"),
                    uvContent = allergyObj.optString("uvContent"),
                    uvLevel = allergyObj.optString("uvLevel"),
                    uvTypeCn = allergyObj.optString("uvTypeCn"),
                )
            } else com.smartisan.weather.data.model.Allergy()

            val alertList = mutableListOf<com.smartisan.weather.data.model.AlertInfo>()
            o.optJSONArray("alert")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val d = arr.optJSONObject(i) ?: continue
                    alertList.add(
                        com.smartisan.weather.data.model.AlertInfo(
                            typeNumber = d.optString("typeNumber"),
                            level = d.optString("level"),
                            content = d.optString("content"),
                            publishTime = d.optLong("publishTime"),
                            levelNumber = d.optString("levelNumber"),
                            type = d.optString("type"),
                        )
                    )
                }
            }

            val airObj = o.optJSONObject("airQuality")
            val airQuality = if (airObj != null) {
                com.smartisan.weather.data.model.AirQuality(
                    aqiValue = airObj.optString("aqiValue"),
                    primary = airObj.optString("primary"),
                    pm25 = airObj.optString("pm25"),
                    pm10 = airObj.optString("pm10"),
                    o3 = airObj.optString("o3"),
                    no2 = airObj.optString("no2"),
                    so2 = airObj.optString("so2"),
                    co = airObj.optString("co"),
                    publishTime = airObj.optString("publishTime"),
                )
            } else com.smartisan.weather.data.model.AirQuality()

            Weather(
                observe = observe,
                dailyForecast = dailyList,
                hourForecast = hourList,
                allergy = allergy,
                alert = com.smartisan.weather.data.model.WeatherAlert(alertList),
                airQuality = airQuality,
                source = o.optString("source"),
                weatherCode = o.optString("weatherCode"),
                temp = o.optString("temp"),
                windDirection = o.optString("windDirection"),
                windSpeed = o.optString("windSpeed"),
                relativeHumidity = o.optString("relativeHumidity"),
                compareC = o.optInt("compareC"),
                compareF = o.optInt("compareF"),
                realFeelTemp = o.optString("realFeelTemp"),
                pubdate = o.optString("pubdate"),
                timezoneOffsetSeconds = o.optInt(
                    "timezoneOffsetSeconds",
                    DEFAULT_TIMEZONE_OFFSET_SECONDS,
                ),
                attributionUrl = o.optString("attributionUrl"),
            )
        } catch (e: Exception) {
            null
        }

        private const val CACHE_PROVIDER = "xiaomi-v1"
        private const val DEFAULT_TIMEZONE_OFFSET_SECONDS = 8 * 60 * 60
    }
}

data class CachedWeatherSnapshot(
    val weather: Weather,
    val updatedAtMillis: Long,
)
