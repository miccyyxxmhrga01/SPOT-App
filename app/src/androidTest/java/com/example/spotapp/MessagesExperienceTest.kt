package com.example.spotapp

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.models.SpotMockData
import com.example.spotapp.ui.theme.screens.messages.MessagesScreen
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class MessagesExperienceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: TestMessagesStore
    @Before fun prepare() {
        compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        store = TestMessagesStore()
    }
    private fun show(fontScale: Float = 1f, width: Int = 360) {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                SPOTAppTheme { Box(Modifier.requiredWidth(width.dp).fillMaxHeight()) {
                    MessagesScreen(SpotMockData.mika.copy(uid = "me"), {}, {}, store = store)
                } }
            }
        }
    }
    private fun screenshot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "messages-review").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun listKeepsItsArrangementAndTabsAtLargeTextSize() {
        show(1.3f, 320)
        // The title, and the bottom bar's Messages tab, which is labelled too.
        compose.onAllNodesWithText("Messages").assertCountEquals(2).onFirst().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        compose.onNodeWithContentDescription("New conversation").assertIsDisplayed()
        // Requests are one row on Chats that opens their own page.
        compose.onNodeWithText("Alex Rivera is waiting").assertIsDisplayed()
        compose.onNodeWithTag("requests-row").performClick()
        compose.onNodeWithTag("conversation-invite").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("requests-page").assertDoesNotExist()
        compose.onNodeWithTag("messages-tab-EVENTS").performClick().assertIsSelected()
        compose.onNodeWithText("No event chats yet").assertIsDisplayed()
        compose.onNodeWithTag("messages-tab-COLLABS").performClick().assertIsSelected()
        compose.onNodeWithTag("messages-tab-GROUPS").performClick().assertIsSelected()
        compose.onNodeWithTag("messages-tab-CHATS").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Home").assertIsDisplayed()
        screenshot("messages-large-text")
    }
    @Test fun sendingPersistsThroughStoreAndSearchFindsIt() {
        show()
        compose.onNodeWithTag("conversation-chat").performClick()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithTag("send-message").assertIsNotEnabled()
        compose.onNodeWithTag("message-draft").performTextInput("Ready for rehearsal")
        compose.onNodeWithTag("send-message").performClick()
        compose.waitUntil(5000) { store.sent.size == 1 }
        compose.onNodeWithText("Ready for rehearsal").assertExists()
        compose.onNodeWithTag("message-draft").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithContentDescription("Conversation info").performClick()
        compose.onNodeWithText("Search", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("thread-search").performTextInput("rehearsal")
        compose.onNodeWithText("Ready for rehearsal").assertIsDisplayed()
        screenshot("message-thread")
    }
    @Test fun longPressUnsendsOwnMessagesAndDeletesOthersOnlyForMe() {
        show(); compose.onNodeWithTag("conversation-chat").performClick()
        compose.onNodeWithTag("message-draft").performTextInput("Oops, wrong chat")
        compose.onNodeWithTag("send-message").performClick()
        compose.waitUntil(5000) { store.sent.size == 1 }
        compose.onNodeWithTag("message-${store.sent.single().id}").performTouchInput { longClick() }
        compose.onNodeWithTag("message-options").assertIsDisplayed()
        compose.onNodeWithText("Unsend").performClick()
        compose.onNodeWithText("Unsend message?").assertIsDisplayed()
        compose.onNodeWithTag("confirm-unsend").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("You unsent a message").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Oops, wrong chat").assertDoesNotExist()
        compose.onNodeWithTag("message-first").performTouchInput { longClick() }
        compose.onNodeWithTag("message-options").assertIsDisplayed()
        compose.onNodeWithText("Unsend").assertDoesNotExist()
        compose.onNodeWithText("Delete for you").performClick()
        compose.waitUntil(5000) { "first" in store.hidden.value }
        compose.onNodeWithTag("message-first").assertDoesNotExist()
        screenshot("message-unsend-delete")
    }
    @Test fun failedSendKeepsDraftAndRetryUsesSameMessageId() {
        store.failSend = true
        show(); compose.onNodeWithTag("conversation-chat").performClick()
        compose.onNodeWithTag("message-draft").performTextInput("Keep this draft")
        compose.onNodeWithTag("send-message").performClick()
        compose.onNodeWithText("Action not completed").assertIsDisplayed()
        val first = store.attempts.single()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithTag("message-draft").assertTextEquals("Keep this draft")
        store.failSend = false
        compose.onNodeWithTag("send-message").performClick()
        compose.waitUntil(5000) { store.sent.size == 1 }
        assertEquals(first, store.attempts.last())
    }
    @Test fun invitationAcceptOpensTheCorrectConversation() {
        show(); compose.onNodeWithTag("requests-row").performClick()
        compose.onNodeWithTag("conversation-invite").performClick()
        compose.onNodeWithText("Accept invitation").performClick()
        compose.waitUntil(5000) { store.accepted == "invite" }
        compose.onNodeWithTag("message-draft").assertIsDisplayed()
        assertEquals("invite", store.accepted)
    }
    @Test fun nicknameAndMuteSavePrivatePreferences() {
        show(); compose.onNodeWithTag("conversation-chat").performClick()
        compose.onNodeWithContentDescription("Conversation info").performClick()
        compose.onAllNodesWithText("Nickname").onFirst().performClick()
        compose.onNodeWithTag("conversation-dialog-input").performTextInput("Study partner")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5000) { store.state.value.preferences["chat"]?.nickname == "Study partner" }
        compose.onNodeWithText("Mute in-app alerts").performScrollTo().performClick()
        compose.waitUntil(5000) { store.state.value.preferences["chat"]?.muted == true }
        screenshot("conversation-info")
    }
    @Test fun composingSelectsARealUidAndCreatesInvitation() {
        show(); compose.onNodeWithContentDescription("New conversation").performClick()
        compose.onNodeWithTag("people-search").performTextInput("Jamie")
        compose.waitUntil(5000) { compose.onAllNodesWithText("Jamie Santos").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Jamie Santos").performClick()
        compose.onNodeWithText("Start conversation").performClick()
        compose.waitUntil(5000) { store.createdPerson != null }
        assertEquals("jamie-uid", store.createdPerson)
    }
    @Test fun errorOffersRetryAndNoSampleChats() {
        store.state.value = MessagesInbox(loading = false, error = "Test unavailable")
        show(); compose.onNodeWithText("Messages couldn't load").assertIsDisplayed()
        store.state.value = MessagesInbox(loading = false)
        compose.onNodeWithText("Start a conversation").assertIsDisplayed()
        compose.onNodeWithTag("messages-samples").assertDoesNotExist()
    }
    @Test fun tabsCountUnreadGroupCollabAndEventChats() {
        val now = System.currentTimeMillis()
        fun community(id: String, kind: String, communityKind: String, title: String) = CampusConversation(id, title, kind = kind,
            creatorId = "friend", partyIds = listOf("me", "friend"), memberIds = listOf("me", "friend"),
            preview = "See you at practice!", lastSenderId = "friend", updatedAt = now, communityKind = communityKind)
        store.state.value = store.state.value.copy(
            conversations = store.state.value.conversations + listOf(
                community("g1", "group", "group", "Dance crew"),
                community("g2", "group", "group", "Choir"),
                community("g3", "group", "group", "Muted group"),
                community("c1", "group", "", "Mural collab"),
                community("e1", "group", "event", "Campus fair")),
            preferences = store.state.value.preferences + ("g3" to ChatPreferences(muted = true)))
        show()
        // Muted chats stay quiet, so Groups counts two of its three unread chats.
        compose.onNodeWithTag("tab-unread-GROUPS", useUnmergedTree = true).assertContentDescriptionEquals("2 unread chats")
        compose.onNodeWithTag("tab-unread-COLLABS", useUnmergedTree = true).assertContentDescriptionEquals("1 unread chat")
        compose.onNodeWithTag("tab-unread-EVENTS", useUnmergedTree = true).assertContentDescriptionEquals("1 unread chat")
        compose.onNodeWithTag("tab-unread-CHATS", useUnmergedTree = true).assertDoesNotExist()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "messages-review").apply { mkdirs() }
        File(folder, "tab-counts.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

private class TestMessagesStore : MessagesStore {
    override val uid = "me"
    private val now = System.currentTimeMillis()
    private val chat = CampusConversation("chat", "", creatorId = "me", partyIds = listOf("me", "friend"), memberIds = listOf("me", "friend"),
        people = mapOf("friend" to ChatPerson("friend", "Alex Rivera", "alex")), preview = "Let's create something great.", lastSenderId = "friend", updatedAt = now)
    private val invitation = chat.copy(id = "invite", creatorId = "friend", memberIds = listOf("friend"), invitedIds = listOf("me"), inviteNote = "Join our campus showcase.")
    val state = MutableStateFlow(MessagesInbox(listOf(chat), listOf(invitation), loading = false))
    private val thread = MutableStateFlow(listOf(CampusMessage("first", "friend", "Let's create something great.", createdAt = now)))
    val sent = mutableListOf<ChatDraft>()
    val attempts = mutableListOf<String>()
    var failSend = false
    var accepted = ""
    var createdPerson: String? = null
    override fun inbox() = state
    override fun messages(id: String) = thread
    override suspend fun olderMessages(id: String, before: CampusMessage) = emptyList<CampusMessage>()
    override suspend fun syncProfile() {}
    override suspend fun people(search: String) = listOf(ChatPerson("jamie-uid", "Jamie Santos", "jamie"))
    override suspend fun person(uid: String) = ChatPerson(uid, "Jamie Santos")
    override suspend fun create(people: List<ChatPerson>, title: String, note: String, group: Boolean, id: String): String {
        createdPerson = people.single().uid
        state.value = state.value.copy(conversations = state.value.conversations + chat.copy(id = id, memberIds = listOf(uid), invitedIds = listOf(createdPerson!!)))
        return id
    }
    override suspend fun respond(id: String, accept: Boolean) {
        accepted = if (accept) id else ""
        state.value = state.value.copy(requests = emptyList(), conversations = state.value.conversations + invitation.copy(memberIds = listOf("me", "friend"), invitedIds = emptyList()))
    }
    override suspend fun send(id: String, draft: ChatDraft) {
        attempts.add(draft.id)
        if (failSend) error("You're offline. Your draft is still here.")
        sent.add(draft)
        thread.value = thread.value + CampusMessage(draft.id, uid, draft.text, createdAt = now + sent.size)
    }
    val hidden = MutableStateFlow(emptySet<String>())
    override suspend fun unsend(id: String, messageId: String) {
        thread.value = thread.value.map { if (it.id == messageId) it.copy(text = "", attachment = null, unsent = true) else it }
    }
    override suspend fun deleteForMe(id: String, messageId: String) { hidden.value = hidden.value + messageId }
    override fun hiddenMessages(id: String) = hidden
    override suspend fun upload(context: Context, id: String, messageId: String, uri: Uri, mime: String?, progress: (Float) -> Unit) = error("No test upload")
    override suspend fun mediaFile(context: Context, attachment: ChatAttachment) = Uri.EMPTY
    override suspend fun preferences(id: String, values: Map<String, Any>) {
        val old = state.value.preferences[id] ?: ChatPreferences()
        val next = old.copy(nickname = values["nickname"] as? String ?: old.nickname, muted = values["muted"] as? Boolean ?: old.muted,
            readAt = if ("readAt" in values) now + 100 else old.readAt, hiddenAt = if ("hiddenAt" in values) now + 100 else old.hiddenAt)
        state.value = state.value.copy(preferences = state.value.preferences + (id to next))
    }
    override suspend fun block(otherUid: String, blocked: Boolean) { state.value = state.value.copy(blockedIds = if (blocked) setOf(otherUid) else emptySet()) }
    override suspend fun leave(id: String) { state.value = state.value.copy(conversations = state.value.conversations.filter { it.id != id }) }
    override suspend fun report(id: String, reason: String) {}
}
