package com.smartisan.weather.ui.navigation

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import com.smartisan.weather.R

/** 原版天气页面的上推进入、下滑退出转场。 */
abstract class WeatherTransitionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.pop_up_in,
                R.anim.fake_anim,
            )
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                0,
                R.anim.slide_down_out,
            )
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, R.anim.slide_down_out)
        }
    }
}

fun Activity.startWeatherActivity(intent: Intent) {
    startActivity(intent)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.pop_up_in, R.anim.fake_anim)
    }
}

fun Activity.startWeatherActivityForResult(
    launcher: ActivityResultLauncher<Intent>,
    intent: Intent,
) {
    launcher.launch(intent)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.pop_up_in, R.anim.fake_anim)
    }
}
