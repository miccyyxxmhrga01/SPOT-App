package com.example.spotapp.ui.theme.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.discover.CommunityError
import com.example.spotapp.ui.theme.screens.discover.CommunityFormBody
import com.example.spotapp.ui.theme.screens.discover.CommunityLine
import com.example.spotapp.ui.theme.screens.discover.CommunityWindow
import com.example.spotapp.ui.theme.screens.discover.communityChipColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * A post's "⋯" menu, as on Instagram: its owner edits, pins, hides the like count, turns comments off or deletes it;
 * anyone else can hide it from their feed, unfollow, open the account or report it. Edit opens [EditPostPage].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostOptionsSheet(post: PublishedPost, onDismiss: () -> Unit, onProfile: ((SpotUiProfile) -> Unit)? = null,
    onNotInterested: (() -> Unit)? = null, onReport: (() -> Unit)? = null, onDeleted: () -> Unit = {},
    own: Boolean = post.author.uid == Supabase.uid()) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var following by remember(post.author.uid) { mutableStateOf<Boolean?>(null) }
    LifecycleDisposableEffect(post.author.uid, own) {
        // Only while signed in: the session can still be loading when the app comes back to the front.
        val listener = if (own || post.author.uid.isBlank() || Supabase.uid() == null) null
            else SocialRepository.shared.listenFollowing(post.author.uid, { following = it }, { })
        onDispose { listener?.remove() }
    }
    fun act(failure: String, done: () -> Unit = onDismiss, block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = ""
        scope.launch {
            try { withTimeout(30_000) { block() }; done() }
            catch (_: TimeoutCancellationException) { error = "That took too long. Check your connection and retry." }
            catch (c: CancellationException) { throw c }
            catch (e: Exception) { error = userMessage(e, failure) }
            finally { busy = false }
        }
    }
    if (editing) {
        EditPostPage(post, close = { editing = false }, saved = { editing = false; onDismiss() })
        return
    }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, containerColor = SpotPalette.Surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp).testTag("post-options")) {
            if (own) {
                PostOption(Icons.Rounded.Edit, "Edit", enabled = !busy, tag = "post-option-edit") { editing = true }
                PostOption(Icons.Rounded.PushPin, if (post.pinned) "Unpin from your profile" else "Pin to your profile",
                    if (post.pinned) null else "Pinned posts stay at the top of your profile.", enabled = !busy, tag = "post-option-pin") {
                    act("Couldn't pin this post. Please retry.") { SpotPostsRepository.setOptions(post.id, pinned = !post.pinned) }
                }
                PostOption(if (post.likesHidden) Icons.Rounded.Favorite else Icons.Rounded.HeartBroken,
                    if (post.likesHidden) "Show like count" else "Hide like count",
                    if (post.likesHidden) null else "Only you will see how many people liked it.", enabled = !busy, tag = "post-option-likes") {
                    act("Couldn't change the like count. Please retry.") { SpotPostsRepository.setOptions(post.id, likesHidden = !post.likesHidden) }
                }
                PostOption(if (post.commentsOff) Icons.Rounded.ChatBubbleOutline else Icons.Rounded.CommentsDisabled,
                    if (post.commentsOff) "Turn on commenting" else "Turn off commenting",
                    if (post.commentsOff) null else "Comments already here stay visible.", enabled = !busy, tag = "post-option-comments") {
                    act("Couldn't change commenting. Please retry.") { SpotPostsRepository.setOptions(post.id, commentsOff = !post.commentsOff) }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = SpotPalette.Line)
                PostOption(Icons.Rounded.DeleteOutline, "Delete", danger = true, enabled = !busy, tag = "post-option-delete") { confirmDelete = true }
            } else {
                onNotInterested?.let { hide ->
                    PostOption(Icons.Rounded.NotInterested, "Not interested", "See fewer posts like this.", tag = "post-option-hide") { onDismiss(); hide() }
                }
                if (following == true) PostOption(Icons.Rounded.PersonRemove, "Unfollow ${post.author.displayName.ifBlank { "this account" }}",
                    enabled = !busy, tag = "post-option-unfollow") {
                    act("Couldn't unfollow. Please retry.") { SocialRepository.shared.setFollowing(post.author.uid, false) }
                }
                onProfile?.let { open ->
                    PostOption(Icons.Rounded.AccountCircle, "About this account", tag = "post-option-profile") { onDismiss(); open(post.author) }
                }
                onReport?.let { report ->
                    PostOption(Icons.Rounded.Flag, "Report", danger = true, tag = "post-option-report") { onDismiss(); report() }
                }
            }
            if (error.isNotBlank() && !confirmDelete) Text(error, Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = SpotPalette.Danger)
        }
    }
    if (confirmDelete) SpotConfirmDialog(Icons.Rounded.DeleteOutline, "Delete this post?",
        "It'll be removed from SPOT for everyone, with its likes and comments. You can't undo this.", "Delete",
        onDismiss = { if (!busy) { confirmDelete = false; error = "" } }, danger = true, busy = busy,
        content = if (error.isNotBlank()) { { Text(error, style = MaterialTheme.typography.bodySmall, color = SpotPalette.Danger) } } else null) {
        act("Couldn't delete this post. Please retry.", done = { confirmDelete = false; onDismiss(); onDeleted() }) {
            SpotPostsRepository.delete(post.id)
        }
    }
}

@Composable
private fun PostOption(icon: ImageVector, label: String, detail: String? = null, danger: Boolean = false, enabled: Boolean = true,
    tag: String = "", onClick: () -> Unit) {
    val color = if (danger) SpotPalette.Danger else SpotPalette.Ink
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 24.dp, vertical = 10.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(24.dp), tint = if (enabled) color else SpotPalette.Muted)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = if (enabled) color else SpotPalette.Muted)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted)
        }
    }
}

/** Hashtags as typed ("#music live, #campus"), without # or repeats, in order. */
internal fun parseHashtags(text: String): List<String> =
    text.split(Regex("[\\s,]+")).map { it.trim().trimStart('#') }.filter(String::isNotBlank).distinct()

/** The owner changes a post's caption, talent categories, hashtags, place or audience. Photos and videos stay as posted. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditPostPage(post: PublishedPost, close: () -> Unit, saved: () -> Unit) {
    var caption by rememberSaveable(post.id) { mutableStateOf(post.caption) }
    var categories by remember(post.id) { mutableStateOf(post.categories) }
    var tags by rememberSaveable(post.id) { mutableStateOf(post.tags.joinToString(" ") { "#$it" }) }
    var location by rememberSaveable(post.id) { mutableStateOf(post.location) }
    var audience by rememberSaveable(post.id) { mutableStateOf(post.audience) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var discard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val hashtags = parseHashtags(tags)
    val changed = caption.trim() != post.caption || categories != post.categories || hashtags != post.tags ||
        location.trim() != post.location || audience != post.audience
    val issue = when {
        caption.isBlank() && post.media.isEmpty() -> "A post without photos or videos needs a caption."
        hashtags.size > 20 || hashtags.any { it.length > 60 } -> "Use up to 20 hashtags, each under 60 characters."
        else -> null
    }
    fun back() { if (busy) return; if (changed) discard = true else close() }
    fun save() {
        if (issue != null) { error = issue; return }
        busy = true; error = ""
        scope.launch {
            try {
                withTimeout(30_000) { SpotPostsRepository.update(post.id, caption, categories, hashtags, location, audience) }
                saved()
            } catch (_: TimeoutCancellationException) { error = "Saving took too long. Check your connection and retry." }
            catch (c: CancellationException) { throw c }
            catch (e: Exception) { error = userMessage(e, "Couldn't save your changes. Please retry.") }
            finally { busy = false }
        }
    }
    CommunityWindow("Edit post", ::back, busy) { padding ->
        CommunityFormBody(padding, "Save", enabled = changed, busy = busy, onAction = ::save) {
            post.media.firstOrNull()?.let { first ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(first.previewUrl, null, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(when {
                            post.media.size > 1 -> "${post.media.size} photos and videos"
                            first.isVideo -> "Video"
                            else -> "Photo"
                        }, style = MaterialTheme.typography.titleSmall, color = SpotPalette.Ink)
                        Text("Photos and videos stay as posted.", style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted)
                    }
                }
            }
            OutlinedTextField(caption, { caption = it.take(2000); error = "" }, Modifier.fillMaxWidth().testTag("edit-post-caption"),
                label = { Text("Caption") }, minLines = 3, maxLines = 8, enabled = !busy, shape = SpotAuthFieldShape, colors = spotAuthFieldColors(),
                supportingText = if (caption.length > 1800) { { Text("${caption.length}/2,000") } } else null)
            Text("Talent categories", style = MaterialTheme.typography.titleSmall, color = SpotPalette.Ink)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (SpotTalentCategories + categories.filterNot { it in SpotTalentCategories }).forEach { item ->
                    val on = item in categories
                    FilterChip(on, { categories = if (on) categories - item else if (categories.size < 8) categories + item else categories; error = "" },
                        label = { Text(item, style = MaterialTheme.typography.labelMedium) }, enabled = !busy,
                        shape = RoundedCornerShape(50), colors = communityChipColors(),
                        border = BorderStroke(1.dp, if (on) SpotPalette.GoldLine else CommunityLine))
                }
            }
            Text(if (categories.isEmpty()) "No category: it shows as a personal post." else "${categories.size} of 8 chosen",
                style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted)
            OutlinedTextField(tags, { tags = it.take(1300); error = "" }, Modifier.fillMaxWidth().testTag("edit-post-tags"),
                label = { Text("Hashtags") }, placeholder = { Text("#music #live") }, maxLines = 3, enabled = !busy,
                shape = SpotAuthFieldShape, colors = spotAuthFieldColors())
            OutlinedTextField(location, { location = it.take(160); error = "" }, Modifier.fillMaxWidth().testTag("edit-post-location"),
                label = { Text("Location") }, leadingIcon = { Icon(Icons.Rounded.Place, null) }, singleLine = true, enabled = !busy,
                shape = SpotAuthFieldShape, colors = spotAuthFieldColors())
            Text("Who can see this post", style = MaterialTheme.typography.titleSmall, color = SpotPalette.Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("public", "followers", "private").forEach { value ->
                    FilterChip(audience == value, { audience = value; error = "" }, label = { Text(audienceLabel(value)) }, enabled = !busy,
                        shape = RoundedCornerShape(50), colors = communityChipColors(),
                        border = BorderStroke(1.dp, if (audience == value) SpotPalette.GoldLine else CommunityLine),
                        modifier = Modifier.testTag("edit-post-audience-$value"))
                }
            }
            CommunityError(error)
        }
    }
    if (discard) SpotConfirmDialog(Icons.Rounded.EditOff, "Discard your changes?", "Your edits to this post won't be saved.",
        "Discard", onDismiss = { discard = false }, danger = true, dismiss = "Keep editing", onConfirm = close)
}
