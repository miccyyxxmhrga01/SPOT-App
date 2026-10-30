package com.example.spotapp.data

import com.cloudinary.android.MediaManager
import com.example.spotapp.data.cloudinary.CloudinaryUploader
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object SpotPostsRepository {

    fun listenPopular(onResult: (List<PublishedPost>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        HomeRepository.listenPopular(onResult, onError)

    fun listenRecent(onResult: (List<PublishedPost>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        HomeRepository.listenRecent(onResult, onError)

    fun listenOwn(onResult: (List<PublishedPost>) -> Unit, onError: (String) -> Unit = {}): ListenerRegistration? =
        Supabase.uid()?.let { HomeRepository.listenProfilePosts(it, onResult, onError) }

    fun listenInteraction(id: String, onLiked: (Boolean) -> Unit): ListenerRegistration? {
        val uid = Supabase.uid() ?: return null
        return Live.query(listOf(Watch("post_interactions", "user_id", uid)), {
            Supabase.db.from("post_interactions").select(Columns.list("liked")) {
                filter { eq("post_id", id); eq("user_id", uid) }
            }.decodeList<LikedRow>().firstOrNull()?.liked == true
        }, onLiked, {})
    }

    suspend fun publish(context: Context, draft: PostDraft, progress: (Float) -> Unit): PublishedPost {
        draft.error()?.let { throw IllegalArgumentException(it) }
        val uid = Supabase.uid() ?: throw IllegalStateException("Sign in before posting.")
        // A retry after a lost acknowledgement returns the existing post, not a duplicate.
        HomeRepository.fetchPosts { filter { eq("id", draft.id) } }.firstOrNull()?.let { existing ->
            return (PostDocumentMapper.published(existing) ?: throw IllegalStateException("Could not restore your post.")).also { HomeRepository.cachePublished(it) }
        }
        val author = ProfileRepository.shared.row(uid)?.takeIf { it.profileCompleted }
            ?: throw IllegalStateException("Finish your profile before posting.")
        // Preparing fills the first part of the bar; re-encoding videos takes long enough to need a real share of it.
        val count = draft.mediaUris.size.coerceAtLeast(1)
        val prepareShare = withContext(Dispatchers.IO) {
            if (draft.mediaUris.any { context.contentResolver.getType(Uri.parse(it))?.startsWith("video/") == true }) .3f else .05f
        }
        val uploads = withContext(Dispatchers.IO) {
            draft.mediaUris.mapIndexed { index, value ->
                val uri = Uri.parse(value)
                val type = context.contentResolver.getType(uri).orEmpty()
                require(type.startsWith("image/") || type.startsWith("video/")) { "Choose a supported photo or video." }
                val size = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                    if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else -1L
                } ?: -1L
                require(size in 1..50L * 1024 * 1024) { "Each photo or video must be under 50 MB." }
                val exported = PostMediaExporter.export(context, uid, draft.id, value, draft.mediaEdits[value] ?: PostMediaEdit()) { done ->
                    progress(prepareShare * (index + done) / count)
                }
                val editedUri = Uri.parse(exported)
                val editedType = context.contentResolver.getType(editedUri).orEmpty()
                val editedSize = if (exported == value) size else context.contentResolver.query(editedUri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                    if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else -1L
                } ?: -1L
                require(editedSize in 1..50L * 1024 * 1024) { "The edited file must be under 50 MB. Try a smaller file." }
                progress(prepareShare * (index + 1) / count)
                Triple(editedUri, editedType, editedSize)
            }
        }
        val totalBytes = uploads.sumOf { it.third }.coerceAtLeast(1)
        var uploadedBytes = 0L
        val media = mutableListOf<Map<String, String>>()
        for (upload in uploads) {
            check(Supabase.uid() == uid) { "Your account changed. Sign in again before posting." }
            val (uri, type, size) = upload
            val url = suspendCancellableCoroutine<String> { continuation ->
                val requestId = CloudinaryUploader.uploadMedia(uri, "spot/posts/$uid/${draft.id}",
                    onSuccess = { if (continuation.isActive) continuation.resume(it) },
                    onError = { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Media upload failed. Please retry.")) },
                    onProgress = { bytes, total ->
                        val fraction = if (total > 0) bytes.toFloat() / total else 0f
                        progress((prepareShare + (uploadedBytes + size * fraction) / totalBytes * (.95f - prepareShare)).coerceIn(prepareShare, .95f))
                    })
                continuation.invokeOnCancellation { MediaManager.get().cancelRequest(requestId) }
            }
            uploadedBytes += size
            media.add(mapOf("url" to url, "contentType" to type, "storagePath" to "",
                "thumbnailUrl" to if (type.startsWith("video/")) cloudinaryVideoThumbnail(url) else ""))
        }
        check(Supabase.uid() == uid) { "Your account changed. Sign in again before posting." }
        // The server snapshots the author, keeps private accounts off the public feed,
        // and returns the existing post if this id was already published.
        // Uploaded files are kept on an ambiguous failure: the post may already exist.
        val row = rpc("publish_post", buildJsonObject {
            put("p_id", draft.id); put("p_caption", draft.caption.trim())
            put("p_categories", jsonStrings(draft.categories))
            put("p_tags", jsonStrings(draft.tags.map { it.removePrefix("#") }.distinct()))
            put("p_media", JsonArray(media.map(::toJson))); put("p_audience", draft.audience)
            put("p_location", draft.location); put("p_tagged_people", jsonStrings(draft.taggedPeople))
            put("p_ai_caption", draft.aiCaption); put("p_ai_tagged", draft.aiTagged)
            put("p_ai_category_suggested", draft.aiCategorySuggested)
        }).decodeAs<PostRow>().copy(author = PostAuthorRow(author.accountType))
        progress(1f)
        val published = PostDocumentMapper.published(row) ?: throw IllegalStateException("Could not load your published post.")
        HomeRepository.cachePublished(published)
        Live.changed("posts")
        return published
    }

    suspend fun setLiked(id: String, liked: Boolean) = updateInteraction(id, liked, false)
    suspend fun recordView(id: String) = updateInteraction(id, null, true)

    // Counters, the unique view and the like notification change together on the server.
    private suspend fun updateInteraction(id: String, liked: Boolean?, viewed: Boolean) {
        Supabase.uid() ?: return
        rpc("set_post_interaction", buildJsonObject { put("p_post_id", id); put("p_liked", liked); put("p_viewed", viewed) })
        if (liked != null) Live.changed("post_interactions")
    }

    /** Saves the owner's changes to a post's words and audience; its photos and videos stay as published. */
    suspend fun update(id: String, caption: String, categories: List<String>, tags: List<String>, location: String, audience: String) {
        rpc("update_post", buildJsonObject {
            put("p_id", id); put("p_caption", caption.trim()); put("p_categories", jsonStrings(categories))
            put("p_tags", jsonStrings(tags.map { it.trim().removePrefix("#") }.filter(String::isNotBlank).distinct()))
            put("p_location", location.trim()); put("p_audience", audience)
        })
        Live.changed("posts")
    }

    /** Pins to the owner's profile (up to 3), hides the like count from others, or turns comments off; null leaves one as it is. */
    suspend fun setOptions(id: String, pinned: Boolean? = null, likesHidden: Boolean? = null, commentsOff: Boolean? = null) {
        rpc("set_post_options", buildJsonObject {
            put("p_id", id); put("p_pinned", pinned); put("p_likes_hidden", likesHidden); put("p_comments_off", commentsOff)
        })
        Live.changed("posts")
    }

    /** Deletes the owner's post for everyone, with its comments, likes, saves and notifications. */
    suspend fun delete(id: String) {
        rpc("delete_post", buildJsonObject { put("p_id", id) })
        HomeRepository.forget(id)
        Live.changed("posts", "saved_posts", "notifications")
    }

    /** Files a report for SPOT's team to review; reporting the same post again replaces the reason. */
    suspend fun report(id: String, reason: String, details: String) {
        require(PostReportReasons.any { it.first == reason }) { "Choose why you're reporting this post." }
        Supabase.requireUid()
        rpc("report_post", buildJsonObject { put("p_post_id", id); put("p_reason", reason); put("p_details", details.trim()) })
    }

    fun publishError(error: Exception): String = when (error) {
        is IllegalArgumentException, is IllegalStateException -> error.message ?: "Check your post and retry."
        else -> "Couldn't publish your post. Check your connection and retry."
    }
}

/** Why a post is reported, as stored (report_post checks the same list) and as shown. */
val PostReportReasons = listOf(
    "spam" to "Spam or scam",
    "nudity" to "Nudity or sexual content",
    "harassment" to "Bullying or harassment",
    "hate" to "Hate speech",
    "violence" to "Violence or dangerous acts",
    "false_info" to "False information",
    "stolen" to "Someone else's work",
    "other" to "Something else"
)
