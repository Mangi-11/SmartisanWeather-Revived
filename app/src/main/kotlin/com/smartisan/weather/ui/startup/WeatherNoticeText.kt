package com.smartisan.weather.ui.startup

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/** Preserve the original resource's bold emphasis instead of flattening getText to a String. */
@Composable
internal fun weatherNoticeText(@StringRes resId: Int): AnnotatedString {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    return remember(resources, configuration, resId) {
        val text = resources.getText(resId)
        buildAnnotatedString {
            append(text.toString())
            if (text is Spanned) {
                text.getSpans(0, text.length, StyleSpan::class.java).forEach { span ->
                    addStyle(
                        SpanStyle(
                            fontWeight = if (span.style and Typeface.BOLD != 0) FontWeight.Bold else null,
                            fontStyle = if (span.style and Typeface.ITALIC != 0) FontStyle.Italic else null,
                        ),
                        start = text.getSpanStart(span),
                        end = text.getSpanEnd(span),
                    )
                }
            }
        }
    }
}
