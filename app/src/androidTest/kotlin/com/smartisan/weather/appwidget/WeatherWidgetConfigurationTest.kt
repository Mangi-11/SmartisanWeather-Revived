package com.smartisan.weather.appwidget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.smartisan.weather.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WeatherWidgetConfigurationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun selectionChangesOnlyWhenChosenAndSaveUsesTheLatestChoice() {
        var saved: String? = null
        compose.setContent {
            var selected by remember { mutableStateOf("") }
            WeatherWidgetConfigurationScreen(
                choices = listOf("" to "自动选择", "101010100" to "北京", "101020100" to "上海"),
                selectedKey = selected, setupComplete = true, hasCities = true, ready = true,
                onSelect = { selected = it }, onCancel = {}, onDone = { saved = selected },
            )
        }
        compose.onNodeWithText("上海").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(null, saved) }
        val done = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.complete)
        compose.onNodeWithText(done).performClick()
        compose.runOnIdle { assertEquals("101020100", saved) }
    }

    @Test
    fun configurationCannotBeCommittedUntilTheSettingsHaveLoaded() {
        compose.setContent {
            WeatherWidgetConfigurationScreen(emptyList(), "", false, false, false, {}, {}, {})
        }
        val done = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.complete)
        compose.onNodeWithText(done).assertIsNotEnabled()
    }
}
