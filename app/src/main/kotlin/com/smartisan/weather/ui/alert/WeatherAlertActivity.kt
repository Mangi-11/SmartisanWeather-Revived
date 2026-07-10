package com.smartisan.weather.ui.alert

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.core.content.IntentCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.smartisan.weather.R
import com.smartisan.weather.custom.VerticalRecyclerView
import com.smartisan.weather.data.model.WeatherAlert
import com.smartisan.weather.util.Utility
import com.smartisan.weather.util.centeredPhoneContentInsets
import com.smartisan.weather.widget.TitleBar

/** 原版锤子天气风格的预警详情页。 */
class WeatherAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val alert = readAlert() ?: run {
            finish()
            return
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContentView(R.layout.weather_alert_layout)

        val root = findViewById<android.view.View>(R.id.calendars)
        val titleBar = findViewById<TitleBar>(R.id.title_bar)
        val recycler = findViewById<VerticalRecyclerView>(R.id.recycler_content)

        titleBar.setShadowVisible(false)
        titleBar.setCenterView(
            Utility.getDefaultDeviceTitleView(this, getString(R.string.weather_alert_title)),
        )
        titleBar
            .addLeftImageView(R.drawable.standard_icon_back_selector)
            .apply {
                contentDescription = getString(R.string.cancel)
                setOnClickListener { finish() }
            }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.itemAnimator = null
        recycler.adapter = WeatherAlertAdapter(alert.infos)

        applySystemBarInsets(root, recycler)
    }

    private fun applySystemBarInsets(
        root: android.view.View,
        recycler: VerticalRecyclerView,
    ) {
        val baseRootPaddingLeft = root.paddingLeft
        val baseRootPaddingTop = root.paddingTop
        val baseRootPaddingRight = root.paddingRight
        val baseRecyclerPaddingBottom = recycler.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout(),
            )
            val horizontalInsets = root.centeredPhoneContentInsets(bars)
            root.updatePadding(
                left = baseRootPaddingLeft + horizontalInsets.left,
                top = baseRootPaddingTop + bars.top,
                right = baseRootPaddingRight + horizontalInsets.right,
            )
            recycler.updatePadding(bottom = baseRecyclerPaddingBottom + bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
        root.doOnLayout { ViewCompat.requestApplyInsets(it) }
    }

    private fun readAlert(): WeatherAlert? =
        IntentCompat.getSerializableExtra(intent, EXTRA_ALERT, WeatherAlert::class.java)

    companion object {
        const val EXTRA_ALERT = "alert"
    }
}
