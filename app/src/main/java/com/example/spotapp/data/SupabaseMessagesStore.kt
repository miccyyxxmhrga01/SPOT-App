package com.example.spotapp.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.cloudinary.android.MediaManager
import com.example.spotapp.data.cloudinary.CloudinaryUploader
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun validChatMediaUrl(url: String, path: String): Boolean {
    if (!path.matches(Regex("chat_media/[A-Za-z0-9_-]{1,100}/[A-Za-z0-9_-]{1,128}/[A-Za-z0-9_-]{1,100}"))) return false
    val parsed = Uri.parse(url)
    return parsed.scheme == "https" && parsed.host == "res.cloudinary.com" && parsed.port == -1 &&
        parsed.query == null && parsed.fragment == null &&
        // Cloudinary names the file; it may or may not keep an extension. The folder is what ties it to this message.
        parsed.path.orEmpty().matches(Regex("/jcuzzxsz/raw/upload/v[0-9]+/spot/${Regex.escape(path)}/[A-Za-z0-9_-]+(?:[.][A-Za-z0-9]{1,8})?"))
}

/** Decrypted chat photos and videos for [uid], kept in the cache so a chat opens them without downloading again. */
internal fun chatMediaFolder(context: Context, uid: String) = File(context.cacheDir, "chat-media/$uid")

class SupabaseMessagesStore(override val uid: String) : MessagesStore {
    private fun table(name: String) = Supabase.db.from(name)
    private fun checkAccount() { check(Supabase.uid() == uid) { "Sign in again to use Messages." } }

    override fun inbox(): Flow<MessagesInbox> = combine(
        conversations("member_ids", "updated_at"), conversations("invited_ids", "created_at"),
        Live.flow(listOf(Watch("chat_settings", "user_id", uid))) {
            table("chat_settings").select { filter { eq("user_id", uid) } }.decodeList<ChatSettingsRow>()
        },
        Live.flow(listOf(Watch("blocks", "blocker_id", uid))) {
            table("blocks").select(Columns.list("blocked_id")) { filter { eq("blocker_id", uid) } }.decodeList<BlockedIdRow>()
        }
    ) { joined, requests, prefs, blocked ->
        MessagesInbox(joined.mapNotNull(::parseConversation), requests.mapNotNull(::parseConversation),
            prefs.associate { it.conversationId to ChatPreferences(it.nickname, it.muted, millis(it.hiddenAt), millis(it.readAt),
                millis(it.pinnedAt), it.markedUnread) },
            blocked.map { it.blockedId }.toSet(), loading = false)
    }.catch { emit(MessagesInbox(loading = false, error = messagesError(it))) }

    // Row level security limits both lists to requests you're part of and applications to your own events.
    override fun workInbox(): Flow<WorkInbox> = combine(
        Live.flow(listOf(Watch("talent_requests", "seeker_id", uid), Watch("talent_requests", "creator_id", uid))) {
            table("talent_requests").select {
                filter { or { eq("seeker_id", uid); eq("creator_id", uid) } }
                order("seq", Order.DESCENDING); limit(50)
            }.decodeList<TalentRequestRow>()
        },
        Live.flow(listOf(Watch("event_applications"))) {
            table("event_applications").select(Columns.raw("*, event:events(title, starts_at, organizer_id)")) {
                order("seq", Order.DESCENDING); limit(50)
            }.decodeList<EventApplicationRow>()
        }
    ) { requests, applications ->
        WorkInbox(requests.map { it.request() }, applications.map { it.application() }, loading = false)
    }.catch { emit(WorkInbox(loading = false, error = "Requests couldn't load. Check your connection and retry.")) }

    override suspend fun events(ids: List<String>): Map<String, CampusEvent> = ids.distinct().chunked(100).flatMap { group ->
        table("events").select { filter { isIn("id", group) } }.decodeList<EventRow>().map { it.event() }
    }.associateBy { it.eventId }

    // Membership lives in arrays, which Realtime can't filter; row level security limits events to your chats.
    private fun conversations(people: String, orderBy: String) = Live.flow(listOf(Watch("conversations"))) {
        table("conversations").select {
            filter { contains(people, listOf(uid)) }
            order(orderBy, Order.DESCENDING); limit(100)
        }.decodeList<ConversationRow>()
    }

    override fun messages(id: String): Flow<List<CampusMessage>> =
        Live.flow(listOf(Watch("messages", "conversation_id", id))) {
            table("messages").select {
                filter { eq("conversation_id", id) }
                order("seq", Order.DESCENDING); limit(50)
            }.decodeList<MessageRow>().map(::parseMessage).reversed()
        }

    override suspend fun olderMessages(id: String, before: CampusMessage): List<CampusMessage> =
        rpc("older_messages", buildJsonObject { put("p_conversation_id", id); put("p_before_id", before.id) })
            .decodeList<MessageRow>().map(::parseMessage).reversed()

    // Profiles are a single public table now, so there is no separate directory copy to refresh.
    override suspend fun syncProfile() { checkAccount() }

    override suspend fun people(search: String): List<ChatPerson> {
        checkAccount()
        val key = search.trim().lowercase().removePrefix("@")
        val profiles = ProfileRepository.shared
        val rows = if (key.isBlank()) profiles.searchProfiles("", byUsername = false)
        else coroutineScope {
            listOf(false, true).map { byUsername -> async { profiles.searchProfiles(key, byUsername, limit = 20) } }.awaitAll().flatten()
        }
        return rows.distinctBy { it.id }.filter { it.id != uid }.map { it.toPerson() }
    }
    override suspend fun person(uid: String): ChatPerson? = table("profiles").select {
        filter { eq("id", uid); eq("profile_completed", true) }
    }.decodeSingleOrNull<ProfileRow>()?.toPerson()

    override suspend fun create(people: List<ChatPerson>, title: String, note: String, group: Boolean, id: String): String {
        checkAccount()
        require(people.size in 1..7 && people.map { it.uid }.distinct().size == people.size && people.none { it.uid == uid }) { "Choose one to seven campus connections." }
        require(group || people.size == 1) { "Choose one person for a direct chat." }
        require(!group || title.trim().length in 1..60) { "Give your collaboration a name (up to 60 characters)." }
        require(note.length <= 500) { "Keep the invitation under 500 characters." }
        rpc("create_conversation", buildJsonObject {
            put("p_id", id); put("p_people", jsonStrings(people.map { it.uid })); put("p_title", title.trim())
            put("p_note", note.trim()); put("p_group", group)
        })
        Live.changed("conversations")
        return id
    }

    override suspend fun respond(id: String, accept: Boolean) {
        checkAccount()
        rpc("respond_conversation", buildJsonObject { put("p_id", id); put("p_accept", accept) })
        Live.changed("conversations")
    }

    // A retry after a lost acknowledgement reuses the draft id, so it never duplicates.
    override suspend fun send(id: String, draft: ChatDraft) {
        checkAccount()
        draft.error()?.let { throw IllegalArgumentException(it) }
        val attachment = draft.attachment?.let {
            toJson(mapOf("path" to it.path, "mime" to it.mime, "name" to it.name, "size" to it.size) +
                if (it.encrypted) mapOf("url" to it.url, "key" to it.key, "iv" to it.iv) else emptyMap())
        }
        rpc("send_message", buildJsonObject {
            put("p_conversation_id", id); put("p_message_id", draft.id); put("p_text", draft.text.trim())
            if (attachment != null) put("p_attachment", attachment)
        })
        Live.changed("messages", "conversations")
    }

    // Clearing the attachment also drops its decryption key, so the stored file can't be opened again.
    override suspend fun unsend(id: String, messageId: String) {
        checkAccount()
        rpc("unsend_message", buildJsonObject { put("p_conversation_id", id); put("p_message_id", messageId) })
        Live.changed("messages", "conversations")
    }

    override suspend fun deleteForMe(id: String, messageId: String) {
        checkAccount()
        table("hidden_messages").upsert(buildJsonObject {
            put("user_id", uid); put("conversation_id", id); put("message_id", messageId)
        }) { onConflict = "user_id,conversation_id,message_id"; ignoreDuplicates = true }
        Live.changed("hidden_messages")
    }

    override fun hiddenMessages(id: String): Flow<Set<String>> =
        Live.flow(listOf(Watch("hidden_messages", "user_id", uid))) {
            table("hidden_messages").select(Columns.list("message_id")) {
                filter { eq("user_id", uid); eq("conversation_id", id) }
            }.decodeList<MessageIdRow>().map { it.messageId }.toSet()
        }

    override suspend fun upload(context: Context, id: String, messageId: String, uri: Uri, mime: String?, progress: (Float) -> Unit): ChatAttachment {
        checkAccount()
        val type = mime?.takeIf { it.isNotBlank() } ?: context.contentResolver.getType(uri).orEmpty()
        val source = withContext(Dispatchers.IO) {
            var name = "Photo"
            var size = -1L
            if (uri.scheme != "file") context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) { name = it.getString(0).orEmpty(); if (!it.isNull(1)) size = it.getLong(1) }
            }
            if (uri.scheme == "file") size = File(uri.path.orEmpty()).length()
            ChatAttachment("chat_media/$id/$uid/$messageId", type, name.take(120), size)
        }
        require(type.startsWith("image/") || type.startsWith("video/")) { "Choose a photo or video." }
        if (source.size > ChatMediaCrypto.MAX_BYTES) throw IllegalArgumentException("Choose a photo or video under 25 MB.")
        // Chat media is encrypted before it reaches the app's Cloudinary upload preset:
        // the public URL alone cannot reveal the photo. Cloudinary refuses ".bin" uploads, so the ciphertext is ".enc".
        val encrypted = File.createTempFile("chat-media-", ".enc", context.cacheDir)
        try {
            val (secret, actualSize) = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    encrypted.outputStream().use { output -> ChatMediaCrypto.encrypt(input, output, source.path) }
                } ?: throw IllegalArgumentException("Couldn't read that photo or video. Choose it again.")
            }
            val url = suspendCancellableCoroutine<String> { continuation ->
                val requestId = CloudinaryUploader.uploadMedia(Uri.fromFile(encrypted),
                    "spot/chat_media/$id/$uid/$messageId",
                    onSuccess = { if (continuation.isActive) continuation.resume(it) },
                    onError = { reason ->
                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException(
                            "Photo or video upload failed. " + reason.take(180).ifBlank { "Please retry." }))
                    },
                    onProgress = { done, total -> if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f)) },
                    resourceType = "raw")
                continuation.invokeOnCancellation { MediaManager.get().cancelRequest(requestId) }
            }
            require(validChatMediaUrl(url, source.path)) { "The media service returned an invalid attachment. Retry." }
            return source.copy(size = actualSize, url = url, key = secret.key, iv = secret.iv)
        } finally { encrypted.delete() }
    }

    override suspend fun mediaFile(context: Context, attachment: ChatAttachment): Uri = withContext(Dispatchers.IO) {
        checkAccount()
        // Unencrypted attachments were stored in the retired Firebase Storage bucket.
        check(attachment.encrypted) { "This attachment is no longer available." }
        val key = MessageDigest.getInstance("SHA-256").digest((attachment.path + attachment.url + attachment.key).toByteArray())
            .joinToString("") { "%02x".format(it) }
        val folder = chatMediaFolder(context, uid).apply { mkdirs() }
        val file = File(folder, key + if (attachment.isVideo) ".mp4" else ".jpg")
        if (!file.exists() || file.length() != attachment.size) {
            val temp = File.createTempFile(key, ".part", folder)
            try {
                require(validChatMediaUrl(attachment.url, attachment.path)) { "This attachment address is invalid." }
                val connection = (URL(attachment.url).openConnection() as HttpsURLConnection).apply {
                    connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = false
                }
                try {
                    require(connection.responseCode == 200) { "Couldn't download this attachment. Retry." }
                    require(connection.contentLengthLong in -1..(attachment.size + 16)) { "This attachment is too large." }
                    connection.inputStream.use { input -> temp.outputStream().use { output ->
                        ChatMediaCrypto.decrypt(input, output, attachment.path,
                            ChatMediaCrypto.Secret(attachment.key, attachment.iv), attachment.size)
                    } }
                } finally { connection.disconnect() }
                checkAccount()
                check(temp.renameTo(file)) { "Couldn't open this attachment. Please retry." }
            } finally { temp.delete() }
            folder.listFiles()?.filter { it != file && System.currentTimeMillis() - it.lastModified() > 7 * 86400000L }?.forEach { it.delete() }
        }
        Uri.fromFile(file)
    }

    // readAt and hiddenAt use the server's clock, like the conversation's updated time.
    override suspend fun preferences(id: String, values: Map<String, Any>) {
        checkAccount()
        rpc("set_chat_preferences", buildJsonObject { put("p_conversation_id", id); put("p_values", toJson(values)) })
        Live.changed("chat_settings")
    }
    override suspend fun seenAt(id: String): Long {
        checkAccount()
        // A bare JSON value: a quoted timestamp, or null.
        val value = rpc("chat_seen", buildJsonObject { put("p_conversation_id", id) }).data.trim().removeSurrounding("\"")
        return if (value.isEmpty() || value == "null") 0 else millis(value)
    }
    override suspend fun block(otherUid: String, blocked: Boolean) {
        checkAccount()
        require(otherUid.isNotBlank() && otherUid != uid)
        if (blocked) table("blocks").upsert(buildJsonObject { put("blocker_id", uid); put("blocked_id", otherUid) }) {
            onConflict = "blocker_id,blocked_id"; ignoreDuplicates = true
        }
        else table("blocks").delete { filter { eq("blocker_id", uid); eq("blocked_id", otherUid) } }
        // Blocks also hide posts, stories, highlights and comments both ways, and end follows.
        Live.changed("blocks", "follows", "posts", "stories", "highlights", "comments")
    }
    override suspend fun leave(id: String) {
        checkAccount()
        rpc("leave_conversation", buildJsonObject { put("p_id", id) })
        Live.changed("conversations")
    }
    override suspend fun report(id: String, reason: String) {
        checkAccount()
        require(reason.trim().length in 5..1000) { "Describe the issue in 5–1,000 characters." }
        table("conversation_reports").upsert(buildJsonObject {
            put("conversation_id", id); put("reporter_id", uid); put("reason", reason.trim())
        }) { onConflict = "conversation_id,reporter_id" }
    }

    private fun parseConversation(row: ConversationRow): CampusConversation? {
        // Sample chats from the retired demo feature stay out of the inbox.
        if (row.creatorId.isBlank() || row.demo) return null
        val people = row.people.mapNotNull { (id, value) ->
            val data = value as? JsonObject ?: return@mapNotNull null
            fun text(key: String) = (data[key] as? JsonPrimitive)?.content
            id to ChatPerson(id, text("displayName") ?: "Campus connection", text("username").orEmpty(), text("profileImageUrl").orEmpty())
        }.toMap()
        return CampusConversation(row.id, row.title, row.kind, row.creatorId, row.partyIds, row.memberIds,
            row.invitedIds, people, row.inviteNote, row.preview, row.lastSenderId, millis(row.updatedAt), millis(row.createdAt),
            row.communityKind.orEmpty(), row.communityId.orEmpty())
    }
    private fun parseMessage(row: MessageRow): CampusMessage {
        val media = fromJson(row.attachment) as? Map<*, *>
        return CampusMessage(row.id, row.senderId, row.text,
            media?.let { ChatAttachment(it["path"] as? String ?: "", it["mime"] as? String ?: "", it["name"] as? String ?: "",
                (it["size"] as? Number)?.toLong() ?: 0, it["url"] as? String ?: "", it["key"] as? String ?: "", it["iv"] as? String ?: "") },
            millis(row.createdAt), unsent = row.unsentAt != null)
    }
}

fun messagesError(error: Throwable): String = when (error) {
    is IllegalArgumentException, is IllegalStateException -> error.message ?: "Please check your message and retry."
    is HttpRequestTimeoutException -> "Messages took too long to respond. Please retry; your draft is still here."
    is IOException -> "You're offline. Reconnect and retry; your draft is still here."
    is RestException -> "Messages couldn't access this conversation. Check that your invitation was accepted and try again."
    else -> "Something interrupted Messages. Please retry."
}
