package com.example.spotapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.view.WindowManager
import org.junit.Before
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.PostDraftStore
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.data.PostDraft
import com.example.spotapp.data.PublishedPost
import com.example.spotapp.ui.theme.screens.home.ReportPostPage
import com.example.spotapp.ui.theme.screens.post.AiWriteRow
import com.example.spotapp.ui.theme.screens.post.rememberPostAiAssist
import com.example.spotapp.ui.theme.screens.post.NewPostScreen
import com.example.spotapp.ui.theme.screens.discover.CommunityEditor
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CommunityExperienceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun awake() { compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }
    private val profile = SpotUiProfile("composer-ui-test", "Campus creator", "creator", "Guitar", "BSCS")
    private fun capture(name: String, node: SemanticsNodeInteraction = compose.onRoot()) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "social-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun aiAssistNeedsMediaBeforeItCanWrite() {
        val draft = PostDraft("ai-assist-ui-test", "", emptyList(), emptyList(), emptyList(), "public")
        compose.setContent { SPOTAppTheme { AiWriteRow(rememberPostAiAssist(draft), enabled = true) } }
        listOf("ai-generate-caption", "ai-suggest-tags").forEach { compose.onNodeWithTag(it).assertIsNotEnabled() }
        compose.onNodeWithText("Write with AI").assertIsDisplayed()
        compose.onNodeWithText("Add a photo or video to get ideas.").assertIsDisplayed()
    }
    // Only checks the form; it never sends a report.
    @Test fun reportPostNeedsAReasonAndDetailsForSomethingElse() {
        val post = PublishedPost("report-ui-test", profile, "A song", listOf("Singing"), emptyList(), emptyList(), "public")
        compose.setContent { SPOTAppTheme { ReportPostPage(post, {}, {}) } }
        compose.onNodeWithText("Send report").assertIsNotEnabled()
        compose.onNodeWithTag("post-report-spam").performClick()
        compose.onNodeWithText("Send report").assertIsEnabled()
        compose.onNodeWithTag("post-report-other").performScrollTo().performClick()
        compose.onNodeWithText("Send report").assertIsNotEnabled()
        compose.onNodeWithTag("post-report-details").performScrollTo().performTextInput("Fake giveaway")
        compose.onNodeWithText("Send report").assertIsEnabled()
        capture("report-post", compose.onNode(isDialog()))
    }
    @Test fun manualComposerSavesCaptionCategoryAndEditableTags() {
        val store = PostDraftStore(InstrumentationRegistry.getInstrumentation().targetContext, profile.uid)
        store.clear()
        var saved = false
        compose.setContent { SPOTAppTheme { NewPostScreen(profile, { saved = true }, {}) } }
        compose.onNodeWithText("Write a text post").performClick()
        capture("manual-composer")
        compose.onNodeWithTag("post-caption").performScrollTo().performTextInput("An acoustic set after class")
        compose.onNodeWithTag("post-category").performScrollTo().performClick()
        compose.onNodeWithText("Guitar", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Done").performClick()
        // Hashtags are added on the page itself.
        compose.onNodeWithTag("post-tag-input").performScrollTo().performTextInput("CampusTalent")
        compose.onNodeWithContentDescription("Add tag").performClick()
        compose.onNodeWithText("#CampusTalent").assertExists()
        capture("manual-tags")
        compose.onNodeWithTag("save-draft").performClick()
        compose.waitUntil(5000) { saved }
        val draft = store.load()!!
        assertEquals("An acoustic set after class", draft.caption)
        assertEquals(listOf("Guitar"), draft.categories); assertEquals(listOf("CampusTalent"), draft.tags)
        assertFalse(draft.aiCaption || draft.aiTagged || draft.aiCategorySuggested)
        store.clear()
    }
    @Test fun privateGroupExplainsOwnerApprovalWithoutCreatingData() {
        compose.setContent { SPOTAppTheme { CommunityEditor(false, profile, {}, {}) } }
        compose.onNodeWithText("Private").performScrollTo().performClick()
        compose.onNodeWithText("People can discover your group and request to join. You approve requests; only members see the member list.").performScrollTo().assertIsDisplayed()
        capture("private-group", compose.onNode(isDialog()))
    }
}
