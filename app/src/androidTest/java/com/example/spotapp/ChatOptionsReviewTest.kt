package com.example.spotapp

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.models.SpotMockData
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.discover.CommunityEditor
import com.example.spotapp.ui.theme.screens.messages.MessagesScreen
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Holding a chat in Messages, and the new Create group form, at SPOT's largest text (1.15×), drawn from made-up chats
 * in memory. Whole-screen shots, so the options sheet and its questions are included. Saved under the app's external
 * files: `adb pull /sdcard/Android/data/com.example.spotapp/files/chat-options-review`. Nothing is written anywhere.
 */
class ChatOptionsReviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun save(name: String) {
        compose.waitForIdle()
        Thread.sleep(700)
        val shot = instrumentation.uiAutomation.takeScreenshot()
        assertTrue("screenshot failed for $name", shot != null)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "chat-options-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.15f)) {
                SPOTAppTheme(dark = dark) { Box(Modifier.fillMaxSize().background(SpotPalette.Ivory)) { content() } }
            }
        }
    }

    private fun inbox(store: ReviewChatsStore, dark: Boolean = false) =
        show(dark) { MessagesScreen(SpotMockData.mika.copy(uid = "me"), {}, {}, store = store) }

    private fun hold(id: String) = compose.onNodeWithTag("conversation-$id").performTouchInput { longClick() }

    @Test fun holdingADirectChatOpensItsOptions() {
        val store = ReviewChatsStore()
        inbox(store)
        // Pinned and muted names show in full beside their icons.
        compose.onNodeWithText("Jamie Santos").assertIsDisplayed()
        save("01-inbox-with-pinned-chat")
        hold("alex")
        compose.onNodeWithTag("chat-options").assertExists()
        compose.onNodeWithText("Pin to top").assertExists()
        compose.onNodeWithText("Mark as read").assertExists()
        compose.onNodeWithText("View profile").assertExists()
        // The last option is on screen, not cut off below the sheet.
        compose.onNodeWithText("Remove from inbox").assertIsDisplayed()
        compose.onNodeWithText("Block").assertIsDisplayed()
        save("02-direct-chat-options")
    }

    @Test fun pinningMovesTheChatToTheTop() {
        val store = ReviewChatsStore()
        inbox(store)
        hold("dana")
        compose.onNodeWithText("Pin to top").performClick()
        compose.waitForIdle()
        assertTrue(store.state.value.preferences["dana"]?.pinned == true)
        // Dana was pinned last, so Dana's chat now leads, ahead of Jamie's earlier pin.
        val rows = compose.onAllNodes(hasTestTag("conversation-dana") or hasTestTag("conversation-jamie") or hasTestTag("conversation-alex"))
            .fetchSemanticsNodes().sortedBy { it.boundsInRoot.top }.map { it.config[SemanticsProperties.TestTag] }
        assertEquals(listOf("conversation-dana", "conversation-jamie", "conversation-alex"), rows)
        save("03-after-pinning")
    }

    @Test fun markingUnreadAddsTheDot() {
        val store = ReviewChatsStore()
        inbox(store)
        hold("jamie")
        compose.onNodeWithText("Mark as unread").performClick()
        compose.waitForIdle()
        assertTrue(store.state.value.preferences["jamie"]?.markedUnread == true)
        save("04-marked-unread")
    }

    @Test fun collaborationOptionsOfferLeaving() {
        val store = ReviewChatsStore()
        inbox(store)
        compose.onNodeWithTag("messages-tab-COLLABS").performClick()
        hold("band")
        compose.onNodeWithText("Leave collaboration").assertExists()
        compose.onNodeWithText("Block").assertDoesNotExist()
        save("05-collaboration-options")
        compose.onNodeWithText("Leave collaboration").performClick()
        compose.onNodeWithText("Leave collaboration?").assertExists()
        save("06-leave-question")
    }

    @Test fun eventChatOptionsSkipPinning() {
        val store = ReviewChatsStore()
        inbox(store)
        compose.onNodeWithTag("messages-tab-EVENTS").performClick()
        hold("open-mic")
        compose.onNodeWithText("Mark as unread").assertExists()
        // The Events tab keeps date order, and event chats are left by leaving the event.
        compose.onNodeWithText("Pin to top").assertDoesNotExist()
        compose.onNodeWithText("Leave collaboration").assertDoesNotExist()
        save("10-event-chat-options")
    }

    @Test fun directChatOptionsDark() {
        val store = ReviewChatsStore()
        inbox(store, dark = true)
        hold("dana")
        save("07-direct-chat-options-dark")
    }

    @Test fun newGroupForm() {
        show { CommunityEditor(false, SpotUiProfile("me", "Mika Santos", "mika", "Dance", "BSEd", ""), {}, {}) }
        compose.onNodeWithTag("group-editor").assertExists()
        save("08-new-group")
    }

    @Test fun newGroupFormDark() {
        show(dark = true) { CommunityEditor(false, SpotUiProfile("me", "Mika Santos", "mika", "Dance", "BSEd", ""), {}, {}) }
        save("09-new-group-dark")
    }
}

/** Four chats: Jamie's is pinned, Alex's has a new message, Dana's is muted, and a group collaboration. */
private class ReviewChatsStore : MessagesStore {
    override val uid = "me"
    private val now = System.currentTimeMillis()
    private fun direct(id: String, name: String, username: String, preview: String, from: String, minutesAgo: Int) =
        CampusConversation(id, "", creatorId = "me", partyIds = listOf("me", id), memberIds = listOf("me", id),
            people = mapOf(id to ChatPerson(id, name, username)), preview = preview, lastSenderId = from,
            updatedAt = now - minutesAgo * 60_000L)
    private val chats = listOf(
        direct("alex", "Alex Rivera", "alex", "Are you free for rehearsal tonight?", "alex", 2),
        direct("jamie", "Jamie Santos", "jamie", "See you at the open mic!", "me", 40),
        CampusConversation("band", "Sunday Band", "group", creatorId = "me", partyIds = listOf("me", "alex", "jamie"),
            memberIds = listOf("me", "alex", "jamie"), people = mapOf("alex" to ChatPerson("alex", "Alex Rivera"),
                "jamie" to ChatPerson("jamie", "Jamie Santos")), preview = "New song idea uploaded", lastSenderId = "jamie",
            updatedAt = now - 90 * 60_000L),
        direct("dana", "Dana Cruz", "dana", "Thanks for the photos", "dana", 300),
        CampusConversation("open-mic", "Open Mic Night", "group", creatorId = "alex", partyIds = listOf("me", "alex"),
            memberIds = listOf("me", "alex"), people = mapOf("alex" to ChatPerson("alex", "Alex Rivera")), preview = "Doors open at 6",
            lastSenderId = "alex", updatedAt = now - 10 * 60_000L, communityKind = "event", communityId = "ev-open-mic"))
    val state = MutableStateFlow(MessagesInbox(chats, preferences = mapOf(
        "jamie" to ChatPreferences(readAt = now, pinnedAt = now - 3_600_000),
        "band" to ChatPreferences(readAt = now),
        "open-mic" to ChatPreferences(readAt = now),
        "dana" to ChatPreferences(muted = true, readAt = now)), loading = false))
    override fun inbox() = state
    override fun messages(id: String) = MutableStateFlow(emptyList<CampusMessage>())
    override suspend fun olderMessages(id: String, before: CampusMessage) = emptyList<CampusMessage>()
    override suspend fun syncProfile() {}
    override suspend fun people(search: String) = emptyList<ChatPerson>()
    override suspend fun person(uid: String): ChatPerson? = null
    override suspend fun create(people: List<ChatPerson>, title: String, note: String, group: Boolean, id: String) = id
    override suspend fun respond(id: String, accept: Boolean) {}
    override suspend fun send(id: String, draft: ChatDraft) {}
    override suspend fun unsend(id: String, messageId: String) {}
    override suspend fun deleteForMe(id: String, messageId: String) {}
    override fun hiddenMessages(id: String) = MutableStateFlow(emptySet<String>())
    override suspend fun upload(context: Context, id: String, messageId: String, uri: Uri, mime: String?, progress: (Float) -> Unit): ChatAttachment = error("No upload")
    override suspend fun mediaFile(context: Context, attachment: ChatAttachment): Uri = Uri.EMPTY
    // As set_chat_preferences does it.
    override suspend fun preferences(id: String, values: Map<String, Any>) {
        val old = state.value.preferences[id] ?: ChatPreferences()
        val pin = values["pinned"] as? Boolean
        if (pin == true && !old.pinned && state.value.preferences.values.count { it.pinned } >= 3) error("You can pin up to 3 chats. Unpin one first.")
        val next = old.copy(muted = values["muted"] as? Boolean ?: old.muted,
            readAt = if ("readAt" in values) System.currentTimeMillis() else old.readAt,
            pinnedAt = when (pin) { null -> old.pinnedAt; true -> old.pinnedAt.takeIf { it > 0 } ?: System.currentTimeMillis(); false -> 0 },
            markedUnread = when { "unread" in values -> values["unread"] == true; "readAt" in values -> false; else -> old.markedUnread })
        state.value = state.value.copy(preferences = state.value.preferences + (id to next))
    }
    override suspend fun block(otherUid: String, blocked: Boolean) {}
    override suspend fun leave(id: String) {}
    override suspend fun report(id: String, reason: String) {}
}
