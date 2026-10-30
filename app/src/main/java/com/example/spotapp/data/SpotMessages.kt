package com.example.spotapp.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

data class ChatPerson(val uid: String, val name: String, val username: String = "", val photo: String = "")

data class ChatPreferences(
    val nickname: String = "", val muted: Boolean = false,
    val hiddenAt: Long = 0, val readAt: Long = 0,
    /** Pinned chats stay at the top of the list, most recently pinned first. */
    val pinnedAt: Long = 0,
    /** Marked unread from the chat's long-press menu; reading the chat clears it. */
    val markedUnread: Boolean = false
) {
    val pinned get() = pinnedAt > 0
}

data class CampusConversation(
    val id: String, val title: String, val kind: String = "direct",
    val creatorId: String, val partyIds: List<String>, val memberIds: List<String>,
    val invitedIds: List<String> = emptyList(), val people: Map<String, ChatPerson> = emptyMap(),
    val inviteNote: String = "", val preview: String = "", val lastSenderId: String = "",
    val updatedAt: Long = 0, val createdAt: Long = 0,
    /** "event" or "group" for the chat of an event or group, whose members are whoever joined it. */
    val communityKind: String = "", val communityId: String = ""
) {
    val isCommunity get() = communityKind.isNotBlank()
    /** Community chats are open to whoever is there; other chats need someone to have accepted. */
    val canMessage get() = isCommunity || memberIds.size >= 2
    fun nameFor(uid: String, prefs: ChatPreferences = ChatPreferences()): String =
        prefs.nickname.ifBlank { if (kind == "group") title else people[otherUid(uid)]?.name ?: "Campus connection" }
    fun otherUid(uid: String): String = partyIds.firstOrNull { it != uid }.orEmpty()
    fun unread(uid: String, prefs: ChatPreferences): Boolean =
        prefs.markedUnread || (preview.isNotBlank() && lastSenderId != uid && updatedAt > prefs.readAt)
    fun visible(prefs: ChatPreferences): Boolean = prefs.hiddenAt == 0L || updatedAt > prefs.hiddenAt
}

data class ChatAttachment(val path: String, val mime: String, val name: String, val size: Long,
    val url: String = "", val key: String = "", val iv: String = "") {
    val isVideo get() = mime.startsWith("video/")
    val encrypted get() = url.isNotBlank() && key.isNotBlank() && iv.isNotBlank()
}

/** [unsent]: the sender removed it for everyone; its text and attachment are already cleared. */
data class CampusMessage(
    val id: String, val senderId: String, val text: String = "", val attachment: ChatAttachment? = null,
    val createdAt: Long = 0, val pending: Boolean = false, val unsent: Boolean = false
) {
    fun canUnsend(uid: String) = senderId == uid && !unsent && !pending
}


data class ChatDraft(
    val id: String = UUID.randomUUID().toString(), val text: String = "", val attachment: ChatAttachment? = null
) {
    fun error(): String? = when {
        text.isBlank() && attachment == null -> "Write a message or choose a photo or video."
        text.length > 4000 -> "Keep your message under 4,000 characters."
        attachment != null && (attachment.size !in 1..25L * 1024 * 1024 ||
            !(attachment.mime.startsWith("image/") || attachment.mime.startsWith("video/"))) -> "Choose a photo or video under 25 MB."
        else -> null
    }
}

/**
 * A post shared into a chat travels as a link line at the start of the message text, followed by an optional note.
 * The link comes first so the server's 160-character inbox preview can never cut it in half.
 */
object SharedPostLink {
    private val pattern = Regex("""spot://post/([A-Za-z0-9_-]{1,100})""")
    fun message(postId: String, note: String = ""): String = listOf("spot://post/$postId", note.trim()).filter(String::isNotBlank).joinToString("\n")
    fun postId(text: String): String? = pattern.find(text)?.groupValues?.get(1)
    /** The message text without the link: the sender's note, if they wrote one. */
    fun note(text: String): String = text.replace(pattern, "").trim()
    /** What inbox rows and banners show for a message. */
    fun preview(text: String): String = if (postId(text) == null) text else note(text).ifBlank { "Shared a post" }
}

/** A reply to a story: the server writes "spot://story/<id>" and the reply on the next line. */
object StoryReplyLink {
    private val pattern = Regex("""^spot://story/([A-Za-z0-9_-]{1,100})""")
    fun storyId(text: String): String? = pattern.find(text)?.groupValues?.get(1)
    /** The reply itself, without the story link. */
    fun note(text: String): String = text.replace(pattern, "").trim()
}

/** An event invitation: the server writes "spot://event/<id>" and the Spotter's details on the next line. */
object EventInviteLink {
    private val pattern = Regex("""^spot://event/([A-Za-z0-9_-]{1,100})""")
    fun eventId(text: String): String? = pattern.find(text)?.groupValues?.get(1)
    /** The invitation details, without the event link. */
    fun note(text: String): String = text.replace(pattern, "").trim()
}

/** An event someone shared into a chat: "spot://shared-event/<id>" first, then their optional note. Not an invitation. */
object SharedEventLink {
    private val pattern = Regex("""^spot://shared-event/([A-Za-z0-9_-]{1,100})""")
    fun message(eventId: String, note: String = ""): String = listOf("spot://shared-event/$eventId", note.trim()).filter(String::isNotBlank).joinToString("\n")
    fun eventId(text: String): String? = pattern.find(text)?.groupValues?.get(1)
    /** The sender's note, without the event link. */
    fun note(text: String): String = text.replace(pattern, "").trim()
}

/** A profile someone shared into a chat: "spot://shared-profile/<uid>" first, then their optional note. */
object SharedProfileLink {
    private val pattern = Regex("""^spot://shared-profile/([A-Za-z0-9_-]{1,100})""")
    fun message(uid: String, note: String = ""): String = listOf("spot://shared-profile/$uid", note.trim()).filter(String::isNotBlank).joinToString("\n")
    fun uid(text: String): String? = pattern.find(text)?.groupValues?.get(1)
    /** The sender's note, without the profile link. */
    fun note(text: String): String = text.replace(pattern, "").trim()
}

/** What inbox rows and banners show for a message. */
fun messagePreview(text: String): String = when {
    SharedProfileLink.uid(text) != null -> SharedProfileLink.note(text).ifBlank { "Shared a profile" }
    StoryReplyLink.storyId(text) != null -> "Replied to a story: ${StoryReplyLink.note(text)}".removeSuffix(": ")
    EventInviteLink.eventId(text) != null -> "Event invitation: ${EventInviteLink.note(text)}".removeSuffix(": ")
    SharedEventLink.eventId(text) != null -> SharedEventLink.note(text).ifBlank { "Shared an event" }
    else -> SharedPostLink.preview(text)
}

data class MessagesInbox(
    val conversations: List<CampusConversation> = emptyList(),
    val requests: List<CampusConversation> = emptyList(),
    val preferences: Map<String, ChatPreferences> = emptyMap(),
    val blockedIds: Set<String> = emptySet(), val loading: Boolean = true, val error: String = ""
) {
    /** Chats with a message [uid] hasn't read yet; drives the Messages badge. */
    fun unreadCount(uid: String): Int = conversations.count { chat ->
        val prefs = preferences[chat.id] ?: ChatPreferences()
        chat.visible(prefs) && chat.unread(uid, prefs)
    }
}

/**
 * Everything besides chat invitations that waits in the Requests tab: event invitations (collaboration invitations from
 * Spotters to Creators) and job or audition applications where you're either side, and applications to perform at
 * events, yours or to your events.
 */
data class WorkInbox(val talentRequests: List<TalentRequest> = emptyList(), val eventApplications: List<EventApplication> = emptyList(),
    val loading: Boolean = true, val error: String = "")

/** Supabase is behind this boundary so interaction tests never message real accounts. */
interface MessagesStore {
    val uid: String
    fun inbox(): Flow<MessagesInbox>
    /** Interaction tests have no work requests. */
    fun workInbox(): Flow<WorkInbox> = flowOf(WorkInbox(loading = false))
    /** The events behind event chats, by id, for their date and place. Interaction tests have none. */
    suspend fun events(ids: List<String>): Map<String, CampusEvent> = emptyMap()
    fun messages(id: String): Flow<List<CampusMessage>>
    suspend fun olderMessages(id: String, before: CampusMessage): List<CampusMessage>
    suspend fun syncProfile()
    suspend fun people(search: String): List<ChatPerson>
    suspend fun person(uid: String): ChatPerson?
    suspend fun create(people: List<ChatPerson>, title: String, note: String, group: Boolean, id: String): String
    suspend fun respond(id: String, accept: Boolean)
    suspend fun send(id: String, draft: ChatDraft)
    /** Removes your own message for everyone in the conversation. */
    suspend fun unsend(id: String, messageId: String)
    /** Hides any message for you only; other members still see it. */
    suspend fun deleteForMe(id: String, messageId: String)
    fun hiddenMessages(id: String): Flow<Set<String>>
    suspend fun upload(context: Context, id: String, messageId: String, uri: Uri, mime: String?, progress: (Float) -> Unit): ChatAttachment
    suspend fun mediaFile(context: Context, attachment: ChatAttachment): Uri
    suspend fun preferences(id: String, values: Map<String, Any>)
    /** When the other person in a direct chat last read it, or 0 when that can't be shown (supabase chat_seen). */
    suspend fun seenAt(id: String): Long = 0
    suspend fun block(otherUid: String, blocked: Boolean)
    suspend fun leave(id: String)
    suspend fun report(id: String, reason: String)
}
