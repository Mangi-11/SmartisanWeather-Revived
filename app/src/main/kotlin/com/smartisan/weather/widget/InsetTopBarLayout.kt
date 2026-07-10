package com.smartisan.weather.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import com.smartisan.weather.util.safeDrawingInsets

/**
 * Draws a top-app-bar surface behind the status bar while keeping its content
 * below system bars and display cutouts.
 */
class InsetTopBarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val contentPaddingTop = paddingTop

    init {
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            view.updatePadding(top = contentPaddingTop + insets.safeDrawingInsets().top)
            insets
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }
}
