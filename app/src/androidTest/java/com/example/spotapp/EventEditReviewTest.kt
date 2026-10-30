package com.example.spotapp

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.AccountType
import com.example.spotapp.data.CampusEvent
import com.example.spotapp.data.DiscoverRepository
import com.example.spotapp.data.Supabase
import com.example.spotapp.data.TalentFee
import com.example.spotapp.data.awaitSession
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.discover.CommunityDetails
import com.example.spotapp.ui.theme.screens.discover.CommunityEditor
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Pictures of editing an event, and of the organizer's own event page, at SPOT's largest text (1.15×). Saved under
 * the app's external files, so `adb pull /sdcard/Android/data/com.example.spotapp/files/event-edit-review` copies
 * them. Nothing is saved, deleted or cancelled: the tests only open the screens.
 */
class EventEditReviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var dark by mutableStateOf(false)

    private fun save(name: String) {
        compose.waitForIdle()
        val directory = File(context.getExternalFilesDir(null), "event-edit-review").apply { mkdirs() }
        File(directory, "$name-${if (dark) "dark" else "light"}.png").outputStream().use {
            compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun editFormInLightAndDark() {
        val organizer = SpotUiProfile("edit-review", "Ben Reyes", "ben", "", "", accountType = AccountType.TALENT_SEEKER,
            organization = "Indie Studio")
        val event = CampusEvent("edit-review", "Gig Night", "An acoustic night on the quad. Bring your best set.", "",
            "Singing", System.currentTimeMillis() + 3 * 86_400_000L, "Main Hall", "Indie Studio", "edit-review", "Ben Reyes", "",
            5L, 2L, 0L, TalentFee.PAID, "₱1,500 per act")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.15f)) {
                SPOTAppTheme(dark = dark) { CommunityEditor(true, organizer, {}, {}, existing = event) }
            }
        }
        for (mode in listOf(false, true)) {
            dark = mode
            save("1-edit-top")
            compose.onNodeWithText("With talent fee").performScrollTo()
            save("2-edit-bottom")
            compose.onNodeWithText("Event title").performScrollTo()
        }
    }

    /** The signed-in organizer's next event as they see it, with Edit and Cancel/Delete. Skipped for other accounts. */
    @Test fun organizerEventPage() {
        runBlocking { awaitSession() }
        val uid = Supabase.uid()
        assumeTrue("Sign in on the phone first", uid != null)
        val upcoming = runBlocking { DiscoverRepository.shared.eventsBy(uid!!) }.items
            .firstOrNull { !it.cancelled && it.startsAt > System.currentTimeMillis() }
        assumeTrue("The signed-in account has no upcoming events", upcoming != null)
        val me = SpotUiProfile(uid!!, "", "", "", "", accountType = AccountType.TALENT_SEEKER)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.15f)) {
                SPOTAppTheme(dark = dark) { CommunityDetails(upcoming!!.eventId, true, me, close = {}, onProfile = {}, onChat = {}) }
            }
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("event-edit", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("event-remove", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("event-edit", useUnmergedTree = true).performScrollTo()
        save("3-organizer-page")
    }
}
