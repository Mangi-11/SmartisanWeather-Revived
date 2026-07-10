package com.smartisan.weather.custom

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.LinearLayout
import com.smartisan.weather.R

/**
 * Displays the main temperature using per-digit image drawables (num_0..num_9, num_minus).
 *
 * Ported from the decompiled Java class `com.smartisan.weather.custom.WeatherMainTemView`.
 * The original obfuscated field names are preserved where they carry no semantic meaning.
 */
class WeatherMainTemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var tempC: Int = 0
    private var tempF: Int = 0
    private var checked: Boolean = false

    private lateinit var digit1: ImageView
    private lateinit var digit2: ImageView
    private lateinit var digit3: ImageView
    private lateinit var tempTypeImage: ImageView
    private lateinit var signImage: ImageView

    override fun onFinishInflate() {
        super.onFinishInflate()
        digit1 = findViewById(R.id.real_num_1)
        digit2 = findViewById(R.id.real_num_2)
        digit3 = findViewById(R.id.real_num_3)
        tempTypeImage = findViewById(R.id.imageView_temptype)
        signImage = findViewById(R.id.real_sign)
    }

    /**
     * Splits [temp] into hundreds / tens / units digits and sets the corresponding
     * num_* drawables on the three digit ImageViews, showing a minus sign for negatives.
     */
    private fun setDigits(temp: Int) {
        val hundreds = Math.abs(temp / 100)
        val tens = Math.abs((temp % 100) / 10)
        val units = Math.abs(temp % 10)

        if (hundreds != 0) {
            digit1.visibility = VISIBLE
            digit1.setImageResource(getNumDrawable(hundreds))
        } else {
            digit1.visibility = GONE
        }

        if (tens == 0 && hundreds == 0) {
            digit2.visibility = GONE
        } else {
            digit2.visibility = VISIBLE
            digit2.setImageResource(getNumDrawable(tens))
        }

        digit3.visibility = VISIBLE
        digit3.setImageResource(getNumDrawable(units))

        if (temp >= 0) {
            signImage.visibility = GONE
        } else {
            signImage.visibility = VISIBLE
            signImage.setImageResource(TemperatureDrawableResources.minus())
        }
    }

    private fun getNumDrawable(digit: Int): Int =
        TemperatureDrawableResources.digit(digit)

    fun setAnimTemp(temp: String) {
        setDigits(temp.toInt())
        tempTypeImage.visibility = INVISIBLE
    }

    fun setChecked(checked: Boolean) {
        this.checked = checked
        tempTypeImage.isSelected = this.checked
        tempTypeImage.alpha = 1.0f
        if (this.checked) {
            tempTypeImage.contentDescription =
                context.getString(R.string.weather_f_icon_description, tempF)
            setDigits(tempF)
        } else {
            tempTypeImage.contentDescription =
                context.getString(R.string.weather_c_icon_description, tempC)
            setDigits(tempC)
        }
    }

    fun setTemp(tempCStr: String, tempFStr: String, checked: Boolean) {
        this.tempC = tempCStr.toInt()
        this.tempF = tempFStr.toInt()
        this.checked = checked
        tempTypeImage.isSelected = this.checked
        tempTypeImage.alpha = 1.0f
        if (this.checked) {
            tempTypeImage.contentDescription =
                context.getString(R.string.weather_f_icon_description, tempF)
            setDigits(tempF)
        } else {
            tempTypeImage.contentDescription =
                context.getString(R.string.weather_c_icon_description, tempC)
            setDigits(tempC)
        }
    }
}
