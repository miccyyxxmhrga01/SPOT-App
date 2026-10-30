package com.example.spotapp.ui.theme.screens.messages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.components.LifecycleDisposableEffect
import com.example.spotapp.ui.theme.components.SkeletonKind
import com.example.spotapp.ui.theme.components.SpotAvatar
import com.example.spotapp.ui.theme.components.SpotLoading
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.discover.EventApplicationCard

/** What the Requests tab can do with an item; the screen runs each action and shows any failure. */
internal class RequestActions(
    val openChat: (CampusConversation) -> Unit,
    val openRequest: (TalentRequest) -> Unit,
    val respond: (TalentRequest, String) -> Unit,
    val message: (String) -> Unit,
    val review: (EventApplication, Boolean) -> Unit,
    val openEvent: (String) -> Unit,
    val profile: (SpotUiProfile) -> Unit,
    val retry: () -> Unit
)

/**
 * The Requests tab: applications to perform at events, collaboration invitations (event invitations from Spotters
 * and invitations to collaboration chats), job and audition applications, and message requests. Nothing here
 * shows a total.
 */
internal fun LazyListScope.requestItems(work: WorkInbox, chatRequests: List<CampusConversation>,
    preferences: Map<String, ChatPreferences>, uid: String, search: String, busy: Boolean, actions: RequestActions) {
    fun matches(vararg texts: String) = search.isBlank() || texts.any { it.contains(search, true) }
    val applications = work.eventApplications.filter { matches(it.displayName, it.eventTitle) }
    val invitations = work.talentRequests.filter { it.kind == "invitation" && matches(it.title) }
    val jobApplications = work.talentRequests.filter { it.kind == "application" && matches(it.title) }
    fun chatMatches(chat: CampusConversation) = matches(chat.nameFor(uid, preferences[chat.id] ?: ChatPreferences()), chat.inviteNote)
    val collabInvites = chatRequests.filter { it.kind == "group" && chatMatches(it) }
    val messageRequests = chatRequests.filter { it.kind != "group" && chatMatches(it) }

    if (work.error.isNotBlank()) item(key = "work-error") {
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text(work.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                TextButton(actions.retry) { Text("Retry") }
            }
        }
    }
    if (work.loading) item(key = "work-loading") { SpotLoading(true, SkeletonKind.ROWS) }

    if (applications.isNotEmpty()) item(key = "label-applications") { RequestLabel("Event applications") }
    items(applications, key = { "application-${it.eventId}-${it.userId}" }) { application ->
        val mine = application.userId == uid
        EventApplicationCard(application, actions.profile, Modifier.animateItem(),
            eventLine = if (mine) "${application.eventTitle} · ${statusWord(
                if (application.pending && !application.awaitingReview(System.currentTimeMillis())) "closed" else application.status)}"
                else "Wants to perform at ${application.eventTitle}",
            mine = mine, busy = busy, onReview = if (!mine && application.organizerId == uid) { accept -> actions.review(application, accept) } else null,
            onOpenEvent = { actions.openEvent(application.eventId) })
    }

    if (invitations.isNotEmpty() || collabInvites.isNotEmpty()) item(key = "label-invitations") { RequestLabel("Collaboration invitations") }
    items(invitations, key = { "request-${it.id}" }) { request ->
        TalentRequestCard(request, uid, busy, actions, Modifier.animateItem())
    }
    items(collabInvites, key = { "chat-${it.id}" }) { chat ->
        InboxConversationRow(chat, preferences[chat.id] ?: ChatPreferences(), uid, true, onClick = { actions.openChat(chat) }, modifier = Modifier.animateItem())
    }

    if (jobApplications.isNotEmpty()) item(key = "label-jobs") { RequestLabel("Job & audition applications") }
    items(jobApplications, key = { "request-${it.id}" }) { request ->
        TalentRequestCard(request, uid, busy, actions, Modifier.animateItem())
    }

    if (messageRequests.isNotEmpty()) item(key = "label-messages") { RequestLabel("Message requests") }
    items(messageRequests, key = { "chat-${it.id}" }) { chat ->
        InboxConversationRow(chat, preferences[chat.id] ?: ChatPreferences(), uid, true, onClick = { actions.openChat(chat) }, modifier = Modifier.animateItem())
    }

    if (!work.loading && work.error.isBlank() && applications.isEmpty() && invitations.isEmpty() && jobApplications.isEmpty()
        && collabInvites.isEmpty() && messageRequests.isEmpty()) item(key = "requests-empty") {
        if (search.isNotBlank()) MessageEmpty(Icons.Rounded.Search, "No requests found", "")
        else MessageEmpty(Icons.Rounded.Inbox, "You're all caught up", "Collaboration invitations, applications and message requests appear here.")
    }
}

@Composable
private fun RequestLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = SpotPalette.Muted, modifier = Modifier.padding(start = 6.dp, top = 10.dp))
}

private fun statusWord(status: String) = when (status) {
    "accepted" -> "Accepted"; "declined" -> "Declined"; "withdrawn" -> "Withdrawn"; "closed" -> "Closed"; else -> "Pending"
}

/** A collaboration invitation or job application, with the other person's name and the answer it needs. */
@Composable
private fun TalentRequestCard(request: TalentRequest, uid: String, busy: Boolean, actions: RequestActions, modifier: Modifier = Modifier) {
    val other = request.otherUid(uid)
    val person = rememberPersonName(other)
    val invitation = request.kind == "invitation"
    val sent = uid == if (invitation) request.seekerId else request.creatorId
    val headline = when {
        invitation && sent -> "You invited $person to ${request.title}"
        invitation -> "$person invited you to perform at ${request.title}"
        sent -> "You applied to ${request.title}"
        else -> "$person applied to ${request.title}"
    }
    val respond = request.canRespond(uid)
    Surface(onClick = { actions.openRequest(request) }, modifier = modifier.fillMaxWidth().testTag("talent-request-${request.id}"),
        shape = RoundedCornerShape(20.dp), color = if (respond) SpotPalette.GoldWash else SpotPalette.Surface,
        border = BorderStroke(1.dp, if (respond) SpotPalette.GoldLine else SpotPalette.Line)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpotAvatar("", person, Modifier.size(44.dp), other)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(if (invitation) "EVENT INVITATION" else "APPLICATION", style = MaterialTheme.typography.labelSmall, color = SpotPalette.GoldInk)
                    Text(headline, style = MaterialTheme.typography.titleSmall, color = SpotPalette.Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(statusWord(request.status) + if (respond) " · your answer is needed" else "", style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted)
                }
                Icon(Icons.Rounded.ChevronRight, "View details", tint = SpotPalette.Muted, modifier = Modifier.size(20.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (respond) {
                    Button({ actions.respond(request, "accepted") }, enabled = !busy, contentPadding = PaddingValues(horizontal = 14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD1AD32), contentColor = SpotPalette.OnGold)) { Text("Accept") }
                    OutlinedButton({ actions.respond(request, "declined") }, enabled = !busy, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Decline") }
                }
                TextButton({ actions.message(other) }, enabled = !busy) {
                    Icon(Icons.Rounded.ChatBubbleOutline, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Message")
                }
            }
        }
    }
}

/** The other person's current display name, kept live like avatars are. */
@Composable
internal fun rememberPersonName(uid: String): String {
    val identity = remember(uid) { ProfileIdentityStore.identity(uid) }
    val current by identity.collectAsStateWithLifecycle()
    LifecycleDisposableEffect(uid) {
        val release = ProfileIdentityStore.acquire(uid)
        onDispose { release() }
    }
    return current?.displayName?.ifBlank { null } ?: "A SPOT member"
}
