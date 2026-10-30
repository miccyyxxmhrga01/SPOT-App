package com.example.spotapp.data

import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * [showActivity]: whether people you chat with see when you're online or typing (and you see them).
 * [readReceipts]: whether "Seen" is shared both ways in direct chats.
 * [likes] through [requests]: which phone alerts you get (supabase private.wants_push); Activity keeps everything.
 */
data class SpotPreferences(val storyAudience: String = "followers", val postAudience: String = "followers",
    val likes: Boolean = true, val comments: Boolean = true, val follows: Boolean = true, val showActivity: Boolean = true,
    val readReceipts: Boolean = true, val messages: Boolean = true, val community: Boolean = true, val requests: Boolean = true)

class PrivacyRepository {
    companion object { val shared by lazy { PrivacyRepository() } }
    private fun me() = Supabase.requireUid()
    private fun table(name: String) = Supabase.db.from(name)

    fun listenPreferences(result: (SpotPreferences) -> Unit, error: (String) -> Unit = {}): ListenerRegistration {
        val me = me()
        return Live.query(listOf(Watch("user_settings", "user_id", me)), {
            val s = table("user_settings").select { filter { eq("user_id", me) } }.decodeSingleOrNull<SettingsRow>() ?: SettingsRow()
            SpotPreferences(s.storyAudience, s.postAudience, s.likes, s.comments, s.follows, s.showActivity,
                s.readReceipts, s.messages, s.community, s.requests)
        }, result, { error("Settings couldn't load.") })
    }

    suspend fun savePreferences(p: SpotPreferences) {
        table("user_settings").upsert(buildJsonObject {
            put("user_id", me()); put("story_audience", p.storyAudience); put("post_audience", p.postAudience)
            put("likes", p.likes); put("comments", p.comments); put("follows", p.follows); put("show_activity", p.showActivity)
            put("read_receipts", p.readReceipts); put("messages", p.messages); put("community", p.community); put("requests", p.requests)
        }) { onConflict = "user_id" }
        Live.changed("user_settings")
    }

    /** Going private moves every public post, story and highlight to followers in one server step. */
    suspend fun setPrivate(private: Boolean) {
        rpc("set_private", buildJsonObject { put("p_private", private) })
        Live.changed("profiles", "posts", "stories", "highlights")
    }

    fun listenRequested(target: String, result: (Boolean) -> Unit): ListenerRegistration {
        val me = me()
        return Live.query(listOf(Watch("follow_requests", "requester_id", me)), {
            table("follow_requests").select(Columns.list("requester_id")) {
                filter { eq("requester_id", me); eq("target_id", target) }
            }.decodeList<RequesterIdRow>().isNotEmpty()
        }, result, {})
    }

    suspend fun requestFollow(target: String, request: Boolean) {
        require(target != me())
        rpc("request_follow", buildJsonObject { put("p_target", target); put("p_request", request) })
        Live.changed("follow_requests")
    }

    fun listenRequests(result: (List<String>) -> Unit, error: (String) -> Unit): ListenerRegistration {
        val me = me()
        return Live.query(listOf(Watch("follow_requests", "target_id", me)), {
            table("follow_requests").select(Columns.list("requester_id")) {
                filter { eq("target_id", me) }
                order("created_at", Order.DESCENDING); limit(50)
            }.decodeList<RequesterIdRow>().map { it.requesterId }
        }, result, { error("Requests couldn't load.") })
    }

    suspend fun reviewRequest(requester: String, accept: Boolean) {
        rpc("review_follow_request", buildJsonObject { put("p_requester", requester); put("p_accept", accept) })
        Live.changed("follow_requests", "follows")
    }

    fun listenConnections(target: String, followers: Boolean, result: (List<String>) -> Unit, error: (String) -> Unit): ListenerRegistration {
        val column = if (followers) "following_id" else "follower_id"
        return Live.query(listOf(Watch("follows", column, target)), {
            val rows = table("follows").select(Columns.list("follower_id", "following_id")) {
                filter { eq(column, target) }
                order("created_at", Order.DESCENDING); limit(100)
            }
            if (followers) rows.decodeList<FollowerIdRow>().map { it.followerId } else rows.decodeList<FollowingIdRow>().map { it.followingId }
        }, result, { error("Connections couldn't load.") })
    }

    fun listenBlocked(result: (List<String>) -> Unit, error: (String) -> Unit): ListenerRegistration {
        val me = me()
        return Live.query(listOf(Watch("blocks", "blocker_id", me)), {
            table("blocks").select(Columns.list("blocked_id")) { filter { eq("blocker_id", me) } }
                .decodeList<BlockedIdRow>().map { it.blockedId }
        }, result, { error("Blocked accounts couldn't load.") })
    }

    suspend fun unblock(id: String) {
        table("blocks").delete { filter { eq("blocker_id", me()); eq("blocked_id", id) } }
        // Their posts, stories, highlights and comments can show again.
        Live.changed("blocks", "posts", "stories", "highlights", "comments")
    }

    fun listenSaved(result: (List<String>) -> Unit, error: (String) -> Unit): ListenerRegistration {
        val me = me()
        return Live.query(listOf(Watch("saved_posts", "user_id", me)), {
            table("saved_posts").select(Columns.list("post_id")) {
                filter { eq("user_id", me) }
                order("created_at", Order.DESCENDING); limit(50)
            }.decodeList<PostIdRow>().map { it.postId }
        }, result, { error("Saved posts couldn't load.") })
    }
}
