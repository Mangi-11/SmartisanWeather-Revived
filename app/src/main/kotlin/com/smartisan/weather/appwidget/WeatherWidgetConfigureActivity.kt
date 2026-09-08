package com.smartisan.weather.appwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.smartisan.weather.R
import com.smartisan.weather.data.city.CityRepository
import com.smartisan.weather.data.model.SavedCity
import com.smartisan.weather.data.settings.WeatherSettings
import com.smartisan.weather.ui.components.*
import com.smartisan.weather.ui.navigation.WeatherEdgeToEdgeActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WeatherWidgetConfigureActivity : WeatherEdgeToEdgeActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var selectedCityKey by mutableStateOf(AUTO_CITY_SELECTION)
    private var canRefresh = false
    private var ready by mutableStateOf(false)
    private var setupComplete by mutableStateOf(false)
    private var cities by mutableStateOf<List<SavedCity>>(emptyList())

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

        selectedCityKey = savedInstanceState?.getString("selectedCityKey") ?: AUTO_CITY_SELECTION
        setContent {
            WeatherWidgetConfigurationScreen(
                choices = buildList {
                    add(AUTO_CITY_SELECTION to getString(R.string.weather_widget_auto_city))
                    addAll(cities.map { it.locationKey to cityLabel(it) })
                },
                selectedKey = selectedCityKey,
                setupComplete = setupComplete,
                hasCities = cities.isNotEmpty(),
                ready = ready,
                onSelect = { selectedCityKey = it },
                onCancel = ::finish,
                onDone = ::saveAndFinish,
            )
        }
        lifecycleScope.launch {
            val settings = WeatherSettings.getInstance(this@WeatherWidgetConfigureActivity)
            setupComplete = settings.startupNoticeAccepted.first()
            cities = if (setupComplete) CityRepository(this@WeatherWidgetConfigureActivity).savedCities.first() else emptyList()
            if (savedInstanceState == null) {
                selectedCityKey = settings.readWidgetCitySelections(intArrayOf(appWidgetId))[appWidgetId] ?: AUTO_CITY_SELECTION
            }
            if (cities.none { it.locationKey == selectedCityKey }) selectedCityKey = AUTO_CITY_SELECTION
            canRefresh = setupComplete && cities.isNotEmpty()
            ready = true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selectedCityKey", selectedCityKey)
        super.onSaveInstanceState(outState)
    }

    private fun cityLabel(city: SavedCity): String {
        val parent = city.locationParentName
            .takeUnless { it.isBlank() || it == city.displayName }
        val name = listOfNotNull(city.displayName, parent).joinToString(" · ")
        return if (city.isLocationCity) getString(R.string.current_location, name) else name
    }

    private fun saveAndFinish() {
        if (!ready) return
        ready = false
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

}

@Composable
internal fun WeatherWidgetConfigurationScreen(
    choices: List<Pair<String, String>>,
    selectedKey: String,
    setupComplete: Boolean,
    hasCities: Boolean,
    ready: Boolean,
    onSelect: (String) -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    WeatherScreenFrame {
        Column(Modifier.fillMaxSize()) {
            WeatherTitleBar(
                title = stringResource(R.string.weather_widget_configure_title),
                leftIcon = R.drawable.standard_icon_back_selector,
                leftDescription = stringResource(R.string.cancel),
                onLeft = onCancel,
            )
            if (setupComplete && hasCities) {
                WeatherText(
                    stringResource(R.string.weather_widget_configure_hint),
                    Modifier.padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 12.dp),
                    color = colorResource(R.color.item_pager_content_text_content_color),
                    fontSize = 13.sp,
                )
                LazyColumn(Modifier.weight(1f).selectableGroup(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(choices, key = { it.first }) { (key, label) ->
                        val interactions = remember { MutableInteractionSource() }
                        val pressed by interactions.collectWeatherPressedAsState()
                        val selected = key == selectedKey
                        Row(
                            Modifier.fillMaxWidth().height(52.dp)
                                .weatherDrawableBackground(R.drawable.selector_listitem, pressed = pressed)
                                .selectable(selected, interactions, indication = null, role = Role.RadioButton, onClick = { onSelect(key) })
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WeatherText(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val context = LocalContext.current
                            val configuration = LocalConfiguration.current
                            val indicator = remember(context, configuration) {
                                context.obtainStyledAttributes(intArrayOf(android.R.attr.listChoiceIndicatorSingle)).let { attributes ->
                                    try { requireNotNull(attributes.getDrawable(0)).mutate() } finally { attributes.recycle() }
                                }
                            }
                            Image(
                                painter = rememberWeatherDrawablePainter(indicator, pressed = pressed, checked = selected),
                                contentDescription = null,
                                modifier = Modifier.padding(start = 12.dp).size(32.dp),
                                contentScale = ContentScale.Inside,
                            )
                        }
                        Spacer(Modifier.fillMaxWidth().height(0.5.dp).background(colorResource(R.color.divider_listview)))
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    if (ready) WeatherText(
                        stringResource(if (setupComplete) R.string.weather_widget_configure_no_city else R.string.weather_widget_configure_setup),
                        color = colorResource(R.color.item_pager_content_text_content_color),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(80.dp).background(colorResource(R.color.app_surface_color)).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                WeatherButton(stringResource(R.string.complete), onDone, Modifier.fillMaxWidth(), enabled = ready)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WidgetConfigurationPreview() {
    WeatherWidgetConfigurationScreen(listOf("" to "自动选择", "101010100" to "北京"), "", true, true, true, {}, {}, {})
}
