package com.example.spotapp.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Reads only the media the OS currently permits, on an IO dispatcher. No uploads occur here. */
class MediaLibraryRepository(private val context: Context) {
    fun permissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    fun hasAccess() = permissions().any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    fun hasFullAccess() = if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } else hasAccess()
    private val base get() = MediaStore.Files.getContentUri("external")

    suspend fun albums(): List<MediaAlbum> = withContext(Dispatchers.IO) {
        val result = linkedMapOf<String, String>()
        context.contentResolver.query(base, arrayOf(MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?,?)", arrayOf("1", "3"), null)?.use { c ->
            while (c.moveToNext()) { ensureActive(); val id = c.getString(0).orEmpty(); if (id.isNotEmpty()) result[id] = c.getString(1).orEmpty().ifBlank { "Album" } }
        }
        result.map { MediaAlbum(it.key, it.value) }.sortedBy { it.name.lowercase() }
    }
    suspend fun page(filter: MediaFilter, album: String, after: LibraryMedia?): LibraryPage = withContext(Dispatchers.IO) {
        val args = mutableListOf<String>()
        val clauses = mutableListOf<String>()
        clauses += "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${if (filter == MediaFilter.ALL) "?,?" else "?"})"
        args += when (filter) { MediaFilter.ALL -> listOf("1", "3"); MediaFilter.PHOTOS -> listOf("1"); MediaFilter.VIDEOS -> listOf("3") }
        if (album.isNotBlank()) { clauses += "${MediaStore.Images.Media.BUCKET_ID} = ?"; args += album }
        if (after != null) {
            clauses += "(${MediaStore.MediaColumns.DATE_ADDED} < ? OR (${MediaStore.MediaColumns.DATE_ADDED} = ? AND ${MediaStore.MediaColumns._ID} < ?))"
            args += listOf(after.addedAt.toString(), after.addedAt.toString(), after.id.toString())
        }
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Video.Media.DURATION, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED)
        val sort = "${MediaStore.MediaColumns.DATE_ADDED} DESC, ${MediaStore.MediaColumns._ID} DESC"
        val selection = clauses.joinToString(" AND ")
        val resolver = context.contentResolver
        val cursor = if (Build.VERSION.SDK_INT >= 26) resolver.query(base, projection, Bundle().apply {
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args.toTypedArray())
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sort)
            putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 91)
        }, null) else resolver.query(base, projection, selection, args.toTypedArray(), sort)
        val items = mutableListOf<LibraryMedia>()
        cursor?.use { c ->
            while (items.size < 91 && c.moveToNext()) {
                ensureActive()
                val id = c.getLong(0); val video = c.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                items += LibraryMedia(ContentUris.withAppendedId(collection, id).toString(), video, c.getLong(2), c.getLong(3), id, c.getLong(4))
            }
        }
        LibraryPage(items.take(90), items.size > 90)
    }
    suspend fun describe(value: String): LibraryMedia = withContext(Dispatchers.IO) {
        val uri = value.toUri(); val mime = context.contentResolver.getType(uri).orEmpty()
        require(mime.startsWith("image/") || mime.startsWith("video/")) { "This media is unavailable. Choose it again." }
        val size = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0 } ?: 0
        LibraryMedia(value, mime.startsWith("video/"), size = size)
    }
}
