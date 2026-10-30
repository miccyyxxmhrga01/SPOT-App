package com.example.spotapp.ui.theme.screens.notification

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.components.LifecycleDisposableEffect
import com.example.spotapp.ui.theme.components.LocalSpotBottomContentPadding
import com.example.spotapp.ui.theme.components.SkeletonKind
import com.example.spotapp.ui.theme.components.SpotEmptyState
import com.example.spotapp.ui.theme.components.SpotHeaderAction
import com.example.spotapp.ui.theme.components.SpotLoading
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.components.SpotScreenHeader
import com.example.spotapp.ui.theme.components.spotAmbientBackground
import com.example.spotapp.ui.theme.models.*
import com.example.spotapp.ui.theme.navigation.SpotMainBottomNavigation
import com.example.spotapp.ui.theme.screens.discover.CommunityDetails
import com.example.spotapp.ui.theme.screens.home.SocialAvatar
import com.example.spotapp.ui.theme.screens.seeker.OpportunityDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Activity only: invitations are answered in Messages, under Requests ([onOpenRequests]). */
@Composable
fun NotificationsScreen(profile: SpotUiProfile, onNavigate: (SpotRoute) -> Unit, showBottomNavigation: Boolean = true,
    onOpen: (SocialNotification) -> Unit = {}, repository: SocialRepository = SocialRepository.shared,
    onProfile: (SpotUiProfile) -> Unit = {}, onMessage: (String) -> Unit = {}, onChat: (String) -> Unit = {},
    onOpenRequests: () -> Unit = {}, onBack: (() -> Unit)? = null) {
    // The event, group or opportunity a tapped notification is about; posts and profiles open through onOpen.
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    var targetKind by rememberSaveable { mutableStateOf(NotificationTarget.EVENT) }
    Scaffold(modifier = Modifier.spotAmbientBackground(SpotPalette.Ivory), containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = {
        if (showBottomNavigation) SpotMainBottomNavigation(SpotRoute.NOTIFICATIONS, profile, onNavigate = onNavigate)
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            NotificationActivity(profile, onBack, { item ->
                when (item.opens) {
                    NotificationTarget.PROFILE, NotificationTarget.POST -> onOpen(item)
                    NotificationTarget.INVITATIONS -> onOpenRequests()
                    else -> { targetKind = item.opens; target = item.targetId }
                }
            }, onChat, repository)
        }
    }
    target?.let { id ->
        if (targetKind == NotificationTarget.OPPORTUNITY) OpportunityDetails(id, profile, { target = null }, { target = null; onProfile(it) }, onMessage)
        else CommunityDetails(id, targetKind == NotificationTarget.EVENT, profile, { target = null }, { target = null; onProfile(it) }, onChat)
    }
}

@Composable
private fun NotificationActivity(profile: SpotUiProfile, onBack: (() -> Unit)?, onOpen: (SocialNotification) -> Unit, onChat: (String) -> Unit,
    repository: SocialRepository) {
    val pages = remember(profile.uid) { mutableStateMapOf<Int, SocialPage<SocialNotification>>() }
    val cursors = remember(profile.uid) { mutableStateListOf<PageCursor?>(null) }
    var error by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(NotificationKind.ALL) }
    var preferences by remember { mutableStateOf(SpotPreferences()) }
    LifecycleDisposableEffect(profile.uid) {
        val listener = PrivacyRepository.shared.listenPreferences({ preferences = it })
        onDispose { listener.remove() }
    }
    val scope = rememberCoroutineScope()
    cursors.forEachIndexed { index, cursor ->
        LifecycleDisposableEffect(profile.uid, cursor, retry) {
            val listener = repository.listenNotifications(cursor, { page ->
                pages[index] = page
                if (index + 1 < cursors.size && page.cursor != null && cursors[index + 1]?.id != page.cursor.id) cursors[index + 1] = page.cursor
            }, { error = it })
            onDispose { listener.remove() }
        }
    }
    val all = pages.toSortedMap().values.flatMap { it.items }.distinctBy { it.id }.sortedByDescending { it.createdAt }.filter { when (it.type) { "like" -> preferences.likes; "comment" -> preferences.comments; "follow" -> preferences.follows; else -> true } }
    // Who you already follow, so a new follower shows Follow back only when you don't.
    val followers = remember(all) { all.filter { it.type == "follow" }.map { it.senderId }.distinct() }
    var following by remember { mutableStateOf(emptySet<String>()) }
    var requested by remember { mutableStateOf(emptySet<String>()) }
    LifecycleDisposableEffect(followers) {
        val listener = if (followers.isEmpty()) null else repository.listenFollowingAmong(followers) { following = it }
        onDispose { listener?.remove() }
    }
    fun action(failure: String = "Couldn't update notifications. Please retry.", block: suspend () -> Unit) {
        if (saving) return
        saving = true
        scope.launch {
            try { block(); error = "" }
            catch (c: CancellationException) { throw c }
            catch (_: Exception) { error = failure }
            finally { saving = false }
        }
    }
    fun open(group: NotificationGroup) = action {
        group.items.filter { !it.isRead }.forEach { repository.markRead(it.id) }
        onOpen(group.first)
    }
    fun followBack(person: String) = action("Couldn't follow back. Please retry.") {
        try { repository.setFollowing(person, true) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            // A private account takes a request instead.
            if (e.message?.contains("private", ignoreCase = true) != true) throw e
            PrivacyRepository.shared.requestFollow(person, true)
            requested = requested + person
        }
    }
    fun openChat(item: SocialNotification) = action("Couldn't open the chat. Please retry.") {
        if (!item.isRead) repository.markRead(item.id)
        onChat(DiscoverRepository.shared.openChat(item.targetId, isEvent = true))
    }

    val kinds = remember(all) { NotificationKind.entries.filter { kind -> kind == NotificationKind.ALL || all.any { it.kind() == kind } } }
    val chosen = filter.takeIf { it in kinds } ?: NotificationKind.ALL
    val groups = groupNotifications(all.filter { chosen == NotificationKind.ALL || it.kind() == chosen })
    val (fresh, earlier) = groups.partition { it.unread }

    SpotScreenHeader("Notifications", onBack = onBack) {
        if (all.any { !it.isRead }) SpotHeaderAction(Icons.Rounded.DoneAll, "Mark all as read", { action { repository.markAllRead() } }, enabled = !saving)
    }
    // Fixed under the title, so the filters stay in reach while the list scrolls.
    if (kinds.size > 2) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(kinds, key = { it.name }) { kind -> FilterChip(kind.label, kind == chosen) { filter = kind } }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("notifications-feed"), contentPadding = PaddingValues(bottom = 24.dp + LocalSpotBottomContentPadding.current)) {
        item(key = "status") {
            if (pages.isEmpty() && error.isBlank()) SpotLoading(true, SkeletonKind.ROWS)
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp), color = SpotPalette.GoldDeep)
            if (error.isNotBlank()) TextButton({ error = ""; retry++ }, Modifier.padding(horizontal = 16.dp)) { Text(error, color = SpotPalette.GoldInk) }
        }
        if (pages.isNotEmpty() && groups.isEmpty()) item(key = "empty") {
            if (all.isEmpty()) SpotEmptyState(Icons.Rounded.NotificationsNone, "You're all caught up", Modifier.padding(top = 36.dp),
                message = "New activity will show up here.")
            else SpotEmptyState(Icons.Rounded.NotificationsNone, "Nothing here yet", Modifier.padding(top = 36.dp))
        }
        listOf("New" to fresh, "Earlier" to earlier).filter { it.second.isNotEmpty() }.forEach { (title, section) ->
            item(key = "section-$title") {
                Text(title.uppercase(), Modifier.padding(start = 22.dp, top = 18.dp, bottom = 8.dp), style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, letterSpacing = .8.sp, color = SpotPalette.GoldInk)
            }
            itemsIndexed(section, key = { _, group -> "row-${group.first.id}" }) { index, group ->
                val item = group.first
                var preview by remember(item.postId) { mutableStateOf<String?>(null) }
                LifecycleDisposableEffect(item.postId) {
                    val listener = item.postId.takeIf { it.isNotBlank() }?.let { id ->
                        HomeRepository.listenPost(id, { preview = it?.media?.firstOrNull()?.previewUrl }, { preview = null })
                    }
                    onDispose { listener?.remove() }
                }
                val corner = 22.dp
                val shape = RoundedCornerShape(topStart = if (index == 0) corner else 0.dp, topEnd = if (index == 0) corner else 0.dp,
                    bottomStart = if (index == section.lastIndex) corner else 0.dp, bottomEnd = if (index == section.lastIndex) corner else 0.dp)
                Column(Modifier.animateItem().padding(horizontal = 16.dp).clip(shape)) {
                    if (index > 0) HorizontalDivider(Modifier.background(SpotPalette.Surface).padding(start = 74.dp), color = SpotPalette.Line)
                    NotificationRow(group, previewUrl = preview, onOpen = { open(group) },
                        onToggleRead = { action { group.items.forEach { repository.markRead(it.id, !group.unread) } } }) {
                        when {
                            group.items.size > 1 -> Unit
                            item.type == "follow" -> when (item.senderId) {
                                in following -> RowAction("Following", primary = false, enabled = false) {}
                                in requested -> RowAction("Requested", primary = false, enabled = false) {}
                                else -> RowAction("Follow back", primary = true, enabled = !saving) { followBack(item.senderId) }
                            }
                            item.type == "event_application_accepted" && item.targetId.isNotBlank() ->
                                RowAction("Open chat", primary = true, enabled = !saving) { openChat(item) }
                            item.type == "event_application" || item.type == "group_request" ->
                                RowAction("Review", primary = false, enabled = !saving) { open(group) }
                        }
                    }
                }
            }
        }
        val last = pages[cursors.lastIndex]
        if (last?.hasMore == true) item(key = "older") {
            TextButton({ last.cursor?.let { if (cursors.none { c -> c?.id == it.id }) cursors.add(it) } }, Modifier.fillMaxWidth()) {
                Text("Load older notifications", color = SpotPalette.GoldInk)
            }
        }
    }
}

/** What a notification is about, for the filter chips. A chip shows only when there's something under it. */
internal enum class NotificationKind(val label: String) {
    ALL("All"), LIKES("Likes"), COMMENTS("Comments"), FOLLOWS("Follows"), EVENTS("Events"), GROUPS("Groups"), WORK("Work")
}

internal fun SocialNotification.kind(): NotificationKind = when {
    type == "like" -> NotificationKind.LIKES
    type == "comment" -> NotificationKind.COMMENTS
    type == "follow" -> NotificationKind.FOLLOWS
    type.startsWith("event_") -> NotificationKind.EVENTS
    type.startsWith("group_") -> NotificationKind.GROUPS
    else -> NotificationKind.WORK
}

/** One row: likes or comments on the same post come together; everything else stands alone. Newest first. */
internal class NotificationGroup(val items: List<SocialNotification>) {
    val first: SocialNotification get() = items.first()
    val unread: Boolean get() = items.any { !it.isRead }
    /** Everyone in the row, newest first, once each. */
    val people: List<SocialNotification> get() = items.distinctBy { it.senderId }
}

internal fun groupNotifications(items: List<SocialNotification>): List<NotificationGroup> =
    items.groupBy { if ((it.type == "like" || it.type == "comment") && it.postId.isNotBlank()) "${it.type}:${it.postId}" else it.id }
        .values.map { NotificationGroup(it.sortedByDescending { item -> item.createdAt }) }
        .sortedByDescending { it.first.createdAt }

/** "Sam", "Sam and Ana", or "Sam, Ana and 3 others". */
internal fun groupNames(people: List<SocialNotification>): String = when (people.size) {
    0 -> ""
    1 -> people[0].senderName
    2 -> "${people[0].senderName} and ${people[1].senderName}"
    else -> "${people[0].senderName}, ${people[1].senderName} and ${people.size - 2} ${if (people.size == 3) "other" else "others"}"
}

/**
 * A notification as one sentence (who, what, when) beside their avatar, with the post on the right and any action
 * below. Unread rows sit on gold. Long-press to mark it read or unread.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NotificationRow(group: NotificationGroup, modifier: Modifier = Modifier, previewUrl: String? = null, onOpen: () -> Unit,
    onToggleRead: () -> Unit, action: (@Composable () -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    val item = group.first
    val unread = group.unread
    Box(modifier.fillMaxWidth().background(if (unread) SpotPalette.GoldWash else SpotPalette.Surface)) {
        Row(Modifier.fillMaxWidth().combinedClickable(onClickLabel = "Open", onLongClickLabel = "Notification options",
            onLongClick = { menu = true }, onClick = onOpen).semantics { if (unread) stateDescription = "Unread" }
            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp).testTag("notification-${item.id}"), verticalAlignment = Alignment.Top) {
            NotificationAvatar(group)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold)) { append(groupNames(group.people)) }
                    append(" "); append(item.message)
                    withStyle(SpanStyle(color = SpotPalette.Muted)) { append(" · ${postTimeLabel(item.createdAt)}") }
                }, style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Ink, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (action != null) Box(Modifier.padding(top = 8.dp)) { action() }
            }
            if (!previewUrl.isNullOrBlank()) AsyncImage(previewUrl, "Post preview",
                Modifier.size(42.dp, 52.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text(if (unread) "Mark as read" else "Mark as unread") }, onClick = { menu = false; onToggleRead() })
        }
    }
}

/** One avatar, or the two newest people overlapping when several did the same thing; a glyph says what happened. */
@Composable
private fun NotificationAvatar(group: NotificationGroup) {
    val people = group.people
    Box(Modifier.size(48.dp, 46.dp)) {
        if (people.size > 1) {
            SocialAvatar(people[1].senderProfileImageUrl, "${people[1].senderName} profile", Modifier.size(34.dp).border(2.dp, SpotPalette.Surface, CircleShape), people[1].senderId)
            SocialAvatar(people[0].senderProfileImageUrl, "${people[0].senderName} profile",
                Modifier.align(Alignment.BottomEnd).size(34.dp).border(2.dp, SpotPalette.Surface, CircleShape), people[0].senderId)
        } else SocialAvatar(group.first.senderProfileImageUrl, "${group.first.senderName} profile", Modifier.size(44.dp), group.first.senderId)
        NotificationTypeBadge(group.first.type, Modifier.align(Alignment.BottomEnd).offset(3.dp, 3.dp))
    }
}

@Composable
private fun RowAction(label: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val modifier = Modifier.heightIn(min = 36.dp).testTag("notification-action-$label")
    val padding = PaddingValues(horizontal = 16.dp)
    if (primary) Button(onClick, modifier, enabled = enabled, shape = CircleShape, contentPadding = padding,
        colors = ButtonDefaults.buttonColors(containerColor = SpotPalette.Gold, contentColor = SpotPalette.OnGold)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
    }
    else OutlinedButton(onClick, modifier, enabled = enabled, shape = CircleShape, contentPadding = padding,
        border = BorderStroke(1.dp, SpotPalette.Line), colors = ButtonDefaults.outlinedButtonColors(containerColor = SpotPalette.Surface,
            contentColor = SpotPalette.Ink, disabledContainerColor = SpotPalette.Surface, disabledContentColor = SpotPalette.Muted)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun FilterChip(label: String, chosen: Boolean, onClick: () -> Unit) {
    Surface(Modifier.selectable(chosen, role = Role.RadioButton, onClick = onClick).testTag("notification-filter-$label"), shape = CircleShape,
        color = if (chosen) SpotPalette.Ink else SpotPalette.Surface, border = BorderStroke(1.dp, if (chosen) SpotPalette.Ink else SpotPalette.Line)) {
        Box(Modifier.heightIn(min = 38.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (chosen) SpotPalette.OnInk else SpotPalette.Ink,
                fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium)
        }
    }
}

/** A small glyph on the avatar says what happened before the text is read. */
@Composable
private fun NotificationTypeBadge(type: String, modifier: Modifier = Modifier) {
    val (icon, tint) = when (type) {
        "like" -> Icons.Rounded.Favorite to SpotPalette.Heart
        "comment" -> Icons.Rounded.ChatBubble to SpotPalette.GoldDeep
        "follow" -> Icons.Rounded.PersonAdd to SpotPalette.GoldDeep
        "event_join", "event_application", "event_application_accepted", "event_application_declined", "event_updated" -> Icons.Rounded.Event to SpotPalette.GoldDeep
        "event_cancelled" -> Icons.Rounded.EventBusy to SpotPalette.GoldDeep
        "group_join", "group_request", "group_approved", "group_post" -> Icons.Rounded.Groups to SpotPalette.GoldDeep
        "invitation", "invitation_accepted", "invitation_declined", "application", "application_accepted", "application_declined" ->
            Icons.Rounded.Work to SpotPalette.GoldDeep
        else -> Icons.Rounded.Notifications to SpotPalette.GoldDeep
    }
    Box(modifier.size(20.dp).background(SpotPalette.Surface, CircleShape).padding(2.dp).background(tint.copy(alpha = .14f), CircleShape),
        contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(11.dp), tint = tint)
    }
}
