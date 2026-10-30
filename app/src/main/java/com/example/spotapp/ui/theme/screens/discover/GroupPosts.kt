package com.example.spotapp.ui.theme.screens.discover

import com.example.spotapp.ui.theme.components.SpotConfirmDialog
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.components.SkeletonKind
import com.example.spotapp.ui.theme.components.SpotLoading
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.models.SpotUiProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * A group's page: members share posts, and whoever can see the group's posts reads them.
 * [posts] is null while the first page loads; [readable] is false for outsiders of a private group.
 */
internal fun LazyListScope.groupPage(group: CampusGroup, profile: SpotUiProfile, member: Boolean, readable: Boolean,
    posts: SocialPage<GroupPost>?, error: String, retry: () -> Unit, more: () -> Unit, onProfile: (SpotUiProfile) -> Unit) {
    item(key = "group-posts-title") { Text("Posts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
    if (member) item(key = "group-post-composer") { GroupPostComposer(group.groupId, profile) }
    if (!readable) {
        item(key = "group-posts-locked") { Text("Join this group to see its posts.", style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted) }
        return
    }
    if (error.isNotBlank()) item(key = "group-posts-error") { CommunityError(error, retry) }
    when {
        posts == null -> if (error.isBlank()) item(key = "group-posts-loading") { SpotLoading(true, SkeletonKind.ROWS) }
        posts.items.isEmpty() -> item(key = "group-posts-empty") {
            Text(if (member) "No posts yet. Share the first one." else "No posts yet.", style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted)
        }
        else -> {
            items(posts.items, key = { "group-post-${it.id}" }) { post ->
                GroupPostCard(post, member, post.canDelete(profile.uid, group.ownerId), post.canEdit(profile.uid), onProfile, Modifier.animateItem())
            }
            if (posts.hasMore) item(key = "group-posts-more") { TextButton(more, Modifier.fillMaxWidth()) { Text("Load more posts") } }
        }
    }
}

@Composable
private fun GroupPostComposer(groupId: String, profile: SpotUiProfile) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The id stays with the draft, so retrying a post whose reply was lost never posts it twice.
    var id by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var text by rememberSaveable { mutableStateOf("") }
    var image by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; image = uri.toString() }
    }
    // As the keyboard comes up, the whole card scrolls into view, so the Post button isn't left under it.
    val card = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val keyboard = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, keyboard) { if (focused && keyboard > 0) { delay(50); card.bringIntoView() } }
    Surface(color = SpotPalette.Surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, CommunityLine),
        modifier = Modifier.bringIntoViewRequester(card).testTag("group-post-composer")) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CommunityAvatar(profile.profileImageUrl, profile.uid)
                OutlinedTextField(text, { text = it.take(3000) }, Modifier.weight(1f).testTag("group-post-text")
                    .onFocusChanged { focused = it.isFocused },
                    placeholder = { Text("Share something with the group") }, enabled = !busy, maxLines = 6, shape = RoundedCornerShape(16.dp))
            }
            if (image.isNotBlank()) Box {
                AsyncImage(image, "Selected photo", Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                FilledTonalIconButton({ image = "" }, Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp), enabled = !busy) {
                    Icon(Icons.Rounded.Close, "Remove photo", Modifier.size(18.dp))
                }
            }
            CommunityError(error)
            if (busy) LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth(), color = CommunityGold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy) {
                    Icon(Icons.Rounded.AddPhotoAlternate, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Photo")
                }
                Spacer(Modifier.weight(1f))
                Button({
                    busy = true; error = ""; progress = 0f
                    scope.launch {
                        try {
                            withTimeout(180_000) { DiscoverRepository.shared.createGroupPost(context.applicationContext, id, groupId, text, image) { progress = it } }
                            text = ""; image = ""; id = UUID.randomUUID().toString()
                        } catch (_: TimeoutCancellationException) { error = "Posting took too long. Retry; it won't be posted twice." }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = userMessage(e, "Couldn't post. Check your connection and retry.") }
                        finally { busy = false }
                    }
                }, enabled = !busy && (text.isNotBlank() || image.isNotBlank()), colors = ButtonDefaults.buttonColors(containerColor = CommunityGold),
                    modifier = Modifier.testTag("group-post-send")) { Text(if (busy) "Posting…" else "Post") }
            }
        }
    }
}

@Composable
private fun GroupPostCard(post: GroupPost, member: Boolean, canDelete: Boolean, canEdit: Boolean, onProfile: (SpotUiProfile) -> Unit,
    modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    fun act(failure: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = ""
        scope.launch {
            try { withTimeout(30_000) { action() } }
            catch (_: TimeoutCancellationException) { error = "That took too long. Reconnect and retry." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = userMessage(e, failure) }
            finally { busy = false }
        }
    }
    Surface(modifier.testTag("group-post-${post.id}"), color = SpotPalette.Surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, CommunityLine)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .clickable { onProfile(SpotUiProfile(post.userId, post.displayName, "", "", "", post.profileImageUrl)) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CommunityAvatar(post.profileImageUrl, post.userId)
                    Column {
                        Text(post.displayName.ifBlank { "SPOT member" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(postTimeLabel(post.createdAt) + if (post.edited) " · Edited" else "", style = MaterialTheme.typography.labelSmall, color = SpotPalette.Muted)
                    }
                }
                if (canDelete || canEdit) Box {
                    IconButton({ menu = true }, enabled = !busy) { Icon(Icons.Rounded.MoreHoriz, "Post options", tint = SpotPalette.Muted) }
                    DropdownMenu(menu, { menu = false }) {
                        if (canEdit) DropdownMenuItem(text = { Text("Edit post") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = { menu = false; editing = true })
                        if (canDelete) DropdownMenuItem(text = { Text("Delete post") }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                            onClick = { menu = false; confirmDelete = true })
                    }
                } else Spacer(Modifier.height(48.dp))
            }
            if (post.text.isNotBlank()) Text(post.text, Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium)
            if (post.imageUrl.isNotBlank()) AsyncImage(post.imageUrl, "Post photo",
                Modifier.padding(top = 6.dp).fillMaxWidth().aspectRatio(4f / 3f), contentScale = ContentScale.Crop)
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ act("Couldn't update your like. Please retry.") { DiscoverRepository.shared.setGroupPostLiked(post.id, !post.liked) } },
                    enabled = member && !busy) {
                    Icon(if (post.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (post.liked) "Unlike" else "Like",
                        tint = if (post.liked) SpotPalette.Heart else SpotPalette.Muted)
                }
                if (post.likesCount > 0) Text(post.likesCount.toString(), style = MaterialTheme.typography.labelLarge, color = SpotPalette.Muted)
            }
            if (error.isNotBlank()) Text(error, Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirmDelete) SpotConfirmDialog(Icons.Rounded.DeleteOutline, "Delete this post?", "It will be removed from the group for everyone.",
        "Delete", onDismiss = { confirmDelete = false }, danger = true) {
        confirmDelete = false; act("Couldn't delete this post. Please retry.") { DiscoverRepository.shared.deleteGroupPost(post.id) }
    }
    if (editing) EditGroupPostPage(post, close = { editing = false })
}

/** The author changes their group post's text; its photo stays as posted. */
@Composable
private fun EditGroupPostPage(post: GroupPost, close: () -> Unit) {
    var text by rememberSaveable(post.id) { mutableStateOf(post.text) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var discard by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val changed = text.trim() != post.text
    fun back() { if (busy) return; if (changed) discard = true else close() }
    CommunityWindow("Edit post", ::back, busy) { padding ->
        CommunityFormBody(padding, "Save", enabled = changed && (text.isNotBlank() || post.imageUrl.isNotBlank()), busy = busy, onAction = {
            busy = true; error = ""
            scope.launch {
                try { withTimeout(30_000) { DiscoverRepository.shared.updateGroupPost(post.id, text) }; close() }
                catch (_: TimeoutCancellationException) { error = "Saving took too long. Check your connection and retry." }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = userMessage(e, "Couldn't save your changes. Please retry.") }
                finally { busy = false }
            }
        }) {
            if (post.imageUrl.isNotBlank()) AsyncImage(post.imageUrl, "Post photo",
                Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
            OutlinedTextField(text, { text = it.take(3000); error = "" }, Modifier.fillMaxWidth().testTag("edit-group-post-text"),
                label = { Text("Post") }, minLines = 3, maxLines = 10, enabled = !busy, shape = RoundedCornerShape(16.dp))
            CommunityError(error)
        }
    }
    if (discard) SpotConfirmDialog(Icons.Rounded.EditOff, "Discard your changes?", "Your edits to this post won't be saved.",
        "Discard", onDismiss = { discard = false }, danger = true, dismiss = "Keep editing", onConfirm = close)
}
