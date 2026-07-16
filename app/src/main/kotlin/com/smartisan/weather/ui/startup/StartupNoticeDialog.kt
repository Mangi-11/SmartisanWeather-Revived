package com.smartisan.weather.ui.startup

import android.app.Activity
import android.app.Dialog
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.smartisan.weather.R
import com.smartisan.weather.util.ThemeUtils

/** 首次使用说明；用户确认继续前不访问天气网络数据。 */
class StartupNoticeDialog(
    private val activity: Activity,
    private val onContinue: () -> Unit,
) : Dialog(activity) {

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dismiss()
                activity.finish()
                true
            } else {
                false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.weather_startup_notice_dialog)

        findViewById<TextView>(R.id.startup_notice_message)
            .setText(R.string.weather_startup_notice_message)
        findViewById<View>(R.id.startup_notice_exit).setOnClickListener {
            dismiss()
            activity.finish()
        }
        findViewById<View>(R.id.startup_notice_continue).setOnClickListener {
            dismiss()
            onContinue()
        }

        val originalDialogBackground =
            ContextCompat.getDrawable(
                activity,
                R.drawable.dialog_full_smartisanos_light,
            )?.mutate()?.apply {
                if (ThemeUtils.isNightMode(activity)) {
                    colorFilter = PorterDuffColorFilter(
                        ContextCompat.getColor(activity, R.color.app_surface_color),
                        PorterDuff.Mode.MULTIPLY,
                    )
                }
            }
        window?.apply {
            setBackgroundDrawable(originalDialogBackground)
            setGravity(Gravity.CENTER)
            setDimAmount(0.5f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setWindowAnimations(R.style.PrivacyDialogAnimation)
        }
    }
}
