package com.smartisan.weather.adapter

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.smartisan.weather.R
import com.smartisan.weather.bean.SmartisanLocation
import com.smartisan.weather.bean.WeatherSearchBean
import com.smartisan.weather.custom.FlowLayout
import com.smartisan.weather.custom.NightWeatherActionDrawable
import com.smartisan.weather.data.city.CityRepository
import com.smartisan.weather.util.ThemeUtils
import com.smartisan.weather.util.Utility

/**
 * 热门城市填充助手，复刻自原版 com.smartisan.weather.adapter.HotCityHelper。
 *
 * Smartisan OS 框架依赖替换说明：
 * - LocationDBHelper.getInstance(ctx).queryHotCities()（ContentProvider）
 *   → [CityRepository.defaultHotCities]（内置热门城市列表），并映射为 WeatherSearchBean。
 * - com.smartisan.weather.util.SingleClickProxy（500ms 防抖代理）
 *   → 内联等价的 lastClickTime 防抖逻辑。
 * - com.smartisan.appbaselayer.quality.NullSafe.nonNull(View) → 直接判空。
 */
class HotCityHelper(
    private val context: Context?,
    private val d: ViewGroup?,
) {

    private val a: ArrayList<WeatherSearchBean> = ArrayList()
    private var b: ArrayList<String>? = null
    private var e: SearchContentAdapter.OnItemClickListener? = null

    /** 复刻 SingleClickProxy 的 500ms 点击防抖时间戳。 */
    private var lastClickTime: Long = 0L

    init {
        if (context != null && d != null) {
            // 用内置热门城市列表替代原版 LocationDBHelper.queryHotCities()
            for (city in CityRepository.defaultHotCities) {
                val bean = WeatherSearchBean()
                bean.cityId = city.cityId
                bean.province = city.province
                bean.city = city.city
                bean.county = city.county
                bean.country = city.country
                a.add(bean)
                d.addView(
                    LayoutInflater.from(context)
                        .inflate(R.layout.weather_search_item_hot, null, false)
                )
            }
        }
    }

    /** 绑定第 [position] 个城市标签到 [view]。position == 0 为定位城市，1 为首个热门城市（另起一行）。 */
    private fun bindItem(view: View, position: Int) {
        val ctx = context ?: return
        if (position == 1) {
            val lp = FlowLayout.LayoutParams(0, 0)
            lp.isNewLine = true
            view.layoutParams = lp
        }
        val textView = view.findViewById<TextView>(R.id.tv_city_name) ?: return
        val isLocationItem = position == 0
        val backgroundRes = if (isLocationItem) {
            R.drawable.selector_location_city_item
        } else {
            R.drawable.selector_hot_city_item
        }
        val originalBackground = ContextCompat.getDrawable(ctx, backgroundRes)?.mutate()
        textView.background =
            if (ThemeUtils.isNightMode(ctx) && originalBackground != null) {
                /*
                 * The original NinePatch owns the pill outline, transparent shadow
                 * margin, content padding and (for the location item) arrow artwork.
                 * Recolor its pixels instead of replacing it with a generic shape.
                 */
                NightWeatherActionDrawable(
                    source = originalBackground,
                    normalSurfaceColor =
                        ContextCompat.getColor(ctx, R.color.app_surface_color),
                    pressedSurfaceColor =
                        ContextCompat.getColor(ctx, R.color.app_surface_pressed_color),
                    disabledSurfaceColor =
                        ContextCompat.getColor(ctx, R.color.app_surface_disabled_color),
                    embeddedIconColor = if (isLocationItem) {
                        ContextCompat.getColor(ctx, R.color.app_tertiary_text_color)
                    } else {
                        null
                    },
                )
            } else {
                originalBackground
            }
        val bean = a[position]
        val sb = StringBuilder()
        sb.append(bean.city)
        if (!TextUtils.isEmpty(bean.county) && bean.county != bean.city) {
            sb.append(ctx.getString(R.string.separator))
            sb.append(bean.county)
        }
        textView.text = sb.toString()
        val local = b
        if (local == null || !local.contains(bean.cityId)) {
            textView.setTextColor(
                ContextCompat.getColor(ctx, R.color.weather_hot_city_item_default_text_color)
            )
            textView.isSelected = false
        } else {
            textView.setTextColor(
                ContextCompat.getColor(ctx, R.color.weather_hot_city_item_added_text_color)
            )
            textView.isSelected = true
        }
        textView.setOnClickListener {
            // 等价于原版 SingleClickProxy 的 500ms 防抖
            if (System.currentTimeMillis() - lastClickTime <= 500) return@setOnClickListener
            lastClickTime = System.currentTimeMillis()
            val listener = e ?: return@setOnClickListener
            if (b != null && b!!.contains(bean.cityId)) {
                Toast.makeText(
                    ctx,
                    ctx.getString(R.string.weather_add_city_alread),
                    Toast.LENGTH_SHORT,
                ).show()
            } else if (bean.city == ctx.getString(R.string.weather_search_city_default_locaiton)) {
                listener.onItemClick(it, -1, bean)
            } else {
                listener.onItemClick(it, position, bean)
            }
        }
    }

    /** 在头部插入定位城市项。smartisanLocation 为空或 key 为 "-1" 时显示「定位服务」占位。 */
    private fun a(smartisanLocation: SmartisanLocation?) {
        if (context == null || d == null) return
        val bean = WeatherSearchBean()
        if (smartisanLocation == null || TextUtils.equals(smartisanLocation.mLocationKey, "-1")) {
            bean.cityId = ""
            bean.province = context.getString(R.string.weather_search_city_default_locaiton)
            bean.city = context.getString(R.string.weather_search_city_default_locaiton)
            bean.county = ""
        } else {
            bean.cityId = smartisanLocation.mLocationKey
            bean.province = smartisanLocation.mProvince
            bean.city = smartisanLocation.mLocationParentName
            bean.county = smartisanLocation.mLocationName
        }
        a.add(0, bean)
        d.addView(
            LayoutInflater.from(context)
                .inflate(R.layout.weather_search_item_hot, null, false),
            0,
        )
    }

    fun notifyViewStatus() {
        if (Utility.isCollectionEmpty(a)) return
        val viewGroup = d ?: return
        if (viewGroup.childCount != a.size) return
        for (i in a.indices) {
            bindItem(viewGroup.getChildAt(i), i)
        }
    }

    fun setCurrentLocation(smartisanLocation: SmartisanLocation?) {
        a(smartisanLocation)
    }

    fun setLocalCityIds(arrayList: ArrayList<String>?) {
        b = arrayList
    }

    fun setOnItemClickListener(onItemClickListener: SearchContentAdapter.OnItemClickListener?) {
        e = onItemClickListener
    }
}
