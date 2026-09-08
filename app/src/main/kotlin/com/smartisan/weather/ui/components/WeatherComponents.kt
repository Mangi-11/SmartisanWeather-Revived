package com.smartisan.weather.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.smartisan.weather.R

/** Full-window original background, safe interactive area, and the original phone canvas. */
@Composable
fun WeatherScreenFrame(
    modifier: Modifier = Modifier,
    @DrawableRes backgroundRes: Int = R.drawable.list_bg,
    avoidIme: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().weatherDrawableBackground(backgroundRes)) {
        Spacer(
            Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.safeDrawing)
                .background(colorResource(R.color.app_top_bar_background)),
        )
        val insets = if (avoidIme) WindowInsets.safeDrawing else WindowInsets.systemBars.union(WindowInsets.displayCutout)
        Box(Modifier.fillMaxSize().windowInsetsPadding(insets), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 480.dp).fillMaxSize(), content = content)
        }
    }
}

@Composable
fun WeatherText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = colorResource(R.color.item_pager_content_text_color),
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    BasicText(
        text,
        modifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign ?: TextAlign.Start,
            platformStyle = PlatformTextStyle(includeFontPadding = true),
        ),
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun WeatherTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    @DrawableRes leftIcon: Int? = null,
    leftDescription: String? = null,
    onLeft: () -> Unit = {},
    @DrawableRes rightIcon: Int? = null,
    rightDescription: String? = null,
    onRight: () -> Unit = {},
    rightEnabled: Boolean = true,
    showShadow: Boolean = true,
) {
    val shadow = rememberWeatherDrawablePainter(R.drawable.title_bar_shadow)
    val shadowHeight = dimensionResource(R.dimen.title_bar_shadow_height)
    Box(
        modifier.fillMaxWidth().height(dimensionResource(R.dimen.title_bar_height))
            .zIndex(1f)
            .background(colorResource(R.color.app_top_bar_background))
            .drawWithContent {
                drawContent()
                if (showShadow) translate(top = size.height) {
                    with(shadow) { draw(Size(size.width, shadowHeight.toPx())) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        WeatherTitleText(title, Modifier.fillMaxSize().padding(horizontal = if (leftIcon != null || rightIcon != null) 60.dp else 0.dp))
        if (leftIcon != null) WeatherIconButton(
            leftIcon, leftDescription, onLeft,
            Modifier.align(Alignment.CenterStart).padding(start = dimensionResource(R.dimen.bar_margin_edge)),
        )
        if (rightIcon != null) WeatherIconButton(
            rightIcon, rightDescription, onRight,
            Modifier.align(Alignment.CenterEnd).padding(end = dimensionResource(R.dimen.bar_margin_edge)),
            enabled = rightEnabled,
        )
    }
}

/** TitleBar used Paint's synthetic bold on the normal face, not a bold font file. */
@Composable
private fun WeatherTitleText(title: String, modifier: Modifier = Modifier) {
    val textSize = LocalResources.current.getDimension(R.dimen.title_text_size)
    val color = colorResource(R.color.title_color)
    val paint = remember(textSize, color) {
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.FAKE_BOLD_TEXT_FLAG).apply {
            this.textSize = textSize
            this.color = color.toArgb()
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }
    }
    Canvas(modifier.semantics { text = AnnotatedString(title); heading() }) {
        val visible = TextUtils.ellipsize(title, paint, size.width, TextUtils.TruncateAt.END).toString()
        val metrics = paint.fontMetricsInt
        val baseline = (size.height - (metrics.bottom - metrics.top)) / 2f - metrics.top
        drawIntoCanvas { it.nativeCanvas.drawText(visible, size.width / 2f, baseline, paint) }
    }
}

@Composable
fun WeatherIconButton(
    @DrawableRes resId: Int,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectWeatherPressedAsState()
    Box(
        modifier.size(dimensionResource(R.dimen.standard_icon_size))
            .semantics { description?.let { contentDescription = it } }
            .clickable(interactions, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        WeatherDrawable(resId, null, Modifier.fillMaxSize(), enabled, pressed, contentScale = ContentScale.Inside)
    }
}

@Composable
fun WeatherButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes backgroundRes: Int = R.drawable.shrink_long_btn_red_selector,
    color: Color = Color.White,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectWeatherPressedAsState()
    Box(
        modifier.height(48.dp).weatherDrawableBackground(backgroundRes, enabled, pressed)
            .clickable(interactions, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        WeatherText(text, color = if (enabled) color else color.copy(alpha = 0.5f), fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Preview(showBackground = true)
@Composable
private fun WeatherTitleBarPreview() {
    WeatherTitleBar("城市管理", leftIcon = R.drawable.standard_icon_back_selector, leftDescription = "返回")
}
