package com.example.spotapp.ui.theme.screens.home

import androidx.compose.material.icons.rounded.HideImage
import com.example.spotapp.ui.theme.screens.discover.CommunityWindow
import com.example.spotapp.ui.theme.components.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.compose.currentStateAsState

import android.content.Intent
import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.items
import com.example.spotapp.data.HomeRepository
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.example.spotapp.data.PublishedMedia
import com.example.spotapp.data.PublishedPost
import com.example.spotapp.data.SpotPostsRepository
import com.example.spotapp.data.Supabase
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.spotapp.ui.theme.models.SpotRoute
import com.example.spotapp.ui.theme.models.SpotUiPost
import com.example.spotapp.ui.theme.models.SpotUiProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.absoluteValue

private val FeedGold get() = SpotPalette.GoldDeep
private val FeedInk get() = SpotPalette.Ink
private val FeedMuted get() = SpotPalette.Muted
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotConnectedHomePage(
    profile: SpotUiProfile, post: SpotUiPost, storyImageUri: String?,
    onPostChanged: (SpotUiPost) -> Unit, onStoryChanged: (String) -> Unit,
    onNavigate: (SpotRoute) -> Unit, onProfileClick: (SpotUiProfile) -> Unit,
    onOpenReels: (SpotUiPost) -> Unit, showBottomNavigation: Boolean = true, onWorkOpen: (String) -> Unit = {},
    notificationCount: Int = 0, onNotifications: (() -> Unit)? = null,
    createStory: Boolean = false, onCreateStoryHandled: () -> Unit = {}, onChat: (String) -> Unit = {}
) {
    var popular by remember { mutableStateOf(HomeRepository.cachedPopular()) }
    var recent by remember { mutableStateOf(HomeRepository.cachedRecent()) }
    var followingPosts by remember { mutableStateOf(emptyList<PublishedPost>()) }
    var own by remember { mutableStateOf(HomeRepository.cachedProfile(profile.uid)) }
    var loadingPopular by remember { mutableStateOf(true) }
    var loadingFeed by remember { mutableStateOf(true) }
    var popularError by remember { mutableStateOf("") }
    var feedError by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    // Pull to refresh: reloads the feed, Popular and events, and the spinner stays until the feed answers.
    // Stories already update live, and reloading them would flash their placeholders.
    var refreshing by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<PublishedPost?>(null) }
    var commentsFor by remember { mutableStateOf<PublishedPost?>(null) }
    var reporting by remember { mutableStateOf<PublishedPost?>(null) }
    var storyOverlay by remember { mutableStateOf(false) }
    var connectedStoryOverlay by remember { mutableStateOf(false) }
    val hidden = remember { mutableStateListOf<String>() }
    // For you mixes everyone's recent posts with Popular and events; Following is only people you follow, and you.
    var following by rememberSaveable(profile.uid) { mutableStateOf(false) }
    var openedEvent by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val playback = remember { FeedPlayback() }
    val feed = (if (following) own + followingPosts else recent + own + followingPosts)
        .distinctBy { it.id }.sortedByDescending { it.createdAt }.filterNot { it.id in hidden }
    val feedPlaying = opened == null && commentsFor == null && reporting == null && !storyOverlay && !connectedStoryOverlay && openedEvent == null

    LifecycleDisposableEffect(profile.uid, retry) {
        loadingPopular = true
        loadingFeed = true
        popularError = ""
        feedError = ""
        val popularListener = HomeRepository.listenPopular(
            { popular = it; loadingPopular = false; popularError = "" },
            { popularError = it; loadingPopular = false })
        val recentListener = HomeRepository.listenRecent(
            { recent = it; loadingFeed = false; feedError = "" },
            { feedError = it; loadingFeed = false })
        val followingListener = HomeRepository.listenFollowingFeed({ followingPosts = it }, { feedError = it })
        val ownListener = SpotPostsRepository.listenOwn(onResult = { own = it }, onError = { feedError = it })
        onDispose { popularListener.remove(); recentListener.remove(); ownListener?.remove(); followingListener?.remove() }
    }
    LaunchedEffect(refreshing) {
        if (!refreshing) return@LaunchedEffect
        // A short minimum, so a fast answer still reads as a refresh rather than a flicker.
        delay(500)
        withTimeoutOrNull(15_000) { snapshotFlow { loadingFeed || loadingPopular }.first { !it } }
        refreshing = false
    }
    fun open(item: PublishedPost) {
        if (item.media.firstOrNull()?.isVideo == true) onOpenReels(item.asUiPost()) else opened = item
    }
    Box(Modifier.fillMaxSize().feedViewport(playback)) {
        SpotHomePage(profile, post, storyImageUri, onPostChanged, onStoryChanged, onNavigate, onProfileClick, onOpenReels,
            showBottomNavigation = showBottomNavigation, showPost = false,
            notificationCount = notificationCount, onNotificationsClick = onNotifications,
            refreshing = refreshing, onRefresh = { if (!refreshing) { refreshing = true; retry++ } },
            storiesContent = {
                PostUploadBanner(profile.uid)
                ConnectedStories(profile, { connectedStoryOverlay = it }, onProfileClick, createStory, onCreateStoryHandled)
            },
            contentAfterStories = {
                FeedSwitch(following) { following = it }
                if (!following) {
                    PopularStrip(popular, loadingPopular, popularError, { retry++ }, ::open)
                    HomeEventsRow(profile, retry) { openedEvent = it.eventId }
                }
                Spacer(Modifier.height(8.dp))
                SpotSectionHeader(if (following) "Following" else "Your community", icon = Icons.Rounded.Groups)
                when {
                    // Saved posts (from this phone's last visit) stay readable; a slim note says they may be out of date.
                    feedError.isNotBlank() && feed.isNotEmpty() -> Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .background(SpotPalette.Track, RoundedCornerShape(14.dp)).padding(start = 12.dp).testTag("feed-saved-notice"),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CloudOff, null, Modifier.size(18.dp), tint = SpotPalette.Muted)
                        Text("Showing saved posts. Check your connection.", Modifier.weight(1f).padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted)
                        TextButton({ retry++ }) { Text("Retry", color = SpotPalette.GoldInk, fontWeight = FontWeight.SemiBold) }
                    }
                    feedError.isNotBlank() -> SpotEmptyState(Icons.Rounded.CloudOff, "Your feed couldn't load", message = feedError,
                        actionLabel = "Retry", onAction = { retry++ }, actionIcon = Icons.Rounded.Refresh)
                    loadingFeed && feed.isEmpty() -> SpotLoading(true, SkeletonKind.POST)
                    feed.isEmpty() && following -> SpotEmptyState(Icons.Rounded.Groups, "Nobody to show yet",
                        message = "Posts from people you follow appear here.",
                        actionLabel = "Find creators", onAction = { onNavigate(SpotRoute.DISCOVER) }, actionIcon = Icons.Rounded.Search)
                    feed.isEmpty() -> SpotEmptyState(Icons.Rounded.DynamicFeed, "No posts yet",
                        message = "Share your talent or follow creators to fill your feed.",
                        actionLabel = "Create a post", onAction = { onNavigate(SpotRoute.NEW_POST) }, actionIcon = Icons.Rounded.Add)
                }
            }, feedContent = {
                items(feed, key = { it.id }) { item ->
                    ConnectedPostCard(item, playback, feedPlaying,
                        onProfileClick, { commentsFor = it }, { opened = it }, onOpenReels,
                        onHide = { hidden.add(item.id) },
                        onReport = if (item.author.uid == profile.uid) null else ({ reporting = item }),
                        modifier = Modifier.animateItem())
                    Spacer(Modifier.height(14.dp))
                }
            }, onOverlayVisible = { storyOverlay = it })
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    opened?.let { item ->
        ProfilePublishedPostViewer(item, onProfileClick = onProfileClick, onOpenReels = onOpenReels,
            profile = profile, onClose = { opened = null })
    }
    commentsFor?.let { item -> ConnectedCommentsSheet(item.id, profile, { commentsFor = null }, onProfileClick,
        postOwnerId = item.author.uid, commentsOff = item.commentsOff) }
    // A reported post leaves this feed straight away, like "Not interested".
    reporting?.let { item ->
        ReportPostPage(item, close = { reporting = null }) {
            reporting = null
            hidden.add(item.id)
            scope.launch { snackbar.showSnackbar("Thanks for reporting. SPOT's team will review it.") }
        }
    }
    openedEvent?.let { id ->
        com.example.spotapp.ui.theme.screens.discover.CommunityDetails(id, true, profile, close = { openedEvent = null },
            onProfile = { openedEvent = null; onProfileClick(it) }, onChat = { openedEvent = null; onChat(it) })
    }
}

@Composable
internal fun ProfilePublishedPostViewer(post: PublishedPost,
    onProfileClick: ((SpotUiProfile) -> Unit)? = null, onOpenReels: ((SpotUiPost) -> Unit)? = null,
    profile: SpotUiProfile? = null, onClose: () -> Unit) {
    var current by remember(post.id) { mutableStateOf(post) }
    var showComments by remember(post.id) { mutableStateOf(false) }
    var unavailable by remember(post.id) { mutableStateOf("") }
    LifecycleDisposableEffect(post.id) {
        val listener = HomeRepository.listenPost(post.id, {
            if (it != null) { current = it; unavailable = "" } else unavailable = "This post is no longer available."
        }, { unavailable = it })
        onDispose { listener.remove() }
    }
    if (unavailable.isNotBlank()) {
        CommunityWindow("Post", onClose) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                SpotEmptyState(Icons.Rounded.HideImage, "Post unavailable", message = unavailable, actionLabel = "Close", onAction = onClose)
            }
        }
        return
    }
    var liked by remember(post.id) { mutableStateOf<Boolean?>(null) }
    var pending by remember(post.id) { mutableStateOf(false) }
    var error by remember(post.id) { mutableStateOf("") }
    var options by remember(post.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LifecycleDisposableEffect(post.id) {
        val listener = SpotPostsRepository.listenInteraction(post.id) { liked = it }
        onDispose { listener?.remove() }
    }
    fun toggleLike() {
        if (!pending && liked != null) {
            pending = true
            scope.launch {
                try { SpotPostsRepository.setLiked(post.id, liked != true); error = "" }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = "Couldn't update your like. Please retry." }
                finally { pending = false }
            }
        }
    }
    PublishedPostViewer(current, liked == true, onMore = { options = true },
        likesHidden = current.likesHidden && current.author.uid != Supabase.uid(), onLike = ::toggleLike,
        // Double-tap only ever likes, like Instagram.
        onDoubleTapLike = { if (liked == false) toggleLike() }, onClose = onClose, onProfileClick = { onClose(); onProfileClick?.invoke(current.author) }, error = error,
        onComment = { showComments = true }, playbackEnabled = !showComments, onOpenReels = onOpenReels,
        onQualifiedView = {
        try { SpotPostsRepository.recordView(post.id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Reading a post does not depend on recording a view. */ }
    })
    if (showComments && profile != null) ConnectedCommentsSheet(post.id, profile, { showComments = false }, { author ->
        showComments = false; onClose(); onProfileClick?.invoke(author)
    }, postOwnerId = current.author.uid, commentsOff = current.commentsOff)
    // Deleting the post closes the viewer.
    if (options) PostOptionsSheet(current, { options = false },
        onProfile = onProfileClick?.let { open -> { author -> onClose(); open(author) } }, onDeleted = onClose)
}

@Composable
internal fun PopularPostsCarousel(posts: List<PublishedPost>, loading: Boolean, error: String,
    onRetry: () -> Unit, onOpen: (PublishedPost) -> Unit,
    playback: FeedPlayback? = null, playbackEnabled: Boolean = true, autoAdvanceMillis: Long = 6_000) {
    // The section is an auto-sliding carousel; with nothing popular yet it is hidden rather than showing a prompt.
    if (posts.isEmpty() && !loading && error.isBlank()) return
    Column(Modifier.fillMaxWidth().testTag("popular-section")) {
        SpotSectionHeader("Popular on SPOT", icon = Icons.Rounded.LocalFireDepartment)
        when {
            loading && posts.isEmpty() -> SpotLoading(true, SkeletonKind.CARDS)
            error.isNotBlank() && posts.isEmpty() -> FeedPlaceholder("Popular posts couldn't load", "Tap to try again.", "Retry", onRetry)
            else -> {
                val pager = rememberPagerState(pageCount = { posts.size })
                val dragged by pager.interactionSource.collectIsDraggedAsState()
                var held by remember { mutableStateOf(false) }
                var bounds by remember { mutableStateOf(Rect.Zero) }
                var clipped by remember { mutableStateOf(Rect.Zero) }
                val view = LocalView.current
                val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
                val inset = with(LocalDensity.current) { LocalSpotBottomContentPadding.current.toPx() }
                val viewport = playback?.viewport ?: Rect(0f, 0f, view.width.toFloat(), view.height.toFloat())
                val usable = Rect(viewport.left, viewport.top, viewport.right, (viewport.bottom - inset).coerceAtLeast(viewport.top))
                val onScreen = carouselVisible(bounds, clipped, usable)
                val enabled = playbackEnabled && LocalSpotVideoEnabled.current && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
                val canAdvance = enabled && onScreen && posts.size > 1 && !held && !dragged
                val timeout = LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(autoAdvanceMillis,
                    containsIcons = true, containsText = true, containsControls = true) ?: autoAdvanceMillis
                LaunchedEffect(canAdvance, pager.settledPage, posts.map { it.id }, timeout) {
                    if (canAdvance) {
                        delay(timeout)
                        if (!pager.isScrollInProgress && pager.pageCount > 1) {
                            pager.animateScrollToPage((pager.settledPage + 1) % pager.pageCount,
                                animationSpec = tween(650, easing = FastOutSlowInEasing))
                        }
                    }
                }
                HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 12.dp,
                    key = { posts[it].id }, modifier = Modifier.fillMaxWidth().testTag("popular-carousel")
                        .semantics { stateDescription = "Popular ${pager.settledPage + 1} of ${posts.size}" }
                        .onGloballyPositioned { bounds = Rect(it.positionInWindow(), it.size.toSize()); clipped = it.boundsInWindow() }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false); held = true
                                try { waitForUpOrCancellation() } finally { held = false }
                            }
                        }) { index ->
                    val item = posts[index]
                    Box(Modifier.fillMaxWidth().height(200.dp)
                        .graphicsLayer {
                            val distance = ((pager.currentPage - index) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                            scaleY = 1f - distance * 0.045f
                            alpha = 1f - distance * 0.12f
                        }.clip(RoundedCornerShape(22.dp)).background(if (SpotPalette.dark) SpotPalette.Track else Color(0xFFE9E1CB))
                        .clickable(role = Role.Button, onClickLabel = "Open post by ${item.author.displayName}") { onOpen(item) }) {
                        val media = item.media.first()
                        val videoId = "popular:${item.id}"
                        if (media.isVideo && playback != null) SpotVideo(media.url,
                            playbackEnabled && pager.settledPage == index && playback.activeId == videoId,
                            Modifier.fillMaxSize().then(if (pager.settledPage == index) Modifier.feedVideo(playback, videoId) else Modifier),
                            onTap = { onOpen(item) })
                        else MediaThumbnail(media, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)))))
                        Surface(Modifier.align(Alignment.TopStart).padding(12.dp), color = Color.Black.copy(alpha = 0.32f), shape = CircleShape) {
                            Icon(if (media.isVideo) Icons.Rounded.PlayArrow else Icons.Outlined.PhotoLibrary, if (media.isVideo) "Video" else "Photo",
                                Modifier.padding(7.dp).size(16.dp), tint = Color.White)
                        }
                        // Posts arrive ordered by popularity, so the page index is the rank.
                        Text("#${index + 1}", Modifier.align(Alignment.TopEnd).padding(12.dp)
                            .background(SpotGoldGradient, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                            color = SpotPalette.OnGold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        if (media.isVideo) Icon(Icons.Rounded.PlayCircle, "Play video", Modifier.align(Alignment.Center).size(52.dp), tint = Color.White.copy(alpha = .92f))
                        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SpotAvatar(item.author.profileImageUrl, null, Modifier.size(30.dp).border(1.5.dp, Color.White.copy(alpha = .85f), CircleShape), item.author.uid)
                                Spacer(Modifier.width(8.dp))
                                Text(item.author.displayName, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(item.caption.ifBlank { item.categories.joinToString(" · ") }, color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp))
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PopularStat(Icons.Rounded.Favorite, compactCount(item.likes), "${compactCount(item.likes)} ${if (item.likes == 1L) "like" else "likes"}")
                                PopularStat(Icons.Rounded.Visibility, compactCount(item.views), "${compactCount(item.views)} ${if (item.views == 1L) "view" else "views"}")
                            }
                        }
                    }
                }
                if (posts.size > 1) Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                    SpotPageIndicator(posts.size, pager.currentPage)
                }
            }
        }
    }
}

@Composable
private fun PopularStat(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, description: String) {
    Row(Modifier.background(SpotPalette.Surface.copy(alpha = .2f), CircleShape).padding(horizontal = 9.dp, vertical = 4.dp)
        .semantics(mergeDescendants = true) { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(13.dp), tint = Color.White)
        Text(value, Modifier.padding(start = 4.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun FeedPlaceholder(title: String, message: String, action: String, onClick: () -> Unit) {
    Surface(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), color = SpotPalette.Ivory,
        shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, if (SpotPalette.dark) SpotPalette.Line else Color(0xFFEAE1C8))) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = FeedInk)
            Spacer(Modifier.height(5.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = FeedMuted)
            TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) { Text(action, color = FeedGold) }
        }
    }
}

@Composable
private fun MediaThumbnail(media: PublishedMedia, modifier: Modifier = Modifier) {
    val source = media.previewUrl
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF938363), Color(0xFF39362F)))), contentAlignment = Alignment.Center) {
        Icon(if (media.isVideo) Icons.Rounded.PlayArrow else Icons.Outlined.PhotoLibrary, null, Modifier.size(36.dp), tint = Color.White.copy(alpha = 0.5f))
        if (source.isNotBlank()) AsyncImage(source, if (media.isVideo) "Video preview" else "Post photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun PublishedPostViewer(post: PublishedPost, liked: Boolean, onLike: () -> Unit, onClose: () -> Unit,
    onProfileClick: () -> Unit, error: String = "", onComment: () -> Unit = {}, playbackEnabled: Boolean = true,
    onOpenReels: ((SpotUiPost) -> Unit)? = null, onMore: (() -> Unit)? = null, likesHidden: Boolean = false,
    onDoubleTapLike: () -> Unit = {}, onQualifiedView: suspend () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var hearts by remember(post.id) { mutableIntStateOf(0) }
    var zoomed by remember(post.id) { mutableStateOf(false) }
    fun doubleTap() { hearts++; haptics.performHapticFeedback(HapticFeedbackType.LongPress); onDoubleTapLike() }
    val owner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val latestView by rememberUpdatedState(onQualifiedView)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(post.id, resumed) { if (resumed) { delay(2000); latestView() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        StorySystemBars()
        SpotScreenEntrance(Modifier.fillMaxSize(), fullscreen = true) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF151511)) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(end = if (onMore != null) 4.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close post", tint = Color.White) }
                    Column(Modifier.weight(1f).clickable(role = Role.Button, onClick = onProfileClick)) {
                        Text(post.author.displayName, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("@${post.author.username}" + if (post.edited) " · Edited" else "", color = Color.White.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (onMore != null) IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreHoriz, "More options", tint = Color.White) }
                }
                val pager = rememberPagerState(pageCount = { post.media.size })
                if (error.isNotBlank()) Text(error, color = Color.White, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                if (post.media.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f).padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(post.caption, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                } else Box(Modifier.fillMaxWidth().weight(1f)) {
                    LaunchedEffect(pager.settledPage) { zoomed = false }
                    // While a photo is zoomed, swipes pan it instead of changing photos.
                    HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed) { index ->
                        val media = post.media[index]
                        if (media.isVideo) key(media.url) { SpotVideo(media.url, playbackEnabled && resumed && pager.settledPage == index,
                            Modifier.fillMaxSize(), muted = false, scrubber = true,
                            onTap = onOpenReels?.let { open -> { onClose(); open(post.asUiPost().copy(mediaUri = media.url)) } },
                            onDoubleTap = ::doubleTap) }
                        else ZoomablePhoto(media.url, "Photo ${index + 1}", Modifier.fillMaxSize(), onDoubleTap = ::doubleTap,
                            onZoomed = { if (pager.settledPage == index) zoomed = it })
                    }
                    DoubleTapHeart(hearts, Modifier.align(Alignment.Center))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SpotAnimatedIconButton(if (liked) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                        if (liked) "Unlike post" else "Like post", onLike, tint = if (liked) Color(0xFFEF7070) else Color.White, selected = liked)
                    if (!likesHidden) Text(compactCount(post.likes), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    SpotAnimatedIconButton(androidx.compose.material.icons.Icons.Outlined.ChatBubbleOutline, "Comments", onComment, tint = Color.White)
                    Text(compactCount(post.comments), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.weight(1f))
                    SpotPageIndicator(post.media.size, pager.currentPage, Modifier.padding(horizontal = 8.dp), dark = true)
                }
                if (post.media.isNotEmpty()) Text(post.caption, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 18.dp))
            }
        }
    }
}
}

@Composable
internal fun PublishedVideo(url: String, active: Boolean) {
    SpotVideo(url, active, Modifier.fillMaxSize(), muted = false)
}

private fun compactCount(value: Long): String = when {
    value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000f)
    value >= 1_000 -> String.format("%.1fK", value / 1_000f)
    else -> value.toString()
}

/** Advance only when the media card is mostly visible above the floating navigation. */
internal fun carouselVisible(bounds: Rect, clipped: Rect, viewport: Rect): Boolean {
    if (bounds.width <= 0 || bounds.height <= 0 || !clipped.overlaps(viewport)) return false
    val visible = clipped.intersect(viewport)
    return visible.width >= bounds.width * .8f && visible.height >= bounds.height * .75f
}
