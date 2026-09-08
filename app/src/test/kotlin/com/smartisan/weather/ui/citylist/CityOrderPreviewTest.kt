package com.smartisan.weather.ui.citylist

import com.smartisan.weather.data.model.SavedCity
import org.junit.Assert.assertEquals
import org.junit.Test

class CityOrderPreviewTest {
    @Test
    fun repositoryRefreshUpdatesObjectsWithoutReplacingPreviewOrder() {
        val cities = listOf(city("a"), city("b").copy(locationName = "Updated"), city("c"))
        val result = mergeCityPreview(cities, listOf("c", "b", "a"))
        assertEquals(listOf("c", "b", "a"), result.map(SavedCity::locationKey))
        assertEquals("Updated", result[1].locationName)
    }

    @Test
    fun deletedCityStaysDeletedAndNewLocationIsPinned() {
        val cities = listOf(city("location").copy(sortOrder = 1), city("a"), city("new"))
        val result = mergeCityPreview(cities, listOf("deleted", "a"))
        assertEquals(listOf("location", "a", "new"), result.map(SavedCity::locationKey))
    }

    @Test
    fun edgeScrollingUsesViewportQuarterAndOriginalPixelVelocity() {
        assertEquals(0f, cityEdgeScrollSpeed(400f, 800f))
        assertEquals(-0.25f, cityEdgeScrollSpeed(100f, 800f))
        assertEquals(0.25f, cityEdgeScrollSpeed(700f, 800f))
        assertEquals(-0.5f, cityEdgeScrollSpeed(-100f, 800f))
        assertEquals(0.5f, cityEdgeScrollSpeed(900f, 800f))
    }

    private fun city(key: String) = SavedCity(locationKey = key, locationName = key, sortOrder = 2)
}
