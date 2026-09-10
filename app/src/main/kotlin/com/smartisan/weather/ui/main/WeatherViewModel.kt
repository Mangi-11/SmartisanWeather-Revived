package com.smartisan.weather.ui.main

import android.app.Application
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartisan.weather.R
import com.smartisan.weather.data.city.CityRepository
import com.smartisan.weather.data.location.LocationCityFailureReason
import com.smartisan.weather.data.location.LocationCityResolutionException
import com.smartisan.weather.data.location.LocationCityResolver
import com.smartisan.weather.data.location.DeviceLocationSource
import com.smartisan.weather.data.model.SavedCity
import com.smartisan.weather.data.model.Weather
import com.smartisan.weather.data.settings.WeatherSettings
import com.smartisan.weather.data.weather.WeatherRepository
import com.smartisan.weather.util.DebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * 主界面状态：城市列表 + 每个城市的天气数据。
 */
data class WeatherUiState(
    val citiesLoaded: Boolean = false,
    val cities: List<SavedCity> = emptyList(),
    val weathers: Map<String, Weather> = emptyMap(),
    val currentIndex: Int = 0,
    val isCelsius: Boolean = true,
    val isLocating: Boolean = false,
    val loadingKeys: Set<String> = emptySet(),
    val errorsByKey: Map<String, String> = emptyMap(),
    val loadVersions: Map<String, Long> = emptyMap(),
) {
    val currentCity: SavedCity?
        get() = cities.getOrNull(currentIndex)

    val currentWeather: Weather?
        get() = currentCity?.let { weathers[it.locationKey] }

    val isLoading: Boolean
        get() = currentCity?.locationKey in loadingKeys

    val error: String?
        get() = currentCity?.let { errorsByKey[it.locationKey] }
}

sealed interface WeatherEvent {
    data class LocationFailed(@param:StringRes val message: Int) : WeatherEvent
}

internal const val AUTOMATIC_WEATHER_REFRESH_INTERVAL_MILLIS = 10L * 60L * 1_000L

internal fun shouldRefreshWeatherCache(
    updatedAtMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
): Boolean {
    if (updatedAtMillis <= 0L) return true
    val ageMillis = nowMillis - updatedAtMillis
    return ageMillis !in 0L until AUTOMATIC_WEATHER_REFRESH_INTERVAL_MILLIS
}

/**
 * 主界面 ViewModel。
 */
class WeatherViewModel(app: Application) : AndroidViewModel(app) {

    private val cityRepo = CityRepository(app)
    private val weatherRepo = WeatherRepository(app)
    private val settings = WeatherSettings.getInstance(app)
    private val locationResolver = LocationCityResolver(app)
    private val locationSource = DeviceLocationSource(app)
    private var lastLocationAttemptMillis: Long? = null

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()
    private val eventChannel = Channel<WeatherEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private val loadJobs = mutableMapOf<String, Job>()
    private val pendingForceRefreshKeys = mutableSetOf<String>()
    private var locationJob: Job? = null
    private var pendingFocusKey: String? = null

    init {
        // 观察城市列表
        viewModelScope.launch {
            cityRepo.savedCities.collectLatest { cities ->
                val cityKeys = cities.mapTo(mutableSetOf()) { it.locationKey }
                loadJobs.keys.filterNot(cityKeys::contains).forEach { key ->
                    loadJobs.remove(key)?.cancel()
                }
                pendingForceRefreshKeys.retainAll(cityKeys)
                _uiState.update { state ->
                    val currentKey = state.currentCity?.locationKey
                    val preservedIndex = cities.indexOfFirst { it.locationKey == currentKey }
                    val requestedIndex = cities.indexOfFirst {
                        it.locationKey == pendingFocusKey
                    }
                    if (requestedIndex >= 0) pendingFocusKey = null
                    val nextIndex = when {
                        cities.isEmpty() -> 0
                        requestedIndex >= 0 -> requestedIndex
                        preservedIndex >= 0 -> preservedIndex
                        else -> state.currentIndex.coerceIn(cities.indices)
                    }
                    state.copy(
                        citiesLoaded = true,
                        cities = cities,
                        currentIndex = nextIndex,
                        weathers = state.weathers.filterKeys(cityKeys::contains),
                        loadingKeys = state.loadingKeys.intersect(cityKeys),
                        errorsByKey = state.errorsByKey.filterKeys(cityKeys::contains),
                        loadVersions = state.loadVersions.filterKeys(cityKeys::contains),
                    )
                }
                // 为每个城市加载天气
                cities.forEach { city ->
                    if (!_uiState.value.weathers.containsKey(city.locationKey)) {
                        loadWeather(city.locationKey)
                    }
                }
            }
        }
        // 观察温度单位设置
        viewModelScope.launch {
            settings.isCelsius.collectLatest { isC ->
                _uiState.update { it.copy(isCelsius = isC) }
            }
        }
    }

    /** 加载天气数据；自动加载复用短时间内的新鲜缓存，用户刷新始终请求网络。 */
    fun loadWeather(cityKey: String, forceRefresh: Boolean = false) {
        if (cityKey.isBlank()) return
        if (loadJobs[cityKey]?.isActive == true) {
            if (forceRefresh) pendingForceRefreshKeys += cityKey
            return
        }
        loadJobs[cityKey] = viewModelScope.launch {
            val initialState = _uiState.value
            var notifyLoadCompletion = initialState.errorsByKey.containsKey(cityKey)
            _uiState.update { state ->
                state.copy(
                    loadingKeys = state.loadingKeys + cityKey,
                    errorsByKey = state.errorsByKey - cityKey,
                )
            }
            var failure: String? = null
            var networkRefreshSucceeded = false
            try {
                val snapshot = weatherRepo.getCachedWeatherSnapshot(cityKey)
                if (snapshot != null) {
                    _uiState.update { state ->
                        if (state.cities.none { it.locationKey == cityKey }) state
                        else state.copy(weathers = state.weathers + (cityKey to snapshot.weather))
                    }
                }

                val cacheIsFresh = snapshot?.let { cached ->
                    cached.weather.isComplete &&
                        !shouldRefreshWeatherCache(cached.updatedAtMillis)
                } == true
                val shouldFetch = forceRefresh || !cacheIsFresh
                notifyLoadCompletion = notifyLoadCompletion || shouldFetch
                if (shouldFetch) {
                    val weather = weatherRepo.fetchWeather(cityKey)
                    if (weather != null && weather.isComplete) {
                        networkRefreshSucceeded = true
                        _uiState.update { state ->
                            if (state.cities.none { it.locationKey == cityKey }) state
                            else state.copy(weathers = state.weathers + (cityKey to weather))
                        }
                    } else {
                        failure = "获取天气数据失败"
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failure = "获取天气数据失败"
            } finally {
                _uiState.update { state ->
                    if (state.cities.none { it.locationKey == cityKey }) {
                        return@update state.copy(
                            loadingKeys = state.loadingKeys - cityKey,
                            errorsByKey = state.errorsByKey - cityKey,
                            loadVersions = state.loadVersions - cityKey,
                        )
                    }
                    state.copy(
                        loadingKeys = state.loadingKeys - cityKey,
                        errorsByKey = if (failure == null) {
                            state.errorsByKey - cityKey
                        } else {
                            state.errorsByKey + (cityKey to failure)
                        },
                        loadVersions = if (notifyLoadCompletion) {
                            val nextVersion = (state.loadVersions[cityKey] ?: 0L) + 1L
                            state.loadVersions + (cityKey to nextVersion)
                        } else {
                            state.loadVersions
                        },
                    )
                }
                loadJobs.remove(cityKey)
                if (
                    pendingForceRefreshKeys.remove(cityKey) &&
                    !networkRefreshSucceeded &&
                    _uiState.value.cities.any { it.locationKey == cityKey }
                ) {
                    loadWeather(cityKey, forceRefresh = true)
                }
            }
        }
    }

    /** 刷新当前城市天气 */
    fun refreshCurrentCity() {
        val city = _uiState.value.currentCity ?: return
        loadWeather(city.locationKey, forceRefresh = true)
    }

    fun refreshAllCities(forceRefresh: Boolean = false) {
        _uiState.value.cities.forEach { city ->
            loadWeather(city.locationKey, forceRefresh = forceRefresh)
        }
    }

    fun markNetworkUnavailable() {
        _uiState.update { state ->
            val cityKeys = state.cities.mapTo(mutableSetOf(), SavedCity::locationKey)
            state.copy(
                loadingKeys = emptySet(),
                errorsByKey = cityKeys.associateWith { NETWORK_ERROR },
                loadVersions = state.loadVersions + cityKeys.associateWith { key ->
                    (state.loadVersions[key] ?: 0L) + 1L
                },
            )
        }
    }

    fun focusCity(cityKey: String) {
        if (cityKey.isBlank()) return
        pendingFocusKey = cityKey
        _uiState.update { state ->
            val index = state.cities.indexOfFirst { it.locationKey == cityKey }
            if (index < 0 || index == state.currentIndex) state else state.copy(currentIndex = index)
        }
    }

    /** Owns acquisition, city resolution and refresh as one cancellable foreground operation. */
    fun refreshLocation(automatic: Boolean = false) {
        if (locationJob?.isActive == true) return
        val previousCity = _uiState.value.cities.firstOrNull(SavedCity::isLocationCity)
        val now = SystemClock.elapsedRealtime()
        if (automatic && (previousCity == null || !shouldRefreshLocation(lastLocationAttemptMillis, now))) return
        lastLocationAttemptMillis = now
        _uiState.update { it.copy(isLocating = true) }
        locationJob = viewModelScope.launch {
            try {
                val location = locationSource.getCurrentLocation()
                if (location == null) {
                    if (!automatic) locationUnavailable(R.string.weather_location_fix_unavailable)
                    return@launch
                }
                val city = locationResolver.resolve(location).getOrThrow()
                if (cityRepo.setLocationCity(city) == CityRepository.AddResult.LIMIT_EXCEEDED) {
                    if (!automatic) locationUnavailable(R.string.city_count_over_limit)
                    return@launch
                }
                // Room emits asynchronously. Publish the target before starting its weather job,
                // otherwise a fast response could be discarded as belonging to an unknown city.
                _uiState.first { state -> state.cities.any { it.locationKey == city.cityId } }
                if (!automatic) focusCity(city.cityId)
                loadWeather(city.cityId, forceRefresh = !automatic)
            } catch (cancelled: CancellationException) {
                // A stopped screen must not report failure or turn cancellation into a refresh.
                lastLocationAttemptMillis = null
                throw cancelled
            } catch (error: Exception) {
                DebugLog.log(
                    LOCATION_TAG,
                    "Location failed: " +
                        ((error as? LocationCityResolutionException)?.reason?.name
                            ?: error.javaClass.simpleName),
                )
                if (!automatic) {
                    val message = when ((error as? LocationCityResolutionException)?.reason) {
                        LocationCityFailureReason.CITY_SEARCH_FAILED -> R.string.findcity_update_failed_network_unavailable
                        LocationCityFailureReason.CITY_NOT_FOUND,
                        LocationCityFailureReason.INVALID_LOCATION -> R.string.update_location_not_find_city
                        null -> R.string.weather_location_fix_unavailable
                    }
                    locationUnavailable(message)
                }
            } finally {
                _uiState.update { it.copy(isLocating = false) }
                locationJob = null
            }
        }
    }

    fun cancelLocation() {
        locationJob?.cancel()
    }

    fun locationUnavailable(@StringRes message: Int) {
        // A failed coordinate fix must not prevent a user from refreshing existing weather.
        _uiState.value.cities.firstOrNull(SavedCity::isLocationCity)?.let {
            loadWeather(it.locationKey, forceRefresh = true)
        }
        eventChannel.trySend(WeatherEvent.LocationFailed(message))
    }

    /** 切换城市页面 */
    fun setCurrentIndex(index: Int) {
        _uiState.update { state ->
            if (index !in state.cities.indices || index == state.currentIndex) state
            else state.copy(currentIndex = index)
        }
    }

    /** 切换温度单位 */
    fun toggleTempUnit() {
        viewModelScope.launch {
            val newUnit = if (_uiState.value.isCelsius) WeatherSettings.UNIT_FAHRENHEIT else WeatherSettings.UNIT_CELSIUS
            settings.setTempUnit(newUnit)
        }
    }

    /** 删除城市 */
    fun deleteCity(key: String) {
        viewModelScope.launch {
            cityRepo.deleteCity(key)
            _uiState.update { it.copy(weathers = it.weathers - key) }
        }
    }

    /** 城市重排序 */
    fun swapCities(from: Int, to: Int) {
        val cities = _uiState.value.cities
        if (from !in cities.indices || to !in cities.indices) return
        viewModelScope.launch {
            cityRepo.swapSortOrder(cities[from], cities[to])
        }
    }

    private companion object {
        const val LOCATION_TAG = "WeatherLocation"
        const val NETWORK_ERROR = "网络连接不可用"
    }
}
