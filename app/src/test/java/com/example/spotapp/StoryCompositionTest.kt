package com.example.spotapp

import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.screens.home.clampStoryTrim
import org.junit.Assert.*
import org.junit.Test

class StoryCompositionTest {
    @Test fun legacyStoriesKeepTheirOriginalAppearance() { assertEquals(StoryComposition(), StoryComposition.from(null)) }
    @Test fun compositionRoundTripsWithoutLosingPositionsOrTrim() {
        val edit = StoryComposition(scale = 2.0, offsetX = -.1, fill = true, rotation = 2, trimStartMs = 30_000, trimEndMs = 90_000,
            overlays = listOf(StoryOverlay("title", "Campus", .4, .6, .09, 0xFFFDD87A)))
        edit.validate(); assertEquals(edit, StoryComposition.from(edit.fields()))
    }
    @Test fun malformedOrUnboundedEditsFailValidation() {
        assertNotNull(runCatching { StoryComposition(scale = Double.NaN).validate() }.exceptionOrNull())
        assertNotNull(runCatching { StoryComposition(trimEndMs = 60_001).validate() }.exceptionOrNull())
        assertNotNull(runCatching { StoryComposition(overlays = List(5) { StoryOverlay("$it", "Text") }).validate() }.exceptionOrNull())
        assertEquals(StoryComposition(), StoryComposition.from(mapOf("scale" to Double.POSITIVE_INFINITY)))
    }
    @Test fun filtersRoundTripAndUnknownOnesFallBackToNormal() {
        val filtered = StoryComposition(filter = "golden")
        filtered.validate(); assertEquals(filtered, StoryComposition.from(filtered.fields()))
        // Unfiltered stories keep exactly the data they had before filters existed.
        assertFalse("filter" in StoryComposition().fields())
        assertEquals("normal", StoryComposition.from(mapOf("filter" to "sparkle")).filter)
        assertNotNull(runCatching { StoryComposition(filter = "sparkle").validate() }.exceptionOrNull())
    }
    @Test fun trimmingKeepsAtMostOneMinuteAndNeverGoesOutsideMedia() {
        assertEquals(30_000L to 90_000L, clampStoryTrim(0, 90_000, 120_000, 0))
        assertEquals(10_000L to 70_000L, clampStoryTrim(10_000, 120_000, 120_000, 0))
        assertEquals(0L to 700L, clampStoryTrim(-1, 700, 700, 0))
        assertEquals(119_000L to 120_000L, clampStoryTrim(120_000, 120_000, 120_000, 0))
    }
}
