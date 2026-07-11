package com.smartisan.weather.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
 * 小米城市搜索一次返回完整结果、不提供分页，因此这里只维护真实的城市行。
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
    private var itemClickListener: OnItemClickListener? = null
    private val highlightColor = ContextCompat.getColor(context, R.color.highlight_text_color)

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return CityHolder(inflater.inflate(R.layout.weather_searhc_item_normal, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as CityHolder).bind(items[position], position)
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
