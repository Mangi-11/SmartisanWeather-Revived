package com.smartisan.weather.ui.navigation

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.smartisan.weather.util.enableWeatherEdgeToEdge

/** 所有应用页面统一启用 edge-to-edge，子类只负责各自内容的 Insets。 */
abstract class WeatherEdgeToEdgeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableWeatherEdgeToEdge()
    }
}
