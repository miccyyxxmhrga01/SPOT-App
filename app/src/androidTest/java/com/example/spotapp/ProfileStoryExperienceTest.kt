package com.example.spotapp

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.home.*
import com.example.spotapp.ui.theme.screens.profile.ConnectedEditProfile
import com.example.spotapp.ui.theme.screens.settings.SettingsScreen
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class ProfileStoryExperienceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun awake() { compose.activityRule.scenario.onActivity {
        it.enableEdgeToEdge(); it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    } }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(300)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.cacheDir, "profile-story-review").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun listenersStopInBackgroundAndRestartWithoutDuplicates() {
        var active = 0; var starts = 0
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            LifecycleDisposableEffect(Unit) { active++; starts++; onDispose { active-- } }; Text("Listening")
        } }
        compose.runOnIdle { assertEquals(1, active) }
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle { assertEquals(0, active) }
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.runOnIdle { assertEquals(1, active); assertEquals(2, starts) }
    }
    @Test fun compactSearchClearsWithoutLosingItsLayout() {
        var query by mutableStateOf("Music")
        compose.setContent { SPOTAppTheme { Column(Modifier.statusBarsPadding().padding(16.dp)) { SpotSearchField(query, { query = it }) } } }
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.runOnIdle { assertEquals("", query) }
        compose.onNodeWithContentDescription("Clear search").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("Dance")
        compose.onNodeWithText("Dance").assertIsDisplayed()
    }
    @Test fun settingsSearchAndSubpagesNavigateWithoutChangingAccountData() {
        val profile = SpotUiProfile("", "Campus Creator", "campus_creator", "Dancing", ProfileCourseOptions[0])
        var closed = false
        compose.setContent { SPOTAppTheme { SettingsScreen({ closed = true }, {}, profile = profile) } }
        // Common switches sit on the main page; this test only reads them.
        compose.onNodeWithText("Private account").assertIsDisplayed()
        compose.onNodeWithText("Open to collabs").assertExists()
        screenshot("settings")
        compose.onNodeWithText("Audience").performScrollTo().performClick()
        compose.onNodeWithText("Posts").assertIsDisplayed()
        compose.onNodeWithText("Stories").assertIsDisplayed()
        screenshot("audience")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("follow")
        compose.onNodeWithText("New followers").assertIsDisplayed()
        compose.onNodeWithText("Follow requests").assertIsDisplayed()
        compose.onNodeWithText("Likes").assertDoesNotExist()
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithText("Saved posts").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
    @Test fun editProfileKeepsAllSixCourseChoicesAndManualTalentSelection() {
        val profile = SpotUiProfile("ui-fixture", "Campus Creator", "campus_creator", "Dancing", ProfileCourseOptions[0],
            accountType = AccountType.STUDENT, bio = "Sharing campus talent", talentCategories = listOf("Dancing"))
        compose.setContent { SPOTAppTheme { ConnectedEditProfile(profile, {}, {}) } }
        compose.onNodeWithText(ProfileCourseOptions[0]).performScrollTo().performClick()
        ProfileCourseOptions.forEach { option -> compose.onNode(hasText(option) and hasAnyAncestor(isDialog()), useUnmergedTree = true).assertExists() }
        compose.onNode(hasText(ProfileCourseOptions[4]) and hasAnyAncestor(isDialog()), useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText(ProfileCourseOptions[4]).assertIsDisplayed()
        screenshot("edit-profile")
        compose.onNodeWithText("Talents").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("profile-save").assertIsDisplayed()
    }
    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun ownCommentCanBeRemovedWithoutRemovingOtherPeoplesComments() {
        var rows by mutableStateOf(listOf(SpotUiComment("mine", "creator", text = "My comment", userId = "me"), SpotUiComment("theirs", "friend", text = "Their comment", userId = "friend")))
        compose.setContent { SPOTAppTheme { HomeCommentsSheet(rememberModalBottomSheetState(skipPartiallyExpanded = true), rows, null, {}, {}, {},
            currentUserId = "me", onDelete = { id -> rows = rows.filterNot { it.id == id } }) } }
        compose.onAllNodesWithContentDescription("Delete comment").assertCountEquals(1)
        compose.onNodeWithContentDescription("Delete comment").performClick()
        compose.onNodeWithText("My comment").assertDoesNotExist()
        compose.onNodeWithText("Their comment").assertIsDisplayed()
        screenshot("comments")
    }
    @Test fun storyViewerMovesForwardBackAndClosesFromTheLastMoment() {
        val uri = "android.resource://${InstrumentationRegistry.getInstrumentation().targetContext.packageName}/${R.drawable.image1}"
        val moments = listOf(StoryItem("one", "ui-fixture", uri, "image", System.currentTimeMillis(), username = "First moment"),
            StoryItem("two", "ui-fixture", uri, "image", System.currentTimeMillis(), username = "Second moment"))
        var closed = false
        compose.mainClock.autoAdvance = false
        compose.setContent { SPOTAppTheme { StoryViewer(moments, "ui-fixture", { closed = true }) } }
        compose.mainClock.advanceTimeBy(240)
        compose.onNodeWithText("First moment").assertIsDisplayed()
        screenshot("story-viewer")
        compose.onNodeWithContentDescription("Next story").performClick(); compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("Second moment").assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous story").performClick(); compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("First moment").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next story").performClick(); compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithContentDescription("Next story").performClick(); compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertTrue(closed) }
    }
}
