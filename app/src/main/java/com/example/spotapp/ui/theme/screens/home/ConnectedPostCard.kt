package com.example.spotapp.ui.theme.screens.home

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.models.SpotUiPost
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.messages.SharePostSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ConnectedPostCard(post: PublishedPost, playback: FeedPlayback, enabled: Boolean,
    onProfile: (SpotUiProfile) -> Unit, onComments: (PublishedPost) -> Unit, onImage: (PublishedPost) -> Unit,
    onReel: (SpotUiPost) -> Unit, onHide: () -> Unit = {}, onReport: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    var liked by remember(post.id) { mutableStateOf<Boolean?>(null) }
    var saved by remember(post.id) { mutableStateOf(false) }
    var pending by remember(post.id) { mutableStateOf(false) }
    var error by remember(post.id) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var sharing by remember(post.id) { mutableStateOf(false) }
    var options by remember(post.id) { mutableStateOf(false) }
    val own = post.author.uid == Supabase.uid()
    LifecycleDisposableEffect(post.id) {
        val like = SpotPostsRepository.listenInteraction(post.id) { liked = it }
        val save = SocialRepository.shared.listenSaved(post.id) { saved = it }
        onDispose { like?.remove(); save.remove() }
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
    Column(modifier) {
        HomePostCard(post.asUiPost(liked == true).copy(saved = saved, likesHidden = post.likesHidden && !own), onProfileClick = { onProfile(post.author) },
            onLike = { if (liked != null) action { SpotPostsRepository.setLiked(post.id, liked != true) } },
            onComment = { onComments(post) }, onShare = { sharing = true }, onSave = { action { SocialRepository.shared.setSaved(post.id, !saved) } },
            onNotInterested = onHide, onReport = onReport,
            onOpenReels = { onReel(post.asUiPost(liked == true)) },
            // Double-tap only ever likes, like Instagram; the heart shows either way.
            mediaContent = { if (post.media.isNotEmpty()) FeedPostMedia(post, playback, enabled, onImage, onReel,
                onDoubleTapLike = { if (liked == false) action { SpotPostsRepository.setLiked(post.id, true) } }) },
            onMore = { options = true })
        if (error.isNotBlank()) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    }
    if (sharing) SharePostSheet(post.id, post.author.displayName, post.caption, post.media.firstOrNull()?.previewUrl.orEmpty(), onMoreApps = {
        sharing = false
        runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${post.author.displayName} on SPOT: ${post.caption}\n${SpotLinks.post(post.id)}")
        }, "Share SPOT post")) }.onFailure { error = "No sharing app is available." }
    }, onDismiss = { sharing = false })
    // A deleted post leaves the feed straight away, like "Not interested".
    if (options) PostOptionsSheet(post, { options = false }, onProfile = onProfile, onNotInterested = onHide,
        onReport = onReport, onDeleted = onHide)
}

@Composable
internal fun FeedPostMedia(post: PublishedPost, playback: FeedPlayback, enabled: Boolean,
    onImage: (PublishedPost) -> Unit, onReel: (SpotUiPost) -> Unit, onDoubleTapLike: () -> Unit = {}) {
    val pager = rememberPagerState(pageCount = { post.media.size })
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var hearts by remember(post.id) { mutableIntStateOf(0) }
    fun doubleTap() { hearts++; haptics.performHapticFeedback(HapticFeedbackType.LongPress); onDoubleTapLike() }
    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(if (SpotPalette.dark) SpotPalette.Track else Color(0xFFF1EEE6))) {
        HorizontalPager(pager, modifier = Modifier.fillMaxSize()) { index ->
            val media = post.media[index]
            val id = "feed:${post.id}:$index"
            if (media.isVideo) SpotVideo(media.url, enabled && pager.settledPage == index && playback.activeId == id,
                modifier = Modifier.fillMaxSize().then(if (pager.settledPage == index) Modifier.feedVideo(playback, id) else Modifier),
                muted = FeedSound.muted,
                onTap = { onReel(post.asUiPost().copy(mediaUri = media.url)) }, onQualifiedView = {
                    scope.launch { try { SpotPostsRepository.recordView(post.id) } catch (c: CancellationException) { throw c } catch (_: Exception) { } }
                }, onDoubleTap = ::doubleTap)
            // Pinch to zoom like Instagram; a tap opens the post, a double-tap likes it.
            else PinchZoomPhoto(media.url, "Photo ${index + 1}", Modifier.fillMaxSize(), onTap = { onImage(post) }, onDoubleTap = ::doubleTap)
        }
        DoubleTapHeart(hearts, Modifier.align(Alignment.Center))
        if (post.media.size > 1) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
            .background(Color.Black.copy(alpha = .25f), androidx.compose.foundation.shape.CircleShape).padding(horizontal = 9.dp, vertical = 7.dp)) {
            SpotPageIndicator(post.media.size, pager.currentPage, dark = true)
        }
        if (post.media.size > 1) Surface(Modifier.align(Alignment.TopEnd).padding(12.dp), color = Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.large) {
            Text("${pager.currentPage + 1}/${post.media.size}", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
        }
        if (post.media.getOrNull(pager.currentPage)?.isVideo == true) FeedSoundButton(Modifier.align(Alignment.BottomEnd).padding(4.dp))
    }
}

/** The speaker on a feed video: one tap turns sound on or off for every feed video ([FeedSound]). */
@Composable
private fun FeedSoundButton(modifier: Modifier = Modifier) {
    val muted = FeedSound.muted
    IconButton({ FeedSound.muted = !muted }, modifier.size(48.dp).testTag("feed-sound")) {
        // The light rim keeps it visible over the black bars of portrait videos.
        Box(Modifier.size(32.dp).background(Color.Black.copy(alpha = .45f), CircleShape).border(1.dp, Color.White.copy(alpha = .3f), CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, if (muted) "Turn sound on" else "Turn sound off",
                Modifier.size(18.dp), tint = Color.White)
        }
    }
}
