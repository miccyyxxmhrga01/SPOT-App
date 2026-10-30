package com.example.spotapp.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.Assert.*
import org.junit.Test

class PostDocumentMapperTest {
    private val image = "https://res.cloudinary.com/jcuzzxsz/image/upload/v1/spot/posts/creator/photo.jpg"
    private val video = "https://res.cloudinary.com/jcuzzxsz/video/upload/v1/spot/posts/creator/video.mp4"
    private fun media(vararg items: Map<String, String>) = toJson(items.toList()) as JsonArray
    private fun row() = PostRow(id = "actual-row-id", userId = "creator", authorUsername = "sam", authorDisplayName = "Sam Cruz",
        authorProfileImageUrl = image, caption = "Campus performance", categories = listOf("Dancing"),
        media = media(mapOf("url" to image, "contentType" to "image/jpeg")), mediaType = "image",
        likesCount = 12, commentsCount = 4, createdAt = "1970-01-01T00:16:40+00:00", audience = "public")

    @Test fun rowMapsToPublishedPostWithoutStoragePath() {
        val post = requireNotNull(PostDocumentMapper.published(row()))
        assertEquals("actual-row-id", post.id)
        assertEquals("creator", post.author.uid)
        assertEquals("Sam Cruz", post.author.displayName)
        assertEquals("sam", post.author.username)
        assertEquals(image, post.author.profileImageUrl)
        assertEquals(image, post.media.single().url)
        assertEquals("", post.media.single().storagePath)
        assertEquals("Campus performance", post.caption)
        assertEquals(listOf("Dancing"), post.categories)
        assertEquals(12, post.asUiPost().likes)
        assertEquals(4, post.asUiPost().comments)
        assertEquals(1_000_000L, post.createdAt)
        assertFalse(post.asUiPost().aiCaption)
        assertEquals(image, PostDocumentMapper.spotPost(row())?.mediaUrls?.single())
    }

    @Test fun videoCaseAndCloudinaryPostersAreHandledWithoutChangingPlaybackUrl() {
        val post = requireNotNull(PostDocumentMapper.published(row().copy(media = media(mapOf("url" to video)), mediaType = "VIDEO")))
        assertTrue(post.media.single().isVideo)
        assertEquals(video, post.media.single().url)
        assertEquals("https://res.cloudinary.com/jcuzzxsz/video/upload/so_0,w_960,c_limit,q_auto/v1/spot/posts/creator/video.jpg", post.media.single().previewUrl)
        assertTrue(isPostVideoUrl(video + "?download=1"))
        assertEquals("", cloudinaryVideoThumbnail("https://example.test/video.mp4"))
    }

    @Test fun mixedAlbumAndItsMetadataArePreserved() {
        val post = requireNotNull(PostDocumentMapper.published(row().copy(
            media = media(mapOf("url" to image, "contentType" to "image/jpeg", "storagePath" to "old/path"),
                mapOf("url" to video, "contentType" to "video/mp4", "thumbnailUrl" to image)),
            audience = "followers")))
        assertEquals(2, post.media.size)
        assertEquals("old/path", post.media.first().storagePath)
        assertEquals(image, post.media.last().previewUrl)
        assertEquals("followers", post.audience)
    }

    @Test fun missingOptionalFieldsAndBadCountersDoNotBreakTheFeed() {
        val post = requireNotNull(PostDocumentMapper.published(PostRow(id = "minimal", userId = "uid", caption = "Text only", likesCount = -5)))
        assertTrue(post.media.isEmpty())
        assertEquals(0L, post.likes)
        assertEquals("Campus creator", post.author.displayName)
        assertEquals("private", post.audience)
        assertEquals(0L, post.createdAt)
        assertNull(PostDocumentMapper.published(PostRow(id = "invalid")))
        assertTrue(requireNotNull(PostDocumentMapper.published(row().copy(media = media(mapOf("url" to "javascript:alert(1)"))))).media.isEmpty())
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test fun snakeCaseColumnsDecodeIntoRows() {
        val json = Json { ignoreUnknownKeys = true; namingStrategy = JsonNamingStrategy.SnakeCase }
        val decoded = json.decodeFromString<PostRow>("""{"id":"p1","seq":7,"user_id":"u1","author_display_name":"Ana",
            "media":[{"url":"$image","contentType":"image/jpeg"}],"likes_count":3,"has_media":true,
            "created_at":"2026-09-27T10:57:58.22641+00:00"}""")
        val post = requireNotNull(PostDocumentMapper.published(decoded))
        assertEquals("Ana", post.author.displayName)
        assertEquals(image, post.media.single().url)
        assertEquals(3L, post.likes)
        assertEquals(1790506678226L, post.createdAt)
    }

    @Test fun feedSortingAndRelativeTimesUseCreatedAt() {
        val first = requireNotNull(PostDocumentMapper.published(row().copy(id = "older")))
        val second = requireNotNull(PostDocumentMapper.published(row().copy(id = "newer", createdAt = "1970-01-01T00:33:20Z")))
        assertEquals(listOf("newer", "older"), listOf(first, second).sortedByDescending { it.createdAt }.map { it.id })
        assertEquals("5m ago", postTimeLabel(1_000_000, 1_300_000))
        assertEquals("Just now", postTimeLabel(1_000_000, 900_000))
    }
}
