package com.smartisan.weather.data.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherApiClientTest {

    private val client = WeatherApiClient.getInstance()

    @Test
    fun `parseWeather parses complete weather JSON`() {
        val json = """
        {
            "code": 0,
            "data": {
                "source": "华风新天",
                "local_time": "1718870400",
                "observe": {
                    "info": {
                        "temp": "25",
                        "code": "00",
                        "wind": "4",
                        "speed": "3",
                        "humidity": "60",
                        "body_feel": "27",
                        "compare": "2"
                    },
                    "publish_time": "202406201200"
                },
                "forecast": {
                    "info": [
                        {"date":"2024-06-20","low":"18","high":"28","code1":"00","sun":"06:00|19:00"},
                        {"date":"2024-06-21","low":"19","high":"30","code1":"01","sun":"06:00|19:00"}
                    ]
                },
                "air": {
                    "info": {
                        "aqi":"45",
                        "pm2_5":"20",
                        "pm10":"35",
                        "o3":"80",
                        "no2":"15",
                        "so2":"5",
                        "co":"0.5",
                        "publish_time":"2024-06-20 12:00:00"
                    }
                },
                "forecast_hour": {
                    "info": [
                        {"code":"00","weatherCode":"00","temp":"25","tempC":25,"tempF":77,"startTime":"202406201200","night":false,"sunDes":""},
                        {"code":"01","weatherCode":"01","temp":"24","tempC":24,"tempF":75,"startTime":"202406201300","night":false,"sunDes":""}
                    ]
                },
                "alert": {
                    "info": [
                        {"type_number":"01","level":"蓝色","content":"大风蓝色预警","level_number":"1","publish_time":"2024-06-20 10:00:00","type":"大风"}
                    ]
                },
                "allergy": {
                    "info": [
                        {"type":"uv","content":"紫外线中等","type_cn":"紫外线","level":"3"},
                        {"type":"ag","content":"过敏指数低","type_cn":"过敏","level":"1"}
                    ]
                }
            }
        }
        """.trimIndent()

        val weather = client.parseWeather(json)
        assertNotNull(weather)
        assertTrue(weather!!.isComplete)
        assertEquals("华风新天", weather.source)
        assertEquals("00", weather.observe.code)
        assertEquals("25", weather.observe.tempC)
        assertEquals("60", weather.observe.humidity)
        assertEquals(2, weather.dailyForecast.size)
        assertEquals(28, weather.dailyForecast[0].highTempC)
        assertEquals(18, weather.dailyForecast[0].lowTempC)
        assertEquals(2, weather.hourForecast.size)
        assertEquals(25, weather.hourForecast[0].tempC)
        assertEquals(77, weather.hourForecast[0].tempF)
        assertEquals(1, weather.alert.infos.size)
        assertEquals("大风", weather.alert.infos[0].type)
        assertEquals("蓝色", weather.alert.infos[0].level)
        assertEquals("紫外线中等", weather.allergy.uvContent)
        assertEquals("过敏指数低", weather.allergy.agContent)
        assertEquals("45", weather.airQuality.aqiValue)
        assertEquals("20", weather.airQuality.pm25)
    }

    @Test
    fun `weather signature preserves original parameter order`() {
        assertEquals(
            "d87d062a5d60fce548dfa85fda0e6a80",
            client.weatherSignature("101010100", "1234567890"),
        )
    }

    @Test
    fun `Chinese search signature uses original UTF-8 low-byte algorithm`() {
        assertEquals(
            "ffbcc204e1ce61104e68119521d795e8",
            client.searchSignature("北京", 1),
        )
    }

    @Test
    fun `parseSearchResults parses city search response`() {
        val json = """
        {
            "code": 0,
            "data": {
                "content": [
                    {"cityId":"101010100","county":"北京","city":"北京","province":"北京","country":"中国","countyEn":"beijing","countyPinyin":"beijing","id":"1"},
                    {"cityId":"101020100","county":"上海","city":"上海","province":"上海","country":"中国","countyEn":"shanghai","countyPinyin":"shanghai","id":"2"}
                ]
            }
        }
        """.trimIndent()

        val results = client.parseSearchResults(json)
        assertEquals(2, results.size)
        assertEquals("101010100", results[0].cityId)
        assertEquals("北京", results[0].county)
        assertEquals("中国", results[0].country)
    }

    @Test
    fun `request signatures preserve parameter order and byte contract`() {
        assertEquals(
            "ffbcc204e1ce61104e68119521d795e8",
            client.searchSignature("北京", 1),
        )
        assertEquals(
            "f7ed9959fc1a8f16dbd7bc6c5b93f3e4",
            client.searchSignature("上海", 2),
        )
        assertEquals(
            "ffff9aeb48cad396429f0133c3cf19d2",
            client.weatherSignature("101010100", "1700000000000"),
        )
    }

    @Test
    fun `parseWeather supports production snake case hourly fields`() {
        val json = """
            {
              "code": 0,
              "data": {
                "local_time": 1783651244,
                "observe": {
                  "info": {"temp":27,"code":"08","speed":0,"humidity":78,"wind":3,"compare":0},
                  "publish_time":"20260710104044"
                },
                "forecast": {
                  "info": [
                    {"code1":"08","code2":"09","sun":"04:56|19:43","date":"2026-07-10","low":24,"high":31}
                  ]
                },
                "forecast_hour": {
                  "info": [
                    {"temp":27,"f_start_time":"20260710100000","start_time":"20260710100000","code":"01"}
                  ]
                },
                "air": {"info":{"aqi":46,"primary":0,"pm2_5":9}}
              }
            }
        """.trimIndent()

        val weather = client.parseWeather(json)

        assertNotNull(weather)
        assertEquals("09", weather!!.dailyForecast.single().weatherCodePm)
        assertEquals("01", weather.hourForecast.single().weatherCode)
        assertEquals(27, weather.hourForecast.single().tempC)
        assertEquals(80, weather.hourForecast.single().tempF)
        assertEquals("20260710100000", weather.hourForecast.single().startTime)
        assertEquals("0", weather.airQuality.primary)
    }

    @Test
    fun `daily forecast filtering uses response date across month boundary`() {
        val json = """
            {
              "code": 0,
              "data": {
                "local_time": 1772294400,
                "forecast": {
                  "info": [
                    {"date":"2026-02-28","low":1,"high":8,"code1":"00"},
                    {"date":"2026-03-01","low":2,"high":9,"code1":"01"},
                    {"date":"2026-03-02","low":3,"high":10,"code1":"02"}
                  ]
                }
              }
            }
        """.trimIndent()

        val weather = client.parseWeather(json)

        assertNotNull(weather)
        assertEquals(
            listOf("202603010000", "202603020000"),
            weather!!.dailyForecast.map { it.date },
        )
    }

    @Test
    fun `Celsius to Fahrenheit conversion`() {
        assertEquals(32, com.smartisan.weather.util.WeatherCodeMapping.celsiusToFahrenheit(0))
        assertEquals(212, com.smartisan.weather.util.WeatherCodeMapping.celsiusToFahrenheit(100))
        assertEquals(77, com.smartisan.weather.util.WeatherCodeMapping.celsiusToFahrenheit(25))
    }

    @Test
    fun `AQI level mapping`() {
        assertEquals(0, com.smartisan.weather.util.WeatherCodeMapping.AqiLevel.getLevel(30))
        assertEquals(1, com.smartisan.weather.util.WeatherCodeMapping.AqiLevel.getLevel(75))
        assertEquals(2, com.smartisan.weather.util.WeatherCodeMapping.AqiLevel.getLevel(120))
        assertEquals(3, com.smartisan.weather.util.WeatherCodeMapping.AqiLevel.getLevel(180))
        assertEquals(4, com.smartisan.weather.util.WeatherCodeMapping.AqiLevel.getLevel(250))
        assertEquals(5, com.smartisan.weather.util.WeatherCodeMapping.AqiLevel.getLevel(350))
    }

    @Test
    fun `weather code to theme mapping`() {
        val sunny = com.smartisan.weather.util.WeatherCodeMapping.getTheme("00")
        assertEquals(com.smartisan.weather.util.WeatherCodeMapping.WeatherTheme.SUNNY, sunny)

        val rain = com.smartisan.weather.util.WeatherCodeMapping.getTheme("08")
        assertEquals(com.smartisan.weather.util.WeatherCodeMapping.WeatherTheme.RAIN, rain)

        val snow = com.smartisan.weather.util.WeatherCodeMapping.getTheme("15")
        assertEquals(com.smartisan.weather.util.WeatherCodeMapping.WeatherTheme.SNOW, snow)

        val default = com.smartisan.weather.util.WeatherCodeMapping.getTheme(null)
        assertEquals(com.smartisan.weather.util.WeatherCodeMapping.WeatherTheme.DEFAULT, default)
    }

    @Test
    fun `weather code to icon mapping`() {
        val sunnyIcon = com.smartisan.weather.util.WeatherCodeMapping.getIcon("00")
        assertTrue(sunnyIcon > 0)

        val nightIcon = com.smartisan.weather.util.WeatherCodeMapping.getIcon("00", isNight = true)
        assertTrue(nightIcon > 0)

        val unknownIcon = com.smartisan.weather.util.WeatherCodeMapping.getIcon("99")
        assertTrue(unknownIcon > 0)
    }

    @Test
    fun `WeatherRepository json serialization roundtrip`() {
        val weather = com.smartisan.weather.data.model.Weather(
            source = "test",
            observe = com.smartisan.weather.data.model.Observe(
                tempC = "25",
                tempF = "77",
                code = "00",
                humidity = "60",
            ),
        )
        val json = WeatherRepository.weatherToJson(weather)
        val parsed = WeatherRepository.jsonToWeather(json)
        assertNotNull(parsed)
        assertEquals("test", parsed!!.source)
        assertEquals("25", parsed.observe.tempC)
        assertEquals("77", parsed.observe.tempF)
        assertEquals("00", parsed.observe.code)
        assertEquals("60", parsed.observe.humidity)
    }
}
