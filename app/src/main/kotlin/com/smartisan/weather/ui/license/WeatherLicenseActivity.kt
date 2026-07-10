package com.smartisan.weather.ui.license

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import com.smartisan.weather.R
import com.smartisan.weather.widget.TitleBar
import com.smartisan.weather.util.centeredPhoneContentInsets

/**
 * Smartisan 隐私政策与最终用户许可协议页面。
 *
 * 原版由首次隐私提示中的两个链接进入，同一个 Activity 根据 [EXTRA_TYPE]
 * 加载对应的本地 HTML；页面本身不使用城市页的上推/下滑转场。
 */
class WeatherLicenseActivity : ComponentActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val assetUrl = resolveAssetUrl(intent.getStringExtra(EXTRA_TYPE)) ?: run {
            finish()
            return
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContentView(R.layout.activity_license)

        val root = findViewById<View>(R.id.license_root)
        val titleBar = findViewById<TitleBar>(R.id.title_bar)
        webView = findViewById(R.id.webview)

        // activity_license.xml 已单独放置原版标题栏阴影，关闭本地 TitleBar 内建阴影。
        titleBar.setShadowVisible(false)
        titleBar
            .addLeftImageView(R.drawable.standard_icon_back_selector)
            .apply {
                contentDescription = getString(R.string.cancel)
                setOnClickListener { finish() }
            }

        webView.settings.apply {
            useWideViewPort = true
            javaScriptEnabled = false
            setSupportZoom(false)
        }
        webView.loadUrl(assetUrl)

        applySystemBarInsets(root)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.removeAllViews()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun applySystemBarInsets(root: View) {
        val initialRootPaddingLeft = root.paddingLeft
        val initialRootPaddingTop = root.paddingTop
        val initialRootPaddingRight = root.paddingRight
        val initialRootPaddingBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safeDrawing = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout(),
            )
            val horizontalInsets = view.centeredPhoneContentInsets(safeDrawing)
            view.updatePadding(
                left = initialRootPaddingLeft + horizontalInsets.left,
                top = initialRootPaddingTop + safeDrawing.top,
                right = initialRootPaddingRight + horizontalInsets.right,
                bottom = initialRootPaddingBottom + safeDrawing.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
        root.doOnLayout { ViewCompat.requestApplyInsets(it) }
    }

    companion object {
        private const val EXTRA_TYPE = "type"
        private const val TYPE_LICENSE = "type_license"

        // 原版协议值就是 provacy 的拼写；仅保留在页面内部，不暴露为新 API。
        private const val TYPE_PRIVACY = "type_provacy"
        private const val ASSET_ROOT = "file:///android_asset/"
        private const val LICENSE_ASSET = "smartisan_os_user_license.html"
        private const val PRIVACY_ASSET = "smartisan_os_privacy_policy.html"

        fun createLicenseIntent(context: Context): Intent =
            createIntent(context, TYPE_LICENSE)

        fun createPrivacyPolicyIntent(context: Context): Intent =
            createIntent(context, TYPE_PRIVACY)

        private fun createIntent(context: Context, type: String): Intent =
            Intent(context, WeatherLicenseActivity::class.java).putExtra(EXTRA_TYPE, type)

        private fun resolveAssetUrl(type: String?): String? = when (type) {
            TYPE_LICENSE -> ASSET_ROOT + LICENSE_ASSET
            TYPE_PRIVACY -> ASSET_ROOT + PRIVACY_ASSET
            else -> null
        }
    }
}
