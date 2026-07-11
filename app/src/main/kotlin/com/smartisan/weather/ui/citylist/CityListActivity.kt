package com.smartisan.weather.ui.citylist

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.widget.AbsListView
import android.widget.ImageView
import android.widget.TextView
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
import com.smartisan.weather.util.Utility
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
    private lateinit var sourceGroup: View
    private lateinit var footerSource: View
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
        titleBar.getTitleView().apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
            paint.isFakeBoldText = false
        }

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
        sourceGroup = findViewById(R.id.source_group)
        findViewById<TextView>(R.id.source_logo).text = Utility.getParnterText(this)

        footerSource = layoutInflater.inflate(R.layout.footer_source_logo, listView, false).apply {
            findViewById<TextView>(R.id.source_logo).text = Utility.getParnterText(this@CityListActivity)
        }
        listView.addFooterView(footerSource, null, false)
        listView.setFooterDividersEnabled(false)

        listAdapter = CityListAdapter(this, ::requestDelete)
        listView.adapter = listAdapter
        listView.setDragPositionValidator { position ->
            position in 0 until listAdapter.count && !listAdapter.getItem(position).isLocationCity
        }
        listView.setDragSortListener(object : DragSortListView.DragSortListener {
            override fun onMove(from: Int, to: Int) {
                listAdapter.moveDrag(from, to)
                listView.post(::updateSourcePlacement)
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
        listView.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) = Unit

            override fun onScroll(
                view: AbsListView?,
                firstVisibleItem: Int,
                visibleItemCount: Int,
                totalItemCount: Int,
            ) {
                updateSourcePlacement()
            }
        })
    }

    private fun collectState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    latestState = state
                    listAdapter.submitState(state)
                    listView.post(::updateSourcePlacement)
                }
            }
        }
    }

    private fun applySystemBarInsets(root: View) {
        val baseRootPaddingLeft = root.paddingLeft
        val baseRootPaddingRight = root.paddingRight
        val baseListPaddingBottom = listView.paddingBottom
        val sourceParams = sourceGroup.layoutParams as ViewGroup.MarginLayoutParams
        val baseSourceMarginBottom = sourceParams.bottomMargin

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.safeDrawingInsets()
            val horizontalInsets = root.centeredPhoneContentInsets(bars)
            root.updatePadding(
                left = baseRootPaddingLeft + horizontalInsets.left,
                right = baseRootPaddingRight + horizontalInsets.right,
            )
            listView.updatePadding(bottom = baseListPaddingBottom + bars.bottom)
            sourceParams.bottomMargin = baseSourceMarginBottom + bars.bottom
            sourceGroup.layoutParams = sourceParams
            listView.post(::updateSourcePlacement)
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
                    listView.post(::updateSourcePlacement)
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

    private fun updateSourcePlacement() {
        if (!::listView.isInitialized) return
        val firstRow = listView.getChildAt(0)
        val rowHeight = firstRow?.height ?: 0
        val sourceParams = sourceGroup.layoutParams as ViewGroup.MarginLayoutParams
        if (sourceGroup.measuredHeight == 0) {
            sourceGroup.measure(
                View.MeasureSpec.makeMeasureSpec(listView.width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
        }
        // 与原版 getAllItemHeight/useStaticSourceLogo 一致：首行高度 × 城市数，
        // 再预留数据源文字本身和上下 margin；不依赖 HeaderViewListAdapter 的滚动范围。
        val cityRowsHeight = rowHeight * latestState.cities.size
        val sourceSpace = sourceGroup.measuredHeight + sourceParams.topMargin + sourceParams.bottomMargin
        val contentFits = cityRowsHeight < listView.height - sourceSpace
        sourceGroup.visibility = if (contentFits) View.VISIBLE else View.GONE
        footerSource.visibility = if (contentFits) View.GONE else View.VISIBLE
        if (contentFits) {
            // ListView 会在 footer 可见性变化后重建子项；确保静态数据源仍位于列表绘制层之上。
            sourceGroup.bringToFront()
        }
    }

    private companion object {
        const val DRAG_START_VIBRATION_MILLIS = 30L
    }
}
