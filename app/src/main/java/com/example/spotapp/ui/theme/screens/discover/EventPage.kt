package com.example.spotapp.ui.theme.screens.discover

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.messages.ShareEventSheet
import kotlinx.coroutines.*

/** What the event page's buttons do. The page itself only draws, so previews and the review test can show it. */
internal class EventPageActions(
    val back: () -> Unit = {}, val share: () -> Unit = {}, val apply: () -> Unit = {}, val withdraw: () -> Unit = {},
    val leave: () -> Unit = {}, val chat: () -> Unit = {}, val performers: () -> Unit = {}, val applications: () -> Unit = {},
    val edit: () -> Unit = {}, val remove: () -> Unit = {}, val organizer: () -> Unit = {}, val retry: () -> Unit = {}
)

/** Where a Creator (or a Spotter who isn't organizing) stands, which picks the banner and the pinned action. */
private enum class Standing { APPLY, APPLIED, GOING, DECLINED, CLOSED, CANCELLED, FULL, ENDED, SPOTTER }

/** Whether anyone besides the organizer is in the event, so it has to be cancelled rather than deleted. */
internal fun eventInvolved(event: CampusEvent, applications: List<EventApplication>?, invites: Int?): Boolean =
    event.participantCount > 0 || applications.orEmpty().any { it.pending } || (invites ?: 0) > 0

/**
 * An event's page. Creators apply, withdraw or leave, and accepted performers get the chat. The organizer reviews
 * applications, edits the event until it starts, and deletes it while nobody is involved or cancels it otherwise.
 */
@Composable
internal fun EventPage(id: String, profile: SpotUiProfile, close: () -> Unit, onProfile: (SpotUiProfile) -> Unit,
    onChat: (String) -> Unit, startApply: Boolean = false) {
    val repo = remember { DiscoverRepository.shared }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var event by remember { mutableStateOf<CampusEvent?>(null) }
    var loading by remember { mutableStateOf(true) }
    var joined by remember { mutableStateOf<Boolean?>(null) }
    var application by remember { mutableStateOf<EventApplication?>(null) }
    var applicationLoaded by remember { mutableStateOf(false) }
    var applications by remember { mutableStateOf<List<EventApplication>?>(null) }
    var invites by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var applying by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    // Opened from an "Apply to perform" button: the apply sheet opens once we know the Creator can apply.
    var autoApplied by rememberSaveable { mutableStateOf(false) }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(id) { while (true) { clock = System.currentTimeMillis(); delay(15_000) } }
    LifecycleDisposableEffect(id, revision) {
        error = ""; loading = true; joined = null
        val failed: (String) -> Unit = { error = it; loading = false }
        val registrations = listOf(
            repo.listenEvent(id, { event = it; loading = false }, failed),
            repo.listenMyApplication(id, { application = it; applicationLoaded = true }, failed),
            repo.listenMembership(id, true, { joined = it }, failed))
        onDispose { registrations.forEach { it.remove() } }
    }
    val organizing = event != null && event?.organizerId == profile.uid
    LifecycleDisposableEffect(id, organizing, revision) {
        val listener = if (organizing) repo.listenApplications(id, { applications = it }, { }) else null
        onDispose { listener?.remove() }
    }
    LaunchedEffect(id, organizing, revision, event?.cancelled) {
        invites = if (!organizing) null else try { repo.pendingInvitations(id) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }
    fun act(failure: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = ""
        scope.launch {
            try { withTimeout(30_000) { action() } }
            catch (_: TimeoutCancellationException) { error = "The request took too long. Reconnect and retry." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = if (e is IllegalStateException) e.message.orEmpty() else failure }
            finally { busy = false }
        }
    }
    LaunchedEffect(startApply, event, joined, applicationLoaded, application) {
        val e = event ?: return@LaunchedEffect
        if (!startApply || autoApplied || joined != false || !applicationLoaded) return@LaunchedEffect
        autoApplied = true
        if (application == null && profile.accountType != AccountType.TALENT_SEEKER && e.organizerId != profile.uid && !e.cancelled &&
            !e.isFull && e.startsAt > System.currentTimeMillis()) applying = true
    }
    EventWindow(close, busy) {
        val e = event
        if (e == null) Column(Modifier.fillMaxSize()) {
            EventPageHeader("Event", close)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = CommunityGold)
            Box(Modifier.padding(20.dp)) { CommunityError(error) { revision++ } }
            if (!loading && error.isBlank()) SpotEmptyState(Icons.Rounded.EventBusy, "This event is no longer available")
        }
        else EventPageScreen(e, profile, joined == true, application, applications, invites, busy, error,
            ready = !busy && joined != null && applicationLoaded && error.isBlank(), now = clock, actions = EventPageActions(
                back = close, share = { sharing = true }, apply = { applying = true },
                withdraw = { act("Couldn't withdraw your application. Please retry.") { repo.withdrawApplication(id) } },
                leave = { leaving = true },
                chat = { act("Couldn't open the chat. Please retry.") { onChat(repo.openChat(id, true)) } },
                performers = { panel = "performers" }, applications = { panel = "applications" }, edit = { editing = true },
                remove = { removing = true },
                organizer = { onProfile(SpotUiProfile(e.organizerId, e.organizerName, "", "", "", e.organizerProfileImageUrl)) },
                retry = { revision++ }))
    }
    val shown = event
    // Into your SPOT chats as an event card, or to other apps as text.
    if (sharing && shown != null) ShareEventSheet(shown, onMoreApps = { sharing = false; shareEvent(context, shown) },
        onDismiss = { sharing = false })
    // The sheet closes first, so a failure shows on the page with its Retry.
    if (applying && shown != null) ApplyToEventSheet(shown, busy, onApply = { note ->
        applying = false
        act("Couldn't send your application. Please retry.") { repo.applyToEvent(id, note) }
    }, onDismiss = { applying = false })
    if (removing && shown != null) {
        if (eventInvolved(shown, applications, invites)) SpotConfirmDialog(Icons.Rounded.EventBusy, "Cancel this event?",
            "Everyone who joined, applied or was invited is told. Performers keep it and its chat. This can't be undone.",
            "Cancel event", onDismiss = { removing = false }, danger = true, dismiss = "Keep event") {
            removing = false; act("Couldn't cancel the event. Please retry.") { repo.cancelEvent(id) }
        }
        else SpotConfirmDialog(Icons.Rounded.DeleteOutline, "Delete this event?",
            "Nobody has applied or been invited yet, so it's removed for good.",
            "Delete", onDismiss = { removing = false }, danger = true, dismiss = "Keep event") {
            removing = false; act("Couldn't delete the event. Please retry.") { repo.deleteEvent(id); close() }
        }
    }
    if (editing && shown != null) EventEditor(profile, close = { editing = false }, saved = { editing = false }, existing = shown,
        audience = EventAudience(shown.participantCount.toInt(), applications.orEmpty().count { it.awaitingReview(clock) }, invites ?: 0),
        onRemove = { editing = false; removing = true }, removeDeletes = !eventInvolved(shown, applications, invites))
    if (leaving) SpotConfirmDialog(Icons.AutoMirrored.Rounded.ExitToApp, "Leave this event?",
        "Your slot opens up and you leave the event chat. To come back, you'd apply again.", "Leave",
        onDismiss = { leaving = false }, danger = true, dismiss = "Stay") {
        leaving = false; act("Couldn't leave the event. Please retry.") { repo.leaveEvent(id) }
    }
    when (panel) {
        "applications" -> shown?.let { EventApplications(it, close = { panel = null }, onProfile = { panel = null; onProfile(it) }) }
        "performers" -> CommunityPeople(id, true, false, close = { panel = null }, onProfile = { panel = null; onProfile(it) })
    }
}

private fun shareEvent(context: Context, event: CampusEvent) {
    val text = "${event.title}\n${eventDate(event.startsAt, "EEE, MMM d · h:mm a")} · ${event.location}\n${SpotLinks.event(event.eventId)}"
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share event"))
}

/**
 * The event page drawn from its data: the poster with Back and Share over it, then a sheet with the tags, title,
 * where you stand and the details. Creators get their main action pinned to the bottom; the organizer gets the
 * counts, the review button and their tools in the page.
 */
@Composable
internal fun EventPageScreen(event: CampusEvent, profile: SpotUiProfile, joined: Boolean, application: EventApplication?,
    applications: List<EventApplication>?, invites: Int?, busy: Boolean, error: String, ready: Boolean, now: Long,
    actions: EventPageActions, modifier: Modifier = Modifier) {
    val organizer = event.organizerId == profile.uid
    val creator = profile.accountType != AccountType.TALENT_SEEKER
    val ended = event.startsAt <= now
    val pending = application?.status == "pending"
    val standing = when {
        organizer -> null
        joined -> Standing.GOING
        event.cancelled -> Standing.CANCELLED
        pending && ended -> Standing.CLOSED
        pending -> Standing.APPLIED
        application?.status == "declined" -> Standing.DECLINED
        ended -> Standing.ENDED
        !creator -> Standing.SPOTTER
        event.isFull -> Standing.FULL
        else -> Standing.APPLY
    }
    Column(modifier.fillMaxSize().testTag("event-page")) {
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                EventPoster(event.imageUrl, Modifier.fillMaxWidth().height(230.dp))
                Column(Modifier.offset(y = (-24).dp).fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(SpotPalette.Ivory).padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (organizer) {
                            EventTag("Your event", strong = true)
                            EventTag("${eventDate(event.startsAt, "EEE, MMM d")} · ${eventClock(event.startsAt)}")
                        } else {
                            if (event.category.isNotBlank()) EventTag(event.category)
                            when (event.talentFee) {
                                TalentFee.PAID -> EventTag("Paid", strong = true)
                                TalentFee.FREE -> EventTag("Free", strong = true)
                                else -> Unit
                            }
                        }
                    }
                    Text(event.title, Modifier.offset(y = (-4).dp), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                        color = SpotPalette.Ink)
                    if (error.isNotBlank()) CommunityError(error, actions.retry)
                    if (organizer) OrganizerDetails(event, applications, invites, busy, now, actions)
                    else CreatorDetails(event, standing!!, application, busy, ended, actions)
                }
            }
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                HeroButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", actions.back)
                HeroButton(Icons.Rounded.Share, "Share event", actions.share)
            }
        }
        when (standing) {
            Standing.APPLY -> EventPinnedBar {
                Text("${organizerFirstName(event)} reviews each application. If you're accepted, you join the event chat.",
                    Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted, textAlign = TextAlign.Center)
                Box(Modifier.testTag("event-apply")) { SpotAuthButton("Apply to perform", enabled = ready, onClick = actions.apply) }
            }
            Standing.APPLIED -> EventPinnedBar {
                SpotAuthSecondaryButton(if (busy) "Updating…" else "Withdraw application", enabled = ready, onClick = actions.withdraw,
                    modifier = Modifier.testTag("event-withdraw"))
            }
            Standing.GOING -> EventPinnedBar {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SpotPillButton("Event chat", actions.chat, Modifier.weight(1f).heightIn(min = 52.dp).testTag("event-chat"),
                        icon = Icons.Rounded.Forum, enabled = !busy)
                    SpotPillButton("Performers", actions.performers, Modifier.weight(1f).heightIn(min = 52.dp),
                        icon = Icons.Rounded.Groups, style = SpotButtonStyle.Secondary)
                }
            }
            else -> Unit
        }
    }
}

/** A light round button over the poster, light in both themes like the poster is dark in both. */
@Composable
private fun HeroButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.size(44.dp), shape = CircleShape, color = PosterText) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(20.dp), tint = PosterInk) }
    }
}

@Composable
private fun CreatorDetails(event: CampusEvent, standing: Standing, application: EventApplication?, busy: Boolean, ended: Boolean,
    actions: EventPageActions) {
    val host = organizerFirstName(event)
    when (standing) {
        Standing.APPLIED -> EventBanner(Icons.Rounded.Schedule, "Application sent", "$host will accept or decline it. We'll let you know.",
            BannerTone.GOLD) {
            val note = application?.note.orEmpty()
            if (note.isNotBlank()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SpotPalette.Surface)
                .padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text("Your note", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = SpotPalette.GoldInk)
                Text(note, style = MaterialTheme.typography.bodyLarge, color = SpotPalette.Ink)
            }
        }
        Standing.GOING -> when {
            event.cancelled -> EventBanner(Icons.Rounded.EventBusy, "Event cancelled", "$host cancelled it. You can still open its chat.", BannerTone.RED)
            ended -> EventBanner(Icons.Rounded.Check, "You performed here", "Thanks for performing.", BannerTone.GREEN)
            else -> EventBanner(Icons.Rounded.Check, "You're performing",
                "Your slot is confirmed. See you ${eventDate(event.startsAt, "EEEE")} at ${eventDate(event.startsAt, "h:mm a")}.", BannerTone.GREEN)
        }
        Standing.DECLINED -> EventBanner(Icons.Rounded.Close, "Not this time", "$host declined your application.", BannerTone.GREY)
        Standing.CLOSED -> EventBanner(Icons.Rounded.Lock, "Applications closed", "The event started before $host answered.", BannerTone.GREY) {
            TextButton(actions.withdraw, enabled = !busy, contentPadding = PaddingValues(0.dp)) {
                Text("Remove my application", color = SpotPalette.GoldInk, fontWeight = FontWeight.SemiBold)
            }
        }
        Standing.CANCELLED -> EventBanner(Icons.Rounded.EventBusy, "Event cancelled", "$host cancelled it. Registration is closed.",
            BannerTone.RED, Modifier.testTag("event-cancelled"))
        Standing.FULL -> EventBanner(Icons.Rounded.Groups, "All slots are filled", "Check back if a performer leaves.", BannerTone.GREY)
        Standing.ENDED -> EventBanner(Icons.Rounded.Lock, "Registration closed", "This event has already started.", BannerTone.GREY)
        Standing.SPOTTER -> Text("Only Creators can apply to perform.", style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted)
        Standing.APPLY -> Unit
    }
    EventInfoRow(Icons.Rounded.CalendarMonth, eventDate(event.startsAt, "EEEE, MMMM d"), eventDate(event.startsAt, "h:mm a"))
    PlaceRow(event)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = actions.organizer)
        .heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        EventPersonAvatar(event.organizerProfileImageUrl, event.organizerName, event.organizerId, 44.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(event.organizerName.ifBlank { event.organizer }, style = MaterialTheme.typography.titleMedium, color = SpotPalette.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Organizer" + if (event.organizer.isNotBlank()) " · ${event.organizer}" else "", style = MaterialTheme.typography.bodyMedium,
                color = SpotPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = SpotPalette.Muted)
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val (fee, feeNote) = feeFact(event)
        FactCard("Talent fee", fee, feeNote, Modifier.weight(1f).fillMaxHeight())
        val limit = event.participantLimit
        val left = limit?.let { (it - event.participantCount).coerceAtLeast(0) }
        FactCard("Slots", when { limit == null -> "Open"; left == 0L -> "Full"; else -> "$left left" },
            if (limit == null) "No slot limit" else null, Modifier.weight(1f).fillMaxHeight()) {
            EventSlotsBar(event, Modifier.padding(top = 8.dp))
        }
    }
    About(event)
    if (standing == Standing.GOING && !event.cancelled && !ended) Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = SpotPalette.Line)
        TextButton(actions.leave, Modifier.padding(top = 4.dp).testTag("event-leave"), enabled = !busy, contentPadding = PaddingValues(0.dp)) {
            Text("Leave event", color = SpotPalette.ErrorText, fontWeight = FontWeight.SemiBold)
        }
        Text("Your slot opens up for someone else.", style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted)
    }
}

@Composable
private fun OrganizerDetails(event: CampusEvent, applications: List<EventApplication>?, invites: Int?, busy: Boolean, now: Long,
    actions: EventPageActions) {
    val waiting = applications.orEmpty().count { it.awaitingReview(now) }
    val ended = event.startsAt <= now
    if (event.cancelled) EventBanner(Icons.Rounded.EventBusy, "Event cancelled", "Performers can still see it and its chat.",
        BannerTone.RED, Modifier.testTag("event-cancelled"))
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(event.participantLimit?.let { "${event.participantCount}/$it" } ?: "${event.participantCount}", "Accepted", false,
            Modifier.weight(1f).fillMaxHeight())
        StatCard("$waiting", "Waiting", waiting > 0, Modifier.weight(1f).fillMaxHeight())
        StatCard(invites?.toString() ?: "–", "Invited", false, Modifier.weight(1f).fillMaxHeight())
    }
    if (!event.cancelled) {
        if (waiting > 0) SpotPillButton("Review $waiting ${if (waiting == 1) "application" else "applications"}", actions.applications,
            Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("event-review"), trailingIcon = Icons.Rounded.ChevronRight)
        else SpotPillButton("Applications", actions.applications, Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("event-review"),
            style = SpotButtonStyle.Secondary, trailingIcon = Icons.Rounded.ChevronRight)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionTile(Icons.Rounded.Forum, "Event chat", actions.chat, Modifier.weight(1f), enabled = !busy)
        ActionTile(Icons.Rounded.Groups, "Performers", actions.performers, Modifier.weight(1f))
        if (!ended && !event.cancelled) ActionTile(Icons.Rounded.Edit, "Edit", actions.edit, Modifier.weight(1f).testTag("event-edit"), enabled = !busy)
    }
    PlaceRow(event)
    EventInfoRow(Icons.Rounded.Payments, feeFact(event).let { (fee, note) -> if (note != null && event.talentFee == TalentFee.PAID) "$fee $note" else fee },
        "Talent fee")
    About(event)
    val involved = eventInvolved(event, applications, invites)
    val known = applications != null && invites != null
    // A past event people took part in stays as it is.
    if (!event.cancelled && (!ended || !involved)) Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = SpotPalette.Line)
        TextButton(actions.remove, Modifier.padding(top = 4.dp).testTag("event-remove"), enabled = !busy && known,
            contentPadding = PaddingValues(0.dp)) {
            Icon(if (involved) Icons.Rounded.EventBusy else Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp), tint = SpotPalette.ErrorText)
            Spacer(Modifier.width(8.dp))
            Text(if (involved) "Cancel event" else "Delete event", color = SpotPalette.ErrorText, fontWeight = FontWeight.SemiBold)
        }
        Text(if (involved) "Everyone in it is told. Performers keep the event and its chat."
            else "Nobody has applied or been invited yet, so it's removed for good.",
            style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted)
    }
}

/** "Main Quad" over "North Campus" when the location has a comma, otherwise the whole location. */
@Composable
private fun PlaceRow(event: CampusEvent) {
    val place = event.location.trim()
    EventInfoRow(Icons.Rounded.Place, place.substringBefore(',').trim().ifBlank { place }, place.substringAfter(',', "").trim())
}

/** The fee as a big value and a note: "₱1,500" and "per act", "Free" and "Unpaid performance". */
private fun feeFact(event: CampusEvent): Pair<String, String?> {
    val budget = event.talentFeeBudget.trim()
    return when (event.talentFee) {
        TalentFee.PAID -> {
            val first = budget.substringBefore(' ')
            when {
                budget.isBlank() -> "Paid" to "Ask about the budget"
                ' ' in budget && first.any(Char::isDigit) -> first to budget.substringAfter(' ').trim()
                else -> budget to "Paid performance"
            }
        }
        TalentFee.FREE -> "Free" to "Unpaid performance"
        else -> "Not set" to "Ask the organizer"
    }
}

@Composable
private fun FactCard(label: String, value: String, note: String?, modifier: Modifier = Modifier, extra: (@Composable () -> Unit)? = null) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = SpotPalette.Surface, border = BorderStroke(1.dp, SpotPalette.Line)) {
        Column(Modifier.padding(14.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, letterSpacing = .6.sp,
                color = SpotPalette.GoldInk)
            Text(value, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                color = SpotPalette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!note.isNullOrBlank()) Text(note, style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted, maxLines = 2)
            extra?.invoke()
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, highlight: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = if (highlight) SpotPalette.GoldWash else SpotPalette.Surface,
        border = BorderStroke(1.dp, if (highlight) SpotPalette.GoldLine else SpotPalette.Line)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = SpotPalette.Ink, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (highlight) SpotPalette.GoldInk else SpotPalette.Muted, maxLines = 1)
        }
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Surface(onClick = onClick, modifier = modifier.height(68.dp), enabled = enabled, shape = RoundedCornerShape(16.dp),
        color = SpotPalette.Surface, border = BorderStroke(1.dp, SpotPalette.Line)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = SpotPalette.Ink)
            Text(label, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelLarge, color = SpotPalette.Ink, maxLines = 1)
        }
    }
}

@Composable
private fun About(event: CampusEvent) {
    if (event.description.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("About", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SpotPalette.Ink)
        Text(event.description, style = MaterialTheme.typography.bodyLarge, color = SpotPalette.Ink.copy(alpha = .86f))
    }
}

@Composable
private fun EventPagePreview(profile: SpotUiProfile, joined: Boolean = false, application: EventApplication? = null,
    event: CampusEvent = EventSamples.acousticNight(), dark: Boolean = false) {
    val organizer = event.organizerId == profile.uid
    SPOTAppTheme(dark = dark) {
        Box(Modifier.background(SpotPalette.Ivory)) {
            EventPageScreen(event, profile, joined, application, if (organizer) EventSamples.applications() else null,
                if (organizer) 2 else null, busy = false, error = "", ready = true, now = System.currentTimeMillis(), actions = EventPageActions())
        }
    }
}

@Preview(name = "Event · can apply", widthDp = 360, heightDp = 800, fontScale = 1.15f)
@Composable private fun EventApplyPreview() = EventPagePreview(EventSamples.creator)

@Preview(name = "Event · applied", widthDp = 360, heightDp = 800, fontScale = 1.15f)
@Composable private fun EventAppliedPreview() = EventPagePreview(EventSamples.creator, application = EventSamples.application())

@Preview(name = "Event · performing · dark", widthDp = 360, heightDp = 800, fontScale = 1.15f)
@Composable private fun EventGoingPreview() = EventPagePreview(EventSamples.creator, joined = true, dark = true)

@Preview(name = "Event · organizer", widthDp = 360, heightDp = 900, fontScale = 1.15f)
@Composable private fun EventOrganizerPreview() = EventPagePreview(EventSamples.spotter)

@Preview(name = "Event · cancelled", widthDp = 360, heightDp = 800, fontScale = 1.15f)
@Composable private fun EventCancelledPreview() = EventPagePreview(EventSamples.creator, event = EventSamples.acousticNight().copy(cancelled = true))
