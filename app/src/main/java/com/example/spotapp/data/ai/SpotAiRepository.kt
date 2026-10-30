package com.example.spotapp.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.core.net.toUri
import com.example.spotapp.data.PostMediaEdit
import com.example.spotapp.data.PostMediaExporter
import com.example.spotapp.data.SpotTalentCategories
import android.util.Base64
import com.example.spotapp.data.Supabase
import com.example.spotapp.data.awaitSession
import com.example.spotapp.data.jsonStrings
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.roundToInt

class SpotAiRepository {
    private val json = Json { ignoreUnknownKeys = true }

    /** Sends one frame of the post's media ([postFrameJpeg]) and SPOT's categories to the talent-ai Edge Function (Gemini). */
    suspend fun predictTalent(context: Context, mediaUri: String, edit: PostMediaEdit = PostMediaEdit()): TalentPredictionResponse {
        val jpeg = postFrameJpeg(context, mediaUri, edit)
        val body = buildJsonObject {
            put("image", Base64.encodeToString(jpeg, Base64.NO_WRAP))
            put("categories", jsonStrings(SpotTalentCategories))
        }
        awaitSession()
        val response = try {
            Supabase.client.functions.invoke("talent-ai") {
                setBody(TextContent(body.toString(), ContentType.Application.Json))
                timeout { requestTimeoutMillis = 45_000 }
            }
        } catch (error: UnauthorizedRestException) {
            throw IllegalStateException(serverMessage(error.error) ?: "Sign in again to use SPOT AI.", error)
        } catch (error: RestException) {
            throw IllegalStateException(serverMessage(error.error) ?: "SPOT AI isn't available right now. Choose a category yourself.", error)
        }
        val result = json.decodeFromString<TalentPredictionResponse>(response.bodyAsText())
        if (!result.success) throw IllegalStateException(result.message.ifBlank { "SPOT AI couldn't analyze this media." })
        return result
    }

    /** The function's own message for people, e.g. a limit or "isn't set up yet". */
    private fun serverMessage(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
    }.getOrNull()?.takeIf { it.isNotBlank() && it.length <= 200 }
}

// Gemini doesn't need more than this to recognize a talent or write a caption.
private const val MAX_EDGE = 512

/**
 * The post's media as a small JPEG for SPOT AI and AI Assist. Photos go through the same renderer as the
 * editor (EXIF orientation, rotation, crop); videos use a frame from the middle of the clip.
 * Everything is re-encoded, so HEIC/GIF photos and videos are accepted too.
 */
internal suspend fun postFrameJpeg(context: Context, mediaUri: String, edit: PostMediaEdit): ByteArray = withContext(Dispatchers.IO) {
    val video = context.contentResolver.getType(mediaUri.toUri())?.startsWith("video/") == true
    val bitmap = if (video) videoFrame(context, mediaUri) else PostMediaExporter.imagePreview(context, mediaUri, edit, MAX_EDGE)
    try { ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it); it.toByteArray() } }
    finally { bitmap.recycle() }
}

private fun videoFrame(context: Context, uri: String): Bitmap {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri.toUri())
        val middleUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000 / 2
        val frame = if (Build.VERSION.SDK_INT >= 27) retriever.getScaledFrameAtTime(middleUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, MAX_EDGE, MAX_EDGE)
            else retriever.getFrameAtTime(middleUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { original ->
                val scale = MAX_EDGE.toFloat() / maxOf(original.width, original.height)
                if (scale >= 1f) original
                else Bitmap.createScaledBitmap(original, (original.width * scale).roundToInt().coerceAtLeast(1), (original.height * scale).roundToInt().coerceAtLeast(1), true)
                    .also { if (it !== original) original.recycle() }
            }
        return frame ?: throw IllegalArgumentException("This video couldn't be read. Choose a category yourself.")
    } finally { retriever.release() }
}

/** Maps SPOT AI's model classes onto the composer's categories. "other" means no talent was recognized. */
fun TalentSuggestion.composerCategory(): String? = when (rawClass.lowercase()) {
    "other" -> null
    "keyboard" -> "Piano"
    else -> SpotTalentCategories.firstOrNull { it.equals(rawClass, ignoreCase = true) }
}

fun spotAiError(error: Throwable): String = when (error) {
    is HttpRequestTimeoutException -> "SPOT AI took too long to answer. Try again in a moment."
    is IOException -> "Couldn't reach SPOT AI. Check your connection and try again."
    is IllegalArgumentException, is IllegalStateException -> error.message ?: "SPOT AI couldn't analyze this media."
    else -> "SPOT AI couldn't analyze this media. Please try again."
}
