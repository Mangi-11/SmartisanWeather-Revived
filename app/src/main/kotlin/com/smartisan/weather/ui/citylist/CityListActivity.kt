package com.smartisan.weather.ui.citylist

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.smartisan.weather.R
import com.smartisan.weather.bean.SmartisanLocation
import com.smartisan.weather.data.city.CityRepository
import com.smartisan.weather.data.model.SavedCity
import com.smartisan.weather.ui.navigation.WeatherTransitionActivity
import com.smartisan.weather.ui.navigation.startWeatherActivityForResult
import com.smartisan.weather.ui.search.SearchCityActivity
import com.smartisan.weather.util.Constants
import kotlinx.coroutines.launch

/** Database operations remain in the ViewModel; Compose owns list gestures and transient animation. */
class CityListActivity : WeatherTransitionActivity() {
    private val viewModel: CityListViewModel by viewModels()
    private var saving by mutableStateOf(false)
    private val searchLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data?.getBooleanExtra(SearchCityActivity.EXTRA_REQUEST_LOCATION, false) == true) {
            setResult(Activity.RESULT_OK, result.data)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            CityListScreen(
                state = state,
                saving = saving,
                onAddCity = ::startAddCity,
                onDone = ::persistOrderAndFinish,
                onBeginDrag = viewModel::beginDrag,
                onMoveCity = viewModel::moveCity,
                onFinishDrag = viewModel::finishDrag,
                onCancelDrag = viewModel::cancelDrag,
                onRequestDelete = { city ->
                    if (state.cities.size <= 1) message(R.string.weather_city_list_delete_last_city_tips)
                    else viewModel.showDeleteConfirm(city)
                },
                onDismissDelete = viewModel::dismissDeleteConfirm,
                onDelete = { city ->
                    val result = viewModel.deleteCity(city)
                    when {
                        result.isFailure -> message(R.string.city_delete_failed)
                        result.getOrNull() == false -> message(R.string.weather_city_list_delete_last_city_tips)
                    }
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshCachedWeather()
    }

    private fun startAddCity() {
        val cities = viewModel.uiState.value.displayCities
        if (cities.size >= CityRepository.MAX_CITIES) { message(R.string.city_count_over_limit); return }
        val search = Intent(this, SearchCityActivity::class.java).apply {
            putStringArrayListExtra(Constants.WEATHER_SEARCH_CITY_PARAMETER_CITYIDS, ArrayList(cities.map(SavedCity::locationKey)))
            cities.maxByOrNull(SavedCity::sortOrder)?.toSmartisanLocation()?.let { putExtra(Constants.WEATHER_SEARCH_CITY_PARAMETER_LOCATION, it) }
            cities.firstOrNull(SavedCity::isLocationCity)?.toSmartisanLocation()?.let { putExtra(Constants.WEATHER_SEARCH_CITY_LOCATION_CITY, it) }
        }
        startWeatherActivityForResult(searchLauncher, search)
    }

    private fun SavedCity.toSmartisanLocation() = SmartisanLocation(
        locationKey = locationKey, locationName = locationName, locationParentName = locationParentName,
        province = province, country = country,
    ).also { it.id = id; it.sortOrder = sortOrder }

    private fun persistOrderAndFinish() {
        if (saving) return
        saving = true
        lifecycleScope.launch {
            if (viewModel.persistCityOrder(viewModel.uiState.value.displayCities).isSuccess) {
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                saving = false
                message(R.string.city_order_save_failed)
            }
        }
    }

    private fun message(resource: Int) = Toast.makeText(this, resource, Toast.LENGTH_SHORT).show()
}
