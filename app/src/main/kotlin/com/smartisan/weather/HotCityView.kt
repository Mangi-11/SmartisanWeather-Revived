package com.smartisan.weather

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.smartisan.weather.adapter.HotCityHelper
import com.smartisan.weather.adapter.SearchContentAdapter
import com.smartisan.weather.bean.SmartisanLocation
import com.smartisan.weather.bean.WeatherSearchBean
import com.smartisan.weather.util.Utility

/**
 * 热门城市视图，复刻自原版 com.smartisan.weather.HotCityView。
 *
 * 一个竖直 LinearLayout，内部包含一个 [com.smartisan.weather.custom.FlowLayout]，
 * 由 [HotCityHelper] 填充热门城市标签并处理点击。
 *
 * Smartisan OS 框架依赖替换说明：
 * - SearchContentAdapter.OnItemClickListener → 保留原接口（见 SearchContentAdapter.kt）
 * - LocationDBHelper（ContentProvider）→ 由 HotCityHelper 改用 CityRepository.defaultHotCities
 */
class HotCityView : LinearLayout, SearchContentAdapter.OnItemClickListener {

    private var a: Context? = null
    private var b: SearchContentAdapter.OnItemClickListener? = null
    private var c: HotCityHelper? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
        super(context, attrs, defStyleAttr)

    fun initView(arrayList: ArrayList<String>?, smartisanLocation: SmartisanLocation?) {
        var list = arrayList
        if (Utility.isCollectionEmpty(list)) {
            list = ArrayList()
        }
        val helper = c
        if (helper != null) {
            helper.setLocalCityIds(list)
            helper.setCurrentLocation(smartisanLocation)
            helper.setOnItemClickListener(this)
            helper.notifyViewStatus()
        }
    }

    /** Room 城市集合变化后只刷新选中态，不重复插入定位项。 */
    fun updateLocalCityIds(cityIds: Collection<String>) {
        c?.setLocalCityIds(ArrayList(cityIds))
        c?.notifyViewStatus()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        a = context
        c = HotCityHelper(a, findViewById(R.id.flow_parent))
    }

    override fun onItemClick(view: View, position: Int, bean: WeatherSearchBean) {
        b?.onItemClick(view, position, bean)
    }

    fun setHotCityItemClickLister(onItemClickListener: SearchContentAdapter.OnItemClickListener?) {
        b = onItemClickListener
    }
}
