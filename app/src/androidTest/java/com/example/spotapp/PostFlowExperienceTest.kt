package com.example.spotapp

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.post.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class PostFlowExperienceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val profile = SpotUiProfile("post-flow-ui-test", "Campus creator", "creator", "Guitar", "BSCS")
    @Before fun awake() { compose.activityRule.scenario.onActivity {
        it.enableEdgeToEdge()
        it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } }
    private fun capture(name: String) {
        val folder = File(context.cacheDir, "post-flow-review").apply { mkdirs() }
        val bitmap = if (name == "video-editor" || name == "details-keyboard") InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() else compose.onRoot().captureToImage().asAndroidBitmap()
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun mediaStepsPreserveEditsCaptionAndDraftOnBackNavigation() {
        val uri = PostTestMedia.image(context)
        val store = PostDraftStore(context, profile.uid)
        store.save(PostDraft("post-flow-ui", "", listOf("Guitar"), emptyList(), listOf(uri), "followers"))
        var saved = false
        compose.setContent { SPOTAppTheme { NewPostScreen(profile, { saved = true }, {}) } }
        compose.onNodeWithTag("post-picker").assertExists(); capture("picker")
        compose.onNodeWithTag("post-next").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Rotate").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Rotate").performClick()
        compose.onNodeWithContentDescription("Crop").performClick()
        compose.onNodeWithText("Square · 1:1").performClick()
        compose.onNodeWithTag("post-editor").assertExists(); capture("image-editor")
        compose.runOnIdle { assertFalse(androidx.core.view.WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).isAppearanceLightStatusBars) }
        compose.onNodeWithTag("post-next").performClick()
        compose.runOnIdle { assertTrue(androidx.core.view.WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).isAppearanceLightStatusBars) }
        compose.onNodeWithTag("post-caption").performClick().performTextInput("A new campus performance")
        compose.waitUntil(5000) { androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true }
        compose.onNodeWithTag("publish-post").assertIsDisplayed()
        capture("details-keyboard")
        compose.onNodeWithTag("post-category").performScrollTo().performClick()
        compose.onNode(hasText("Guitar") and isSelected()).assertExists()
        compose.onNodeWithText("Done").performClick()
        capture("details")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("post-editor").assertExists()
        compose.onNodeWithTag("post-next").performClick()
        compose.onNodeWithTag("post-caption").assertTextContains("A new campus performance")
        compose.onNodeWithTag("save-draft").performClick()
        compose.waitUntil(5000) { saved }
        val draft = store.load()!!
        assertEquals("A new campus performance", draft.caption)
        assertEquals(PostMediaEdit(1, "square"), draft.mediaEdits[uri])
        assertFalse(draft.aiCaption || draft.aiTagged || draft.aiCategorySuggested); store.clear()
    }
    @Test fun mixedMediaEditorSwitchesClipsAndKeepsMuteChoice() {
        val video = PostTestMedia.video(context); val photo = PostTestMedia.image(context, "second")
        val state = mutableStateOf(PostDraft("post-mixed-ui", "", listOf("Guitar"), emptyList(), listOf(video, photo), "followers"))
        compose.setContent { SPOTAppTheme { PostMediaEditor(state.value, { state.value = it }, true, {}, {}, {}) } }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Mute").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Mute").performClick()
        compose.onNodeWithContentDescription("Unmute").assertExists()
        fun findPlayer(view: android.view.View): androidx.media3.ui.PlayerView? {
            if (view is androidx.media3.ui.PlayerView) return view
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) findPlayer(view.getChildAt(i))?.let { return it }
            return null
        }
        compose.waitUntil(15000) {
            var playing = false
            compose.runOnUiThread { val player = findPlayer(compose.activity.window.decorView)?.player; playing = player?.isPlaying == true && player.currentPosition > 100 }
            playing
        }
        compose.runOnUiThread { assertEquals(0f, findPlayer(compose.activity.window.decorView)!!.player!!.volume, 0f) }
        capture("video-editor")
        compose.onNodeWithTag("selected-media-1").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Rotate").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithTag("active-social-video").assertCountEquals(0)
        compose.onNodeWithTag("selected-media-0").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("active-social-video").fetchSemanticsNodes().size == 1 }
        assertTrue(state.value.mediaEdits[video]!!.muted)
    }
    @Test fun mediaGridShowsSelectionOrder() {
        val one = PostTestMedia.image(context, "grid-one"); val two = PostTestMedia.image(context, "grid-two")
        val state = mutableStateOf(emptyList<String>())
        compose.setContent { SPOTAppTheme { PostMediaGrid(listOf(LibraryMedia(one, false), LibraryMedia(two, false)), state.value, Modifier.fillMaxSize(), { state.value = orderedMediaSelection(state.value, it) }) } }
        compose.onAllNodesWithContentDescription("Photo")[1].performClick()
        compose.onNodeWithContentDescription("Photo").performClick()
        assertEquals(listOf(two, one), state.value)
        compose.onNodeWithContentDescription("Photo, selected 1").assertExists()
        compose.onNodeWithContentDescription("Photo, selected 2").assertExists()
    }
    @Test fun detailsRestoreAfterRecreationAndRequireCategoryBeforePublishing() {
        val store = PostDraftStore(context, profile.uid); store.clear()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { SPOTAppTheme { NewPostScreen(profile, {}, { error("Invalid draft must not publish") }) } }
        compose.onNodeWithText("Write a text post").performClick()
        compose.onNodeWithTag("post-caption").performTextInput("A draft worth keeping")
        compose.onNodeWithTag("publish-post").performClick()
        compose.onNodeWithText("Choose a talent category, or pick Personal to post without one.").performScrollTo().assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("post-details").assertExists()
        compose.onNodeWithTag("post-caption").assertTextContains("A draft worth keeping")
        compose.onNodeWithTag("post-tag-input").performScrollTo().performTextInput("CampusTalent")
        compose.onNodeWithTag("post-tag-input").performImeAction()
        compose.onNodeWithText("#CampusTalent").assertExists()
        store.clear()
    }
}
