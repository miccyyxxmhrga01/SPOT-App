package com.example.spotapp.data

/** Local, non-destructive editing instructions. These are never AI results. */
data class PostMediaEdit(val quarterTurns: Int = 0, val crop: String = "original", val muted: Boolean = false,
    val look: MediaLook = MediaLook()) {
    val rotation: Int get() = ((quarterTurns % 4) + 4) % 4
    val aspectRatio: Float? get() = when (crop) { "square" -> 1f; "portrait" -> 4f / 5f; "landscape" -> 16f / 9f; else -> null }
    val changesImage: Boolean get() = rotation != 0 || aspectRatio != null || !look.isOriginal
    /** Only rotation and crop: the editor draws the look live on top, so sliders don't re-render the photo. */
    val geometry: PostMediaEdit get() = PostMediaEdit(quarterTurns = rotation, crop = crop)
    fun rotate() = copy(quarterTurns = (rotation + 1) % 4)
}

enum class PostStep { PICKER, EDITOR, DETAILS }
enum class MediaFilter { ALL, VIDEOS, PHOTOS }
data class LibraryMedia(val uri: String, val isVideo: Boolean, val durationMs: Long = 0, val size: Long = 0,
    val id: Long = 0, val addedAt: Long = 0)
data class MediaAlbum(val id: String, val name: String)
data class LibraryPage(val items: List<LibraryMedia>, val hasMore: Boolean)

fun orderedMediaSelection(current: List<String>, uri: String): List<String> =
    if (uri in current) current - uri else if (current.size < 10) current + uri else current

fun movePostMedia(current: List<String>, index: Int, direction: Int): List<String> {
    val next = index + direction
    if (index !in current.indices || next !in current.indices) return current
    return current.toMutableList().apply { add(next, removeAt(index)) }
}
