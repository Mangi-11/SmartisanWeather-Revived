package com.smartisan.weather.ui.components

import android.content.Context
import android.content.res.Configuration
import android.view.ViewConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.smartisan.weather.R

@RunWith(AndroidJUnit4::class)
class WeatherComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sameFrameTapDrawsPressedSelectorBeforeReleaseClearsIt() {
        val source = MutableInteractionSource()
        lateinit var pressed: State<Boolean>
        var pressedColor = 0
        compose.setContent {
            pressed = source.collectWeatherPressedAsState()
            val color = colorResource(R.color.app_surface_pressed_color)
            SideEffect { pressedColor = color.toArgb() }
            Box(
                Modifier.size(100.dp).testTag("selector")
                    .background(Color.White)
                    .weatherDrawableBackground(R.drawable.privacy_dialog_button_left_bg, pressed = pressed.value),
            )
        }
        compose.mainClock.autoAdvance = false
        val press = PressInteraction.Press(Offset.Zero)
        compose.runOnIdle {
            assertTrue(source.tryEmit(press))
            assertTrue(source.tryEmit(PressInteraction.Release(press)))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertTrue(pressed.value) }
        assertEquals(pressedColor, centerPixel(compose.onNodeWithTag("selector").captureToImage()))
        compose.mainClock.advanceTimeBy(ViewConfiguration.getPressedStateDuration() + 48L)
        compose.runOnIdle { assertFalse(pressed.value) }
        assertEquals(Color.White.toArgb(), centerPixel(compose.onNodeWithTag("selector").captureToImage()))
    }

    @Test
    fun scrollCancellationClearsImmediatelyAndPriorReleaseCannotClearANewPress() {
        val source = MutableInteractionSource()
        lateinit var pressed: State<Boolean>
        compose.setContent {
            pressed = source.collectWeatherPressedAsState()
            Box(
                Modifier.size(100.dp).testTag("selector")
                    .background(Color.White)
                    .weatherDrawableBackground(R.drawable.privacy_dialog_button_left_bg, pressed = pressed.value),
            )
        }
        compose.mainClock.autoAdvance = false
        val first = PressInteraction.Press(Offset.Zero)
        val second = PressInteraction.Press(Offset.Zero)
        compose.runOnIdle {
            source.tryEmit(first)
            source.tryEmit(PressInteraction.Release(first))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { source.tryEmit(second) }
        compose.mainClock.advanceTimeBy(ViewConfiguration.getPressedStateDuration() + 48L)
        compose.runOnIdle {
            assertTrue(pressed.value)
            source.tryEmit(PressInteraction.Cancel(second))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertFalse(pressed.value) }
        assertEquals(Color.White.toArgb(), centerPixel(compose.onNodeWithTag("selector").captureToImage()))
    }

    @Test
    fun disabledButtonIgnoresTouch() {
        var clicks = 0
        compose.setContent {
            WeatherButton(
                text = "完成",
                onClick = { clicks++ },
                enabled = false,
                modifier = Modifier.testTag("disabled_button"),
            )
        }
        compose.onNodeWithTag("disabled_button").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, clicks) }
    }

    @Test
    fun nightChangeReloadsSelectorAndRendersItsTextColor() {
        var night by mutableStateOf(false)
        var pressed by mutableStateOf(true)
        lateinit var themedContext: Context
        compose.setContent {
            val baseContext = LocalContext.current
            val baseConfiguration = LocalConfiguration.current
            val context = remember(baseContext, baseConfiguration, night) {
                baseContext.createConfigurationContext(
                    Configuration(baseConfiguration).apply {
                        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                            if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                    },
                )
            }
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides context.resources.configuration,
                LocalResources provides context.resources,
            ) {
                SideEffect { themedContext = context }
                Column {
                    Box(
                        Modifier.size(100.dp).testTag("selector")
                            .background(colorResource(R.color.app_surface_color))
                            .weatherDrawableBackground(R.drawable.privacy_dialog_button_left_bg, pressed = pressed),
                    )
                    WeatherText(
                        text = "MMMM",
                        color = weatherStateColor(R.color.privacy_dialog_positive_text, pressed = pressed),
                        fontSize = 22.sp,
                        modifier = Modifier.testTag("state_text"),
                    )
                }
            }
        }

        fun assertCurrentPalette(isPressed: Boolean): Pair<Int, Int> {
            val context = compose.runOnIdle { themedContext }
            val expectedBackground = context.getColor(
                if (isPressed) R.color.app_surface_pressed_color else R.color.app_surface_color,
            )
            assertEquals(expectedBackground, centerPixel(compose.onNodeWithTag("selector").captureToImage()))
            val colors = context.getColorStateList(R.color.privacy_dialog_positive_text)
            val states = intArrayOf(
                android.R.attr.state_enabled,
                if (isPressed) android.R.attr.state_pressed else -android.R.attr.state_pressed,
            )
            val expectedText = colors.getColorForState(states, colors.defaultColor)
            val image = compose.onNodeWithTag("state_text").captureToImage()
            assertTrue("Glyphs must use the configured day/night selector color", pixels(image).any { it == expectedText })
            return expectedBackground to expectedText
        }

        val day = assertCurrentPalette(isPressed = true)
        compose.runOnIdle { night = true }
        val dark = assertCurrentPalette(isPressed = true)
        assertNotEquals(day.first, dark.first)
        assertNotEquals(day.second, dark.second)
        compose.runOnIdle { pressed = false }
        assertCurrentPalette(isPressed = false)
        compose.runOnIdle { night = false; pressed = true }
        assertEquals(day, assertCurrentPalette(isPressed = true))
    }

    @Test
    fun ninePatchBackgroundNeverExpandsAContentSizedColumn() {
        var parentHeight by mutableStateOf(220.dp)
        var density = 1f
        compose.setContent {
            val currentDensity = LocalDensity.current
            SideEffect { density = currentDensity.density }
            Box(Modifier.size(280.dp, parentHeight).testTag("parent")) {
                Column(
                    Modifier.fillMaxWidth().testTag("background")
                        .weatherDrawableBackground(R.drawable.dialog_full_smartisanos_light),
                ) {
                    Spacer(Modifier.height(31.dp))
                }
            }
        }
        listOf(220.dp, 400.dp).forEach { height ->
            compose.runOnIdle { parentHeight = height }
            val parent = compose.onNodeWithTag("parent").fetchSemanticsNode().boundsInRoot
            val background = compose.onNodeWithTag("background").fetchSemanticsNode().boundsInRoot
            assertEquals(parent.width, background.width, 1f)
            assertEquals(31f * density, background.height, 1f)
            assertEquals(parent.top, background.top, 1f)
            assertTrue(background.bottom < parent.bottom)
        }
    }

    private fun pixels(image: ImageBitmap): IntArray =
        IntArray(image.width * image.height).also { image.readPixels(it) }

    private fun centerPixel(image: ImageBitmap): Int =
        pixels(image)[image.width * (image.height / 2) + image.width / 2]
}
