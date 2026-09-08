package com.smartisan.weather.util

import com.smartisan.weather.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherResourceMappingTest {
    @Test
    fun `unsupported weather codes use the unknown icon without throwing`() {
        listOf(null, "", "unknown", "9999").forEach { code ->
            val icon = ResMappingUtil.getWeatherResId(code)
            assertEquals(R.drawable.little_icon_unknown, icon.getLittleIcon(false))
            assertEquals(R.drawable.little_icon_unknown_shadow, icon.getLittleIconShadow(true))
            assertTrue(ResMappingUtil.isUnknown(code))
        }
    }

    @Test
    fun `sun events keep their dedicated forecast icons`() {
        assertEquals(
            R.drawable.little_icon_sunrise_shadow,
            ResMappingUtil.getWeatherResId("1000").getLittleIconShadow(false),
        )
        assertEquals(
            R.drawable.little_icon_sunset_shadow,
            ResMappingUtil.getWeatherResId("1001").getLittleIconShadow(true),
        )
        assertFalse(ResMappingUtil.isUnknown("1000"))
        assertFalse(ResMappingUtil.isUnknown("1001"))
    }

    @Test
    fun `unsupported theme keys safely use the default background`() {
        listOf(null, "", "unknown", "9999").forEach { key ->
            assertEquals(R.drawable.drawable_weather_bg_sunny, ThemeUtils.getBgRes(key))
            assertEquals(R.drawable.bg_weather_info_error, ThemeUtils.getCurTheme(key).getInfoBgRes())
        }
    }

    @Test
    fun `querying other weather themes cannot change an existing page theme`() {
        val rain = ThemeUtils.getCurTheme("08")
        ThemeUtils.getCurTheme("00")
        ThemeUtils.getCurTheme(null)
        assertSame(rain, ThemeUtils.getCurTheme("10"))
        assertEquals(R.drawable.drawable_weather_bg_rain, rain.getBgRes())
        assertEquals(R.drawable.button_refresh_icon_rain, rain.getRefreshSrcRes())
        assertEquals(R.drawable.bg_weather_info_rain, rain.getInfoBgRes())
    }
}
