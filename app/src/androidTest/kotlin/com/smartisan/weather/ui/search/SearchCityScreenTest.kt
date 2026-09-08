package com.smartisan.weather.ui.search

import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.smartisan.weather.R
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.smartisan.weather.data.model.SearchResultCity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchCityScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun typingSwitchesFromHotCitiesToExactResultsAndAddedCityCannotBeAddedAgain() {
        val state = mutableStateOf(SearchUiState())
        var selected = ""
        var alreadyAdded = 0
        compose.setContent {
            SearchCityScreen(state.value,
                onQueryChange = { state.value = state.value.copy(query = it, results = listOf(SearchResultCity(cityId = "accu:1", county = "London", country = "UK")), addedKeys = setOf("accu:1")) },
                onCancel = {}, onRetry = {}, onCityClick = { selected = it.cityId }, onHotCityClick = {},
                onLocationClick = {}, onAlreadyAdded = { alreadyAdded++ }, requestKeyboard = false)
        }
        compose.onNodeWithTag("search_query").performTextInput("London")
        compose.onNodeWithTag("search_results").assertExists()
        compose.onNodeWithTag("search_city_accu:1").performClick()
        compose.runOnIdle { assertEquals("", selected); assertEquals(1, alreadyAdded) }
    }

    @Test
    fun retryDoesNotRequireAnotherQueryChange() {
        var retries = 0
        compose.setContent {
            SearchCityScreen(SearchUiState(query = "London", isError = true),
                onQueryChange = {}, onCancel = {}, onRetry = { retries++ }, onCityClick = {}, onHotCityClick = {},
                onLocationClick = {}, onAlreadyAdded = {}, requestKeyboard = false)
        }
        compose.onNodeWithTag("search_results").assertDoesNotExist()
        compose.onNodeWithText(ApplicationProvider.getApplicationContext<Context>().getString(R.string.weather_search_refresh)).performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }
}
