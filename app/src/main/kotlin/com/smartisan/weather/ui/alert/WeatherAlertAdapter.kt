package com.smartisan.weather.ui.alert

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smartisan.weather.R
import com.smartisan.weather.data.model.AlertInfo
import com.smartisan.weather.util.Utility

internal class WeatherAlertAdapter(
    private val alerts: List<AlertInfo>,
) : RecyclerView.Adapter<WeatherAlertAdapter.AlertViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.weather_alert_layout_item, parent, false)
        return AlertViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(alerts[position])
    }

    override fun getItemCount(): Int = alerts.size

    internal class AlertViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val type = itemView.findViewById<TextView>(R.id.alert_type)
        private val updateTime = itemView.findViewById<TextView>(R.id.alert_update_time)
        private val content = itemView.findViewById<TextView>(R.id.alert_content)
        private val levelBackground = itemView.findViewById<ImageView>(R.id.alert_img01)
        private val typeIcon = itemView.findViewById<ImageView>(R.id.alert_img02)

        fun bind(alert: AlertInfo) {
            val context = itemView.context
            type.text = context.getString(R.string.weather_alert_tip, alert.type, alert.level)
            updateTime.text = Utility.getDisplayTime(context, alert.publishTime)
            content.text = alert.content
            levelBackground.setBackgroundResource(
                AlertIconMapping.levelBackground(alert.levelNumber),
            )
            typeIcon.setImageResource(
                AlertIconMapping.typeIcon(context, alert.typeNumber, alert.type),
            )
        }
    }
}
