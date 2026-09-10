package com.smartisan.weather.ui.search

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.smartisan.weather.R
import com.smartisan.weather.bean.SmartisanLocation
import com.smartisan.weather.ui.navigation.WeatherTransitionActivity
import com.smartisan.weather.util.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Search returns navigation results; MainActivity owns the shared location permission flow. */
class SearchCityActivity : WeatherTransitionActivity() {
    private val viewModel: SearchViewModel by viewModels()
    private var closing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launchCityIds = intent.getStringArrayListExtra(
            Constants.WEATHER_SEARCH_CITY_PARAMETER_CITYIDS,
        ).orEmpty().toSet()
        val currentLocation = IntentCompat.getParcelableExtra(
            intent, Constants.WEATHER_SEARCH_CITY_PARAMETER_LOCATION, SmartisanLocation::class.java,
        )
        val location = IntentCompat.getParcelableExtra(
            intent, Constants.WEATHER_SEARCH_CITY_LOCATION_CITY, SmartisanLocation::class.java,
        )
        viewModel.setInsertAfterKey(currentLocation?.mLocationKey)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            BackHandler { closeWithoutSelection() }
            SearchCityScreen(
                state = state.copy(addedKeys = launchCityIds + state.addedKeys),
                locationName = location?.takeUnless { it.mLocationKey == "-1" }?.let {
                    listOfNotNull(it.mLocationParentName, it.mLocationName)
                        .filter(String::isNotBlank).distinct().joinToString(getString(R.string.separator))
                },
                locationKey = location?.mLocationKey,
                onQueryChange = viewModel::updateQuery,
                onCancel = ::closeWithoutSelection,
                onRetry = viewModel::retry,
                onCityClick = viewModel::addCity,
                onHotCityClick = viewModel::addHotCity,
                onLocationClick = ::returnLocationRequest,
                onAlreadyAdded = { Toast.makeText(this, R.string.weather_add_city_alread, Toast.LENGTH_SHORT).show() },
            )
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is SearchEvent.CityAdded -> {
                            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_SELECTED_CITY_KEY, event.cityKey))
                            finishAfterKeyboard(false)
                        }
                        is SearchEvent.ShowMessage -> Toast.makeText(
                            this@SearchCityActivity, event.message, Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }

    private fun closeWithoutSelection() = finishAfterKeyboard(intent.getBooleanExtra(EXTRA_REQUIRE_CITY, false))

    /** Preserve the original 100 ms keyboard exit window before the Activity transition. */
    private fun finishAfterKeyboard(useAffinity: Boolean) {
        if (closing) return
        closing = true
        hideKeyboard()
        lifecycleScope.launch {
            delay(100)
            if (useAffinity) finishAffinity() else finish()
        }
    }

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    private fun returnLocationRequest() {
        if (closing) return
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_REQUEST_LOCATION, true))
        finishAfterKeyboard(false)
    }

    companion object {
        const val EXTRA_REQUIRE_CITY = "require_city"
        const val EXTRA_REQUEST_LOCATION = "request_location"
        const val EXTRA_SELECTED_CITY_KEY = "selected_city_key"
    }
}
