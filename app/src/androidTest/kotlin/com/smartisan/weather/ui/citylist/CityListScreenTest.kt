package com.smartisan.weather.ui.citylist

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.smartisan.weather.data.model.SavedCity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CityListScreenTest {
    @get:Rule val compose = createComposeRule()
    private val initial = listOf(city("location", 1), city("a", 2), city("b", 3), city("c", 4))

    @Test
    fun handleDragReordersPreviewWithoutSavingAndCancelRestoresSnapshot() {
        val state = mutableStateOf(CityListUiState(cities = initial))
        var snapshot: List<String>? = null
        var saves = 0
        compose.setContent {
            CityListScreen(state.value, false, {}, { saves++ },
                onBeginDrag = { snapshot = state.value.displayCities.map(SavedCity::locationKey) },
                onMoveCity = { key, target ->
                    val keys = state.value.displayCities.map(SavedCity::locationKey).toMutableList()
                    val targetIndex = keys.indexOf(target)
                    keys.add(targetIndex, keys.removeAt(keys.indexOf(key)))
                    state.value = state.value.copy(previewOrderKeys = keys)
                },
                onFinishDrag = { snapshot = null },
                onCancelDrag = { snapshot?.let { state.value = state.value.copy(previewOrderKeys = it) }; snapshot = null },
                onRequestDelete = {}, onDismissDelete = {}, onDelete = {})
        }
        val handle = compose.onNodeWithTag("drag_c", useUnmergedTree = true)
        val rowHeight = handle.fetchSemanticsNode().boundsInRoot.height
        compose.mainClock.autoAdvance = false
        handle.performTouchInput { down(center); moveBy(Offset(0f, -rowHeight * 1.5f), delayMillis = 100) }
        compose.mainClock.advanceTimeBy(220)
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf("location", "a", "c", "b"), state.value.displayCities.map(SavedCity::locationKey))
            assertEquals(0, saves)
        }
        handle.performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(initial, state.value.displayCities) }
    }

    private fun city(key: String, order: Int) = SavedCity(locationKey = key, locationName = key, sortOrder = order)
}
