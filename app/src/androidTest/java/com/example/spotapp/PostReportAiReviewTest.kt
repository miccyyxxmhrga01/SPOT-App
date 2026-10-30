package com.example.spotapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.PostDraft
import com.example.spotapp.data.PostMediaEdit
import com.example.spotapp.data.PublishedPost
import com.example.spotapp.data.ai.PostAiAssist
import com.example.spotapp.data.ai.PostAiSuggestions
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.home.ReportPostPage
import com.example.spotapp.ui.theme.screens.post.AiCaptionIdeas
import com.example.spotapp.ui.theme.screens.post.AiWriteRow
import com.example.spotapp.ui.theme.screens.post.HashtagsField
import com.example.spotapp.ui.theme.screens.post.PostAiAssistState
import com.example.spotapp.ui.theme.screens.post.PostAiAssistUi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import androidx.compose.foundation.background

/**
 * Pictures of the post report page and AI Assist's caption and hashtag ideas at the test phone's usable size and
 * SPOT's largest text (1.15×), in light and dark. Saved under the app's external files, so
 * `adb pull /sdcard/Android/data/com.example.spotapp/files/report-ai-review` copies them without root.
 * Nothing here sends a report or calls Gemini, except [aiAssistReachesTheFunction].
 */
class PostReportAiReviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val author = SpotUiProfile("report-review", "Mika Reyes", "mikareyes", "Singing", "BS Music")
    private var dark by mutableStateOf(false)

    private fun save(name: String, node: SemanticsNodeInteraction) {
        compose.waitForIdle()
        val directory = File(context.getExternalFilesDir(null), "report-ai-review").apply { mkdirs() }
        File(directory, "$name-${if (dark) "dark" else "light"}.png").outputStream().use {
            node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun reportPageInLightAndDark() {
        val post = PublishedPost("report-review", author, "Late night practice", listOf("Singing"), emptyList(), emptyList(), "public")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.15f)) {
                SPOTAppTheme(dark = dark) { ReportPostPage(post, {}, {}) }
            }
        }
        for (mode in listOf(false, true)) {
            dark = mode
            save("1-report", compose.onNode(isDialog()))
            compose.onNodeWithTag("post-report-other").performScrollTo().performClick()
            compose.onNodeWithTag("post-report-details").performScrollTo().performTextInput("A fake giveaway asking for passwords")
            save("2-report-something-else", compose.onNode(isDialog()))
        }
    }

    @Test fun aiAssistIdeasInLightAndDark() {
        var draft by mutableStateOf(PostDraft("ai-review", "", listOf("Singing"), listOf("campustalent"),
            listOf("file:///review/cover.jpg"), "public"))
        // What the caption card shows once Gemini has answered both buttons.
        val answered = PostAiAssistUi(PostAiAssistState(inputKey = "review", showCaptions = true, showTags = true,
            suggestions = PostAiSuggestions(listOf("Finding my voice, one late-night practice at a time 🎤",
                "Me vs. the high note: round 3", "Just me and a song I love."),
                listOf("singing", "vocalcover", "campustalent", "latenightpractice", "studentmusician", "acoustic", "spotlight", "musiclover"))),
            available = true, ask = {}, retry = {})
        val pending = mutableStateOf("")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.15f)) {
                SPOTAppTheme(dark = dark) {
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.requiredSize(360.dp, 728.dp).background(SpotPalette.Ivory).verticalScroll(rememberScrollState())
                            .padding(16.dp).background(SpotPalette.Surface).padding(16.dp).testTag("review"),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            AiWriteRow(answered, enabled = true)
                            AiCaptionIdeas(answered, draft, { draft = it }, enabled = true)
                            HashtagsField(draft, { draft = it }, pending, answered, enabled = true)
                        }
                    }
                }
            }
        }
        for (mode in listOf(false, true)) {
            dark = mode
            save("3-ai-ideas", compose.onNodeWithTag("review"))
            compose.onNodeWithTag("ai-tag-singing").performClick()
            compose.onNodeWithTag("post-use-caption-idea").performClick()
            save("4-ai-ideas-used", compose.onNodeWithTag("review"))
            draft = draft.copy(caption = "", tags = listOf("campustalent"), aiCaption = false, aiTagged = false)
        }
    }

    /**
     * Calls the live post-ai function as the phone's signed-in member. Until the GEMINI_API_KEY secret is set it
     * answers "AI Assist isn't set up yet." without counting a request; after that it returns real ideas and counts one.
     * The answer is saved to report-ai-review/ai-assist-check.txt.
     */
    @Test fun aiAssistReachesTheFunction() = runBlocking {
        val photo = File(context.cacheDir, "ai-assist-check.jpg")
        // A guitar drawn with the phone's emoji font gives Gemini something real to describe.
        Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(250, 244, 230))
            Canvas(this).drawText("🎸", 256f, 330f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 300f; textAlign = Paint.Align.CENTER })
        }.let { bitmap -> photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
        val outcome = try {
            PostAiAssist.suggest(context, Uri.fromFile(photo).toString(), PostMediaEdit(), listOf("Guitar"), "", false).toString()
        } catch (e: IllegalStateException) { e.message.orEmpty() }
        photo.delete()
        File(File(context.getExternalFilesDir(null), "report-ai-review").apply { mkdirs() }, "ai-assist-check.txt").writeText(outcome)
        assertTrue(outcome, outcome == "AI Assist isn't set up yet." || outcome.startsWith("PostAiSuggestions("))
    }
}
