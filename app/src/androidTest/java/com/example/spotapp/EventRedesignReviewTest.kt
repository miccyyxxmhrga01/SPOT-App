package com.example.spotapp

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.TalentFee
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.screens.discover.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Pictures of every redesigned event screen, drawn from made-up data at SPOT's largest text (1.15×), in light and dark,
 * to compare with the "SPOT Event Redesign" mock. Saved under the app's external files, so
 * `adb pull /sdcard/Android/data/com.example.spotapp/files/event-review` copies them. Nothing is read from or written
 * to the account: the screens are drawn from EventSamples.
 */
class EventRedesignReviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var dark by mutableStateOf(false)
    private var screen by mutableStateOf("")

    private fun save(name: String) {
        compose.waitForIdle()
        val directory = File(context.getExternalFilesDir(null), "event-review").apply { mkdirs() }
        File(directory, "$name-${if (dark) "dark" else "light"}.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun show(name: String) { screen = name; compose.waitForIdle() }

    @Test fun eventScreensInLightAndDark() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.15f)) {
                SPOTAppTheme(dark = dark) {
                    Box(Modifier.fillMaxSize().background(SpotPalette.Ivory)) { key(screen, dark) { Screen(screen) } }
                }
            }
        }
        for (mode in listOf(false, true)) {
            dark = mode
            show("01-tab-creator"); save("01-tab-creator")
            compose.onNodeWithTag("discover-events").performScrollToNode(hasText("Past")); save("01-tab-creator-scrolled")
            show("02-tab-spotter"); save("02-tab-spotter")
            show("03-event-apply"); save("03-event-apply")
            compose.onNodeWithText("About").performScrollTo(); save("03-event-apply-scrolled")
            show("04-event-applied"); save("04-event-applied")
            show("05-event-going"); save("05-event-going")
            compose.onNodeWithText("Leave event").performScrollTo(); save("05-event-going-scrolled")
            show("06-event-organizer"); save("06-event-organizer")
            compose.onNodeWithText("Cancel event").performScrollTo(); save("06-event-organizer-scrolled")
            show("07-statuses"); save("07-statuses")
            show("07-event-cancelled"); save("07-event-cancelled")
            show("08-apply-sheet"); save("08-apply-sheet")
            show("09-applications"); save("09-applications")
            show("09-applications-earlier"); save("09-applications-earlier")
            show("10-new-event"); save("10-new-event")
            compose.onNodeWithText("Performer slots").performScrollTo(); save("10-new-event-middle")
            compose.onNodeWithText("With talent fee").performScrollTo(); save("10-new-event-bottom")
            show("11-edit-event"); save("11-edit-event")
            compose.onNodeWithTag("event-remove-card").performScrollTo(); save("11-edit-event-bottom")
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Screen(name: String) {
        val now = System.currentTimeMillis()
        val event = EventSamples.acousticNight()
        when (name) {
            "01-tab-creator" -> EventsTabContent(EventSamples.creator, EventSamples.events(), EventSamples.creatorStatus, emptyList(), false,
                EventFilter.ALL, "", "", false, "", false, now, {}, {}, {}, {}, { _, _ -> }, {}, {})
            "02-tab-spotter" -> EventsTabContent(EventSamples.spotter, EventSamples.events(), emptyMap(), EventSamples.organized(), true,
                EventFilter.ALL, "", "", false, "", false, now, {}, {}, {}, {}, { _, _ -> }, {}, {})
            "03-event-apply" -> page(EventSamples.creator)
            "04-event-applied" -> page(EventSamples.creator, application = EventSamples.application())
            "05-event-going" -> page(EventSamples.creator, joined = true)
            "06-event-organizer" -> page(EventSamples.spotter)
            "07-event-cancelled" -> page(EventSamples.creator, event = event.copy(cancelled = true))
            "07-statuses" -> Statuses()
            "08-apply-sheet" -> Box(Modifier.fillMaxSize()) {
                page(EventSamples.creator)
                Box(Modifier.fillMaxSize().background(Color(0x8C17150F)))
                Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = SpotPalette.Surface) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BottomSheetDefaults.DragHandle()
                        ApplySheetBody(event, "", {}, false, {}, {})
                    }
                }
            }
            "09-applications" -> ApplicationsScreen(event, EventSamples.applications(), EventSamples.applicants, ApplicationsTab.WAITING, {},
                false, "", now, {}, {}, { _, _ -> }, {})
            "09-applications-earlier" -> ApplicationsScreen(event, EventSamples.applications(), EventSamples.applicants, ApplicationsTab.EARLIER, {},
                false, "", now, {}, {}, { _, _ -> }, {})
            "10-new-event" -> {
                val form = remember { EventForm(null, "Indie Studio", EventForm.defaultStart()).apply {
                    title = "Acoustic Night at the Quad"; category = "Singing"; location = "Main Quad, North Campus"
                    talentFee = TalentFee.PAID; budget = "₱1,500 per act"; unlimited = false; limit = "8"
                } }
                EventEditorScreen(form, EventSamples.spotter, EventAudience(), false, 0f, "", {}, {})
            }
            "11-edit-event" -> {
                val form = remember { EventForm(event, "Indie Studio", EventForm.defaultStart()).apply { startsAt = event.startsAt + 3_600_000L } }
                EventEditorScreen(form, EventSamples.spotter, EventAudience(5, 3), false, 0f, "", {}, {}, onRemove = {})
            }
        }
    }

    @Composable
    private fun page(profile: com.example.spotapp.ui.theme.models.SpotUiProfile, joined: Boolean = false,
        application: com.example.spotapp.data.EventApplication? = null, event: com.example.spotapp.data.CampusEvent = EventSamples.acousticNight()) {
        val organizer = event.organizerId == profile.uid
        EventPageScreen(event, profile, joined, application, if (organizer) EventSamples.applications() else null, if (organizer) 2 else null,
            busy = false, error = "", ready = true, now = System.currentTimeMillis(), actions = EventPageActions())
    }

    /** Board 7: every banner and row pill. */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun Statuses() {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Statuses", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = SpotPalette.Ink)
            EventBanner(Icons.Rounded.Schedule, "Application sent", "Ben will accept or decline it.", BannerTone.GOLD)
            EventBanner(Icons.Rounded.Check, "You're performing", "Your slot is confirmed.", BannerTone.GREEN)
            EventBanner(Icons.Rounded.Close, "Not this time", "Ben declined your application.", BannerTone.GREY)
            EventBanner(Icons.Rounded.Lock, "Applications closed", "The event started before Ben answered.", BannerTone.GREY)
            EventBanner(Icons.Rounded.EventBusy, "Event cancelled", "Ben cancelled it. Registration is closed.", BannerTone.RED)
            EventBanner(Icons.Rounded.Groups, "All slots are filled", "Check back if a performer leaves.", BannerTone.GREY)
            Text("On event rows", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = SpotPalette.Ink)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EventStanding.entries.forEach { EventStatusPill(it) }
            }
        }
    }
}
