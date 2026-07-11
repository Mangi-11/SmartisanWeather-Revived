package com.smartisan.weather.ui.search

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smartisan.weather.HotCityView
import com.smartisan.weather.R
import com.smartisan.weather.adapter.SearchContentAdapter
import com.smartisan.weather.bean.SmartisanLocation
import com.smartisan.weather.bean.WeatherSearchBean
import com.smartisan.weather.data.model.SearchResultCity
import com.smartisan.weather.ui.navigation.WeatherTransitionActivity
import com.smartisan.weather.util.Constants
import com.smartisan.weather.util.centeredPhoneContentInsets
import com.smartisan.weather.util.enableWeatherEdgeToEdge
import com.smartisan.weather.util.safeDrawingInsets
import com.smartisan.weather.widget.SearchBar
import kotlinx.coroutines.launch

/** 城市搜索页：原版 XML/View 结构，StateFlow 驱动数据和页面状态。 */
class SearchCityActivity : WeatherTransitionActivity(),
    SearchContentAdapter.OnItemClickListener {

    private val viewModel: SearchViewModel by viewModels()

    private lateinit var searchBar: SearchBar
    private lateinit var searchEditor: EditText
    private lateinit var resultGroup: View
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SearchContentAdapter
    private lateinit var hotCityGroup: View
    private lateinit var hotCityView: HotCityView
    private lateinit var emptyGroup: View
    private lateinit var progress: ProgressBar
    private lateinit var errorGroup: View
    private lateinit var errorTitle: TextView
    private lateinit var errorDescription: TextView
    private lateinit var retryButton: Button

    private var requireCity = false
    private var renderedResults: List<SearchResultCity> = emptyList()
    private var launchCityIds: Set<String> = emptySet()
    private var navigationBarBottom = 0
    private var imeBottom = 0
    private var imeVisible = false
    private var currentQueryBlank = true
    private var initialResultBottomPadding = 0
    private var closing = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            returnLocationRequest()
        } else {
            showPermissionSettingsDialog()
        }
    }

    private val appSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (hasLocationPermission()) returnLocationRequest()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireCity = intent.getBooleanExtra(EXTRA_REQUIRE_CITY, false)
        enableWeatherEdgeToEdge()
        setContentView(R.layout.activity_search_city_layout)

        bindViews()
        applySystemBarInsets()
        setupSearchBar()
        setupHotCities()
        setupResults()
        collectUi()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = closeWithoutSelection()
            },
        )
    }

    private fun applySystemBarInsets() {
        val root = findViewById<View>(R.id.search_city_root)
        val initialLeft = root.paddingLeft
        val initialRight = root.paddingRight
        initialResultBottomPadding = resultGroup.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safeDrawing = insets.safeDrawingInsets()
            navigationBarBottom = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars(),
            ).bottom
            imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val horizontalInsets = view.centeredPhoneContentInsets(safeDrawing)
            view.updatePadding(
                left = initialLeft + horizontalInsets.left,
                right = initialRight + horizontalInsets.right,
            )
            applyResultBottomInset()
            insets
        }
        root.doOnLayout { ViewCompat.requestApplyInsets(it) }
    }

    private fun bindViews() {
        searchBar = findViewById(R.id.search_bar)
        searchEditor = requireNotNull(searchBar.getSearchEditor() as? EditText)
        resultGroup = findViewById(R.id.group_search_result)
        recyclerView = findViewById(R.id.rv_content)
        hotCityGroup = findViewById(R.id.hot_city_group)
        hotCityView = findViewById(R.id.hot_city_parent)
        emptyGroup = findViewById(R.id.empty_group)
        progress = findViewById(R.id.progress_loading)
        errorGroup = findViewById(R.id.error_info_group)
        errorTitle = findViewById(R.id.error_info_tv1)
        errorDescription = findViewById(R.id.error_info_tv2)
        retryButton = findViewById(R.id.btn_refresh)
    }

    private fun setupSearchBar() {
        searchEditor.setHint(R.string.hint_edittext_add_city)
        searchEditor.imeOptions = EditorInfo.IME_ACTION_DONE
        searchEditor.doAfterTextChanged { editable ->
            viewModel.updateQuery(editable?.toString().orEmpty())
        }
        searchEditor.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchBar.hideKeyboard()
                true
            } else {
                false
            }
        }
        searchBar.setOnCancelClickListener(
            object : SearchBar.OnCancelClickListener {
                override fun onClick(view: View?) = closeWithoutSelection()
            }
        )
        searchBar.getClearView()?.setOnClickListener {
            searchEditor.text?.clear()
            searchBar.showKeyboard()
        }
        searchBar.post { searchBar.onClickSearchEditor(false) }
    }

    private fun setupHotCities() {
        val localCityIds = intent.getStringArrayListExtra(
            Constants.WEATHER_SEARCH_CITY_PARAMETER_CITYIDS,
        ) ?: arrayListOf()
        launchCityIds = localCityIds.toSet()
        val currentLocation = IntentCompat.getParcelableExtra(
            intent,
            Constants.WEATHER_SEARCH_CITY_PARAMETER_LOCATION,
            SmartisanLocation::class.java,
        )
        viewModel.setInsertAfterKey(currentLocation?.mLocationKey)
        val location = IntentCompat.getParcelableExtra(
            intent,
            Constants.WEATHER_SEARCH_CITY_LOCATION_CITY,
            SmartisanLocation::class.java,
        )
        hotCityView.initView(localCityIds, location)
        hotCityView.setHotCityItemClickLister(this)
        hotCityGroup.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_MOVE) searchBar.hideKeyboard()
            false
        }
    }

    private fun setupResults() {
        adapter = SearchContentAdapter(this).also {
            it.setOnItemClickListener(this)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) searchBar.hideKeyboard()
                }
            }
        )
        retryButton.setOnClickListener { viewModel.retry() }
    }

    private fun collectUi() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is SearchEvent.CityAdded -> finishWithSelection(event.cityKey)
                            is SearchEvent.ShowMessage -> Toast.makeText(
                                this@SearchCityActivity,
                                event.message,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun render(state: SearchUiState) {
        currentQueryBlank = state.query.isBlank()
        applyResultBottomInset()
        if (searchEditor.text?.toString() != state.query) {
            searchEditor.setText(state.query)
            searchEditor.setSelection(state.query.length)
        }
        adapter.setSearchKey(state.query.trim())
        // Intent 中的快照先于 Room 首次 Flow 返回，合并后可避免首帧短暂把已添加城市
        // 渲染成可点击状态。
        val addedKeys = launchCityIds + state.addedKeys
        adapter.setLocalCityIds(addedKeys)
        hotCityView.updateLocalCityIds(addedKeys)
        if (renderedResults != state.results) {
            renderedResults = state.results
            adapter.submitData(state.results)
        }
        when {
            state.query.isBlank() -> showHotCities()
            state.isLoading && state.results.isEmpty() -> showLoading()
            state.isError && state.results.isEmpty() -> showError()
            state.results.isEmpty() -> showEmptyResult()
            else -> showResults()
        }
    }

    private fun applyResultBottomInset() {
        if (!::resultGroup.isInitialized) return
        val bottomInset = if (!currentQueryBlank && imeVisible) {
            maxOf(navigationBarBottom, imeBottom)
        } else {
            navigationBarBottom
        }
        resultGroup.updatePadding(bottom = initialResultBottomPadding + bottomInset)
    }

    private fun showHotCities() {
        hotCityGroup.isVisible = true
        recyclerView.isVisible = false
        emptyGroup.isVisible = false
    }

    private fun showResults() {
        hotCityGroup.isVisible = false
        recyclerView.isVisible = true
        emptyGroup.isVisible = false
    }

    private fun showLoading() {
        hotCityGroup.isVisible = false
        recyclerView.isVisible = false
        emptyGroup.isVisible = true
        progress.isVisible = true
        errorGroup.isVisible = false
    }

    private fun showEmptyResult() {
        showMessageState(
            title = R.string.weather_search_empty_info1,
            description = R.string.weather_search_empty_info2,
            canRetry = false,
        )
    }

    private fun showError() {
        showMessageState(
            title = R.string.weather_search_no_connect_info,
            description = R.string.weather_search_no_connect_info_tip,
            canRetry = true,
        )
    }

    private fun showMessageState(title: Int, description: Int, canRetry: Boolean) {
        hotCityGroup.isVisible = false
        recyclerView.isVisible = false
        emptyGroup.isVisible = true
        progress.isVisible = false
        errorGroup.isVisible = true
        errorTitle.setText(title)
        errorDescription.setText(description)
        retryButton.isVisible = canRetry
    }

    override fun onItemClick(view: View, position: Int, bean: WeatherSearchBean) {
        if (position == -1) {
            startLocationFlow()
            return
        }
        viewModel.addCity(
            SearchResultCity(
                cityId = bean.cityId.orEmpty(),
                county = bean.county.orEmpty(),
                city = bean.city.orEmpty(),
                province = bean.province.orEmpty(),
                country = bean.country.orEmpty(),
                countyEn = bean.countyEn.orEmpty(),
                id = bean.id.orEmpty(),
            )
        )
    }

    private fun finishWithSelection(cityKey: String) {
        if (closing) return
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(EXTRA_SELECTED_CITY_KEY, cityKey),
        )
        finishAfterKeyboard(useAffinity = false)
    }

    private fun closeWithoutSelection() {
        if (closing) return
        finishAfterKeyboard(useAffinity = requireCity)
    }

    /** Preserve the original SearchBar/IME exit window before the page transition. */
    private fun finishAfterKeyboard(useAffinity: Boolean) {
        closing = true
        searchBar.hideKeyboard()
        searchBar.postDelayed(
            {
                if (useAffinity) finishAffinity() else finish()
            },
            SEARCH_EXIT_DELAY_MILLIS,
        )
    }

    private fun startLocationFlow() {
        if (closing) return
        searchBar.hideKeyboard()
        if (hasLocationPermission()) {
            returnLocationRequest()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /** Search only returns the request; Main owns geocoding and location-city replacement. */
    private fun returnLocationRequest() {
        if (closing) return
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(EXTRA_REQUEST_LOCATION, true),
        )
        finishAfterKeyboard(useAffinity = false)
    }

    private fun showPermissionSettingsDialog() {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle(R.string.weather_request_location_permission_tips_title)
            .setMessage(R.string.weather_request_location_permission_tips_message)
            .setNegativeButton(R.string.weather_request_location_permission_tips_cancel, null)
            .setPositiveButton(R.string.weather_request_location_permission_tips_setting) { _, _ ->
                appSettingsLauncher.launch(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null),
                    )
                )
            }
            .create()
            .apply { setCanceledOnTouchOutside(false) }
            .show()
    }

    companion object {
        const val EXTRA_REQUIRE_CITY = "require_city"
        const val EXTRA_REQUEST_LOCATION = "request_location"
        const val EXTRA_SELECTED_CITY_KEY = "selected_city_key"
        private const val SEARCH_EXIT_DELAY_MILLIS = 100L
    }
}
