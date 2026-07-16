package com.smartisan.weather

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
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
import com.smartisan.weather.ui.main.toLegacyDrawItems
import com.smartisan.weather.ui.navigation.startWeatherActivityForResult
import com.smartisan.weather.ui.privacy.PrivacyConsentDialog
import com.smartisan.weather.ui.search.SearchCityActivity
import com.smartisan.weather.util.Constants
import com.smartisan.weather.util.centeredPhoneContentInsets
import com.smartisan.weather.util.enableWeatherEdgeToEdge
import com.smartisan.weather.util.safeDrawingInsets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 主天气页宿主。
 *
 * UI 直接使用原版 XML 和自定义 View；现代 Android 层只负责生命周期、Insets 和把
 * [WeatherViewModel] 的不可变状态增量同步给 [WeatherGroupContainer]。
 */
class MainActivity : ComponentActivity(), AbstractController {

    private val viewModel by viewModels<WeatherViewModel>()

    private lateinit var container: WeatherGroupContainer
    private lateinit var statusBarScrim: View
    private var renderedState: WeatherUiState? = null
    private var firstStart = true
    private var initialSearchLaunched = false
    private var initialLocationRequested = false
    private var weatherStarted = false
    private var pendingWidgetCityKey: String? = null
    private var locationRequestJob: Job? = null
    private var privacyDialog: PrivacyConsentDialog? = null
    private val settings by lazy(LazyThreadSafetyMode.NONE) {
        WeatherSettings.getInstance(this)
    }
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
        enableWeatherEdgeToEdge()
        setContentView(R.layout.activity_main)
        pendingWidgetCityKey = intent.getStringExtra(WeatherWidgetProvider.EXTRA_CITY_KEY)

        container = findViewById(R.id.group_container)
        statusBarScrim = findViewById(R.id.status_bar_scrim)
        container.setBgView(findViewById<ImageView>(R.id.group_forecast_bg))
        container.setController(this)
        container.setOnBackgroundChangedListener(::updateStatusBarScrim)
        container.setOnCurrentItemChangedListener { index ->
            viewModel.setCurrentIndex(index)
        }

        // 天气背景和原版纯色保护层铺进状态栏，只移动前景交互内容。
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val safeDrawing = insets.safeDrawingInsets()
            val horizontalInsets = view.centeredPhoneContentInsets(safeDrawing)
            view.updatePadding(
                left = horizontalInsets.left,
                top = safeDrawing.top,
                right = horizontalInsets.right,
                bottom = safeDrawing.bottom,
            )
            statusBarScrim.layoutParams = statusBarScrim.layoutParams.apply {
                height = safeDrawing.top
            }
            insets
        }
        ViewCompat.requestApplyInsets(container)
        container.doOnLayout { ViewCompat.requestApplyInsets(it) }

        lifecycleScope.launch {
            if (settings.privacyAccepted.first()) {
                startWeather()
            } else {
                showPrivacyConsent()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (weatherStarted && !firstStart && container.canRefreshPageData()) {
            viewModel.refreshAllCities()
        }
        firstStart = false
    }

    override fun onResume() {
        super.onResume()
        container.resetRefreshPageState(true)
    }

    override fun onPause() {
        container.resetPageState()
        super.onPause()
    }

    override fun onDestroy() {
        locationRequestJob?.cancel()
        privacyDialog?.dismiss()
        privacyDialog = null
        container.setOnCurrentItemChangedListener(null)
        container.setOnBackgroundChangedListener(null)
        super.onDestroy()
    }

    private fun showPrivacyConsent() {
        if (isFinishing || isDestroyed || privacyDialog?.isShowing == true) return
        privacyDialog = PrivacyConsentDialog(this) {
            lifecycleScope.launch {
                settings.setPrivacyAccepted(true)
                startWeather()
            }
        }.also(PrivacyConsentDialog::show)
    }

    private fun startWeather() {
        if (weatherStarted || isFinishing || isDestroyed) return
        weatherStarted = true
        pendingWidgetCityKey?.let(viewModel::focusCity)
        pendingWidgetCityKey = null
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.events.collect(::handleWeatherEvent) }
                launch {
                    var previousOnline: Boolean? = null
                    networkMonitor.isOnline.collect { online ->
                        when {
                            previousOnline == true && !online -> {
                                viewModel.markNetworkUnavailable()
                                viewModel.uiState.value.cities.forEach { city ->
                                    container.setLoadType(city.locationKey, 2)
                                }
                            }

                            previousOnline == false && online -> {
                                viewModel.uiState.value.cities.forEach { city ->
                                    container.setLoadType(city.locationKey, 1)
                                }
                                viewModel.refreshAllCities()
                            }
                        }
                        previousOnline = online
                    }
                }
            }
        }
    }

    private fun handleWeatherEvent(event: WeatherEvent) {
        when (event) {
            is WeatherEvent.LocationUpdated -> {
                if (viewModel.uiState.value.cities.isNotEmpty()) {
                    container.getCurrentView().stopRefresh(null)
                }
                viewModel.focusCity(event.cityKey)
            }

            is WeatherEvent.LocationFailed -> {
                if (viewModel.uiState.value.cities.isNotEmpty()) {
                    container.getCurrentView().stopRefresh(null)
                }
                Toast.makeText(this, event.message, Toast.LENGTH_SHORT).show()
                if (viewModel.uiState.value.cities.isEmpty()) launchRequiredSearch()
            }
        }
    }

    /** Recreates the original solid weather status-bar surface behind the transparent system bar. */
    private fun updateStatusBarScrim(backgroundRes: Int) {
        val colorRes = when (backgroundRes) {
            R.drawable.drawable_weather_bg_foggy -> R.color.weather_bg_color_end_foggy
            R.drawable.drawable_weather_bg_haze -> R.color.weather_bg_color_end_haze
            R.drawable.drawable_weather_bg_rain -> R.color.weather_bg_color_end_rain
            R.drawable.drawable_weather_bg_sandstorm -> R.color.weather_bg_color_end_standstorm
            R.drawable.drawable_weather_bg_snow -> R.color.weather_bg_color_end_snow
            else -> R.color.weather_bg_color_end_sunny
        }
        statusBarScrim.setBackgroundColor(ContextCompat.getColor(this, colorRes))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(WeatherWidgetProvider.EXTRA_CITY_KEY)
            ?.takeIf(String::isNotBlank)
            ?.let { cityKey ->
                if (weatherStarted) {
                    viewModel.focusCity(cityKey)
                } else {
                    pendingWidgetCityKey = cityKey
                }
                return
            }
        if (intent.getStringExtra(Constants.WEATHER_LAUNCH_PARAM) ==
            Constants.WEATHER_LUNCH_SOURCE_LAUNCHER_CARD
        ) {
            container.setCurrentItem(0)
        }
    }

    private fun render(state: WeatherUiState) {
        val previous = renderedState
        val items = state.toLegacyDrawItems()
        val structureChanged = previous == null || previous.cities != state.cities

        if (structureChanged) {
            val currentKey = container.getCurrentCityId()
            container.initDatas(items)
            if (items.isNotEmpty()) {
                val preservedIndex = state.cities.indexOfFirst { it.locationKey == currentKey }
                val targetIndex = preservedIndex
                    .takeIf { it >= 0 }
                    ?: state.currentIndex.coerceIn(state.cities.indices)
                container.setCurrentItem(targetIndex)
            }
        } else {
            state.cities.forEachIndexed { index, city ->
                val oldWeather = previous.weathers[city.locationKey]
                val newWeather = state.weathers[city.locationKey]
                val loadCompleted = previous.loadVersions[city.locationKey] !=
                    state.loadVersions[city.locationKey]
                if (oldWeather != newWeather || loadCompleted) {
                    val succeeded = newWeather?.isComplete == true &&
                        state.errorsByKey[city.locationKey] == null
                    container.replaceData(
                        success = succeeded,
                        locationKey = city.locationKey,
                        drawItem = items[index].takeIf { succeeded },
                    )
                }
            }
            if (state.cities.isNotEmpty() && container.getCurrentItem() != state.currentIndex) {
                container.setCurrentItem(state.currentIndex.coerceIn(state.cities.indices))
            }
        }

        val currentKey = state.currentCity?.locationKey
        if (currentKey != null && state.currentWeather == null) {
            when {
                state.isLoading -> container.setLoadType(currentKey, 1)
                state.error != null -> container.setLoadType(currentKey, 2)
            }
        }

        if (previous != null && previous.isCelsius != state.isCelsius) {
            container.onTempChange()
        }

        renderedState = state

        if (state.citiesLoaded && state.cities.isEmpty() && !initialLocationRequested) {
            initialLocationRequested = true
            container.post {
                startLocationFlow()
            }
        }
    }

    override fun openAlerDetailPage() {
        container.resetRefreshPageState(false)
        val weather = currentWeather() ?: return
        if (weather.alert.isEmpty) return
        startActivity(
            Intent(this, WeatherAlertActivity::class.java)
                .putExtra(WeatherAlertActivity.EXTRA_ALERT, weather.alert),
        )
    }

    override fun openCityLisPage() {
        container.resetRefreshPageState(false)
        startWeatherActivityForResult(
            cityListLauncher,
            Intent(this, CityListActivity::class.java),
        )
    }

    override fun openParnterDetailPage() {
        container.resetRefreshPageState(false)
        val item = container.getData().getOrNull(container.getCurrentItem())
        val location = item?.locationData
        val url = if (location?.mLocationKey.isNullOrBlank()) {
            Constants.PARNTER_URL
        } else {
            buildString {
                append(Constants.PARNTER_URL)
                append("?cityId=")
                append(Uri.encode(location.mLocationKey))
                append("&area=")
                append(Uri.encode(location.mLocationName.orEmpty()))
            }
        }
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    override fun refreshCurrentCity() {
        val city = viewModel.uiState.value.currentCity
        if (city == null) {
            return
        } else if (city.isLocationCity) {
            startLocationFlow()
        } else {
            viewModel.loadWeather(city.locationKey)
        }
    }

    override fun setScrollale(z: Boolean) {
        container.setScrollale(z)
    }

    override fun startAddCity(smartisanLocation: SmartisanLocation) {
        if (viewModel.uiState.value.cities.size >= CityRepository.MAX_CITIES) {
            Toast.makeText(this, R.string.city_count_over_limit, Toast.LENGTH_SHORT).show()
            return
        }
        container.resetRefreshPageState(false)
        startSearchActivity(requireCity = false, currentLocation = smartisanLocation)
    }

    private fun startSearchActivity(
        requireCity: Boolean,
        currentLocation: SmartisanLocation? = null,
    ) {
        val state = viewModel.uiState.value
        val intent = Intent(this, SearchCityActivity::class.java).apply {
            putExtra(SearchCityActivity.EXTRA_REQUIRE_CITY, requireCity)
            putExtra(Constants.WEATHER_SEARCH_CITY_PARAMETER_LOCATION, currentLocation)
            putStringArrayListExtra(
                Constants.WEATHER_SEARCH_CITY_PARAMETER_CITYIDS,
                ArrayList(state.cities.map { it.locationKey }),
            )
            state.cities.firstOrNull { it.isLocationCity }?.let { locationCity ->
                putExtra(
                    Constants.WEATHER_SEARCH_CITY_LOCATION_CITY,
                    itemsLocation(locationCity.locationKey),
                )
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
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle(R.string.location_server_unavailable)
            .setMessage(R.string.whether_open_location_server)
            .setNegativeButton(R.string.weather_request_location_permission_tips_cancel) { _, _ ->
                viewModel.locationUnavailable(
                    R.string.findcity_update_failed_location_server_unavailable,
                )
            }
            .setPositiveButton(R.string.weather_request_location_permission_tips_setting) { _, _ ->
                locationSettingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .create()
            .apply { setCanceledOnTouchOutside(false) }
            .show()
    }

    private fun itemsLocation(locationKey: String): SmartisanLocation? =
        container.getData()
            .firstOrNull { it.locationData?.mLocationKey == locationKey }
            ?.locationData

    private fun currentWeather(): Weather? {
        val state = viewModel.uiState.value
        return state.weathers[container.getCurrentCityId()]
    }

    private companion object {
        const val CURRENT_LOCATION_TIMEOUT_MILLIS = 20_000L
        const val PROVIDER_LOCATION_TIMEOUT_MILLIS = 6_000L
        const val MAX_LAST_LOCATION_AGE_NANOS = 5L * 60L * 1_000_000_000L
    }
}
