package com.smartisan.weather

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.smartisan.weather.appwidget.WeatherWidgetProvider
import com.smartisan.weather.bean.SmartisanLocation
import com.smartisan.weather.data.city.CityRepository
import com.smartisan.weather.data.model.Weather
import com.smartisan.weather.data.network.NetworkMonitor
import com.smartisan.weather.data.settings.WeatherSettings
import com.smartisan.weather.ui.alert.WeatherAlertActivity
import com.smartisan.weather.ui.citylist.CityListActivity
import com.smartisan.weather.ui.main.WeatherUiState
import com.smartisan.weather.ui.main.WeatherEvent
import com.smartisan.weather.ui.main.WeatherViewModel
import com.smartisan.weather.ui.navigation.WeatherEdgeToEdgeActivity
import com.smartisan.weather.ui.navigation.startWeatherActivityForResult
import com.smartisan.weather.ui.search.SearchCityActivity
import com.smartisan.weather.ui.startup.StartupNoticeDialog
import com.smartisan.weather.util.Constants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartisan.weather.ui.main.WeatherScreen
import com.smartisan.weather.ui.navigation.toSearchLocation
import com.smartisan.weather.ui.startup.WeatherLocationDialog

/** Owns permissions, navigation and lifecycle; Compose owns the weather presentation. */
class MainActivity : WeatherEdgeToEdgeActivity() {
    // Accessed only after the local startup notice has been accepted.
    private val viewModel by viewModels<WeatherViewModel>()
    private var weatherStarted by mutableStateOf(false)
    private var showStartupNotice by mutableStateOf(false)
    private var showLocationNotice by mutableStateOf(false)
    private var firstStart = true
    private var initialSearchLaunched = false
    private var initialLocationRequested = false
    private var pendingWidgetCityKey: String? = null
    private var locationRequestJob: Job? = null
    private val settings by lazy(LazyThreadSafetyMode.NONE) { WeatherSettings.getInstance(this) }
    private val networkMonitor by lazy(LazyThreadSafetyMode.NONE) { NetworkMonitor(this) }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            requestCurrentLocation()
        } else {
            viewModel.locationUnavailable(R.string.findcity_update_failed_location_server_unavailable)
        }
    }

    private val locationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val manager = getSystemService(LocationManager::class.java)
        if (manager != null && LocationManagerCompat.isLocationEnabled(manager)) {
            requestCurrentLocation()
        } else {
            viewModel.locationUnavailable(R.string.findcity_update_failed_location_server_unavailable)
        }
    }

    private val searchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data
        if (data?.getBooleanExtra(SearchCityActivity.EXTRA_REQUEST_LOCATION, false) == true) {
            initialSearchLaunched = false
            startLocationFlow()
        } else {
            data?.getStringExtra(SearchCityActivity.EXTRA_SELECTED_CITY_KEY)?.let(viewModel::focusCity)
        }
    }

    private val cityListLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (
            result.resultCode == Activity.RESULT_OK &&
            result.data?.getBooleanExtra(SearchCityActivity.EXTRA_REQUEST_LOCATION, false) == true
        ) {
            startLocationFlow()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialSearchLaunched = savedInstanceState?.getBoolean("initialSearchLaunched") ?: false
        initialLocationRequested = savedInstanceState?.getBoolean("initialLocationRequested") ?: false
        pendingWidgetCityKey = intent.getStringExtra(WeatherWidgetProvider.EXTRA_CITY_KEY)
        setContent {
            if (weatherStarted) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                WeatherScreen(
                    state = state,
                    onSelectCity = viewModel::setCurrentIndex,
                    onRefresh = ::refreshCurrentCity,
                    onToggleUnit = viewModel::toggleTempUnit,
                    onAddCity = ::addCity,
                    onManageCities = ::openCityList,
                    onOpenAlerts = { _, weather -> openAlerts(weather) },
                    onLocate = ::startLocationFlow,
                    onOpenSource = ::openSource,
                )
                LaunchedEffect(state.citiesLoaded, state.cities.isEmpty()) {
                    if (state.citiesLoaded && state.cities.isEmpty() && !initialLocationRequested) {
                        initialLocationRequested = true
                        startLocationFlow()
                    }
                }
            } else {
                WeatherScreen(
                    state = WeatherUiState(),
                    onSelectCity = {}, onRefresh = {}, onToggleUnit = {}, onAddCity = {},
                    onManageCities = {}, onOpenAlerts = { _, _ -> }, onLocate = {}, onOpenSource = {},
                )
            }
            if (showStartupNotice) StartupNoticeDialog(
                onContinue = {
                    lifecycleScope.launch {
                        settings.setStartupNoticeAccepted(true)
                        showStartupNotice = false
                        startWeather()
                    }
                },
                onExit = ::finish,
            )
            if (showLocationNotice) WeatherLocationDialog(
                onCancel = {
                    showLocationNotice = false
                    viewModel.locationUnavailable(R.string.findcity_update_failed_location_server_unavailable)
                },
                onSettings = {
                    showLocationNotice = false
                    locationSettingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                },
            )
        }
        lifecycleScope.launch {
            if (settings.startupNoticeAccepted.first()) startWeather() else showStartupNotice = true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("initialSearchLaunched", initialSearchLaunched)
        outState.putBoolean("initialLocationRequested", initialLocationRequested)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        if (weatherStarted && !firstStart) viewModel.refreshAllCities()
        firstStart = false
    }

    override fun onDestroy() {
        locationRequestJob?.cancel()
        super.onDestroy()
    }

    private fun startWeather() {
        if (weatherStarted || isFinishing || isDestroyed) return
        pendingWidgetCityKey?.let(viewModel::focusCity)
        pendingWidgetCityKey = null
        weatherStarted = true
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.events.collect(::handleWeatherEvent) }
                launch {
                    var previousOnline: Boolean? = null
                    networkMonitor.isOnline.collect { online ->
                        when {
                            previousOnline == true && !online -> viewModel.markNetworkUnavailable()
                            previousOnline == false && online -> viewModel.refreshAllCities(forceRefresh = true)
                        }
                        previousOnline = online
                    }
                }
            }
        }
    }

    private fun handleWeatherEvent(event: WeatherEvent) {
        when (event) {
            is WeatherEvent.LocationUpdated -> viewModel.focusCity(event.cityKey)
            is WeatherEvent.LocationFailed -> {
                Toast.makeText(this, event.message, Toast.LENGTH_SHORT).show()
                if (viewModel.uiState.value.cities.isEmpty()) launchRequiredSearch()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(WeatherWidgetProvider.EXTRA_CITY_KEY)?.takeIf(String::isNotBlank)?.let {
            if (weatherStarted) viewModel.focusCity(it) else pendingWidgetCityKey = it
            return
        }
        if (weatherStarted && intent.getStringExtra(Constants.WEATHER_LAUNCH_PARAM) ==
            Constants.WEATHER_LUNCH_SOURCE_LAUNCHER_CARD
        ) viewModel.setCurrentIndex(0)
    }

    private fun openAlerts(weather: Weather) {
        if (weather.alert.isEmpty) return
        startActivity(Intent(this, WeatherAlertActivity::class.java).putExtra(WeatherAlertActivity.EXTRA_ALERT, weather.alert))
    }

    private fun openCityList() {
        startWeatherActivityForResult(cityListLauncher, Intent(this, CityListActivity::class.java))
    }

    private fun openSource(url: String) {
        val destination = url.takeIf { Uri.parse(it).scheme.equals("https", ignoreCase = true) } ?: Constants.PARNTER_URL
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(destination))
        // ACTION_VIEW can launch a browser even when package visibility hides it from queries.
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.weather_no_browser, Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshCurrentCity() {
        val city = viewModel.uiState.value.currentCity ?: return
        if (city.isLocationCity) startLocationFlow() else viewModel.refreshCurrentCity()
    }

    private fun addCity() {
        if (viewModel.uiState.value.cities.size >= CityRepository.MAX_CITIES) {
            Toast.makeText(this, R.string.city_count_over_limit, Toast.LENGTH_SHORT).show()
            return
        }
        startSearchActivity(requireCity = false, currentLocation = viewModel.uiState.value.currentCity?.toSearchLocation())
    }

    private fun startSearchActivity(requireCity: Boolean, currentLocation: SmartisanLocation? = null) {
        val state = viewModel.uiState.value
        val intent = Intent(this, SearchCityActivity::class.java).apply {
            putExtra(SearchCityActivity.EXTRA_REQUIRE_CITY, requireCity)
            putExtra(Constants.WEATHER_SEARCH_CITY_PARAMETER_LOCATION, currentLocation)
            putStringArrayListExtra(Constants.WEATHER_SEARCH_CITY_PARAMETER_CITYIDS, ArrayList(state.cities.map { it.locationKey }))
            state.cities.firstOrNull { it.isLocationCity }?.let {
                putExtra(Constants.WEATHER_SEARCH_CITY_LOCATION_CITY, it.toSearchLocation())
            }
        }
        startWeatherActivityForResult(searchLauncher, intent)
    }

    private fun launchRequiredSearch() {
        if (initialSearchLaunched || isFinishing || isDestroyed) return
        initialSearchLaunched = true
        startSearchActivity(requireCity = true)
    }

    private fun startLocationFlow() {
        if (viewModel.uiState.value.isLocating || locationRequestJob?.isActive == true) return
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            return
        }
        requestCurrentLocation()
    }

    @SuppressLint("MissingPermission")
    private fun requestCurrentLocation() {
        if (locationRequestJob?.isActive == true) return
        val manager = getSystemService(LocationManager::class.java)
        if (manager == null || !LocationManagerCompat.isLocationEnabled(manager)) {
            showLocationSettingsDialog()
            return
        }
        if (availableLocationProviders(manager).isEmpty()) {
            viewModel.locationUnavailable(
                R.string.findcity_update_failed_location_server_unavailable,
            )
            return
        }

        locationRequestJob = lifecycleScope.launch {
            try {
                val location = getCurrentLocation(manager)
                if (location == null) {
                    viewModel.locationUnavailable(
                        R.string.findcity_update_failed_location_server_unavailable,
                    )
                } else {
                    viewModel.resolveLocation(location)
                }
            } finally {
                locationRequestJob = null
            }
        }
    }

    private fun availableLocationProviders(manager: LocationManager): List<String> {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        return candidates.filter { provider ->
            provider in manager.allProviders && runCatching {
                manager.isProviderEnabled(provider)
            }.getOrDefault(false)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(manager: LocationManager): Location? =
        withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MILLIS) {
            val providers = availableLocationProviders(manager)
            providers.mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }.filter(::isFreshLocation).maxByOrNull(Location::getElapsedRealtimeNanos)?.let {
                return@withTimeoutOrNull it
            }
            providers.firstNotNullOfOrNull { provider ->
                withTimeoutOrNull(PROVIDER_LOCATION_TIMEOUT_MILLIS) {
                    requestProviderLocation(manager, provider)
                }
            }
        }

    private fun isFreshLocation(location: Location): Boolean {
        val ageNanos = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
        return ageNanos in 0..MAX_LAST_LOCATION_AGE_NANOS
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestProviderLocation(
        manager: LocationManager,
        provider: String,
    ): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(
                    manager,
                    provider,
                    cancellationSignal,
                    ContextCompat.getMainExecutor(this@MainActivity),
                ) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } catch (_: RuntimeException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private fun showLocationSettingsDialog() {
        if (!isFinishing && !isDestroyed) showLocationNotice = true
    }

    private companion object {
        const val CURRENT_LOCATION_TIMEOUT_MILLIS = 20_000L
        const val PROVIDER_LOCATION_TIMEOUT_MILLIS = 6_000L
        const val MAX_LAST_LOCATION_AGE_NANOS = 5L * 60L * 1_000_000_000L
    }
}
