package com.example.spotapp

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.screens.home.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class StoryEditorExperienceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun awake() { compose.activityRule.scenario.onActivity { it.enableEdgeToEdge(); it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "story-editor-review").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun photoEditingMovesTextAddsStickerAndPublishesTheSameComposition() {
        var edits by mutableStateOf(StoryComposition())
        var published: StoryComposition? = null
        val source = "android.resource://${InstrumentationRegistry.getInstrumentation().targetContext.packageName}/${R.drawable.image1}"
        compose.setContent { SPOTAppTheme { StoryEditor(source, false, 0, edits, { edits = it }, "followers", false, {}, false, 0f, "", true, {}, {}, { published = edits }) } }
        compose.onNodeWithContentDescription("Add text").performClick()
        compose.onNodeWithTag("story-text-input").performTextInput("Campus spotlight")
        compose.onNodeWithContentDescription("Done editing text").performClick()
        compose.runOnIdle { assertEquals("Campus spotlight", edits.overlays.single().text) }
        val id = edits.overlays.single().id
        compose.onNodeWithTag("story-layer-$id").performTouchInput { swipe(center, center + Offset(-55f, -80f), 500) }
        compose.runOnIdle { assertTrue(edits.overlays.single().x < .5); assertTrue(edits.overlays.single().y < .5) }
        compose.onNodeWithContentDescription("Add sticker").performClick()
        compose.onNodeWithText("🔥").performClick()
        compose.runOnIdle { assertEquals(2, edits.overlays.size) }
        compose.onNodeWithContentDescription("Crop and zoom").performClick()
        compose.onNodeWithContentDescription("Zoom").performSemanticsAction(SemanticsActions.SetProgress) { it(1.6f) }
        compose.onNodeWithContentDescription("Done cropping").performClick()
        screenshot("photo-editor")
        compose.onNodeWithTag("publish-story").performClick()
        compose.runOnIdle { assertEquals(edits, published); assertEquals(edits, StoryComposition.decode(edits.encode())) }
    }
    @Test fun trimLimitsThePlaybackWindowAndPrivateAudienceStaysPrivate() {
        var edits by mutableStateOf(StoryComposition())
        var audience by mutableStateOf("followers")
        val source = "android.resource://${InstrumentationRegistry.getInstrumentation().targetContext.packageName}/${R.raw.dance}"
        val duration = kotlinx.coroutines.runBlocking { storyVideoDuration(InstrumentationRegistry.getInstrumentation().targetContext, source) }
        assertTrue("The video fixture needs a readable duration", duration > 1000)
        val start = (duration / 4).toFloat(); val end = (duration * 3 / 4).toFloat()
        edits = StoryComposition(trimEndMs = minOf(duration, 60_000))
        compose.setContent { SPOTAppTheme { StoryEditor(source, true, duration, edits, { edits = it }, audience, true, { audience = it }, false, 0f, "", true, {}, {}, {}) } }
        compose.onNodeWithContentDescription("Trim video").performClick()
        screenshot("video-trim-before")
        compose.onNodeWithTag("story-trim-range").assertIsDisplayed()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress), useUnmergedTree = true)[0].performSemanticsAction(SemanticsActions.SetProgress) { it(start) }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress), useUnmergedTree = true)[1].performSemanticsAction(SemanticsActions.SetProgress) { it(end) }
        compose.runOnIdle { assertEquals(start.toLong(), edits.trimStartMs); assertEquals(end.toLong(), edits.trimEndMs) }
        screenshot("video-trim")
        compose.onNodeWithContentDescription("Done trimming").performClick()
        compose.onNodeWithContentDescription("Audience: Followers").performClick()
        compose.onNodeWithText("Public").assertDoesNotExist()
        compose.onNodeWithText("Only me").performClick()
        compose.runOnIdle { assertEquals("private", audience) }
        compose.onNodeWithContentDescription("Mute video").performClick()
        compose.runOnIdle { assertTrue(edits.muted) }
    }
}
