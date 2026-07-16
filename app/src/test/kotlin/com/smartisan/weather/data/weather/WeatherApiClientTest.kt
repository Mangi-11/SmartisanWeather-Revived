package com.smartisan.weather.data.weather

import com.smartisan.weather.data.model.Observe
import com.smartisan.weather.data.model.Weather
import com.smartisan.weather.util.WeatherCodeMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherApiClientTest {

    private val client = WeatherApiClient.getInstance()

    @Test
    fun `request URLs match Xiaomi China protocol`() {
        assertEquals(
            "https://weatherapi.market.xiaomi.com/wtr-v3/weather/all" +
                "?latitude=0&longitude=0&locationKey=weathercn%3A101010100&days=15" +
                "&appKey=weather20151024&sign=zUFJoAR2ZVrDy1vF3D07" +
                "&isGlobal=false&locale=zh_cn",
            client.buildWeatherUrl("101010100"),
        )
        assertEquals(
            "https://weatherapi.market.xiaomi.com/wtr-v3/location/city/search" +
                "?name=%E5%8C%97%E4%BA%AC&locale=zh_cn",
            client.buildSearchUrl("北京"),
        )
        assertEquals(
            "https://weatherapi.market.xiaomi.com/wtr-v3/location/city/geo" +
                "?latitude=39.904&longitude=116.408&locale=zh_cn",
            client.buildGeoUrl(39.904, 116.408),
        )
    }

    @Test
    fun `global request uses accu key and global protocol flag`() {
        assertEquals(
            "https://weatherapi.market.xiaomi.com/wtr-v3/weather/all" +
                "?latitude=0&longitude=0&locationKey=accu%3A328328&days=15" +
                "&appKey=weather20151024&sign=zUFJoAR2ZVrDy1vF3D07" +
                "&isGlobal=true&locale=zh_cn",
            client.buildWeatherUrl("accu:328328"),
        )
        assertEquals(
            client.buildWeatherUrl("101010100"),
            client.buildWeatherUrl("weathercn:101010100"),
        )
    }

    @Test
    fun `parseWeather maps complete Xiaomi mixed-source response`() {
        val weather = client.parseWeather(loadFixture())

        assertNotNull(weather)
        weather!!
        assertTrue(weather.isComplete)
        assertEquals("小米天气、彩云天气、北京气象局、中国环境监测总站", weather.source)
        assertEquals(8 * 60 * 60, weather.timezoneOffsetSeconds)
        assertEquals("25", weather.observe.tempC)
        assertEquals("77", weather.observe.tempF)
        assertEquals("28", weather.observe.bodyFeelC)
        assertEquals("82", weather.observe.bodyFeelF)
        assertEquals("18", weather.observe.code)
        assertEquals("1", weather.observe.wind)
        assertEquals("1", weather.observe.speed)
        assertEquals("97", weather.observe.humidity)
        assertEquals("UNKNOWN", weather.observe.compareC)
        assertEquals("1785511500000", weather.observe.pubdate)
        assertEquals("45", weather.observe.aqi)
        assertEquals("30", weather.observe.pm25)
        assertEquals("pm25", weather.observe.primary)
        assertEquals("2", weather.allergy.uvLevel)
        assertEquals("", weather.allergy.agLevel)

        assertEquals(3, weather.dailyForecast.size)
        assertEquals("202607310000", weather.dailyForecast[0].date)
        assertEquals("周五", weather.dailyForecast[0].weekDay)
        assertEquals("08", weather.dailyForecast[0].weatherCodeAm)
        assertEquals("09", weather.dailyForecast[0].weatherCodePm)
        assertEquals(28, weather.dailyForecast[0].highTempC)
        assertEquals(18, weather.dailyForecast[0].lowTempC)
        assertEquals("05:10|19:30", weather.dailyForecast[0].sunriseAndSunset)
        assertEquals("202608010000", weather.dailyForecast[1].date)
        assertEquals("", weather.dailyForecast[2].sunriseAndSunset)

        assertEquals(3, weather.hourForecast.size)
        assertEquals(
            listOf("202607312300", "202608010000", "202608010100"),
            weather.hourForecast.map { it.startTime },
        )
        assertEquals(listOf("18", "01", "03"), weather.hourForecast.map { it.weatherCode })
        assertTrue(weather.hourForecast.all { it.night })
        assertEquals(-1, weather.hourForecast[2].tempC)
        assertEquals(30, weather.hourForecast[2].tempF)

        assertEquals("44", weather.airQuality.pm10)
        assertEquals("0.80", weather.airQuality.co)
        assertEquals(1, weather.alert.infos.size)
        assertEquals("暴雨", weather.alert.infos.single().type)
        assertEquals("03", weather.alert.infos.single().levelNumber)
        assertEquals(1_487_575_500_000L, weather.alert.infos.single().publishTime)
    }

    @Test
    fun `parseWeather maps global AccuWeather response without inventing AQI`() {
        val weather = client.parseWeather(loadGlobalFixture())

        assertNotNull(weather)
        weather!!
        assertTrue(weather.isComplete)
        assertEquals("小米天气、Accu Weather", weather.source)
        assertEquals(60 * 60, weather.timezoneOffsetSeconds)
        assertEquals(
            "https://www.accuweather.com/zh/gb/london/weather-forecast/328328",
            weather.attributionUrl,
        )
        assertEquals("", weather.airQuality.aqiValue)
        assertEquals("", weather.observe.aqi)
        assertEquals("5", weather.allergy.uvLevel)
        assertEquals(2, weather.dailyForecast.size)
        assertEquals(2, weather.hourForecast.size)
        assertTrue(weather.hourForecast.all { it.night })
    }

    @Test
    fun `missing current temperature never becomes a complete weather result`() {
        val weather = client.parseWeather(
            """
                {
                  "current": {
                    "temperature": {"value": null},
                    "feelsLike": {"value": null},
                    "weather": 99,
                    "pubTime": "2026-07-31T23:25:00+08:00"
                  }
                }
            """.trimIndent(),
        )

        assertNotNull(weather)
        assertFalse(weather!!.isComplete)
        assertEquals("UNKNOWN", weather.observe.tempC)
        assertEquals("UNKNOWN", weather.observe.tempF)
        assertEquals("99", weather.observe.code)
    }

    @Test
    fun `alerts keep original newest-first order and map level colors`() {
        val weather = client.parseWeather(
            """
                {
                  "current": {"temperature":{"value":20}, "weather":0},
                  "alerts": [
                    {"pubTime":"2026-07-31T10:00:00+08:00", "type":"雷电", "level":"黄色", "detail":"较早"},
                    {"pubTime":"2026-07-31T12:00:00+08:00", "type":"暴雨", "level":"红色", "detail":"最新"}
                  ]
                }
            """.trimIndent(),
        )

        assertEquals(listOf("最新", "较早"), weather!!.alert.infos.map { it.content })
        assertEquals(listOf("04", "02"), weather.alert.infos.map { it.levelNumber })
    }

    @Test
    fun `Xiaomi weather codes preserve China weather semantics`() {
        for (code in 0..19) {
            assertEquals(
                code.toString().padStart(2, '0'),
                XiaomiWeatherParser.toSmartisanWeatherCode(code.toString()),
            )
        }
        for (code in 21..31) {
            assertEquals(
                code.toString(),
                XiaomiWeatherParser.toSmartisanWeatherCode(code.toString()),
            )
        }
        assertEquals("31", XiaomiWeatherParser.toSmartisanWeatherCode("20"))
        assertEquals("03", XiaomiWeatherParser.toSmartisanWeatherCode("03"))
        assertEquals("18", XiaomiWeatherParser.toSmartisanWeatherCode("18"))
        assertEquals("18", XiaomiWeatherParser.toSmartisanWeatherCode("35"))
        assertEquals("53", XiaomiWeatherParser.toSmartisanWeatherCode("53"))
        assertEquals("99", XiaomiWeatherParser.toSmartisanWeatherCode("32"))
        assertEquals("99", XiaomiWeatherParser.toSmartisanWeatherCode("invalid"))
        assertEquals("99", XiaomiWeatherParser.toSmartisanWeatherCode(null))
    }

    @Test
    fun `wind direction uses eight sectors with exact boundaries`() {
        val cases = mapOf(
            "0" to "8",
            "22.499" to "8",
            "22.5" to "1",
            "67.499" to "1",
            "67.5" to "2",
            "112.5" to "3",
            "157.5" to "4",
            "202.5" to "5",
            "247.5" to "6",
            "292.5" to "7",
            "337.5" to "8",
            "360" to "8",
        )
        cases.forEach { (degrees, expected) ->
            assertEquals(expected, XiaomiWeatherParser.windDirectionCode(degrees))
        }
        assertEquals("0", XiaomiWeatherParser.windDirectionCode("-1"))
        assertEquals("0", XiaomiWeatherParser.windDirectionCode("not-a-number"))
    }

    @Test
    fun `wind speed converts kilometers per hour to Beaufort level`() {
        val cases = mapOf(
            "0.9" to "0",
            "1" to "1",
            "5.999" to "1",
            "6" to "2",
            "11.999" to "2",
            "12" to "3",
            "28.999" to "4",
            "29" to "5",
            "117.999" to "11",
            "118" to "12",
        )
        cases.forEach { (speed, expected) ->
            assertEquals(expected, XiaomiWeatherParser.beaufortLevel(speed))
        }
        assertEquals("", XiaomiWeatherParser.beaufortLevel("-0.1"))
        assertEquals("", XiaomiWeatherParser.beaufortLevel(""))
    }

    @Test
    fun `parseSearchResults maps China and global location keys`() {
        val json = """
            [
              {"name":"北京市","affiliation":"中国","locationKey":"weathercn:101010100","status":0},
              {"name":"海淀区","affiliation":"北京市, 中国","locationKey":"weathercn:101010200","status":0},
              {"name":"广州市","affiliation":"广东, 中国","locationKey":"weathercn:101280101","status":0},
              {"name":"番禺区","affiliation":"广州市, 广东, 中国","locationKey":"weathercn:101280102","status":0},
              {"name":"苏州市","affiliation":"江苏, 中国","key":"weathercn:101190401","status":0},
              {"name":"香港特别行政区","affiliation":"中国","locationKey":"accu:1123655","status":0},
              {"name":"London","affiliation":"Ontario, Canada","locationKey":"accu:55489","status":0},
              {"name":"未知来源","affiliation":"未知","locationKey":"other:123","status":0},
              {"name":"损坏标识","affiliation":"未知","locationKey":"accu:bad/value","status":0},
              {"name":"失效城市","affiliation":"中国","locationKey":"weathercn:101000000","status":1}
            ]
        """.trimIndent()

        val results = client.parseSearchResults(json)

        assertEquals(7, results.size)
        assertEquals(
            listOf("北京", "北京", "北京", "中国"),
            results[0].let { listOf(it.county, it.city, it.province, it.country) },
        )
        assertEquals(
            listOf("海淀", "北京", "北京", "中国"),
            results[1].let { listOf(it.county, it.city, it.province, it.country) },
        )
        assertEquals(
            listOf("广州", "广州", "广东", "中国"),
            results[2].let { listOf(it.county, it.city, it.province, it.country) },
        )
        assertEquals(
            listOf("番禺", "广州", "广东", "中国"),
            results[3].let { listOf(it.county, it.city, it.province, it.country) },
        )
        assertEquals("101190401", results[4].cityId)
        assertEquals("101190401", results[4].id)
        assertEquals("accu:1123655", results[5].cityId)
        assertTrue(results[5].isGlobal)
        assertEquals("中国", results[5].searchContext)
        assertEquals("中国", results[5].displayParentName)
        assertEquals("accu:55489", results[6].cityId)
        assertEquals("Ontario, Canada", results[6].searchContext)
        assertEquals("Ontario", results[6].displayParentName)
    }

    @Test
    fun `cache roundtrip accepts Xiaomi cache and rejects obsolete provider cache`() {
        val weather = Weather(
            source = "小米天气",
            observe = Observe(tempC = "25", tempF = "77", code = "00", humidity = "60"),
            timezoneOffsetSeconds = 3_600,
            attributionUrl = "https://example.com/weather",
        )

        val json = WeatherRepository.weatherToJson(weather)
        val parsed = WeatherRepository.jsonToWeather(json)

        assertNotNull(parsed)
        assertEquals("小米天气", parsed!!.source)
        assertEquals("25", parsed.observe.tempC)
        assertEquals(3_600, parsed.timezoneOffsetSeconds)
        assertEquals("https://example.com/weather", parsed.attributionUrl)
        assertNull(
            WeatherRepository.jsonToWeather(
                """{"observe":{"tempC":"99","tempF":"210"}}""",
            ),
        )
    }

    @Test
    fun `existing temperature AQI theme and icon mappings remain compatible`() {
        assertEquals(32, WeatherCodeMapping.celsiusToFahrenheit(0))
        assertEquals(77, WeatherCodeMapping.celsiusToFahrenheit(25))
        assertEquals(0, WeatherCodeMapping.AqiLevel.getLevel(30))
        assertEquals(5, WeatherCodeMapping.AqiLevel.getLevel(350))
        assertEquals(
            WeatherCodeMapping.WeatherTheme.SUNNY,
            WeatherCodeMapping.getTheme("00"),
        )
        assertTrue(WeatherCodeMapping.getIcon("08") > 0)
        assertTrue(WeatherCodeMapping.getIcon("99") > 0)
    }

    private fun loadFixture(): String {
        return requireNotNull(javaClass.classLoader?.getResource("xiaomi_weather_response.json"))
            .readText()
    }

    private fun loadGlobalFixture(): String {
        return requireNotNull(
            javaClass.classLoader?.getResource("xiaomi_global_weather_response.json"),
        ).readText()
    }
}
