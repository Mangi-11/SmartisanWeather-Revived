package com.smartisan.weather.data.location

import android.location.Location
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceLocationSourceTest {
    @Test
    fun networkIsNotBlockedByAnUnresponsiveFusedProvider() = runBlocking {
        val cancelled = CompletableDeferred<Unit>()
        val fix = fix(30f)
        val result = withTimeout(2_000) {
            selectCurrentLocation(listOf("fused", "network"), precise = true) { provider ->
                if (provider == "network") fix else try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        }
        assertSame(fix, result)
        assertTrue(cancelled.isCompleted)
    }

    @Test
    fun preciseRequestAllowsGpsToImproveTheFirstCoarseFix() = runBlocking {
        val coarse = fix(3_000f)
        val precise = fix(15f)
        val result = selectCurrentLocation(listOf("network", "gps"), precise = true) {
            if (it == "network") coarse else {
                delay(20)
                precise
            }
        }
        assertSame(precise, result)
    }

    @Test
    fun approximateAccessDoesNotWaitForPreciseCoordinates() = runBlocking {
        val coarse = fix(3_000f)
        val result = withTimeout(2_000) {
            selectCurrentLocation(listOf("network", "fused"), precise = false) {
                if (it == "network") coarse else awaitCancellation()
            }
        }
        assertSame(coarse, result)
    }

    @Test
    fun invalidStaleAndFutureFixesCannotChooseACity() = runBlocking {
        val fixes = listOf(
            fix(10f).apply { elapsedRealtimeNanos -= 300_000_000_000L },
            fix(10f).apply { elapsedRealtimeNanos += 300_000_000_000L },
            fix(10f).apply { latitude = Double.NaN },
            fix(10f).apply { longitude = 181.0 },
            fix(Float.NaN),
            fix(10f).apply { removeAccuracy() },
        )
        val result = selectCurrentLocation(fixes.indices.map(Int::toString), precise = true) {
            fixes[it.toInt()]
        }
        assertNull(result)
    }

    @Test
    fun improvementWindowReturnsBestAvailableFixAndCancelsGps() = runBlocking {
        val best = fix(700f)
        val result = withTimeout(2_000) {
            selectCurrentLocation(
                listOf("network", "fused", "gps"), precise = true, accuracyWaitMillis = 100,
            ) {
                when (it) {
                    "network" -> best
                    "fused" -> fix(2_000f)
                    else -> awaitCancellation()
                }
            }
        }
        assertSame(best, result)
    }

    @Test
    fun noProvidersNullResultsAndDeadlineAllTerminate() = runBlocking {
        assertNull(selectCurrentLocation(emptyList(), precise = true) { error("No provider") })
        assertNull(selectCurrentLocation(listOf("gps"), precise = true) { null })
        assertNull(selectCurrentLocation(listOf("gps"), precise = true, timeoutMillis = 50) {
            awaitCancellation()
        })
    }

    @Test
    fun parentCancellationReleasesEveryProviderWithoutReturningAFix() = runBlocking {
        val started = List(3) { CompletableDeferred<Unit>() }
        var stopped = 0
        var returned = false
        val job = launch {
            selectCurrentLocation(listOf("0", "1", "2"), precise = true) {
                started[it.toInt()].complete(Unit)
                try { awaitCancellation() } finally { stopped++ }
            }
            returned = true
        }
        withTimeout(2_000) { started.forEach { it.await() } }
        job.cancelAndJoin()
        assertEquals(3, stopped)
        assertEquals(false, returned)
    }

    private fun fix(meters: Float) = Location("test").apply {
        latitude = 31.23
        longitude = 121.47
        accuracy = meters
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }
}
