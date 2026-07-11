package com.smartisan.weather.data.weather

import android.util.Log
import com.smartisan.weather.BuildConfig
import com.smartisan.weather.data.model.SearchResultCity
import com.smartisan.weather.data.model.Weather
import java.io.IOException
import java.io.InputStreamReader
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
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
 * 小米天气中国区客户端。
 *
 * 小米的 `weather/all` 是混合数据接口：城市级天气、彩云网格数据和中国环境监测总站
 * 空气质量会被合并成一次响应。应用只使用 `weathercn:` 城市，不接入第二天气源。
 */
class WeatherApiClient private constructor() {

    companion object {
        private const val TAG = "WeatherApiClient"
        private const val API_BASE_URL = "https://weatherapi.market.xiaomi.com/wtr-v3"
        private const val WEATHER_URL = "$API_BASE_URL/weather/all"
        private const val SEARCH_URL = "$API_BASE_URL/location/city/search"
        private const val GEO_URL = "$API_BASE_URL/location/city/geo"

        // 小米公开客户端协议中的固定标识，不是用户密钥，也不应写入日志。
        private const val APP_KEY = "weather20151024"
        private const val SIGN = "zUFJoAR2ZVrDy1vF3D07"
        private const val LOCATION_KEY_PREFIX = "weathercn:"
        private const val LOCALE = "zh_cn"
        private const val FORECAST_DAYS = "15"

        private const val CONNECT_TIMEOUT = 3_000
        private const val READ_TIMEOUT = 15_000
        private const val MAX_RETRIES = 3
        private const val RETRY_BACKOFF_MILLIS = 200L
        private const val MAX_CONCURRENT_REQUESTS = 4

        /** 主页面会同时刷新多座城市，搜索和定位请求也共享同一网络预算。 */
        private val requestSemaphore = Semaphore(MAX_CONCURRENT_REQUESTS)

        @Volatile
        private var instance: WeatherApiClient? = null

        fun getInstance(): WeatherApiClient =
            instance ?: synchronized(this) {
                instance ?: WeatherApiClient().also { instance = it }
            }
    }

    /**
     * 构建混合天气请求。
     *
     * 当前服务端会按 [cityId] 解析彩云所需的标准坐标；线上交叉验证表明传入 `0,0`、
     * 任意坐标或城市标准坐标均会规范化为相同城市数据，因此遵循小米文档使用固定值。
     */
    fun buildWeatherUrl(cityId: String): String {
        val normalizedCityId = normalizeCityId(cityId)
        require(normalizedCityId.matches(CHINA_CITY_ID)) { "Invalid China weather city id" }
        return buildUrl(
            WEATHER_URL,
            listOf(
                "latitude" to "0",
                "longitude" to "0",
                "locationKey" to "$LOCATION_KEY_PREFIX$normalizedCityId",
                "days" to FORECAST_DAYS,
                "appKey" to APP_KEY,
                "sign" to SIGN,
                "isGlobal" to "false",
                "locale" to LOCALE,
            ),
        )
    }

    /** 小米城市搜索没有分页；服务端一次最多返回二十条。 */
    fun buildSearchUrl(query: String): String = buildUrl(
        SEARCH_URL,
        listOf(
            "name" to query.trim(),
            "locale" to LOCALE,
        ),
    )

    /** 使用天气服务自己的坐标反查，避免设备 Geocoder 的语言和厂商差异。 */
    fun buildGeoUrl(latitude: Double, longitude: Double): String {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Invalid latitude" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Invalid longitude" }
        return buildUrl(
            GEO_URL,
            listOf(
                "latitude" to latitude.toPlainCoordinate(),
                "longitude" to longitude.toPlainCoordinate(),
                "locale" to LOCALE,
            ),
        )
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
                delay(RETRY_BACKOFF_MILLIS * (attempt + 1L))
            }
        }
        throw lastError ?: IOException("Request failed")
    }

    /**
     * `runInterruptible` 会中断阻塞 I/O；取消观察器同时主动 disconnect，兼容不可靠响应
     * 线程中断的 HttpURLConnection 实现。
     */
    private suspend fun executeGet(url: String): String = coroutineScope {
        val requestJob = currentCoroutineContext().job
        val activeConnection = AtomicReference<HttpURLConnection?>()
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

    suspend fun fetchWeather(cityId: String): Weather? {
        return try {
            parseWeather(httpGet(buildWeatherUrl(cityId))).also { weather ->
                when {
                    weather == null -> debugWarning("Weather response could not be parsed")
                    !weather.isComplete -> debugWarning("Weather response is incomplete")
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            debugWarning("Weather request failed: ${error.javaClass.simpleName}")
            null
        }
    }

    suspend fun searchCities(query: String): List<SearchResultCity> =
        searchCitiesResult(query).getOrElse { emptyList() }

    /** 保留“请求失败”和“成功但无结果”的区别供搜索页展示。 */
    suspend fun searchCitiesResult(query: String): Result<List<SearchResultCity>> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return Result.success(emptyList())
        return try {
            Result.success(parseLocationResultsOrThrow(httpGet(buildSearchUrl(normalizedQuery))))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            debugWarning("City search failed: ${error.javaClass.simpleName}")
            Result.failure(IOException("City search request failed"))
        }
    }

    /** 坐标反查可能成功但不含中国天气城市，因此结果本身可为空。 */
    suspend fun resolveCityByCoordinatesResult(
        latitude: Double,
        longitude: Double,
    ): Result<SearchResultCity?> {
        return try {
            val cities = parseLocationResultsOrThrow(httpGet(buildGeoUrl(latitude, longitude)))
            Result.success(cities.firstOrNull())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            debugWarning("Coordinate city lookup failed: ${error.javaClass.simpleName}")
            Result.failure(IOException("Coordinate city lookup failed"))
        }
    }

    fun parseWeather(json: String): Weather? = XiaomiWeatherParser.parseWeather(json)

    fun parseSearchResults(json: String): List<SearchResultCity> =
        runCatching { parseLocationResultsOrThrow(json) }.getOrElse {
            debugWarning("Failed to parse city response: ${it.javaClass.simpleName}")
            emptyList()
        }

    private fun parseLocationResultsOrThrow(json: String): List<SearchResultCity> =
        XiaomiWeatherParser.parseLocationResults(json)

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
}

private val CHINA_CITY_ID = Regex("\\d{9}")

private fun normalizeCityId(cityId: String): String =
    cityId.trim().removePrefix("weathercn:")

private fun buildUrl(baseUrl: String, parameters: List<Pair<String, String>>): String =
    parameters.joinToString(prefix = "$baseUrl?", separator = "&") { (name, value) ->
        "${name.urlEncode()}=${value.urlEncode()}"
    }

private fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name()).replace("+", "%20")

private fun Double.toPlainCoordinate(): String =
    BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()
