package com.example.spotapp

import android.graphics.Bitmap
import android.view.WindowManager
import android.view.View
import android.view.ViewGroup
import androidx.media3.ui.PlayerView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.PublishedMedia
import com.example.spotapp.data.PublishedPost
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.models.SpotRoute
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.navigation.SpotMainBottomNavigation
import com.example.spotapp.ui.theme.screens.home.HomePostCard
import com.example.spotapp.ui.theme.screens.home.PopularPostsCarousel
import com.example.spotapp.ui.theme.screens.home.SpotHomePage
import com.example.spotapp.ui.theme.screens.home.PublishedVideo
import com.example.spotapp.ui.theme.screens.profile.UserProfileScreen
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class CloudinaryHomeExperienceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val creator = SpotUiProfile("creator-uid", "Sam Cruz", "samcruz", "Dancing", "BSIT")
    private val photo get() = "android.resource://${InstrumentationRegistry.getInstrumentation().targetContext.packageName}/${R.drawable.image1}"
    private fun post(id: String = "live-post") = PublishedPost(id, creator, "A little practice, a lot of progress.",
        listOf("Dancing"), listOf("CampusTalent"), listOf(PublishedMedia(photo, "image/jpeg")), "public", 24, 130,
        System.currentTimeMillis() - 180_000, comments = 7)
    @Before fun keepAwake() { compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
    private fun screenshot(name: String) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "cloudinary-home-review").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun creatorIdentityAndCommentCountStayVisibleAndClickable() {
        var opened = ""
        val item = post()
        compose.setContent { SPOTAppTheme {
            HomePostCard(item.asUiPost(), { opened = item.author.uid }, {}, {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("@samcruz").assertIsDisplayed().performClick()
        assertEquals("creator-uid", opened)
        compose.onNodeWithText("7").assertIsDisplayed()
        compose.onNodeWithText("3m ago", substring = true).assertIsDisplayed()
        screenshot("post-card")
    }
    @Test fun refinedHomePreservesStoriesCarouselAndFloatingNavigation() {
        val item = post()
        compose.setContent { SPOTAppTheme {
            SpotHomePage(creator, item.asUiPost(), null, {}, {}, {}, {}, {}, showPost = false,
                contentAfterStories = { PopularPostsCarousel(listOf(item), false, "", {}, {}) },
                feedContent = { items(listOf(item), key = { it.id }) { p -> HomePostCard(p.asUiPost(), {}, {}, {}, {}, {}, {}, {}, {}) } })
        } }
        compose.onNodeWithText("SPOT").assertIsDisplayed()
        compose.onNodeWithText("Your Story").assertIsDisplayed()
        compose.onNodeWithText("Popular on SPOT").assertIsDisplayed()
        compose.onNodeWithContentDescription("Profile").assertIsDisplayed()
        screenshot("home-top")
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText("@samcruz"))
        compose.onNodeWithText("@samcruz").assertIsDisplayed()
        screenshot("home-feed")
    }
    @Test fun navigationUsesPhotoUrlAndNormalIconWhenMissing() {
        var current by mutableStateOf(creator)
        compose.setContent { SPOTAppTheme { SpotMainBottomNavigation(SpotRoute.HOME, current, {}) } }
        compose.onNodeWithTag("profile-avatar-fallback", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("profile-avatar-photo", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { current = creator.copy(profileImageUrl = photo) }
        compose.onNodeWithTag("profile-avatar-photo", useUnmergedTree = true).assertExists()
        screenshot("navigation-avatar")
    }
    @Test fun profileShowsAllLoadedPostsInsteadOfOneSample() {
        val posts = (1..4).map { post("post-$it").asUiPost() }
        compose.setContent { SPOTAppTheme { UserProfileScreen(creator, false, null, {}, {}, {}, posts = posts) } }
        compose.onNodeWithText("4", useUnmergedTree = true).assertExists()
        screenshot("creator-profile")
    }

    @Test fun cloudinaryVideoPlaysPausesAndStopsWhenInactive() {
        // A read-only media fixture from the user's existing Cloudinary seed. No post or view is written.
        val url = "https://res.cloudinary.com/jcuzzxsz/video/upload/v1789197830/spot/posts/GcOAMAZgpCVo514ybnlmVCyuV8y1/rntrki2pdlu8vzpldz49.mp4"
        var active by mutableStateOf(true)
        compose.setContent { SPOTAppTheme { PublishedVideo(url, active) } }
        fun find(view: View): PlayerView? {
            if (view is PlayerView) return view
            if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
            return null
        }
        fun playing(moved: Boolean = false): Boolean {
            var value = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                value = find(compose.activity.window.decorView)?.let { it.player?.isPlaying == true && (!moved || (it.player?.currentPosition ?: 0) > 0) } == true
            }
            return value
        }
        compose.waitUntil(30_000) { playing(moved = true) }
        screenshot("cloudinary-playback")
        compose.onNode(hasClickAction()).performClick()
        compose.waitUntil(5_000) { !playing() }
        compose.onNode(hasClickAction()).performClick()
        compose.waitUntil(5_000) { playing() }
        compose.runOnIdle { active = false }
        compose.waitUntil(5_000) { !playing() }
    }
}
