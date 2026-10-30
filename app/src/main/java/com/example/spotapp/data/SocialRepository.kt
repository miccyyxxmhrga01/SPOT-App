package com.example.spotapp.data

import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Comments, notifications, follows and saves. Counters and notifications change together on the server. */
class SocialRepository {
    companion object { val shared by lazy { SocialRepository() } }
    private fun uid() = Supabase.requireUid()
    private fun table(name: String) = Supabase.db.from(name)

    fun listenComments(postId: String, after: PageCursor? = null,
        result: (SocialPage<SocialComment>) -> Unit, error: (String) -> Unit): ListenerRegistration =
        Live.query(listOf(Watch("comments", "post_id", postId)), {
            val rows = table("comments").select {
                filter { eq("post_id", postId); eq("deleted", false); if (after != null) lt("seq", after.position) }
                order("seq", Order.DESCENDING); limit(50)
            }.decodeList<CommentRow>()
            SocialPage(rows.mapNotNull(SocialComment::from), rows.lastOrNull()?.let { PageCursor(it.id, it.seq) }, rows.size == 50)
        }, result, { error("Comments couldn't load. Please retry.") })

    fun listenCommentLike(postId: String, commentId: String, result: (Boolean) -> Unit,
        error: (String) -> Unit): ListenerRegistration {
        val me = uid()
        return Live.query(listOf(Watch("comment_likes", "user_id", me)), {
            table("comment_likes").select(Columns.list("comment_id")) {
                filter { eq("comment_id", commentId); eq("user_id", me) }
            }.decodeList<JsonObject>().isNotEmpty()
        }, result, { error("Comment likes couldn't load. Please retry.") })
    }

    suspend fun sendComment(postId: String, commentId: String, text: String) {
        val body = text.trim()
        require(body.isNotEmpty() && body.length <= 2000) { "Write a comment of 1–2,000 characters." }
        uid()
        rpc("add_comment", buildJsonObject { put("p_post_id", postId); put("p_comment_id", commentId); put("p_text", body) })
        Live.changed("comments", "posts")
    }

    /** The writer changes their comment's text; it shows as edited. */
    suspend fun editComment(commentId: String, text: String) {
        val body = text.trim()
        require(body.isNotEmpty() && body.length <= 2000) { "Write a comment of 1–2,000 characters." }
        rpc("edit_comment", buildJsonObject { put("p_comment_id", commentId); put("p_text", body) })
        Live.changed("comments")
    }

    /** The writer, or the post's owner, removes a comment. */
    suspend fun removeComment(postId: String, commentId: String) {
        rpc("delete_comment", buildJsonObject { put("p_post_id", postId); put("p_comment_id", commentId) })
        Live.changed("comments", "posts")
    }

    suspend fun setCommentLiked(postId: String, commentId: String, liked: Boolean) {
        rpc("set_comment_liked", buildJsonObject { put("p_comment_id", commentId); put("p_liked", liked) })
        Live.changed("comment_likes", "comments")
    }

    fun listenNotifications(after: PageCursor? = null, result: (SocialPage<SocialNotification>) -> Unit,
        error: (String) -> Unit): ListenerRegistration {
        val me = uid()
        return Live.query(listOf(Watch("notifications", "recipient_id", me)), {
            val rows = table("notifications").select {
                filter { eq("recipient_id", me); if (after != null) lt("seq", after.position) }
                order("seq", Order.DESCENDING); limit(50)
            }.decodeList<NotificationRow>()
            SocialPage(rows.mapNotNull(SocialNotification::from), rows.lastOrNull()?.let { PageCursor(it.id, it.seq) }, rows.size == 50)
        }, result, { error("Notifications couldn't load. Please retry.") })
    }

    fun listenUnread(result: (Int) -> Unit): ListenerRegistration {
        val me = uid()
        return Live.query(listOf(Watch("notifications", "recipient_id", me)), {
            table("notifications").select(Columns.list("id")) {
                filter { eq("recipient_id", me); eq("is_read", false) }
                limit(100)
            }.decodeList<IdRow>().size
        }, result, {})
    }

    suspend fun markRead(id: String, read: Boolean = true) {
        table("notifications").update({ set("is_read", read) }) { filter { eq("recipient_id", uid()); eq("id", id) } }
        Live.changed("notifications")
    }

    suspend fun markAllRead() {
        table("notifications").update({ set("is_read", true) }) { filter { eq("recipient_id", uid()); eq("is_read", false) } }
        Live.changed("notifications")
    }

    fun listenFollowing(target: String, result: (Boolean) -> Unit, error: (String) -> Unit): ListenerRegistration {
        val me = uid()
        return Live.query(listOf(Watch("follows", "follower_id", me)), { isFollowing(me, target) },
            result, { error("Follow status couldn't load.") })
    }

    /** Which of [targets] you follow, kept current. */
    fun listenFollowingAmong(targets: List<String>, result: (Set<String>) -> Unit): ListenerRegistration {
        val me = uid()
        return Live.query(listOf(Watch("follows", "follower_id", me)), {
            targets.distinct().chunked(100).flatMap { group ->
                table("follows").select(Columns.list("following_id")) { filter { eq("follower_id", me); isIn("following_id", group) } }
                    .decodeList<FollowingIdRow>().map { it.followingId }
            }.toSet()
        }, result, {})
    }

    suspend fun setFollowing(target: String, following: Boolean) {
        require(target != uid()) { "You cannot follow yourself." }
        rpc("set_following", buildJsonObject { put("p_target", target); put("p_following", following) })
        Live.changed("follows")
    }

    suspend fun counts(target: String): Pair<Long, Long> {
        val stats = rpc("profile_stats", buildJsonObject { put("p_user_id", target) }).decodeList<ProfileStatsRow>().firstOrNull()
            ?: ProfileStatsRow()
        return stats.followers to stats.following
    }

    fun listenSaved(id: String, result: (Boolean) -> Unit): ListenerRegistration {
        val me = uid()
        return Live.query(listOf(Watch("saved_posts", "user_id", me)), {
            table("saved_posts").select(Columns.list("post_id")) { filter { eq("user_id", me); eq("post_id", id) } }
                .decodeList<PostIdRow>().isNotEmpty()
        }, result, {})
    }

    suspend fun setSaved(id: String, saved: Boolean) {
        val me = uid()
        if (saved) table("saved_posts").upsert(buildJsonObject { put("user_id", me); put("post_id", id) }) {
            onConflict = "user_id,post_id"; ignoreDuplicates = true
        }
        else table("saved_posts").delete { filter { eq("user_id", me); eq("post_id", id) } }
        Live.changed("saved_posts")
    }
}

internal suspend fun isFollowing(follower: String, target: String): Boolean =
    Supabase.db.from("follows").select(Columns.list("following_id")) {
        filter { eq("follower_id", follower); eq("following_id", target) }
    }.decodeList<FollowingIdRow>().isNotEmpty()
