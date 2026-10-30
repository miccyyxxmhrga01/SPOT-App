package com.example.spotapp.ui.theme.screens.home

import com.example.spotapp.ui.theme.components.LifecycleDisposableEffect

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.data.PageCursor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectedCommentsSheet(postId: String, profile: SpotUiProfile, onDismiss: () -> Unit,
    onProfileClick: (SpotUiProfile) -> Unit = {},
    // The post's owner can remove anyone's comment; with comments off, nobody can add one.
    postOwnerId: String = "", commentsOff: Boolean = false,
    repository: SocialRepository = SocialRepository.shared) {
    val pages = remember(postId) { mutableStateMapOf<Int, SocialPage<SocialComment>>() }
    val cursors = remember(postId) { mutableStateListOf<PageCursor?>(null) }
    val liked = remember(postId) { mutableStateMapOf<String, Boolean>() }
    val pendingLikes = remember(postId) { mutableStateListOf<String>() }
    var retry by remember(postId) { mutableIntStateOf(0) }
    var error by remember(postId) { mutableStateOf("") }
    var sending by remember(postId) { mutableStateOf(false) }
    var revision by remember(postId) { mutableIntStateOf(0) }
    var attemptId by rememberSaveable(postId) { mutableStateOf<String?>(null) }
    var attemptText by rememberSaveable(postId) { mutableStateOf<String?>(null) }
    var optimistic by remember(postId) { mutableStateOf<SpotUiComment?>(null) }
    val scope = rememberCoroutineScope()
    val removed = remember(postId) { mutableStateListOf<String>() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(60_000) } }
    cursors.forEachIndexed { i, cursor ->
        LifecycleDisposableEffect(postId, cursor, retry) {
            val listener = repository.listenComments(postId, cursor, { page ->
                pages[i] = page; error = ""
                if (i + 1 < cursors.size && page.cursor != null && cursors[i + 1]?.id != page.cursor.id) cursors[i + 1] = page.cursor
            }, { error = it })
            onDispose { listener.remove() }
        }
    }
    val comments = pages.toSortedMap().values.flatMap { it.items }.distinctBy { it.id }.sortedByDescending { it.createdAt }
    comments.forEach { c ->
        key(c.id) {
            LifecycleDisposableEffect(c.id, retry) {
                val listener = repository.listenCommentLike(postId, c.id, { liked[c.id] = it }, { error = it })
                onDispose { listener.remove() }
            }
        }
    }
    fun send(text: String) {
        if (sending) return
        val current = (attemptId?.takeIf { attemptText == text } ?: UUID.randomUUID().toString()) to text
        attemptId = current.first; attemptText = text; sending = true; error = ""
        optimistic = SpotUiComment(current.first, profile.username, profile.profileImageUrl, text, userId = profile.uid, pending = true)
        scope.launch {
            try { repository.sendComment(postId, current.first, text); attemptId = null; attemptText = null; revision++; optimistic = optimistic?.copy(pending = false) }
            catch (c: CancellationException) { throw c }
            catch (_: Exception) { optimistic = null; error = "Comment wasn't confirmed. Your text is kept; tap send to retry." }
            finally { sending = false }
        }
    }
    LaunchedEffect(comments) { if (comments.any { it.id == optimistic?.id }) optimistic = null }
    val rows = comments.filterNot { it.id in removed }.map { SpotUiComment(it.id, it.username.ifBlank { it.displayName }, it.profileImageUrl, it.text,
        postTimeLabel(it.createdAt, now), it.likesCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), liked[it.id] == true, it.userId,
        edited = it.edited) }
    val pending = optimistic?.takeIf { o -> rows.none { it.id == o.id } }
    val last = pages[cursors.lastIndex]
    HomeCommentsSheet(rememberModalBottomSheetState(skipPartiallyExpanded = true), listOfNotNull(pending) + rows, profile.profileImageUrl,
        onDismiss = onDismiss, onSendComment = ::send, onToggleLike = { id ->
            if (liked.containsKey(id) && id !in pendingLikes) {
                pendingLikes.add(id)
                scope.launch {
                    try { repository.setCommentLiked(postId, id, liked[id] != true) }
                    catch (c: CancellationException) { throw c }
                    catch (_: Exception) { error = "Couldn't update your like. Please retry." }
                    finally { pendingLikes.remove(id) }
                }
            }
        }, loading = pages.size < cursors.size && error.isBlank(), sending = sending, error = error, sendRevision = revision,
        currentUserId = profile.uid, onDelete = { id ->
            if (id !in removed) { removed.add(id); scope.launch {
                try { repository.removeComment(postId, id) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { removed.remove(id); error = "Couldn't delete comment. Retry." }
            } }
        }, postOwner = postOwnerId.isNotBlank() && postOwnerId == profile.uid, commentsOff = commentsOff,
        onEdit = { id, text ->
            if (!sending) {
                sending = true; error = ""
                scope.launch {
                    try { repository.editComment(id, text); revision++ }
                    catch (c: CancellationException) { throw c }
                    catch (e: Exception) { error = userMessage(e, "Your edit wasn't saved. Your text is kept; tap save to retry.") }
                    finally { sending = false }
                }
            }
        }, managedSend = true, onRetry = { error = ""; retry++ },
        onLoadMore = if (last?.hasMore == true) { { last.cursor?.let { if (cursors.none { c -> c?.id == it.id }) cursors.add(it) } } } else null,
        onProfileClick = { uid -> comments.firstOrNull { it.userId == uid }?.let {
            onDismiss(); onProfileClick(SpotUiProfile(uid, it.displayName, it.username, "", "", it.profileImageUrl))
        } })
}
