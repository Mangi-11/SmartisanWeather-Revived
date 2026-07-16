package com.smartisan.weather.ui.citylist

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.smartisan.weather.R
import com.smartisan.weather.bean.SmartisanLocation
import com.smartisan.weather.custom.DragSortListView
import com.smartisan.weather.custom.WeatherListView
import com.smartisan.weather.data.city.CityRepository
import com.smartisan.weather.data.model.SavedCity
import com.smartisan.weather.ui.navigation.WeatherTransitionActivity
import com.smartisan.weather.ui.navigation.startWeatherActivityForResult
import com.smartisan.weather.ui.search.SearchCityActivity
import com.smartisan.weather.util.Constants
import com.smartisan.weather.util.centeredPhoneContentInsets
import com.smartisan.weather.util.enableWeatherEdgeToEdge
import com.smartisan.weather.util.safeDrawingInsets
import com.smartisan.weather.widget.MenuDialog
import com.smartisan.weather.widget.TitleBar
import kotlinx.coroutines.launch

/** 城市管理页：直接承载原版 XML/View。 */
class CityListActivity : WeatherTransitionActivity() {

    private val viewModel: CityListViewModel by viewModels()

    private lateinit var listView: WeatherListView
    private lateinit var listAdapter: CityListAdapter
    private lateinit var titleBar: TitleBar
    private lateinit var addButton: ImageView
    private lateinit var doneButton: ImageView
    private var latestState = CityListUiState()
    private var deleteDialog: MenuDialog? = null
    private var pendingDeletePosition: Int = -1

    private val searchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (
            result.resultCode == Activity.RESULT_OK &&
            data?.getBooleanExtra(SearchCityActivity.EXTRA_REQUEST_LOCATION, false) == true
        ) {
            setResult(Activity.RESULT_OK, data)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableWeatherEdgeToEdge()
        setContentView(R.layout.activity_city_list)

        setupTitleBar()
        setupCityList()
        applySystemBarInsets(findViewById(R.id.city_list_root))
        collectState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshCachedWeather()
    }

    override fun onDestroy() {
        deleteDialog?.dismiss()
        deleteDialog = null
        super.onDestroy()
    }

    private fun setupTitleBar() {
        titleBar = findViewById(R.id.title_bar)
        titleBar.setShadowVisible(false)
        titleBar.setCenterText(R.string.city_list)

        addButton = titleBar.addLeftImageView(R.drawable.standard_icon_common_add_selector).apply {
            contentDescription = getString(R.string.add_city)
            setOnClickListener { startAddCity() }
        }
        doneButton = titleBar.addRightImageView(R.drawable.standard_icon_complete_selector).apply {
            contentDescription = getString(R.string.complete)
            setOnClickListener {
                persistOrderAndFinish()
            }
        }
    }

    private fun startAddCity() {
        val cities = listAdapter.currentCities()
        if (cities.size >= CityRepository.MAX_CITIES) {
            Toast.makeText(this, R.string.city_count_over_limit, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, SearchCityActivity::class.java).apply {
            putStringArrayListExtra(
                Constants.WEATHER_SEARCH_CITY_PARAMETER_CITYIDS,
                ArrayList(cities.map(SavedCity::locationKey)),
            )
            cities.maxByOrNull(SavedCity::sortOrder)?.toSmartisanLocation()?.let {
                putExtra(Constants.WEATHER_SEARCH_CITY_PARAMETER_LOCATION, it)
            }
            cities.firstOrNull(SavedCity::isLocationCity)?.toSmartisanLocation()?.let {
                putExtra(Constants.WEATHER_SEARCH_CITY_LOCATION_CITY, it)
            }
        }
        startWeatherActivityForResult(searchLauncher, intent)
    }

    private fun SavedCity.toSmartisanLocation(): SmartisanLocation = SmartisanLocation(
        locationKey = locationKey,
        locationName = locationName,
        locationParentName = locationParentName,
        province = province,
        country = country,
    ).also { location ->
        location.id = id
        location.sortOrder = sortOrder
    }

    private fun setupCityList() {
        listView = findViewById(R.id.dragsortlistview)

        listAdapter = CityListAdapter(this, ::requestDelete)
        listView.adapter = listAdapter
        listView.setDragPositionValidator { position ->
            position in 0 until listAdapter.count && !listAdapter.getItem(position).isLocationCity
        }
        listView.setDragSortListener(object : DragSortListView.DragSortListener {
            override fun onMove(from: Int, to: Int) {
                listAdapter.moveDrag(from, to)
            }

            override fun onDrop(from: Int, to: Int) {
                listAdapter.finishDrag()
            }

            override fun onCancel(from: Int, to: Int) {
                listAdapter.cancelDrag()
            }
        })
        listView.setOnDragEventListener(object : WeatherListView.OnDragEventListener {
            override fun onStartDrag(position: Int) {
                listAdapter.beginDrag(position)
                vibrateDragStart()
                setAllViewsClickable(false)
            }

            override fun onStopDrag() {
                setAllViewsClickable(true)
            }
        })
    }

    private fun collectState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    latestState = state
                    listAdapter.submitState(state)
                }
            }
        }
    }

    private fun applySystemBarInsets(root: View) {
        val baseRootPaddingLeft = root.paddingLeft
        val baseRootPaddingRight = root.paddingRight
        val baseListPaddingBottom = listView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.safeDrawingInsets()
            val horizontalInsets = root.centeredPhoneContentInsets(bars)
            root.updatePadding(
                left = baseRootPaddingLeft + horizontalInsets.left,
                right = baseRootPaddingRight + horizontalInsets.right,
            )
            listView.updatePadding(bottom = baseListPaddingBottom + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        root.doOnLayout { ViewCompat.requestApplyInsets(it) }
    }

    private fun requestDelete(city: SavedCity, position: Int) {
        if (latestState.cities.size <= 1) {
            Toast.makeText(
                this,
                R.string.weather_city_list_delete_last_city_tips,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        pendingDeletePosition = position
        listView.setFocusView(position)
        viewModel.showDeleteConfirm(city)
        showDeleteDialog()
    }

    private fun showDeleteDialog() {
        val dialog = deleteDialog ?: MenuDialog(this).also { created ->
            created.setTitle(R.string.whether_delete_city)
            created.setPositiveButton(R.string.delete_city) {
                confirmDelete()
            }
            created.setNegativeButton {
                clearPendingDelete()
                created.cancel()
            }
            created.setOnCancelListener {
                clearPendingDelete()
            }
            deleteDialog = created
        }
        dialog.show()
    }

    private fun confirmDelete() {
        val city = latestState.showDeleteConfirm ?: return
        if (latestState.cities.size <= 1) {
            Toast.makeText(
                this,
                R.string.weather_city_list_delete_last_city_tips,
                Toast.LENGTH_SHORT,
            ).show()
            clearPendingDelete()
            return
        }

        deleteDialog?.dismiss()
        listView.setFocusView(pendingDeletePosition)
        listView.playDeleteItemAnimation(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
                setAllViewsClickable(false)
                listView.setDragEnabled(false)
            }

            override fun onAnimationRepeat(animation: Animation?) = Unit

            override fun onAnimationEnd(animation: Animation?) {
                lifecycleScope.launch {
                    val result = viewModel.deleteCity(city)
                    when {
                        result.getOrNull() == true -> {
                            listAdapter.removeCity(city.locationKey)
                            pendingDeletePosition = -1
                            listView.setFocusView(null)
                        }

                        result.getOrNull() == false -> {
                            listAdapter.notifyDataSetChanged()
                            clearPendingDelete()
                            Toast.makeText(
                                this@CityListActivity,
                                R.string.weather_city_list_delete_last_city_tips,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }

                        else -> {
                            listAdapter.notifyDataSetChanged()
                            clearPendingDelete()
                            Toast.makeText(
                                this@CityListActivity,
                                R.string.city_delete_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                    listView.setDragEnabled(true)
                    setAllViewsClickable(true)
                }
            }
        })
    }

    private fun clearPendingDelete() {
        pendingDeletePosition = -1
        listView.setFocusView(null)
        viewModel.dismissDeleteConfirm()
    }

    private fun persistOrderAndFinish() {
        setAllViewsClickable(false)
        lifecycleScope.launch {
            val result = viewModel.persistCityOrder(listAdapter.currentCities())
            if (result.isSuccess) {
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                setAllViewsClickable(true)
                Toast.makeText(
                    this@CityListActivity,
                    R.string.city_order_save_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun setAllViewsClickable(clickable: Boolean) {
        titleBar.isClickable = clickable
        addButton.isEnabled = clickable
        doneButton.isEnabled = clickable
        listAdapter.setItemClickable(clickable)
    }

    private fun vibrateDragStart() {
        if (
            listView.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.GESTURE_START
                } else {
                    HapticFeedbackConstants.LONG_PRESS
                },
            )
        ) {
            return
        }
        val vibrator = getSystemService(Vibrator::class.java)
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(
            VibrationEffect.createOneShot(
                DRAG_START_VIBRATION_MILLIS,
                VibrationEffect.DEFAULT_AMPLITUDE,
            ),
        )
    }

    private companion object {
        const val DRAG_START_VIBRATION_MILLIS = 30L
    }
}
