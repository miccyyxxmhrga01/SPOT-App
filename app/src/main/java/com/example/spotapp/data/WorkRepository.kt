package com.example.spotapp.data

import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/** The database is authoritative for roles, ownership and request transitions. */
class WorkRepository {
    companion object { val shared by lazy { WorkRepository() } }
    private fun uid() = requireNotNull(Supabase.uid()) { "Please sign in again." }
    private fun table(name: String) = Supabase.db.from(name)
    fun newOpportunityId() = UUID.randomUUID().toString()

    suspend fun opportunities(mine: Boolean, after: PageCursor? = null): SocialPage<Opportunity> {
        val rows = table("opportunities").select {
            filter {
                if (mine) eq("seeker_id", uid()) else eq("status", "open")
                if (after != null) lt("seq", after.position)
            }
            order("seq", Order.DESCENDING); limit(30)
        }.decodeList<OpportunityRow>()
        return SocialPage(rows.map(::opportunity), rows.lastOrNull()?.let { PageCursor(it.id, it.seq) }, rows.size == 30)
    }

    fun listenOpportunity(id: String, result: (Opportunity?) -> Unit, error: (String) -> Unit): ListenerRegistration =
        Live.query(listOf(Watch("opportunities", "id", id)), {
            table("opportunities").select { filter { eq("id", id) } }.decodeSingleOrNull<OpportunityRow>()?.let(::opportunity)
        }, result, { error("This opportunity couldn't load. Please retry.") })

    suspend fun publish(id: String, draft: OpportunityDraft) {
        draft.problem()?.let { throw IllegalArgumentException(it) }
        uid()
        rpc("publish_opportunity", buildJsonObject {
            put("p_id", id); put("p_title", draft.title.trim()); put("p_kind", draft.kind)
            put("p_description", draft.description.trim()); put("p_category", draft.category)
            put("p_location", draft.location.trim()); put("p_deadline", isoTimestamp(draft.deadline))
        })
        Live.changed("opportunities")
    }

    suspend fun close(id: String) {
        rpc("close_opportunity", buildJsonObject { put("p_id", id) })
        Live.changed("opportunities")
    }

    suspend fun myEvents(after: PageCursor? = null): SocialPage<CampusEvent> = DiscoverRepository.shared.eventsBy(uid(), after)

    suspend fun event(id: String): CampusEvent? =
        table("events").select { filter { eq("id", id) } }.decodeSingleOrNull<EventRow>()?.event()

    fun listenRequests(seeker: Boolean, after: PageCursor? = null, opportunityId: String? = null,
        result: (SocialPage<TalentRequest>) -> Unit, error: (String) -> Unit, kind: String? = null): ListenerRegistration {
        val me = uid()
        val party = if (seeker) "seeker_id" else "creator_id"
        if (opportunityId == null && kind != null) require(kind in listOf("invitation", "application"))
        return Live.query(listOf(Watch("talent_requests", party, me)), {
            val rows = table("talent_requests").select {
                filter {
                    eq(party, me)
                    if (opportunityId != null) { eq("kind", "application"); eq("source_id", opportunityId) }
                    else if (kind != null) eq("kind", kind)
                    if (after != null) lt("seq", after.position)
                }
                order("seq", Order.DESCENDING); limit(30)
            }.decodeList<TalentRequestRow>()
            SocialPage(rows.map { it.request() }, rows.lastOrNull()?.let { PageCursor(it.id, it.seq) }, rows.size == 30)
        }, result, { error("Requests couldn't load. Check your connection and retry.") })
    }

    fun listenApplication(opportunityId: String, result: (TalentRequest?) -> Unit, error: (String) -> Unit): ListenerRegistration {
        val me = uid()
        return Live.query(listOf(Watch("talent_requests", "creator_id", me)), {
            table("talent_requests").select { filter { eq("id", "application_${opportunityId}_$me") } }
                .decodeSingleOrNull<TalentRequestRow>()?.request()
        }, result, { error("Application status couldn't load. Please retry.") })
    }

    suspend fun invite(event: CampusEvent, creatorId: String, details: String) {
        val me = uid()
        require(event.organizerId == me && creatorId != me) { "Choose your event and a creator." }
        require(event.startsAt > System.currentTimeMillis()) { "Choose an upcoming event." }
        createRequest("invitation", event.eventId, creatorId, details)
    }

    suspend fun apply(item: Opportunity, note: String) {
        require(item.isOpen()) { "Applications have closed." }
        createRequest("application", item.id, uid(), note)
    }

    // The server takes the seeker and title from the event or opportunity itself, notifies the other person,
    // and sends an invitation as a message in the direct chat with the creator.
    private suspend fun createRequest(kind: String, source: String, creator: String, details: String) {
        require(details.trim().length in 10..2000) { "Add details between 10 and 2,000 characters." }
        rpc("create_talent_request", buildJsonObject {
            put("p_kind", kind); put("p_source_id", source); put("p_creator_id", creator); put("p_details", details.trim())
        })
        Live.changed("talent_requests", "conversations", "messages")
    }

    // Accepting an invitation also adds the creator to the event and its chat.
    suspend fun respond(item: TalentRequest, next: String) {
        val me = uid(); require(item.allows(me, next)) { "This action is no longer available." }
        rpc("respond_talent_request", buildJsonObject { put("p_id", item.id); put("p_status", next) })
        Live.changed("talent_requests", "event_participants", "events", "conversations")
    }

    private fun opportunity(s: OpportunityRow) = Opportunity(s.id, s.seekerId, s.title, s.kind, s.description, s.category,
        s.location, millis(s.deadline), s.status, millis(s.createdAt))
}

internal fun TalentRequestRow.request() = TalentRequest(id, kind, sourceId, seekerId, creatorId, title, details, status, millis(createdAt))
