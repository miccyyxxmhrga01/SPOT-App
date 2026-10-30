package com.example.spotapp.data.collaboration

import com.example.spotapp.data.AccountType
import com.example.spotapp.data.Supabase
import com.example.spotapp.data.awaitSession
import com.example.spotapp.data.jsonStrings
import com.example.spotapp.ui.theme.models.SpotUiProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A suggested collaborator and why SPOT suggests them. */
data class CollaborationCreator(val profile: SpotUiProfile, val talents: List<String>, val reason: String = "")

@Serializable
private data class TalentRow(val talent: String)

/** Uses SPOT's existing model results and database function; never fabricates predictions. */
class CollaborationRepository(private val supabase: SupabaseClient = Supabase.client) {
    /** One database request saves the whole selection, so a failure cannot leave a partial selection. */
    suspend fun saveDetectedTalents(
        predictions: List<DetectedCollaborationTalent>,
        expectedUserId: String? = null
    ): Result<Unit> = result {
        awaitSession()
        val uid = checkNotNull(supabase.auth.currentUserOrNull()?.id) { "Sign in to save a talent." }
        check(expectedUserId == null || uid == expectedUserId) { "Your account changed. Sign in again to save your talents." }
        require(predictions.isNotEmpty()) { "Select a talent for collaborations first." }
        val talents = predictions.map { prediction ->
            require(prediction.confidence.isFinite() && prediction.confidence in 0.0..1.0) {
                "This prediction has an invalid confidence. Analyze the media again."
            }
            val talent = requireNotNull(TalentMapper.fromPrediction(prediction.prediction)) {
                "This talent isn't supported for collaborations yet."
            }
            UserTalent(uid, talent, prediction.confidence, "ai", true)
        }.groupBy { it.talent }.values.map { matching -> matching.maxBy { it.confidence } }
        // Only called when the creator explicitly accepts a result for collaboration matching.
        check(supabase.auth.currentUserOrNull()?.id == uid) { "Your account changed. Sign in again to save your talents." }
        supabase.postgrest["user_talents"].upsert(talents) {
            onConflict = "user_id,talent"
        }
    }

    /** The signed-in creator's own collaboration talents. */
    suspend fun getMyTalents(): Result<List<String>> = result {
        awaitSession()
        val uid = Supabase.requireUid()
        supabase.postgrest["user_talents"].select(Columns.list("talent")) {
            filter { eq("user_id", uid); eq("confirmed", true) }
            order("talent", Order.ASCENDING)
        }.decodeList<TalentRow>().map { it.talent }
    }

    /** Takes a talent back out of collaboration matching; other creators stop seeing it on their next refresh. */
    suspend fun removeTalent(talent: String): Result<Unit> = result {
        awaitSession()
        val uid = Supabase.requireUid()
        supabase.postgrest["user_talents"].delete { filter { eq("user_id", uid); eq("talent", talent) } }
    }

    /**
     * Ranked by suggest_collaborators from relevant and complementary talents, shared interests, location and
     * collaboration preferences. It leaves out you, blocked accounts and dismissed suggestions.
     */
    suspend fun getCreators(): Result<List<CollaborationCreator>> = result {
        awaitSession()
        val uid = Supabase.requireUid()
        val suggestions = supabase.postgrest.rpc("suggest_collaborators").decodeList<CollaboratorSuggestion>()
        check(Supabase.uid() == uid) { "Your account changed. Open Collaborations again." }
        suggestions.filter { it.userId != uid }.map { s ->
            CollaborationCreator(SpotUiProfile(s.userId, s.displayName, s.username, s.talents.joinToString(", "), "",
                s.avatarUrl.orEmpty(), accountType = AccountType.STUDENT, talentCategories = s.talents), s.talents, s.reason)
        }
    }

    /** Hides a suggestion until [undoDismiss]. */
    suspend fun dismiss(userId: String): Result<Unit> = result {
        awaitSession()
        val uid = Supabase.requireUid()
        supabase.postgrest["collab_dismissals"].upsert(buildJsonObject { put("user_id", uid); put("dismissed_id", userId) }) {
            onConflict = "user_id,dismissed_id"; ignoreDuplicates = true
        }
    }

    suspend fun undoDismiss(userId: String): Result<Unit> = result {
        awaitSession()
        val uid = Supabase.requireUid()
        supabase.postgrest["collab_dismissals"].delete { filter { eq("user_id", uid); eq("dismissed_id", userId) } }
    }

    suspend fun getPreferences(): Result<CollabPreferences> = result {
        awaitSession()
        val uid = Supabase.requireUid()
        supabase.postgrest["profiles"].select(Columns.list("location", "collab_talents", "open_to_collab")) {
            filter { eq("id", uid) }
        }.decodeSingleOrNull<CollabPreferences>() ?: CollabPreferences()
    }

    suspend fun savePreferences(preferences: CollabPreferences): Result<Unit> = result {
        preferences.error()?.let { throw IllegalArgumentException(it) }
        awaitSession()
        Supabase.requireUid()
        supabase.postgrest.rpc("set_collab_preferences", buildJsonObject {
            put("p_location", preferences.location.trim()); put("p_talents", jsonStrings(preferences.collabTalents))
            put("p_open", preferences.openToCollab)
        })
        com.example.spotapp.data.Live.changed("profiles")
    }

    private suspend fun <T> result(block: suspend () -> T): Result<T> = try { Result.success(block()) }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { Result.failure(error) }
}
