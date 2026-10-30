package com.example.spotapp.data

import android.content.Context
import com.example.spotapp.data.cloudinary.uploadCommunityImage
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Discover directories and memberships. Every rule is enforced by the database functions too. */
class DiscoverRepository {
    private fun uid() = Supabase.uid() ?: error("Sign in to continue.")
    private fun table(name: String) = Supabase.db.from(name)
    private suspend fun account(): ProfileRow {
        val user = table("profiles").select { filter { eq("id", uid()) } }.decodeSingleOrNull<ProfileRow>()
        return checkNotNull(user?.takeIf { it.profileCompleted }) { "Finish setting up your profile first." }
    }
    suspend fun canCreateEvents(): Boolean = AccountType.fromStorage(account().accountType) == AccountType.TALENT_SEEKER

    private fun directoryArgs(search: String, category: String, after: PageCursor?) = buildJsonObject {
        put("p_term", search.trim()); put("p_category", category); put("p_offset", after?.position ?: 0L)
    }
    private fun <T> page(rows: List<T>, after: PageCursor?, id: (T) -> String): SocialPage<T> {
        val offset = after?.position ?: 0L
        return SocialPage(rows, rows.lastOrNull()?.let { PageCursor(id(it), offset + rows.size) }, rows.size == 30)
    }
    suspend fun groups(search: String, category: String, after: PageCursor? = null): SocialPage<CampusGroup> =
        page(rpc("search_groups", directoryArgs(search, category, after)).decodeList<GroupRow>().map { it.group() }, after) { it.groupId }
    suspend fun events(search: String, category: String, after: PageCursor? = null): SocialPage<CampusEvent> =
        page(rpc("search_events", directoryArgs(search, category, after)).decodeList<EventRow>().map { it.event() }, after) { it.eventId }
    /** Search › Posts (supabase search_posts): captions, hashtags, categories and authors you may see, newest first. */
    suspend fun posts(search: String, after: PageCursor? = null): SocialPage<PublishedPost> =
        page(rpc("search_posts", buildJsonObject { put("p_term", search.trim()); put("p_offset", after?.position ?: 0L) })
            .decodeList<PostRow>().mapNotNull(PostDocumentMapper::published), after) { it.id }

    fun listenGroup(id: String, result: (CampusGroup?) -> Unit, error: (String) -> Unit): ListenerRegistration =
        Live.query(listOf(Watch("groups", "id", id)), {
            table("groups").select { filter { eq("id", id) } }.decodeSingleOrNull<GroupRow>()?.group()
        }, result, { error("This group couldn't load. Please retry.") })
    fun listenEvent(id: String, result: (CampusEvent?) -> Unit, error: (String) -> Unit): ListenerRegistration =
        Live.query(listOf(Watch("events", "id", id)), {
            table("events").select { filter { eq("id", id) } }.decodeSingleOrNull<EventRow>()?.event()
        }, result, { error("This event couldn't load. Please retry.") })
    fun listenMembership(id: String, isEvent: Boolean, result: (Boolean) -> Unit, error: (String) -> Unit): ListenerRegistration {
        val me = uid()
        val name = if (isEvent) "event_participants" else "group_members"
        return Live.query(listOf(Watch(name, "user_id", me)), { exists(name, if (isEvent) "event_id" else "group_id", id, me) },
            result, { error("Your membership couldn't load. Please retry.") })
    }
    fun listenRequest(id: String, result: (Boolean) -> Unit, error: (String) -> Unit): ListenerRegistration {
        val me = uid()
        return Live.query(listOf(Watch("group_join_requests", "user_id", me)), { exists("group_join_requests", "group_id", id, me) },
            result, { error("Your join request couldn't load. Please retry.") })
    }
    private suspend fun exists(name: String, column: String, id: String, user: String) =
        table(name).select { filter { eq(column, id); eq("user_id", user) } }.decodeList<JsonObject>().isNotEmpty()

    suspend fun createGroup(context: Context, draft: GroupDraft, progress: (Float) -> Unit): String {
        draft.error()?.let { error(it) }
        account(); val id = uid()
        // A retry after a lost response must not upload the image again.
        table("groups").select { filter { eq("id", draft.id) } }.decodeSingleOrNull<GroupRow>()?.let { old ->
            check(old.ownerId == id); return old.id
        }
        val image = uploadCommunityImage(context, draft.imageUri, "spot/groups/$id/${draft.id}", progress)
        check(uid() == id) { "Your account changed. Please retry." }
        val created = rpc("create_group", buildJsonObject {
            put("p_id", draft.id); put("p_name", draft.name.trim()); put("p_description", draft.description.trim())
            put("p_image_url", image); put("p_category", draft.category); put("p_privacy", draft.privacy)
        }).decodeAs<String>()
        Live.changed("groups", "group_members")
        progress(1f); return created
    }
    /**
     * Saves the owner's changes to a group. [draft]'s image is the current cover's URL when it wasn't changed,
     * a picked image to upload, or blank to remove the cover.
     */
    suspend fun updateGroup(context: Context, current: CampusGroup, draft: GroupDraft, progress: (Float) -> Unit) {
        draft.error()?.let { error(it) }
        val id = uid()
        check(current.ownerId == id) { "Only the group's owner can edit it." }
        val image = if (draft.imageUri == current.imageUrl) current.imageUrl
            else uploadCommunityImage(context, draft.imageUri, "spot/groups/$id/${current.groupId}", progress)
        check(uid() == id) { "Your account changed. Please retry." }
        rpc("update_group", buildJsonObject {
            put("p_id", current.groupId); put("p_name", draft.name.trim()); put("p_description", draft.description.trim())
            put("p_image_url", image); put("p_category", draft.category); put("p_privacy", draft.privacy)
        })
        Live.changed("groups", "conversations")
        progress(1f)
    }
    /** Deletes a group for everyone, with its members, posts and chat. */
    suspend fun deleteGroup(id: String) {
        rpc("delete_group", buildJsonObject { put("p_id", id) })
        Live.changed("groups", "group_members", "group_posts", "conversations", "notifications")
    }
    /** The owner removes a member, who also leaves the group chat. */
    suspend fun removeMember(groupId: String, user: String) {
        rpc("remove_group_member", buildJsonObject { put("p_id", groupId); put("p_user", user) })
        Live.changed("group_members", "groups", "conversations")
    }
    suspend fun requestJoin(id: String) {
        rpc("request_join_group", buildJsonObject { put("p_id", id) })
        Live.changed("group_join_requests")
    }
    suspend fun cancelRequest(id: String) {
        table("group_join_requests").delete { filter { eq("group_id", id); eq("user_id", uid()) } }
        Live.changed("group_join_requests")
    }
    // Joining or leaving also joins or leaves the group's chat.
    suspend fun setGroupJoined(id: String, joined: Boolean) {
        rpc("set_group_joined", buildJsonObject { put("p_id", id); put("p_joined", joined) })
        Live.changed("group_members", "groups", "conversations")
    }
    suspend fun reviewRequest(id: String, applicant: String, approve: Boolean) {
        rpc("review_group_request", buildJsonObject { put("p_id", id); put("p_applicant", applicant); put("p_approve", approve) })
        Live.changed("group_join_requests", "group_members", "groups", "conversations")
    }

    /** The chat of an event or group, for its organizer, participants or members. Returns the conversation id. */
    suspend fun openChat(id: String, isEvent: Boolean): String {
        val chat = rpc("open_community_chat", buildJsonObject { put("p_kind", if (isEvent) "event" else "group"); put("p_id", id) })
            .decodeAs<String>()
        Live.changed("conversations")
        return chat
    }

    /** The newest [count] posts on a group's page, each with whether you liked it. Outsiders of a private group get none. */
    fun listenGroupPosts(groupId: String, count: Int, result: (SocialPage<GroupPost>) -> Unit, error: (String) -> Unit): ListenerRegistration {
        val me = uid()
        return Live.query(listOf(Watch("group_posts", "group_id", groupId), Watch("group_post_likes", "user_id", me)), {
            val rows = table("group_posts").select {
                filter { eq("group_id", groupId) }
                order("seq", Order.DESCENDING); limit(count.toLong())
            }.decodeList<GroupPostRow>()
            val liked = if (rows.isEmpty()) emptySet() else table("group_post_likes").select(Columns.list("post_id")) {
                filter { eq("user_id", me); isIn("post_id", rows.map { it.id }) }
            }.decodeList<PostIdRow>().map { it.postId }.toSet()
            SocialPage(rows.map { it.post(it.id in liked) }, null, rows.size == count)
        }, result, { error("Posts couldn't load. Please retry.") })
    }
    suspend fun createGroupPost(context: Context, id: String, groupId: String, text: String, imageUri: String, progress: (Float) -> Unit) {
        GroupPost.error(text, imageUri)?.let { error(it) }
        val me = uid()
        // A retry after a lost response must not upload the photo again.
        if (table("group_posts").select { filter { eq("id", id) } }.decodeSingleOrNull<GroupPostRow>() == null) {
            val image = uploadCommunityImage(context, imageUri, "spot/group_posts/$me/$id", progress)
            check(uid() == me) { "Your account changed. Please retry." }
            rpc("create_group_post", buildJsonObject {
                put("p_id", id); put("p_group_id", groupId); put("p_text", text.trim()); put("p_image_url", image)
            })
        }
        Live.changed("group_posts")
        progress(1f)
    }
    /** The author changes their group post's text; its photo stays as posted. */
    suspend fun updateGroupPost(id: String, text: String) {
        require(text.trim().length <= 3000) { "Keep your post under 3,000 characters." }
        rpc("update_group_post", buildJsonObject { put("p_id", id); put("p_text", text.trim()) })
        Live.changed("group_posts")
    }
    suspend fun deleteGroupPost(id: String) {
        rpc("delete_group_post", buildJsonObject { put("p_id", id) })
        Live.changed("group_posts")
    }
    suspend fun setGroupPostLiked(id: String, liked: Boolean) {
        rpc("set_group_post_liked", buildJsonObject { put("p_id", id); put("p_liked", liked) })
        Live.changed("group_post_likes", "group_posts")
    }
    suspend fun people(id: String, isEvent: Boolean, requests: Boolean, after: PageCursor? = null): SocialPage<CommunityMember> {
        val name = if (isEvent) "event_participants" else if (requests) "group_join_requests" else "group_members"
        val rows = table(name).select {
            filter { eq(if (isEvent) "event_id" else "group_id", id); if (after != null) gt("seq", after.position) }
            order("seq", Order.ASCENDING); limit(50)
        }.decodeList<PersonRow>()
        return SocialPage(rows.map { it.communityMember() }, rows.lastOrNull()?.let { PageCursor(it.userId, it.seq) }, rows.size == 50)
    }
    suspend fun createEvent(context: Context, draft: EventDraft, progress: (Float) -> Unit): String {
        draft.error()?.let { error(it) }
        val user = account(); val id = uid()
        check(AccountType.fromStorage(user.accountType) == AccountType.TALENT_SEEKER) { "Only Spotters can create events." }
        table("events").select { filter { eq("id", draft.id) } }.decodeSingleOrNull<EventRow>()?.let { old ->
            check(old.organizerId == id); return old.id
        }
        val image = uploadCommunityImage(context, draft.imageUri, "spot/events/$id/${draft.id}", progress)
        check(uid() == id) { "Your account changed. Please retry." }
        val created = rpc("create_event", buildJsonObject {
            put("p_id", draft.id); put("p_title", draft.title.trim()); put("p_description", draft.description.trim())
            put("p_image_url", image); put("p_category", draft.category); put("p_starts_at", isoTimestamp(draft.startsAt))
            put("p_location", draft.location.trim()); put("p_organizer", draft.organizer.trim())
            put("p_participant_limit", draft.participantLimit); put("p_talent_fee", draft.talentFee)
            put("p_talent_fee_budget", if (draft.talentFee == TalentFee.PAID) draft.talentFeeBudget.trim() else "")
        }).decodeAs<String>()
        Live.changed("events")
        progress(1f); return created
    }
    /**
     * Saves the organizer's changes before the event starts. [draft]'s image is the current poster's URL when it
     * wasn't changed, a picked image to upload, or blank to remove the poster.
     */
    suspend fun updateEvent(context: Context, current: CampusEvent, draft: EventDraft, progress: (Float) -> Unit) {
        draft.error()?.let { error(it) }
        val id = uid()
        check(current.organizerId == id) { "Only the event's organizer can edit it." }
        val image = if (draft.imageUri == current.imageUrl) current.imageUrl
            else uploadCommunityImage(context, draft.imageUri, "spot/events/$id/${current.eventId}", progress)
        check(uid() == id) { "Your account changed. Please retry." }
        rpc("update_event", buildJsonObject {
            put("p_id", current.eventId); put("p_title", draft.title.trim()); put("p_description", draft.description.trim())
            put("p_image_url", image); put("p_category", draft.category); put("p_starts_at", isoTimestamp(draft.startsAt))
            put("p_location", draft.location.trim()); put("p_organizer", draft.organizer.trim())
            put("p_participant_limit", draft.participantLimit); put("p_talent_fee", draft.talentFee)
            put("p_talent_fee_budget", if (draft.talentFee == TalentFee.PAID) draft.talentFeeBudget.trim() else "")
        })
        Live.changed("events", "conversations", "talent_requests")
        progress(1f)
    }
    /** Removes an event for good; the server refuses once anyone has applied, joined or been invited. */
    suspend fun deleteEvent(id: String) {
        rpc("delete_event", buildJsonObject { put("p_id", id) })
        Live.changed("events", "conversations")
    }
    /** Closes an event that people are in, and tells them. Performers keep it and its chat. */
    suspend fun cancelEvent(id: String) {
        rpc("cancel_event", buildJsonObject { put("p_id", id) })
        Live.changed("events", "talent_requests")
    }
    /** Invitations to [eventId] still waiting for an answer; only its organizer can see them. */
    suspend fun pendingInvitations(eventId: String): Int =
        table("talent_requests").select(Columns.list("id")) {
            filter { eq("kind", "invitation"); eq("source_id", eventId); eq("status", "pending") }
        }.decodeList<JsonObject>().size
    // Creators join an event by applying; the organizer accepts or declines each application, and only an
    // accepted Creator takes a slot and joins the event's chat. Each application carries its event, whose start
    // closes applications still pending.
    private val withEvent = Columns.raw("*, event:events(title, starts_at, organizer_id)")
    fun listenMyApplication(eventId: String, result: (EventApplication?) -> Unit, error: (String) -> Unit): ListenerRegistration {
        val me = uid()
        return Live.query(listOf(Watch("event_applications", "user_id", me)), {
            table("event_applications").select(withEvent) { filter { eq("event_id", eventId); eq("user_id", me) } }
                .decodeSingleOrNull<EventApplicationRow>()?.application()
        }, result, { error("Your application couldn't load. Please retry.") })
    }
    /** An event's applications, newest first. Only its organizer can read other Creators' applications. */
    fun listenApplications(eventId: String, result: (List<EventApplication>) -> Unit, error: (String) -> Unit): ListenerRegistration =
        Live.query(listOf(Watch("event_applications", "event_id", eventId)), {
            table("event_applications").select(withEvent) {
                filter { eq("event_id", eventId) }
                order("seq", Order.DESCENDING); limit(200)
            }.decodeList<EventApplicationRow>().map { it.application() }
        }, result, { error("Applications couldn't load. Please retry.") })
    /**
     * Your place in each event you've applied to or joined: "going" once you're a participant, otherwise your
     * application's status ("pending" or "declined").
     */
    fun listenMyEventStatus(result: (Map<String, String>) -> Unit): ListenerRegistration {
        val me = uid()
        return Live.query(listOf(Watch("event_applications", "user_id", me), Watch("event_participants", "user_id", me)), {
            val applied = table("event_applications").select(Columns.list("event_id", "status")) { filter { eq("user_id", me) } }
                .decodeList<EventStatusRow>().associate { it.eventId to it.status }
            val going = table("event_participants").select(Columns.list("event_id")) { filter { eq("user_id", me) } }
                .decodeList<EventIdRow>().associate { it.eventId to "going" }
            applied + going
        }, result, { })
    }
    suspend fun applyToEvent(id: String, note: String = "") {
        require(note.trim().length <= 500) { "Keep your note under 500 characters." }
        rpc("apply_to_event", buildJsonObject { put("p_id", id); put("p_note", note.trim()) })
        Live.changed("event_applications")
    }
    suspend fun withdrawApplication(id: String) {
        rpc("withdraw_event_application", buildJsonObject { put("p_id", id) })
        Live.changed("event_applications")
    }
    suspend fun reviewApplication(eventId: String, applicant: String, accept: Boolean) {
        rpc("review_event_application", buildJsonObject { put("p_id", eventId); put("p_applicant", applicant); put("p_accept", accept) })
        Live.changed("event_applications", "event_participants", "events", "conversations")
    }
    // Leaving frees the slot and leaves the event's chat.
    suspend fun leaveEvent(id: String) {
        rpc("set_event_joined", buildJsonObject { put("p_id", id); put("p_joined", false) })
        Live.changed("event_participants", "event_applications", "events", "conversations")
    }
    /** Events [organizerId] posted, latest date first. */
    suspend fun eventsBy(organizerId: String, after: PageCursor? = null): SocialPage<CampusEvent> {
        val offset = after?.position ?: 0L
        val rows = table("events").select {
            filter { eq("organizer_id", organizerId) }
            order("starts_at", Order.DESCENDING); order("id", Order.DESCENDING); range(offset, offset + 29)
        }.decodeList<EventRow>()
        return SocialPage(rows.map { it.event() }, rows.lastOrNull()?.let { PageCursor(it.id, offset + rows.size) }, rows.size == 30)
    }
    /** Your upcoming events that aren't cancelled, soonest first, each with its applications still waiting. */
    fun listenMyUpcomingEvents(result: (List<OrganizedEvent>) -> Unit, error: (String) -> Unit): ListenerRegistration {
        val me = uid()
        return Live.query(listOf(Watch("events", "organizer_id", me), Watch("event_applications")), {
            val now = System.currentTimeMillis()
            val upcoming = eventsBy(me).items.filter { !it.cancelled && it.startsAt > now }.sortedBy { it.startsAt }
            val waiting = if (upcoming.isEmpty()) emptyMap() else table("event_applications").select(Columns.list("event_id")) {
                filter { isIn("event_id", upcoming.map { it.eventId }); eq("status", "pending") }
            }.decodeList<EventIdRow>().groupingBy { it.eventId }.eachCount()
            upcoming.map { OrganizedEvent(it, waiting[it.eventId] ?: 0) }
        }, result, { error("Your events couldn't load. Please retry.") })
    }
    /** Usernames and talents of the Creators in [ids], for the organizer's review list. */
    suspend fun applicants(ids: List<String>): Map<String, Applicant> =
        if (ids.isEmpty()) emptyMap() else table("profiles").select(Columns.list("id", "username", "talent_categories")) {
            filter { isIn("id", ids.distinct()) }
        }.decodeList<ProfileRow>().associate { it.id to Applicant(it.username.orEmpty(), it.talentCategories) }
    /** Events [organizerId] posted, kept current as they change. */
    fun listenOrganizedEvents(organizerId: String, result: (SocialPage<CampusEvent>) -> Unit, error: (String) -> Unit): ListenerRegistration =
        Live.query(listOf(Watch("events", "organizer_id", organizerId)), { eventsBy(organizerId) }, result,
            { error("Events couldn't load. Please retry.") })
    /** Events [user] performed at or is going to, latest first. Hidden when you can't see their profile. */
    fun listenProfileEvents(user: String, result: (List<CampusEvent>) -> Unit, error: (String) -> Unit): ListenerRegistration =
        Live.query(listOf(Watch("event_participants"), Watch("events")), {
            rpc("profile_events", buildJsonObject { put("p_user", user) }).decodeList<EventRow>().map { it.event() }
        }, result, { error("Events couldn't load. Please retry.") })
    companion object { val shared by lazy { DiscoverRepository() } }
}
