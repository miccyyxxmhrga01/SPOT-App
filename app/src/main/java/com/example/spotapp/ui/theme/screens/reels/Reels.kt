package com.example.spotapp.ui.theme.screens.reels

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.models.*
import com.example.spotapp.ui.theme.screens.home.ConnectedCommentsSheet
import com.example.spotapp.ui.theme.screens.home.PostOptionsSheet
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.spotapp.ui.theme.screens.home.SocialAvatar
import com.example.spotapp.ui.theme.screens.messages.SharePostSheet
import com.example.spotapp.data.PageCursor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Suppress("UNUSED_PARAMETER")
@Composable
fun ReelsScreen(posts: List<SpotUiPost>, initialPostId: String? = null, onBack: () -> Unit,
    onPostChanged: (SpotUiPost) -> Unit = {}, onComment: (SpotUiPost) -> Unit = {},
    profile: SpotUiProfile? = null, onProfileClick: (SpotUiProfile) -> Unit = {}) {
    BackHandler(onBack = onBack)
    ReelSystemBars()
    val context = LocalContext.current
    val pages = remember(initialPostId) { mutableStateMapOf<Int, SocialPage<PublishedPost>>() }
    val cursors = remember(initialPostId) { mutableStateListOf<PageCursor?>(null) }
    var first by remember(initialPostId) { mutableStateOf<PublishedPost?>(null) }
    var firstResolved by remember(initialPostId) { mutableStateOf(initialPostId == null) }
    var error by remember(initialPostId) { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    var commentPost by remember { mutableStateOf<PublishedPost?>(null) }
    var muted by rememberSaveable { mutableStateOf(false) }
    // Counts mute taps, so each one flashes the new sound state over the video.
    var muteFlash by remember { mutableIntStateOf(0) }
    // Like TikTok, dragging the timeline clears the screen down to the video and its time.
    var scrubbing by remember { mutableStateOf(false) }
    val chrome by animateFloatAsState(if (scrubbing) 0f else 1f, label = "reels chrome")
    var savedPage by rememberSaveable(initialPostId) { mutableIntStateOf(0) }
    var restored by remember(initialPostId) { mutableStateOf(false) }
    LifecycleDisposableEffect(initialPostId, retry) {
        val listener = initialPostId?.let { id -> HomeRepository.listenPost(id, {
            first = it?.takeIf { p -> p.media.any { m -> m.isVideo } }; firstResolved = true
            if (it == null) error = "This reel is no longer available."
        }, { error = it; firstResolved = true }) }
        onDispose { listener?.remove() }
    }
    cursors.forEachIndexed { index, cursor ->
        LifecycleDisposableEffect(initialPostId, cursor, retry) {
            val listener = HomeRepository.listenVideos(cursor, { page ->
                pages[index] = page
                if (index + 1 < cursors.size && page.cursor != null && cursors[index + 1]?.id != page.cursor.id) cursors[index + 1] = page.cursor
            }, { error = it })
            onDispose { listener.remove() }
        }
    }
    val feed = (listOfNotNull(first) + pages.toSortedMap().values.flatMap { it.items })
        .distinctBy { it.id }.filter { it.media.any { m -> m.isVideo } }
    val readyFeed = if (firstResolved) feed else emptyList()
    // The tapped tile already has the video, so it starts right away while the reel's details load.
    val opening = posts.firstOrNull { it.id == initialPostId }?.mediaUri?.takeIf { !firstResolved && isPostVideoUrl(it) }
    val pager = rememberPagerState(pageCount = { readyFeed.size })
    val last = pages[cursors.lastIndex]
    fun loadMore() { last?.cursor?.let { if (cursors.none { c -> c?.id == it.id }) cursors.add(it) } }
    LaunchedEffect(readyFeed.size, last, restored) {
        if (!restored && readyFeed.isNotEmpty()) {
            if (savedPage >= readyFeed.size && last?.hasMore == true) loadMore()
            else { pager.scrollToPage(savedPage.coerceAtMost(readyFeed.lastIndex)); restored = true }
        }
    }
    LaunchedEffect(pager.settledPage, restored) { if (restored) savedPage = pager.settledPage }
    LaunchedEffect(pager.settledPage, readyFeed.size, last?.cursor?.id) {
        if (pager.settledPage >= readyFeed.size - 3 && last?.hasMore == true) loadMore()
    }
    // While one reel plays, the start of the next one downloads, so swiping to it starts without a wait.
    val nextUrl = readyFeed.getOrNull(pager.settledPage + 1)?.media?.firstOrNull { it.isVideo }?.url
    LaunchedEffect(nextUrl) { if (nextUrl != null) SpotVideoCache.prefetch(context, nextUrl) }
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("reels-screen")) {
        if (readyFeed.isNotEmpty()) VerticalPager(pager, Modifier.fillMaxSize(), key = { readyFeed[it].id }, beyondViewportPageCount = 1) { index ->
            val post = readyFeed[index]
            val selectedUrl = posts.firstOrNull { it.id == initialPostId }?.mediaUri
            val media = post.media.firstOrNull { post.id == initialPostId && it.url == selectedUrl && it.isVideo }
                ?: post.media.first { it.isVideo }
            ReelPage(post, media.url, restored && pager.settledPage == index && commentPost == null,
                muted, { commentPost = post }, onProfileClick) { scrubbing = it }
        } else if (opening != null) SpotVideo(opening, true, Modifier.fillMaxSize(), muted, autoFill = true)
        else Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val loading = !firstResolved || (pages.isEmpty() && error.isBlank())
            Box(Modifier.size(84.dp).background(ReelGold.copy(alpha = .12f), CircleShape).border(1.dp, ReelGold.copy(alpha = .35f), CircleShape),
                contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator(Modifier.size(30.dp), color = ReelGold, strokeWidth = 2.5.dp)
                else Icon(if (error.isBlank()) Icons.Rounded.VideoLibrary else Icons.Rounded.CloudOff, null, Modifier.size(34.dp), tint = ReelGold)
            }
            Text(when { loading -> "Loading reels"; error.isBlank() -> "No reels yet"; else -> "Reels couldn't load" },
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 16.dp))
            if (!loading) Text(error.ifBlank { "Videos shared on SPOT will show up here." }, style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = .72f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            if (!loading && error.isNotBlank()) SpotPillButton("Retry", { error = ""; retry++ }, Modifier.padding(top = 18.dp),
                icon = Icons.Rounded.Refresh, compact = true)
        }
        // A soft scrim keeps the top controls readable over bright videos.
        Box(Modifier.fillMaxWidth().graphicsLayer { alpha = chrome }.background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .55f), Color.Transparent)))
            .statusBarsPadding().height(76.dp))
        Row(Modifier.fillMaxWidth().graphicsLayer { alpha = chrome }.statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ReelCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Text("Reels", style = MaterialTheme.typography.titleLarge.copy(shadow = ReelTextShadow), fontWeight = FontWeight.Bold,
                color = Color.White, modifier = Modifier.weight(1f).padding(start = 8.dp))
            if (readyFeed.isNotEmpty()) ReelCircleButton(if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                if (muted) "Unmute" else "Mute", { muted = !muted; muteFlash++ })
        }
        MuteFlash(muteFlash, muted, Modifier.align(Alignment.Center))
        if (error.isNotBlank() && readyFeed.isNotEmpty()) Surface(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp, start = 16.dp, end = 16.dp),
            shape = CircleShape, color = Color.Black.copy(alpha = .72f), border = BorderStroke(1.dp, ReelGold.copy(alpha = .45f))) {
            Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CloudOff, null, Modifier.size(16.dp), tint = ReelGold)
                Text(error, style = MaterialTheme.typography.bodySmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(horizontal = 8.dp))
                TextButton({ error = ""; retry++ }) { Text("Retry", color = ReelGold, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
    commentPost?.let { item ->
        if (profile != null) ConnectedCommentsSheet(item.id, profile, { commentPost = null }, onProfileClick,
            postOwnerId = item.author.uid, commentsOff = item.commentsOff)
        else LaunchedEffect(item.id) { onComment(item.asUiPost()); commentPost = null }
    }
}

private val ReelGold = Color(0xFFFDD87A)
private val ReelTextShadow = Shadow(Color.Black.copy(alpha = .55f), Offset(0f, 1f), blurRadius = 8f)

@Composable
internal fun ReelPage(post: PublishedPost, url: String, active: Boolean, muted: Boolean,
    onComment: () -> Unit, onProfile: (SpotUiProfile) -> Unit, onScrubbing: (Boolean) -> Unit) {
    var scrubbing by remember { mutableStateOf(false) }
    val chrome by animateFloatAsState(if (scrubbing) 0f else 1f, label = "reel chrome")
    var fast by remember { mutableStateOf(false) }
    var liked by remember(post.id) { mutableStateOf<Boolean?>(null) }
    var saved by remember(post.id) { mutableStateOf(false) }
    var pending by remember(post.id) { mutableStateOf(false) }
    var error by remember(post.id) { mutableStateOf("") }
    // Null until known; the Follow button only shows for people you don't follow yet.
    var following by remember(post.author.uid) { mutableStateOf<Boolean?>(null) }
    var expandedCaption by remember(post.id) { mutableStateOf(false) }
    var captionOverflows by remember(post.id) { mutableStateOf(false) }
    var sharing by remember(post.id) { mutableStateOf(false) }
    var options by remember(post.id) { mutableStateOf(false) }
    val own = post.author.uid == Supabase.uid()
    var hearts by remember(post.id) { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LifecycleDisposableEffect(post.id) {
        val like = SpotPostsRepository.listenInteraction(post.id) { liked = it }
        val save = SocialRepository.shared.listenSaved(post.id) { saved = it }
        val me = Supabase.uid()
        val follow = if (me != null && me != post.author.uid && post.author.uid.isNotBlank())
            SocialRepository.shared.listenFollowing(post.author.uid, { following = it }, { }) else null
        onDispose { like?.remove(); save.remove(); follow?.remove() }
    }
    fun action(block: suspend () -> Unit) {
        if (pending) return
        pending = true
        scope.launch {
            try { block(); error = "" }
            catch (c: CancellationException) { throw c }
            catch (_: Exception) { error = "Couldn't save this action. Please retry." }
            finally { pending = false }
        }
    }
    Box(Modifier.fillMaxSize()) {
        // Double-tap likes (never unlikes), with a heart burst, like other video apps.
        SpotVideo(url, active, Modifier.fillMaxSize(), muted, autoFill = true, onQualifiedView = {
            scope.launch { try { SpotPostsRepository.recordView(post.id) } catch (c: CancellationException) { throw c } catch (_: Exception) { } }
        }, onDoubleTap = {
            hearts++; haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            if (liked == false) action { SpotPostsRepository.setLiked(post.id, true) }
        }, onHoldFast = { fast = it },
            skip = true)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, .55f to Color.Transparent, 1f to Color.Black.copy(alpha = .82f))))
        DoubleTapHeart(hearts, Modifier.align(Alignment.Center))
        Column(Modifier.align(Alignment.BottomStart).graphicsLayer { alpha = chrome }.navigationBarsPadding()
            .padding(start = 16.dp, end = 80.dp, bottom = 30.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f, fill = false).clip(RoundedCornerShape(26.dp)).clickable(onClickLabel = "Open profile") { onProfile(post.author) }
                    .padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SocialAvatar(post.author.profileImageUrl, "${post.author.displayName} profile",
                        Modifier.size(42.dp).border(2.dp, SpotGoldGradient, CircleShape).padding(3.dp).clip(CircleShape), post.author.uid)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(post.author.displayName, color = Color.White, style = MaterialTheme.typography.titleMedium.copy(shadow = ReelTextShadow),
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(post.author.username.takeIf { it.isNotBlank() }?.let { "@$it" }, postTimeLabel(post.createdAt),
                            post.views.takeIf { it > 0 }?.let { "${spotCompactCount(it)} views" }).joinToString(" · "),
                            color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall.copy(shadow = ReelTextShadow),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (following == false) Surface(onClick = { action { SocialRepository.shared.setFollowing(post.author.uid, true) } },
                    enabled = !pending, shape = CircleShape, color = Color.Transparent, border = BorderStroke(1.5.dp, Color.White.copy(alpha = .85f)),
                    modifier = Modifier.padding(start = 4.dp).heightIn(min = 34.dp).testTag("reel-follow")) {
                    Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text("Follow", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (post.categories.isNotEmpty()) Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                post.categories.forEach { category ->
                    Text(category, color = ReelGold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.background(Color.Black.copy(alpha = .35f), CircleShape).border(1.dp, ReelGold.copy(alpha = .55f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 3.dp))
                }
            }
            if (post.caption.isNotBlank()) Column(Modifier.padding(top = 8.dp).animateContentSize()
                .clickable(enabled = captionOverflows || expandedCaption, onClickLabel = if (expandedCaption) "Show less" else "Show more") { expandedCaption = !expandedCaption }) {
                Text(post.caption, color = Color.White, style = MaterialTheme.typography.bodyMedium.copy(shadow = ReelTextShadow),
                    maxLines = if (expandedCaption) 12 else 2, overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expandedCaption) captionOverflows = it.hasVisualOverflow })
                if (captionOverflows || expandedCaption) Text(if (expandedCaption) "Show less" else "more", color = Color.White.copy(alpha = .75f),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            if (post.tags.isNotEmpty()) Text(post.tags.joinToString(" ") { "#$it" }, color = ReelGold,
                style = MaterialTheme.typography.bodySmall.copy(shadow = ReelTextShadow), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp))
            if (error.isNotBlank()) Text(error, color = ReelGold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
        Column(Modifier.align(Alignment.BottomEnd).graphicsLayer { alpha = chrome }.navigationBarsPadding().padding(end = 6.dp, bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // A hidden like count shows to the owner only.
            ReelAction(if (liked == true) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder, "Like",
                if (post.likesHidden && !own) "Like" else spotCompactCount(post.likes),
                tint = if (liked == true) SpotPalette.Heart else Color.White, selected = liked == true, enabled = !pending) {
                if (liked != null) action { SpotPostsRepository.setLiked(post.id, liked != true) }
            }
            ReelAction(Icons.Outlined.ChatBubbleOutline, "Comments", spotCompactCount(post.comments), onClick = onComment)
            ReelAction(Icons.AutoMirrored.Outlined.Send, "Share", "Share") { sharing = true }
            ReelAction(if (saved) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder, if (saved) "Unsave" else "Save",
                if (saved) "Saved" else "Save", tint = if (saved) ReelGold else Color.White, selected = saved, enabled = !pending) {
                action { SocialRepository.shared.setSaved(post.id, !saved) }
            }
            ReelAction(Icons.Rounded.MoreHoriz, "More options", "More") { options = true }
        }
        SpotVideoScrubber(url, Modifier.align(Alignment.BottomCenter).navigationBarsPadding(), compact = true,
            onScrubbing = { scrubbing = it; onScrubbing(it) })
        if (fast) Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp)
            .background(Color.Black.copy(alpha = .55f), CircleShape).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("2x", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Icon(Icons.Rounded.FastForward, null, Modifier.padding(start = 4.dp).size(18.dp), tint = Color.White)
        }
    }
    if (sharing) SharePostSheet(post.id, post.author.displayName, post.caption, post.media.firstOrNull()?.previewUrl.orEmpty(), onMoreApps = {
        sharing = false
        runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${post.author.displayName} on SPOT: ${post.caption}\n$url")
        }, "Share SPOT reel")) }.onFailure { error = "No sharing app is available." }
    }, onDismiss = { sharing = false })
    // A deleted reel leaves the list when the feed reloads.
    if (options) PostOptionsSheet(post, { options = false }, onProfile = onProfile)
}

/**
 * Reels are black edge to edge: light status-bar icons, and black instead of the page colour behind the
 * system buttons. Both go back to the app's theme when Reels closes.
 */
@Composable
private fun ReelSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatus = bars?.isAppearanceLightStatusBars
        val lightNavigation = bars?.isAppearanceLightNavigationBars
        val background = window?.decorView?.background
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
        onDispose {
            if (lightStatus != null) bars.isAppearanceLightStatusBars = lightStatus
            if (lightNavigation != null) bars.isAppearanceLightNavigationBars = lightNavigation
            if (background != null) window.setBackgroundDrawable(background)
        }
    }
}

/** A frosted round control that stays legible on any video. */
@Composable
private fun ReelCircleButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(48.dp)) {
        Box(Modifier.size(42.dp).background(Color.Black.copy(alpha = .35f), CircleShape).border(1.dp, Color.White.copy(alpha = .18f), CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(icon, label, Modifier.size(22.dp), tint = Color.White)
        }
    }
}

/**
 * A side-rail action: a large icon on a soft shadow (no hard disc, so the video shows through) and its count below.
 * The icon pops when it turns on or off.
 */
@Composable
private fun ReelAction(icon: ImageVector, label: String, caption: String, tint: Color = Color.White, selected: Boolean = false,
    enabled: Boolean = true, onClick: () -> Unit) {
    val pulse = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected != previous) {
            previous = selected
            pulse.snapTo(if (selected) 1.25f else .9f)
            pulse.animateTo(1f, spring(dampingRatio = .5f, stiffness = 600f))
        }
    }
    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
        .padding(horizontal = 4.dp, vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).background(Brush.radialGradient(listOf(Color.Black.copy(alpha = .32f), Color.Transparent)), CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(icon, label, Modifier.size(31.dp).graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }, tint = tint)
        }
        Text(caption, color = Color.White, style = MaterialTheme.typography.labelLarge.copy(shadow = ReelTextShadow), fontWeight = FontWeight.Bold,
            maxLines = 1)
    }
}

/** "Sound off" or "Sound on" over the video for a moment after the mute button is tapped. */
@Composable
private fun MuteFlash(trigger: Int, muted: Boolean, modifier: Modifier = Modifier) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        alpha.snapTo(1f); delay(650); alpha.animateTo(0f, tween(220))
    }
    if (alpha.value > 0f) Column(modifier.graphicsLayer { this.alpha = alpha.value }.size(96.dp).background(Color.Black.copy(alpha = .5f), CircleShape),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, null, Modifier.size(32.dp), tint = Color.White)
        Text(if (muted) "Sound off" else "Sound on", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

