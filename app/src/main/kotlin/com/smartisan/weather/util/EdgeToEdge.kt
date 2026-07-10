package com.smartisan.weather.util

import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat

/** Applies the app-wide transparent system-bar policy on every Activity. */
fun ComponentActivity.enableWeatherEdgeToEdge() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }
}

/** Insets that keep interactive content clear of system bars and display cutouts. */
fun WindowInsetsCompat.safeDrawingInsets(): Insets = getInsets(
    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
)
