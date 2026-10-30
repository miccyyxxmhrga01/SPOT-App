package com.example.spotapp.data

import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.request.SelectRequestBuilder

/** Feeds, reels and profile grids. Row level security decides which posts each member can see. */
object HomeRepository {
    private val posts get() = Supabase.db.from("posts")
    fun currentUserId(): String? = Supabase.uid()
    private var cacheUid: String? = null
    private var recentCache = emptyList<PublishedPost>()
    private var popularCache = emptyList<PublishedPost>()
    private val profileCache = linkedMapOf<String, List<PublishedPost>>()
    private fun checkCacheAccount() {
        if (cacheUid != currentUserId()) {
            cacheUid = currentUserId(); recentCache = emptyList(); popularCache = emptyList(); profileCache.clear()
        }
    }
    fun cachePublished(post: PublishedPost) {
        checkCacheAccount()
        if (post.author.uid != currentUserId()) return
        profileCache[post.author.uid] = (listOf(post) + profileCache[post.author.uid].orEmpty()).distinctBy { it.id }.take(30)
        if (post.audience == "public") recentCache = (listOf(post) + recentCache).distinctBy { it.id }.take(30)
    }
    /** Drops a deleted post from every cached list, so it doesn't flash back before the next load. */
    fun forget(id: String) {
        recentCache = recentCache.filterNot { it.id == id }
        popularCache = popularCache.filterNot { it.id == id }
        profileCache.replaceAll { _, posts -> posts.filterNot { it.id == id } }
    }
    // On a fresh start, the feed this phone saved last time (StartupCache) shows until new posts load.
    fun cachedRecent(): List<PublishedPost> { checkCacheAccount(); if (recentCache.isEmpty()) recentCache = saved("recent"); return recentCache }
    fun cachedPopular(): List<PublishedPost> { checkCacheAccount(); if (popularCache.isEmpty()) popularCache = saved("popular"); return popularCache }
    private fun saved(name: String) = cacheUid?.let { uid -> StartupCache.feed(uid, name).mapNotNull(PostDocumentMapper::published) }.orEmpty()
    fun cachedProfile(uid: String): List<PublishedPost> { checkCacheAccount(); return profileCache[uid].orEmpty() }

    fun listenVideos(after: PageCursor? = null,
        onResult: (SocialPage<PublishedPost>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        Live.query(listOf(Watch("posts")), {
            val rows = fetchPosts {
                filter { eq("audience", "public"); eq("media_type", "video"); if (after != null) lt("seq", after.position) }
                order("seq", Order.DESCENDING); limit(30)
            }
            SocialPage(rows.mapNotNull(PostDocumentMapper::published), rows.lastOrNull()?.let { PageCursor(it.id, it.seq) }, rows.size == 30)
        }, onResult, { onError("Reels couldn't load. Please retry.") })

    fun listenPost(id: String, onResult: (PublishedPost?) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        Live.query(listOf(Watch("posts")), {
            fetchPosts { filter { eq("id", id) } }.firstOrNull()?.let(PostDocumentMapper::published)
        }, onResult, { onError("This post is unavailable or you no longer have access.") })

    fun listenRecent(onResult: (List<PublishedPost>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        listenPublished(listOf(Watch("posts")), {
            filter { eq("audience", "public") }
            order("seq", Order.DESCENDING); limit(30)
        }, { checkCacheAccount(); recentCache = it; onResult(it) }, onError, saveAs = "recent")

    fun listenPopular(onResult: (List<PublishedPost>) -> Unit, onError: (String) -> Unit): ListenerRegistration =
        listenPublished(listOf(Watch("posts")), {
            filter { eq("audience", "public"); eq("has_media", true) }
            order("popularity_score", Order.DESCENDING); order("seq", Order.DESCENDING); limit(12)
        }, { checkCacheAccount(); popularCache = it; onResult(it) }, onError, saveAs = "popular")

    /** Pinned posts first, then the newest. Followers-only posts appear once you follow; row level security applies that rule. */
    fun listenProfilePosts(uid: String, onResult: (List<PublishedPost>) -> Unit,
        onError: (String) -> Unit = {}): ListenerRegistration {
        val watches = listOfNotNull(Watch("posts", "user_id", uid), currentUserId()?.let { Watch("follows", "follower_id", it) })
        return listenPublished(watches, {
            filter { eq("user_id", uid) }
            order("pinned_at", Order.DESCENDING, nullsFirst = false); order("seq", Order.DESCENDING); limit(30)
        }, {
            checkCacheAccount(); profileCache[uid] = it
            while (profileCache.size > 5) profileCache.remove(profileCache.keys.first())
            onResult(it)
        }, { profileCache.remove(uid); onResult(emptyList()); onError(it) })
    }

    /** Posts that mention [uid], newest first, as far as the viewer may see them. */
    fun listenTaggedPosts(uid: String, onResult: (List<PublishedPost>) -> Unit, onError: (String) -> Unit = {}): ListenerRegistration =
        listenPublished(listOf(Watch("posts")), {
            filter { contains("tagged_people", listOf(uid)) }
            order("seq", Order.DESCENDING); limit(30)
        }, onResult, onError)

    fun listenFollowingFeed(onResult: (List<PublishedPost>) -> Unit, onError: (String) -> Unit): ListenerRegistration? {
        val me = currentUserId() ?: return null
        return Live.query(listOf(Watch("follows", "follower_id", me), Watch("posts")), {
            val following = Supabase.db.from("follows").select(Columns.list("following_id")) {
                filter { eq("follower_id", me) }
                order("created_at", Order.DESCENDING); limit(30)
            }.decodeList<FollowingIdRow>().map { it.followingId }
            if (following.isEmpty()) emptyList()
            else fetchPosts {
                filter { isIn("user_id", following); eq("audience", "followers") }
                order("seq", Order.DESCENDING); limit(30)
            }.mapNotNull(PostDocumentMapper::published)
        }, { if (me == currentUserId()) onResult(it) }, { onError("Following posts couldn't load.") })
    }

    /** [saveAs] keeps the latest rows on this phone (StartupCache) for the next start. */
    private fun listenPublished(watches: List<Watch>, query: SelectRequestBuilder.() -> Unit,
        onResult: (List<PublishedPost>) -> Unit, onError: (String) -> Unit, saveAs: String? = null): ListenerRegistration {
        val account = currentUserId()
        return Live.query(watches, {
            val rows = fetchPosts(query)
            if (saveAs != null && account != null) StartupCache.saveFeed(account, saveAs, rows)
            rows.mapNotNull(PostDocumentMapper::published)
        }, { result ->
            if (account == currentUserId()) onResult(result)
        }, { if (account == currentUserId()) onError("Posts couldn't load. Check your connection and retry.") })
    }

    internal suspend fun fetchPosts(query: SelectRequestBuilder.() -> Unit): List<PostRow> =
        posts.select(Columns.raw("*, author:profiles!posts_user_id_fkey(account_type)"), request = query).decodeList()

    fun listenToCurrentUser(onResult: (SpotUser?) -> Unit): ListenerRegistration? =
        currentUserId()?.let { listenToUser(it, onResult) }

    fun listenToUser(uid: String, onResult: (SpotUser?) -> Unit): ListenerRegistration =
        ProfileRepository.shared.listen(uid, onResult) { onResult(null) }
}
