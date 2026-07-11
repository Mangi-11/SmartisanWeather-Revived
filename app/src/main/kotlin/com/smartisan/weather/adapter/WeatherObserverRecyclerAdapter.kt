package com.smartisan.weather.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smartisan.weather.R
import com.smartisan.weather.bean.HourForecastInfo
import com.smartisan.weather.custom.WeatherTempAnimView
import com.smartisan.weather.util.ResMappingUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class HourForecastItem(
    val startTimeMillis: Long?,
    val sunDescription: String?,
    val temperatureCelsius: Int,
    val temperatureFahrenheit: Int,
    val weatherCode: String?,
    val isNight: Boolean,
) {
    companion object {
        fun from(info: HourForecastInfo): HourForecastItem = HourForecastItem(
            startTimeMillis = info.getStartTime()?.toLongOrNull(),
            sunDescription = info.getSunDes(),
            temperatureCelsius = info.getTempC(),
            temperatureFahrenheit = info.getTempF(),
            weatherCode = info.getWeatherCode(),
            isNight = info.night,
        )
    }
}

/** Horizontally scrolling hourly forecast backed by immutable UI snapshots. */
internal class WeatherObserverRecyclerAdapter :
    ListAdapter<HourForecastItem, WeatherObserverRecyclerAdapter.HourViewHolder>(DIFF_CALLBACK) {

    private var showFahrenheit = false
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    class HourViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timeView: TextView = itemView.findViewById(R.id.item_view_time)
        val iconView: ImageView = itemView.findViewById(R.id.item_view_img)
        val temperatureCelsiusView: TextView = itemView.findViewById(R.id.item_view_tmpc)
        val temperatureFahrenheitView: TextView = itemView.findViewById(R.id.item_view_tmpf)
        val temperatureView: WeatherTempAnimView = itemView.findViewById(R.id.viewflipper)

        fun showTemperatureUnit(showFahrenheit: Boolean, animate: Boolean) {
            if (showFahrenheit) {
                temperatureView.showFView(animate)
            } else {
                temperatureView.showCView(animate)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HourViewHolder =
        HourViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_viewpager_content_item_view, parent, false),
        )

    override fun onBindViewHolder(holder: HourViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context
        holder.timeView.text = item.startTimeMillis
            ?.let { timeFormat.format(Date(it)) }
            ?: context.getText(R.string.weather_null)
        holder.iconView.setImageResource(
            ResMappingUtil.getWeatherResId(item.weatherCode.orEmpty())
                .getLittleIconShadow(item.isNight),
        )

        val sunDescription = item.sunDescription
        if (sunDescription.isNullOrEmpty()) {
            if (
                item.temperatureCelsius == Integer.MAX_VALUE ||
                item.temperatureFahrenheit == Integer.MAX_VALUE
            ) {
                holder.temperatureCelsiusView.setText(R.string.weather_null)
                holder.temperatureFahrenheitView.setText(R.string.weather_null)
            } else {
                holder.temperatureCelsiusView.text = "${item.temperatureCelsius}°"
                holder.temperatureFahrenheitView.text = "${item.temperatureFahrenheit}°"
            }
        } else {
            holder.temperatureCelsiusView.text = sunDescription
            holder.temperatureFahrenheitView.text = sunDescription
        }
        holder.showTemperatureUnit(showFahrenheit, animate = false)
    }

    override fun onBindViewHolder(
        holder: HourViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        val temperaturePayload = payloads.lastOrNull {
            it === ANIMATED_TEMPERATURE_PAYLOAD || it === IMMEDIATE_TEMPERATURE_PAYLOAD
        }
        if (temperaturePayload != null) {
            holder.showTemperatureUnit(
                showFahrenheit = showFahrenheit,
                animate = temperaturePayload === ANIMATED_TEMPERATURE_PAYLOAD,
            )
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onViewRecycled(holder: HourViewHolder) {
        holder.showTemperatureUnit(showFahrenheit, animate = false)
        super.onViewRecycled(holder)
    }

    fun submitForecasts(forecasts: List<HourForecastInfo>?, onCommitted: () -> Unit) {
        submitList(forecasts.orEmpty().map(HourForecastItem::from)) {
            onCommitted()
        }
    }

    fun setFahrenheitVisible(visible: Boolean, animate: Boolean) {
        if (showFahrenheit == visible) return
        showFahrenheit = visible
        if (itemCount > 0) {
            notifyItemRangeChanged(
                0,
                itemCount,
                if (animate) ANIMATED_TEMPERATURE_PAYLOAD else IMMEDIATE_TEMPERATURE_PAYLOAD,
            )
        }
    }

    companion object {
        private val ANIMATED_TEMPERATURE_PAYLOAD = Any()
        private val IMMEDIATE_TEMPERATURE_PAYLOAD = Any()

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<HourForecastItem>() {
            override fun areItemsTheSame(
                oldItem: HourForecastItem,
                newItem: HourForecastItem,
            ): Boolean =
                oldItem.startTimeMillis == newItem.startTimeMillis &&
                    oldItem.sunDescription == newItem.sunDescription

            override fun areContentsTheSame(
                oldItem: HourForecastItem,
                newItem: HourForecastItem,
            ): Boolean = oldItem == newItem
        }
    }
}
