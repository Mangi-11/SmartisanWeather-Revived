package com.smartisan.weather.util

import android.content.Context
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.core.graphics.Insets
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Applies the app-wide edge-to-edge policy to an Activity window. */
fun ComponentActivity.enableWeatherEdgeToEdge() {
    window.enableWeatherEdgeToEdge(this)
}

/**
 * Makes any app-owned window draw behind both system bars.
 *
 * Background surfaces are responsible for filling the system-bar areas, while
 * interactive content applies [WindowInsetsCompat] separately.
 */
fun Window.enableWeatherEdgeToEdge(context: Context) {
    WindowCompat.enableEdgeToEdge(this)
    WindowCompat.getInsetsController(this, decorView).apply {
        val useDarkIcons = !ThemeUtils.isNightMode(context)
        isAppearanceLightStatusBars = useDarkIcons
        isAppearanceLightNavigationBars = useDarkIcons
    }
}

/** Insets that keep interactive content clear of system bars and display cutouts. */
fun WindowInsetsCompat.safeDrawingInsets(): Insets = getInsets(
    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
)
