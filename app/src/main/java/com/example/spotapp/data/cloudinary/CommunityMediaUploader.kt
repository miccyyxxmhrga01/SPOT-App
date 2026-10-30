package com.example.spotapp.data.cloudinary

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.cloudinary.android.MediaManager
import com.example.spotapp.data.PostMediaExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reuses SPOT's unsigned Cloudinary uploader. No Firebase Storage or AI dependency. */
suspend fun uploadCommunityImage(context: Context, value: String, folder: String, progress: (Float) -> Unit): String {
    if (value.isBlank()) return ""
    val uri = Uri.parse(value)
    withContext(Dispatchers.IO) {
        require(context.contentResolver.getType(uri)?.startsWith("image/") == true) { "Choose an image." }
        val size = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else -1L
        } ?: -1L
        require(size in 1..10L * 1024 * 1024) { "Choose an image under 10 MB." }
    }
    val shrunk = PostMediaExporter.shrinkForUpload(context, value)
    try {
        return suspendCancellableCoroutine { c ->
            val requestId = CloudinaryUploader.uploadMedia(shrunk?.let(Uri::fromFile) ?: uri, folder,
                onSuccess = { if (c.isActive) c.resume(it) },
                onError = { if (c.isActive) c.resumeWithException(IllegalStateException("Image upload failed. Please retry.")) },
                onProgress = { bytes, total -> if (total > 0) progress((bytes.toFloat() / total * .9f).coerceIn(0f, .9f)) })
            c.invokeOnCancellation { MediaManager.get().cancelRequest(requestId) }
        }
    } finally { shrunk?.delete() }
}
