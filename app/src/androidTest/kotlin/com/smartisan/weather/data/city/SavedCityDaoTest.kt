package com.smartisan.weather.data.city

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedCityDaoTest {
    private lateinit var database: WeatherDatabase
    private lateinit var dao: SavedCityDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder<WeatherDatabase>(context)
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        dao = database.savedCityDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun narrowQueriesPreserveInsertAndLocationReplacementSemantics() = runBlocking {
        dao.insert(city(key = "location", order = 1, weatherJson = "location-cache", lastUpdate = 10L))
        dao.insert(city(key = "alpha", order = 2, weatherJson = "alpha-cache", lastUpdate = 20L))
        dao.insert(city(key = "beta", order = 3))

        val duplicate = dao.insertRegularCity(
            entity = city(key = "alpha", order = 0),
            maxCities = 36,
            insertAfterKey = null,
        )
        assertEquals(InsertRegularCityResult.ALREADY_EXISTS, duplicate)
        assertEquals(SavedCityCache("alpha-cache", 20L), dao.getCacheByKey("alpha"))

        val inserted = dao.insertRegularCity(
            entity = city(key = "gamma", order = 0),
            maxCities = 36,
            insertAfterKey = "alpha",
        )
        assertEquals(InsertRegularCityResult.SUCCESS, inserted)
        assertEquals(
            listOf("location", "alpha", "gamma", "beta"),
            dao.observeAll().first().map(SavedCitySummary::locationKey),
        )

        assertTrue(
            dao.replaceLocationCity(
                entity = city(key = "alpha", order = 1, name = "Alpha Location"),
                maxCities = 36,
            )
        )
        assertFalse(dao.existsByKey("location"))
        assertEquals(1, dao.getSortOrderByKey("alpha"))
        assertEquals(SavedCityCache("alpha-cache", 20L), dao.getCacheByKey("alpha"))

        assertTrue(
            dao.replaceLocationCity(
                entity = city(key = "alpha", order = 1, name = "Refreshed Alpha"),
                maxCities = 36,
            )
        )
        assertEquals(SavedCityCache("alpha-cache", 20L), dao.getCacheByKey("alpha"))
        assertEquals("Refreshed Alpha", dao.observeAll().first().first().locationName)
    }

    @Test
    fun locationReplacementHonorsCapacityWithoutPartialWrites() = runBlocking {
        repeat(36) { index ->
            dao.insert(city(key = "city-$index", order = index + 2))
        }

        assertFalse(
            dao.replaceLocationCity(
                entity = city(key = "new-location", order = 1),
                maxCities = 36,
            )
        )
        assertEquals(36, dao.count())
        assertFalse(dao.existsByKey("new-location"))

        dao.deleteByKey("city-35")
        assertTrue(
            dao.replaceLocationCity(
                entity = city(key = "new-location", order = 1),
                maxCities = 36,
            )
        )
        assertEquals(36, dao.count())
        assertEquals("new-location", dao.getLocationCityKey())

        assertTrue(
            dao.replaceLocationCity(
                entity = city(key = "replacement-location", order = 1),
                maxCities = 36,
            )
        )
        assertEquals(36, dao.count())
        assertFalse(dao.existsByKey("new-location"))
        assertEquals("replacement-location", dao.getLocationCityKey())
    }

    @Test
    fun orderTransactionKeepsLocationFirstAndRegularCitiesContiguous() = runBlocking {
        dao.insert(city(key = "location", order = 1))
        dao.insert(city(key = "alpha", order = 2))
        dao.insert(city(key = "beta", order = 3))

        dao.updateCityOrder(
            orderedKeys = listOf("beta", "location", "alpha"),
            locationCityKey = "location",
        )

        val cities = dao.observeAll().first()
        assertEquals(listOf("location", "beta", "alpha"), cities.map(SavedCitySummary::locationKey))
        assertEquals(listOf(1, 2, 3), cities.map(SavedCitySummary::sortOrder))
        assertNull(dao.getWeatherJsonByKey("missing"))
    }

    private fun city(
        key: String,
        order: Int,
        name: String = key,
        weatherJson: String? = null,
        lastUpdate: Long = 0L,
    ): SavedCityEntity = SavedCityEntity(
        locationKey = key,
        locationName = name,
        locationParentName = "Parent",
        country = "中国",
        province = "Province",
        sortOrder = order,
        weatherJson = weatherJson,
        lastUpdate = lastUpdate,
    )
}
