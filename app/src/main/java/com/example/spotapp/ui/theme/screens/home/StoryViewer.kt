package com.example.spotapp.ui.theme.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.components.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.UUID

private val QuickReactions = listOf("😂", "😮", "😍", "😢", "👏", "🔥")

/** One person's stories on their own, as highlights show them. */
@Composable
internal fun StoryViewer(items: List<StoryItem>, currentUid: String, close: () -> Unit, onSeen: (String) -> Unit = {},
    onProfile: (String) -> Unit = {}, onDelete: (suspend (String) -> Unit)? = null, highlight: Boolean = false,
    repository: StoryRepository = StoryRepository.shared) =
    StoryViewer(listOf(items), 0, currentUid, close, onSeen, onProfile, onDelete, highlight, repository = repository)

/**
 * Instagram-style stories: tap right or left for the next or previous story, hold to pause (the controls hide),
 * swipe sideways to the next person with a cube turn, swipe down to close, and swipe up or tap the bar to reply.
 * Each of [groups] is one person's stories, oldest first; [start] is the person whose circle was tapped.
 */
@Composable
internal fun StoryViewer(groups: List<List<StoryItem>>, start: Int, currentUid: String, close: () -> Unit,
    onSeen: (String) -> Unit = {}, onProfile: (String) -> Unit = {}, onDelete: (suspend (String) -> Unit)? = null,
    highlight: Boolean = false, seen: Collection<String> = emptyList(), repository: StoryRepository = StoryRepository.shared) {
    if (LocalSpotVideo.current == null) {
        ProvideSpotVideo { StoryViewer(groups, start, currentUid, close, onSeen, onProfile, onDelete, highlight, seen, repository) }
        return
    }
    if (groups.isEmpty()) return
    val pager = rememberPagerState(initialPage = start.coerceIn(groups.indices)) { groups.size }
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val dragY = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    // Going back from someone's first story opens the previous person's last one.
    var enteredFromEnd by remember { mutableStateOf<Int?>(null) }
    var replyRequests by remember { mutableIntStateOf(0) }
    fun dismiss() { if (!closing) { closing = true; visible = false; scope.launch { delay(180); close() } } }
    fun nextGroup(from: Int) { if (from < groups.lastIndex) scope.launch { pager.animateScrollToPage(from + 1) } else dismiss() }
    fun previousGroup(from: Int) { if (from > 0) { enteredFromEnd = from - 1; scope.launch { pager.animateScrollToPage(from - 1) } } }
    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(pager.settledPage) { if (enteredFromEnd != pager.settledPage) enteredFromEnd = null }

    Dialog(onDismissRequest = { dismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        StorySystemBars()
        AnimatedVisibility(visible, enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = .96f), exit = fadeOut(tween(150))) {
            Box(Modifier.fillMaxSize().background(Color(0xFF111111))
                .pointerInput(Unit) {
                    val closeAt = 140.dp.toPx(); val replyAt = 80.dp.toPx()
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            when { dragY.value > closeAt -> dismiss(); dragY.value < -replyAt -> replyRequests++ }
                            scope.launch { dragY.animateTo(0f) }
                        },
                        onDragCancel = { dragging = false; scope.launch { dragY.animateTo(0f) } },
                        onVerticalDrag = { change, amount -> change.consume(); scope.launch { dragY.snapTo(dragY.value + amount) } })
                }
                .graphicsLayer {
                    // Pulling down shrinks the story toward the bottom, like Instagram, before it closes.
                    val down = dragY.value.coerceAtLeast(0f)
                    translationY = down
                    val scale = 1f - (down / size.height).coerceIn(0f, 1f) * .2f
                    scaleX = scale; scaleY = scale
                }
                // The keyboard only lifts the reply bar, so the story itself doesn't jump.
                .windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets.ime))) {
                HorizontalPager(pager, Modifier.fillMaxSize(), key = { groups[it].firstOrNull()?.userId ?: it.toString() }) { page ->
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
                        cameraDistance = 12f * density
                        transformOrigin = TransformOrigin(if (offset > 0f) 1f else 0f, .5f)
                        rotationY = -90f * offset.coerceIn(-1f, 1f)
                    }) {
                        StoryPage(groups[page], currentUid, highlight, repository,
                            playing = visible && !dragging && page == pager.settledPage && !pager.isScrollInProgress,
                            startAtEnd = enteredFromEnd == page, seen = seen, hasPreviousGroup = page > 0,
                            replyRequests = replyRequests,
                            onSeen = onSeen, onProfile = onProfile, onDelete = onDelete,
                            onNextGroup = { nextGroup(page) }, onPreviousGroup = { previousGroup(page) }, onClose = ::dismiss)
                    }
                }
            }
        }
    }
}

/** One person's stories: progress, header, the story itself and the reply bar. */
@Composable
private fun StoryPage(items: List<StoryItem>, currentUid: String, highlight: Boolean, repository: StoryRepository,
    playing: Boolean, startAtEnd: Boolean, seen: Collection<String>, hasPreviousGroup: Boolean, replyRequests: Int,
    onSeen: (String) -> Unit, onProfile: (String) -> Unit, onDelete: (suspend (String) -> Unit)?,
    onNextGroup: () -> Unit, onPreviousGroup: () -> Unit, onClose: () -> Unit) {
    // Opens at this person's first story you haven't seen, or their last one when you came back to them.
    var index by rememberSaveable { mutableIntStateOf(if (startAtEnd) items.lastIndex else items.indexOfFirst { it.id !in seen }.coerceAtLeast(0)) }
    val item = items.getOrNull(index) ?: items.lastOrNull() ?: return
    var progress by remember(item.id) { mutableFloatStateOf(0f) }
    var holding by remember(item.id) { mutableStateOf(false) }
    var ready by remember(item.id) { mutableStateOf(false) }
    var failure by remember(item.id) { mutableStateOf(false) }
    var retry by remember(item.id) { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember(item.id) { mutableStateOf("") }
    var liked by remember(item.id, currentUid) { mutableStateOf(false) }
    var likeLoaded by remember(item.id, currentUid) { mutableStateOf(false) }
    var reacting by remember(item.id) { mutableStateOf(false) }
    var reactionError by remember(item.id) { mutableStateOf("") }
    var burst by remember(item.id) { mutableStateOf(false) }
    var reply by rememberSaveable { mutableStateOf("") }
    var replyId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var replying by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    val own = item.userId == currentUid
    val canReply = !own && !highlight && currentUid.isNotBlank()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val replyFocus = remember { FocusRequester() }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val controller = LocalSpotVideo.current!!
    val currentId by rememberUpdatedState(item.id)
    fun next() { if (index < items.lastIndex) index++ else onNextGroup() }
    fun previous() {
        when {
            index > 0 -> index--
            hasPreviousGroup -> onPreviousGroup()
            else -> { progress = 0f; controller.player.seekTo(0) }
        }
    }
    LaunchedEffect(burst) { if (burst) { delay(650); burst = false } }
    LaunchedEffect(sent) { if (sent) { delay(1_500); sent = false } }
    // Swiping up opens the reply keyboard, but only for a swipe made while this person's story is playing.
    var handledReplyRequest by remember { mutableIntStateOf(replyRequests) }
    LaunchedEffect(replyRequests, playing) {
        if (replyRequests == handledReplyRequest) return@LaunchedEffect
        handledReplyRequest = replyRequests
        if (playing && canReply) replyFocus.requestFocus()
    }
    LifecycleDisposableEffect(item.id, currentUid, retry, highlight) {
        val listener = if (!own && !highlight && currentUid.isNotBlank()) repository.listenLiked(item.id,
            { liked = it; likeLoaded = true; reactionError = "" }, { likeLoaded = false; reactionError = it }) else null
        onDispose { listener?.remove() }
    }
    LaunchedEffect(item.id, retry) {
        progress = 0f; ready = false; failure = false
        if (item.video) {
            snapshotFlow { controller.owner to controller.player.currentMediaItem?.localConfiguration?.uri.toString() }
                .first { it.first != null && it.second == item.mediaUrl }
            controller.player.seekTo(0)
        }
    }
    fun react() {
        if (own || highlight || reacting || !likeLoaded) return
        val before = liked; val desired = !liked
        val storyId = item.id
        liked = desired; reacting = true; reactionError = ""
        if (desired) burst = true
        scope.launch {
            try { withTimeout(30_000) { repository.setLiked(storyId, desired) } }
            catch (e: CancellationException) {
                if (e is TimeoutCancellationException) { liked = before; reactionError = "Reaction wasn't confirmed. Retry." } else throw e
            }
            catch (_: Exception) { liked = before; reactionError = "Reaction wasn't saved. Retry." }
            finally { reacting = false }
        }
    }
    fun sendReply(text: String, quick: Boolean = false) {
        val body = text.trim()
        if (body.isEmpty() || sending || !canReply) return
        val storyId = item.id
        // A retry after a lost answer reuses the id, so the reply is never sent twice.
        val messageId = if (quick) UUID.randomUUID().toString() else replyId
        sending = true; error = ""
        scope.launch {
            try {
                withTimeout(30_000) { repository.reply(storyId, messageId, body) }
                if (!quick) { reply = ""; replyId = UUID.randomUUID().toString() }
                focus.clearFocus(); sent = true
            }
            catch (e: CancellationException) { if (e is TimeoutCancellationException) error = "Reply wasn't confirmed. Retry." else throw e }
            catch (e: Exception) { error = userMessage(e, "Your reply wasn't sent. Retry.") }
            finally { sending = false }
        }
    }
    val latestNext by rememberUpdatedState({ next() })
    val running = playing && !holding && !replying && !confirmDelete && !reacting && reactionError.isBlank() &&
        lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(item.id, running, ready, retry) {
        if (!running) return@LaunchedEffect
        while (isActive) {
            if (!highlight && !item.active()) { latestNext(); break }
            if (item.video) {
                if (controller.playing && controller.player.currentMediaItem?.localConfiguration?.uri.toString() == item.mediaUrl) {
                    val duration = controller.player.duration
                    if (duration > 0) {
                        progress = (controller.player.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
                        if (controller.player.currentPosition >= duration - 120) { onSeen(item.id); latestNext(); break }
                    }
                }
            } else if (ready) {
                progress = (progress + 40f / item.composition.imageDurationMs).coerceAtMost(1f)
                if (progress >= 1f) { onSeen(item.id); latestNext(); break }
            }
            delay(40)
        }
    }
    // Holding hides everything but the story, like Instagram.
    val chrome by animateFloatAsState(if (holding) 0f else 1f, tween(160), label = "story-controls")

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(item.id to retry, transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) }, label = "Story") { target ->
            val shown = items.firstOrNull { it.id == target.first } ?: item
            StoryCanvas(shown.mediaUrl, shown.video, shown.composition, active = target.first == item.id && running,
                modifier = Modifier.fillMaxSize(), onReady = { if (target.first == currentId) ready = true }, onFailure = { if (target.first == currentId) failure = true })
        }
        // Soft shade behind the header and reply bar so they stay readable on bright stories.
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(120.dp).graphicsLayer { alpha = chrome }
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .45f), Color.Transparent))))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(140.dp).graphicsLayer { alpha = chrome }
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .55f)))))
        // While typing a reply the story dims, like Instagram.
        AnimatedVisibility(replying, Modifier.fillMaxSize(), enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)))
        }
        Box(Modifier.fillMaxSize().testTag("story-tap-area")
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Next story") { onSeen(item.id); next(); true },
                    CustomAccessibilityAction("Previous story") { previous(); true })
            }
            .pointerInput(item.id, replying) {
                detectTapGestures(onPress = { holding = true; try { tryAwaitRelease() } finally { holding = false } },
                    onTap = { position ->
                        when {
                            // With the keyboard open, a tap on the story just closes it.
                            replying -> focus.clearFocus()
                            position.x < size.width * .3f -> previous()
                            else -> { onSeen(item.id); next() }
                        }
                    })
            })
        Column(Modifier.fillMaxWidth().graphicsLayer { alpha = chrome }.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { items.forEachIndexed { i, _ ->
                LinearProgressIndicator(progress = { if (i < index) 1f else if (i == index) progress else 0f }, Modifier.weight(1f).height(2.5.dp),
                    color = Color.White, trackColor = Color.White.copy(alpha = .35f), drawStopIndicator = {})
            } }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                SpotAvatar(item.profileImageUrl, "Profile", Modifier.size(34.dp).clickable { onProfile(item.userId) }, item.userId)
                Text(item.username.ifBlank { if (own) "Your story" else "Story" },
                    Modifier.padding(start = 10.dp).weight(1f, fill = false).clickable { onProfile(item.userId) },
                    color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!highlight) Text(postTimeLabel(item.createdAt).removeSuffix(" ago"), Modifier.padding(start = 8.dp),
                    color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                Spacer(Modifier.weight(1f))
                if (own && onDelete != null) StoryTool(Icons.Rounded.DeleteOutline, "Delete story", { confirmDelete = true }, enabled = !deleting)
                StoryTool(Icons.Rounded.Close, "Close story", onClose, enabled = !deleting)
            }
        }
        AnimatedVisibility(burst, modifier = Modifier.align(Alignment.Center), enter = scaleIn(initialScale = .4f) + fadeIn(), exit = scaleOut() + fadeOut()) {
            Icon(Icons.Rounded.Favorite, null, Modifier.size(88.dp), tint = StoryGold)
        }
        AnimatedVisibility(sent, modifier = Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = .7f)) {
                Text("Sent", Modifier.padding(horizontal = 18.dp, vertical = 10.dp), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding().graphicsLayer { alpha = chrome }.padding(12.dp)) {
            if (error.isNotBlank() || reactionError.isNotBlank()) Text(error.ifBlank { reactionError }, Modifier.padding(bottom = 8.dp),
                color = StoryGold, style = MaterialTheme.typography.bodySmall)
            if (failure || controller.failure || reactionError.isNotBlank()) StoryTool(Icons.Rounded.Refresh, "Retry story", {
                retry++; error = ""; reactionError = ""; if (item.video) controller.retry()
            }, Modifier.padding(bottom = 8.dp))
            when {
                canReply -> StoryReplyBar(reply, { if (it != reply) { reply = it.take(1_000); replyId = UUID.randomUUID().toString() } },
                    replying, sending, liked, likeLoaded && !reacting, replyFocus,
                    onFocus = { replying = it }, onSend = { sendReply(reply) }, onQuick = { sendReply(it, quick = true) }, onLike = ::react)
                own && !highlight -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Favorite, "Story hearts", Modifier.size(22.dp), tint = StoryGold)
                    Text(item.likesCount.toString(), Modifier.padding(horizontal = 8.dp), color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    if (confirmDelete) SpotConfirmDialog(Icons.Rounded.DeleteOutline, "Delete story?", null, "Delete",
        onDismiss = { confirmDelete = false }, danger = true, busy = deleting) {
        if (!deleting) { deleting = true; scope.launch {
            try { onDelete?.invoke(item.id); confirmDelete = false; deleting = false; if (items.size == 1) onClose() else index = index.coerceAtMost(items.lastIndex - 1) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Couldn't delete. Retry."; confirmDelete = false }
            finally { deleting = false }
        } }
    }
}

/** "Send message" pill, quick reactions while it's open, and the like heart; replies go to the owner's chat. */
@Composable
private fun StoryReplyBar(text: String, onText: (String) -> Unit, focused: Boolean, sending: Boolean, liked: Boolean, likeEnabled: Boolean,
    focusRequester: FocusRequester, onFocus: (Boolean) -> Unit, onSend: () -> Unit, onQuick: (String) -> Unit, onLike: () -> Unit) {
    Column {
        AnimatedVisibility(focused && text.isBlank()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                QuickReactions.forEach { emoji ->
                    Text(emoji, Modifier.clip(CircleShape).clickable(enabled = !sending, onClickLabel = "Send $emoji") { onQuick(emoji) }
                        .padding(8.dp), style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).heightIn(min = 48.dp).border(1.dp, Color.White.copy(alpha = .75f), CircleShape)
                .padding(horizontal = 18.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(text, onText, Modifier.fillMaxWidth().focusRequester(focusRequester)
                    .onFocusChanged { onFocus(it.isFocused) }.testTag("story-reply"),
                    enabled = !sending, maxLines = 4, cursorBrush = SolidColor(StoryGold),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    decorationBox = { field ->
                        if (text.isEmpty()) Text("Send message", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        field()
                    })
            }
            if (text.isNotBlank()) TextButton(onSend, enabled = !sending, modifier = Modifier.padding(start = 4.dp).testTag("story-reply-send")) {
                Text(if (sending) "Sending…" else "Send", color = StoryGold, style = MaterialTheme.typography.titleSmall)
            }
            else IconButton(onLike, enabled = likeEnabled, modifier = Modifier.padding(start = 4.dp).size(48.dp).testTag("story-like")) {
                Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Unlike story" else "Like story",
                    Modifier.size(28.dp), tint = if (liked) StoryGold else Color.White)
            }
        }
    }
}
