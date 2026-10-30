package com.example.spotapp.ui.theme.screens.discover

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.components.LifecycleDisposableEffect
import com.example.spotapp.ui.theme.components.SkeletonKind
import com.example.spotapp.ui.theme.components.SpotAuthButton
import com.example.spotapp.ui.theme.components.SpotAuthFieldShape
import com.example.spotapp.ui.theme.components.SpotEmptyState
import com.example.spotapp.ui.theme.components.SpotLoading
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.components.spotAuthFieldColors
import com.example.spotapp.ui.theme.models.SpotUiProfile
import kotlinx.coroutines.*

/** Confirms an application: the event, its talent fee and an optional note for the organizer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ApplyToEventSheet(event: CampusEvent, busy: Boolean, onApply: (String) -> Unit, onDismiss: () -> Unit) {
    var note by rememberSaveable(event.eventId) { mutableStateOf("") }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !busy })
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheet, containerColor = SpotPalette.Surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        ApplySheetBody(event, note, { note = it.take(500) }, busy, onSend = { onApply(note) }, onClose = { if (!busy) onDismiss() })
    }
}

/** The apply sheet's content, apart from the sheet itself so previews can show it. */
@Composable
internal fun ApplySheetBody(event: CampusEvent, note: String, onNote: (String) -> Unit, busy: Boolean, onSend: () -> Unit, onClose: () -> Unit,
    modifier: Modifier = Modifier) {
    val host = organizerFirstName(event)
    Column(modifier.navigationBarsPadding().imePadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp).testTag("apply-sheet"),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Apply to perform", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = SpotPalette.Ink)
            Surface(onClick = onClose, modifier = Modifier.size(44.dp), shape = CircleShape, color = SpotPalette.Track, enabled = !busy) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, "Close", Modifier.size(18.dp), tint = SpotPalette.Ink) }
            }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SpotPalette.Ivory)
            .border(1.dp, SpotPalette.Line, RoundedCornerShape(16.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            EventPoster(event.imageUrl, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.padding(start = 12.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium, color = SpotPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${eventDate(event.startsAt, "EEE, MMM d · h:mm a")} · ${shortPlace(event.location)}",
                    style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("Talent fee · " + when (event.talentFee) {
            TalentFee.PAID -> event.talentFeeBudget.ifBlank { "Paid" }
            TalentFee.FREE -> "Free"
            else -> "Not specified"
        }, Modifier.clip(CircleShape).background(SpotPalette.GoldSoft).padding(horizontal = 12.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelLarge, color = SpotPalette.GoldInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Note for $host", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = SpotPalette.Ink)
                Text("Optional", style = MaterialTheme.typography.labelMedium, color = SpotPalette.Muted)
            }
            OutlinedTextField(note, onNote, Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag("apply-note"),
                placeholder = { Text("What will you perform? Add a link to a clip if you have one.") }, minLines = 4, maxLines = 6,
                enabled = !busy, textStyle = MaterialTheme.typography.bodyLarge, shape = SpotAuthFieldShape,
                colors = spotAuthFieldColors().copy(unfocusedContainerColor = SpotPalette.Surface))
            Text("${note.length} / 500", Modifier.align(Alignment.End), style = MaterialTheme.typography.labelMedium, color = SpotPalette.Muted)
        }
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = SpotPalette.GreenInk)
            Text("If $host accepts, you get a slot and join the event chat.", Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted)
        }
        SpotAuthButton("Send application", loading = busy, onClick = onSend)
    }
}

/**
 * One application, under the Creator's name. [eventLine] replaces the "@username · 2h ago" line (Messages names the
 * event there); [onReview] shows Decline and Accept while it waits for review; [applicant] adds the username and talents.
 */
@Composable
internal fun EventApplicationCard(application: EventApplication, onProfile: (SpotUiProfile) -> Unit, modifier: Modifier = Modifier,
    eventLine: String? = null, mine: Boolean = false, busy: Boolean = false, onReview: ((Boolean) -> Unit)? = null,
    onOpenEvent: (() -> Unit)? = null, applicant: Applicant? = null) {
    val now = System.currentTimeMillis()
    val waiting = application.awaitingReview(now)
    val reviewing = onReview != null && waiting
    Surface(modifier.fillMaxWidth().testTag("event-application-${application.eventId}-${application.userId}"), color = SpotPalette.Surface,
        shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, if (reviewing) SpotPalette.GoldLine else SpotPalette.Line)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) {
                if (mine) onOpenEvent?.invoke()
                else onProfile(SpotUiProfile(application.userId, application.displayName, applicant?.username.orEmpty(), "", "",
                    application.profileImageUrl, accountType = AccountType.STUDENT))
            }, verticalAlignment = Alignment.CenterVertically) {
                EventPersonAvatar(application.profileImageUrl, application.displayName, application.userId, 44.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(if (mine) "You applied" else application.displayName.ifBlank { "Creator" }, style = MaterialTheme.typography.titleMedium,
                        color = SpotPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(eventLine ?: listOfNotNull(applicant?.username?.takeIf { it.isNotBlank() }?.let { "@$it" },
                        postTimeLabel(application.createdAt, now)).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (reviewing) Icon(Icons.Rounded.ChevronRight, null, tint = SpotPalette.Muted)
                else ApplicationStatus(if (application.pending && !waiting) "closed" else application.status)
            }
            applicant?.talents?.takeIf { it.isNotEmpty() }?.let { talents ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    talents.take(3).forEach { talent ->
                        Text(talent, Modifier.clip(CircleShape).background(SpotPalette.Track).padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = EventGreyInk, maxLines = 1)
                    }
                }
            }
            if (application.note.isNotBlank()) Text(application.note, style = MaterialTheme.typography.bodyLarge, color = SpotPalette.Ink.copy(alpha = .86f))
            if (onOpenEvent != null && !mine) TextButton(onOpenEvent, contentPadding = PaddingValues(0.dp)) { Text("View event", color = SpotPalette.GoldInk) }
            if (reviewing) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ onReview?.invoke(false) }, Modifier.weight(1f).heightIn(min = 44.dp), enabled = !busy, shape = CircleShape,
                    border = BorderStroke(1.5.dp, SpotPalette.Line), colors = ButtonDefaults.outlinedButtonColors(contentColor = SpotPalette.Ink)) {
                    Text("Decline", fontWeight = FontWeight.SemiBold)
                }
                Button({ onReview?.invoke(true) }, Modifier.weight(1f).heightIn(min = 44.dp), enabled = !busy, shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = SpotPalette.Gold, contentColor = SpotPalette.OnGold)) {
                    Text("Accept", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ApplicationStatus(status: String) {
    val (label, fill, ink) = when (status) {
        "accepted" -> Triple("Accepted", SpotPalette.GreenFill, SpotPalette.GreenInk)
        "declined" -> Triple("Declined", SpotPalette.Track, EventGreyInk)
        // Still pending when the event started, so it can't be answered any more.
        "closed" -> Triple("Closed", SpotPalette.Track, EventGreyInk)
        else -> Triple("Pending", SpotPalette.GoldWash, SpotPalette.GoldInk)
    }
    Text(label, Modifier.padding(start = 8.dp).clip(CircleShape).background(fill).padding(horizontal = 11.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1)
}

internal enum class ApplicationsTab { WAITING, EARLIER }

/** The organizer's review page: the event and its slots, then the applications waiting for an answer or answered before. */
@Composable
internal fun EventApplications(event: CampusEvent, close: () -> Unit, onProfile: (SpotUiProfile) -> Unit) {
    val repo = remember { DiscoverRepository.shared }
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<EventApplication>?>(null) }
    var applicants by remember { mutableStateOf<Map<String, Applicant>>(emptyMap()) }
    var current by remember { mutableStateOf(event) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableStateOf(ApplicationsTab.WAITING) }
    LifecycleDisposableEffect(event.eventId, revision) {
        val listeners = listOf(repo.listenApplications(event.eventId, { rows = it; error = "" }, { error = it }),
            // Accepting changes the slots, so the summary follows the event.
            repo.listenEvent(event.eventId, { it?.let { e -> current = e } }, { }))
        onDispose { listeners.forEach { it.remove() } }
    }
    LaunchedEffect(rows) {
        val missing = rows.orEmpty().map { it.userId }.filter { it !in applicants }
        if (missing.isNotEmpty()) applicants = applicants +
            try { repo.applicants(missing) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyMap() }
    }
    fun review(application: EventApplication, accept: Boolean) {
        if (busy != null) return
        busy = application.userId; error = ""
        scope.launch {
            try { withTimeout(30_000) { repo.reviewApplication(event.eventId, application.userId, accept) } }
            catch (_: TimeoutCancellationException) { error = "That took too long. Refresh to check the application." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = if (e is IllegalStateException) e.message.orEmpty() else "Couldn't review this application. Please retry." }
            finally { busy = null }
        }
    }
    EventWindow(close, busy != null) {
        ApplicationsScreen(current, rows, applicants, tab, { tab = it }, busy != null, error, System.currentTimeMillis(), onBack = close,
            onRetry = { revision++ }, onReview = ::review, onProfile = onProfile)
    }
}

/** The review page drawn from its data, for the app, previews and the review test. */
@Composable
internal fun ApplicationsScreen(event: CampusEvent, rows: List<EventApplication>?, applicants: Map<String, Applicant>, tab: ApplicationsTab,
    onTab: (ApplicationsTab) -> Unit, busy: Boolean, error: String, now: Long, onBack: () -> Unit, onRetry: () -> Unit,
    onReview: (EventApplication, Boolean) -> Unit, onProfile: (SpotUiProfile) -> Unit) {
    val waiting = rows.orEmpty().filter { it.awaitingReview(now) }
    val earlier = rows.orEmpty().filterNot { it.awaitingReview(now) }
    val shown = if (tab == ApplicationsTab.WAITING) waiting else earlier
    Column(Modifier.fillMaxSize()) {
        EventPageHeader("Applications", onBack, enabled = !busy)
        LazyColumn(Modifier.weight(1f).navigationBarsPadding().testTag("event-applications"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item(key = "summary") {
                Surface(shape = RoundedCornerShape(18.dp), color = SpotPalette.Surface, border = BorderStroke(1.dp, SpotPalette.Line)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EventPoster(event.imageUrl, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(event.title, style = MaterialTheme.typography.titleMedium, color = SpotPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(eventDate(event.startsAt, "EEE, MMM d · h:mm a"), style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted)
                            }
                        }
                        EventSlotsBar(event)
                        Text(buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = SpotPalette.Ink)) {
                                append(event.participantLimit?.let { "${event.participantCount} of $it accepted" } ?: "${event.participantCount} accepted")
                            }
                            append(" · ${slotsLeft(event)}")
                        }, style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted)
                    }
                }
            }
            item(key = "tabs") {
                Row(Modifier.fillMaxWidth().clip(CircleShape).background(SpotPalette.Track).padding(4.dp).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ApplicationsTab.entries.forEach { option ->
                        val chosen = option == tab
                        Box(Modifier.weight(1f).heightIn(min = 40.dp).clip(CircleShape).background(if (chosen) SpotPalette.Surface else Color.Transparent)
                            .selectable(chosen, role = Role.Tab) { onTab(option) }.testTag("applications-${option.name.lowercase()}"),
                            contentAlignment = Alignment.Center) {
                            Text(if (option == ApplicationsTab.WAITING) "Waiting · ${waiting.size}" else "Earlier · ${earlier.size}",
                                style = MaterialTheme.typography.labelLarge, color = if (chosen) SpotPalette.Ink else SpotPalette.Muted,
                                fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium)
                        }
                    }
                }
            }
            if (error.isNotBlank()) item(key = "error") { CommunityError(error, onRetry) }
            if (rows == null && error.isBlank()) item(key = "loading") { SpotLoading(true, SkeletonKind.ROWS) }
            if (rows != null && shown.isEmpty()) item(key = "empty") {
                SpotEmptyState(Icons.Rounded.HowToReg, if (tab == ApplicationsTab.WAITING) "No applications waiting" else "Nothing answered yet",
                    message = if (tab == ApplicationsTab.WAITING) "Creators who apply to perform appear here." else "Applications you've answered show up here.")
            }
            items(shown, key = { it.userId }) { application ->
                EventApplicationCard(application, onProfile, busy = busy, applicant = applicants[application.userId],
                    onReview = if (tab == ApplicationsTab.WAITING) { accept -> onReview(application, accept) } else null)
            }
        }
    }
}

/** Events a Spotter posted, opened from the Spotter badge on their profile. [viewer] is the signed-in member. */
@Composable
internal fun PostedEvents(organizer: SpotUiProfile, viewer: SpotUiProfile, close: () -> Unit, onProfile: (SpotUiProfile) -> Unit,
    onChat: (String) -> Unit) {
    var events by remember { mutableStateOf(emptyList<CampusEvent>()) }
    var cursor by remember { mutableStateOf<PageCursor?>(null) }
    var more by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(organizer.uid, page, revision) {
        loading = true; error = ""
        try {
            val result = DiscoverRepository.shared.eventsBy(organizer.uid, if (page == 0) null else cursor)
            events = (if (page == 0) result.items else events + result.items).distinctBy { it.eventId }
            cursor = result.cursor; more = result.hasMore
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Events couldn't load. Check your connection and retry." }
        finally { loading = false }
    }
    val now = System.currentTimeMillis()
    val mine = organizer.uid == viewer.uid
    CommunityWindow(if (mine) "Your events" else "Events by ${organizer.displayName.substringBefore(' ')}", close) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).navigationBarsPadding().testTag("posted-events"),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { CommunityError(error) { page = 0; revision++ } }
            if (!loading && error.isBlank() && events.isEmpty()) item {
                SpotEmptyState(Icons.Rounded.Event, "No events yet", message = if (mine) "Events you post appear here." else "Events this Spotter posts appear here.")
            }
            items(events, key = { it.eventId }) { event ->
                Card(onClick = { opened = event.eventId }, colors = CardDefaults.cardColors(containerColor = SpotPalette.Surface),
                    border = BorderStroke(1.dp, SpotPalette.Line), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    CommunityCover(event.imageUrl, true, Modifier.fillMaxWidth().height(120.dp), title = event.title)
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CommunityBadge(if (event.cancelled) "Cancelled" else if (event.startsAt > now) "Upcoming" else "Past")
                            if (event.talentFee != null) CommunityBadge(if (event.talentFee == TalentFee.PAID) "Paid" else "Free")
                        }
                        Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(eventDate(event.startsAt), style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted)
                        Text(event.location, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            item {
                if (loading) SpotLoading(true, SkeletonKind.CARDS)
                if (more && !loading) TextButton({ page++ }, Modifier.fillMaxWidth()) { Text("Load more") }
            }
        }
    }
    opened?.let { id -> EventPage(id, viewer, { opened = null }, { opened = null; onProfile(it) }, onChat) }
}

@Preview(name = "Apply sheet", widthDp = 360, heightDp = 640, fontScale = 1.15f)
@Composable
private fun ApplySheetPreview() = SPOTAppTheme(dark = false) {
    Box(Modifier.background(SpotPalette.Surface)) { ApplySheetBody(EventSamples.acousticNight(), "", {}, false, {}, {}) }
}

@Preview(name = "Applications · waiting", widthDp = 360, heightDp = 900, fontScale = 1.15f)
@Composable
private fun ApplicationsPreview() = SPOTAppTheme(dark = false) {
    Box(Modifier.background(SpotPalette.Ivory)) {
        ApplicationsScreen(EventSamples.acousticNight(), EventSamples.applications(), EventSamples.applicants, ApplicationsTab.WAITING, {},
            false, "", System.currentTimeMillis(), {}, {}, { _, _ -> }, {})
    }
}
