package com.example.spotapp.ui.theme.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import coil3.compose.AsyncImage
import com.example.spotapp.data.cloudinaryVideoThumbnail
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull

/** One decoder/player for the entire social navigation tree, including outgoing animations. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class SpotVideoController(context: Context) {
    // Streams through the on-phone cache, and tries the next decoder when the phone's first choice fails to start.
    val player = ExoPlayer.Builder(context.applicationContext,
        DefaultRenderersFactory(context.applicationContext).setEnableDecoderFallback(true))
        .setMediaSourceFactory(DefaultMediaSourceFactory(SpotVideoCache.dataSourceFactory(context)))
        // Unplugging headphones pauses instead of playing out loud.
        .setHandleAudioBecomingNoisy(true)
        .build().apply { repeatMode = Player.REPEAT_MODE_ONE }
    var owner by mutableStateOf<Any?>(null); private set
    var buffering by mutableStateOf(false); private set
    var failure by mutableStateOf(false); private set
    var playing by mutableStateOf(false); private set
    var paused by mutableStateOf(false); private set
    /** Width / height of the clip that's loaded, or 0 until its first frame size is known. */
    var videoAspect by mutableFloatStateOf(0f); private set
    private var audible = false
    private var foreground = false
    private var scrubbing = false
    private val positions = linkedMapOf<String, Long>()
    private var currentUrl by mutableStateOf("")
    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlayerError(error: PlaybackException) { failure = true; buffering = false }
            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) videoAspect = size.width * size.pixelWidthHeightRatio / size.height
            }
            // A call, another app's music or unplugged headphones stopped the sound: show it paused, so one tap resumes.
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                        reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)) paused = true
            }
        })
    }
    fun claim(token: Any, url: String, startMs: Long = 0, endMs: Long = 0) {
        val playbackKey = playbackKey(url, startMs, endMs)
        player.pause()
        owner = token
        paused = false
        stopScrubbing()
        fast(false)
        if (currentUrl != playbackKey) {
            if (currentUrl.isNotEmpty()) positions[currentUrl] = player.currentPosition
            while (positions.size > 20) positions.remove(positions.keys.first())
            currentUrl = playbackKey; failure = false; videoAspect = 0f
            val clip = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs.coerceAtLeast(0))
            if (endMs > startMs) clip.setEndPositionMs(endMs)
            player.setMediaItem(MediaItem.Builder().setUri(url).setClippingConfiguration(clip.build()).build(), positions[playbackKey] ?: 0)
            player.prepare()
        }
        sync()
    }
    fun unclaim(token: Any) { if (owner === token) { owner = null; player.pause() } }
    fun foreground(value: Boolean) { foreground = value; sync() }
    /**
     * Sound on asks Android for the audio, so a call or another app's music pauses the reel. Muted feed
     * videos never ask, so scrolling past them doesn't stop the member's music.
     */
    fun audible(on: Boolean) {
        player.volume = if (on) 1f else 0f
        if (audible != on) { audible = on; player.setAudioAttributes(SpotVideoAudio, on) }
    }
    fun toggle() { paused = !paused; sync() }
    /** Jumps [ms] ahead (back when negative), staying inside the clip. */
    fun seekBy(ms: Long) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        player.seekTo((player.currentPosition + ms).coerceIn(0, (duration - 250).coerceAtLeast(0)))
    }
    fun fast(on: Boolean) { val speed = if (on) 2f else 1f; if (player.playbackParameters.speed != speed) player.setPlaybackSpeed(speed) }
    fun retry() { failure = false; player.prepare(); sync() }
    /** Shows the frame at [ms] while a finger drags the timeline. Paused, nearest-keyframe seeks keep the picture up with the finger. */
    fun scrub(ms: Long) {
        if (!scrubbing) { scrubbing = true; player.setSeekParameters(SeekParameters.CLOSEST_SYNC); sync() }
        player.seekTo(ms)
    }
    /** Lands exactly on [ms] when the finger lifts and plays on unless the viewer had paused. */
    fun endScrub(ms: Long) { stopScrubbing(); player.seekTo(ms); sync() }
    private fun stopScrubbing() { if (scrubbing) { scrubbing = false; player.setSeekParameters(SeekParameters.DEFAULT) } }
    /** True while some owner is watching this exact clip. */
    fun isShowing(url: String, startMs: Long = 0, endMs: Long = 0) = owner != null && currentUrl == playbackKey(url, startMs, endMs)
    private fun playbackKey(url: String, startMs: Long, endMs: Long) = if (startMs == 0L && endMs == 0L) url else "$url|$startMs|$endMs"
    private fun sync() { player.playWhenReady = foreground && owner != null && !paused && !failure && !scrubbing }
    fun release() { player.release() }
}

private val SpotVideoAudio = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build()

internal val LocalSpotVideo = staticCompositionLocalOf<SpotVideoController?> { null }
internal val LocalSpotVideoEnabled = compositionLocalOf { true }

@Composable
internal fun ProvideSpotVideo(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controller = remember { SpotVideoController(context) }
    DisposableEffect(controller, lifecycle) {
        fun update() = controller.foreground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, _ -> update() }
        lifecycle.addObserver(observer); update()
        onDispose { lifecycle.removeObserver(observer); controller.release() }
    }
    CompositionLocalProvider(LocalSpotVideo provides controller, content = content)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun SpotVideo(url: String, active: Boolean, modifier: Modifier = Modifier, muted: Boolean = true,
    onTap: (() -> Unit)? = null, onQualifiedView: (() -> Unit)? = null,
    clipStartMs: Long = 0, clipEndMs: Long = 0, fill: Boolean = false, showControls: Boolean = true,
    onDoubleTap: (() -> Unit)? = null, scrubber: Boolean = false, onHoldFast: ((Boolean) -> Unit)? = null,
    autoFill: Boolean = false, skip: Boolean = false) {
    val controller = LocalSpotVideo.current
    if (controller == null) {
        ProvideSpotVideo { SpotVideo(url, active, modifier, muted, onTap, onQualifiedView, clipStartMs, clipEndMs, fill, showControls, onDoubleTap, scrubber, onHoldFast, autoFill, skip) }
        return
    }
    val enabled = active && LocalSpotVideoEnabled.current
    val token = remember(url, clipStartMs, clipEndMs) { Any() }
    DisposableEffect(controller, token, enabled) {
        if (enabled) controller.claim(token, url, clipStartMs, clipEndMs)
        onDispose { controller.unclaim(token) }
    }
    val owns = enabled && controller.owner === token
    SideEffect { if (owns) controller.audible(!muted) }
    // [autoFill]: tall videos fill the screen edge to edge (cropping the sides a little), wide ones fit with bars,
    // as on TikTok and Instagram. The poster's shape decides until the video reports its own.
    var posterAspect by remember(url) { mutableFloatStateOf(0f) }
    val aspect = (if (owns) controller.videoAspect else 0f).takeIf { it > 0f } ?: posterAspect
    val crop = fill || (autoFill && aspect > 0f && aspect < .8f)
    var progress by remember(url) { mutableFloatStateOf(0f) }
    val latestView by rememberUpdatedState(onQualifiedView)
    LaunchedEffect(owns, controller.playing) {
        if (owns && controller.playing) { delay(2000); latestView?.invoke() }
    }
    LaunchedEffect(owns) {
        while (owns) {
            val duration = controller.player.duration
            progress = if (duration > 0) (controller.player.currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
            delay(250)
        }
    }
    Box(modifier.background(Color.Black).testTag(if (owns) "active-social-video" else "inactive-social-video"), contentAlignment = Alignment.Center) {
        AsyncImage(cloudinaryVideoThumbnail(url), "Video preview", Modifier.fillMaxSize(), contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
            onSuccess = { state -> state.painter.intrinsicSize.let { if (it.width > 0f && it.height > 0f) posterAspect = it.width / it.height } })
        val resize = if (crop) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
        if (owns) AndroidView(factory = { context -> PlayerView(context).apply {
            useController = false
            resizeMode = resize
            player = controller.player
            setKeepContentOnPlayerReset(true)
        } }, update = { it.player = controller.player; it.keepScreenOn = controller.playing; it.resizeMode = resize },
            onRelease = { it.player = null; it.keepScreenOn = false }, modifier = Modifier.fillMaxSize())
        // [skip]: double-tapping the left or right third jumps 5 seconds back or ahead; the middle keeps [onDoubleTap].
        // Taps in quick succession add up (10s, 15s…) in one bubble.
        var burst by remember(url) { mutableStateOf<SkipBurst?>(null) }
        fun skipBy(forward: Boolean) {
            if (!owns) return
            controller.seekBy(if (forward) 5_000 else -5_000)
            val last = burst
            burst = SkipBurst(forward, if (last != null && last.forward == forward) last.seconds + 5 else 5, (last?.id ?: 0) + 1)
        }
        LaunchedEffect(burst?.id) { if (burst != null) { delay(700); burst = null } }
        val latestTap by rememberUpdatedState(onTap)
        val latestDoubleTap by rememberUpdatedState(onDoubleTap)
        fun tap() { latestTap?.invoke() ?: run { if (owns) controller.toggle() } }
        // With a double-tap action, a single tap waits briefly to be sure it isn't the first of two.
        // With onHoldFast, a long press is claimed so letting go after 2x doesn't also pause.
        val gestures = if (skip) Modifier
            .semantics {
                onClick(if (onTap != null) "Open reel" else "Play or pause video") { tap(); true }
                customActions = listOf(CustomAccessibilityAction("Forward 5 seconds") { skipBy(true); true },
                    CustomAccessibilityAction("Back 5 seconds") { skipBy(false); true })
            }
            .pointerInput(owns, onHoldFast != null) {
                detectTapGestures(onTap = { tap() }, onLongPress = if (onHoldFast != null) { _ -> } else null, onDoubleTap = { at ->
                    when {
                        at.x < size.width / 3f -> skipBy(false)
                        at.x > size.width * 2f / 3f -> skipBy(true)
                        else -> latestDoubleTap?.invoke()
                    }
                })
            }
        else Modifier.combinedClickable(onClickLabel = if (onTap != null) "Open reel" else "Play or pause video",
            onLongClick = onHoldFast?.let { { } }, onDoubleClick = onDoubleTap, hapticFeedbackEnabled = false) { tap() }
        if (showControls) Box(Modifier.fillMaxSize().then(if (onHoldFast != null) Modifier.holdRightForFast(controller, owns, onHoldFast) else Modifier)
            .then(gestures))
        if (owns && controller.failure) TextButton(onClick = controller::retry) { Text("Video couldn't play. Retry", color = Color.White) }
        else if (owns && controller.buffering) CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
        else if (skip && showControls && owns && controller.paused) Row(Modifier.testTag("video-paused-controls"),
            horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            VideoRoundButton(Icons.Rounded.Replay5, "Back 5 seconds", 52.dp) { skipBy(false) }
            VideoRoundButton(Icons.Rounded.PlayArrow, "Play video", 72.dp) { controller.toggle() }
            VideoRoundButton(Icons.Rounded.Forward5, "Forward 5 seconds", 52.dp) { skipBy(true) }
        }
        else if (showControls && (!owns || controller.paused)) Icon(Icons.Rounded.PlayArrow, "Play video", Modifier.size(44.dp), tint = Color.White)
        burst?.let { shown ->
            Column(Modifier.align(if (shown.forward) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 28.dp)
                .size(88.dp).background(Color.Black.copy(alpha = .45f), CircleShape).testTag("video-skip-bubble"),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(if (shown.forward) Icons.Rounded.FastForward else Icons.Rounded.FastRewind, null, Modifier.size(28.dp), tint = Color.White)
                Text("${shown.seconds}s", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
        if (owns && showControls && scrubber) SpotVideoScrubber(url, Modifier.align(Alignment.BottomCenter).navigationBarsPadding(), clipStartMs, clipEndMs)
        else if (owns && showControls) LinearProgressIndicator(progress = { progress }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp),
            color = SpotVideoGold, trackColor = Color.White.copy(alpha = 0.2f))
    }
}

/** One run of double-tap skips in one direction: [seconds] so far, [id] restarts the bubble's timer. */
private data class SkipBurst(val forward: Boolean, val seconds: Int, val id: Int)

/** A dark round control over the video, legible on any frame. */
@Composable
private fun VideoRoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.size(size), shape = CircleShape, color = Color.Black.copy(alpha = .45f)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(size * .55f), tint = Color.White) }
    }
}

/** Like TikTok: pressing and holding the right half plays at 2x until the finger lifts. */
@Composable
private fun Modifier.holdRightForFast(controller: SpotVideoController, owns: Boolean, onFast: (Boolean) -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    val report by rememberUpdatedState(onFast)
    return pointerInput(controller, owns) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!owns || controller.paused || down.position.x < size.width / 2) return@awaitEachGesture
            // A tap, or a swipe to the next reel, ends the press before it counts as a hold.
            var ended = false
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation(); ended = true }
            if (ended) return@awaitEachGesture
            controller.fast(true); report(true)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            try {
                // Consumed so sliding the held finger doesn't scroll to another reel.
                do { val event = awaitPointerEvent(); event.changes.forEach { it.consume() } } while (event.changes.any { it.pressed })
            } finally { controller.fast(false); report(false) }
        }
    }
}

/**
 * A timeline that can be tapped or dragged to seek whichever [SpotVideo] is showing [url].
 * [compact] is the TikTok-style hairline: it thickens under the finger and shows a large time only while dragging.
 * [onScrubbing] reports when a drag starts and ends, so a screen can fade its overlays out of the way.
 */
@Composable
internal fun SpotVideoScrubber(url: String, modifier: Modifier = Modifier, clipStartMs: Long = 0, clipEndMs: Long = 0,
    compact: Boolean = false, onScrubbing: (Boolean) -> Unit = {}) {
    val controller = LocalSpotVideo.current ?: return
    if (!controller.isShowing(url, clipStartMs, clipEndMs)) return
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    // Where the finger is while dragging.
    var drag by remember { mutableStateOf<Float?>(null) }
    val dragging = drag != null
    val reportScrubbing by rememberUpdatedState(onScrubbing)
    DisposableEffect(dragging) { reportScrubbing(dragging); onDispose { if (dragging) reportScrubbing(false) } }
    LaunchedEffect(controller) {
        while (true) {
            position = controller.player.currentPosition
            duration = controller.player.duration.coerceAtLeast(0)
            delay(200)
        }
    }
    if (duration == 0L) return
    val fraction = drag ?: (position.toFloat() / duration).coerceIn(0f, 1f)
    fun ms(at: Float) = (at.coerceIn(0f, 1f) * duration).toLong()
    fun seek(to: Float) = controller.player.seekTo(ms(to))
    fun release() { drag?.let { controller.endScrub(ms(it)) }; drag = null }
    // The picture follows the finger, with a short gap between seeks so the decoder can show each frame.
    LaunchedEffect(controller, duration) {
        snapshotFlow { drag }.filterNotNull().conflate().collect { _ ->
            // Reads the latest drag, so a seek queued before the finger lifted can't restart scrubbing.
            drag?.let { controller.scrub(ms(it)) }
            delay(80)
        }
    }
    val elapsed = videoClock(ms(fraction))
    val total = videoClock(duration)
    // A paused compact bar shows its handle so it reads as draggable.
    val handle = !compact || dragging || controller.paused
    val track = Modifier
        .semantics {
            contentDescription = "Seek video"
            stateDescription = "$elapsed of $total"
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            setProgress { seek(it); true }
        }
        .pointerInput(duration) { detectTapGestures { seek(it.x / size.width) } }
        .pointerInput(duration) {
            detectHorizontalDragGestures(onDragStart = { drag = (it.x / size.width).coerceIn(0f, 1f) },
                onDragEnd = { release() }, onDragCancel = { release() }) { change, _ ->
                change.consume(); drag = (change.position.x / size.width).coerceIn(0f, 1f)
            }
        }
        .drawBehind {
            val y = size.height / 2
            val stroke = when { dragging -> 5.dp; handle -> 3.dp; else -> 2.dp }.toPx()
            drawLine(Color.White.copy(alpha = .3f), Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
            drawLine(SpotVideoGold, Offset(0f, y), Offset(size.width * fraction, y), stroke, StrokeCap.Round)
            if (handle) drawCircle(SpotVideoGold, (if (dragging) 8.dp else 5.dp).toPx(), Offset(size.width * fraction, y))
        }
    val shadow = Shadow(Color.Black.copy(alpha = .6f), blurRadius = 6f)
    if (compact) Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (dragging) Text(buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(elapsed) }
            withStyle(SpanStyle(color = Color.White.copy(alpha = .6f))) { append(" / $total") }
        }, color = Color.White, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum", shadow = shadow),
            maxLines = 1, modifier = Modifier.padding(bottom = 20.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(24.dp).then(track))
    } else Row(modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .4f))))
        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(32.dp).then(track))
        Text("$elapsed / $total", color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum", shadow = shadow),
            maxLines = 1, modifier = Modifier.padding(start = 10.dp))
    }
}

private val SpotVideoGold = Color(0xFFFDD87A)

private fun videoClock(ms: Long): String {
    val total = ms / 1000
    val (h, m, s) = Triple(total / 3600, total / 60 % 60, total % 60)
    val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
}
