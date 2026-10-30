package com.example.spotapp

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.PublishedMedia
import com.example.spotapp.data.PublishedPost
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.components.FeedPlayback
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.components.ZoomablePhoto
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.home.FeedPostMedia
import com.example.spotapp.ui.theme.screens.home.HomePostCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * The photo gestures on a real feed card and the full-screen viewer, at SPOT's largest text (1.15×): double-tap to like
 * with the heart, Instagram-style pinch zoom that springs back, and the viewer's zoom that stays. Drawn from a made-up
 * post with a photo bundled in the app, so nothing is read or written. Whole-screen shots, saved under the app's
 * external files: `adb pull /sdcard/Android/data/com.example.spotapp/files/media-review`.
 */
class PostMediaGesturesReviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val photo get() = "android.resource://${instrumentation.targetContext.packageName}/${R.drawable.onboarding_showcase}"
    private val author = SpotUiProfile("00000000-0000-0000-0000-0000000000bb", "Ben Reyes", "ben", "Guitar", "", "")
    private fun post() = PublishedPost("review-photo", author, "Golden hour at the quad.", listOf("Photography"), listOf("campus"),
        listOf(PublishedMedia(photo, "image/png")), "public", likes = 24)

    private fun save(name: String) {
        compose.waitForIdle()
        Thread.sleep(500)
        val shot = instrumentation.uiAutomation.takeScreenshot()
        assertTrue("screenshot failed for $name", shot != null)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "media-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.15f)) {
                SPOTAppTheme(dark = false) { Box(Modifier.fillMaxSize().background(SpotPalette.Ivory)) { content() } }
            }
        }
    }

    @Composable
    private fun FeedCard(onLike: () -> Unit) {
        val playback = remember { FeedPlayback() }
        Column(Modifier.padding(top = 56.dp)) {
            HomePostCard(post().asUiPost(), {}, {}, {}, {}, {}, {}, null, {},
                mediaContent = { FeedPostMedia(post(), playback, false, {}, {}, onDoubleTapLike = onLike) })
        }
    }

    /** Two fingers start near the middle and spread apart in steps; they stay down until [release]. */
    private fun SemanticsNodeInteraction.spread() = performTouchInput {
        down(0, center - Offset(70f, 20f)); down(1, center + Offset(70f, 20f))
        repeat(12) { step ->
            val distance = 70f + (step + 1) * 22f
            updatePointerTo(0, center - Offset(distance, distance / 3f)); updatePointerTo(1, center + Offset(distance, distance / 3f))
            move()
        }
    }

    private fun SemanticsNodeInteraction.release() = performTouchInput { up(0); up(1) }

    @Test fun doubleTapLikesWithAHeart() {
        var likes = 0
        show { FeedCard { likes++ } }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Photo 1").performTouchInput { doubleClick() }
        compose.mainClock.advanceTimeBy(280)
        save("01-double-tap-heart")
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("a double-tap likes once", 1, likes)
    }

    @Test fun pinchLiftsThePhotoAndSpringsBack() {
        show { FeedCard {} }
        val photo = compose.onNodeWithContentDescription("Photo 1")
        photo.spread()
        save("02-pinch-zoom-in-feed")
        photo.release()
        compose.waitForIdle()
        save("03-pinch-released")
    }

    @Test fun viewerPhotoStaysZoomedUntilDoubleTap() {
        var zoomed = false
        show {
            Box(Modifier.fillMaxSize().background(Color(0xFF151511))) {
                ZoomablePhoto(photo, "Viewer photo", Modifier.fillMaxSize(), onZoomed = { zoomed = it })
            }
        }
        val image = compose.onNodeWithContentDescription("Viewer photo")
        image.spread()
        image.release()
        compose.waitForIdle()
        assertTrue("the viewer stays zoomed after letting go", zoomed)
        save("04-viewer-zoomed")
        image.performTouchInput { doubleClick() }
        compose.waitForIdle()
        assertFalse("a double-tap zooms back out", zoomed)
        save("05-viewer-zoomed-out")
    }
}
