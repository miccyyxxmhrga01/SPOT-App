package com.example.spotapp.ui.theme.screens.home

import com.example.spotapp.ui.theme.components.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CommentsDisabled
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SpotUiComment(val id: String, val username: String, val avatarUrl: String? = null,
    val text: String, val timeLabel: String = "now", val likes: Int = 0, val liked: Boolean = false,
    val userId: String = "", val pending: Boolean = false, val edited: Boolean = false)

/** One tap adds a reaction to the draft, the way people usually answer a performance. */
private val QuickReactions = listOf("❤️", "🔥", "👏", "😍", "🎉", "💯", "😂")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeCommentsSheet(sheetState: SheetState, comments: List<SpotUiComment>, currentUserAvatarUrl: String?,
    onDismiss: () -> Unit, onSendComment: (String) -> Unit, onToggleLike: (String) -> Unit,
    loading: Boolean = false, sending: Boolean = false, error: String = "", sendRevision: Int = 0,
    managedSend: Boolean = false, onRetry: () -> Unit = {}, onLoadMore: (() -> Unit)? = null,
    onProfileClick: (String) -> Unit = {}, currentUserId: String = "", onDelete: ((String) -> Unit)? = null,
    /** The viewer owns the post, so they can delete anyone's comment on it. */
    postOwner: Boolean = false, commentsOff: Boolean = false,
    /** Saves the viewer's edit of one of their comments (id, new text). */
    onEdit: ((String, String) -> Unit)? = null) {
    var draft by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(sendRevision) { if (sendRevision > 0) { draft = ""; editingId = null } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = SpotPalette.Ivory,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().fillMaxHeight(0.8f).testTag("connected-comments")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Comments", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SpotPalette.Ink)
                if (comments.isNotEmpty()) Text(if (onLoadMore != null) "${comments.size}+" else "${comments.size}",
                    style = MaterialTheme.typography.labelLarge, color = SpotPalette.GoldInk,
                    modifier = Modifier.padding(start = 10.dp).background(SpotPalette.GoldSoft, CircleShape).padding(horizontal = 10.dp, vertical = 2.dp))
            }
            if (loading && comments.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth(), color = SpotPalette.GoldDeep, trackColor = SpotPalette.Track)
            else HorizontalDivider(color = SpotPalette.Line)
            if (error.isNotBlank()) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                .background(SpotPalette.Danger.copy(alpha = .07f), RoundedCornerShape(14.dp)).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(18.dp), tint = SpotPalette.Danger)
                Text(error, style = MaterialTheme.typography.bodySmall, color = SpotPalette.Danger, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                TextButton(onClick = onRetry) { Text("Retry", color = SpotPalette.Danger, fontWeight = FontWeight.SemiBold) }
            }
            if (comments.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                SpotLoading(loading, SkeletonKind.ROWS) {
                    SpotEmptyState(Icons.Rounded.ChatBubbleOutline, "No comments yet", message = "Be the first to share your thoughts.")
                }
            } else LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(vertical = 6.dp)) {
                items(comments, key = { it.id }) { c ->
                    val own = currentUserId.isNotBlank() && c.userId == currentUserId
                    CommentRow(c, own, onDelete.takeIf { (own || postOwner) && !c.pending },
                        onEdit?.takeIf { own && !c.pending && !commentsOff }?.let { { draft = c.text; editingId = c.id } },
                        onProfileClick, onToggleLike, Modifier.animateItem())
                }
                if (onLoadMore != null) item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        SpotPillButton("Load older comments", onLoadMore, icon = Icons.Rounded.ExpandMore, style = SpotButtonStyle.Secondary, compact = true)
                    }
                }
            }
            if (commentsOff) Surface(color = SpotPalette.Surface, shadowElevation = 10.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp).testTag("comments-off"),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CommentsDisabled, null, Modifier.size(20.dp), tint = SpotPalette.Muted)
                    Text("Comments are turned off for this post.", Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted)
                }
            } else Surface(color = SpotPalette.Surface, shadowElevation = 10.dp) {
                Column {
                    editingId?.let {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp).testTag("comment-editing"),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Editing your comment", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold, color = SpotPalette.GoldInk)
                            TextButton(onClick = { editingId = null; draft = "" }, enabled = !sending) { Text("Cancel", color = SpotPalette.Muted) }
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        QuickReactions.forEach { emoji ->
                            Text(emoji, fontSize = 22.sp, modifier = Modifier.clip(CircleShape)
                                .clickable(enabled = !sending, onClickLabel = "Add $emoji") { if (draft.length + emoji.length <= 2000) draft += emoji }
                                .padding(horizontal = 9.dp, vertical = 5.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        SocialAvatar(currentUserAvatarUrl, "Your profile", Modifier.size(36.dp), currentUserId)
                        Spacer(Modifier.width(10.dp))
                        OutlinedTextField(draft, { if (it.length <= 2000) draft = it }, Modifier.weight(1f),
                            enabled = !sending, placeholder = { Text("Add a comment…", style = MaterialTheme.typography.bodyMedium) },
                            textStyle = MaterialTheme.typography.bodyMedium, maxLines = 4, shape = RoundedCornerShape(24.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            supportingText = if (draft.length > 1800) { { Text("${draft.length}/2,000") } } else null,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotPalette.GoldDeep, unfocusedBorderColor = SpotPalette.Line,
                                focusedContainerColor = SpotPalette.Surface, unfocusedContainerColor = SpotPalette.Ivory, cursorColor = SpotPalette.GoldDeep))
                        val canSend = draft.isNotBlank() && !sending
                        val editing = editingId
                        IconButton(onClick = {
                            if (editing != null && onEdit != null) onEdit(editing, draft.trim())
                            else { onSendComment(draft.trim()); if (!managedSend) draft = "" }
                        }, enabled = canSend, modifier = Modifier.size(52.dp)) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(if (canSend) SpotGoldGradient else SolidColor(SpotPalette.Track)),
                                contentAlignment = Alignment.Center) {
                                if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = SpotPalette.GoldDeep)
                                else Icon(if (editing != null) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.Send,
                                    if (editing != null) "Save edit" else "Post comment", Modifier.size(19.dp),
                                    tint = if (canSend) SpotPalette.OnGold else SpotPalette.Muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(c: SpotUiComment, own: Boolean, onDelete: ((String) -> Unit)?, onEdit: (() -> Unit)?,
    onProfileClick: (String) -> Unit, onToggleLike: (String) -> Unit, modifier: Modifier = Modifier) {
    val openProfile = Modifier.clickable(enabled = c.userId.isNotBlank(), onClickLabel = "Open profile") { onProfileClick(c.userId) }
    Row(modifier.fillMaxWidth().alpha(if (c.pending) .6f else 1f).padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top) {
        SocialAvatar(c.avatarUrl, "${c.username} profile", Modifier.size(40.dp).clip(CircleShape).then(openProfile), c.userId)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.username, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = SpotPalette.Ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).then(openProfile))
                if (own) Text("You", style = MaterialTheme.typography.labelSmall, color = SpotPalette.GoldInk,
                    modifier = Modifier.padding(start = 6.dp).background(SpotPalette.GoldSoft, CircleShape).padding(horizontal = 7.dp, vertical = 1.dp))
                Text("  ·  ${if (c.pending) "Posting…" else c.timeLabel}${if (c.edited && !c.pending) " · Edited" else ""}",
                    style = MaterialTheme.typography.labelMedium, color = SpotPalette.Muted, maxLines = 1)
            }
            Text(c.text, style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Ink, modifier = Modifier.padding(top = 3.dp))
            if (onEdit != null || onDelete != null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onEdit != null) Text("Edit", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = SpotPalette.Muted,
                    modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onEdit)
                        .semantics { contentDescription = "Edit comment" }.padding(horizontal = 2.dp, vertical = 4.dp))
                if (onDelete != null) Text("Delete", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = SpotPalette.Muted,
                    modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { onDelete(c.id) }
                        .semantics { contentDescription = "Delete comment" }.padding(horizontal = 2.dp, vertical = 4.dp))
            }
        }
        Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CommentLike(c.liked, enabled = !c.pending) { onToggleLike(c.id) }
            if (c.likes > 0) Text(c.likes.toString(), style = MaterialTheme.typography.labelSmall, color = SpotPalette.Muted)
        }
    }
}

/** A small heart that pops when liked, sized for a comment rather than a post. */
@Composable
private fun CommentLike(liked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val pulse = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(liked) }
    LaunchedEffect(liked) {
        if (liked != previous) {
            previous = liked
            pulse.snapTo(if (liked) 1.3f else .9f)
            pulse.animateTo(1f, spring(dampingRatio = .5f, stiffness = 600f))
        }
    }
    IconButton(onClick, Modifier.size(40.dp), enabled = enabled) {
        Icon(if (liked) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder, "Like comment",
            Modifier.size(19.dp).graphicsLayer { scaleX = pulse.value; scaleY = pulse.value },
            tint = if (liked) SpotPalette.Heart else SpotPalette.Muted)
    }
}

@Composable
internal fun SocialAvatar(url: String?, description: String, modifier: Modifier = Modifier, uid: String = "") { SpotAvatar(url, description, modifier, uid) }
