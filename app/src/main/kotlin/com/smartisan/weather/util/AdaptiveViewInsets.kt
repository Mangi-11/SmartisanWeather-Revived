package com.smartisan.weather.util

import android.view.View
import androidx.core.graphics.Insets
import kotlin.math.max

data class HorizontalContentInsets(val left: Int, val right: Int)

/** Keeps the restored phone composition intact when API 37 forces a large resizable window. */
fun View.centeredPhoneContentInsets(
    safeInsets: Insets,
    maximumWidthDp: Float = 480f,
): HorizontalContentInsets {
    val maximumWidth = (maximumWidthDp * resources.displayMetrics.density).toInt()
    val centeredInset = ((width - maximumWidth) / 2).coerceAtLeast(0)
    return HorizontalContentInsets(
        left = max(safeInsets.left, centeredInset),
        right = max(safeInsets.right, centeredInset),
    )
}
