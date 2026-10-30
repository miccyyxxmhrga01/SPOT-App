package com.example.spotapp

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.CampusGroup
import com.example.spotapp.data.PublishedPost
import com.example.spotapp.data.Supabase
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.models.SpotUiPost
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.discover.CommunityEditor
import com.example.spotapp.ui.theme.screens.home.EditPostPage
import com.example.spotapp.ui.theme.screens.home.HomeCommentsSheet
import com.example.spotapp.ui.theme.screens.home.HomePostCard
import com.example.spotapp.ui.theme.screens.home.PostOptionsSheet
import com.example.spotapp.ui.theme.screens.home.SpotUiComment
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Pictures of the owner controls (post menu, edit post, comment editing, comments off, edit group) at SPOT's largest
 * text (1.15×), drawn from made-up posts, comments and a made-up group. Whole-screen shots, so bottom sheets and
 * dialogs are included. Saved under the app's external files:
 * `adb pull /sdcard/Android/data/com.example.spotapp/files/owner-review`.
 * Nothing is written: the only reads are the signed-in account's id (to show the owner's menu) and, for someone
 * else's post, whether that account follows the made-up author.
 */
class OwnerControlsReviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val me get() = Supabase.uid() ?: "00000000-0000-0000-0000-0000000000aa"
    private val author get() = SpotUiProfile(me, "Ana Cruz", "ana_c", "Singing", "BS Music", "")
    private val stranger = SpotUiProfile("00000000-0000-0000-0000-0000000000bb", "Ben Reyes", "ben", "Guitar", "", "")

    private fun post(owner: SpotUiProfile) = PublishedPost("review-post", owner, "Open mic at 7 in the main hall. Come sing with us!",
        listOf("Singing"), listOf("music", "live"), emptyList(), "followers", likes = 12, location = "Main hall",
        edited = true, pinned = true, likesHidden = false)

    private fun save(name: String) {
        compose.waitForIdle()
        Thread.sleep(700)
        val shot = instrumentation.uiAutomation.takeScreenshot()
        assertTrue("screenshot failed for $name", shot != null)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "owner-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.15f)) {
                SPOTAppTheme(dark = dark) { Box(Modifier.fillMaxSize().background(SpotPalette.Ivory)) { content() } }
            }
        }
    }

    @Test fun ownPostOptionsLight() { show { PostOptionsSheet(post(author), {}, own = true) }; save("01-own-post-options-light") }

    @Test fun ownPostOptionsDark() { show(dark = true) { PostOptionsSheet(post(author), {}, own = true) }; save("01-own-post-options-dark") }

    @Test fun someoneElsesPostOptions() {
        show { PostOptionsSheet(post(stranger), {}, onProfile = {}, onNotInterested = {}, onReport = {}, own = false) }
        save("02-others-post-options")
    }

    @Test fun deleteQuestion() {
        show { PostOptionsSheet(post(author), {}, own = true) }
        compose.onNodeWithTag("post-option-delete").performClick()
        save("03-delete-post-question")
    }

    @Test fun editPost() { show { EditPostPage(post(author), {}, {}) }; save("04-edit-post") }

    @Test fun feedCardShowsEditedAndHidesLikes() {
        show {
            Column(Modifier.padding(top = 40.dp)) {
                HomePostCard(SpotUiPost("review-post", stranger, "Open mic at 7 in the main hall.", "Singing", listOf("music"),
                    postedTime = "2h ago", likes = 12, comments = 3, edited = true, likesHidden = true),
                    {}, {}, {}, {}, {}, {}, {}, {}, onMore = {})
            }
        }
        save("05-feed-card-edited-likes-hidden")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Comments(commentsOff: Boolean) {
        val comments = listOf(
            SpotUiComment("c1", "ana_c", null, "See you at 7!", "5m ago", userId = me, edited = true),
            SpotUiComment("c2", "ben", null, "Can't wait", "12m ago", likes = 2, userId = stranger.uid))
        HomeCommentsSheet(rememberModalBottomSheetState(skipPartiallyExpanded = true), comments, null, {}, {}, {},
            currentUserId = me, onDelete = {}, postOwner = true, commentsOff = commentsOff, onEdit = { _, _ -> })
    }

    @Test fun commentsForThePostsOwner() {
        show { Comments(commentsOff = false) }
        save("06-comments-owner")
        compose.onNodeWithContentDescription("Edit comment").performClick()
        save("07-comment-editing")
    }

    @Test fun commentsTurnedOff() { show { Comments(commentsOff = true) }; save("08-comments-off") }

    @Test fun editGroup() {
        show {
            CommunityEditor(false, author, {}, {}, existingGroup = CampusGroup("review-group", "Sunday Band", "We jam every Sunday.",
                "", "Guitar", "private", me, "Ana Cruz", "", 8, 0))
        }
        save("09-edit-group")
    }
}
