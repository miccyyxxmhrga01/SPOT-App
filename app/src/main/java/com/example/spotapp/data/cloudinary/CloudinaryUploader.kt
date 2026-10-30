package com.example.spotapp.data.cloudinary

import android.net.Uri
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback

object CloudinaryUploader {

    private const val UPLOAD_PRESET = "spot_uploads"

    fun uploadMedia(
        uri: Uri,
        folder: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        resourceType: String = "auto"
    ): String {
        return MediaManager.get()
            .upload(uri)
            .unsigned(UPLOAD_PRESET)
            .option("folder", folder)
            .option("resource_type", resourceType)
            .callback(object : UploadCallback {

                override fun onStart(requestId: String) {
                    // Upload started
                }

                override fun onProgress(
                    requestId: String,
                    bytes: Long,
                    totalBytes: Long
                ) {
                    onProgress(bytes, totalBytes)
                }

                override fun onSuccess(
                    requestId: String,
                    resultData: Map<*, *>
                ) {
                    val url = resultData["secure_url"] as? String

                    if (url != null) {
                        onSuccess(url)
                    } else {
                        onError("Upload succeeded but no URL was returned.")
                    }
                }

                override fun onError(
                    requestId: String,
                    error: ErrorInfo
                ) {
                    onError(error.description)
                }

                override fun onReschedule(
                    requestId: String,
                    error: ErrorInfo
                ) {
                    // Cloudinary can retry recoverable uploads automatically.
                }
            })
            .dispatch()
    }
}
