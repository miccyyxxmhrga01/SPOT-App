package com.example.spotapp.data

import android.content.Context
import com.example.spotapp.data.cloudinary.uploadCommunityImage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Files feedback and bug reports in SPOT's database, where the team reads them, so sending never depends on the
 * member having an email app set up. [FeedbackEmail] stays available as a way to email the team instead.
 */
object FeedbackReports {
    /**
     * Sends one report. [id] is generated once per report, so retrying after a dropped connection can't file it twice.
     * A [screenshot] (a content URI) is uploaded to Cloudinary first.
     */
    suspend fun submit(context: Context, id: String, bug: Boolean, message: String, screenshot: String?, version: String,
        device: String, progress: (Float) -> Unit = {}) {
        val uid = Supabase.uid() ?: throw IllegalStateException("Sign in to send feedback.")
        FeedbackEmail.error(message)?.let { throw IllegalStateException(it) }
        val shot = screenshot?.let { uploadCommunityImage(context, it, "spot/feedback/$uid/$id", progress) }
        rpc("submit_feedback", buildJsonObject {
            put("p_id", id)
            put("p_kind", if (bug) "bug" else "feedback")
            put("p_message", message.trim())
            put("p_screenshot_url", shot?.takeIf { it.isNotBlank() })
            put("p_app_version", version)
            put("p_device", device)
        })
    }
}
