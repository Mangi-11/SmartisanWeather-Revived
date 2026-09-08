package com.smartisan.weather.ui.startup

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.smartisan.weather.R

@Composable
fun WeatherLocationDialog(onCancel: () -> Unit, onSettings: () -> Unit) {
    WeatherNoticeDialog(
        title = stringResource(R.string.location_server_unavailable),
        message = weatherNoticeText(R.string.whether_open_location_server),
        positiveText = stringResource(R.string.weather_request_location_permission_tips_setting),
        negativeText = stringResource(R.string.weather_request_location_permission_tips_cancel),
        onPositive = onSettings,
        onNegative = onCancel,
        tagPrefix = "location_notice",
        titleMaxLines = 2,
    )
}
