package com.smartisan.weather.ui.startup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringResource
import com.smartisan.weather.R

@Composable
fun WeatherLocationDialog(
    onCancel: () -> Unit,
    onSettings: () -> Unit,
    notice: LocationNotice = LocationNotice.SERVICES,
) {
    key(notice) {
        WeatherNoticeDialog(
            title = stringResource(when (notice) {
                LocationNotice.SERVICES -> R.string.location_server_unavailable
                LocationNotice.PERMISSION, LocationNotice.RATIONALE -> R.string.weather_location_permission_title
                LocationNotice.PRECISION -> R.string.weather_location_precision_title
            }),
            message = weatherNoticeText(when (notice) {
                LocationNotice.SERVICES -> R.string.whether_open_location_server
                LocationNotice.PERMISSION, LocationNotice.RATIONALE -> R.string.weather_location_permission_message
                LocationNotice.PRECISION -> R.string.weather_location_precision_message
            }),
            positiveText = stringResource(
                if (notice == LocationNotice.RATIONALE) R.string.weather_location_allow
                else R.string.weather_request_location_permission_tips_setting,
            ),
            negativeText = stringResource(
                if (notice == LocationNotice.PRECISION) R.string.weather_location_use_approximate
                else R.string.weather_request_location_permission_tips_cancel,
            ),
            onPositive = onSettings,
            onNegative = onCancel,
            tagPrefix = "location_notice",
            titleMaxLines = 2,
        )
    }
}

enum class LocationNotice { SERVICES, PERMISSION, PRECISION, RATIONALE }
