package com.example.spotapp.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One private local draft per account. Media uses persisted Photo Picker URI grants. */
class PostDraftStore(context: Context, uid: String) {
    private val prefs = context.getSharedPreferences(fileName(uid), Context.MODE_PRIVATE)
    fun load(): PostDraft? = prefs.getString("draft", null)?.let(PostDraftCodec::decode)
    fun save(draft: PostDraft) {
        check(prefs.edit().putString("draft", PostDraftCodec.encode(draft)).commit()) { "Your draft couldn't be saved. Please retry." }
    }
    fun clear() { prefs.edit().remove("draft").apply() }

    companion object {
        private fun fileName(uid: String) = "post_draft_$uid"

        /** Removes [uid]'s draft file from this phone once that account is deleted. */
        fun forget(context: Context, uid: String) { context.deleteSharedPreferences(fileName(uid)) }
    }
}

/** Shared by persistent drafts and Compose's saved-state restoration. */
object PostDraftCodec {
    fun decode(value: String): PostDraft? = runCatching {
        val j = JSONObject(value)
        fun strings(key: String): List<String> = j.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        val edits = j.optJSONObject("mediaEdits")
        val instructions = edits?.keys()?.asSequence()?.associateWith { uri ->
            val e = edits.getJSONObject(uri)
            val look = e.optJSONObject("look")
            PostMediaEdit(e.optInt("quarterTurns"), e.optString("crop", "original"), e.optBoolean("muted", false),
                if (look == null) MediaLook() else MediaLook(look.optString("filter", "normal"), look.optDouble("brightness", .0).toFloat(),
                    look.optDouble("contrast", .0).toFloat(), look.optDouble("saturation", .0).toFloat(), look.optDouble("warmth", .0).toFloat()).normalized())
        }.orEmpty()
        PostDraft(j.getString("id"), j.optString("caption"), strings("categories"), strings("tags"), strings("mediaUris"),
            j.optString("audience", "followers"), j.optString("location"), strings("taggedPeople"),
            j.optBoolean("aiCaption", false), j.optBoolean("aiTagged", false), j.optBoolean("aiCategorySuggested", false), instructions, j.optBoolean("personal", false))
    }.getOrNull()
    fun encode(draft: PostDraft): String {
        val edits = JSONObject()
        draft.mediaEdits.forEach { (uri, edit) ->
            val look = edit.look.normalized()
            edits.put(uri, JSONObject().put("quarterTurns", edit.rotation).put("crop", edit.crop).put("muted", edit.muted)
                .put("look", JSONObject().put("filter", look.filter).put("brightness", look.brightness.toDouble())
                    .put("contrast", look.contrast.toDouble()).put("saturation", look.saturation.toDouble()).put("warmth", look.warmth.toDouble())))
        }
        val j = JSONObject().put("id", draft.id).put("caption", draft.caption).put("categories", JSONArray(draft.categories))
            .put("tags", JSONArray(draft.tags)).put("mediaUris", JSONArray(draft.mediaUris)).put("audience", draft.audience)
            .put("location", draft.location).put("taggedPeople", JSONArray(draft.taggedPeople))
            .put("mediaEdits", edits).put("aiCaption", draft.aiCaption).put("aiTagged", draft.aiTagged).put("aiCategorySuggested", draft.aiCategorySuggested)
            .put("personal", draft.personal)
        return j.toString()
    }
}
