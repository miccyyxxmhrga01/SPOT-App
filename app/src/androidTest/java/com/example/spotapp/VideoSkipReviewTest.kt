package com.example.spotapp

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.AccountType
import com.example.spotapp.data.PublishedMedia
import com.example.spotapp.data.PublishedPost
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.components.LocalSpotVideo
import com.example.spotapp.ui.theme.components.ProvideSpotVideo
import com.example.spotapp.ui.theme.components.SpotVideo
import com.example.spotapp.ui.theme.components.SpotVideoController
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.reels.ReelPage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * A local video (as a decrypted chat video is) plays in SpotVideo, double-tapping the right or left side skips
 * 5 seconds, and pausing shows the ⟲5 ▶ 5⟳ controls. Also pictures of a Reel with the new rail and author row.
 * Uses the bundled dance clip and a made-up post. Nothing is written to the account: the Reel is paused well before
 * the 2 seconds of playing that would count a view.
 */
class VideoSkipReviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var controller: SpotVideoController? = null

    private fun localVideo(): String {
        val file = File(context.cacheDir, "review-dance.mp4")
        if (!file.exists()) context.resources.openRawResource(R.raw.dance).use { input -> file.outputStream().use { input.copyTo(it) } }
        return Uri.fromFile(file).toString()
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val directory = File(context.getExternalFilesDir(null), "video-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun <T> onMain(read: () -> T): T {
        var value: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { value = read() }
        @Suppress("UNCHECKED_CAST") return value as T
    }
    private fun position() = onMain { controller!!.player.currentPosition }
    private fun duration() = onMain { controller!!.player.duration }

    private fun waitForPlaying() {
        compose.waitUntil(15_000) { controller?.playing == true }
    }

    @Test fun localVideoPlaysAndSkipsFiveSeconds() {
        val url = localVideo()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.15f)) {
                SPOTAppTheme(dark = true) {
                    ProvideSpotVideo {
                        controller = LocalSpotVideo.current
                        Box(Modifier.fillMaxSize().background(Color.Black)) {
                            SpotVideo(url, true, Modifier.fillMaxSize(), muted = true, scrubber = true, skip = true)
                        }
                    }
                }
            }
        }
        waitForPlaying()
        Thread.sleep(800)
        // A single tap pauses (after the double-tap window) and shows the skip controls.
        compose.onNodeWithTag("active-social-video").performTouchInput { click(center) }
        compose.waitUntil(3_000) { compose.onAllNodesWithTag("video-paused-controls").fetchSemanticsNodes().isNotEmpty() }
        save("1-paused-controls")
        // Paused, so the clip (short, and looping) can't wrap around while we measure.
        val length = duration()
        val before = position()
        compose.onNodeWithTag("active-social-video").performTouchInput { doubleClick(Offset(width * .93f, height / 2f)) }
        compose.waitUntil(3_000) { compose.onAllNodesWithTag("video-skip-bubble").fetchSemanticsNodes().isNotEmpty() }
        save("2-skip-forward-bubble")
        val after = position()
        val expected = minOf(5_000L, length - 250 - before)
        assertTrue("Skipped from $before to $after in a ${length}ms clip", after - before >= expected - 100)

        compose.onNodeWithTag("active-social-video").performTouchInput { doubleClick(Offset(width * .07f, height / 2f)) }
        compose.waitForIdle()
        val back = position()
        assertTrue("Skipping back from $after went to $back", back <= maxOf(0L, after - 4_900))
    }

    @Test fun reelLooksRightPlayingAndPaused() {
        val url = localVideo()
        val author = SpotUiProfile("review-ana", "Ana Cruz", "ana_c", "Singing", "", accountType = AccountType.STUDENT)
        val post = PublishedPost("review-reel", author, "Late-night OPM cover on the Quad. Should I do a full set at Acoustic Night?",
            listOf("Singing", "Guitar"), listOf("opm", "acoustic"), listOf(PublishedMedia(url, "video/mp4")), "public",
            likes = 1284, views = 18_400, createdAt = System.currentTimeMillis() - 2 * 3_600_000L, comments = 86)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.15f)) {
                SPOTAppTheme(dark = true) {
                    ProvideSpotVideo {
                        controller = LocalSpotVideo.current
                        Box(Modifier.fillMaxSize().background(Color.Black)) {
                            ReelPage(post, url, active = true, muted = true, onComment = {}, onProfile = {}, onScrubbing = {})
                        }
                    }
                }
            }
        }
        waitForPlaying()
        Thread.sleep(600)
        save("3-reel-playing")
        compose.onNodeWithTag("active-social-video").performTouchInput { click(center) }
        compose.waitUntil(3_000) { compose.onAllNodesWithTag("video-paused-controls").fetchSemanticsNodes().isNotEmpty() }
        save("4-reel-paused")
    }
}
