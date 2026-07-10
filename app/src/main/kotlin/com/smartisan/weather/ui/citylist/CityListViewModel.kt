package com.smartisan.weather.ui.citylist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartisan.weather.data.city.CityRepository
import com.smartisan.weather.data.model.SavedCity
import com.smartisan.weather.data.model.Weather
import com.smartisan.weather.data.settings.WeatherSettings
import com.smartisan.weather.data.weather.WeatherRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class CityListUiState(
    val cities: List<SavedCity> = emptyList(),
    val weatherByCity: Map<String, Weather> = emptyMap(),
    val tempUnit: Int = WeatherSettings.UNIT_CELSIUS,
    val showDeleteConfirm: SavedCity? = null,
)

class CityListViewModel(app: Application) : AndroidViewModel(app) {
    private val cityRepo = CityRepository(app)
    private val weatherRepo = WeatherRepository(app)
    private val settings = WeatherSettings.getInstance(app)

    private val _uiState = MutableStateFlow(CityListUiState())
    val uiState: StateFlow<CityListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            cityRepo.savedCities.collectLatest { cities ->
                val cachedWeather = cities.mapNotNull { city ->
                    weatherRepo.getCachedWeather(city.locationKey)?.let { city.locationKey to it }
                }.toMap()
                _uiState.value = _uiState.value.copy(
                    cities = cities,
                    weatherByCity = cachedWeather,
                )
            }
        }
        viewModelScope.launch {
            settings.tempUnit.collectLatest { unit ->
                _uiState.value = _uiState.value.copy(tempUnit = unit)
            }
        }
    }

    /** 城市页从搜索页返回时，数据库行未变化也可能已有新的天气缓存。 */
    fun refreshCachedWeather() {
        val cities = _uiState.value.cities
        if (cities.isEmpty()) return
        viewModelScope.launch {
            val cachedWeather = cities.mapNotNull { city ->
                weatherRepo.getCachedWeather(city.locationKey)?.let { city.locationKey to it }
            }.toMap()
            _uiState.value = _uiState.value.copy(weatherByCity = cachedWeather)
        }
    }

    fun showDeleteConfirm(city: SavedCity) {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = city)
    }

    fun dismissDeleteConfirm() {
        _uiState.value = _uiState.value.copy(showDeleteConfirm = null)
    }

    suspend fun deleteCity(city: SavedCity): Result<Boolean> = try {
        if (cityRepo.cityCount() <= 1) {
            _uiState.value = _uiState.value.copy(showDeleteConfirm = null)
            Result.success(false)
        } else {
            cityRepo.deleteCity(city.locationKey)
            _uiState.value = _uiState.value.copy(
                cities = _uiState.value.cities.filterNot { it.locationKey == city.locationKey },
                weatherByCity = _uiState.value.weatherByCity - city.locationKey,
                showDeleteConfirm = null,
            )
            Result.success(true)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    /** 完成按钮提交完整顺序；DAO 事务保证 StateFlow 只看到最终排列。 */
    suspend fun persistCityOrder(cities: List<SavedCity>): Result<Unit> = try {
        cityRepo.updateCityOrder(cities)
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}
