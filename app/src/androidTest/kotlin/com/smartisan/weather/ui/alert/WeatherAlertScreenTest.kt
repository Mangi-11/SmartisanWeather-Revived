package com.smartisan.weather.ui.alert

import com.smartisan.weather.ui.saveVerificationScreenshot
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartisan.weather.R
import com.smartisan.weather.data.model.AlertInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherAlertScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun allAlertBodiesRemainAvailableAfterScrolling() {
        val alerts = List(20) { index ->
            AlertInfo(
                typeNumber = "02",
                type = "暴雨",
                level = "黄色",
                levelNumber = "02",
                content = "第 $index 条预警的完整内容，注意防范强降雨引发的次生灾害。",
            )
        }
        compose.setContent { WeatherAlertScreen(alerts = alerts, onBack = {}) }
        compose.onNodeWithText(alerts.first().content).assertIsDisplayed()
        saveVerificationScreenshot("alerts-light")
        compose.onNodeWithTag("weather_alert_list").performScrollToIndex(alerts.lastIndex)
        compose.onNodeWithText(alerts.last().content).assertIsDisplayed()
        compose.onNodeWithTag("weather_alert_list").performScrollToIndex(0)
        compose.onNodeWithText(alerts.first().content).assertIsDisplayed()
    }

    @Test
    fun titleBackActionWorksWithEmptyAlerts() {
        var backs = 0
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent { WeatherAlertScreen(alerts = emptyList(), onBack = { backs++ }) }
        compose.onNodeWithContentDescription(context.getString(R.string.cancel)).performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }
}
