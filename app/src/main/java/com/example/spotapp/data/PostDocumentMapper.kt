package com.example.spotapp.data

import com.example.spotapp.ui.theme.models.SpotUiProfile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Maps posts rows to app models, tolerating incomplete or imported rows. */
object PostDocumentMapper {
    internal fun published(row: PostRow): PublishedPost? {
        val uid = row.userId.trim().ifBlank { return null }
        val categories = row.categories.map(String::trim).filter(String::isNotBlank)
        val type = row.mediaType.lowercase()
        val media = row.media.mapNotNull { value ->
            val item = value as? JsonObject ?: return@mapNotNull null
            val url = item.text("url").takeIf(::isWebUrl) ?: return@mapNotNull null
            PublishedMedia(url, item.text("contentType").ifBlank { mimeType(type, url) },
                item.text("storagePath"), item.text("thumbnailUrl"))
        }
        return PublishedPost(
            id = row.id,
            author = SpotUiProfile(uid, row.authorDisplayName.trim().ifBlank { "Campus creator" },
                row.authorUsername.trim(), row.authorTalent.trim().ifBlank { categories.firstOrNull().orEmpty() },
                row.authorCourse.trim(), row.authorProfileImageUrl.trim(),
                accountType = AccountType.fromStorage(row.author?.accountType)),
            caption = row.caption.trim(), categories = categories,
            tags = row.tags.map(String::trim).filter(String::isNotBlank), media = media,
            audience = row.audience.ifBlank { "private" },
            likes = row.likesCount.coerceAtLeast(0), views = row.viewsCount.coerceAtLeast(0),
            comments = row.commentsCount.coerceAtLeast(0), shares = row.sharesCount.coerceAtLeast(0),
            createdAt = millis(row.createdAt), location = row.location.trim(), taggedPeople = row.taggedPeople,
            aiCaption = row.aiCaption, aiTagged = row.aiTagged, aiCategorySuggested = row.aiCategorySuggested,
            edited = row.editedAt != null, pinned = row.pinnedAt != null, likesHidden = row.likesHidden, commentsOff = row.commentsOff
        )
    }

    internal fun spotPost(row: PostRow): SpotPost? = published(row)?.let { post ->
        SpotPost(id = post.id, userId = post.author.uid, caption = post.caption,
            talentCategory = post.categories.joinToString(", "), tags = post.tags,
            mediaType = if (post.media.firstOrNull()?.isVideo == true) PostMediaType.VIDEO else PostMediaType.IMAGE,
            mediaUrls = post.media.map { it.url }, likesCount = post.likes, commentsCount = post.comments,
            sharesCount = post.shares, createdAt = post.createdAt,
            user = SpotUser(uid = post.author.uid, displayName = post.author.displayName,
                username = post.author.username, course = post.author.course,
                talentCategories = post.categories, profileImageUrl = post.author.profileImageUrl))
    }

    private fun JsonObject.text(key: String) = ((this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content)?.trim().orEmpty()
    private fun isWebUrl(value: String) = value.startsWith("https://") || value.startsWith("http://")
    private fun mimeType(type: String, url: String): String = if (type == "video" || type.startsWith("video/") || isPostVideoUrl(url)) "video/mp4" else "image/jpeg"
}
