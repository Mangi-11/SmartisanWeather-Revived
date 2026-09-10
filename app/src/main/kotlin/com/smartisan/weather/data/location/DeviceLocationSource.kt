package com.smartisan.weather.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.smartisan.weather.util.DebugLog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** One foreground fix; no stored coordinates, passive subscription or Play services dependency. */
internal class DeviceLocationSource(context: Context) {
    private val context = context.applicationContext
    private val manager = context.getSystemService(LocationManager::class.java)

    suspend fun getCurrentLocation(): Location? {
        val access = LocationAccess.read(context)
        val manager = manager ?: return null
        if (access == LocationAccess.NONE || !LocationManagerCompat.isLocationEnabled(manager)) return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }.filter { provider ->
            (provider != LocationManager.GPS_PROVIDER || access == LocationAccess.PRECISE) &&
                LocationManagerCompat.hasProvider(manager, provider) && manager.isProviderEnabled(provider)
        }
        val startedAt = SystemClock.elapsedRealtime()
        return selectCurrentLocation(providers, access == LocationAccess.PRECISE) { provider ->
            requestLocation(manager, provider, access)
        }.also { fix ->
            // Diagnostic quality and duration only; never log device coordinates.
            DebugLog.log(
                "WeatherLocation",
                "Fix: access=$access, provider=${fix?.provider}, accuracy=${fix?.accuracy}, " +
                    "durationMs=${SystemClock.elapsedRealtime() - startedAt}",
            )
        }
    }

    @SuppressLint("MissingPermission") // Checked above; revocation during a request is also handled.
    private suspend fun requestLocation(
        manager: LocationManager,
        provider: String,
        access: LocationAccess,
    ): Location? = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        val deliver: (Location?) -> Unit = { location ->
            if (continuation.isActive) continuation.resume(location)
        }
        try {
            val executor = ContextCompat.getMainExecutor(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val request = LocationRequest.Builder(0L)
                    .setQuality(
                        if (access == LocationAccess.PRECISE) LocationRequest.QUALITY_HIGH_ACCURACY
                        else LocationRequest.QUALITY_BALANCED_POWER_ACCURACY,
                    )
                    .setDurationMillis(LOCATION_TIMEOUT_MILLIS)
                    .build()
                manager.getCurrentLocation(provider, request, signal, executor, deliver)
            } else {
                LocationManagerCompat.getCurrentLocation(manager, provider, signal, executor, deliver)
            }
        } catch (_: SecurityException) {
            deliver(null)
        } catch (_: IllegalArgumentException) {
            deliver(null)
        }
    }
}

internal const val LOCATION_TIMEOUT_MILLIS = 20_000L
private const val ACCURACY_WAIT_MILLIS = 4_000L
private const val MAX_LOCATION_AGE_NANOS = 30_000_000_000L

/** Race active providers, briefly allowing a precise fix to improve a coarse first result. */
internal suspend fun selectCurrentLocation(
    providers: List<String>,
    precise: Boolean,
    timeoutMillis: Long = LOCATION_TIMEOUT_MILLIS,
    accuracyWaitMillis: Long = ACCURACY_WAIT_MILLIS,
    request: suspend (String) -> Location?,
): Location? = coroutineScope {
    val results = Channel<Location?>(providers.size.coerceAtLeast(1))
    val jobs = providers.map { provider -> launch { results.send(request(provider)) } }
    var best: Location? = null
    try {
        withTimeoutOrNull(timeoutMillis) {
            var remaining = providers.size
            var improveUntil: Long? = null
            while (remaining > 0) {
                val waitMillis = improveUntil?.let { it - SystemClock.elapsedRealtime() } ?: timeoutMillis
                if (waitMillis <= 0L) break
                // A null fix completes a provider, whereas a timeout ends the improvement window.
                val result = withTimeoutOrNull(waitMillis) { Result.success(results.receive()) } ?: break
                remaining--
                val candidate = result.getOrNull()?.takeIf { it.isUsableNow() } ?: continue
                val previous = best
                if (previous == null || candidate.accuracy < previous.accuracy ||
                    (candidate.accuracy == previous.accuracy &&
                        candidate.elapsedRealtimeNanos > previous.elapsedRealtimeNanos)
                ) best = candidate
                if (!precise || best!!.accuracy <= 200f) break
                if (improveUntil == null) improveUntil = SystemClock.elapsedRealtime() + accuracyWaitMillis
            }
        }
        best?.takeIf { it.isUsableNow() }
    } finally {
        jobs.forEach { it.cancel() }
        results.cancel()
    }
}

private fun Location.isUsableNow(): Boolean =
    latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0 &&
        hasAccuracy() && accuracy.isFinite() && accuracy > 0f &&
        SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos in 0L..MAX_LOCATION_AGE_NANOS
