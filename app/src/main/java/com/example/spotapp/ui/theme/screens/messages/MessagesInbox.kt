package com.example.spotapp.ui.theme.screens.messages

import com.example.spotapp.ui.theme.spotSp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.screens.discover.eventDate

/** The inbox's colors: SPOT's ivory ground, gold for what needs attention and red only for unread event chats. */
private object Inbox {
    val Ground get() = SpotPalette.Ivory
    val Ink get() = SpotPalette.Ink
    val Muted get() = SpotPalette.Muted
    val Faint = Color(0xFF8C877C)
    val Frame get() = if (SpotPalette.dark) SpotPalette.Line else Color(0xFFF0EADF)
    val CardLine get() = if (SpotPalette.dark) SpotPalette.Line else Color(0xFFF3EEE4)
    val Selected get() = if (SpotPalette.dark) SpotPalette.GoldSoft else Color(0xFFFCEBC2)
    /** Read rows and cards: white in light mode, raised charcoal in dark mode. */
    val Card get() = SpotPalette.Surface
    val GoldInk get() = SpotPalette.GoldInk
    val UnreadFill get() = SpotPalette.GoldWash
    val UnreadLine get() = if (SpotPalette.dark) SpotPalette.GoldLine else Color(0xFFF0E3BF)
    val UnreadDot = Color(0xFFD9A82E)
    val Red get() = SpotPalette.Heart
    val Ring = Brush.linearGradient(listOf(Color(0xFFF6D46B), Color(0xFFD9A82E)))
    val Online = Color(0xFF2FB55D)
    val Past get() = SpotPalette.Ivory
    val PastTile get() = if (SpotPalette.dark) SpotPalette.Track else Color(0xFFEFEBE2)
}

/**
 * Chats are direct conversations, Groups the chats of groups, Collabs the collaborations people start, and Events
 * the chats of events. Requests have their own page, opened from the Chats tab.
 */
enum class MessagesTab { CHATS, GROUPS, COLLABS, EVENTS }

internal fun CampusConversation.inTab(tab: MessagesTab) = when (tab) {
    MessagesTab.CHATS -> kind == "direct"
    MessagesTab.GROUPS -> communityKind == "group"
    MessagesTab.COLLABS -> kind == "group" && !isCommunity
    MessagesTab.EVENTS -> communityKind == "event"
}

private fun MessagesTab.label() = when (this) {
    MessagesTab.CHATS -> "Chats"; MessagesTab.GROUPS -> "Groups"; MessagesTab.COLLABS -> "Collabs"; MessagesTab.EVENTS -> "Events"
}

private fun MessagesTab.icon(): ImageVector = when (this) {
    MessagesTab.CHATS -> Icons.Outlined.ChatBubbleOutline
    MessagesTab.GROUPS -> Icons.Outlined.Groups
    MessagesTab.COLLABS -> Icons.AutoMirrored.Outlined.Send
    MessagesTab.EVENTS -> Icons.Outlined.CalendarMonth
}

/** The inbox: header, tabs, and each tab's list. Groups, Collabs and Events show how many of their chats are unread. */
@Composable
internal fun MessagesList(inbox: MessagesInbox, work: WorkInbox, conversations: List<CampusConversation>, uid: String,
    tab: MessagesTab, onTab: (MessagesTab) -> Unit, search: String, onSearch: (String) -> Unit,
    onCompose: () -> Unit, onOpen: (CampusConversation) -> Unit, onRetry: () -> Unit, onRequests: () -> Unit,
    events: Map<String, CampusEvent>, online: Set<String> = emptySet(), onOptions: (CampusConversation) -> Unit = {}) {
    val requests = inbox.requests.filter { it.creatorId !in inbox.blockedIds }
    // Unread chats per tab; muted ones stay quiet. Chats already marks each unread row, so only the other tabs count.
    val unreadCounts = listOf(MessagesTab.GROUPS, MessagesTab.COLLABS, MessagesTab.EVENTS).associateWith { counted ->
        conversations.count { chat ->
            val prefs = inbox.preferences[chat.id] ?: ChatPreferences()
            chat.inTab(counted) && !prefs.muted && chat.unread(uid, prefs)
        }
    }
    var searching by rememberSaveable { mutableStateOf(search.isNotBlank()) }
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().spotAmbientBackground(Inbox.Ground)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Messages", style = MaterialTheme.typography.headlineLarge, color = Inbox.Ink, modifier = Modifier.weight(1f))
            SpotHeaderAction(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, if (searching) "Close search" else "Search conversations",
                { if (searching) onSearch(""); searching = !searching }, tag = "messages-search-toggle")
            Spacer(Modifier.width(8.dp))
            SpotHeaderAction(Icons.Rounded.EditNote, "New conversation", onCompose, prominent = true, tag = "messages-compose")
        }
        AnimatedVisibility(searching, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            SpotSearchField(search, onSearch, Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp).testTag("messages-search"), "Search conversations")
        }
        MessagesTabBar(tab, onTab, unreadCounts)
        AnimatedContent(tab, modifier = Modifier.weight(1f), transitionSpec = {
            (fadeIn(tween(170)) + slideInHorizontally(tween(240)) { it / 12 }) togetherWith fadeOut(tween(100))
        }, label = "message-tabs-content") { shownTab ->
            fun matches(chat: CampusConversation) = search.isBlank() ||
                chat.nameFor(uid, inbox.preferences[chat.id] ?: ChatPreferences()).contains(search, true) || chat.preview.contains(search, true)
            // Pinned chats come first, most recently pinned on top; the rest keep their order.
            val inTab = conversations.filter { it.inTab(shownTab) && matches(it) }
                .sortedByDescending { (inbox.preferences[it.id] ?: ChatPreferences()).pinnedAt }
            val rows = if (unreadOnly && shownTab != MessagesTab.EVENTS) inTab.filter { it.unread(uid, inbox.preferences[it.id] ?: ChatPreferences()) } else inTab
            LazyColumn(Modifier.fillMaxSize().testTag("messages-list"),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 18.dp + LocalSpotBottomContentPadding.current),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (inbox.loading) item { SpotLoading(true, SkeletonKind.ROWS) }
                else if (inbox.error.isNotBlank()) item { MessageEmpty(Icons.Rounded.CloudOff, "Messages couldn't load", inbox.error, "Retry", onRetry) }
                else {
                    if (shownTab == MessagesTab.CHATS && search.isBlank()) {
                        // People who are online come first, then the most recent chats.
                        val recent = conversations.filter { it.kind == "direct" && it.memberIds.size >= 2 }
                            .sortedByDescending { it.otherUid(uid) in online }.take(10)
                        // New chats start from the gold button beside search, so this row only shows people.
                        if (recent.isNotEmpty()) item(key = "recent") { RecentRow(recent, inbox.preferences, uid, online, onOpen) }
                        item(key = "requests") { RequestsRow(requests, work, uid, onRequests) }
                    }
                    if (shownTab == MessagesTab.EVENTS) eventChatItems(rows, events, inbox.preferences, uid, search, onOpen, onOptions)
                    else {
                        item(key = "section") {
                            SectionHeader(when (shownTab) { MessagesTab.GROUPS -> "Group chats"; MessagesTab.COLLABS -> "Collaborations"; else -> "All chats" },
                                unreadOnly) { unreadOnly = it }
                        }
                        if (rows.isEmpty()) item(key = "empty") {
                            MessageEmpty(if (search.isNotBlank()) Icons.Rounded.Search else Icons.Rounded.Forum,
                                when {
                                    search.isNotBlank() -> "No conversations found"
                                    unreadOnly && inTab.isNotEmpty() -> "You're all caught up"
                                    shownTab == MessagesTab.GROUPS -> "No group chats yet"
                                    shownTab == MessagesTab.COLLABS -> "Create something together"
                                    else -> "Start a conversation"
                                },
                                if (search.isBlank() && shownTab == MessagesTab.GROUPS && !unreadOnly) "Join a group in Discover to chat with its members." else "",
                                if (search.isNotBlank() || unreadOnly || shownTab == MessagesTab.GROUPS) null else "New conversation", onCompose)
                        }
                        else items(rows, key = { it.id }) { chat ->
                            InboxConversationRow(chat, inbox.preferences[chat.id] ?: ChatPreferences(), uid, false, onClick = { onOpen(chat) },
                                online = chat.kind == "direct" && chat.otherUid(uid) in online, onLongClick = { onOptions(chat) },
                                modifier = Modifier.animateItem(fadeInSpec = tween(180), placementSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                        }
                    }
                }
            }
        }
    }
}

/** Icon-over-label tabs in one bar; the selected one sits on gold, and a red count shows unread chats. */
@Composable
private fun MessagesTabBar(tab: MessagesTab, onTab: (MessagesTab) -> Unit, unread: Map<MessagesTab, Int>) {
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(SpotPalette.Surface)
        .border(1.dp, Inbox.Frame, RoundedCornerShape(22.dp)).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        MessagesTab.entries.forEach { item ->
            val chosen = item == tab
            val fill by animateColorAsState(if (chosen) Inbox.Selected else Color.Transparent, tween(180), label = "messages-tab-fill")
            val tint by animateColorAsState(if (chosen) Inbox.Ink else Inbox.Muted, tween(180), label = "messages-tab-tint")
            Column(Modifier.weight(1f).heightIn(min = 58.dp).clip(RoundedCornerShape(17.dp)).background(fill)
                .semantics { role = Role.Tab; selected = chosen }.clickable { onTab(item) }.testTag("messages-tab-${item.name}")
                .padding(vertical = 6.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box {
                    Icon(item.icon(), null, Modifier.size(22.dp), tint = tint)
                    val count = unread[item] ?: 0
                    if (count > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = 11.dp, y = (-7).dp).defaultMinSize(18.dp, 18.dp)
                        .background(Inbox.Red, CircleShape).border(1.5.dp, SpotPalette.Surface, CircleShape)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                        .semantics { contentDescription = if (count == 1) "1 unread chat" else "$count unread chats" }
                        .testTag("tab-unread-${item.name}"), contentAlignment = Alignment.Center) {
                        Text(if (count > 9) "9+" else count.toString(), color = Color.White, fontSize = 10.spotSp, lineHeight = 12.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(item.label(), style = MaterialTheme.typography.labelMedium, fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium,
                    color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** People you chat with, online first; a green dot says they're on SPOT now, a gold ring that a chat has something new. */
@Composable
private fun RecentRow(recent: List<CampusConversation>, preferences: Map<String, ChatPreferences>, uid: String, online: Set<String>,
    onOpen: (CampusConversation) -> Unit) {
    LazyRow(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)) {
        items(recent, key = { it.id }) { chat ->
            val unread = chat.unread(uid, preferences[chat.id] ?: ChatPreferences())
            val name = chat.nameFor(uid, preferences[chat.id] ?: ChatPreferences())
            // Wide enough for most first names and usernames at SPOT's largest text.
            Column(Modifier.width(76.dp).clip(RoundedCornerShape(14.dp)).clickable { onOpen(chat) }, horizontalAlignment = Alignment.CenterHorizontally) {
                RingedAvatar(name, chat.people[chat.otherUid(uid)]?.photo.orEmpty(), unread, 56, uid = chat.otherUid(uid),
                    online = chat.otherUid(uid) in online)
                Text(name.substringBefore(' '), style = MaterialTheme.typography.labelMedium, color = Inbox.Ink,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        }
    }
}

/**
 * An avatar inside a gold ring while there's something unread, and a plain gap otherwise, so rows line up. A green
 * dot means the person has SPOT open now.
 */
@Composable
private fun RingedAvatar(name: String, photo: String, ringed: Boolean, size: Int, group: Boolean = false, uid: String = "",
    online: Boolean = false) {
    Box(Modifier.size(size.dp)) {
        Box(Modifier.fillMaxSize().clip(CircleShape).then(if (ringed) Modifier.background(Inbox.Ring) else Modifier).padding(2.5.dp)
            .background(SpotPalette.Surface, CircleShape).padding(2.dp), contentAlignment = Alignment.Center) {
            ChatAvatar(name, photo, group, size - 9, uid)
        }
        if (online) Box(Modifier.align(Alignment.BottomEnd).offset(x = (-1).dp, y = (-1).dp).size((size / 4).coerceAtLeast(12).dp)
            .background(SpotPalette.Surface, CircleShape).padding(2.dp).background(Inbox.Online, CircleShape)
            .semantics { contentDescription = "Active now" }.testTag("online-$uid"))
    }
}

/**
 * Requests in one row: who's waiting for your answer, by name. It opens the full Requests page, where invitations,
 * applications and message requests are answered.
 */
@Composable
private fun RequestsRow(chatRequests: List<CampusConversation>, work: WorkInbox, uid: String, onClick: () -> Unit) {
    // Each person once, newest first: chat invitations, then applications to your events, then invitations and
    // applications that need your answer.
    val waiting = buildList {
        chatRequests.forEach { add(Triple(it.creatorId, it.people[it.creatorId]?.name, it.people[it.creatorId]?.photo.orEmpty())) }
        val now = System.currentTimeMillis()
        work.eventApplications.filter { it.awaitingReview(now) && it.organizerId == uid && it.userId != uid }
            .forEach { add(Triple(it.userId, it.displayName.ifBlank { null }, it.profileImageUrl)) }
        work.talentRequests.filter { it.canRespond(uid) }.forEach { add(Triple(it.otherUid(uid), null, "")) }
    }.distinctBy { it.first }
    val names = waiting.take(3).map { (person, name, _) -> key(person) { name ?: rememberPersonName(person) } }
    val summary = when {
        work.loading && waiting.isEmpty() -> "Checking for requests…"
        waiting.isEmpty() -> "Nothing waiting for you"
        waiting.size == 1 -> "${names[0]} is waiting"
        waiting.size == 2 -> "${names[0]} and ${names[1]} are waiting"
        waiting.size == 3 -> "${names[0]}, ${names[1]} and ${names[2]} are waiting"
        else -> "${names[0]}, ${names[1]} and others are waiting"
    }
    val active = waiting.isNotEmpty()
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("requests-row"), shape = RoundedCornerShape(20.dp),
        color = if (active) Inbox.UnreadFill else Inbox.Card, border = BorderStroke(1.dp, if (active) Inbox.UnreadLine else Inbox.CardLine)) {
        Row(Modifier.padding(start = 12.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (active) Box(Modifier.width(if (waiting.size > 1) 62.dp else 40.dp).height(40.dp)) {
                waiting.take(2).forEachIndexed { index, (person, name, photo) ->
                    Box(Modifier.offset(x = (22 * index).dp).size(40.dp).clip(CircleShape)
                        .background(if (active) Inbox.UnreadFill else Inbox.Card).padding(2.dp)) {
                        SpotAvatar(photo, name, Modifier.fillMaxSize(), person)
                    }
                }
            } else Box(Modifier.size(40.dp).clip(CircleShape).background(Inbox.Selected), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Inbox, null, tint = Inbox.GoldInk, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Requests", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Inbox.Ink)
                    if (active) Box(Modifier.padding(start = 6.dp).size(8.dp).background(Inbox.UnreadDot, CircleShape)
                        .semantics { contentDescription = "New requests" })
                }
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = Inbox.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.ChevronRight, "Open requests", tint = Inbox.GoldInk, modifier = Modifier.size(22.dp))
        }
    }
}

/** A list's title with an All / Unread switch. */
@Composable
private fun SectionHeader(title: String, unreadOnly: Boolean, onUnreadOnly: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Inbox.Ink, modifier = Modifier.weight(1f))
        Row(Modifier.clip(RoundedCornerShape(16.dp)).background(SpotPalette.Track).padding(3.dp)) {
            listOf(false to "All", true to "Unread").forEach { (value, label) ->
                val chosen = unreadOnly == value
                Box(Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(13.dp)).background(if (chosen) Inbox.Card else Color.Transparent)
                    .selectable(chosen, role = Role.RadioButton) { onUnreadOnly(value) }.padding(horizontal = 12.dp).testTag("filter-$label"),
                    contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = if (chosen) Inbox.Ink else Inbox.Muted,
                        fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
    }
}

/** What a chat row says under its name: a group's latest message carries who sent it. */
private fun rowPreview(chat: CampusConversation, uid: String, invitation: Boolean): String {
    if (invitation) return chat.inviteNote.ifBlank { if (chat.kind == "group") "Invited you to collaborate" else "Invited you to connect" }
    val text = messagePreview(chat.preview)
    if (text.isBlank()) return if (chat.isCommunity) "No messages yet" else if (chat.invitedIds.isEmpty() && chat.memberIds.size < 2) "Invitation closed" else "Invitation sent"
    if (chat.kind != "group" || chat.lastSenderId.isBlank()) return text
    val sender = if (chat.lastSenderId == uid) "You" else chat.people[chat.lastSenderId]?.name?.substringBefore(' ') ?: return text
    return "$sender: $text"
}

/**
 * A conversation in a list; unread ones get a gold ring, bold name and a dot (never a count). Holding it calls
 * [onLongClick], which opens the chat's options in Messages.
 */
@Composable
internal fun InboxConversationRow(chat: CampusConversation, preferences: ChatPreferences, uid: String, invitation: Boolean,
    onClick: () -> Unit, modifier: Modifier = Modifier, online: Boolean = false, onLongClick: (() -> Unit)? = null) {
    val unread = invitation || chat.unread(uid, preferences)
    val name = chat.nameFor(uid, preferences)
    val group = chat.kind == "group"
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(20.dp)
    Surface(modifier = modifier.fillMaxWidth().clip(shape)
            .combinedClickable(onClickLabel = "Open chat", onLongClickLabel = if (onLongClick != null) "Chat options" else null,
                onLongClick = onLongClick?.let { { haptics.performHapticFeedback(HapticFeedbackType.LongPress); it() } }, onClick = onClick)
            .testTag("conversation-${chat.id}"), shape = shape,
        color = if (unread) Inbox.UnreadFill else Inbox.Card, border = BorderStroke(1.dp, if (unread) Inbox.UnreadLine else Inbox.CardLine)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RingedAvatar(name, if (group) "" else chat.people[chat.otherUid(uid)]?.photo.orEmpty(), unread, 50, group,
                if (group) "" else chat.otherUid(uid), online)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The name gets all the room the pin and time leave, with the muted bell right after it.
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                            color = Inbox.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (preferences.muted) Icon(Icons.Rounded.NotificationsOff, "Muted", tint = Inbox.Muted, modifier = Modifier.padding(start = 4.dp).size(15.dp))
                    }
                    if (preferences.pinned) Icon(Icons.Rounded.PushPin, "Pinned", tint = Inbox.GoldInk, modifier = Modifier.padding(start = 6.dp).size(15.dp))
                    Text(shortTime(chat.updatedAt), style = MaterialTheme.typography.labelSmall, color = if (unread) Inbox.GoldInk else Inbox.Faint,
                        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium, modifier = Modifier.padding(start = 8.dp))
                }
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(rowPreview(chat, uid, invitation), style = MaterialTheme.typography.bodyMedium, color = if (unread) SpotPalette.Ink else Inbox.Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (unread) Box(Modifier.padding(start = 8.dp).size(9.dp).background(Inbox.UnreadDot, CircleShape))
                }
            }
        }
    }
}

/**
 * The Events tab: each event's chat with its date, place and your part in it, coming events first and past ones
 * after, quieter. A red dot marks unread messages.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.eventChatItems(rows: List<CampusConversation>, events: Map<String, CampusEvent>,
    preferences: Map<String, ChatPreferences>, uid: String, search: String, onOpen: (CampusConversation) -> Unit,
    onOptions: (CampusConversation) -> Unit) {
    val now = System.currentTimeMillis()
    // An event that couldn't load yet counts as coming up.
    val (past, upcoming) = rows.partition { chat -> events[chat.communityId]?.let { it.startsAt < now } == true }
    val coming = upcoming.sortedWith(compareBy<CampusConversation> { events[it.communityId]?.startsAt ?: Long.MAX_VALUE }.thenByDescending { it.updatedAt })
    val done = past.sortedByDescending { events[it.communityId]?.startsAt ?: 0L }
    if (rows.isEmpty()) {
        item(key = "events-empty") {
            MessageEmpty(if (search.isNotBlank()) Icons.Rounded.Search else Icons.Rounded.Event,
                if (search.isNotBlank()) "No event chats found" else "No event chats yet",
                if (search.isNotBlank()) "" else "You join an event's chat once its organizer accepts your application.")
        }
        return
    }
    if (coming.isNotEmpty()) item(key = "label-upcoming") { EventLabel("UPCOMING", Inbox.GoldInk) }
    items(coming, key = { it.id }) { chat ->
        EventChatRow(chat, events[chat.communityId], preferences[chat.id] ?: ChatPreferences(), uid, past = false, onClick = { onOpen(chat) },
            modifier = Modifier.animateItem(), onLongClick = { onOptions(chat) })
    }
    if (done.isNotEmpty()) item(key = "label-past") { EventLabel("PAST", Inbox.Faint) }
    items(done, key = { it.id }) { chat ->
        EventChatRow(chat, events[chat.communityId], preferences[chat.id] ?: ChatPreferences(), uid, past = true, onClick = { onOpen(chat) },
            modifier = Modifier.animateItem(), onLongClick = { onOptions(chat) })
    }
}

@Composable
private fun EventLabel(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = color,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp))
}

@Composable
private fun EventChatRow(chat: CampusConversation, event: CampusEvent?, preferences: ChatPreferences, uid: String, past: Boolean,
    onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: () -> Unit = {}) {
    val unread = chat.unread(uid, preferences)
    val title = event?.title ?: chat.nameFor(uid, preferences)
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(20.dp)
    Surface(modifier = modifier.fillMaxWidth().clip(shape)
            .combinedClickable(onClickLabel = "Open chat", onLongClickLabel = "Chat options",
                onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() }, onClick = onClick)
            .testTag("conversation-${chat.id}"), shape = shape,
        color = when { past -> Inbox.Past; unread -> Inbox.UnreadFill; else -> Inbox.Card },
        border = BorderStroke(1.dp, if (unread && !past) Inbox.UnreadLine else Inbox.CardLine)) {
        Row(Modifier.padding(12.dp)) {
            Column(Modifier.width(52.dp).clip(RoundedCornerShape(14.dp)).background(if (past) Inbox.PastTile else Inbox.Selected)
                .padding(top = 7.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (event != null) {
                    Text(eventDate(event.startsAt, "MMM").uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp, color = if (past) Inbox.Muted else Inbox.GoldInk)
                    Text(eventDate(event.startsAt, "d"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        color = if (past) Inbox.Muted else Inbox.Ink)
                } else Icon(Icons.Rounded.Event, null, tint = Inbox.GoldInk, modifier = Modifier.padding(vertical = 8.dp).size(22.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = if (unread && !past) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (past) SpotPalette.Muted else Inbox.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Row(Modifier.padding(start = 8.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (chat.preview.isNotBlank()) Text(shortTime(chat.updatedAt), style = MaterialTheme.typography.labelSmall,
                            color = if (unread) (if (SpotPalette.dark) SpotPalette.Heart else Color(0xFFB5383C)) else Inbox.Faint)
                        if (unread) Box(Modifier.padding(start = 6.dp).size(9.dp).background(Inbox.Red, CircleShape)
                            .semantics { contentDescription = "Unread" })
                    }
                }
                if (event != null && !past) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Place, null, tint = Inbox.Muted, modifier = Modifier.size(14.dp))
                    Text("${event.location} · ${eventDate(event.startsAt, "EEE h:mm a")}", style = MaterialTheme.typography.bodySmall,
                        color = Inbox.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
                }
                Text(rowPreview(chat, uid, false), style = MaterialTheme.typography.bodyMedium,
                    color = if (unread && !past) SpotPalette.Ink else Inbox.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (event != null && !past) Text(if (event.organizerId == uid) "Organizer" else "Performing", style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold, color = SpotPalette.Muted,
                    modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(10.dp)).background(SpotPalette.Track).padding(horizontal = 9.dp, vertical = 3.dp))
            }
        }
    }
}

/** Every request, opened from the Requests row: invitations, applications and message requests, each answerable here. */
@Composable
internal fun RequestsPage(inbox: MessagesInbox, work: WorkInbox, uid: String, busy: Boolean, actions: RequestActions, onBack: () -> Unit) {
    val requests = inbox.requests.filter { it.creatorId !in inbox.blockedIds }
    Column(Modifier.fillMaxSize().spotAmbientBackground(Inbox.Ground).testTag("requests-page")) {
        SpotScreenHeader("Requests", onBack = onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            requestItems(work, requests, inbox.preferences, uid, "", busy, actions)
        }
    }
}
