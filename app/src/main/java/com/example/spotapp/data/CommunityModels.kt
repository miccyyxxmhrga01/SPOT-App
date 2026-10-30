package com.example.spotapp.data

data class CampusGroup(val groupId: String, val name: String, val description: String, val imageUrl: String,
    val category: String, val privacy: String, val ownerId: String, val ownerName: String,
    val ownerProfileImageUrl: String, val memberCount: Long, val createdAt: Long)
data class CampusEvent(val eventId: String, val title: String, val description: String, val imageUrl: String,
    val category: String, val startsAt: Long, val location: String, val organizer: String, val organizerId: String,
    val organizerName: String, val organizerProfileImageUrl: String, val participantLimit: Long?,
    val participantCount: Long, val createdAt: Long,
    /** "free", "paid", or null for events posted before Spotters chose one. */
    val talentFee: String? = null, val talentFeeBudget: String = "",
    /** Cancelled by the organizer: closed, and only shown to the people who were in it. */
    val cancelled: Boolean = false) {
    val isFull get() = participantLimit?.let { participantCount >= it } == true
    /** "2 accepted · 1 slot left", or open slots when there's no limit. */
    val slotSummary: String get() = "$participantCount accepted · " + (participantLimit?.let {
        val left = (it - participantCount).coerceAtLeast(0)
        "$left ${if (left == 1L) "slot" else "slots"} left"
    } ?: "Open slots")
}

/**
 * What the Spotter offers performers for an event. This is payment for the Creator's performance,
 * not a posting or admission fee.
 */
object TalentFee {
    const val FREE = "free"
    const val PAID = "paid"
    fun label(fee: String?): String = when (fee) {
        FREE -> "For Free Only"
        PAID -> "With Talent Fee Budget"
        else -> "Not specified"
    }
    fun description(fee: String?, budget: String): String = when (fee) {
        FREE -> "Unpaid performance. The Spotter isn't offering a talent fee."
        PAID -> if (budget.isBlank()) "Paid performance. Ask the Spotter about the budget." else "Paid performance · Budget: $budget"
        else -> "The Spotter didn't say whether this performance is paid. Ask before applying."
    }
}

/** A Creator's application to perform at an event; one per Creator and event, and one slot once accepted. */
data class EventApplication(val eventId: String, val userId: String, val displayName: String, val profileImageUrl: String,
    val note: String, val status: String, val createdAt: Long, val eventTitle: String = "", val eventStartsAt: Long = 0,
    val organizerId: String = "") {
    val pending get() = status == "pending"
    /** Still waiting for the organizer's answer. Applications close when the event starts, so one left pending then has lapsed. */
    fun awaitingReview(now: Long) = pending && eventStartsAt > now
}
/** One of the organizer's upcoming events, with how many applications wait for their answer. */
data class OrganizedEvent(val event: CampusEvent, val waiting: Int)
/** What the organizer sees of an applicant beyond the application itself. */
data class Applicant(val username: String, val talents: List<String>)
data class CommunityMember(val userId: String, val displayName: String, val profileImageUrl: String, val role: String = "member")
/** A post on a group's page. [liked] is whether the signed-in member liked it. */
data class GroupPost(val id: String, val groupId: String, val userId: String, val displayName: String,
    val profileImageUrl: String, val text: String, val imageUrl: String, val likesCount: Long, val createdAt: Long,
    val liked: Boolean = false, val edited: Boolean = false) {
    fun canDelete(uid: String, groupOwnerId: String) = uid == userId || uid == groupOwnerId
    fun canEdit(uid: String) = uid == userId
    companion object {
        fun error(text: String, imageUri: String): String? = when {
            text.isBlank() && imageUri.isBlank() -> "Write something or add a photo."
            text.trim().length > 3000 -> "Keep your post under 3,000 characters."
            else -> null
        }
    }
}
data class GroupDraft(val id: String, val name: String, val description: String, val imageUri: String,
    val category: String, val privacy: String) {
    fun error(): String? = when {
        name.trim().length !in 2..80 -> "Use 2–80 characters for your group name."
        description.isBlank() -> "Add a description of your group."
        description.length > 2000 -> "Keep the description under 2,000 characters."
        category.isBlank() || category.length > 60 -> "Choose a talent category."
        privacy !in listOf("public", "private") -> "Choose Public or Private."
        else -> null
    }
}
data class EventDraft(val id: String, val title: String, val description: String, val imageUri: String,
    val category: String, val startsAt: Long, val location: String, val organizer: String, val participantLimit: Long?,
    val talentFee: String = "", val talentFeeBudget: String = "") {
    fun error(now: Long = System.currentTimeMillis()): String? = when {
        title.trim().length !in 2..100 -> "Use 2–100 characters for the event title."
        description.isBlank() -> "Add a description of your event."
        description.length > 3000 -> "Keep the description under 3,000 characters."
        category.isBlank() || category.length > 60 -> "Choose a talent category."
        startsAt <= now -> "Choose a future date and time."
        location.isBlank() -> "Add where the event takes place."
        location.length > 160 -> "Keep the location under 160 characters."
        organizer.isBlank() || organizer.length > 100 -> "Enter the organizer name."
        participantLimit != null && participantLimit !in 1..100000 -> "Use a slot limit between 1 and 100,000, or leave it empty."
        talentFee !in listOf(TalentFee.FREE, TalentFee.PAID) -> "Choose For Free Only or With Talent Fee Budget."
        talentFeeBudget.trim().length > 80 -> "Keep the talent fee budget to 80 characters."
        else -> null
    }
}
internal fun GroupRow.group() = CampusGroup(id, name, description, imageUrl, category, privacy, ownerId, ownerName,
    ownerProfileImageUrl, memberCount, millis(createdAt))
internal fun EventRow.event() = CampusEvent(id, title, description, imageUrl, category, millis(startsAt), location,
    organizer, organizerId, organizerName, organizerProfileImageUrl, participantLimit, participantCount, millis(createdAt),
    talentFee, talentFeeBudget, cancelledAt != null)
internal fun EventApplicationRow.application() = EventApplication(eventId, userId, displayName, profileImageUrl, note, status,
    millis(createdAt), event?.title.orEmpty(), millis(event?.startsAt), event?.organizerId.orEmpty())
internal fun PersonRow.communityMember() = CommunityMember(userId, displayName, profileImageUrl, role)
internal fun GroupPostRow.post(liked: Boolean) = GroupPost(id, groupId, userId, displayName, profileImageUrl, text, imageUrl,
    likesCount.coerceAtLeast(0), millis(createdAt), liked, editedAt != null)
