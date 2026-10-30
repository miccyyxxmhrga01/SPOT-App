package com.example.spotapp

import android.view.WindowManager
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.PublishedMedia
import com.example.spotapp.data.PublishedPost
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.models.SpotMockData
import com.example.spotapp.ui.theme.models.SpotRoute
import com.example.spotapp.ui.theme.navigation.SpotMainBottomNavigation
import com.example.spotapp.ui.theme.screens.home.PopularPostsCarousel
import com.example.spotapp.ui.theme.screens.home.SpotHomePage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class HomeExperienceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun keepScreenOn() { compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
    private fun screenshot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "home-review")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun allTabsAndCreateAreReachableOnSmallPhoneWithLargeText() {
        var destination = SpotRoute.HOME
        var created: com.example.spotapp.ui.theme.navigation.SpotCreateAction? = null
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.5f)) {
                SPOTAppTheme {
                    var route by remember { mutableStateOf(SpotRoute.HOME) }
                    Box(Modifier.requiredWidth(320.dp).testTag("fixture")) {
                        SpotMainBottomNavigation(route, SpotMockData.mika, { route = it; destination = it },
                            unreadMessagesCount = 12, onCreate = { created = it })
                    }
                }
            }
        }
        val routes = listOf("discover" to SpotRoute.DISCOVER, "messages" to SpotRoute.MESSAGES_CHATS,
            "profile" to SpotRoute.PROFILE, "home" to SpotRoute.HOME)
        for ((label, route) in routes) {
            val node = compose.onNodeWithTag("nav-$label").assertIsDisplayed()
            node.performClick().assertIsSelected()
            assertEquals(route, destination)
            val rect = node.getUnclippedBoundsInRoot()
            val container = compose.onNodeWithTag("fixture").getUnclippedBoundsInRoot()
            assertTrue(rect.left >= container.left && rect.right <= container.right)
            assertTrue(rect.right - rect.left >= 48.dp && rect.bottom - rect.top >= 48.dp)
        }
        compose.onNodeWithTag("nav-create").assertIsDisplayed().performClick()
        compose.onNodeWithTag("create-post").assertIsDisplayed()
        compose.onNodeWithTag("create-event").assertDoesNotExist()
        compose.onNodeWithTag("create-story").performClick()
        assertEquals(com.example.spotapp.ui.theme.navigation.SpotCreateAction.STORY, created)
        screenshot("glass-navigation-large-text")
    }
    @Test fun emptyPopularSectionIsHiddenWithoutSamplePosts() {
        compose.setContent { SPOTAppTheme { PopularPostsCarousel(emptyList(), false, "", {}, {}) } }
        compose.onNodeWithTag("popular-section").assertDoesNotExist()
        compose.onNodeWithText("Popular on SPOT").assertDoesNotExist()
        compose.onNodeWithText("The spotlight is open").assertDoesNotExist()
    }
    @Test fun popularityErrorsOfferRetryInsteadOfAnEmptyFeed() {
        var retries = 0
        compose.setContent { SPOTAppTheme { PopularPostsCarousel(emptyList(), false, "offline", { retries++ }, {}) } }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("The spotlight is open").assertDoesNotExist()
        assertEquals(1, retries)
    }
    @Test fun carouselSwipesBetweenPhotoAndVideoAndOpensSelectedCreator() {
        val uri = "android.resource://${InstrumentationRegistry.getInstrumentation().targetContext.packageName}/${R.drawable.image1}"
        val photo = PublishedPost("photo", SpotMockData.mika, "Photo moment", listOf("Art"), emptyList(),
            listOf(PublishedMedia(uri, "image/jpeg", "test")), "public", 25, 10)
        val video = photo.copy(id = "video", author = SpotMockData.deandra,
            media = listOf(PublishedMedia(uri, "video/mp4", "test", uri)))
        var opened = ""
        compose.setContent { SPOTAppTheme { PopularPostsCarousel(listOf(photo, video), false, "", {}, { opened = it.id }) } }
        compose.onNodeWithText("Mika Suarez").assertIsDisplayed().performClick()
        assertEquals("photo", opened)
        screenshot("popular-photo")
        compose.onNodeWithTag("popular-carousel").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Deandra Pasquin").assertIsDisplayed().performClick()
        assertEquals("video", opened)
        screenshot("popular-video")
    }
    @Test fun homeKeepsHeaderStoriesFeedAndNavigationWithTheCarousel() {
        val uri = "android.resource://${InstrumentationRegistry.getInstrumentation().targetContext.packageName}/${R.drawable.image1}"
        val item = PublishedPost("photo", SpotMockData.mika, "A new talent moment", listOf("Dance"), emptyList(),
            listOf(PublishedMedia(uri, "image/jpeg", "test")), "public", 25, 10)
        compose.setContent { SPOTAppTheme {
            SpotHomePage(SpotMockData.mika, SpotMockData.homePostFor(SpotMockData.mika), null, {}, {}, {}, {}, {},
                contentAfterStories = { PopularPostsCarousel(listOf(item), false, "", {}, {}) })
        } }
        compose.onNodeWithText("SPOT").assertIsDisplayed()
        compose.onNodeWithText("Your Story").assertIsDisplayed()
        compose.onNodeWithText("Popular on SPOT").assertIsDisplayed()
        compose.onNodeWithContentDescription("Home").assertIsDisplayed()
        screenshot("home-carousel-layout")
    }
}
