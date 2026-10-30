package com.example.spotapp.data.ai

import android.content.Context
import android.util.Base64
import com.example.spotapp.data.PostMediaEdit
import com.example.spotapp.data.Supabase
import com.example.spotapp.data.awaitSession
import com.example.spotapp.data.jsonStrings
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import io.github.jan.supabase.functions.functions
import io.ktor.client.plugins.timeout
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Caption ideas and hashtags Gemini wrote for a post. Nothing is applied until the member chooses one. */
@Serializable
data class PostAiSuggestions(val captions: List<String> = emptyList(), val tags: List<String> = emptyList())

/**
 * AI Assist in the post composer: sends a small JPEG of the post's first photo, or a frame of its video, to the
 * post-ai Edge Function, which asks Google Gemini for caption ideas and hashtags.
 */
object PostAiAssist {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun suggest(context: Context, mediaUri: String, edit: PostMediaEdit, categories: List<String>,
        caption: String, personal: Boolean): PostAiSuggestions {
        val jpeg = try { postFrameJpeg(context, mediaUri, edit) }
        catch (_: IllegalArgumentException) { throw IllegalStateException("AI Assist couldn't read this media. Try another photo or video.") }
        val body = buildJsonObject {
            put("image", Base64.encodeToString(jpeg, Base64.NO_WRAP))
            put("categories", jsonStrings(categories))
            put("caption", caption)
            put("personal", personal)
        }
        awaitSession()
        val response = try {
            Supabase.client.functions.invoke("post-ai") {
                setBody(TextContent(body.toString(), ContentType.Application.Json))
                // A cold start plus Gemini's answer can take longer than SPOT's usual 30 seconds.
                timeout { requestTimeoutMillis = 60_000 }
            }
        } catch (error: UnauthorizedRestException) {
            throw IllegalStateException(serverMessage(error.error) ?: "Sign in again to use AI Assist.", error)
        } catch (error: RestException) {
            throw IllegalStateException(serverMessage(error.error) ?: "AI Assist isn't available right now. Try again later.", error)
        }
        val suggestions = json.decodeFromString<PostAiSuggestions>(response.bodyAsText())
        check(suggestions.captions.isNotEmpty() || suggestions.tags.isNotEmpty()) { "AI Assist couldn't write suggestions this time. Try again." }
        return suggestions
    }

    /** The function's own message for people, e.g. a limit or "isn't set up yet". */
    private fun serverMessage(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
    }.getOrNull()?.takeIf { it.isNotBlank() && it.length <= 200 }
}

/** Adds [suggested] hashtags to [current], skipping ones already there (ignoring case) and keeping SPOT's 20-tag limit. */
fun mergeTags(current: List<String>, suggested: List<String>, limit: Int = 20): List<String> {
    val merged = current.toMutableList()
    suggested.forEach { tag ->
        val clean = tag.trim().removePrefix("#")
        if (clean.isNotBlank() && clean.length <= 60 && merged.size < limit && merged.none { it.equals(clean, ignoreCase = true) }) merged += clean
    }
    return merged
}
