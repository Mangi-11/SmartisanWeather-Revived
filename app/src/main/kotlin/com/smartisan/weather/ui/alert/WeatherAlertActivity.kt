package com.smartisan.weather.ui.alert

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.content.IntentCompat
import com.smartisan.weather.data.model.WeatherAlert
import com.smartisan.weather.ui.navigation.WeatherEdgeToEdgeActivity

/** 原版锤子天气风格的预警详情页。 */
class WeatherAlertActivity : WeatherEdgeToEdgeActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val alert = readAlert() ?: run {
            finish()
            return
        }
        setContent {
            WeatherAlertScreen(alerts = alert.infos, onBack = ::finish)
        }
    }

    private fun readAlert(): WeatherAlert? =
        IntentCompat.getSerializableExtra(intent, EXTRA_ALERT, WeatherAlert::class.java)

    companion object {
        const val EXTRA_ALERT = "alert"
    }
}
