package com.smartisan.weather.ui.main

import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartisan.weather.R
import com.smartisan.weather.data.model.Observe
import com.smartisan.weather.data.model.HourForecast
import com.smartisan.weather.data.model.SavedCity
import com.smartisan.weather.data.model.Weather
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun citySwipeCommitsOnceAndCannotPassTheLastCity() {
        var state by mutableStateOf(sampleState())
        val selections = mutableListOf<Int>()
        compose.setContent {
            WeatherScreen(state, { selections += it; state = state.copy(currentIndex = it) }, {}, {}, {}, {}, { _, _ -> }, {}, {})
        }
        compose.onNodeWithTag("weather_city_pager").performTouchInput { swipeLeft() }
        compose.onNodeWithText("上海").assertIsDisplayed()
        compose.onNodeWithTag("weather_city_pager").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(listOf(1), selections) }
        compose.onNodeWithTag("weather_city_pager").performTouchInput { swipeRight() }
        compose.onNodeWithText("北京").assertIsDisplayed()
    }

    @Test
    fun cancelledCityDragRestoresTheSelectedCity() {
        var state by mutableStateOf(sampleState())
        compose.setContent { WeatherScreen(state, { state = state.copy(currentIndex = it) }, {}, {}, {}, {}, { _, _ -> }, {}, {}) }
        compose.onNodeWithTag("weather_city_pager").performTouchInput {
            down(Offset(width * 0.8f, height * 0.3f))
            moveBy(Offset(-width * 0.5f, 0f))
            cancel()
        }
        compose.onNodeWithText("北京").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, state.currentIndex) }
    }

    @Test
    fun hourlyEdgeHandsPagingBackButCancelledDragKeepsCity() {
        var state by mutableStateOf(sampleState().copy(currentIndex = 1))
        compose.setContent { WeatherScreen(state, { state = state.copy(currentIndex = it) }, {}, {}, {}, {}, { _, _ -> }, {}, {}) }
        compose.onNodeWithTag("weather_hourly_forecast").performTouchInput {
            down(center)
            moveBy(Offset(width * 0.4f, 0f))
            cancel()
        }
        compose.onNodeWithText("上海").assertIsDisplayed()
        compose.onNodeWithTag("weather_hourly_forecast").performTouchInput { swipeRight() }
        compose.onNodeWithText("北京").assertIsDisplayed()
        compose.onNodeWithTag("weather_hourly_forecast").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(0, state.currentIndex) }
        compose.runOnIdle { state = state.copy(loadVersions = mapOf("beijing" to 1L)) }
        compose.onNodeWithText("00:00").assertIsDisplayed()
    }

    @Test
    fun unitChangeAndRefreshInterruptionSettleOnLatestTemperature() {
        var state by mutableStateOf(sampleState())
        compose.setContent {
            WeatherScreen(state, {}, {}, { state = state.copy(isCelsius = !state.isCelsius) }, {}, {}, { _, _ -> }, {}, {})
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("weather_temperature_unit").performClick()
        compose.mainClock.advanceTimeBy(250)
        compose.onNodeWithTag("weather_temperature_unit").performClick()
        compose.runOnIdle { state = state.copy(loadVersions = mapOf("beijing" to 1L)) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("weather_main_temperature").assertIsDisplayed()
            .assertContentDescriptionEquals(compose.activity.getString(R.string.weather_c_icon_description, 18))
        compose.runOnIdle { assertEquals(true, state.isCelsius) }
    }

    @Test
    fun stoppingDuringUnitAnimationResumesWithCompleteLatestValue() {
        var state by mutableStateOf(sampleState())
        compose.setContent {
            WeatherScreen(state, {}, {}, { state = state.copy(isCelsius = !state.isCelsius) }, {}, {}, { _, _ -> }, {}, {})
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("weather_temperature_unit").performClick()
        compose.mainClock.advanceTimeBy(250)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("weather_main_temperature").assertIsDisplayed()
            .assertContentDescriptionEquals(compose.activity.getString(R.string.weather_f_icon_description, 64))
    }

    private fun sampleState(): WeatherUiState {
        val cities = listOf(SavedCity(locationKey = "beijing", locationName = "北京"), SavedCity(locationKey = "shanghai", locationName = "上海"))
        return WeatherUiState(citiesLoaded = true, cities = cities, weathers = cities.associate { city ->
            city.locationKey to Weather(observe = Observe(tempC = "18", tempF = "64", code = "07"), hourForecast = List(24) { hour ->
                HourForecast(startTime = "20260908" + "%02d00".format(hour), tempC = 18, tempF = 64, weatherCode = "07")
            })
        })
    }
}
