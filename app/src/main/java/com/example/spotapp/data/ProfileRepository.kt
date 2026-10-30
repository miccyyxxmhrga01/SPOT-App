package com.example.spotapp.data

import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale

class ProfileRepository {
    companion object { val shared by lazy { ProfileRepository() } }
    private val profiles get() = Supabase.db.from("profiles")

    // Profiles change rarely, so every identity shares one Realtime channel instead of one per author.
    fun listen(id: String, result: (SpotUser?) -> Unit, error: (String) -> Unit = {}): ListenerRegistration =
        Live.query(listOf(Watch("profiles")), { profile(id) }, result, { error("Profile couldn't load.") })

    internal suspend fun profile(id: String): SpotUser? = row(id)?.toUser()

    internal suspend fun row(id: String): ProfileRow? =
        profiles.select { filter { eq("id", id) } }.decodeSingleOrNull<ProfileRow>()

    suspend fun search(text: String): List<SpotUser> = coroutineScope {
        val key = text.trim().lowercase(Locale.ROOT).removePrefix("@")
        val rows = if (key.isBlank()) searchProfiles("", byUsername = false)
        else listOf(false, true).map { byUsername -> async { searchProfiles(key, byUsername) } }.flatMap { it.await() }
        rows.distinctBy { it.id }.map { it.toUser() }
    }

    /** Role-aware, paged public directory. Prefix search supports names and @usernames. */
    suspend fun creators(search: String = "", category: String = "", after: PageCursor? = null): SocialPage<com.example.spotapp.ui.theme.models.SpotUiProfile> {
        val term = search.trim().lowercase(Locale.ROOT)
        val offset = after?.position ?: 0L
        val rows = searchProfiles(term.removePrefix("@"), term.startsWith("@"), AccountType.STUDENT.storageValue,
            category.ifBlank { null }, offset = offset)
        return SocialPage(rows.map { it.toUser().asProfileUi() },
            rows.lastOrNull()?.let { PageCursor(it.id, offset + rows.size) }, rows.size == 30)
    }

    internal suspend fun searchProfiles(term: String, byUsername: Boolean, accountType: String? = null,
        category: String? = null, limit: Int = 30, offset: Long = 0): List<ProfileRow> =
        rpc("search_profiles", buildJsonObject {
            put("p_term", term); put("p_by_username", byUsername); put("p_account_type", accountType)
            put("p_category", category); put("p_limit", limit); put("p_offset", offset)
        }).decodeList()

    suspend fun loadProfiles(ids: List<String>): List<SpotUser> = ids.distinct().chunked(100).flatMap { group ->
        profiles.select(Columns.ALL) { filter { isIn("id", group) } }.decodeList<ProfileRow>().map { it.toUser() }
    }

    suspend fun save(details: ProfileDetails, photo: String?, pronouns: String, links: String, gender: String, completing: Boolean = false) {
        details.validationError()?.let { throw IllegalArgumentException(it) }
        require(pronouns.length <= 40 && links.length <= 300 && gender.length <= 40) { "One of your fields is too long." }
        require(links.isBlank() || links.startsWith("https://")) { "Use a full https:// link." }
        Supabase.requireUid()
        val fields = details.firestoreFields()
        fun strings(key: String) = jsonStrings((fields[key] as? List<*>).orEmpty().filterIsInstance<String>())
        // The server checks username uniqueness and keeps a completed account type fixed.
        rpc("save_profile", buildJsonObject {
            put("p_account_type", details.accountType.storageValue)
            put("p_display_name", fields["displayName"] as String)
            put("p_username", fields["username"] as String)
            put("p_course", fields["course"] as String)
            put("p_talents", strings("talentCategories"))
            put("p_organization", fields["organization"] as String)
            put("p_seeking_talents", strings("seekingTalents"))
            put("p_bio", fields["bio"] as String)
            put("p_photo", photo)
            put("p_pronouns", pronouns.trim()); put("p_links", links.trim()); put("p_gender", gender.trim())
            put("p_completing", completing)
        })
        Live.changed("profiles")
    }
}

/** Reference-counted identity streams prevent repeated listeners for the same visible author. */
object ProfileIdentityStore {
    private data class Entry(val state: MutableStateFlow<SpotUser?> = MutableStateFlow(null), var clients: Int = 0, var listener: ListenerRegistration? = null)
    private val entries = linkedMapOf<String, Entry>()
    private var account: String? = null
    @Synchronized private fun entry(uid: String): Entry {
        val current = Supabase.uid()
        if (current != account) { entries.values.forEach { it.listener?.remove(); it.state.value = null }; entries.clear(); account = current }
        return entries.getOrPut(uid) { Entry() }
    }
    @Synchronized fun identity(uid: String): StateFlow<SpotUser?> = entry(uid).state
    @Synchronized fun acquire(uid: String): () -> Unit {
        if (uid.isBlank()) return {}
        val e = entry(uid)
        if (account == null) return {}
        e.clients++
        if (e.listener == null) e.listener = ProfileRepository.shared.listen(uid, { e.state.value = it })
        return { synchronized(this) {
            e.clients = (e.clients - 1).coerceAtLeast(0)
            if (e.clients == 0) { e.listener?.remove(); e.listener = null }
            if (entries.size > 256) entries.entries.removeAll { it.value.clients == 0 && it.value !== e }
        } }
    }
}

fun SpotUser.asProfileUi() = com.example.spotapp.ui.theme.models.SpotUiProfile(
    uid, displayName, username, talentCategories.joinToString(", "), course, profileImageUrl,
    accountType = accountType, organization = organization, bio = bio, talentCategories = talentCategories,
    seekingTalents = seekingTalents, pronouns = pronouns, links = links, gender = gender,
    isPrivate = isPrivate, privacyUpdating = privacyUpdating, openToCollab = openToCollab)
