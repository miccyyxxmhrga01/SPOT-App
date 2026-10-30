package com.example.spotapp.ui.theme.screens.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.screens.discover.eventDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

/** Sends a post into one of your chats, where it appears as a post card, or hands it to other apps. */
@Composable
fun SharePostSheet(postId: String, author: String, caption: String, imageUrl: String, onMoreApps: () -> Unit, onDismiss: () -> Unit,
    store: MessagesStore = remember { SupabaseMessagesStore(HomeRepository.currentUserId().orEmpty()) }) =
    ShareToChatsSheet("Share post", author, caption.ifBlank { "Post on SPOT" }, imageUrl, Icons.Rounded.Notes,
        "Start a conversation in Messages, then share posts there.", "share-post-sheet", { note -> SharedPostLink.message(postId, note) },
        onMoreApps, onDismiss, store)

/** Sends an event into one of your chats, where it appears as an event card, or hands it to other apps. */
@Composable
internal fun ShareEventSheet(event: CampusEvent, onMoreApps: () -> Unit, onDismiss: () -> Unit,
    store: MessagesStore = remember { SupabaseMessagesStore(HomeRepository.currentUserId().orEmpty()) }) =
    ShareToChatsSheet("Share event", event.title, "${eventDate(event.startsAt)} · ${event.location}", event.imageUrl, Icons.Rounded.Event,
        "Start a conversation in Messages, then share events there.", "share-event-sheet", { note -> SharedEventLink.message(event.eventId, note) },
        onMoreApps, onDismiss, store)

/** Sends a profile into one of your chats, where it appears as a profile card, or hands it to other apps. */
@Composable
fun ShareProfileSheet(uid: String, name: String, username: String, photo: String, onMoreApps: () -> Unit, onDismiss: () -> Unit,
    store: MessagesStore = remember { SupabaseMessagesStore(HomeRepository.currentUserId().orEmpty()) }) =
    ShareToChatsSheet("Share profile", name, username.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "Profile on SPOT", photo,
        Icons.Rounded.Person, "Start a conversation in Messages, then share profiles there.", "share-profile-sheet",
        { note -> SharedProfileLink.message(uid, note) }, onMoreApps, onDismiss, store)

/**
 * Picks chats to send something into, one Send per chat, with an optional note: [message] turns the note into the
 * message text. The other-apps button hands it to Android's share sheet instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareToChatsSheet(heading: String, title: String, subtitle: String, imageUrl: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    emptyHint: String, tag: String, message: (String) -> String, onMoreApps: () -> Unit, onDismiss: () -> Unit, store: MessagesStore) {
    val inbox by remember(store) { store.inbox() }.collectAsStateWithLifecycle(MessagesInbox())
    var query by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val sending = remember { mutableStateListOf<String>() }
    val sent = remember { mutableStateListOf<String>() }
    // One message id per chat, so retrying after a lost reply can't post the same share twice.
    val messageIds = remember { mutableStateMapOf<String, String>() }
    val scope = rememberCoroutineScope()
    val chats = inbox.conversations
        .filter { it.canMessage && (it.kind == "group" || it.otherUid(store.uid) !in inbox.blockedIds) }
        .filter { query.isBlank() || it.nameFor(store.uid, inbox.preferences[it.id] ?: ChatPreferences()).contains(query.trim(), true) }
        .sortedByDescending { it.updatedAt }
    fun send(chat: CampusConversation) {
        if (chat.id in sending || chat.id in sent) return
        val id = messageIds.getOrPut(chat.id) { UUID.randomUUID().toString() }
        sending.add(chat.id); error = ""
        scope.launch {
            try { withTimeout(30_000) { store.send(chat.id, ChatDraft(id, message(note))) }; sent.add(chat.id) }
            catch (_: TimeoutCancellationException) { error = "Couldn't connect. Tap Send to retry." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = messagesError(e) }
            finally { sending.remove(chat.id) }
        }
    }
    // While typing a note, the chat list keeps the room: the preview and the other-apps button step aside.
    var noteFocused by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    val typing = noteFocused || searchFocused
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SpotPalette.Ivory, contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.86f).imePadding().navigationBarsPadding().testTag(tag)) {
            Text(heading, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SpotPalette.Ink,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp))
            if (!typing) SpotCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(18.dp), elevation = 1.dp) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (imageUrl.isNotBlank()) AsyncImage(imageUrl, null, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(SpotPalette.Track),
                        contentScale = ContentScale.Crop)
                    else SpotIconTile(icon, size = 56.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, color = SpotPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            SpotSearchField(query, { query = it }, Modifier.padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 6.dp).onFocusChanged { searchFocused = it.isFocused }.testTag("share-search"), "Search your chats")
            if (error.isNotBlank()) Text(error, style = MaterialTheme.typography.bodySmall, color = SpotPalette.Danger,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(vertical = 6.dp)) {
                when {
                    inbox.loading && chats.isEmpty() -> item { SpotLoading(true, SkeletonKind.ROWS) }
                    inbox.error.isNotBlank() && chats.isEmpty() -> item { SpotEmptyState(Icons.Rounded.CloudOff, "Chats couldn't load", message = inbox.error) }
                    chats.isEmpty() -> item {
                        SpotEmptyState(Icons.Rounded.Forum, if (query.isBlank()) "No chats yet" else "No chats found",
                            message = if (query.isBlank()) emptyHint else "Try another name.")
                    }
                    else -> items(chats, key = { it.id }) { chat ->
                        val group = chat.kind == "group"
                        val other = chat.people[chat.otherUid(store.uid)]
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("share-chat-${chat.id}"),
                            verticalAlignment = Alignment.CenterVertically) {
                            ChatAvatar(chat.nameFor(store.uid), other?.photo.orEmpty(), group, size = 46, uid = chat.otherUid(store.uid))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(chat.nameFor(store.uid, inbox.preferences[chat.id] ?: ChatPreferences()), style = MaterialTheme.typography.titleSmall,
                                    color = SpotPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (group) "${chat.memberIds.size} members" else other?.username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "Campus connection",
                                    style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            when (chat.id) {
                                in sent -> SpotPillButton("Sent", {}, icon = Icons.Rounded.Check, style = SpotButtonStyle.Tonal, compact = true, enabled = false)
                                in sending -> Box(Modifier.size(width = 76.dp, height = 40.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(20.dp), color = SpotPalette.GoldDeep, strokeWidth = 2.dp)
                                }
                                else -> SpotPillButton("Send", { send(chat) }, compact = true)
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = SpotPalette.Line)
            OutlinedTextField(note, { note = it.take(500) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp).onFocusChanged { noteFocused = it.isFocused }.testTag("share-note"),
                placeholder = { Text("Add a message…", style = MaterialTheme.typography.bodyMedium) }, maxLines = 3,
                textStyle = MaterialTheme.typography.bodyMedium, shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotPalette.GoldDeep, unfocusedBorderColor = SpotPalette.Line,
                    focusedContainerColor = SpotPalette.Surface, unfocusedContainerColor = SpotPalette.Surface,
                    cursorColor = SpotPalette.GoldDeep))
            if (typing) Spacer(Modifier.height(10.dp))
            else SpotPillButton("Share to other apps", onMoreApps, Modifier.fillMaxWidth().padding(16.dp), icon = Icons.Rounded.Share,
                style = SpotButtonStyle.Secondary)
        }
    }
}
