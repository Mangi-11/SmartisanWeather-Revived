package com.smartisan.weather.ui.citylist

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.smartisan.weather.R
import com.smartisan.weather.data.model.SavedCity
import com.smartisan.weather.data.model.Weather
import com.smartisan.weather.data.settings.WeatherSettings
import com.smartisan.weather.util.WeatherCodeMapping
import java.time.Instant

/** 原版城市编辑列表的数据适配器，同时维护一次拖拽手势内的临时顺序。 */
class CityListAdapter(
    private val context: Context,
    private val onDeleteClick: (city: SavedCity, position: Int) -> Unit,
) : BaseAdapter() {

    private val cities = mutableListOf<SavedCity>()
    private var weatherByCity: Map<String, Weather> = emptyMap()
    private var tempUnit: Int = WeatherSettings.UNIT_CELSIUS
    private var dragSnapshot: List<SavedCity>? = null
    private var draggingCityKey: String? = null
    private var itemClickable = true
    private var persistedOrderKeys: List<String> = emptyList()
    private var previewOrderDirty = false

    fun submitState(state: CityListUiState) {
        val latestOrderKeys = state.cities.map(SavedCity::locationKey)
        persistedOrderKeys = latestOrderKeys
        if (dragSnapshot == null && !previewOrderDirty) {
            cities.clear()
            cities.addAll(state.cities)
        } else {
            // Room/天气/设置 Flow 可能在拖拽预览期间刷新。对象内容按 key 更新，
            // 已删除项移除、新增项追加，但不覆盖用户在页面内形成的临时顺序。
            val latestCities = state.cities.associateBy(SavedCity::locationKey)
            val mergedCities = cities.mapNotNull { latestCities[it.locationKey] }.toMutableList()
            val mergedKeys = mergedCities.mapTo(HashSet(), SavedCity::locationKey)
            state.cities.forEach { city ->
                if (mergedKeys.add(city.locationKey)) mergedCities.add(city)
            }
            cities.clear()
            cities.addAll(mergedCities)
            previewOrderDirty = cities.map(SavedCity::locationKey) != persistedOrderKeys
        }
        weatherByCity = state.weatherByCity
        tempUnit = state.tempUnit
        notifyDataSetChanged()
    }

    fun beginDrag(position: Int): Boolean {
        if (position !in cities.indices || cities[position].isLocationCity) return false
        if (dragSnapshot == null) dragSnapshot = cities.toList()
        draggingCityKey = cities[position].locationKey
        itemClickable = false
        notifyDataSetChanged()
        return true
    }

    fun moveDrag(from: Int, to: Int) {
        if (dragSnapshot == null || from !in cities.indices || to !in cities.indices || from == to) {
            return
        }
        val moved = cities.removeAt(from)
        cities.add(to, moved)
        notifyDataSetChanged()
    }

    fun finishDrag(): List<SavedCity> {
        dragSnapshot = null
        draggingCityKey = null
        itemClickable = true
        previewOrderDirty = cities.map(SavedCity::locationKey) != persistedOrderKeys
        notifyDataSetChanged()
        return cities.toList()
    }

    fun cancelDrag() {
        dragSnapshot?.let { originalOrder ->
            cities.clear()
            cities.addAll(originalOrder)
        }
        dragSnapshot = null
        draggingCityKey = null
        itemClickable = true
        previewOrderDirty = cities.map(SavedCity::locationKey) != persistedOrderKeys
        notifyDataSetChanged()
    }

    fun currentCities(): List<SavedCity> = cities.toList()

    fun removeCity(cityKey: String) {
        cities.removeAll { it.locationKey == cityKey }
        previewOrderDirty = cities.map(SavedCity::locationKey) != persistedOrderKeys
        notifyDataSetChanged()
    }

    fun setItemClickable(clickable: Boolean) {
        itemClickable = clickable
    }

    override fun getCount(): Int = cities.size

    override fun getItem(position: Int): SavedCity = cities[position]

    override fun getItemId(position: Int): Long {
        val city = cities[position]
        return city.id.takeIf { it != 0 }?.toLong() ?: city.locationKey.hashCode().toLong()
    }

    override fun hasStableIds(): Boolean = true

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder: ViewHolder
        val row = if (convertView == null) {
            LayoutInflater.from(context).inflate(R.layout.weather_editable_list_item, parent, false).also {
                holder = ViewHolder(it)
                it.tag = holder
            }
        } else {
            holder = convertView.tag as ViewHolder
            convertView
        }

        // WeatherListView 的删除动画会把旧行折叠成 1 px；复用时必须恢复正常高度。
        row.layoutParams = (row.layoutParams as? AbsListView.LayoutParams)?.apply {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        } ?: AbsListView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        row.translationX = 0f
        row.translationY = 0f
        row.alpha = 1f

        bind(holder, getItem(position), position)
        return row
    }

    private fun bind(holder: ViewHolder, city: SavedCity, position: Int) {
        val weather = weatherByCity[city.locationKey]
        val unit = if (tempUnit == WeatherSettings.UNIT_CELSIUS) "°C" else "°F"
        val currentTemp = weather?.observe?.let { observe ->
            if (tempUnit == WeatherSettings.UNIT_CELSIUS) observe.tempC else observe.tempF
        }.takeUnless { it.isNullOrBlank() || it == "UNKNOWN" }

        val cityName = formatCityName(city)
        holder.cityName.text = context.getString(
            R.string.format_cityname,
            cityName,
            currentTemp.orEmpty(),
            if (currentTemp == null) context.getString(R.string.weather_null) + unit else unit,
        )
        holder.weather.text = formatWeather(weather, unit)
        holder.weather.setPaddingRelative(
            0,
            if (context.resources.configuration.fontScale >= 1.4f) {
                -(2f * context.resources.displayMetrics.density + 0.5f).toInt()
            } else {
                0
            },
            0,
            0,
        )

        val code = weather?.themeCode
        if (weather == null) {
            holder.weatherIcon.setImageDrawable(null)
        } else {
            holder.weatherIcon.setImageResource(WeatherCodeMapping.getIcon(code, isNight(weather)))
        }

        holder.headLine.visibility = if (city.isLocationCity) View.VISIBLE else View.GONE
        holder.delete.isEnabled = itemClickable
        holder.delete.setOnClickListener {
            if (itemClickable) onDeleteClick(city, position)
        }

        // 原版把定位城市作为不可拖动的头部；当前数据层仍将它作为第 0 行展示，
        // 因此保留同样的禁用态拖拽柄，并由列表的 position validator 阻止跨越。
        holder.drag.visibility = View.VISIBLE
        holder.drag.isEnabled = itemClickable && !city.isLocationCity
        holder.root.alpha = if (city.locationKey == draggingCityKey) 0f else 1f

        val description = listOf(holder.cityName.text, holder.weather.text)
            .filter { it.isNotEmpty() }
            .joinToString("，")
        holder.info.contentDescription = description
    }

    private fun formatCityName(city: SavedCity): String {
        val name = city.displayName
        val parent = city.locationParentName
        return if (
            name.equals(parent, ignoreCase = true) ||
            parent.isBlank()
        ) {
            name.replaceFirstChar { it.titlecase() }
        } else {
            "${name.replaceFirstChar { it.titlecase() }} - ${parent.replaceFirstChar { it.titlecase() }}"
        }
    }

    private fun formatWeather(weather: Weather?, unit: String): String {
        val observe = weather?.observe
        if (observe == null) return unavailableWeather(unit)

        val firstForecast = weather.dailyForecast.firstOrNull()
        val low = if (tempUnit == WeatherSettings.UNIT_CELSIUS) {
            observe.lowTempC.ifBlank { firstForecast?.lowTempC?.toString().orEmpty() }
        } else {
            observe.lowTempF.ifBlank { firstForecast?.lowTempF?.toString().orEmpty() }
        }
        val high = if (tempUnit == WeatherSettings.UNIT_CELSIUS) {
            observe.highTempC.ifBlank { firstForecast?.highTempC?.toString().orEmpty() }
        } else {
            observe.highTempF.ifBlank { firstForecast?.highTempF?.toString().orEmpty() }
        }
        if (low.isBlank() || high.isBlank()) return unavailableWeather(unit)

        val description = context.getString(
            WeatherCodeMapping.textResMap[weather.themeCode] ?: R.string.weather_text_99,
        )
        return context.getString(R.string.format_weather, description, low, unit, high, unit)
    }

    private fun unavailableWeather(unit: String): String = context.getString(
        R.string.format_weather,
        context.getString(R.string.weather_null),
        context.getString(R.string.weather_null),
        unit,
        context.getString(R.string.weather_null),
        unit,
    )

    private fun isNight(weather: Weather): Boolean {
        val explicitSunTimes = listOf(
            weather.observe.curSunRise,
            weather.observe.curSunSet,
        ).mapNotNull(::extractTimeInMinutes)
        val sunTimes = if (explicitSunTimes.size == 2) {
            explicitSunTimes
        } else {
            TIME_REGEX.findAll(weather.dailyForecast.firstOrNull()?.sunriseAndSunset.orEmpty())
                .mapNotNull { extractTimeInMinutes(it.value) }
                .take(2)
                .toList()
        }
        if (sunTimes.size != 2) return false

        val now = weather.localTimeAt(Instant.now())
        val nowInMinutes = now.hour * MINUTES_PER_HOUR + now.minute
        return nowInMinutes < sunTimes[0] || nowInMinutes >= sunTimes[1]
    }

    private fun extractTimeInMinutes(value: String): Int? {
        val match = TIME_REGEX.find(value) ?: return null
        val parts = match.value.split(':')
        return parts[0].toIntOrNull()?.times(MINUTES_PER_HOUR)?.plus(
            parts[1].toIntOrNull() ?: return null,
        )
    }

    private class ViewHolder(row: View) {
        val root: View = row
        val cityName: TextView = row.findViewById(R.id.city_name)
        val weather: TextView = row.findViewById(R.id.temp)
        val weatherIcon: ImageView = row.findViewById(R.id.weather_icon)
        val delete: ImageView = row.findViewById(R.id.delete)
        val drag: ImageView = row.findViewById(R.id.drag)
        val headLine: View = row.findViewById(R.id.head_line)
        val info: View = row.findViewById(R.id.layout_info)
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60
        val TIME_REGEX = Regex("(?:[01]?\\d|2[0-3]):[0-5]\\d")
    }
}
