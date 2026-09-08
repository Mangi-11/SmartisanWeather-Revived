package com.smartisan.weather.util

import com.smartisan.weather.R
import org.junit.Assert.assertEquals
import org.junit.Test

class PollutantIndicatorTest {
    private val indicators = listOf(
        Utility::getCoGrade,
        Utility::getNo2Grade,
        Utility::getO3Grade,
        Utility::getPm10Grade,
        Utility::getPm2_5Grade,
        Utility::getSo2Grade,
    )

    @Test
    fun `decimal concentrations are not replaced with a zero grade`() {
        assertEquals(R.drawable.weather_air_good, Utility.getCoGrade("5.1"))
        assertEquals(R.drawable.weather_air_mild_polluted, Utility.getCoGrade("10.1"))
        assertEquals(R.drawable.weather_air_mild_polluted, Utility.getPm10Grade("150.1"))
        assertEquals(R.drawable.weather_air_severe_pollution_most, Utility.getO3Grade("800.1"))
    }

    @Test
    fun `missing and invalid concentrations do not manufacture a perfect indicator`() {
        listOf(null, "", "unknown", "-1", "NaN", "Infinity", "1e309").forEach { value ->
            indicators.forEach { indicator ->
                assertEquals(android.R.color.transparent, indicator(value))
            }
        }
    }

    @Test
    fun `real zero remains a valid concentration`() {
        indicators.forEach { indicator ->
            assertEquals(R.drawable.weather_air_perfect, indicator("0"))
        }
    }
}
