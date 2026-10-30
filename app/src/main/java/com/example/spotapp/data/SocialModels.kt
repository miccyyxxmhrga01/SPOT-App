package com.example.spotapp.data

data class SocialComment(
    val id: String, val postId: String, val userId: String, val username: String,
    val displayName: String, val profileImageUrl: String, val text: String,
    val createdAt: Long, val likesCount: Long = 0, val edited: Boolean = false
) {
    companion object {
        internal fun from(row: CommentRow): SocialComment? {
            if (row.deleted || row.userId.isBlank()) return null
            return SocialComment(row.id, row.postId, row.userId, row.username, row.displayName, row.profileImageUrl,
                row.text, millis(row.createdAt).takeIf { it > 0 } ?: System.currentTimeMillis(),
                row.likesCount.coerceAtLeast(0), row.editedAt != null)
        }
    }
}

data class SocialNotification(
    val id: String, val type: String, val senderId: String, val senderName: String,
    val senderProfileImageUrl: String, val postId: String, val message: String,
    val createdAt: Long, val isRead: Boolean,
    /**
     * The event, group, invitation or opportunity this notification is about; blank for likes, comments and follows.
     * Invitations open in Messages, under Requests.
     */
    val targetId: String = ""
) {
    /** What tapping the notification opens. */
    val opens: NotificationTarget get() = when {
        targetId.isBlank() -> if (type == "follow" || postId.isBlank()) NotificationTarget.PROFILE else NotificationTarget.POST
        type == "event_join" || type.startsWith("event_application") -> NotificationTarget.EVENT
        type.startsWith("group_") -> NotificationTarget.GROUP
        type.startsWith("application") -> NotificationTarget.OPPORTUNITY
        else -> NotificationTarget.INVITATIONS
    }
    companion object {
        internal fun from(row: NotificationRow): SocialNotification? {
            if (row.type.isBlank() || row.senderId.isBlank()) return null
            return SocialNotification(row.id, row.type, row.senderId, row.senderName.ifBlank { "SPOT member" },
                row.senderProfileImageUrl, row.postId, row.message, millis(row.createdAt), row.isRead, row.targetId)
        }
    }
}

enum class NotificationTarget { PROFILE, POST, EVENT, GROUP, INVITATIONS, OPPORTUNITY }

data class SocialPage<T>(val items: List<T>, val cursor: PageCursor?, val hasMore: Boolean)
