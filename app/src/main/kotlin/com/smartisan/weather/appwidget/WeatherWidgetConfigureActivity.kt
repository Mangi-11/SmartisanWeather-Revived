package com.smartisan.weather.appwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.smartisan.weather.R
import com.smartisan.weather.data.city.CityRepository
import com.smartisan.weather.data.model.SavedCity
import com.smartisan.weather.data.settings.WeatherSettings
import com.smartisan.weather.util.centeredPhoneContentInsets
import com.smartisan.weather.util.enableWeatherEdgeToEdge
import com.smartisan.weather.util.safeDrawingInsets
import com.smartisan.weather.widget.ShadowButton
import com.smartisan.weather.widget.TitleBar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WeatherWidgetConfigureActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var selectedCityKey = AUTO_CITY_SELECTION
    private var canRefresh = false
    private lateinit var cityList: ListView
    private lateinit var message: TextView
    private lateinit var hint: TextView
    private lateinit var doneButton: ShadowButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        setResult(
            Activity.RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val provider = AppWidgetManager.getInstance(this)
            .getAppWidgetInfo(appWidgetId)
            ?.provider
        if (provider != ComponentName(this, WeatherWidgetProvider::class.java)) {
            finish()
            return
        }

        enableWeatherEdgeToEdge()
        setContentView(R.layout.activity_weather_widget_configure)
        cityList = findViewById(R.id.widget_configure_city_list)
        message = findViewById(R.id.widget_configure_message)
        hint = findViewById(R.id.widget_configure_hint)
        doneButton = findViewById(R.id.widget_configure_done)

        setupTitleBar()
        applyInsets(findViewById(R.id.widget_configure_root))
        doneButton.setOnClickListener { saveAndFinish() }
        loadConfiguration()
    }

    private fun setupTitleBar() {
        findViewById<TitleBar>(R.id.title_bar).apply {
            setShadowVisible(false)
            setCenterText(R.string.weather_widget_configure_title)
            addLeftImageView(R.drawable.standard_icon_back_selector).apply {
                contentDescription = getString(R.string.cancel)
                setOnClickListener { finish() }
            }
        }
    }

    private fun loadConfiguration() {
        doneButton.isEnabled = false
        lifecycleScope.launch {
            val settings = WeatherSettings.getInstance(this@WeatherWidgetConfigureActivity)
            val setupComplete = settings.startupNoticeAccepted.first()
            val cities = if (setupComplete) {
                CityRepository(this@WeatherWidgetConfigureActivity).savedCities.first()
            } else {
                emptyList()
            }
            selectedCityKey = settings.readWidgetCitySelections(intArrayOf(appWidgetId))[appWidgetId]
                ?: AUTO_CITY_SELECTION
            canRefresh = setupComplete && cities.isNotEmpty()
            renderOptions(setupComplete, cities)
            doneButton.isEnabled = true
        }
    }

    private fun renderOptions(setupComplete: Boolean, cities: List<SavedCity>) {
        if (!setupComplete || cities.isEmpty()) {
            cityList.visibility = View.GONE
            hint.visibility = View.GONE
            message.visibility = View.VISIBLE
            message.setText(
                if (setupComplete) {
                    R.string.weather_widget_configure_no_city
                } else {
                    R.string.weather_widget_configure_setup
                },
            )
            selectedCityKey = AUTO_CITY_SELECTION
            return
        }

        message.visibility = View.GONE
        hint.visibility = View.VISIBLE
        cityList.visibility = View.VISIBLE
        val keys = buildList {
            add(AUTO_CITY_SELECTION)
            addAll(cities.map(SavedCity::locationKey))
        }
        val labels = buildList {
            add(getString(R.string.weather_widget_auto_city))
            addAll(cities.map(::cityLabel))
        }
        cityList.adapter = ArrayAdapter(
            this,
            R.layout.item_weather_widget_city,
            R.id.widget_city_choice,
            labels,
        )
        cityList.choiceMode = ListView.CHOICE_MODE_SINGLE
        val selectedIndex = keys.indexOf(selectedCityKey).takeIf { it >= 0 } ?: 0
        selectedCityKey = keys[selectedIndex]
        cityList.setItemChecked(selectedIndex, true)
        cityList.setSelection(selectedIndex)
        cityList.setOnItemClickListener { _, _, position, _ ->
            selectedCityKey = keys[position]
        }
    }

    private fun cityLabel(city: SavedCity): String {
        val parent = city.locationParentName
            .takeUnless { it.isBlank() || it == city.displayName }
        val name = listOfNotNull(city.displayName, parent).joinToString(" · ")
        return if (city.isLocationCity) getString(R.string.current_location, name) else name
    }

    private fun saveAndFinish() {
        if (!doneButton.isEnabled) return
        doneButton.isEnabled = false
        lifecycleScope.launch {
            WeatherSettings.getInstance(this@WeatherWidgetConfigureActivity)
                .setWidgetCitySelection(appWidgetId, selectedCityKey)
            WeatherWidgetUpdater.renderCached(
                context = this@WeatherWidgetConfigureActivity,
                requestedIds = intArrayOf(appWidgetId),
            )
            WeatherWidgetScheduler.ensurePeriodicRefresh(this@WeatherWidgetConfigureActivity)
            if (canRefresh) {
                WeatherWidgetScheduler.requestRefresh(
                    context = this@WeatherWidgetConfigureActivity,
                    appWidgetId = appWidgetId,
                )
            }
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
        }
    }

    private fun applyInsets(root: View) {
        val basePaddingLeft = root.paddingLeft
        val basePaddingRight = root.paddingRight
        val basePaddingBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.safeDrawingInsets()
            val horizontal = view.centeredPhoneContentInsets(bars)
            view.updatePadding(
                left = basePaddingLeft + horizontal.left,
                right = basePaddingRight + horizontal.right,
                bottom = basePaddingBottom + bars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
        root.doOnLayout { ViewCompat.requestApplyInsets(it) }
    }
}
