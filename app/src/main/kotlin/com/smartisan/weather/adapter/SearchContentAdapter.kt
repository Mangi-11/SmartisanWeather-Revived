package com.smartisan.weather.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.smartisan.weather.R
import com.smartisan.weather.bean.WeatherSearchBean
import com.smartisan.weather.data.model.SearchResultCity
import com.smartisan.weather.util.Utility

/**
 * 原版城市搜索列表的 View 适配器。
 *
 * 加载项不再通过向数据中插入 `null` 表示，而是作为独立的 view type 管理；这样分页
 * 请求取消、失败或快速切换关键字时不会留下幽灵 footer。
 */
class SearchContentAdapter(
    private val context: Context,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(view: View, position: Int, bean: WeatherSearchBean)
    }

    private val items = mutableListOf<WeatherSearchBean>()
    private var localCityIds: Set<String> = emptySet()
    private var searchKey: String = ""
    private var loadState: Int = LOAD_STATE_NORMAL
    private var itemClickListener: OnItemClickListener? = null
    private val highlightColor = ContextCompat.getColor(context, R.color.highlight_text_color)

    override fun getItemCount(): Int = items.size + if (showsLoadingFooter()) 1 else 0

    override fun getItemViewType(position: Int): Int =
        if (position == items.size) VIEW_TYPE_FOOTER else VIEW_TYPE_CITY

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_FOOTER) {
            FooterHolder(inflater.inflate(R.layout.weather_searhc_item_footerview, parent, false))
        } else {
            CityHolder(inflater.inflate(R.layout.weather_searhc_item_normal, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CityHolder -> holder.bind(items[position], position)
            is FooterHolder -> holder.bind()
        }
    }

    fun submitData(cities: List<SearchResultCity>) {
        items.clear()
        items.addAll(cities.map(SearchResultCity::toLegacyBean))
        notifyDataSetChanged()
    }

    fun setLocalCityIds(cityIds: Collection<String>?) {
        val updatedIds = cityIds?.toSet().orEmpty()
        if (updatedIds == localCityIds) return
        localCityIds = updatedIds
        notifyDataSetChanged()
    }

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        itemClickListener = listener
    }

    fun setSearchKey(key: String) {
        if (key == searchKey) return
        searchKey = key
        notifyDataSetChanged()
    }

    fun setLoadState(state: Int) {
        if (state == loadState) return
        loadState = state
        notifyDataSetChanged()
    }

    private fun showsLoadingFooter(): Boolean =
        items.isNotEmpty() && loadState == LOAD_STATE_LOADING

    private inner class CityHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val content: TextView = itemView.findViewById(R.id.content)
        private val separator: TextView = itemView.findViewById(R.id.textview_pad)
        private val subcontent: TextView = itemView.findViewById(R.id.subcontent)
        private val added: TextView = itemView.findViewById(R.id.added_text_view)
        private val headLine: View = itemView.findViewById(R.id.head_line)

        fun bind(bean: WeatherSearchBean, position: Int) {
            val county = bean.county.orEmpty()
            val city = bean.city.orEmpty()
            val province = bean.province.orEmpty()
            val country = bean.country.orEmpty()
            val secondary = when {
                county.isBlank() -> ""
                country == context.getString(R.string.weather_china) && county != province -> province
                country != context.getString(R.string.weather_china) && county != city -> city
                else -> ""
            }

            content.text = Utility.getHighlightText(searchKey, county, highlightColor)
            subcontent.text = Utility.getHighlightText(searchKey, secondary, highlightColor)
            separator.isVisible = secondary.isNotBlank()
            subcontent.isVisible = secondary.isNotBlank()
            headLine.isVisible = false

            val isAdded = bean.cityId.orEmpty() in localCityIds
            added.isVisible = isAdded
            if (isAdded) {
                val color = ContextCompat.getColor(context, R.color.color_city_added)
                content.setTextColor(color)
                separator.setTextColor(color)
                subcontent.setTextColor(color)
            } else {
                val colors = ContextCompat.getColorStateList(
                    context,
                    R.color.selector_findcity_text_color,
                )
                content.setTextColor(colors)
                separator.setTextColor(colors)
                subcontent.setTextColor(colors)
            }

            itemView.setOnClickListener { clickedView ->
                if (isAdded) {
                    Toast.makeText(
                        context,
                        R.string.weather_add_city_alread,
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    itemClickListener?.onItemClick(clickedView, position, bean)
                }
            }
        }
    }

    private inner class FooterHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val progress: ProgressBar = itemView.findViewById(R.id.footer_progressbar)
        private val content: TextView = itemView.findViewById(R.id.footer_content)

        fun bind() {
            progress.isVisible = true
            content.setText(R.string.loading)
        }
    }

    companion object {
        const val LOAD_STATE_NORMAL = 0
        const val LOAD_STATE_LOADING = 1
        const val LOAD_STATE_COMPLETE = 2
        const val LOAD_STATE_NO_MORE = 3

        private const val VIEW_TYPE_CITY = 0
        private const val VIEW_TYPE_FOOTER = 1
    }
}

private fun SearchResultCity.toLegacyBean(): WeatherSearchBean = WeatherSearchBean().also { bean ->
    bean.cityId = cityId
    bean.county = county
    bean.city = city
    bean.province = province
    bean.country = country
    bean.countyEn = countyEn
    bean.id = id
}
