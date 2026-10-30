package com.example.spotapp

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.models.*
import com.example.spotapp.ui.theme.navigation.*
import com.example.spotapp.ui.theme.screens.discover.DiscoverMediaTile
import com.example.spotapp.ui.theme.screens.home.HomePostCard
import com.example.spotapp.ui.theme.screens.profile.UserProfileScreen
import com.example.spotapp.ui.theme.screens.post.PostDraftMediaPreview
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class PremiumExperienceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val author = SpotUiProfile("review-creator", "Sam Cruz", "samcruz", "Dancing", "BSIT")
    private val photo get() = "android.resource://${InstrumentationRegistry.getInstrumentation().targetContext.packageName}/${R.drawable.image1}"
    private fun post() = PublishedPost("review-post", author, "A little practice, a lot of progress.", listOf("Dancing"), listOf("CampusTalent"),
        listOf(PublishedMedia(photo, "image/jpeg")), "public", 24, 130, System.currentTimeMillis() - 180_000, comments = 7)
    @Before fun awake() { compose.activityRule.scenario.onActivity {
        it.enableEdgeToEdge(); it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "premium-review").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun backdropActuallyBlursAndUpdatesFromSourceOnThisAndroidVersion() {
        var changed by mutableStateOf(false)
        compose.setContent {
            val glass = rememberSpotGlassState()
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.fillMaxSize().spotGlassSource(glass)) {
                    Box(Modifier.weight(1f).fillMaxHeight().background(if (changed) Color.Green else Color.Red))
                    Box(Modifier.weight(1f).fillMaxHeight().background(if (changed) Color.Green else Color.Blue))
                }
                SpotGlassBackdrop(Modifier.align(Alignment.Center).fillMaxWidth().height(80.dp).testTag("glass-sample"), glass)
            }
        }
        compose.waitUntil(10_000) {
            val pixels = compose.onNodeWithTag("glass-sample").captureToImage().toPixelMap()
            val center = pixels[pixels.width / 2, pixels.height / 2]
            center.red > .1f && center.blue > .1f
        }
        screenshot("real-blur-edge")
        compose.runOnIdle { changed = true }
        compose.waitUntil(10_000) {
            val pixels = compose.onNodeWithTag("glass-sample").captureToImage().toPixelMap()
            val center = pixels[pixels.width / 2, pixels.height / 2]
            center.green > .8f && center.red < .1f
        }
    }
    @Test fun glassNavigationStaysSingleAndCanHideAndReturn() {
        var visible by mutableStateOf(true)
        var route by mutableStateOf(SpotRoute.HOME)
        compose.setContent { SPOTAppTheme {
            val glass = rememberSpotGlassState()
            CompositionLocalProvider(LocalSpotGlass provides glass) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    Image(painterResource(R.drawable.image1), null, Modifier.fillMaxSize().spotGlassSource(glass), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    Box(Modifier.align(Alignment.BottomCenter)) { SpotMainBottomNavigation(route, author, { route = it }, visible) }
                    CompositionLocalProvider(LocalSpotBottomNavigationSuppressed provides true) { SpotMainBottomNavigation(route, author, {}) }
                }
            }
        } }
        compose.onAllNodesWithTag("spot-navigation").assertCountEquals(1)
        compose.onNodeWithTag("nav-discover").performClick().assertIsSelected()
        compose.runOnIdle { visible = false }
        compose.onNodeWithTag("spot-navigation").assertDoesNotExist()
        compose.runOnIdle { visible = true }
        compose.onNodeWithTag("nav-profile").performClick().assertIsSelected()
        screenshot("glass-navigation-over-media")
    }
    @Test fun feedCaptionPrecedesWorkingReactionsAndProfileAction() {
        var likes = 0; var comments = 0; var shares = 0; var profiles = 0; var saved by mutableStateOf(false)
        compose.setContent { SPOTAppTheme {
            Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
                HomePostCard(post().asUiPost().copy(liked = likes > 0, saved = saved), { profiles++ }, { likes++ }, { comments++ }, { shares++ }, { saved = !saved }, {}, {}, {},
                    mediaContent = { Image(painterResource(R.drawable.image1), "Post image", Modifier.fillMaxWidth().height(240.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop) })
            }
        } }
        compose.onNodeWithText("@samcruz").performClick()
        val caption = compose.onNodeWithText(post().caption).getUnclippedBoundsInRoot()
        val like = compose.onNodeWithContentDescription("Like").getUnclippedBoundsInRoot()
        assertTrue(caption.bottom <= like.top)
        compose.onNodeWithContentDescription("Like").performClick()
        compose.onNodeWithContentDescription("Comment").performClick()
        compose.onNodeWithContentDescription("Share").performClick()
        compose.onNodeWithContentDescription("Save").performClick()
        compose.onNodeWithContentDescription("Saved").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, likes); assertEquals(1, comments); assertEquals(1, shares); assertEquals(1, profiles); assertTrue(saved) }
        screenshot("feed-reactions")
    }
    @Test fun visualDiscoverTileOpensPostProfileAndHideMenuIndependently() {
        var opens = 0; var profiles = 0; var hidden = 0
        compose.setContent { SPOTAppTheme { Box(Modifier.width(180.dp).statusBarsPadding().padding(8.dp)) {
            DiscoverMediaTile(post().copy(media = listOf(PublishedMedia(photo, "video/mp4", thumbnailUrl = photo))), { opens++ }, { profiles++ }, { hidden++ })
        } } }
        compose.onNodeWithTag("discover-post-review-post").performClick()
        compose.onNodeWithText("@samcruz").performClick()
        compose.onNodeWithContentDescription("Post options").performClick()
        compose.onNodeWithText("Not interested").performClick()
        compose.runOnIdle { assertEquals(1, opens); assertEquals(1, profiles); assertEquals(1, hidden) }
    }
    @Test fun profileTabsRetainSelectionAndAnimateIndicator() {
        compose.setContent { SPOTAppTheme { UserProfileScreen(author, false, null, {}, {}, {}, posts = listOf(post().asUiPost())) } }
        compose.onNodeWithText("Reels").performScrollTo().performClick()
        compose.onNodeWithText("Reels").assertIsSelected()
        compose.onNodeWithText("Tagged").performClick().assertIsSelected()
        compose.onNodeWithText("No tagged posts yet").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Posts").performScrollTo().performClick().assertIsSelected()
        screenshot("profile-tabs")
    }
    @Test fun creatorPortfolioShowsTopPostAndFiltersByTalent() {
        val creator = author.copy(talentCategories = listOf("Dancing", "Singing"))
        val dance = post().copy(author = creator).asUiPost()
        val song = post().copy(id = "review-song", author = creator, categories = listOf("Singing"), likes = 3).asUiPost()
        compose.setContent { SPOTAppTheme { UserProfileScreen(creator, false, null, {}, {}, {}, posts = listOf(dance, song)) } }
        compose.onNodeWithTag("open-to-collab").assertIsDisplayed()
        compose.onNodeWithTag("top-performance").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("talent-tile-Singing").performScrollTo().performClick()
        compose.onNodeWithText("Singing posts").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Show all").performClick()
        compose.onNodeWithText("Singing posts").assertDoesNotExist()
        screenshot("creator-portfolio")
    }
    @Test fun spotterProfileLeadsWithEvents() {
        val spotter = author.copy(accountType = AccountType.TALENT_SEEKER, organization = "Arts Council", seekingTalents = listOf("Singing"))
        compose.setContent { SPOTAppTheme { UserProfileScreen(spotter, false, null, {}, {}, {}) } }
        compose.onNodeWithTag("spotter-badge").assertIsDisplayed()
        compose.onNodeWithText("Arts Council").assertIsDisplayed()
        compose.onNodeWithText("Upcoming events").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Past events").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Posts").performClick().assertIsSelected()
        compose.onNodeWithTag("open-to-collab").assertDoesNotExist()
        screenshot("spotter-profile")
    }
    @Test fun draftPreviewPreservesAlbumAndEditAction() {
        var edits = 0
        val second = "android.resource://${InstrumentationRegistry.getInstrumentation().targetContext.packageName}/${R.drawable.image2}"
        val draft = PostDraft("review", "", emptyList(), emptyList(), listOf(photo, second), "public")
        compose.setContent { SPOTAppTheme { Column(Modifier.statusBarsPadding().padding(16.dp)) { PostDraftMediaPreview(draft) { edits++ } } } }
        compose.onNodeWithTag("draft-media-preview").performTouchInput { swipeLeft() }
        compose.onAllNodesWithContentDescription("Edit media").onLast().performClick()
        compose.runOnIdle { assertEquals(1, edits) }
        screenshot("draft-media-preview")
    }
}
