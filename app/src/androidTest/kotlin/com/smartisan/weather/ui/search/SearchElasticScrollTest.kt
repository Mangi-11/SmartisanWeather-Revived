package com.smartisan.weather.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchElasticScrollTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var elastic: SearchElasticState

    @Test
    fun grabbingAReboundingListContinuesAtTheVisiblePosition() {
        showElasticSurface()
        compose.mainClock.autoAdvance = false
        var releaseOffset = 0f
        compose.runOnIdle {
            stretch(400f)
            releaseOffset = elastic.offset
            elastic.onTouchEnd()
        }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            val visibleOffset = elastic.offset
            assertTrue(visibleOffset > 0f && visibleOffset < releaseOffset)
            elastic.onTouchStart()
            stretch(1f)
            assertTrue("A one-pixel drag must not jump back to the old release distance", elastic.offset - visibleOffset in 0f..0.51f)
            elastic.onTouchEnd()
        }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals(0f, elastic.offset, 0.001f) }
    }

    @Test
    fun pointerCancellationReturnsTheListWithoutAFling() {
        showElasticSurface()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { stretch(300f) }
        compose.onNodeWithTag("elastic_surface").performTouchInput { down(center); cancel() }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals(0f, elastic.offset, 0.001f) }
    }

    @Test
    fun disposingDuringReboundResetsTheOldSurfaceAndRecreationStartsAtRest() {
        val visible = mutableStateOf(true)
        compose.setContent {
            if (visible.value) ElasticSurface()
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { stretch(-300f); elastic.onTouchEnd() }
        compose.mainClock.advanceTimeBy(80)
        val disposed = elastic
        compose.runOnIdle { visible.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            assertEquals(0f, disposed.offset, 0.001f)
            visible.value = true
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(0f, elastic.offset, 0.001f) }
    }

    private fun showElasticSurface() = compose.setContent { ElasticSurface() }

    @androidx.compose.runtime.Composable
    private fun ElasticSurface() {
        elastic = rememberSearchElasticState()
        Box(Modifier.size(200.dp).testTag("elastic_surface").searchElasticContainer(elastic)) {
            Box(Modifier.fillMaxSize().searchElasticContent(elastic))
        }
    }

    private fun stretch(distance: Float) {
        val available = Offset(0f, distance)
        val consumed = elastic.onPreScroll(available, NestedScrollSource.UserInput)
        elastic.onPostScroll(Offset.Zero, available - consumed, NestedScrollSource.UserInput)
    }
}
