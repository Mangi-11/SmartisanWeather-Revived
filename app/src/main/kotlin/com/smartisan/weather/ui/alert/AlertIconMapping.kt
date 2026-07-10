package com.smartisan.weather.ui.alert

import android.content.Context
import androidx.annotation.DrawableRes
import com.smartisan.weather.R

/** 原版预警等级、类型编号与图标资源的映射。 */
internal object AlertIconMapping {

    @DrawableRes
    fun levelBackground(levelNumber: String): Int = when (levelNumber) {
        "01" -> R.drawable.alert_level_01
        "02" -> R.drawable.alert_level_02
        "03" -> R.drawable.alert_level_03
        "04" -> R.drawable.alert_level_04
        "05" -> R.drawable.alert_level_05
        else -> R.drawable.alert_level_05
    }

    @DrawableRes
    fun typeIcon(
        context: Context,
        typeNumber: String,
        type: String,
    ): Int {
        if (typeNumber == SPECIAL_TYPE_NUMBER) {
            return specialTypeIcon(context, type)
        }
        return when (typeNumber) {
            "01" -> R.drawable.alert_01
            "02" -> R.drawable.alert_02
            "03" -> R.drawable.alert_03
            "04" -> R.drawable.alert_04
            "05" -> R.drawable.alert_05
            "06" -> R.drawable.alert_06
            "07" -> R.drawable.alert_07
            "08" -> R.drawable.alert_08
            "09" -> R.drawable.alert_09
            "10" -> R.drawable.alert_10
            "11" -> R.drawable.alert_11
            "12" -> R.drawable.alert_12
            "13" -> R.drawable.alert_13
            "14" -> R.drawable.alert_14
            else -> R.drawable.alert_common
        }
    }

    @DrawableRes
    private fun specialTypeIcon(context: Context, type: String): Int {
        return specialTypes.firstOrNull { (nameRes, _) ->
            context.getString(nameRes) == type
        }?.second ?: R.drawable.alert_common
    }

    private val specialTypes = listOf(
        R.string.weather_alert_cold to R.drawable.alert_00_cold,
        R.string.weather_alert_cool_down to R.drawable.alert_00_cool_down,
        R.string.weather_alert_dry_hot_air to R.drawable.alert_00_dry_hot_air,
        R.string.weather_alert_frost_fire to R.drawable.alert_00_frost_fire,
        R.string.weather_alert_frozen to R.drawable.alert_00_frozen,
        R.string.weather_alert_low_temp to R.drawable.alert_00_low_temp,
        R.string.weather_alert_road_ice_and_snow to R.drawable.alert_00_road_ice_and_snow,
        R.string.weather_alert_thunderstorm to R.drawable.alert_00_thunderstorm,
    )

    private const val SPECIAL_TYPE_NUMBER = "00"
}
