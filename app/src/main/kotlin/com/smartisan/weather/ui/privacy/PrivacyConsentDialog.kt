package com.smartisan.weather.ui.privacy

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.smartisan.weather.R
import com.smartisan.weather.ui.license.WeatherLicenseActivity

/** 原版首次启动隐私提示；协议链接继续使用 APK 内置的离线 HTML。 */
class PrivacyConsentDialog(
    private val activity: Activity,
    private val onAgree: () -> Unit,
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
        setContentView(R.layout.weather_privacy_dialog)

        findViewById<TextView>(R.id.privacy_dialog_message).apply {
            text = buildPrivacyMessage()
            movementMethod = LinkMovementMethod.getInstance()
            highlightColor = Color.TRANSPARENT
        }
        findViewById<View>(R.id.privacy_dialog_negative).setOnClickListener {
            dismiss()
            activity.finish()
        }
        findViewById<View>(R.id.privacy_dialog_positive).setOnClickListener {
            dismiss()
            onAgree()
        }

        window?.apply {
            setBackgroundDrawableResource(R.drawable.dialog_full_smartisanos_light)
            setGravity(Gravity.CENTER)
            setDimAmount(0.5f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setWindowAnimations(R.style.PrivacyDialogAnimation)
        }
    }

    private fun buildPrivacyMessage(): CharSequence {
        val agreement = activity.getString(R.string.weather_privacy_statement_tip_user_agreement)
        val policy = activity.getString(R.string.weather_privacy_statement_tip_privacy_policy)
        val message = activity.getString(
            R.string.weather_privacy_statement_tip_message,
            agreement,
            policy,
        )
        return SpannableString(message).apply {
            addAgreementLink(this, message, agreement)
            addPrivacyLink(this, message, policy)
        }
    }

    private fun addAgreementLink(text: SpannableString, message: String, label: String) {
        addLink(text, message, label) {
            activity.startActivity(WeatherLicenseActivity.createLicenseIntent(activity))
        }
    }

    private fun addPrivacyLink(text: SpannableString, message: String, label: String) {
        addLink(text, message, label) {
            activity.startActivity(WeatherLicenseActivity.createPrivacyPolicyIntent(activity))
        }
    }

    private fun addLink(
        text: SpannableString,
        message: String,
        label: String,
        onClick: () -> Unit,
    ) {
        val start = message.lastIndexOf(label)
        if (start < 0) return
        text.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) = onClick()

                override fun updateDrawState(drawState: TextPaint) {
                    drawState.color = ContextCompat.getColor(
                        activity,
                        R.color.privacy_dialog_positive_text,
                    )
                    drawState.isUnderlineText = false
                }
            },
            start,
            start + label.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
}
