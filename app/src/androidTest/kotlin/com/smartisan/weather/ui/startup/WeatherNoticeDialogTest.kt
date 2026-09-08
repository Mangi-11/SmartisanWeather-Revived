package com.smartisan.weather.ui.startup

import com.smartisan.weather.ui.saveVerificationScreenshot
import android.content.Context
import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.smartisan.weather.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherNoticeDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun startupWaitsForExplicitConsentAndDispatchesItOnlyOnce() {
        var accepted = 0
        var rejected = 0
        compose.setContent {
            StartupNoticeDialog(onContinue = { accepted++ }, onExit = { rejected++ })
        }
        compose.onNodeWithTag("startup_notice_positive").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, accepted)
            assertEquals(0, rejected)
        }
        compose.onNodeWithTag("startup_notice_positive").performClick()
        compose.onNodeWithTag("startup_notice_positive").assertIsNotEnabled()
            .performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, accepted)
            assertEquals(0, rejected)
        }
    }

    @Test
    fun exitDoesNotGrantConsent() {
        var accepted = 0
        var rejected = 0
        compose.setContent {
            StartupNoticeDialog(onContinue = { accepted++ }, onExit = { rejected++ })
        }
        compose.onNodeWithTag("startup_notice_negative").performClick()
        compose.runOnIdle {
            assertEquals(0, accepted)
            assertEquals(1, rejected)
        }
    }

    @Test
    fun startupRetainsTheOriginalResourceEmphasis() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val message = context.resources.getText(R.string.weather_startup_notice_message) as Spanned
        compose.setContent { StartupNoticeDialog(onContinue = {}, onExit = {}) }
        compose.waitForIdle()
        saveVerificationScreenshot("startup-light")
        val rendered = compose.onNodeWithText(message.toString()).fetchSemanticsNode()
            .config[SemanticsProperties.Text].single()
        val boldSpans = message.getSpans(0, message.length, StyleSpan::class.java)
            .filter { it.style and Typeface.BOLD != 0 }
        assertTrue("The notice's original bold passages must remain present", boldSpans.isNotEmpty())
        boldSpans.forEach { expected ->
            assertTrue(
                rendered.spanStyles.any {
                    it.start == message.getSpanStart(expected) &&
                        it.end == message.getSpanEnd(expected) &&
                        it.item.fontWeight == FontWeight.Bold
                },
            )
        }
    }

    @Test
    fun systemBackExitsWithoutGrantingConsent() {
        var accepted = 0
        var rejected = 0
        compose.setContent {
            StartupNoticeDialog(onContinue = { accepted++ }, onExit = { rejected++ })
        }
        compose.onNodeWithTag("startup_notice").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.runOnIdle {
            assertEquals(0, accepted)
            assertEquals(1, rejected)
        }
    }

    @Test
    fun longNoticeKeepsBothActionsVisible() {
        var accepted = 0
        compose.setContent {
            WeatherNoticeDialog(
                title = "使用说明",
                message = AnnotatedString("请阅读本应用的数据来源与定位说明。\n".repeat(100)),
                positiveText = "继续使用",
                negativeText = "退出",
                onPositive = { accepted++ },
                onNegative = {},
                tagPrefix = "long_notice",
            )
        }
        compose.onNodeWithTag("long_notice_negative").assertIsDisplayed()
        compose.onNodeWithTag("long_notice_positive").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, accepted) }
    }

    @Test
    fun locationSettingsRequiresItsOwnConfirmation() {
        var settingsOpened = 0
        var cancelled = 0
        compose.setContent {
            WeatherLocationDialog(
                onCancel = { cancelled++ },
                onSettings = { settingsOpened++ },
            )
        }
        compose.onNodeWithTag("location_notice_positive").performClick()
        compose.runOnIdle {
            assertEquals(1, settingsOpened)
            assertEquals(0, cancelled)
        }
    }
}
