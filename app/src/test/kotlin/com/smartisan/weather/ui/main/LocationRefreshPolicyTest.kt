package com.smartisan.weather.ui.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationRefreshPolicyTest {
    @Test
    fun `cold launch and elapsed interval permit foreground relocation`() {
        assertTrue(shouldRefreshLocation(null, 0L))
        assertTrue(shouldRefreshLocation(10L, 10L + AUTOMATIC_LOCATION_INTERVAL_MILLIS))
    }

    @Test
    fun `activity transitions do not repeatedly request location`() {
        assertFalse(shouldRefreshLocation(10L, 10L))
        assertFalse(shouldRefreshLocation(10L, 10L + AUTOMATIC_LOCATION_INTERVAL_MILLIS - 1L))
    }

    @Test
    fun `reset monotonic clock does not suppress future attempts`() {
        assertTrue(shouldRefreshLocation(100L, 10L))
    }
}
