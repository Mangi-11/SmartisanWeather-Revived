package com.smartisan.weather.ui

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Full-window evidence for manual visual QA; assertions remain in the individual tests. */
internal fun saveVerificationScreenshot(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val directory = requireNotNull(instrumentation.targetContext.getExternalFilesDir("compose-verification"))
    directory.mkdirs()
    val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
    File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
}
