package com.smartisan.weather.ui.alert

import android.content.res.Configuration
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smartisan.weather.R
import com.smartisan.weather.data.model.AlertInfo
import com.smartisan.weather.ui.components.WeatherDrawable
import com.smartisan.weather.ui.components.WeatherScreenFrame
import com.smartisan.weather.ui.components.WeatherText
import com.smartisan.weather.ui.components.WeatherTitleBar
import com.smartisan.weather.ui.components.weatherDrawableBackground
import com.smartisan.weather.ui.components.weatherTextSize
import com.smartisan.weather.util.Utility

/** 完整展示每条预警；卡片没有与原界面不符的折叠或摘要状态。 */
@Composable
internal fun WeatherAlertScreen(alerts: List<AlertInfo>, onBack: () -> Unit) {
    WeatherScreenFrame(backgroundRes = R.drawable.list_bg) {
        Column(Modifier.fillMaxSize()) {
            WeatherTitleBar(
                title = stringResource(R.string.weather_alert_title),
                leftIcon = R.drawable.standard_icon_back_selector,
                leftDescription = stringResource(R.string.cancel),
                onLeft = onBack,
                showShadow = false,
            )
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("weather_alert_list"),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(alerts, key = { index, _ -> index }) { _, alert ->
                        WeatherAlertCard(alert)
                    }
                }
                WeatherDrawable(
                    resId = R.drawable.title_bar_shadow,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth()
                        .height(dimensionResource(R.dimen.title_bar_shadow_height)),
                )
            }
        }
    }
}

@Composable
private fun WeatherAlertCard(alert: AlertInfo) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth()
            .padding(
                horizontal = dimensionResource(R.dimen.layout_alert_card_margin_horizontal),
                vertical = dimensionResource(R.dimen.layout_alert_card_margin_vertical),
            )
            .clip(RoundedCornerShape(6.dp))
            .weatherDrawableBackground(R.drawable.weather_alert_card_background),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = dimensionResource(R.dimen.layout_alert_height))
                .padding(
                    start = dimensionResource(R.dimen.layout_alert_type_text_left_margin_left),
                    end = dimensionResource(R.dimen.layout_alert_update_time_text_margin_right),
                    top = dimensionResource(R.dimen.layout_alert_header_padding_vertical),
                    bottom = dimensionResource(R.dimen.layout_alert_header_padding_vertical),
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                WeatherText(
                    text = stringResource(R.string.weather_alert_tip, alert.type, alert.level),
                    modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                    color = colorResource(R.color.weather_alert_title_text_color),
                    fontSize = weatherTextSize(R.dimen.layout_alert_type_text_size),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier.padding(
                        start = dimensionResource(R.dimen.layout_alert_img_margin_left),
                        end = dimensionResource(R.dimen.layout_alert_header_item_spacing),
                    ).size(
                        width = dimensionResource(R.dimen.layout_alert_img_bg_width),
                        height = dimensionResource(R.dimen.layout_alert_img_bg_height),
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    WeatherDrawable(
                        resId = AlertIconMapping.levelBackground(alert.levelNumber),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                    WeatherDrawable(
                        resId = AlertIconMapping.typeIcon(context, alert.typeNumber, alert.type),
                        contentDescription = null,
                        modifier = Modifier.size(
                            width = dimensionResource(R.dimen.layout_alert_img_top_width),
                            height = dimensionResource(R.dimen.layout_alert_img_top_height),
                        ),
                    )
                }
            }
            WeatherText(
                text = Utility.getDisplayTime(context, alert.publishTime),
                modifier = Modifier.padding(top = 0.33.dp),
                color = colorResource(R.color.weather_alert_update_time_text_color),
                fontSize = weatherTextSize(R.dimen.layout_alert_update_time_text_size),
                maxLines = 1,
            )
        }
        Box(
            Modifier.fillMaxWidth().height(0.67.dp)
                .background(colorResource(R.color.weather_alert_seperation_color)),
        )
        val density = LocalDensity.current
        val extraSpacing = dimensionResource(R.dimen.layout_alert_content_line_spacing)
        val fontSize = weatherTextSize(R.dimen.layout_alert_content_text_size)
        // TextView's lineSpacingExtra adds to font metrics, not to the nominal sp size.
        val lineHeight = remember(density, extraSpacing, fontSize) {
            with(density) {
                (Paint().apply { textSize = fontSize.toPx() }.fontSpacing + extraSpacing.toPx()).toSp()
            }
        }
        BasicText(
            text = alert.content,
            modifier = Modifier.fillMaxWidth().padding(
                start = dimensionResource(R.dimen.layout_alert_content_margin_left),
                top = dimensionResource(R.dimen.layout_alert_content_text_margin_top),
                end = dimensionResource(R.dimen.layout_alert_content_margin_right),
                bottom = dimensionResource(R.dimen.layout_alert_content_margin_bottom),
            ),
            style = TextStyle(
                color = colorResource(R.color.weather_alert_body_text_color),
                fontSize = fontSize,
                lineHeight = lineHeight,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Top,
                    trim = LineHeightStyle.Trim.LastLineBottom,
                ),
            ),
        )
    }
}

@Preview(name = "预警 / 日间", widthDp = 393, heightDp = 780)
@Preview(name = "预警 / 夜间", widthDp = 393, heightDp = 780, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WeatherAlertPreview() {
    WeatherAlertScreen(
        alerts = listOf(
            AlertInfo(
                typeNumber = "02", type = "暴雨", level = "黄色", levelNumber = "02",
                content = "预计未来六小时内部分地区降雨量将达到 50 毫米以上，请注意防范强降雨可能引发的次生灾害。",
            ),
        ),
        onBack = {},
    )
}
