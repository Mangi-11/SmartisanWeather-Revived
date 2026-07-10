package com.smartisan.weather.adapter

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smartisan.weather.R
import com.smartisan.weather.bean.HourForecastInfo
import com.smartisan.weather.custom.WeatherTempAnimView
import com.smartisan.weather.util.ResMappingUtil
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 逐小时预报横向列表适配器。
 *
 * 完整复刻自原版 com.smartisan.weather.adapter.WeatherObserverRecyclerAdapter。
 * `NullSafe.nonNull(x)` → `x!!`。
 */
class WeatherObserverRecyclerAdapter(private val context: Context) :
    RecyclerView.Adapter<WeatherObserverRecyclerAdapter.HourVH>() {

    private var data: List<HourForecastInfo>? = null
    private var fValuesVisible: Boolean = false
    private var layoutManager: LinearLayoutManager? = null
    private val calendar: Calendar = Calendar.getInstance()
    private val format: SimpleDateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val views: ArrayList<View?> = ArrayList()

    inner class HourVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timeView: TextView = itemView.findViewById(R.id.item_view_time)
        val iconView: ImageView = itemView.findViewById(R.id.item_view_img)
        val tempCView: TextView = itemView.findViewById(R.id.item_view_tmpc)
        val tempFView: TextView = itemView.findViewById(R.id.item_view_tmpf)
        val tempAnimView: WeatherTempAnimView = itemView.findViewById(R.id.viewflipper)
    }

    override fun getItemCount(): Int = data?.size ?: 0

    override fun onBindViewHolder(holder: HourVH, position: Int) {
        val list = data ?: return
        if (position !in list.indices) return
        val info = list[position]
        val startTime = info.getStartTime() ?: ""
        if (startTime.isNotEmpty()) {
            try { calendar.timeInMillis = startTime.toLong() } catch (_: NumberFormatException) { }
        }
        holder.timeView.text = format.format(calendar.time)
        if (TextUtils.isEmpty(info.getSunDes())) {
            holder.iconView.setImageResource(
                ResMappingUtil.getWeatherResId(info.getWeatherCode() + "")
                    .getLittleIconShadow(info.night)
            )
            if (info.getTempC() == Integer.MAX_VALUE || info.getTempF() == Integer.MAX_VALUE) {
                holder.tempCView.text = context.getText(R.string.weather_null)
                holder.tempFView.text = context.getText(R.string.weather_null)
            } else {
                holder.tempCView.text = info.getTempC().toString() + "°"
                holder.tempFView.text = info.getTempF().toString() + "°"
            }
        } else {
            holder.iconView.setImageResource(
                ResMappingUtil.getWeatherResId(info.getWeatherCode() + "")
                    .getLittleIconShadow(info.night)
            )
            holder.tempCView.text = info.getSunDes()
            holder.tempFView.text = info.getSunDes()
        }
        if (fValuesVisible) {
            holder.tempAnimView.showFView(false)
        } else {
            holder.tempAnimView.showCView(false)
        }
        holder.tempCView.setTag(position)
        if (views.size <= position || views[position] == null) {
            while (views.size <= position) views.add(null)
            views[position] = holder.itemView
        } else {
            views[position] = holder.itemView
        }
        val lp = holder.itemView.layoutParams as RecyclerView.LayoutParams
        if (position == 0) {
            lp.marginStart = context.resources.getDimensionPixelOffset(R.dimen.item_pager_content_forcast_hour_margin_left)
        } else if (data != null && position == list.size - 1) {
            lp.marginEnd = context.resources.getDimensionPixelOffset(R.dimen.item_pager_content_forcast_hour_margin_left)
            lp.marginStart = 0
        } else {
            lp.marginStart = 0
        }
        holder.itemView.layoutParams = lp
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HourVH =
        HourVH(LayoutInflater.from(context).inflate(R.layout.item_viewpager_content_item_view, parent, false))

    fun setData(list: List<HourForecastInfo>?) {
        data = list
    }

    fun setFValuesVisiable(z: Boolean) {
        fValuesVisible = z
    }

    fun setLayoutManager(linearLayoutManager: LinearLayoutManager?) {
        layoutManager = linearLayoutManager
    }

    fun updateHourForecastValue(z: Boolean) {
        val list = data ?: return
        fValuesVisible = z
        val min = Math.min(list.size, views.size)
        var i = layoutManager!!.findFirstVisibleItemPosition()
        while (i < min) {
            if (i >= 0 && i < views.size) {
                val view = views[i]
                if (view != null) {
                    val animView = view.findViewById<WeatherTempAnimView>(R.id.viewflipper)
                    if (z) animView.showFView(true) else animView.showCView(true)
                }
            }
            i++
        }
    }
}
