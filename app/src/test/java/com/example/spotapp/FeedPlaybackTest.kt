package com.example.spotapp

import androidx.compose.ui.geometry.Rect
import com.example.spotapp.ui.theme.components.FeedPlayback
import org.junit.Assert.*
import org.junit.Test

class FeedPlaybackTest {
    @Test fun selectsOnlyTheMostlyVisibleVideoNearestTheViewportCenter() {
        val feed = FeedPlayback().apply { viewport = Rect(0f, 0f, 400f, 800f) }
        feed.update("above", Rect(0f, -300f, 400f, 100f))
        feed.update("center", Rect(0f, 180f, 400f, 580f))
        feed.update("below", Rect(0f, 700f, 400f, 1100f))
        assertEquals("center", feed.activeId)
        feed.update("center", Rect(0f, -400f, 400f, 0f))
        feed.update("below", Rect(0f, 100f, 400f, 500f))
        assertEquals("below", feed.activeId)
        feed.remove("below")
        assertNull(feed.activeId)
    }
    @Test fun neighboringHorizontalPagesAndDisposedMediaCannotStealPlayback() {
        val feed = FeedPlayback().apply { viewport = Rect(0f, 0f, 400f, 800f) }
        feed.update("neighbor", Rect(350f, 180f, 750f, 580f))
        assertNull(feed.activeId)
        feed.update("current", Rect(0f, 180f, 400f, 580f))
        assertEquals("current", feed.activeId)
        feed.viewport = Rect.Zero
        assertNull(feed.activeId)
    }
}
