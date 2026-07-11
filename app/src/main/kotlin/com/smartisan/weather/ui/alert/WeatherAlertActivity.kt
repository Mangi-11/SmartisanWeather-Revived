package com.smartisan.weather.ui.alert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.smartisan.weather.R
import com.smartisan.weather.custom.VerticalRecyclerView
import com.smartisan.weather.data.model.WeatherAlert
import com.smartisan.weather.util.centeredPhoneContentInsets
import com.smartisan.weather.util.enableWeatherEdgeToEdge
import com.smartisan.weather.util.safeDrawingInsets
import com.smartisan.weather.widget.TitleBar

/** 原版锤子天气风格的预警详情页。 */
class WeatherAlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val alert = readAlert() ?: run {
            finish()
            return
        }
        enableWeatherEdgeToEdge()
        setContentView(R.layout.weather_alert_layout)

        val root = findViewById<android.view.View>(R.id.calendars)
        val titleBar = findViewById<TitleBar>(R.id.title_bar)
        val recycler = findViewById<VerticalRecyclerView>(R.id.recycler_content)

        titleBar.setShadowVisible(false)
        titleBar.setCenterText(R.string.weather_alert_title)
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
        val baseRootPaddingRight = root.paddingRight
        val baseRecyclerPaddingBottom = recycler.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.safeDrawingInsets()
            val horizontalInsets = root.centeredPhoneContentInsets(bars)
            root.updatePadding(
                left = baseRootPaddingLeft + horizontalInsets.left,
                right = baseRootPaddingRight + horizontalInsets.right,
            )
            recycler.updatePadding(bottom = baseRecyclerPaddingBottom + bars.bottom)
            insets
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
