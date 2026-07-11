package com.smartisan.weather.ui.search

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartisan.weather.R
import com.smartisan.weather.data.city.CityRepository
import com.smartisan.weather.data.model.HotCity
import com.smartisan.weather.data.model.SearchResultCity
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResultCity> = emptyList(),
    val hotCities: List<HotCity> = CityRepository.defaultHotCities,
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val addedKeys: Set<String> = emptySet(),
)

sealed interface SearchEvent {
    data class CityAdded(val cityKey: String) : SearchEvent
    data class ShowMessage(@param:StringRes val message: Int) : SearchEvent
}

class SearchViewModel(app: Application) : AndroidViewModel(app) {
    private val cityRepository = CityRepository(app)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<SearchEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var searchJob: Job? = null
    private var insertAfterKey: String? = null
    private val addingKeys = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            val keys = runCatching { cityRepository.getAllCities() }
                .getOrDefault(emptyList())
                .mapTo(mutableSetOf()) { it.locationKey }
            _uiState.update { it.copy(addedKeys = keys) }
        }
    }

    fun updateQuery(text: String) {
        if (text == _uiState.value.query) return
        searchJob?.cancel()
        if (text.isBlank()) {
            _uiState.update {
                it.copy(
                    query = text,
                    results = emptyList(),
                    isLoading = false,
                    isError = false,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                query = text,
                results = emptyList(),
                isLoading = true,
                isError = false,
            )
        }
        // 原版为输入即搜；取消上一请求后立即启动当前关键字请求。
        searchJob = viewModelScope.launch { search(text.trim()) }
    }

    fun retry() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty() || _uiState.value.isLoading) return
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                results = emptyList(),
                isLoading = true,
                isError = false,
            )
        }
        searchJob = viewModelScope.launch { search(query) }
    }

    fun addCity(city: SearchResultCity) {
        addCityInternal(city)
    }

    fun setInsertAfterKey(cityKey: String?) {
        insertAfterKey = cityKey?.takeIf(String::isNotBlank)
    }

    fun addHotCity(city: HotCity) {
        addCityInternal(
            SearchResultCity(
                cityId = city.cityId,
                county = city.county,
                city = city.city,
                province = city.province,
                country = city.country,
            )
        )
    }

    private suspend fun search(query: String) {
        val result = cityRepository.searchCitiesResult(query)
        if (_uiState.value.query.trim() != query) return

        result.fold(
            onSuccess = { items ->
                _uiState.update { state ->
                    state.copy(
                        results = items.distinctBy { it.cityId },
                        isLoading = false,
                        isError = false,
                    )
                }
            },
            onFailure = {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        isError = true,
                    )
                }
            },
        )
    }

    private fun addCityInternal(city: SearchResultCity) {
        val key = city.cityId
        if (key.isBlank() || key in addingKeys) return
        if (key in _uiState.value.addedKeys) {
            eventChannel.trySend(SearchEvent.ShowMessage(R.string.weather_add_city_alread))
            return
        }

        addingKeys += key
        viewModelScope.launch {
            try {
                when (cityRepository.addCity(city, insertAfterKey)) {
                    CityRepository.AddResult.SUCCESS -> {
                        _uiState.update { it.copy(addedKeys = it.addedKeys + key) }
                        eventChannel.send(SearchEvent.CityAdded(key))
                    }

                    CityRepository.AddResult.ALREADY_EXISTS -> {
                        _uiState.update { it.copy(addedKeys = it.addedKeys + key) }
                        eventChannel.send(
                            SearchEvent.ShowMessage(R.string.weather_add_city_alread)
                        )
                    }

                    CityRepository.AddResult.LIMIT_EXCEEDED -> {
                        eventChannel.send(
                            SearchEvent.ShowMessage(R.string.city_count_over_limit)
                        )
                    }
                }
            } finally {
                addingKeys -= key
            }
        }
    }
}
