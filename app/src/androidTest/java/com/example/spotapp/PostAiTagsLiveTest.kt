package com.example.spotapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.PostDraft
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.post.PostDetailsScreen
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * The real post composer with a test photo: Write with AI › Hashtags asks Gemini once (as the phone's signed-in
 * member), then hashtags are added. It never publishes. Pictures and a log go to the app's external files:
 * `adb pull /sdcard/Android/data/com.example.spotapp/files/ai-tags-live`.
 */
class PostAiTagsLiveTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory get() = File(context.getExternalFilesDir(null), "ai-tags-live").apply { mkdirs() }
    private val log = StringBuilder()

    private fun shot(name: String, node: SemanticsNodeInteraction) {
        compose.waitForIdle()
        File(directory, "$name.png").outputStream().use { node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun suggestTagsInTheComposer() {
        val photo = File(context.cacheDir, "ai-tags-live.jpg")
        Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(250, 244, 230))
            Canvas(this).drawText("🎸", 256f, 330f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 300f; textAlign = Paint.Align.CENTER })
        }.let { bitmap -> photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
        val profile = SpotUiProfile("ai-tags-live", "Campus creator", "creator", "Guitar", "BSCS")
        var draft by mutableStateOf(PostDraft("ai-tags-live", "", listOf("Guitar"), emptyList(), listOf(Uri.fromFile(photo).toString()),
            "public", personal = true))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.15f)) {
                SPOTAppTheme { PostDetailsScreen(profile, draft, { draft = it }, "", false, {}, {}, {}, {}) }
            }
        }
        try {
            // Everything happens on the page: no sheet opens.
            compose.onNodeWithTag("ai-suggest-tags").performScrollTo()
            shot("1-page", compose.onRoot())
            compose.onNodeWithTag("ai-suggest-tags").performClick()
            compose.waitUntil(60_000) {
                compose.onAllNodesWithTag("ai-tag-ideas").fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithTag("ai-assist-progress").fetchSemanticsNodes().isEmpty()
            }
            log.appendLine("sheet opened: ${compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()}")
            log.appendLine("ideas shown: ${compose.onAllNodesWithTag("ai-tag-ideas").fetchSemanticsNodes().isNotEmpty()}")
            log.appendLine("chips: ${compose.onAllNodes(hasTestTagPrefix("ai-tag-")).fetchSemanticsNodes().size}")
            compose.onNodeWithTag("ai-tag-ideas").performScrollTo()
            shot("2-tags", compose.onRoot())
            compose.onNodeWithTag("ai-add-all-tags").performClick()
            compose.waitForIdle()
            log.appendLine("after Add all: ${draft.tags} aiTagged=${draft.aiTagged}")
            // Added ideas move up into the post's hashtags, so the ideas card is gone.
            log.appendLine("ideas left: ${compose.onAllNodesWithTag("ai-tag-ideas").fetchSemanticsNodes().size}")
            compose.onNodeWithTag("post-tag-input").performScrollTo()
            shot("3-added", compose.onRoot())
            // The caption ideas come from the same answer, so no second request.
            compose.onNodeWithTag("ai-generate-caption").performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("ai-caption-ideas").performScrollTo()
            shot("4-captions", compose.onRoot())
            compose.onNodeWithTag("post-use-caption-idea").performClick()
            compose.waitForIdle()
            log.appendLine("caption: ${draft.caption} aiCaption=${draft.aiCaption}")
            // A hashtag typed but not added still goes in when the draft is saved.
            compose.onNodeWithTag("post-tag-input").performScrollTo().performTextInput("latenight")
            compose.onNodeWithTag("save-draft").performClick()
            compose.waitForIdle()
            log.appendLine("after Draft: ${draft.tags}")
            shot("5-saved", compose.onRoot())
        } catch (e: Throwable) {
            log.appendLine("FAILED: $e")
            runCatching { shot("x-failure", compose.onRoot()) }
            throw e
        } finally {
            File(directory, "log.txt").writeText(log.toString())
            photo.delete()
        }
    }

    private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("testTag starts with $prefix") { node ->
        val tag = node.config.getOrNull(SemanticsProperties.TestTag)
        tag != null && tag.startsWith(prefix) && tag != "ai-tag-ideas"
    }
}
