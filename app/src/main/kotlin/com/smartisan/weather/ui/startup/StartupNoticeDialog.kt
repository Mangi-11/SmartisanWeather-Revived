package com.smartisan.weather.ui.startup

import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.Gravity
import android.view.WindowManager
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import com.smartisan.weather.R
import com.smartisan.weather.ui.components.WeatherDrawable
import com.smartisan.weather.ui.components.WeatherText
import com.smartisan.weather.ui.components.collectWeatherPressedAsState
import com.smartisan.weather.ui.components.weatherDrawableBackground
import com.smartisan.weather.ui.components.weatherStateColor
import com.smartisan.weather.util.ThemeUtils

/** 首次使用说明；是否已同意和天气网络的启动时机均由主页面持有。 */
@Composable
fun StartupNoticeDialog(onContinue: () -> Unit, onExit: () -> Unit) {
    WeatherNoticeDialog(
        title = stringResource(R.string.weather_startup_notice_title),
        message = weatherNoticeText(R.string.weather_startup_notice_message),
        positiveText = stringResource(R.string.weather_startup_notice_continue),
        negativeText = stringResource(R.string.weather_startup_notice_exit),
        onPositive = onContinue,
        onNegative = onExit,
        tagPrefix = "startup_notice",
    )
}

@Composable
internal fun WeatherNoticeDialog(
    title: String,
    message: AnnotatedString,
    positiveText: String,
    negativeText: String,
    onPositive: () -> Unit,
    onNegative: () -> Unit,
    tagPrefix: String,
    titleMaxLines: Int = 1,
) {
    var decisionSubmitted by remember { mutableStateOf(false) }
    fun submitDecision(action: () -> Unit) {
        if (!decisionSubmitted) {
            decisionSubmitted = true
            action()
        }
    }
    Dialog(
        onDismissRequest = { submitDecision(onNegative) },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val background = remember(context, configuration) {
            ContextCompat.getDrawable(context, R.drawable.dialog_full_smartisanos_light)
                ?.mutate()?.apply {
                    if (ThemeUtils.isNightMode(context)) {
                        colorFilter = PorterDuffColorFilter(
                            ContextCompat.getColor(context, R.color.app_surface_color),
                            PorterDuff.Mode.MULTIPLY,
                        )
                    }
                }
        }
        SideEffect {
            // Window graphics preserve the original NinePatch padding and 220/110 ms
            // entrance/exit animations; all content and interaction nodes are Compose.
            window?.apply {
                setBackgroundDrawable(background)
                setGravity(Gravity.CENTER)
                setDimAmount(0.5f)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setWindowAnimations(R.style.PrivacyDialogAnimation)
            }
        }
        WeatherNoticeContent(
            title = title,
            message = message,
            positiveText = positiveText,
            negativeText = negativeText,
            onPositive = { submitDecision(onPositive) },
            onNegative = { submitDecision(onNegative) },
            tagPrefix = tagPrefix,
            titleMaxLines = titleMaxLines,
            enabled = !decisionSubmitted,
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        )
    }
}

@Composable
private fun WeatherNoticeContent(
    title: String,
    message: AnnotatedString,
    positiveText: String,
    negativeText: String,
    onPositive: () -> Unit,
    onNegative: () -> Unit,
    tagPrefix: String,
    titleMaxLines: Int = 1,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val textColor = colorResource(R.color.app_secondary_text_color)
    val raisedColor = colorResource(R.color.app_surface_raised_color)
    val dividerColor = colorResource(R.color.app_divider_color)
    // Compose children do not inherit the Window NinePatch's rounded outline. Clip the
    // content too, using the same 12 dp corners as the original pressed-button resources.
    Column(modifier.clip(RoundedCornerShape(12.dp)).semantics { paneTitle = title }.testTag(tagPrefix)) {
        Box(
            Modifier.fillMaxWidth().background(raisedColor)
                .heightIn(min = 48.dp).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            WeatherText(
                text = title,
                modifier = Modifier.semantics { heading() },
                color = textColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(dividerColor))
        val density = LocalDensity.current
        val lineHeight = remember(density) {
            with(density) {
                (Paint().apply { textSize = 13.sp.toPx() }.fontSpacing * 1.1f).toSp()
            }
        }
        Box(
            Modifier.weight(1f, fill = false).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = 96.dp)
                .padding(start = 20.dp, end = 18.dp, top = 18.dp, bottom = 18.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicText(
                text = message,
                style = TextStyle(
                    color = textColor,
                    fontSize = 13.sp,
                    lineHeight = lineHeight,
                    platformStyle = PlatformTextStyle(includeFontPadding = true),
                ),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(dividerColor))
        Row(Modifier.fillMaxWidth().height(48.dp).background(raisedColor)) {
            StartupNoticeButton(
                text = negativeText,
                onClick = onNegative,
                enabled = enabled,
                backgroundRes = R.drawable.privacy_dialog_button_left_bg,
                textColorRes = R.color.privacy_dialog_negative_text,
                modifier = Modifier.weight(1f).fillMaxHeight().testTag("${tagPrefix}_negative"),
            )
            WeatherDrawable(
                resId = R.drawable.dialog_line_smartisanos,
                contentDescription = null,
                modifier = Modifier.width(0.67.dp).fillMaxHeight(),
            )
            StartupNoticeButton(
                text = positiveText,
                onClick = onPositive,
                enabled = enabled,
                backgroundRes = R.drawable.privacy_dialog_button_right_bg,
                textColorRes = R.color.privacy_dialog_positive_text,
                modifier = Modifier.weight(1f).fillMaxHeight().testTag("${tagPrefix}_positive"),
            )
        }
    }
}

@Composable
private fun StartupNoticeButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    @DrawableRes backgroundRes: Int,
    @ColorRes textColorRes: Int,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectWeatherPressedAsState()
    Box(
        modifier.weatherDrawableBackground(backgroundRes, enabled = enabled, pressed = pressed)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        WeatherText(
            text = text,
            color = weatherStateColor(textColorRes, enabled = enabled, pressed = pressed),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "首次使用 / 日间", widthDp = 340)
@Preview(name = "首次使用 / 夜间", widthDp = 340, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StartupNoticePreview() {
    WeatherNoticeContent(
        title = stringResource(R.string.weather_startup_notice_title),
        message = weatherNoticeText(R.string.weather_startup_notice_message),
        positiveText = stringResource(R.string.weather_startup_notice_continue),
        negativeText = stringResource(R.string.weather_startup_notice_exit),
        onPositive = {},
        onNegative = {},
        tagPrefix = "startup_notice",
        modifier = Modifier.fillMaxWidth().background(colorResource(R.color.app_surface_color), RoundedCornerShape(12.dp)),
    )
}
