package com.smartisan.weather.data.weather

import android.net.Uri
import android.util.Log
import com.smartisan.weather.BuildConfig
import com.smartisan.weather.data.model.AirQuality
import com.smartisan.weather.data.model.Allergy
import com.smartisan.weather.data.model.AlertInfo
import com.smartisan.weather.data.model.DailyForecast
import com.smartisan.weather.data.model.HourForecast
import com.smartisan.weather.data.model.Observe
import com.smartisan.weather.data.model.SearchResultCity
import com.smartisan.weather.data.model.Weather
import com.smartisan.weather.data.model.WeatherAlert
import com.smartisan.weather.util.WeatherCodeMapping
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 天气 API 客户端。
 *
 * 完整复刻原版 WeatherDataRepositoryV3Impl + RepoUtils + WeatherFindCityRequest 的功能：
 * - API 签名算法：MD5(queryString_without_ampersand + "smartisan_weather_api")
 * - 天气数据请求：https://api-weather.smartisan.com/v3/info.php
 * - 城市搜索：https://api-weather.smartisan.com/v3/info/getCity
 * - JSON 解析：observe / forecast / air / forecast_hour / alert / allergy
 */
class WeatherApiClient private constructor() {

    companion object {
        private const val TAG = "WeatherApiClient"
        private const val SERVER_URL_BASE = "https://api-weather.smartisan.com/v3/info.php"
        private const val SEARCH_URL = "https://api-weather.smartisan.com/v3/info/getCity"
        private const val SECRET_KEY = "smartisan_weather_api"
        private const val APP_SOURCE = "com.android.providers.weather"
        private const val WEATHER_FIELDS = "forecast,observe,air,forecast_hour,alert,allergy"
        private const val CONNECT_TIMEOUT = 3000
        private const val READ_TIMEOUT = 15000
        private const val MAX_RETRIES = 3
        private const val RETRY_BACKOFF_MILLIS = 200L
        private const val MAX_CONCURRENT_REQUESTS = 4
        private val API_ZONE_OFFSET: ZoneOffset = ZoneOffset.ofHours(8)
        private val PUBLISH_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("uuuuMMddHHmm", Locale.ROOT)
                .withResolverStyle(ResolverStyle.STRICT)
        private val ALERT_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)
                .withResolverStyle(ResolverStyle.STRICT)

        /**
         * 所有客户端实例（当前为单例）共享同一个许可池，避免主页面一次为全部城市
         * 启动无限量的阻塞连接；城市搜索也计入同一网络预算。
         */
        private val requestSemaphore = Semaphore(MAX_CONCURRENT_REQUESTS)

        @Volatile
        private var instance: WeatherApiClient? = null

        fun getInstance(): WeatherApiClient =
            instance ?: synchronized(this) {
                instance ?: WeatherApiClient().also { instance = it }
            }
    }

    /** MD5 签名：MD5(data + salt) → 32位小写hex */
    private fun md5(data: String, salt: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val chars = (data + salt).toCharArray()
            val bytes = ByteArray(chars.size)
            for (i in chars.indices) bytes[i] = chars[i].code.toByte()
            val digest = md.digest(bytes)
            val sb = StringBuilder()
            for (b in digest) {
                val v = b.toInt() and 0xff
                if (v < 16) sb.append('0')
                sb.append(Integer.toHexString(v))
            }
            sb.toString()
        } catch (e: Exception) {
            ""
        }
    }

    /** 构建天气数据请求 URL */
    fun buildWeatherUrl(cityId: String, versionCode: String = "104"): String {
        val requestTime = System.currentTimeMillis().toString()
        val uriBuilder = Uri.parse(SERVER_URL_BASE).buildUpon()
            .appendQueryParameter("app", APP_SOURCE)
            .appendQueryParameter("city_id", cityId)
            .appendQueryParameter("fields", WEATHER_FIELDS)
            .appendQueryParameter("rtime", requestTime)
            .appendQueryParameter("vcode", versionCode)

        val key = weatherSignature(cityId, requestTime, versionCode)
        uriBuilder.appendQueryParameter("key", key)
        return uriBuilder.build().toString()
    }

    internal fun weatherSignature(
        cityId: String,
        requestTime: String,
        versionCode: String = "104",
    ): String = md5(
        "app=$APP_SOURCE" +
            "city_id=$cityId" +
            "fields=$WEATHER_FIELDS" +
            "rtime=$requestTime" +
            "vcode=$versionCode",
        SECRET_KEY,
    )

    /** 构建城市搜索 URL */
    fun buildSearchUrl(query: String, page: Int): String {
        // 原版服务端按 UTF-8 字节签名，而不是直接按 Unicode code point 截断。
        val key = searchSignature(query, page)
        return Uri.parse(SEARCH_URL).buildUpon()
            .appendQueryParameter("q", query)
            .appendQueryParameter("page", page.toString())
            .appendQueryParameter("size", "20")
            .appendQueryParameter("key", key)
            .build().toString()
    }

    internal fun searchSignature(query: String, page: Int): String {
        val signingQuery = String(
            query.toByteArray(StandardCharsets.UTF_8),
            StandardCharsets.ISO_8859_1,
        )
        return md5("page=$page" + "q=$signingQuery" + "size=20", SECRET_KEY)
    }

    /** HTTP GET：最多四路并发，并让阻塞连接随调用协程一起取消。 */
    private suspend fun httpGet(url: String): String = requestSemaphore.withPermit {
        var lastError: IOException? = null
        repeat(MAX_RETRIES) { attempt ->
            currentCoroutineContext().ensureActive()
            try {
                return@withPermit executeGet(url)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: HttpStatusException) {
                currentCoroutineContext().ensureActive()
                if (!error.retryable) throw error
                lastError = error
                debugWarning(
                    "Retryable HTTP status ${error.statusCode}; " +
                        "attempt ${attempt + 1}/$MAX_RETRIES",
                )
            } catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                lastError = error
                debugWarning(
                    "Transport failure ${error.javaClass.simpleName}; " +
                        "attempt ${attempt + 1}/$MAX_RETRIES",
                )
            }

            if (attempt < MAX_RETRIES - 1) {
                // delay 是可取消的；线性短退避足以避免瞬时故障时紧密重连。
                delay(RETRY_BACKOFF_MILLIS * (attempt + 1L))
            }
        }
        throw lastError ?: IOException("Request failed")
    }

    /**
     * `runInterruptible` 负责中断执行阻塞 I/O 的线程；部分 HttpURLConnection
     * 实现不会可靠响应线程中断，因此取消回调还会主动 disconnect 当前连接。
     */
    private suspend fun executeGet(url: String): String = coroutineScope {
        val requestJob = currentCoroutineContext().job
        val activeConnection = AtomicReference<HttpURLConnection?>()
        // 与请求同属一个结构化作用域；父协程一旦取消，finally 会立即断开连接。
        val cancellationWatcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                activeConnection.getAndSet(null)?.disconnect()
            }
        }

        try {
            runInterruptible(Dispatchers.IO) {
                requestJob.ensureActive()
                val connection = (URL(url).openConnection() as HttpURLConnection).also {
                    activeConnection.set(it)
                }
                try {
                    requestJob.ensureActive()
                    connection.connectTimeout = CONNECT_TIMEOUT
                    connection.readTimeout = READ_TIMEOUT
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept", "application/json")
                    // 不手动声明 gzip — HttpURLConnection 会自动处理 Content-Encoding: gzip。
                    val statusCode = connection.responseCode
                    requestJob.ensureActive()
                    if (statusCode != HttpURLConnection.HTTP_OK) {
                        throw HttpStatusException(statusCode)
                    }
                    InputStreamReader(connection.inputStream, StandardCharsets.UTF_8).use { reader ->
                        reader.readText().also { requestJob.ensureActive() }
                    }
                } finally {
                    activeConnection.compareAndSet(connection, null)
                    connection.disconnect()
                }
            }
        } finally {
            cancellationWatcher.cancel()
            activeConnection.getAndSet(null)?.disconnect()
        }
    }

    /** 获取天气数据 */
    suspend fun fetchWeather(cityId: String): Weather? {
        return try {
            val json = httpGet(buildWeatherUrl(cityId))
            parseWeather(json)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            debugWarning("Weather request failed: ${error.javaClass.simpleName}")
            null
        }
    }

    /** 搜索城市 */
    suspend fun searchCities(query: String, page: Int = 1): List<SearchResultCity> {
        return searchCitiesResult(query, page).getOrElse { emptyList() }
    }

    /** 搜索城市，并保留“网络/服务失败”和“成功但无结果”的区别供 UI 展示。 */
    suspend fun searchCitiesResult(query: String, page: Int = 1): Result<List<SearchResultCity>> {
        if (query.isBlank()) return Result.success(emptyList())
        return try {
            Result.success(parseSearchResultsOrThrow(httpGet(buildSearchUrl(query, page))))
        } catch (cancelled: CancellationException) {
            // Result.runCatching 会吞掉协程取消；这里必须保持结构化并发语义。
            throw cancelled
        } catch (error: Exception) {
            debugWarning("City search failed: ${error.javaClass.simpleName}")
            // 不把底层异常（其 message 可能包含完整 URL）暴露给上层或崩溃日志。
            Result.failure(IOException("City search request failed"))
        }
    }

    private fun debugWarning(message: String) {
        if (BuildConfig.DEBUG) Log.w(TAG, message)
    }

    private class HttpStatusException(
        val statusCode: Int,
    ) : IOException("HTTP status $statusCode") {
        val retryable: Boolean =
            statusCode == HttpURLConnection.HTTP_CLIENT_TIMEOUT ||
                statusCode == 425 ||
                statusCode == 429 ||
                statusCode in 500..599
    }

    // ====== JSON 解析 ======

    /** 解析天气数据 JSON */
    fun parseWeather(jsonStr: String): Weather? {
        return try {
            val root = JSONObject(jsonStr)
            if (root.optInt("code", -1) != 0 || !root.has("data") || root.isNull("data")) {
                debugWarning("Weather API returned code=${root.optInt("code", -1)}")
                return null
            }
            val data = root.optJSONObject("data") ?: return null
            parseWeatherData(data)
        } catch (e: Exception) {
            debugWarning("Failed to parse weather response: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun parseWeatherData(data: JSONObject): Weather {
        val weather = Weather(source = data.optString("source"))

        // observe → 当前天气
        val observe = if (data.has("observe") && !data.isNull("observe")) {
            parseObserve(data.optJSONObject("observe")!!)
        } else Observe()
        var observeVal = observe

        // air → 空气质量
        var airQuality = AirQuality()
        if (data.has("air") && !data.isNull("air")) {
            val air = data.optJSONObject("air")!!
            airQuality = parseAirQuality(air)
            observeVal = observeVal.copy(
                aqi = airQuality.aqiValue,
                primary = airQuality.primary,
                pm25 = airQuality.pm25,
                pm10 = airQuality.pm10,
                o3 = airQuality.o3,
                no2 = airQuality.no2,
                so2 = airQuality.so2,
                co = airQuality.co,
            )
        }

        // local_time → 本地时间
        val localTimeSeconds = data.optString("local_time")
            .toLongOrNull()
            ?.takeIf { it > 0L }
        if (localTimeSeconds != null && observeVal.localTime.isBlank()) {
            val startOfApiDay = Instant.ofEpochSecond(localTimeSeconds)
                .atOffset(API_ZONE_OFFSET)
                .toLocalDate()
                .atStartOfDay()
                .toInstant(API_ZONE_OFFSET)
                .toEpochMilli()
            observeVal = observeVal.copy(localTime = startOfApiDay.toString())
        }

        // forecast → 每日预报
        val forecastReferenceDate = localTimeSeconds
            ?.let { Instant.ofEpochSecond(it).atOffset(API_ZONE_OFFSET).toLocalDate() }
            ?: observeVal.pubdate.toLongOrNull()
                ?.let { Instant.ofEpochMilli(it).atOffset(API_ZONE_OFFSET).toLocalDate() }
        val dailyForecast = if (data.has("forecast") && !data.isNull("forecast")) {
            parseDailyForecast(data.optJSONObject("forecast")!!, forecastReferenceDate)
        } else emptyList()

        // forecast_hour → 逐小时预报
        val hourForecast = if (data.has("forecast_hour") && !data.isNull("forecast_hour")) {
            parseHourForecast(data.optJSONObject("forecast_hour")!!)
        } else emptyList()

        // allergy → 生活指数
        val allergy = if (data.has("allergy") && !data.isNull("allergy")) {
            parseAllergy(data.optJSONObject("allergy")!!)
        } else Allergy()

        // alert → 天气预警
        val alert = if (data.has("alert") && !data.isNull("alert")) {
            parseAlert(data.optJSONObject("alert")!!)
        } else WeatherAlert()

        // 用 observe 填充顶层字段
        return weather.copy(
            observe = observeVal,
            dailyForecast = dailyForecast,
            hourForecast = hourForecast,
            allergy = allergy,
            alert = alert,
            airQuality = airQuality,
            weatherCode = observeVal.code,
            temp = observeVal.tempC,
            windDirection = observeVal.wind,
            windSpeed = observeVal.speed,
            relativeHumidity = observeVal.humidity,
            compareC = WeatherCodeMapping.safeParseInt(observeVal.compareC),
            compareF = WeatherCodeMapping.compareC2F(WeatherCodeMapping.safeParseInt(observeVal.compareC)),
            realFeelTemp = observeVal.bodyFeelC,
            pubdate = observeVal.pubdate,
        )
    }

    /** 解析 observe 节点 */
    private fun parseObserve(obs: JSONObject): Observe {
        val info = obs.optJSONObject("info") ?: return Observe()
        val tempC = info.optString("temp")
        val tempF = if (tempC.isNotBlank() && tempC != "UNKNOWN") {
            WeatherCodeMapping.celsiusToFahrenheit(WeatherCodeMapping.safeParseInt(tempC)).toString()
        } else "UNKNOWN"
        val bodyFeelC = info.optString("body_feel")
        val bodyFeelF = if (bodyFeelC.isNotBlank() && bodyFeelC != "UNKNOWN") {
            WeatherCodeMapping.celsiusToFahrenheit(WeatherCodeMapping.safeParseInt(bodyFeelC)).toString()
        } else "UNKNOWN"
        val compareCStr = info.optString("compare")
        val compareFStr = if (compareCStr.isNotBlank() && compareCStr != "UNKNOWN") {
            WeatherCodeMapping.compareC2F(compareCStr.toInt()).toString()
        } else "UNKNOWN"

        // publish_time "yyyyMMddHHmm" → epoch ms
        val pubTimeStr = obs.optString("publish_time")
        val pubdate = parsePublishTime(pubTimeStr)

        return Observe(
            tempC = tempC.ifBlank { "UNKNOWN" },
            tempF = tempF,
            code = info.optString("code"),
            wind = info.optString("wind"),
            speed = info.optString("speed"),
            humidity = info.optString("humidity"),
            compareC = compareCStr.ifBlank { "UNKNOWN" },
            compareF = compareFStr,
            bodyFeelC = bodyFeelC.ifBlank { "UNKNOWN" },
            bodyFeelF = bodyFeelF,
            pubdate = pubdate,
        )
    }

    /** 解析 publish_time "yyyyMMddHHmm" → 时间字符串 */
    private fun parsePublishTime(pt: String): String {
        return try {
            if (pt.length >= 12) {
                LocalDateTime.parse(pt.substring(0, 12), PUBLISH_TIME_FORMATTER)
                    .toInstant(API_ZONE_OFFSET)
                    .toEpochMilli()
                    .toString()
            } else ""
        } catch (e: Exception) {
            ""
        }
    }

    /** 解析 air 节点 */
    private fun parseAirQuality(air: JSONObject): AirQuality {
        val info = air.optJSONObject("info") ?: return AirQuality()
        return AirQuality(
            aqiValue = info.optString("aqi"),
            primary = info.optString("primary"),
            pm25 = info.optString("pm2_5"),
            pm10 = info.optString("pm10"),
            o3 = info.optString("o3"),
            no2 = info.optString("no2"),
            so2 = info.optString("so2"),
            co = info.optString("co"),
            publishTime = info.optString("publish_time"),
        )
    }

    /** 解析 forecast 节点 → 每日预报列表 */
    private fun parseDailyForecast(
        forecast: JSONObject,
        referenceDate: LocalDate?,
    ): List<DailyForecast> {
        val arr = forecast.optJSONArray("info") ?: return emptyList()
        val list = mutableListOf<DailyForecast>()
        val dayNames = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val date = item.optString("date")
            val forecastDate = runCatching { LocalDate.parse(date) }.getOrNull()
            if (forecastDate != null && referenceDate != null && forecastDate.isBefore(referenceDate)) {
                continue
            }
            val lowC = WeatherCodeMapping.safeParseInt(item.optString("low"))
            val highC = WeatherCodeMapping.safeParseInt(item.optString("high"))
            val codeAm = item.optString("code1")
            val codePm = item.optString("code2").ifBlank { codeAm }
            val weekDay = forecastDate?.let { dayNames[it.dayOfWeek.value % dayNames.size] }.orEmpty()

            list.add(
                DailyForecast(
                    date = date.replace("-", "") + "0000",
                    weekDay = weekDay,
                    weatherCodeAm = codeAm,
                    weatherCodePm = codePm,
                    highTempC = highC,
                    highTempF = WeatherCodeMapping.celsiusToFahrenheit(highC),
                    lowTempC = lowC,
                    lowTempF = WeatherCodeMapping.celsiusToFahrenheit(lowC),
                    sunriseAndSunset = item.optString("sun"),
                )
            )
        }
        return list
    }

    /** 解析 forecast_hour 节点 → 逐小时预报列表 */
    private fun parseHourForecast(hourData: JSONObject): List<HourForecast> {
        val arr = hourData.optJSONArray("info") ?: return emptyList()
        val list = mutableListOf<HourForecast>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val tempC = if (item.has("tempC")) {
                item.optInt("tempC", -1)
            } else {
                item.optInt("temp", -1)
            }
            val tempF = if (tempC != -1) WeatherCodeMapping.celsiusToFahrenheit(tempC) else -1
            val code = item.optString("code")
            list.add(
                HourForecast(
                    code = code,
                    weatherCode = item.optString("weatherCode").ifBlank { code },
                    temp = item.optString("temp"),
                    tempC = tempC,
                    tempF = tempF,
                    startTime = item.optString("startTime").ifBlank {
                        item.optString("f_start_time").ifBlank { item.optString("start_time") }
                    },
                    night = item.optBoolean("night"),
                    sunDes = item.optString("sunDes"),
                )
            )
        }
        return list
    }

    /** 解析 allergy 节点 */
    private fun parseAllergy(allergyNode: JSONObject): Allergy {
        val arr = allergyNode.optJSONArray("info") ?: return Allergy()
        var ag = Allergy()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            when (item.optString("type")) {
                "ag" -> ag = ag.copy(
                    agContent = item.optString("content"),
                    agTypeCn = item.optString("type_cn"),
                    agLevel = item.optString("level"),
                )
                "uv" -> ag = ag.copy(
                    uvContent = item.optString("content"),
                    uvTypeCn = item.optString("type_cn"),
                    uvLevel = item.optString("level"),
                )
            }
        }
        return ag
    }

    /** 解析 alert 节点 */
    private fun parseAlert(alertNode: JSONObject): WeatherAlert {
        val arr: JSONArray = alertNode.optJSONArray("info") ?: return WeatherAlert()
        if (arr.length() == 0) return WeatherAlert()
        val list = mutableListOf<AlertInfo>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val time = runCatching {
                LocalDateTime.parse(item.optString("publish_time"), ALERT_TIME_FORMATTER)
                    .toInstant(API_ZONE_OFFSET)
                    .toEpochMilli()
            }.getOrDefault(0L)
            list.add(
                AlertInfo(
                    typeNumber = item.optString("type_number"),
                    level = item.optString("level"),
                    content = item.optString("content"),
                    publishTime = time,
                    levelNumber = item.optString("level_number"),
                    type = item.optString("type"),
                )
            )
        }
        list.sortBy { it.publishTime }
        return WeatherAlert(infos = list)
    }

    /** 解析城市搜索结果 */
    fun parseSearchResults(jsonStr: String): List<SearchResultCity> {
        return try {
            parseSearchResultsOrThrow(jsonStr)
        } catch (e: Exception) {
            debugWarning("Failed to parse city search response: ${e.javaClass.simpleName}")
            emptyList()
        }
    }

    private fun parseSearchResultsOrThrow(jsonStr: String): List<SearchResultCity> {
        val root = JSONObject(jsonStr)
        val code = root.optInt("code", -1)
        if (code != 0) throw IOException("City search API returned code=$code")
        if (!root.has("data") || root.isNull("data")) return emptyList()
        val data = root.getJSONObject("data")
        val arr = data.optJSONArray("content") ?: return emptyList()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                add(
                    SearchResultCity(
                        cityId = item.optString("cityId"),
                        county = item.optString("county"),
                        city = item.optString("city"),
                        province = item.optString("province"),
                        country = item.optString("country"),
                        countyEn = item.optString("countyEn"),
                        countyPinyin = item.optString("countyPinyin"),
                        id = item.optString("id"),
                    )
                )
            }
        }
    }
}
