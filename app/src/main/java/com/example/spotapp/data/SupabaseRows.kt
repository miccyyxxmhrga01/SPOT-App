package com.example.spotapp.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Database rows as PostgREST returns them. Property names map to snake_case columns
// (see Supabase.client), and defaults keep older or partial rows readable.

@Serializable
internal data class ProfileRow(
    val id: String, val fullName: String = "", val displayName: String = "", val username: String? = null,
    val course: String = "", val talentCategories: List<String> = emptyList(), val bio: String = "",
    val profileImageUrl: String = "", val accountType: String? = null, val organization: String = "",
    val seekingTalents: List<String> = emptyList(), val pronouns: String = "", val links: String = "",
    val gender: String = "", val isPrivate: Boolean = false, val profileCompleted: Boolean = false,
    val location: String = "", val collabTalents: List<String> = emptyList(), val openToCollab: Boolean = true
) {
    fun toUser() = SpotUser(
        uid = id, displayName = displayName.ifBlank { fullName }, username = username.orEmpty(), course = course,
        talentCategories = talentCategories, bio = bio, profileImageUrl = profileImageUrl,
        accountType = AccountType.fromStorage(accountType), organization = organization, seekingTalents = seekingTalents,
        pronouns = pronouns, links = links, gender = gender, isPrivate = isPrivate, openToCollab = openToCollab)
    fun toPerson() = ChatPerson(id, displayName.ifBlank { fullName }, username.orEmpty(), profileImageUrl)
}

@Serializable
internal data class PostRow(
    val id: String, val seq: Long = 0, val userId: String = "", val caption: String = "",
    val categories: List<String> = emptyList(), val tags: List<String> = emptyList(),
    val media: JsonArray = JsonArray(emptyList()), val mediaType: String = "image", val audience: String = "",
    val location: String = "", val taggedPeople: List<String> = emptyList(),
    val authorDisplayName: String = "", val authorUsername: String = "", val authorTalent: String = "",
    val authorCourse: String = "", val authorProfileImageUrl: String = "",
    val likesCount: Long = 0, val viewsCount: Long = 0, val commentsCount: Long = 0, val sharesCount: Long = 0,
    val aiCaption: Boolean = false, val aiTagged: Boolean = false, val aiCategorySuggested: Boolean = false,
    val createdAt: String? = null, val author: PostAuthorRow? = null,
    val editedAt: String? = null, val pinnedAt: String? = null, val likesHidden: Boolean = false, val commentsOff: Boolean = false
)

// Embedded from profiles; an account type is fixed once the profile is completed.
@Serializable
internal data class PostAuthorRow(val accountType: String? = null)

@Serializable
internal data class CommentRow(
    val id: String, val seq: Long = 0, val postId: String = "", val userId: String = "", val username: String = "",
    val displayName: String = "", val profileImageUrl: String = "", val text: String = "",
    val likesCount: Long = 0, val deleted: Boolean = false, val createdAt: String? = null, val editedAt: String? = null
)

@Serializable
internal data class NotificationRow(
    val id: String, val seq: Long = 0, val type: String = "", val senderId: String = "", val senderName: String = "",
    val senderProfileImageUrl: String = "", val postId: String = "", val targetId: String = "", val message: String = "",
    val isRead: Boolean = false, val createdAt: String? = null
)

@Serializable
internal data class SettingsRow(
    val storyAudience: String = "followers", val postAudience: String = "followers",
    val likes: Boolean = true, val comments: Boolean = true, val follows: Boolean = true, val showActivity: Boolean = true,
    val readReceipts: Boolean = true, val messages: Boolean = true, val community: Boolean = true, val requests: Boolean = true
)

@Serializable
internal data class StoryRow(
    val id: String, val seq: Long = 0, val userId: String = "", val mediaUrl: String = "", val mediaType: String = "image",
    val audience: String = "followers", val deleted: Boolean = false, val username: String = "",
    val profileImageUrl: String = "", val composition: JsonElement? = null, val likesCount: Long = 0,
    val createdAt: String? = null
)

@Serializable
internal data class HighlightRow(
    val id: String, val userId: String = "", val title: String = "", val media: JsonArray = JsonArray(emptyList()),
    val cover: Int = 0, val audience: String = "public"
)

@Serializable
internal data class ConversationRow(
    val id: String, val kind: String = "direct", val demo: Boolean = false, val title: String = "",
    val creatorId: String = "", val partyIds: List<String> = emptyList(), val memberIds: List<String> = emptyList(),
    val invitedIds: List<String> = emptyList(), val people: JsonObject = JsonObject(emptyMap()),
    val inviteNote: String = "", val preview: String = "", val lastSenderId: String = "",
    val communityKind: String? = null, val communityId: String? = null,
    val createdAt: String? = null, val updatedAt: String? = null
)

@Serializable
internal data class MessageRow(
    val id: String, val senderId: String = "", val text: String = "", val attachment: JsonObject? = null,
    val createdAt: String? = null, val unsentAt: String? = null
)

@Serializable
internal data class ChatSettingsRow(
    val conversationId: String, val nickname: String = "", val muted: Boolean = false,
    val hiddenAt: String? = null, val readAt: String? = null, val pinnedAt: String? = null, val markedUnread: Boolean = false
)

@Serializable
internal data class GroupRow(
    val id: String, val seq: Long = 0, val name: String = "", val description: String = "", val imageUrl: String = "",
    val category: String = "", val privacy: String = "", val ownerId: String = "", val ownerName: String = "",
    val ownerProfileImageUrl: String = "", val memberCount: Long = 0, val createdAt: String? = null
)

@Serializable
internal data class EventRow(
    val id: String, val seq: Long = 0, val title: String = "", val description: String = "", val imageUrl: String = "",
    val category: String = "", val startsAt: String? = null, val location: String = "", val organizer: String = "",
    val organizerId: String = "", val organizerName: String = "", val organizerProfileImageUrl: String = "",
    val participantLimit: Long? = null, val participantCount: Long = 0, val createdAt: String? = null,
    val talentFee: String? = null, val talentFeeBudget: String = "", val cancelledAt: String? = null
)

/** An event application; [event] is embedded when listing applications across events. */
@Serializable
internal data class EventApplicationRow(
    val eventId: String, val userId: String, val seq: Long = 0, val displayName: String = "",
    val profileImageUrl: String = "", val note: String = "", val status: String = "pending",
    val createdAt: String? = null, val event: EventSummaryRow? = null
)

@Serializable
internal data class EventSummaryRow(val title: String = "", val startsAt: String? = null, val organizerId: String = "")

/** A row of group_members, group_join_requests or event_participants. */
@Serializable
internal data class PersonRow(
    val userId: String, val seq: Long = 0, val displayName: String = "", val profileImageUrl: String = "",
    val role: String = "member"
)

@Serializable
internal data class GroupPostRow(
    val id: String, val seq: Long = 0, val groupId: String = "", val userId: String = "", val displayName: String = "",
    val profileImageUrl: String = "", val text: String = "", val imageUrl: String = "", val likesCount: Long = 0,
    val createdAt: String? = null, val editedAt: String? = null
)

@Serializable
internal data class OpportunityRow(
    val id: String, val seq: Long = 0, val seekerId: String = "", val title: String = "", val kind: String = "",
    val description: String = "", val category: String = "", val location: String = "", val deadline: String? = null,
    val status: String = "", val createdAt: String? = null
)

@Serializable
internal data class TalentRequestRow(
    val id: String, val seq: Long = 0, val kind: String = "", val sourceId: String = "", val seekerId: String = "",
    val creatorId: String = "", val title: String = "", val details: String = "", val status: String = "",
    val createdAt: String? = null
)

@Serializable
internal data class ProfileStatsRow(val followers: Long = 0, val following: Long = 0, val posts: Long = 0, val likes: Long = 0)

// Single-column selections.
@Serializable internal data class FollowerIdRow(val followerId: String)
@Serializable internal data class FollowingIdRow(val followingId: String)
@Serializable internal data class RequesterIdRow(val requesterId: String)
@Serializable internal data class BlockedIdRow(val blockedId: String)
@Serializable internal data class PostIdRow(val postId: String)
@Serializable internal data class MessageIdRow(val messageId: String)
@Serializable internal data class LikedRow(val liked: Boolean = false)
@Serializable internal data class IdRow(val id: String)
@Serializable internal data class EventIdRow(val eventId: String)
@Serializable internal data class EventStatusRow(val eventId: String, val status: String = "pending")
