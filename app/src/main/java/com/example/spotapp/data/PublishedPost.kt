package com.example.spotapp.data

import com.example.spotapp.ui.theme.models.SpotUiPost
import com.example.spotapp.ui.theme.models.SpotUiProfile

data class PublishedMedia(val url: String, val contentType: String, val storagePath: String = "", val thumbnailUrl: String = "") {
    val isVideo: Boolean get() = contentType.startsWith("video/")
    val previewUrl: String get() = if (isVideo) thumbnailUrl.ifBlank { cloudinaryVideoThumbnail(url) } else url
}

data class PublishedPost(
    val id: String,
    val author: SpotUiProfile,
    val caption: String,
    val categories: List<String>,
    val tags: List<String>,
    val media: List<PublishedMedia>,
    val audience: String,
    val likes: Long = 0,
    val views: Long = 0,
    val createdAt: Long = 0,
    val location: String = "",
    val taggedPeople: List<String> = emptyList(),
    val comments: Long = 0,
    val shares: Long = 0,
    val aiCaption: Boolean = false,
    val aiTagged: Boolean = false,
    val aiCategorySuggested: Boolean = false,
    /** The caption, categories, hashtags or place changed after posting. */
    val edited: Boolean = false,
    /** Shown first on the owner's profile. */
    val pinned: Boolean = false,
    /** The owner hid the like count from everyone else. */
    val likesHidden: Boolean = false,
    val commentsOff: Boolean = false
) {
    val popularity: Long get() = popularityScore(likes, views)
    fun asUiPost(liked: Boolean = false) = SpotUiPost(
        id = id, user = author, caption = caption, category = categories.joinToString(", "),
        tags = tags, mediaUri = media.firstOrNull()?.url,
        likes = likes.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), liked = liked,
        comments = comments.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
        shares = shares.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
        aiCaption = aiCaption, aiTagged = aiTagged, aiCategorySuggested = aiCategorySuggested, postedTime = postTimeLabel(createdAt),
        likesHidden = likesHidden, edited = edited, pinned = pinned
    )
}

data class PostDraft(
    val id: String,
    val caption: String,
    val categories: List<String>,
    val tags: List<String>,
    val mediaUris: List<String>,
    val audience: String,
    val location: String = "",
    val taggedPeople: List<String> = emptyList(),
    val aiCaption: Boolean = false,
    val aiTagged: Boolean = false,
    val aiCategorySuggested: Boolean = false,
    val mediaEdits: Map<String, PostMediaEdit> = emptyMap(),
    /** A personal post shares a moment without being tied to a talent category. */
    val personal: Boolean = false
) {
    fun error(): String? = when {
        caption.isBlank() && mediaUris.isEmpty() -> "Add a photo, video or caption first."
        caption.length > 2000 -> "Keep your caption to 2,000 characters."
        mediaUris.size > 10 -> "Choose up to 10 photos or videos."
        audience !in listOf("public", "followers", "private") -> "Choose who can see this post."
        categories.isEmpty() && !personal -> "Choose a talent category, or pick Personal to post without one."
        categories.size > 8 || categories.any { it.length > 60 } -> "Choose up to 8 categories."
        tags.size > 20 || tags.any { it.length > 60 } -> "Use up to 20 hashtags, each under 60 characters."
        location.length > 160 -> "Keep your location to 160 characters."
        taggedPeople.size > 20 || taggedPeople.any { it.length > 80 } -> "Tag up to 20 people."
        else -> null
    }
}

// A like represents stronger interest than an opened post. Views are unique per account.
fun popularityScore(likes: Long, views: Long): Long = likes.coerceAtLeast(0) * 5 + views.coerceAtLeast(0)
fun audienceLabel(value: String): String = when (value) {
    "public" -> "Public"
    "followers" -> "Followers"
    else -> "Only me"
}

fun isPostVideoUrl(url: String): Boolean = "/video/upload/" in url ||
    url.substringBefore('?').substringBefore('#').substringAfterLast('.').lowercase() in listOf("mp4", "mov", "webm", "m4v", "3gp", "m3u8")

/** A Cloudinary video can deliver a JPEG poster without downloading the video into Coil. */
fun cloudinaryVideoThumbnail(url: String): String {
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return ""
    if (uri.scheme != "https" || uri.host != "res.cloudinary.com" || "/video/upload/" !in uri.path) return ""
    val path = uri.rawPath
    val file = path.substringAfterLast('/')
    val poster = path.substringBeforeLast('/') + "/" + file.substringBeforeLast('.', file) + ".jpg"
    return "https://${uri.host}" + poster.replace("/video/upload/", "/video/upload/so_0,w_960,c_limit,q_auto/")
}

fun postTimeLabel(createdAt: Long, now: Long = System.currentTimeMillis()): String {
    if (createdAt <= 0L) return "Just now"
    val minutes = ((now - createdAt).coerceAtLeast(0) / 60_000)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        minutes < 10080 -> "${minutes / 1440}d ago"
        else -> java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(createdAt))
    }
}
