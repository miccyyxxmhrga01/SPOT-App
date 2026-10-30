package com.example.spotapp.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.cloudinary.android.MediaManager
import com.example.spotapp.data.cloudinary.CloudinaryUploader
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

const val STORY_LIFETIME = 24L * 60 * 60 * 1000
data class StoryItem(val id: String, val userId: String, val mediaUrl: String, val mediaType: String,
    val createdAt: Long = 0, val audience: String = "followers", val deleted: Boolean = false,
    val username: String = "", val profileImageUrl: String = "",
    val composition: StoryComposition = StoryComposition(), val likesCount: Long = 0) {
    val video get() = mediaType == "video"
    val preview get() = if (video) cloudinaryVideoThumbnail(mediaUrl) else mediaUrl
    fun active(now: Long = System.currentTimeMillis()) = !deleted && createdAt > 0 && now < createdAt + STORY_LIFETIME
}
data class HighlightMedia(val source: String, val sourceId: String, val index: Int, val url: String, val type: String,
    val composition: StoryComposition = StoryComposition()) {
    val key get() = "$source/$sourceId/$index"
    val preview get() = if (type == "video") cloudinaryVideoThumbnail(url) else url
    fun fields(): Map<String, Any?> = mapOf("source" to source, "sourceId" to sourceId, "index" to index, "url" to url, "type" to type) +
        if (composition == StoryComposition()) emptyMap() else mapOf("composition" to composition.fields())
}
data class StoryHighlight(val id: String, val userId: String, val title: String, val media: List<HighlightMedia>, val cover: Int = 0,
    val audience: String = "public")

class StoryRepository {
    companion object { val shared by lazy { StoryRepository() } }
    private fun uid() = Supabase.requireUid()
    private val stories get() = Supabase.db.from("stories")
    private val highlights get() = Supabase.db.from("highlights")

    /** Everyone's public stories, or one owner's stories that row level security lets you see. */
    fun listenStories(owner: String? = null, result: (List<StoryItem>) -> Unit, error: (String) -> Unit): ListenerRegistration =
        Live.query(listOf(if (owner == null) Watch("stories") else Watch("stories", "user_id", owner)), {
            stories.select {
                filter { if (owner == null) eq("audience", "public") else eq("user_id", owner) }
                order("seq", Order.DESCENDING); limit(50)
            }.decodeList<StoryRow>().mapNotNull(::storyFrom)
        }, result, { error("Stories couldn't load.") })

    fun listenHighlights(owner: String, canReadFollowers: Boolean, result: (List<StoryHighlight>) -> Unit,
        error: (String) -> Unit): ListenerRegistration {
        val own = owner == Supabase.uid()
        return Live.query(listOf(Watch("highlights", "user_id", owner)), {
            highlights.select {
                filter {
                    eq("user_id", owner)
                    if (!own && !canReadFollowers) eq("audience", "public")
                }
                order("updated_at", Order.DESCENDING); limit(30)
            }.decodeList<HighlightRow>().mapNotNull { d ->
                val items = d.media.mapNotNull { fromJson(it) as? Map<*, *> }.mapNotNull { m ->
                    val url = m["url"] as? String ?: return@mapNotNull null
                    HighlightMedia(m["source"] as? String ?: "", m["sourceId"] as? String ?: "", (m["index"] as? Number)?.toInt() ?: 0, url, m["type"] as? String ?: "image", StoryComposition.from(m["composition"]))
                }
                if (items.isEmpty()) null else StoryHighlight(d.id, owner, d.title, items, d.cover.coerceIn(items.indices), d.audience)
            }
        }, result, { error("Highlights couldn't load.") })
    }

    suspend fun createStory(id: String, mediaUrl: String, video: Boolean, audience: String, composition: StoryComposition = StoryComposition()) {
        composition.validate()
        require(audience in listOf("public", "followers", "private"))
        uid()
        // Private accounts never share publicly; the server applies that and makes retries safe.
        rpc("create_story", buildJsonObject {
            put("p_id", id); put("p_media_url", mediaUrl); put("p_video", video); put("p_audience", audience)
            put("p_composition", if (composition == StoryComposition()) JsonNull else toJson(composition.fields()))
        })
        Live.changed("stories")
    }

    fun listenLiked(id: String, result: (Boolean) -> Unit, error: (String) -> Unit): ListenerRegistration {
        val me = uid()
        return Live.query(listOf(Watch("story_reactions", "user_id", me)), {
            Supabase.db.from("story_reactions").select(Columns.list("story_id")) {
                filter { eq("story_id", id); eq("user_id", me) }
            }.decodeList<JsonObject>().isNotEmpty()
        }, result, { error("Your reaction couldn't load. Retry.") })
    }

    suspend fun setLiked(id: String, liked: Boolean) {
        rpc("set_story_liked", buildJsonObject { put("p_story_id", id); put("p_liked", liked) })
        Live.changed("story_reactions", "stories")
    }

    /**
     * Replies to a story in your direct chat with its owner, starting one if needed. Like Instagram, the reply goes
     * straight in, without an invitation; the server still blocks it for blocked people or stories you can't see.
     */
    suspend fun reply(storyId: String, messageId: String, text: String) {
        rpc("reply_to_story", buildJsonObject { put("p_story_id", storyId); put("p_message_id", messageId); put("p_text", text.trim()) })
        Live.changed("messages", "conversations")
    }

    /** One story, while row level security still lets you see it; replies in chat show it. */
    suspend fun story(id: String): StoryItem? {
        awaitSession()
        return stories.select { filter { eq("id", id) } }.decodeSingleOrNull<StoryRow>()?.let(::storyFrom)
    }

    suspend fun removeStory(id: String) {
        rpc("remove_story", buildJsonObject { put("p_id", id) })
        Live.changed("stories")
    }

    suspend fun saveHighlight(id: String, title: String, media: List<HighlightMedia>, cover: Int, audience: String) {
        require(title.trim().length in 1..32) { "Add a title (up to 32 characters)." }
        require(media.size in 1..8 && media.distinctBy { it.key }.size == media.size && cover in media.indices) { "Choose 1–8 moments and a cover." }
        uid()
        rpc("save_highlight", buildJsonObject {
            put("p_id", id); put("p_title", title.trim()); put("p_media", JsonArray(media.map { toJson(it.fields()) }))
            put("p_cover", cover); put("p_audience", audience)
        })
        Live.changed("highlights")
    }

    suspend fun removeHighlight(id: String) {
        highlights.delete { filter { eq("id", id); eq("user_id", uid()) } }
        Live.changed("highlights")
    }

    suspend fun highlightSources(): List<HighlightMedia> {
        val me = uid()
        val ownStories = stories.select {
            filter { eq("user_id", me) }
            order("seq", Order.DESCENDING); limit(50)
        }.decodeList<StoryRow>()
        val posts = HomeRepository.fetchPosts {
            filter { eq("user_id", me) }
            order("seq", Order.DESCENDING); limit(30)
        }
        return ownStories.mapNotNull(::storyFrom).filterNot { it.deleted }.map { HighlightMedia("story", it.id, 0, it.mediaUrl, it.mediaType, it.composition) } +
            posts.flatMap { row -> PostDocumentMapper.published(row)?.media.orEmpty().mapIndexed { index, media ->
                HighlightMedia("post", row.id, index, media.url, if (media.isVideo) "video" else "image")
            } }
    }
}

internal fun storyFrom(d: StoryRow): StoryItem? {
    if (d.mediaUrl.isBlank() || d.userId.isBlank()) return null
    return StoryItem(d.id, d.userId, d.mediaUrl, d.mediaType, millis(d.createdAt), d.audience, d.deleted, d.username,
        d.profileImageUrl, StoryComposition.from(fromJson(d.composition)), d.likesCount.coerceAtLeast(0))
}

suspend fun uploadStoryMedia(context: Context, value: String, folder: String, maxDurationMs: Long = 60_000, progress: (Float) -> Unit): Pair<String, Boolean> {
    val uri = Uri.parse(value)
    val video = withContext(Dispatchers.IO) {
        val type = context.contentResolver.getType(uri).orEmpty()
        require(type.startsWith("image/") || type.startsWith("video/")) { "Choose a photo or video." }
        val bytes = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else -1
        } ?: -1
        require(bytes in 1..(if (type.startsWith("video/")) 100L else 10L) * 1024 * 1024) { "Choose a photo under 10 MB or video under 100 MB." }
        if (type.startsWith("video/")) {
            val length = storyVideoDuration(context, value)
            require(length in 1..maxDurationMs) { "This video is too long. Choose a shorter clip." }
            true
        } else false
    }
    // Re-encoding a video fills the first 30% of the bar; photos shrink almost instantly.
    val prepareShare = if (video) .3f else 0f
    val shrunk = PostMediaExporter.shrinkForUpload(context, value) { progress(it * prepareShare) }
    try {
        val url = suspendCancellableCoroutine { c ->
            val requestId = CloudinaryUploader.uploadMedia(shrunk?.let(Uri::fromFile) ?: uri, folder,
                onSuccess = { if (c.isActive) c.resume(it) },
                onError = { if (c.isActive) c.resumeWithException(IllegalStateException("Upload failed. Retry.")) },
                onProgress = { bytes, total -> if (total > 0) progress(prepareShare + (bytes.toFloat() / total).coerceIn(0f, 1f) * (1 - prepareShare)) })
            c.invokeOnCancellation { MediaManager.get().cancelRequest(requestId) }
        }
        return url to video
    } finally { shrunk?.delete() }
}
