package com.example.spotapp.data

import org.junit.Assert.*
import org.junit.Test

class PublishedPostTest {
    private fun draft() = PostDraft("post-test", "New performance", listOf("Dance"), listOf("campus"),
        listOf("content://media/1"), "public")
    @Test fun rankingCombinesIntentAndReach() {
        assertTrue(popularityScore(8, 10) > popularityScore(2, 30))
        assertEquals(50L, popularityScore(8, 10))
        assertEquals(0L, popularityScore(-1, -1))
    }
    @Test fun invalidDraftsCannotStartPublishing() {
        assertNull(draft().error())
        assertNotNull(draft().copy(caption = "", mediaUris = emptyList()).error())
        assertNotNull(draft().copy(caption = "x".repeat(2001)).error())
        assertNotNull(draft().copy(mediaUris = List(11) { "content://media/$it" }).error())
        assertNotNull(draft().copy(audience = "everyone").error())
    }
    @Test fun talentPostsNeedACategoryButPersonalPostsDoNot() {
        assertNotNull(draft().copy(categories = emptyList()).error())
        assertNull(draft().copy(categories = emptyList(), personal = true).error())
    }
    @Test fun privacyChoiceAndMultipleMediaStayInTheDraft() {
        val privateAlbum = draft().copy(audience = "private", mediaUris = listOf("photo", "video"))
        assertNull(privateAlbum.error())
        assertEquals("Only me", audienceLabel(privateAlbum.audience))
        assertEquals(2, privateAlbum.mediaUris.size)
        assertTrue(PublishedMedia("video-url", "video/webm", "path").isVideo)
        assertFalse(PublishedMedia("photo-url", "image/png", "path").isVideo)
    }
}
