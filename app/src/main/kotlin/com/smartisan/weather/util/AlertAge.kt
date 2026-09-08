package com.smartisan.weather.util

internal sealed interface AlertAge {
    data object Now : AlertAge
    data class Minutes(val count: Long) : AlertAge
    data class Hours(val count: Long) : AlertAge
    data class Days(val count: Long) : AlertAge
}

/** Elapsed time is independent of month, midnight, and the device's current time zone. */
internal fun alertAge(publishedAtMillis: Long, nowMillis: Long): AlertAge? {
    if (publishedAtMillis <= 0 || nowMillis < publishedAtMillis) return null
    val elapsedMillis = nowMillis - publishedAtMillis
    return when {
        elapsedMillis < MINUTE_MILLIS -> AlertAge.Now
        elapsedMillis < HOUR_MILLIS -> AlertAge.Minutes(elapsedMillis / MINUTE_MILLIS)
        elapsedMillis < DAY_MILLIS -> AlertAge.Hours(elapsedMillis / HOUR_MILLIS)
        else -> AlertAge.Days(elapsedMillis / DAY_MILLIS)
    }
}

private const val MINUTE_MILLIS = 60_000L
private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
private const val DAY_MILLIS = 24 * HOUR_MILLIS
