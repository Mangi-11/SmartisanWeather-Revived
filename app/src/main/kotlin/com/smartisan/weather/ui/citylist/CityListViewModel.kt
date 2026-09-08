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
    val previewOrderKeys: List<String>? = null,
) {
    val displayCities: List<SavedCity>
        get() = mergeCityPreview(cities, previewOrderKeys)
}

class CityListViewModel(app: Application) : AndroidViewModel(app) {
    private val cityRepo = CityRepository(app)
    private val weatherRepo = WeatherRepository(app)
    private val settings = WeatherSettings.getInstance(app)
    private var dragSnapshot: List<String>? = null

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

    fun beginDrag(cityKey: String) {
        if (_uiState.value.displayCities.none { it.locationKey == cityKey && !it.isLocationCity }) return
        dragSnapshot = _uiState.value.displayCities.map(SavedCity::locationKey)
    }

    fun moveCity(cityKey: String, targetKey: String) {
        if (dragSnapshot == null) return
        val cities = _uiState.value.displayCities
        val from = cities.indexOfFirst { it.locationKey == cityKey }
        val to = cities.indexOfFirst { it.locationKey == targetKey }
        if (from < 0 || to < 0 || cities[to].isLocationCity || from == to) return
        val keys = cities.map(SavedCity::locationKey).toMutableList()
        keys.add(to, keys.removeAt(from))
        _uiState.value = _uiState.value.copy(previewOrderKeys = keys)
    }

    fun finishDrag() { dragSnapshot = null }

    fun cancelDrag() {
        dragSnapshot?.let { _uiState.value = _uiState.value.copy(previewOrderKeys = it) }
        dragSnapshot = null
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

/** Merge new repository objects without discarding an uncommitted order or resurrecting a deleted city. */
internal fun mergeCityPreview(cities: List<SavedCity>, previewKeys: List<String>?): List<SavedCity> {
    if (previewKeys == null) return cities
    val latest = cities.associateBy(SavedCity::locationKey)
    val merged = previewKeys.mapNotNull(latest::get).toMutableList()
    val known = merged.mapTo(HashSet(), SavedCity::locationKey)
    cities.forEach { if (known.add(it.locationKey)) merged.add(it) }
    // Location remains the non-draggable first row even if it arrived while editing.
    return merged.filter(SavedCity::isLocationCity) + merged.filterNot(SavedCity::isLocationCity)
}
