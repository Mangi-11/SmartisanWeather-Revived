package com.smartisan.weather

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smartisan.weather.adapter.WeatherForecastRecyclerAdapter
import com.smartisan.weather.adapter.WeatherObserverRecyclerAdapter
import com.smartisan.weather.bean.AlertInfo
import com.smartisan.weather.bean.SmartisanLocation
import com.smartisan.weather.bean.Weather
import com.smartisan.weather.custom.CircleAnimButton
import com.smartisan.weather.custom.DrawItem
import com.smartisan.weather.custom.IndicateView
import com.smartisan.weather.custom.SmartisanScrollView
import com.smartisan.weather.custom.TemperatureDrawableResources
import com.smartisan.weather.custom.VerticalRecyclerView
import com.smartisan.weather.custom.WeatherMainTemView
import com.smartisan.weather.custom.WeatherTempAnimView
import com.smartisan.weather.util.ClickUtil
import com.smartisan.weather.util.DebugLog
import com.smartisan.weather.util.SmartisanSwitchEx
import com.smartisan.weather.util.ThemeBean
import com.smartisan.weather.util.ThemeUtils
import com.smartisan.weather.util.Utility
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 单个城市天气内容视图：城市名、温度、逐小时/逐日预报、预警、刷新按钮、C/F 切换，
 * 以及全部进出场/温度滚动动画。
 *
 * 复刻自原版 com.smartisan.weather.WeatherContentViewUtil（约 1474 行）。
 *
 * Smartisan OS 框架替换：
 *  - `smartisanos.api.VibratorSmt.vibrateEffect(vibrator, 0)` → 标准 [Vibrator]（[vibrateEffect]）。
 *  - `com.smartisan.appbaselayer.quality.NullSafe.nonNull(x)` → `x!!`。
 *  - `com.google.android.collect.Lists.newArrayList()` → `arrayListOf()`。
 *  - `Utility.getSystemTemperatureUnit` / `setSystemTemperatureUnit` 原本经由 Smartisan OS
 *    SettingsSmt ContentProvider，现统一委托给 DataStore。
 */
class WeatherContentViewUtil @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), View.OnClickListener {

    companion object {
        const val EMPTY_TYPE_LOADING = 1
        const val EMPTY_TYPE_LOAD_ERROR = 2
        const val TAG = "Weather_WeatherContentViewUtil"
        const val TEXT_TEMP_TYPE_C = "°C"
        const val TEXT_TEMP_TYPE_F = "°F"
        private const val HAPTIC_DURATION_MILLIS = 30L

        @JvmField
        var MAX_ALPHA_TIME: Int = 0

        private val OUT_ANIM_CONFIG = AnimConfig(1, false, false, 0, false, "invisible")
    }

    /**
     * 动画配置包，原版内部类 `a`。
     * - [type] 位掩码：&1 控制 alpha，&2 控制 translationX。
     * - [show] true=入场（0→1），false=出场。
     * - [dirFlag] 控制 translationX 方向。
     * - [delay] 起始延迟。
     * - [prepare] true=动画前预设初始状态。
     * - [endVisibility] 动画结束后目标可见性（"gone"/"invisible"/"visible"/""）。
     * - [endRunnable] 动画结束后回调。
     */
    private class AnimConfig(
        val type: Int,
        val show: Boolean,
        val dirFlag: Boolean,
        val delay: Long,
        val prepare: Boolean,
        val endVisibility: String,
        val endRunnable: Runnable? = null,
    )

    private val ctx: Context = context
    private lateinit var inflater: LayoutInflater
    private var topBox: View? = null
    private var jumpForecastButton: View? = null
    private var topBoxBg: View? = null
    private var emptyGroupTop: View? = null
    private var progressBar: ProgressBar? = null
    private var bottomGroup: View? = null
    private var cityNameView: TextView? = null
    private var cityNameLine: View? = null
    private var centigradeIcon: ImageView? = null
    private var fahrenheitIcon: ImageView? = null
    private var listButton: ImageButton? = null
    private var addButton: ImageButton? = null
    private var circleAnimButton: CircleAnimButton? = null
    private var forecastLayoutManager: LinearLayoutManager? = null
    private lateinit var hourForecastView: RecyclerView
    private val observerAdapter = WeatherObserverRecyclerAdapter()
    private var vibrator: Vibrator? = null
    private var tempScrollView: SmartisanScrollView? = null
    private var tempAnimView: WeatherMainTemView? = null
    private var emptyType: Int = -1
    private var controller: AbstractController? = null
    private var themeType: String = ""
    private var isRefreshAnimating: Boolean = false

    // 包内可见的视图字段（保持原版公开/包私有语义）。
    var aqiTextView: TextView? = null
        private set
    var aqiDescView: TextView? = null
        private set
    var updateTimeView: TextView? = null
        private set
    var alertGroup: View? = null
        private set
    var alertFlipper: ViewFlipper? = null
        private set
    var weekView: TextView? = null
        private set
    var todayView: TextView? = null
        private set
    var lowCTextView: TextView? = null
        private set
    var lowFTextView: TextView? = null
        private set
    var highCTextView: TextView? = null
        private set
    var highFTextView: TextView? = null
        private set
    var lowTempAnimView: WeatherTempAnimView? = null
        private set
    var highTempAnimView: WeatherTempAnimView? = null
        private set
    var partnerLabelView: TextView? = null
        private set
    var forecastRecyclerView: VerticalRecyclerView? = null
        private set
    private var forecastAdapter: WeatherForecastRecyclerAdapter? = null
    private var translateDistanceOut: Int = 0
    private var translateDistanceIn: Int = 0

    var mCOrFSwitchView: SmartisanSwitchEx? = null
        private set
    var mDrawItem: DrawItem? = null
    var mEmptyAlertIcon: View? = null
        private set
    var mEmptyInfoView1: TextView? = null
        private set
    var mEmptyInfoView2: TextView? = null
        private set
    var mForecastContentView: ScrollView? = null
        private set
    var mIndicateView: IndicateView? = null
        private set
    var mWeatherTypeView: TextView? = null
        private set

    private val corfCheckedChangeListener = CompoundButton.OnCheckedChangeListener { _, checked ->
        performTemperatureUnitHapticFeedback()
        updateCorFContentDescription()
        fahrenheitIcon!!.isSelected = checked
        centigradeIcon!!.isSelected = !checked
        post {
            DebugLog.log(TAG, "onCheckedChanged isChecked:$checked")
            Utility.setSystemTemperatureUnit(ctx, if (checked) 2 else 1)
            playAnimationCorF()
        }
    }

    /** 创建模糊温度数字 ImageView（资源 mohuzhong_{i}）。 */
    private fun createBlurryView(i: Int): View {
        val imageView = ImageView(ctx)
        imageView.setImageResource(TemperatureDrawableResources.blurry(i))
        return imageView
    }

    /** 创建单组温度滚动数字视图（item_temp_scroll_num），拆分百/十/个位 + 负号。 */
    private fun createScrollNumView(value: Int, style: Int): View {
        val hundreds = Math.abs(value / 100)
        val tens = Math.abs((value % 100) / 10)
        val units = Math.abs(value % 10)
        val view = inflater.inflate(R.layout.item_temp_scroll_num, null, false)
        val digit1 = view.findViewById<ImageView>(R.id.imageview_temp_scroll_num1)
        val digit2 = view.findViewById<ImageView>(R.id.imageview_temp_scroll_num2)
        val digit3 = view.findViewById<ImageView>(R.id.imageview_temp_scroll_num3)
        val sign = view.findViewById<ImageView>(R.id.textview_temp_sign)
        if (hundreds != 0) {
            digit1.visibility = View.VISIBLE
            digit1.setImageResource(TemperatureDrawableResources.digit(hundreds, style))
        }
        if (tens != 0 || hundreds != 0) {
            digit2.visibility = View.VISIBLE
            digit2.setImageResource(TemperatureDrawableResources.digit(tens, style))
        }
        digit3.visibility = View.VISIBLE
        digit3.setImageResource(TemperatureDrawableResources.digit(units, style))
        if (value < 0) {
            sign.visibility = View.VISIBLE
            sign.setImageResource(TemperatureDrawableResources.minus(style))
        } else {
            sign.visibility = View.GONE
        }
        return view
    }

    /** 拼接城市显示名。 */
    private fun getCityDisplayName(location: SmartisanLocation?): String {
        if (location == null) return ""
        if ("-1" == location.mLocationKey) {
            return ctx.getString(R.string.weather_location_default_name)
        }
        return try {
            val locName = location.mLocationName
            val name: String
            if (ctx.getString(R.string.weather_china) == location.mCountry) {
                name = if (locName?.equals(location.mProvince, ignoreCase = true) == true ||
                    !Utility.isDigitalOnly(location.mLocationKey)
                ) {
                    Utility.fristCharToUpdderCase(locName ?: "")
                } else {
                    Utility.fristCharToUpdderCase(locName ?: "") + " - " +
                        Utility.fristCharToUpdderCase(location.mProvince ?: "")
                }
            } else {
                name = if (locName?.equals(location.mLocationParentName, ignoreCase = true) == true ||
                    !Utility.isDigitalOnly(location.mLocationKey)
                ) {
                    Utility.fristCharToUpdderCase(locName ?: "")
                } else {
                    Utility.fristCharToUpdderCase(locName ?: "") + " - " +
                        Utility.fristCharToUpdderCase(location.mLocationParentName ?: "")
                }
            }
            name
        } catch (e: Exception) {
            DebugLog.log(TAG, "", e)
            ""
        }
    }

    /** 为一组视图生成进/出场动画列表，原版 `a(a, View[])`。 */
    private fun buildAnimators(config: AnimConfig, views: Array<View>?): List<Animator> {
        val animators = arrayListOf<Animator>()
        if (views == null) return animators
        val interpolator = AccelerateDecelerateInterpolator()
        for (view in views) {
            var alphaAnim: ObjectAnimator? = null
            if (config.type and 1 == 1) {
                alphaAnim = if (config.show) {
                    ObjectAnimator.ofFloat(view, "alpha", 0.0f, 1.0f)
                } else {
                    ObjectAnimator.ofFloat(view, "alpha", view.alpha, 0.0f)
                }
                if (config.prepare) {
                    view.alpha = if (config.show) 0.0f else 1.0f
                }
                alphaAnim.addListener(object : AnimatorListenerImpl() {
                    override fun onAnimationEnd(animation: Animator) {
                        when {
                            TextUtils.equals("gone", config.endVisibility) -> {
                                view.visibility = View.GONE
                                view.alpha = 1.0f
                                view.translationX = 0.0f
                            }
                            TextUtils.equals("invisible", config.endVisibility) -> {
                                view.visibility = View.INVISIBLE
                                view.alpha = 1.0f
                                view.translationX = 0.0f
                            }
                            TextUtils.equals("visible", config.endVisibility) -> {
                                view.visibility = View.VISIBLE
                            }
                        }
                        config.endRunnable?.run()
                    }
                })
            }
            var translateAnim: ObjectAnimator? = null
            if (config.type and 2 == 2) {
                if (!config.show) {
                    if (config.prepare) {
                        view.translationX = 0.0f
                    }
                    translateAnim = if (config.dirFlag) {
                        ObjectAnimator.ofFloat(view, "translationX", view.translationX, view.translationX + translateDistanceOut)
                    } else {
                        ObjectAnimator.ofFloat(view, "translationX", view.translationX, view.translationX - translateDistanceOut)
                    }
                } else if (config.dirFlag) {
                    translateAnim = ObjectAnimator.ofFloat(view, "translationX", translateDistanceOut.toFloat(), 0.0f)
                    if (config.prepare) {
                        view.translationX = translateDistanceOut.toFloat()
                    }
                } else {
                    translateAnim = ObjectAnimator.ofFloat(view, "translationX", -translateDistanceOut.toFloat(), 0.0f)
                    if (config.prepare) {
                        view.translationX = -translateDistanceOut.toFloat()
                    }
                }
            }
            if (translateAnim != null) {
                translateAnim.interpolator = interpolator
                translateAnim.duration = 200L
                if (config.delay > 0) {
                    translateAnim.startDelay = config.delay
                }
                animators.add(translateAnim)
            }
            if (alphaAnim != null) {
                alphaAnim.interpolator = interpolator
                alphaAnim.duration = 200L
                if (config.delay > 0) {
                    alphaAnim.startDelay = config.delay
                }
                animators.add(alphaAnim)
            }
        }
        return animators
    }

    /** 温度变化时的滚动/抖动动画，原版 `a(Weather, Weather)`。 */
    private fun playTempChangeAnimation(weather: Weather, weather2In: Weather?) {
        var weather2 = weather2In
        if (weather2 == null) {
            weather2 = weather
        }
        val changed: Boolean
        if (mCOrFSwitchView!!.isChecked) {
            changed = !weather2.observe!!.getTempF().equals(weather.observe!!.getTempF())
            changeValueForC2F()
        } else {
            changed = !weather2.observe!!.getTempC().equals(weather.observe!!.getTempC())
            changeValueForF2C()
        }
        if (changed) {
            tempScrollView!!.setInterpolator(OvershootInterpolator(0.5f))
            val tempArr = if (!mCOrFSwitchView!!.isChecked)
                getTempStringArray(weather.observe!!.getTempC(), weather2.observe!!.getTempC())
            else
                getTempStringArray(weather.observe!!.getTempF(), weather2.observe!!.getTempF())
            tempScrollView!!.removeAllViews()
            if (tempArr != null) {
                for (str in tempArr) {
                    val temView = inflater.inflate(R.layout.weather_main_temp_anim_layout, null, false) as WeatherMainTemView
                    temView.setAnimTemp(str)
                    tempScrollView!!.addView(
                        temView,
                        LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ctx.resources.getDimensionPixelSize(R.dimen.vp_temp_view_height))
                    )
                }
            }
            tempScrollView!!.setScrollEndListener(object : SmartisanScrollView.ScrollEndListener {
                override fun onScrollEnd() {
                    tempAnimView!!.visibility = View.VISIBLE
                    tempScrollView!!.visibility = View.GONE
                    post { tempScrollView!!.removeAllViews() }
                }
            })
            tempAnimView!!.visibility = View.INVISIBLE
            tempScrollView!!.visibility = View.VISIBLE
            post { tempScrollView!!.startUpScroll() }
            return
        }
        val y = tempAnimView!!.y
        val shakeDistance = ctx.resources.getDimensionPixelSize(R.dimen.temp_text_shake_distance).toFloat()
        val up = y - shakeDistance
        val animUp = ObjectAnimator.ofFloat(tempAnimView, "y", y, up)
        animUp.duration = 125L
        val down = shakeDistance + y
        val animDown = ObjectAnimator.ofFloat(tempAnimView, "y", up, down)
        animDown.duration = 250L
        val animBack = ObjectAnimator.ofFloat(tempAnimView, "y", down, y)
        animBack.duration = 250L
        animBack.interpolator = DecelerateInterpolator()
        val animatorSet = AnimatorSet()
        animatorSet.playSequentially(animUp, animDown, animBack)
        postDelayed({ animatorSet.start() }, 70L)
    }

    private fun playTempChangeAnimation(drawItem: DrawItem, drawItem2: DrawItem?) {
        try {
            playTempChangeAnimation(drawItem.weatherData!!, drawItem2?.weatherData)
        } catch (e: Exception) {
            DebugLog.log(DebugLog.TAG_EXCEPTION, "Temperature animation failed", e)
        }
    }

    /** 应用主题资源，原版 `a(String)`。 */
    private fun applyTheme(code: String) {
        val curTheme: ThemeBean = ThemeUtils.getCurTheme(code)
        themeType = curTheme.getThemeType() ?: ""
        centigradeIcon!!.setImageResource(curTheme.getcIconRes())
        fahrenheitIcon!!.setImageResource(curTheme.getfIconRes())
        mCOrFSwitchView!!.setBgRes(ctx, curTheme.getSwitchIcon(), curTheme.getSwitchIcon(), curTheme.getFrameIcon())
        circleAnimButton!!.setRefreshBgRes(curTheme.getRefreshBgRes())
        circleAnimButton!!.setRefreshSrcRes(curTheme.getRefreshSrcRes())
        addButton!!.setBackgroundResource(curTheme.getAddRes())
        listButton!!.setBackgroundResource(curTheme.getListRes())
        topBoxBg!!.setBackgroundResource(curTheme.getInfoBgRes())
        bottomGroup!!.setBackgroundResource(curTheme.getForecastBgRes())
        aqiDescView!!.setTextColor(
            androidx.core.content.ContextCompat.getColor(ctx, curTheme.getAqiTextColor()),
        )
        aqiDescView!!.setBackgroundResource(curTheme.getTipsIcon())
    }

    /** 同步预警 ViewFlipper 子视图与最新预警列表，原版 `a(ArrayList, ViewFlipper)`。 */
    private fun syncAlertViews(alerts: ArrayList<AlertInfo>, flipper: ViewFlipper) {
        for (i in flipper.childCount - 1 downTo 0) {
            val child = flipper.getChildAt(i)
            val tag = child.tag as AlertInfo?
            if (tag != null && !alerts.contains(tag)) {
                flipper.removeView(child)
            }
        }
        for (alertInfo in alerts) {
            var exists = false
            for (i in 0 until flipper.childCount) {
                val tag = flipper.getChildAt(i).tag as AlertInfo?
                if (tag != null && alertInfo == tag) {
                    exists = true
                }
            }
            if (!exists && !TextUtils.isEmpty(alertInfo.getContent())) {
                val view = View.inflate(ctx, R.layout.weather_alert_item, null)
                val textView = view.findViewById<TextView>(R.id.alert_content)!!
                textView.text = String.format(
                    ctx.resources.getString(R.string.weather_alert),
                    alertInfo.getType(), alertInfo.getLevel(), alertInfo.getContent()
                )
                textView.ellipsize = TextUtils.TruncateAt.END
                view.tag = alertInfo
                flipper.addView(view)
            }
        }
    }

    /** 构建温度向上滚动的子视图序列，原版 `a(String[])`。 */
    private fun buildUpScrollViews(arr: Array<String>?) {
        if (arr == null) return
        val temView = inflater.inflate(R.layout.weather_main_temp_anim_layout, null, false) as WeatherMainTemView
        temView.setAnimTemp(arr[0])
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ctx.resources.getDimensionPixelSize(R.dimen.vp_temp_view_height))
        tempScrollView!!.addView(temView, lp)
        for (i in 1..2) {
            tempScrollView!!.addView(createScrollNumView(arr[i].toInt(), i), lp)
        }
        val blurryHeight = ctx.resources.getDimensionPixelSize(R.dimen.scroll_tempvalue_mohu_height)
        val lpBlurry = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, blurryHeight)
        val blurCount1 = getDigitCount(arr[2])
        val blurCount2 = getDigitCount(arr[arr.size - 3])
        val blurry1 = createBlurryView(blurCount1)
        val blurry2 = createBlurryView(blurCount2)
        blurry1.layoutParams = lpBlurry
        tempScrollView!!.addView(blurry1, lpBlurry)
        val marginTop = ctx.resources.getDimensionPixelOffset(R.dimen.weather_temp_blurry_margin_top)
        val lpBlurry2 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, blurryHeight)
        lpBlurry2.setMargins(0, marginTop, 0, 0)
        blurry2.layoutParams = lpBlurry2
        tempScrollView!!.addView(blurry2, lpBlurry)
        tempScrollView!!.addView(createScrollNumView(arr[arr.size - 3].toInt(), 2), lp)
        tempScrollView!!.addView(createScrollNumView(arr[arr.size - 2].toInt(), 1), lp)
        val temView2 = inflater.inflate(R.layout.weather_main_temp_anim_layout, null, false) as WeatherMainTemView
        temView2.setAnimTemp(arr[arr.size - 1])
        tempScrollView!!.addView(temView2, lp)
    }

    /** 构建温度向下滚动的子视图序列，原版 `b(String[])`。 */
    private fun buildDownScrollViews(arr: Array<String>?) {
        if (arr == null) return
        val temView = inflater.inflate(R.layout.weather_main_temp_anim_layout, null, false) as WeatherMainTemView
        temView.setAnimTemp(arr[arr.size - 1])
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ctx.resources.getDimensionPixelSize(R.dimen.vp_temp_view_height))
        tempScrollView!!.addView(temView, lp)
        tempScrollView!!.addView(createScrollNumView(arr[arr.size - 2].toInt(), 1), lp)
        tempScrollView!!.addView(createScrollNumView(arr[arr.size - 3].toInt(), 2), lp)
        val blurryHeight = ctx.resources.getDimensionPixelSize(R.dimen.scroll_tempvalue_mohu_height)
        val lpBlurry = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, blurryHeight)
        val blurCount1 = getDigitCount(arr[arr.size - 3])
        val blurCount2 = getDigitCount(arr[2])
        val blurry1 = createBlurryView(blurCount1)
        val blurry2 = createBlurryView(blurCount2)
        blurry1.layoutParams = lpBlurry
        tempScrollView!!.addView(blurry1, lpBlurry)
        val marginTop = ctx.resources.getDimensionPixelOffset(R.dimen.weather_temp_blurry_margin_top)
        val lpBlurry2 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, blurryHeight)
        lpBlurry2.setMargins(0, marginTop, 0, 0)
        blurry2.layoutParams = lpBlurry2
        tempScrollView!!.addView(blurry2, lpBlurry)
        for (i in 2 downTo 1) {
            tempScrollView!!.addView(createScrollNumView(arr[i].toInt(), i), lp)
        }
        val temView2 = inflater.inflate(R.layout.weather_main_temp_anim_layout, null, false) as WeatherMainTemView
        temView2.setAnimTemp(arr[0])
        tempScrollView!!.addView(temView2, lp)
    }

    /** 生成从 str 到 str2 的温度整数序列；差值 <2 返回 null。 */
    private fun getTempStringArray(str: String?, str2: String?): Array<String>? {
        if (!TextUtils.isEmpty(str) && !TextUtils.isEmpty(str2)) {
            return try {
                val from = str!!.toInt()
                val to = str2!!.toInt()
                val diff = Math.abs(from - to)
                if (diff < 2) return null
                val arr = Array(diff + 1) { "" }
                val delta = to - from
                if (delta > 0) {
                    var i = 0
                    while (i <= delta) {
                        arr[i] = (from + i).toString()
                        i++
                    }
                } else {
                    var i = 0
                    while (i <= Math.abs(delta)) {
                        arr[i] = (from - i).toString()
                        i++
                    }
                }
                arr
            } catch (e: NumberFormatException) {
                DebugLog.log("Excepition", "getTempStringArray", e)
                null
            }
        }
        return null
    }

    /** 返回数字的位数（百/十/个 → 3/2/1，0 → 0），原版 `b(String)`。 */
    private fun getDigitCount(str: String): Int {
        val value = str.toInt()
        val hundreds = Math.abs(value / 100)
        val tens = Math.abs((value % 100) / 10)
        val units = Math.abs(value % 10)
        return when {
            hundreds > 0 -> 3
            tens > 0 -> 2
            units > 0 -> 1
            else -> 0
        }
    }

    /** 触感反馈，替代原版 `VibratorSmt.vibrateEffect(vibrator, 0)`。 */
    private fun vibrateEffect() {
        val v = vibrator ?: return
        v.vibrate(VibrationEffect.createOneShot(HAPTIC_DURATION_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun performTemperatureUnitHapticFeedback() {
        vibrateEffect()
    }

    /** 更新 C/F 开关的无障碍描述。 */
    private fun updateCorFContentDescription() {
        val switch = mCOrFSwitchView!!
        switch.contentDescription = ctx.getString(
            if (switch.isChecked) R.string.fahrenheit_selected else R.string.celsius_selected
        )
    }

    /** C/F 切换时的温度滚动动画，原版 `g()`。 */
    private fun playCorFScrollAnimation() {
        val drawItem = mDrawItem ?: return
        if (drawItem.weatherData == null) return
        val weather = mDrawItem!!.weatherData!!
        tempScrollView!!.removeAllViews()
        tempScrollView!!.setInterpolator(OvershootInterpolator(0.5f))
        val tempArr: Array<String>?
        if (mCOrFSwitchView!!.isChecked) {
            tempArr = getTempStringArray(weather.observe!!.getTempC(), weather.observe!!.getTempF())
            buildUpScrollViews(tempArr)
            weather.observe!!.getTempF()
        } else {
            tempArr = getTempStringArray(weather.observe!!.getTempF(), weather.observe!!.getTempC())
            buildDownScrollViews(tempArr)
            weather.observe!!.getTempC()
        }
        if (tempArr == null) {
            tempAnimView!!.isSelected = mCOrFSwitchView!!.isChecked
            return
        }
        val fadeIn = ObjectAnimator.ofFloat(tempAnimView!!.findViewById(R.id.imageView_temptype), "alpha", 0.0f, 1.0f)
        fadeIn.interpolator = DecelerateInterpolator()
        fadeIn.duration = 300L
        tempScrollView!!.reset()
        tempScrollView!!.setScrollEndListener(object : SmartisanScrollView.ScrollEndListener {
            override fun onScrollEnd() {
                tempAnimView!!.visibility = View.VISIBLE
                tempScrollView!!.visibility = View.INVISIBLE
                post {
                    fadeIn.start()
                    tempScrollView!!.removeAllViews()
                    setAllViewClickable(true)
                }
            }
        })
        val fadeOut = ObjectAnimator.ofFloat(tempAnimView!!.findViewById(R.id.imageView_temptype), "alpha", 1.0f, 0.0f)
        fadeOut.duration = 50L
        fadeOut.interpolator = DecelerateInterpolator()
        fadeOut.addListener(object : AnimatorListenerImpl() {
            override fun onAnimationEnd(animation: Animator) {
                tempAnimView!!.isSelected = mCOrFSwitchView!!.isChecked
            }
        })
        fadeOut.start()
        post {
            if (mCOrFSwitchView!!.isChecked) {
                tempScrollView!!.startUpScroll()
            } else {
                tempScrollView!!.startDownScroll()
            }
        }
        tempScrollView!!.visibility = View.VISIBLE
        tempAnimView!!.visibility = View.INVISIBLE
    }

    private fun hideSelf() {
        visibility = View.GONE
    }

    private fun setPageViewAlpha(visible: Boolean) {
        val alpha = if (visible) 1.0f else 0.0f
        centigradeIcon!!.alpha = alpha
        fahrenheitIcon!!.alpha = alpha
        mCOrFSwitchView!!.alpha = alpha
        listButton!!.alpha = alpha
        addButton!!.alpha = alpha
        circleAnimButton!!.setAlpha(alpha)
        topBoxBg!!.alpha = alpha
        bottomGroup!!.alpha = alpha
        cityNameLine!!.alpha = alpha
    }

    private fun setPageViewVisibility(visibility: Int) {
        centigradeIcon!!.visibility = visibility
        fahrenheitIcon!!.visibility = visibility
        mCOrFSwitchView!!.visibility = visibility
        listButton!!.visibility = visibility
        addButton!!.visibility = visibility
        circleAnimButton!!.setVisibility(visibility)
        topBoxBg!!.visibility = visibility
        bottomGroup!!.visibility = visibility
        cityNameLine!!.visibility = visibility
    }

    fun changeValueForC2F() {
        val drawItem = mDrawItem ?: return
        if (drawItem.weatherData == null) return
        val weather = mDrawItem!!.weatherData!!
        tempAnimView!!.setTemp(weather.observe!!.getTempC() ?: "", weather.observe!!.getTempF() ?: "", true)
    }

    fun changeValueForF2C() {
        val drawItem = mDrawItem ?: return
        if (drawItem.weatherData == null) return
        val weather = mDrawItem!!.weatherData!!
        tempAnimView!!.setTemp(weather.observe!!.getTempC() ?: "", weather.observe!!.getTempF() ?: "", false)
    }

    fun doContentAnimationOutBoth(z: Boolean, z2: Boolean) {
        val animators = arrayListOf<Animator>()
        controller!!.setScrollale(false)
        mIndicateView!!.visibility = View.VISIBLE
        animators.addAll(buildAnimators(OUT_ANIM_CONFIG, arrayOf(mIndicateView!!)))
        val animatorSet = AnimatorSet()
        animatorSet.interpolator = AccelerateDecelerateInterpolator()
        if (emptyGroupTop!!.visibility == View.VISIBLE) {
            animators.addAll(buildAnimators(AnimConfig(3, false, z2, 0L, false, "gone"), arrayOf(emptyGroupTop!!)))
        } else {
            animators.addAll(buildAnimators(AnimConfig(3, false, z2, 0L, false, "gone"), arrayOf(topBox!!, mForecastContentView!!, jumpForecastButton!!)))
        }
        animators.addAll(buildAnimators(AnimConfig(3, false, z2, 0L, false, ""), arrayOf(cityNameView!!)))
        visibility = View.VISIBLE
        if (z) {
            setPageViewVisibility(View.VISIBLE)
            animators.addAll(
                buildAnimators(
                    AnimConfig(1, false, z2, 50L, false, "", Runnable { hideSelf() }),
                    arrayOf(
                        centigradeIcon!!, fahrenheitIcon!!, mCOrFSwitchView!!, listButton!!,
                        addButton!!, circleAnimButton!!.button!!, circleAnimButton!!.image!!,
                        topBoxBg!!, bottomGroup!!, cityNameLine!!
                    )
                )
            )
        } else {
            animatorSet.addListener(object : AnimatorListenerImpl() {
                override fun onAnimationEnd(animation: Animator) {
                    visibility = View.GONE
                }
            })
            setPageViewVisibility(View.INVISIBLE)
        }
        animatorSet.playTogether(animators)
        animatorSet.start()
    }

    fun doContentInAnimationBoth(z: Boolean, z2: Boolean) {
        val animators = arrayListOf<Animator>()
        mIndicateView!!.visibility = View.VISIBLE
        animators.addAll(buildAnimators(AnimConfig(1, true, z2, 0L, true, "visible"), arrayOf(mIndicateView!!)))
        if (emptyGroupTop!!.visibility == View.VISIBLE) {
            animators.addAll(buildAnimators(AnimConfig(3, true, z2, 50L, true, "visible"), arrayOf(emptyGroupTop!!)))
        } else {
            animators.addAll(buildAnimators(AnimConfig(3, true, z2, 50L, true, "visible"), arrayOf(topBox!!, mForecastContentView!!, jumpForecastButton!!)))
        }
        animators.addAll(buildAnimators(AnimConfig(3, true, z2, 50L, true, "visible"), arrayOf(cityNameView!!)))
        visibility = View.VISIBLE
        val animatorSet = AnimatorSet()
        animatorSet.addListener(object : AnimatorListenerImpl() {
            override fun onAnimationEnd(animation: Animator) {
                visibility = View.VISIBLE
                controller!!.setScrollale(true)
            }
        })
        if (z) {
            val views = arrayOf(
                centigradeIcon!!, fahrenheitIcon!!, mCOrFSwitchView!!, listButton!!,
                addButton!!, circleAnimButton!!.button!!, circleAnimButton!!.image!!,
                topBoxBg!!, bottomGroup!!, cityNameLine!!
            )
            setPageViewVisibility(View.VISIBLE)
            animators.addAll(buildAnimators(AnimConfig(1, true, z2, 0L, true, "visible"), views))
        } else {
            setPageViewVisibility(View.VISIBLE)
            setPageViewAlpha(true)
        }
        animatorSet.playTogether(animators)
        animatorSet.start()
    }

    fun fillViewWithData(drawItem: DrawItem?) {
        DebugLog.log(TAG, "fillViewWithData")
        if (drawItem == null || drawItem.weatherData == null || drawItem.weatherData!!.isEmpty()) {
            setEmptyType(EMPTY_TYPE_LOAD_ERROR)
            DebugLog.log(TAG, "fillViewWithData return")
            return
        }
        topBox!!.visibility = View.VISIBLE
        jumpForecastButton!!.visibility = View.VISIBLE
        mForecastContentView!!.visibility = View.VISIBLE
        emptyGroupTop!!.visibility = View.GONE
        emptyType = -1
        setEmptyViewButtonDisable(true)
        mForecastContentView!!.scrollY = 0
        Utility.insertSunriseAndSunset(ctx, drawItem.weatherData)
        mDrawItem = drawItem
        mCOrFSwitchView!!.setOnCheckedChangeListener(null)
        val weather = drawItem.weatherData!!
        applyTheme(weather.observe!!.getCode()!!)
        cityNameView!!.text = getCityDisplayName(drawItem.locationData)
        val systemTempUnit = Utility.getSystemTemperatureUnit(ctx)
        tempScrollView!!.visibility = View.VISIBLE
        tempScrollView!!.alpha = 1.0f
        if (forecastAdapter == null) {
            forecastAdapter = WeatherForecastRecyclerAdapter(ctx, forecastRecyclerView!!)
        }
        var lowC = ctx.getString(R.string.weather_null)
        var lowF = ctx.getString(R.string.weather_null)
        var highC = ctx.getString(R.string.weather_null)
        var highF = ctx.getString(R.string.weather_null)
        if (!"UNKNOWN".equals(weather.observe!!.getLowTempC())) {
            lowC = weather.observe!!.getLowTempC() + "°"
        }
        if (!"UNKNOWN".equals(weather.observe!!.getLowTempC())) {
            lowF = weather.observe!!.getLowTempF() + "°"
        }
        if (!"UNKNOWN".equals(weather.observe!!.getLowTempC())) {
            highC = weather.observe!!.getHighTempC() + "°"
        }
        if (!"UNKNOWN".equals(weather.observe!!.getLowTempC())) {
            highF = weather.observe!!.getHighTempF() + "°"
        }
        lowCTextView!!.text = lowC
        lowFTextView!!.text = lowF
        highCTextView!!.text = highC
        highFTextView!!.text = highF
        if (systemTempUnit == 1) {
            mCOrFSwitchView!!.isChecked = false
            fahrenheitIcon!!.isSelected = false
            centigradeIcon!!.isSelected = true
            tempAnimView!!.setTemp(weather.observe!!.getTempC() ?: "", weather.observe!!.getTempF() ?: "", false)
            lowTempAnimView!!.showCView(false)
            highTempAnimView!!.showCView(false)
            observerAdapter.setFahrenheitVisible(visible = false, animate = false)
            forecastAdapter!!.setFValuesVisiable(false)
        } else {
            tempAnimView!!.setTemp(weather.observe!!.getTempC() ?: "", weather.observe!!.getTempF() ?: "", true)
            mCOrFSwitchView!!.isChecked = true
            fahrenheitIcon!!.isSelected = true
            centigradeIcon!!.isSelected = false
            lowTempAnimView!!.showFView(false)
            highTempAnimView!!.showFView(false)
            observerAdapter.setFahrenheitVisible(visible = true, animate = false)
            forecastAdapter!!.setFValuesVisiable(true)
        }
        tempAnimView!!.visibility = View.VISIBLE
        tempScrollView!!.visibility = View.INVISIBLE
        updateCorFContentDescription()
        mCOrFSwitchView!!.setOnCheckedChangeListener(corfCheckedChangeListener)
        try {
            mWeatherTypeView!!.text = Utility.getWeatherDescByCode(ctx, weather.observe!!.getCode())
        } catch (e: Exception) {
            DebugLog.log(DebugLog.TAG_EXCEPTION, "Weather description binding failed", e)
        }
        if (TextUtils.isEmpty(weather.observe!!.getAqi())) {
            aqiTextView!!.visibility = View.GONE
            aqiDescView!!.visibility = View.GONE
        } else {
            aqiTextView!!.text = ctx.resources.getString(R.string.weather_pager_aqi_text) + weather.observe!!.getAqi()
            aqiDescView!!.setText(Utility.getAQIResId(ctx, weather.observe!!.getAqi()))
            aqiTextView!!.visibility = View.VISIBLE
            aqiDescView!!.visibility = View.VISIBLE
        }
        var updateTime = ctx.getString(R.string.weather_null)
        try {
            updateTime = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                .format(Date(weather.observe!!.getPubdate()!!.toLong()))
        } catch (e: Exception) {
            DebugLog.log(DebugLog.TAG_EXCEPTION, "Observation time formatting failed", e)
        }
        val alert = drawItem.weatherData!!.alert
        if (alert == null || alert.getmInfos() == null || alert.getmInfos()!!.isEmpty()) {
            if (alertFlipper!!.isFlipping) {
                alertFlipper!!.isAutoStart = false
                alertFlipper!!.stopFlipping()
            }
            alertFlipper!!.removeAllViews()
            alertGroup!!.visibility = View.GONE
        } else {
            alertGroup!!.visibility = View.VISIBLE
            syncAlertViews(alert.getmInfos()!!, alertFlipper!!)
            if (alertFlipper!!.childCount == 1) {
                if (alertFlipper!!.isFlipping) {
                    alertFlipper!!.isAutoStart = false
                    alertFlipper!!.stopFlipping()
                }
            } else if (!alertFlipper!!.isFlipping) {
                alertFlipper!!.isAutoStart = true
                alertFlipper!!.startFlipping()
            }
        }
        updateTimeView!!.text = String.format(ctx.resources.getString(R.string.weather_pager_update_text), updateTime)
        if (TextUtils.isEmpty(weather.observe!!.getCurrentWeekDay())) {
            weekView!!.text = ctx.getString(R.string.weather_null)
            lowCTextView!!.text = ctx.getString(R.string.weather_null)
            lowFTextView!!.text = ctx.getString(R.string.weather_null)
            highCTextView!!.text = ctx.getString(R.string.weather_null)
            highFTextView!!.text = ctx.getString(R.string.weather_null)
        } else {
            weekView!!.text = Utility.getLocaleWeekday(ctx, weather.observe!!.getCurrentWeekDay() ?: "")
        }
        observerAdapter.submitForecasts(weather.hourForecast!!.getmInfo()) {
            hourForecastView.scrollToPosition(0)
        }
        forecastAdapter!!.setData(weather, drawItem.locationData!!.mCountry)
        forecastRecyclerView!!.adapter = forecastAdapter
        partnerLabelView!!.text = Utility.getParnterText(ctx)
        setAllViewClickable(true)
    }

    fun fillViewWithData(drawItem: DrawItem, cityCount: Int, position: Int) {
        stopRefresh(drawItem)
        fillViewWithData(drawItem)
        initBottomDot(cityCount, position)
    }

    fun getContentTranslationX(): Float = cityNameView!!.translationX

    fun getThemeType(): String = themeType

    fun initBottomDot(cityCount: Int, position: Int) {
        if (cityCount > 0) {
            val drawItem = mDrawItem
            if (drawItem == null || drawItem.locationData == null) {
                mIndicateView!!.setShowLocation(false)
            } else {
                mIndicateView!!.setShowLocation(Utility.isLocalCity(mDrawItem!!.locationData))
                mIndicateView!!.contentDescription = String.format(
                    ctx.getString(R.string.current_location), mDrawItem!!.locationData!!.mLocationName
                )
            }
            mIndicateView!!.setState(cityCount, position)
        }
    }

    fun setHasLocationCity(hasLocationCity: Boolean) {
        mIndicateView?.setHasLocationCity(hasLocationCity)
    }

    fun initEmptyView(drawItem: DrawItem?, cityCount: Int, position: Int, emptyType: Int) {
        DebugLog.log(TAG, "initEmptyView  size - $cityCount  position - $position  emptyType - $emptyType")
        initBottomDot(cityCount, position)
        mDrawItem = drawItem
        setEmptyType(emptyType)
    }

    fun initViews() {
        mForecastContentView = findViewById(R.id.forecast_content)
        topBox = findViewById(R.id.viewgroup_topbox)
        topBoxBg = findViewById(R.id.viewgroup_topbox_bg)
        jumpForecastButton = findViewById(R.id.imagebutton_jump_weather_forecast)
        emptyGroupTop = findViewById(R.id.empty_group_top)
        progressBar = findViewById(R.id.empty_progress)
        mEmptyInfoView1 = findViewById(R.id.empty_info1)
        mEmptyInfoView2 = findViewById(R.id.empty_info2)
        mEmptyAlertIcon = findViewById(R.id.view_empty_alert)
        bottomGroup = findViewById(R.id.group_bottom)
        cityNameView = findViewById(R.id.textview_cityname)
        cityNameLine = findViewById(R.id.cityname_line)
        centigradeIcon = findViewById(R.id.imageview_centigrade)
        fahrenheitIcon = findViewById(R.id.imageview_fahrenheit)
        mCOrFSwitchView = findViewById(R.id.smartisan_switch)
        listButton = findViewById(R.id.imagebutton_list)
        addButton = findViewById(R.id.imagebutton_add)
        circleAnimButton = CircleAnimButton(findViewById(R.id.cirle_layout))
        circleAnimButton!!.setOnClickListener(this)
        tempAnimView = findViewById(R.id.weather_temp_anim)
        mWeatherTypeView = findViewById(R.id.textview_weather_text)!!
        mWeatherTypeView!!.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        aqiTextView = findViewById(R.id.textview_weather_aqi_text)
        aqiDescView = findViewById(R.id.imageview_weather_des)
        updateTimeView = findViewById(R.id.textview_weather_update_time)
        alertGroup = findViewById(R.id.group_alert)
        alertFlipper = findViewById(R.id.textview_weather_alert_group)
        weekView = findViewById(R.id.content_week)
        todayView = findViewById(R.id.content_today)
        lowCTextView = findViewById(R.id.content_tmp_lowc)
        lowFTextView = findViewById(R.id.content_tmp_lowf)
        highCTextView = findViewById(R.id.content_tmp_highc)
        highFTextView = findViewById(R.id.content_tmp_highf)
        lowTempAnimView = findViewById(R.id.viewflipper1)
        highTempAnimView = findViewById(R.id.viewflipper2)
        partnerLabelView = findViewById(R.id.content_lable)!!
        partnerLabelView!!.setOnClickListener(this)
        addButton!!.setOnClickListener(this)
        listButton!!.setOnClickListener(this)
        centigradeIcon!!.setOnClickListener(this)
        fahrenheitIcon!!.setOnClickListener(this)
        alertGroup!!.setOnClickListener(this)
        jumpForecastButton!!.setOnClickListener(this)
        tempScrollView = findViewById(R.id.linearlayout_temp_value)
        hourForecastView = findViewById(R.id.content_forecast_hour_recycler_view)
        hourForecastView.layoutManager = LinearLayoutManager(
            ctx,
            LinearLayoutManager.HORIZONTAL,
            false,
        )
        hourForecastView.itemAnimator = null
        hourForecastView.adapter = observerAdapter
        forecastRecyclerView = findViewById(R.id.lv_group)
        forecastLayoutManager = LinearLayoutManager(ctx)
        forecastLayoutManager!!.orientation = LinearLayoutManager.VERTICAL
        forecastRecyclerView!!.layoutManager = forecastLayoutManager
        mIndicateView = findViewById(R.id.indicate)
        MAX_ALPHA_TIME = ctx.resources.getDimensionPixelSize(R.dimen.weather_main_scroll_alpha_max)
    }

    fun isEmpty(): Boolean {
        val drawItem = mDrawItem ?: return true
        return drawItem.weatherData == null || mDrawItem!!.isEmpty
    }

    fun move(f: Float) {
        var abs = Math.abs(f)
        val max = MAX_ALPHA_TIME
        if (abs > max) {
            abs = max.toFloat()
        }
        val alpha = 1.0f - (abs / MAX_ALPHA_TIME)
        cityNameView!!.translationX = f
        cityNameView!!.alpha = alpha
        topBox!!.translationX = f
        topBox!!.alpha = alpha
        jumpForecastButton!!.translationX = f
        jumpForecastButton!!.alpha = alpha
        if (mForecastContentView!!.visibility == View.VISIBLE) {
            mForecastContentView!!.translationX = f
            mForecastContentView!!.alpha = alpha
        } else {
            emptyGroupTop!!.translationX = f
            emptyGroupTop!!.alpha = alpha
        }
    }

    override fun onClick(view: View) {
        if (ClickUtil.isFastClick()) {
            return
        }
        val id = view.id
        if (id == R.id.content_lable) {
            DebugLog.log(TAG, "click parnter view")
            controller!!.openParnterDetailPage()
            return
        }
        if (id == R.id.group_alert) {
            DebugLog.log(TAG, "click alert view")
            controller!!.openAlerDetailPage()
            return
        }
        when (id) {
            R.id.imagebutton_add -> {
                DebugLog.log(TAG, "click add view")
                val drawItem = mDrawItem
                if (drawItem != null) {
                    controller!!.startAddCity(drawItem.locationData!!)
                }
            }
            R.id.imagebutton_jump_weather_forecast -> {
                DebugLog.log(TAG, "click jump view")
                controller!!.openParnterDetailPage()
            }
            R.id.imagebutton_list -> {
                DebugLog.log(TAG, "click city list view")
                controller!!.openCityLisPage()
            }
            R.id.imagebutton_refresh -> {
                DebugLog.log(TAG, "click refresh view")
                if (!Utility.isNetworkConnected(ctx)) {
                    Toast.makeText(ctx, R.string.weather_click_refresh_no_network, Toast.LENGTH_SHORT).show()
                } else {
                    isRefreshAnimating = true
                    startCircleAnim()
                    setAllViewClickable(false)
                    controller!!.refreshCurrentCity()
                }
            }
            R.id.imageview_centigrade -> {
                DebugLog.log(TAG, "click c view")
                if (mCOrFSwitchView!!.isChecked) {
                    mCOrFSwitchView!!.performClick()
                }
            }
            R.id.imageview_fahrenheit -> {
                DebugLog.log(TAG, "click f view")
                if (!mCOrFSwitchView!!.isChecked) {
                    mCOrFSwitchView!!.performClick()
                }
            }
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        vibrator = ctx.getSystemService(Vibrator::class.java)
        inflater = LayoutInflater.from(ctx)
        translateDistanceOut = context.resources.getDimensionPixelOffset(R.dimen.weather_main_page_translate_distance_out)
        translateDistanceIn = context.resources.getDimensionPixelOffset(R.dimen.weather_main_page_translate_distance_in)
        initViews()
    }

    fun playAnimationCorF(useFahrenheit: Boolean = mCOrFSwitchView?.isChecked == true) {
        val drawItem = mDrawItem ?: return
        if (drawItem.weatherData?.observe == null) return
        if (mCOrFSwitchView!!.isChecked != useFahrenheit) {
            // setChecked 会触发统一的监听器；监听器随后以新状态执行一次动画。
            mCOrFSwitchView!!.isChecked = useFahrenheit
            return
        }
        setAllViewClickable(false)
        observerAdapter.setFahrenheitVisible(visible = useFahrenheit, animate = true)
        forecastAdapter!!.updateHourForecastValue(useFahrenheit)
        if (useFahrenheit) {
            lowTempAnimView!!.showFView(true)
            highTempAnimView!!.showFView(true)
            tempAnimView!!.setChecked(true)
        } else {
            lowTempAnimView!!.showCView(true)
            highTempAnimView!!.showCView(true)
            tempAnimView!!.setChecked(false)
        }
        playCorFScrollAnimation()
    }

    fun resetPageState() {
        DebugLog.log(TAG, "resetPageState")
        stopCircleAnim()
        isRefreshAnimating = false
    }

    fun setAllViewClickable(z: Boolean) {
        controller!!.setScrollale(z)
    }

    fun setEmptyType(type: Int) {
        DebugLog.log(TAG, "setEmptyType  type - $type")
        val drawItem = mDrawItem
        if (drawItem != null && drawItem.weatherData != null && mDrawItem!!.weatherData!!.isDataComplete()) {
            DebugLog.log(TAG, "setEmptyType  return")
            fillViewWithData(mDrawItem)
            return
        }
        emptyType = type
        if (emptyType == EMPTY_TYPE_LOADING) {
            mEmptyInfoView1!!.text = ""
            mEmptyInfoView2!!.text = ""
            mEmptyAlertIcon!!.visibility = View.GONE
            progressBar!!.visibility = View.VISIBLE
        } else if (emptyType == EMPTY_TYPE_LOAD_ERROR) {
            mEmptyInfoView1!!.setText(R.string.update_failed_network_unavailable1)
            val di = mDrawItem
            if (di == null || di.locationData == null || mDrawItem!!.locationData!!.sortOrder != 1) {
                mEmptyInfoView2!!.setText(R.string.update_failed_network_unavailable2)
            } else {
                mEmptyInfoView2!!.setText(R.string.update_failed_gps_unavailable)
            }
            mEmptyAlertIcon!!.visibility = View.VISIBLE
            progressBar!!.visibility = View.GONE
        }
        topBox!!.visibility = View.GONE
        jumpForecastButton!!.visibility = View.GONE
        mForecastContentView!!.visibility = View.GONE
        emptyGroupTop!!.visibility = View.VISIBLE
        setEmptyViewButtonDisable(false)
        val di3 = mDrawItem
        if (di3 == null || di3.locationData == null) {
            cityNameView!!.text = ""
        } else if (
            mDrawItem!!.isDataComplete() &&
            mDrawItem!!.locationData!!.sortOrder == 1
        ) {
            cityNameView!!.text = ""
        } else {
            cityNameView!!.text = getCityDisplayName(mDrawItem!!.locationData)
        }
        cityNameView!!.contentDescription = cityNameView!!.text
        val systemTempUnit = Utility.getSystemTemperatureUnit(ctx)
        if (systemTempUnit == 1) {
            mCOrFSwitchView!!.isChecked = false
        } else if (systemTempUnit == 2) {
            mCOrFSwitchView!!.isChecked = true
        }
        applyTheme("000")
    }

    fun setEmptyViewButtonDisable(z: Boolean) {
        DebugLog.log(TAG, "setEmptyViewButtonDisable")
        mCOrFSwitchView!!.isEnabled = z
        centigradeIcon!!.isClickable = z
        fahrenheitIcon!!.isClickable = z
    }

    fun setPageController(abstractController: AbstractController?) {
        controller = abstractController
    }

    fun startCircleAnim() {
        DebugLog.log(TAG, "startCircleAnim")
        circleAnimButton?.run()
    }

    fun stopCircleAnim() {
        DebugLog.log(TAG, "stopCircleAnim")
        circleAnimButton?.stop()
    }

    fun stopRefresh(drawItem: DrawItem?) {
        DebugLog.log(TAG, "stopRefresh  mIsShowRefreshAnim - $isRefreshAnimating")
        if (isRefreshAnimating) {
            setAllViewClickable(true)
            stopCircleAnim()
            if (drawItem != null) {
                try {
                    DebugLog.log(TAG, "stopRefresh  playAnimationRefresh")
                    playTempChangeAnimation(mDrawItem!!, drawItem)
                } catch (e: Exception) {
                    DebugLog.log(DebugLog.TAG_EXCEPTION, "Refresh animation failed", e)
                }
            } else {
                setEmptyType(EMPTY_TYPE_LOAD_ERROR)
            }
            isRefreshAnimating = false
        }
    }
}
