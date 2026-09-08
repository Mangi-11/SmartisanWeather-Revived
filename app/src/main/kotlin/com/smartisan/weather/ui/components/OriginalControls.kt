package com.smartisan.weather.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartisan.weather.R
import kotlin.math.roundToInt

@Composable
internal fun OriginalIconButton(resource: Int, description: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectWeatherPressedAsState()
    WeatherDrawable(resource, description, modifier.clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
        enabled = enabled, pressed = pressed, contentScale = ContentScale.Fit)
}

@Composable
internal fun OriginalTextButton(text: String, background: Int, modifier: Modifier = Modifier, color: Color = colorResource(R.color.search_error_button_text_color), onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectWeatherPressedAsState()
    Box(modifier.weatherDrawableBackground(background, pressed = pressed).clickable(interactionSource = interaction, indication = null, onClick = onClick), contentAlignment = Alignment.Center) {
        PixelText(text, 13.5.dp, color, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
internal fun PixelText(text: String, size: Dp, color: Color, modifier: Modifier = Modifier, fontWeight: FontWeight = FontWeight.Normal, maxLines: Int = Int.MAX_VALUE) =
    PixelText(AnnotatedString(text), size, color, modifier, fontWeight, maxLines)

/** XML TextView rounds textSize to physical pixels; retain that rounding and its dp font scale. */
@Composable
internal fun PixelText(text: AnnotatedString, size: Dp, color: Color, modifier: Modifier = Modifier, fontWeight: FontWeight = FontWeight.Normal, maxLines: Int = Int.MAX_VALUE) {
    BasicText(text, modifier, style = TextStyle(color = color, fontSize = with(LocalDensity.current) { size.toPx().roundToInt().toSp() }, fontWeight = fontWeight,
        platformStyle = PlatformTextStyle(includeFontPadding = true)), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}
