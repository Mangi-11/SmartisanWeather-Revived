package com.smartisan.weather.custom

import androidx.annotation.DrawableRes
import com.smartisan.weather.R

/** Explicit resource table for temperature artwork so Release resource shrinking stays safe. */
internal object TemperatureDrawableResources {
    private val mainDigits = intArrayOf(
        R.drawable.num_0,
        R.drawable.num_1,
        R.drawable.num_2,
        R.drawable.num_3,
        R.drawable.num_4,
        R.drawable.num_5,
        R.drawable.num_6,
        R.drawable.num_7,
        R.drawable.num_8,
        R.drawable.num_9,
    )

    private val firstScrollDigits = intArrayOf(
        R.drawable.num_0_1,
        R.drawable.num_1_1,
        R.drawable.num_2_1,
        R.drawable.num_3_1,
        R.drawable.num_4_1,
        R.drawable.num_5_1,
        R.drawable.num_6_1,
        R.drawable.num_7_1,
        R.drawable.num_8_1,
        R.drawable.num_9_1,
    )

    private val secondScrollDigits = intArrayOf(
        R.drawable.num_0_2,
        R.drawable.num_1_2,
        R.drawable.num_2_2,
        R.drawable.num_3_2,
        R.drawable.num_4_2,
        R.drawable.num_5_2,
        R.drawable.num_6_2,
        R.drawable.num_7_2,
        R.drawable.num_8_2,
        R.drawable.num_9_2,
    )

    @DrawableRes
    fun digit(digit: Int, style: Int = 0): Int {
        val table = when (style) {
            1 -> firstScrollDigits
            2 -> secondScrollDigits
            else -> mainDigits
        }
        return table.getOrElse(digit) { R.drawable.num_0 }
    }

    @DrawableRes
    fun minus(style: Int = 0): Int = when (style) {
        1 -> R.drawable.num_minus_1
        2 -> R.drawable.num_minus_2
        else -> R.drawable.num_minus
    }

    @DrawableRes
    fun blurry(index: Int): Int = when (index) {
        1 -> R.drawable.mohuzhong_1
        2 -> R.drawable.mohuzhong_2
        3 -> R.drawable.mohuzhong_3
        else -> 0
    }
}
