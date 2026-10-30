package com.example.spotapp

import com.example.spotapp.data.*
import org.junit.Assert.*
import org.junit.Test

class PostMediaEditTest {
    @Test fun selectionKeepsTapOrderAndStopsAtTen() {
        var selected = emptyList<String>()
        (1..12).forEach { selected = orderedMediaSelection(selected, "media-$it") }
        assertEquals((1..10).map { "media-$it" }, selected)
        selected = orderedMediaSelection(selected, "media-3")
        selected = orderedMediaSelection(selected, "media-11")
        assertEquals("media-11", selected.last()); assertFalse("media-3" in selected)
    }
    @Test fun reorderingMovesOnlyTheChosenItemAndChecksBounds() {
        assertEquals(listOf("b", "a", "c"), movePostMedia(listOf("a", "b", "c"), 1, -1))
        assertEquals(listOf("a", "c", "b"), movePostMedia(listOf("a", "b", "c"), 1, 1))
        assertEquals(listOf("a"), movePostMedia(listOf("a"), 0, -1))
    }
    @Test fun rotationIsNormalizedAndCropIsExplicit() {
        assertEquals(3, PostMediaEdit(-1).rotation)
        assertEquals(0, PostMediaEdit(3).rotate().rotation)
        assertEquals(1f, PostMediaEdit(crop = "square").aspectRatio!!, 0f)
        assertEquals(.8f, PostMediaEdit(crop = "portrait").aspectRatio!!, .001f)
        assertFalse(PostMediaEdit(muted = true).changesImage)
    }
    @Test fun aLookChangesThePhotoButNotItsGeometry() {
        val styled = PostMediaEdit(1, "square", look = MediaLook("mono", brightness = .2f))
        assertTrue(styled.changesImage)
        assertTrue(PostMediaEdit(look = MediaLook(warmth = .1f)).changesImage)
        assertEquals(PostMediaEdit(1, "square"), styled.geometry)
    }
    @Test fun manualDraftsKeepFutureAiFlagsFalse() {
        val draft = PostDraft("post-test", "Caption", listOf("Guitar"), listOf("Campus"), listOf("content://photo"), "public", mediaEdits = mapOf("content://photo" to PostMediaEdit(1)))
        assertNull(draft.error()); assertFalse(draft.aiCaption || draft.aiTagged || draft.aiCategorySuggested)
    }
}
