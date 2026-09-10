package com.smartisan.weather.ui.main

internal const val AUTOMATIC_LOCATION_INTERVAL_MILLIS = 5L * 60L * 1_000L

/** Use monotonic time; manual requests bypass the foreground retry throttle. */
internal fun shouldRefreshLocation(lastAttemptMillis: Long?, nowMillis: Long): Boolean =
    lastAttemptMillis == null || nowMillis - lastAttemptMillis !in 0L until AUTOMATIC_LOCATION_INTERVAL_MILLIS
