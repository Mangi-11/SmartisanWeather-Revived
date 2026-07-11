package com.smartisan.weather.adapter

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smartisan.weather.R
import com.smartisan.weather.bean.NewForecastInfo
import com.smartisan.weather.bean.Observe
import com.smartisan.weather.bean.Weather
import com.smartisan.weather.custom.WeatherTempAnimView
import com.smartisan.weather.util.DebugLog
import com.smartisan.weather.util.ResMappingUtil
import com.smartisan.weather.util.Utility
import java.text.DecimalFormat
import java.util.Calendar
import java.util.Locale
import java.util.TreeSet

/**
 * 每日预报 + 空气质量详情纵向列表适配器。
 *
 * 完整复刻自原版 com.smartisan.weather.adapter.WeatherForecastRecyclerAdapter。
 * `NullSafe.nonNull(x)` → `x!!`。
 */
class WeatherForecastRecyclerAdapter(
    private val context: Context,
    private val recyclerView: RecyclerView,
) : RecyclerView.Adapter<WeatherForecastRecyclerAdapter.MyHolder>() {

    private var weather: Weather? = null
    private var forecasts: ArrayList<NewForecastInfo>? = null
    private var observe: Observe? = null
    private var fValuesVisible: Boolean = false
    private var isChina: Boolean = false
    private val isChinese: Boolean =
        context.resources.configuration.locales[0].language == Locale.CHINESE.language

    private val pollutantsList: ArrayList<String> = ArrayList()
    private val airList: ArrayList<String> = ArrayList()
    private val decimalFormat: DecimalFormat = DecimalFormat("00")
    private val calendar: Calendar = Calendar.getInstance()
    private val viewTypes: ArrayList<Int> = ArrayList()

    open inner class MyHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    inner class MyAirHolder(itemView: View) : MyHolder(itemView) {
        val firstColumn: View = itemView.findViewById<View>(R.id.lable1).parent as View
        val p: ImageView = itemView.findViewById(R.id.lable1_img)
        val q: TextView = itemView.findViewById(R.id.lable1)
        val r: TextView = itemView.findViewById(R.id.lable1_content_c)
        val s: TextView = itemView.findViewById(R.id.lable1_content_f)
        val x: WeatherTempAnimView = itemView.findViewById(R.id.group_1)
        val secondColumn: View = itemView.findViewById<View>(R.id.lable2).parent as View
        val t: ImageView = itemView.findViewById(R.id.lable2_img)
        val u: TextView = itemView.findViewById(R.id.lable2)
        val v: TextView = itemView.findViewById(R.id.lable2_content_c)
        val y: WeatherTempAnimView = itemView.findViewById(R.id.group_2)
    }

    inner class MyAirPollutantsHolder(itemView: View) : MyHolder(itemView) {
        val p: ImageView = itemView.findViewById(R.id.lable1_img)
        val q: TextView = itemView.findViewById(R.id.lable1)
        val r: TextView = itemView.findViewById(R.id.lable1_content_c)
        val s: ImageView = itemView.findViewById(R.id.lable2_img)
        val t: TextView = itemView.findViewById(R.id.lable2)
        val u: TextView = itemView.findViewById(R.id.lable2_content_c)
    }

    inner class MyForecastHolder(itemView: View) : MyHolder(itemView) {
        val p: TextView = itemView.findViewById(R.id.forecast_day)
        val q: ImageView = itemView.findViewById(R.id.forecast_img)
        val r: TextView = itemView.findViewById(R.id.forecast_lowtmpc)
        val s: TextView = itemView.findViewById(R.id.forecast_lowtmpf)
        val t: TextView = itemView.findViewById(R.id.forecast_hightmpc)
        val u: TextView = itemView.findViewById(R.id.forecast_hightmpf)
        val v: WeatherTempAnimView = itemView.findViewById(R.id.group_low)
        val w: WeatherTempAnimView = itemView.findViewById(R.id.group_high)
    }

    private fun str(resId: Int): String = context.getString(resId)

    private fun formatDate(timestamp: String): String {
        return try {
            calendar.timeInMillis = timestamp.toLong()
            String.format(str(R.string.weather_forecast_date), decimalFormat.format(calendar.get(Calendar.MONTH) + 1), decimalFormat.format(calendar.get(Calendar.DAY_OF_MONTH)))
        } catch (e: Exception) {
            DebugLog.log(DebugLog.TAG_EXCEPTION, "Forecast date formatting failed", e)
            ""
        }
    }

    private fun dedupeByDate(list: ArrayList<NewForecastInfo>?): ArrayList<NewForecastInfo> {
        val set = TreeSet<NewForecastInfo> { o1, o2 -> o1.getDate()!!.compareTo(o2.getDate() ?: "") }
        if (list != null) set.addAll(list)
        return ArrayList(set)
    }

    override fun getItemCount(): Int = viewTypes.size

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getItemViewType(position: Int): Int = viewTypes[position]

    override fun onBindViewHolder(holder: MyHolder, position: Int) {
        val type = getItemViewType(position)
        if (type == 0) {
            val forecastItems = forecasts ?: return
            if (holder !is MyForecastHolder || position !in forecastItems.indices) return
            val info = forecastItems[position]
            holder.p.text = String.format(str(R.string.weather_pager_week_and_date_text), formatDate(info.getDate()!!), Utility.getLocaleWeekday(context, info.getWeekDay()!!))
            holder.q.setImageResource(ResMappingUtil.getWeatherResId(info.getWeatherCodeAm() + "").getLittleIconShadow(false))
            if (info.getLowcTemp() == Integer.MAX_VALUE || info.getLowfTemp() == Integer.MAX_VALUE ||
                info.getHighCTemp() == Integer.MAX_VALUE || info.getHighfTemp() == Integer.MAX_VALUE
            ) {
                holder.s.text = str(R.string.weather_null)
                holder.r.text = str(R.string.weather_null)
                holder.u.text = str(R.string.weather_null)
                holder.t.text = str(R.string.weather_null)
                return
            }
            holder.r.text = info.getLowcTemp().toString() + str(R.string.weather_temperature_symbol)
            holder.s.text = info.getLowfTemp().toString() + str(R.string.weather_temperature_symbol)
            holder.t.text = info.getHighCTemp().toString() + str(R.string.weather_temperature_symbol)
            holder.u.text = info.getHighfTemp().toString() + str(R.string.weather_temperature_symbol)
            if (fValuesVisible) {
                holder.v.showFView(false)
                holder.w.showFView(false)
            } else {
                holder.v.showCView(false)
                holder.w.showCView(false)
            }
            return
        }
        if (type == 1) {
            if (holder is MyAirPollutantsHolder) {
                val size = (position - (if (forecasts!!.isNotEmpty()) forecasts!!.size else -1)) -
                    (if (airList.isNotEmpty()) airList.size else -1) - 2
                if (size < 0 || size > pollutantsList.size) return
                val entry = pollutantsList[size].split("=")
                holder.q.text = entry[0]
                holder.p.setImageResource(entry[2].toInt())
                holder.r.text = entry[1]
                holder.t.text = entry[3]
                holder.s.setImageResource(entry[5].toInt())
                holder.u.text = entry[4]
                holder.p.visibility = View.VISIBLE
                holder.s.visibility = View.VISIBLE
            }
            return
        }
        if (type == 2) {
            if (holder is MyAirHolder) {
                val size2 = (position - (if (forecasts!!.isNotEmpty()) forecasts!!.size else -1)) - 1
                if (size2 < 0 || size2 > airList.size) return
                val entry = airList[size2].split("=")
                holder.firstColumn.visibility = if (entry[0].isBlank()) View.GONE else View.VISIBLE
                holder.secondColumn.visibility = if (entry[2].isBlank()) View.GONE else View.VISIBLE
                holder.q.text = entry[0]
                holder.r.text = entry[1]
                holder.u.text = entry[2]
                holder.v.text = entry[3]
                holder.p.visibility = View.GONE
                holder.t.visibility = View.GONE
            }
            return
        }
        if (type == 3 && holder is MyAirHolder) {
            val size3 = (position - (if (forecasts!!.isNotEmpty()) forecasts!!.size else -1)) - 1
            if (size3 < 0 || size3 > airList.size) return
            val entry = airList[size3].split("=")
            holder.firstColumn.visibility = View.VISIBLE
            holder.secondColumn.visibility = if (entry[3].isBlank()) View.GONE else View.VISIBLE
            holder.q.text = entry[0]
            holder.u.text = entry[3]
            holder.v.text = entry[4]
            holder.y.showCView(false)
            val feelC = entry[1]
            val feelF = entry[2]
            if ("UNKNOWN" == feelC.replace(str(R.string.weather_celsius_symbol), "").replace(str(R.string.weather_fahrenheit_symbol), "") ||
                "UNKNOWN" == feelF.replace(str(R.string.weather_celsius_symbol), "").replace(str(R.string.weather_fahrenheit_symbol), "")
            ) {
                holder.s.text = str(R.string.weather_null)
                holder.r.text = str(R.string.weather_null)
            } else {
                holder.r.text = entry[1]
                holder.s.text = entry[2]
            }
            if (fValuesVisible) {
                holder.x.showFView(false)
            } else {
                holder.x.showCView(false)
            }
            holder.p.visibility = View.GONE
            holder.t.visibility = View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyHolder {
        return when (viewType) {
            0 -> MyForecastHolder(LayoutInflater.from(context).inflate(R.layout.item_viewpager_content_forcast_item, parent, false))
            1 -> MyAirPollutantsHolder(LayoutInflater.from(context).inflate(R.layout.item_viewpager_content_index_item_one, parent, false))
            2, 3 -> MyAirHolder(LayoutInflater.from(context).inflate(R.layout.item_viewpager_content_index_item_two, parent, false))
            4 -> MyHolder(LayoutInflater.from(context).inflate(R.layout.item_viewpager_divide, parent, false))
            else -> MyHolder(LayoutInflater.from(context).inflate(R.layout.item_viewpager_divide, parent, false))
        }
    }

    fun setData(weather: Weather, country: String?) {
        this.weather = weather
        forecasts = dedupeByDate(weather.newForecast)
        viewTypes.clear()
        if (forecasts!!.isNotEmpty()) {
            for (i in forecasts!!.indices) {
                viewTypes.add(0)
            }
        }
        observe = weather.observe
        isChina = str(R.string.weather_china) == country
        if (pollutantsList.isNotEmpty()) pollutantsList.clear()
        if (airList.isNotEmpty()) airList.clear()
        val obs = observe
        if (obs != null) {
            val humidity = if (TextUtils.isEmpty(obs.getHumidity()))
                str(R.string.weather_null)
            else
                obs.getHumidity() + str(R.string.weather_percent_symbol)
            val windDirResId = ResMappingUtil.getWindDirRedId(weather.observe!!.getWind())
            var windLevel = str(R.string.weather_null)
            if (!TextUtils.isEmpty(weather.observe!!.getSpeed())) {
                windLevel = weather.observe!!.getSpeed() + str(R.string.weather_forecast_wind_level)
            }
            if (viewTypes.isNotEmpty()) {
                viewTypes.add(4)
            }
            if (isChinese) {
                airList.add(String.format(str(R.string.weather_forecast_templet_normal), str(R.string.relative_Humidity), humidity, str(windDirResId), windLevel))
                viewTypes.add(2)
            } else {
                airList.add(String.format(str(R.string.weather_forecast_templet_normal), str(R.string.relative_Humidity), "", "", humidity))
                viewTypes.add(2)
                airList.add(String.format(str(R.string.weather_forecast_templet_normal), str(windDirResId), "", "", windLevel))
                viewTypes.add(2)
            }
            val feelC = if (TextUtils.isEmpty(obs.getBodyFeelC())) str(R.string.weather_null) else obs.getBodyFeelC() + str(R.string.weather_celsius_symbol)
            val feelF = if (TextUtils.isEmpty(obs.getBodyFeelF())) str(R.string.weather_null) else obs.getBodyFeelF() + str(R.string.weather_fahrenheit_symbol)
            val uvLevel = weather.allergy?.getUvLevel()?.takeIf(String::isNotBlank)
            val uvLabel = if (isChina && uvLevel != null) str(R.string.weather_forecast_ultraviolet_radiation) else ""
            airList.add(String.format(str(R.string.weather_forecast_templet_icon), str(R.string.real_feel_temp), feelC, feelF, uvLabel, uvLevel.orEmpty(), ""))
            viewTypes.add(3)
            if (isChina) {
                if (airList.isNotEmpty()) {
                    viewTypes.add(4)
                }
                val pm10 = if (TextUtils.isEmpty(obs.getPm10())) str(R.string.weather_null) else obs.getPm10()
                val pm25 = if (TextUtils.isEmpty(obs.getPm2_5())) str(R.string.weather_null) else obs.getPm2_5()
                val no2 = if (TextUtils.isEmpty(obs.getNo2())) str(R.string.weather_null) else obs.getNo2()
                val o3 = if (TextUtils.isEmpty(obs.getO3())) str(R.string.weather_null) else obs.getO3()
                val so2 = if (TextUtils.isEmpty(obs.getSo2())) str(R.string.weather_null) else obs.getSo2()
                val co = if (TextUtils.isEmpty(obs.getCo())) str(R.string.weather_null) else obs.getCo()
                pollutantsList.add(String.format(str(R.string.weather_forecast_templet_icon_second), str(R.string.weather_forecast_pm10_text), pm10, Utility.getPm10Grade(obs.getPm10()), str(R.string.weather_forecast_pm2_5_text), pm25, Utility.getPm2_5Grade(obs.getPm2_5())))
                pollutantsList.add(String.format(str(R.string.weather_forecast_templet_icon_second), str(R.string.weather_forecast_no2_text), no2, Utility.getNo2Grade(obs.getNo2()), str(R.string.weather_forecast_o3_text), o3, Utility.getO3Grade(obs.getO3())))
                pollutantsList.add(String.format(str(R.string.weather_forecast_templet_icon_second), str(R.string.weather_forecast_so2_text), so2, Utility.getSo2Grade(obs.getSo2()), str(R.string.weather_forecast_co_text), co, Utility.getCoGrade(obs.getCo())))
                for (i3 in pollutantsList.indices) {
                    viewTypes.add(1)
                }
            }
        }
    }

    fun setFValuesVisiable(z: Boolean) {
        fValuesVisible = z
    }

    fun updateHourForecastValue(z: Boolean) {
        fValuesVisible = z
        val count = itemCount
        for (i in 0 until count) {
            val type = getItemViewType(i)
            val vh = recyclerView.findViewHolderForAdapterPosition(i)
            if (type == 0 && vh is MyForecastHolder) {
                if (fValuesVisible) {
                    vh.v.showFView(true)
                    vh.w.showFView(true)
                } else {
                    vh.v.showCView(true)
                    vh.w.showCView(true)
                }
            } else if (type == 3 && vh is MyAirHolder) {
                if (fValuesVisible) {
                    vh.x.showFView(true)
                } else {
                    vh.x.showCView(true)
                }
            }
        }
    }
}
