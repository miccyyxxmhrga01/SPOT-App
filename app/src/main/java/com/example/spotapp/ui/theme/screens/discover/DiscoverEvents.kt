package com.example.spotapp.ui.theme.screens.discover

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal enum class EventFilter(val label: String) { ALL("All"), WEEK("This week"), PAID("Paid"), FREE("Free") }

/**
 * The Events tab. Creators get the filters, one featured event (one needing their talent when there is one), then
 * what's coming up and what's past, each row showing where they stand. Spotters get Create event and their own
 * upcoming events, with the applications waiting on each, above the same list.
 */
@Composable
internal fun DiscoverEvents(profile: SpotUiProfile, search: String, onProfile: (SpotUiProfile) -> Unit, onChat: (String) -> Unit) {
    val repository = remember { DiscoverRepository.shared }
    var filter by rememberSaveable { mutableStateOf(EventFilter.ALL) }
    var category by rememberSaveable { mutableStateOf("") }
    var talentSheet by remember { mutableStateOf(false) }
    var events by remember { mutableStateOf(emptyList<CampusEvent>()) }
    var cursor by remember { mutableStateOf<PageCursor?>(null) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    // A new search or talent starts again from the first page.
    var pageRequest by remember(search, category) { mutableIntStateOf(0) }
    var canCreate by remember { mutableStateOf(false) }
    var mine by remember { mutableStateOf(emptyList<OrganizedEvent>()) }
    var create by rememberSaveable { mutableStateOf(false) }
    var seeAll by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var applyOnOpen by rememberSaveable { mutableStateOf(false) }
    var status by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(search, category, revision, pageRequest) {
        if (pageRequest == 0) { loading = true; delay(250) }
        loading = true; error = ""
        try {
            val page = repository.events(search, category, if (pageRequest == 0) null else cursor)
            events = (if (pageRequest == 0) page.items else events + page.items).distinctBy { it.eventId }
            cursor = page.cursor; hasMore = page.hasMore
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Events couldn't load. Check your connection and retry." }
        finally { loading = false }
    }
    LaunchedEffect(profile.uid, revision) {
        canCreate = try { repository.canCreateEvents() } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
    }
    LifecycleDisposableEffect(profile.uid) {
        val listener = repository.listenMyEventStatus { status = it }
        onDispose { listener.remove() }
    }
    LifecycleDisposableEffect(profile.uid, canCreate) {
        val listener = if (canCreate) repository.listenMyUpcomingEvents({ mine = it }, { }) else null
        onDispose { listener?.remove() }
    }
    EventsTabContent(profile, events, status, mine, canCreate, filter, category, search, loading, error, hasMore,
        System.currentTimeMillis(), onFilter = { filter = it }, onTalent = { talentSheet = true }, onCreate = { create = true },
        onSeeAll = { seeAll = true }, onOpen = { event, apply -> applyOnOpen = apply; selected = event.eventId },
        onRetry = { revision++ }, onMore = { pageRequest++ })
    if (talentSheet) TalentPickSheet(category, { category = it; talentSheet = false }, { talentSheet = false }, anyLabel = "Any talent")
    if (create) EventEditor(profile, close = { create = false }, saved = { id -> create = false; selected = id; pageRequest = 0; revision++ })
    if (seeAll) PostedEvents(profile, profile, close = { seeAll = false }, onProfile = { seeAll = false; onProfile(it) }, onChat = onChat)
    selected?.let { id ->
        EventPage(id, profile, close = { selected = null; applyOnOpen = false; pageRequest = 0; revision++ },
            onProfile = { selected = null; onProfile(it) }, onChat = onChat, startApply = applyOnOpen)
    }
}

/** The Events tab as drawn from its data, so previews and the review test can show it without a network. */
@Composable
internal fun EventsTabContent(profile: SpotUiProfile, events: List<CampusEvent>, status: Map<String, String>,
    mine: List<OrganizedEvent>, canCreate: Boolean, filter: EventFilter, category: String, search: String,
    loading: Boolean, error: String, hasMore: Boolean, now: Long, onFilter: (EventFilter) -> Unit, onTalent: () -> Unit,
    onCreate: () -> Unit, onSeeAll: () -> Unit, onOpen: (CampusEvent, Boolean) -> Unit, onRetry: () -> Unit, onMore: () -> Unit,
    modifier: Modifier = Modifier) {
    val creator = profile.accountType != AccountType.TALENT_SEEKER
    val shown = events.filter { e ->
        when (filter) {
            EventFilter.ALL -> true
            EventFilter.WEEK -> e.startsAt in now..(now + 7 * 86_400_000L)
            EventFilter.PAID -> e.talentFee == TalentFee.PAID
            EventFilter.FREE -> e.talentFee == TalentFee.FREE
        }
    }
    val (past, upcomingEvents) = shown.partition { it.startsAt <= now }
    val upcoming = upcomingEvents.sortedBy { it.startsAt }
    val talents = profile.talentCategories.toSet()
    // A Creator's featured event needs their talent and isn't theirs to apply to yet. Spotters get their own events instead.
    val matching = if (creator) upcoming.firstOrNull { it.category in talents && it.organizerId != profile.uid && status[it.eventId] == null } else null
    val featured = if (creator) matching ?: upcoming.firstOrNull() else null
    val rest = upcoming.filter { it.eventId != featured?.eventId }
    val side = Modifier.padding(horizontal = 20.dp)
    LazyColumn(modifier.fillMaxSize().testTag("discover-events"),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp + LocalSpotBottomContentPadding.current),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (canCreate) item(key = "create") {
            SpotPillButton("Create event", onCreate, side.fillMaxWidth().heightIn(min = 52.dp).testTag("event-create"), icon = Icons.Rounded.Add)
        }
        if (canCreate && mine.isNotEmpty()) {
            item(key = "mine-title") {
                EventSectionTitle("Your events", side.padding(top = 10.dp)) {
                    TextButton(onSeeAll) { Text("See all", color = SpotPalette.GoldInk, fontWeight = FontWeight.SemiBold) }
                }
            }
            item(key = "mine") {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(mine, key = { it.event.eventId }) { item -> OrganizerEventCard(item) { onOpen(item.event, false) } }
                }
            }
        }
        item(key = "filters") { EventFilters(filter, category, onFilter, onTalent, Modifier.padding(top = if (canCreate) 12.dp else 0.dp)) }
        if (error.isNotBlank()) item(key = "error") { Box(side) { CommunityError(error, onRetry) } }
        if (loading && events.isEmpty()) item(key = "loading") { SpotLoading(true, SkeletonKind.CARDS) }
        featured?.let { event ->
            item(key = "featured-title") {
                EventSectionTitle(if (event === matching) "For your talent" else "Next up", side.padding(top = 10.dp)) {
                    if (event === matching) Text(event.category, style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted)
                }
            }
            item(key = "featured-${event.eventId}") {
                FeaturedEvent(event, status[event.eventId], profile, creator, onOpen = { onOpen(event, false) },
                    onApply = { onOpen(event, true) }, modifier = side)
            }
        }
        if (rest.isNotEmpty()) item(key = "coming-title") { EventSectionTitle("Coming up", side.padding(top = 14.dp)) }
        items(rest, key = { it.eventId }) { event ->
            EventRow(event, status[event.eventId], profile, creator, past = false, modifier = side) { onOpen(event, false) }
        }
        if (past.isNotEmpty()) item(key = "past-title") { EventSectionTitle("Past", side.padding(top = 14.dp)) }
        items(past.sortedByDescending { it.startsAt }, key = { "past-${it.eventId}" }) { event ->
            EventRow(event, status[event.eventId], profile, creator, past = true, modifier = side) { onOpen(event, false) }
        }
        item(key = "end") {
            val narrowed = search.isNotBlank() || category.isNotBlank() || filter != EventFilter.ALL
            if (!loading && error.isBlank() && shown.isEmpty()) SpotEmptyState(Icons.Rounded.Event,
                if (narrowed) "No matching events" else "No events yet",
                message = if (narrowed) "Try another search or filter." else "Upcoming events from the community will appear here.")
            if (loading && events.isNotEmpty()) LinearProgressIndicator(side.fillMaxWidth().padding(vertical = 8.dp), color = CommunityGold)
            if (hasMore && !loading) TextButton(onMore, side.fillMaxWidth()) { Text("Load more events") }
        }
    }
}

/** All, This week, Paid, Free and a talent picker, scrolling sideways. */
@Composable
private fun EventFilters(filter: EventFilter, category: String, onFilter: (EventFilter) -> Unit, onTalent: () -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(EventFilter.entries, key = { it.name }) { option ->
            FilterPill(option.label, filter == option, Modifier.testTag("event-filter-${option.name}")) { onFilter(option) }
        }
        item(key = "talent") {
            FilterPill(category.ifBlank { "Talent" }, category.isNotBlank(), Modifier.testTag("event-filter-talent"),
                trailing = Icons.Rounded.ExpandMore, role = Role.Button, onClick = onTalent)
        }
    }
}

@Composable
private fun FilterPill(label: String, chosen: Boolean, modifier: Modifier = Modifier, trailing: androidx.compose.ui.graphics.vector.ImageVector? = null,
    role: Role = Role.RadioButton, onClick: () -> Unit) {
    Surface(modifier.selectable(chosen, role = role, onClick = onClick), shape = CircleShape,
        color = if (chosen) SpotPalette.Ink else SpotPalette.Surface, border = BorderStroke(1.dp, if (chosen) SpotPalette.Ink else SpotPalette.Line)) {
        Row(Modifier.heightIn(min = 38.dp).padding(start = 14.dp, end = if (trailing != null) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (chosen) SpotPalette.OnInk else SpotPalette.Ink,
                fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium)
            if (trailing != null) Icon(trailing, null, Modifier.padding(start = 2.dp).size(18.dp), tint = if (chosen) SpotPalette.OnInk else SpotPalette.Ink)
        }
    }
}

/** One of the Spotter's upcoming events: when, how many applications wait, and how full it is. */
@Composable
private fun OrganizerEventCard(item: OrganizedEvent, onClick: () -> Unit) {
    val event = item.event
    Surface(onClick = onClick, modifier = Modifier.width(272.dp).testTag("my-event-${event.eventId}"), shape = RoundedCornerShape(20.dp),
        color = SpotPalette.Surface, border = BorderStroke(1.dp, SpotPalette.Line)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EventDateBox(event.startsAt)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(event.title, style = MaterialTheme.typography.titleMedium, color = SpotPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${eventDate(event.startsAt, "EEE")} · ${eventDate(event.startsAt, "h:mm a")} · ${shortPlace(event.location)}",
                        style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val waiting = item.waiting > 0
                Text(if (waiting) "${item.waiting} waiting" else "No applications yet",
                    Modifier.clip(CircleShape).background(if (waiting) SpotPalette.Gold else SpotPalette.Track).padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    color = if (waiting) SpotPalette.OnGold else EventGreyInk, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Text(event.participantLimit?.let { "${event.participantCount} of $it" + if (waiting) " accepted" else "" }
                    ?: "${event.participantCount} accepted", style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted, maxLines = 1)
            }
            if (event.participantLimit != null) EventSlotsBar(event)
        }
    }
}

/**
 * The featured event: a dark card with its poster, date, fee, place, organizer, slots and the one action that fits
 * you. On the organizer's own profile [showOrganizer] is false, since the page already says who they are.
 */
@Composable
internal fun FeaturedEvent(event: CampusEvent, status: String?, profile: SpotUiProfile, creator: Boolean, onOpen: () -> Unit, onApply: () -> Unit,
    showOrganizer: Boolean = true, modifier: Modifier = Modifier) {
    val stand = eventStanding(event, status, profile.uid, creator, System.currentTimeMillis())
    Surface(onClick = onOpen, modifier = modifier.fillMaxWidth().testTag("featured-event"), shape = RoundedCornerShape(24.dp), color = PosterInk) {
        Column {
            Box(Modifier.fillMaxWidth().height(150.dp)) {
                EventPoster(event.imageUrl, Modifier.fillMaxSize())
                EventDateBox(event.startsAt, Modifier.align(Alignment.TopStart).padding(14.dp), container = PosterText, weekday = true,
                    width = 54.dp, light = true)
                EventFeeTag(event, Modifier.align(Alignment.TopEnd).padding(14.dp))
            }
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(event.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = PosterText,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Place, null, Modifier.size(16.dp), tint = PosterMuted)
                    Text("${shortPlace(event.location)} · ${eventDate(event.startsAt, "h:mm a")}", Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.bodyLarge, color = PosterMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (showOrganizer) Row(verticalAlignment = Alignment.CenterVertically) {
                    EventPersonAvatar(event.organizerProfileImageUrl, event.organizerName, event.organizerId, 24.dp)
                    Text("by ${event.organizerName.ifBlank { event.organizer }}" +
                        if (event.organizer.isNotBlank() && event.organizer != event.organizerName) " · ${event.organizer}" else "",
                        Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge, color = PosterMuted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                event.participantLimit?.let { limit ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EventSlotsBar(event, track = PosterTrack)
                        val left = (limit - event.participantCount).coerceAtLeast(0)
                        Text(if (left == 0L) "All slots are filled" else "$left of $limit slots left",
                            style = MaterialTheme.typography.bodyMedium, color = PosterMuted)
                    }
                }
                val (label, primary) = when (stand) {
                    EventStanding.APPLY -> "Apply to perform" to true
                    EventStanding.GOING -> "You're performing · open event" to false
                    EventStanding.APPLIED -> "Application sent · view event" to false
                    EventStanding.YOURS -> "Manage your event" to false
                    EventStanding.FULL -> "Event full · view details" to false
                    else -> "View event" to false
                }
                Button(if (primary) onApply else onOpen, Modifier.padding(top = 4.dp).fillMaxWidth().heightIn(min = 52.dp).testTag("featured-action"),
                    shape = CircleShape, colors = ButtonDefaults.buttonColors(
                        containerColor = if (primary) SpotPalette.Gold else PosterText.copy(alpha = .12f),
                        contentColor = if (primary) SpotPalette.OnGold else PosterText)) {
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** An event as a ticket: the date stub, what and where, the fee and slots, and where you stand on the right. */
@Composable
internal fun EventRow(event: CampusEvent, status: String?, profile: SpotUiProfile, creator: Boolean, past: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    val stand = eventStanding(event, status, profile.uid, creator, System.currentTimeMillis())
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth().testTag("event-row-${event.eventId}"), shape = RoundedCornerShape(18.dp),
        color = if (past) SpotPalette.Track else SpotPalette.Surface, border = BorderStroke(1.dp, SpotPalette.Line)) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            EventStub(event.startsAt, muted = past)
            Column(Modifier.weight(1f).heightIn(min = if (past) 76.dp else 84.dp).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium, color = if (past) EventGreyInk else SpotPalette.Ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(event.category.ifBlank { null }, shortPlace(event.location).ifBlank { null },
                    if (past) null else eventClock(event.startsAt)).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!past) Text(buildAnnotatedString {
                    val fee = when (event.talentFee) { TalentFee.PAID -> "Paid"; TalentFee.FREE -> "Free"; else -> null }
                    if (fee != null) { withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = SpotPalette.Ink)) { append(fee) }; append(" · ") }
                    append(slotsLeft(event))
                }, style = MaterialTheme.typography.labelMedium, color = SpotPalette.Muted, maxLines = 1)
            }
            if (stand != null) EventStatusPill(stand, Modifier.padding(end = 12.dp))
            else Icon(Icons.Rounded.ChevronRight, null, Modifier.padding(end = 14.dp).size(20.dp), tint = SpotPalette.Muted)
        }
    }
}

@Composable
internal fun DateTile(time: Long, muted: Boolean = false, light: Boolean = false) {
    Column(Modifier.width(52.dp).clip(RoundedCornerShape(14.dp))
        .background(when { light -> SpotPalette.Surface; muted -> SpotPalette.Track; else -> SpotPalette.GoldSoft }).padding(top = 6.dp, bottom = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(eventDate(time, "MMM").uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
            color = if (muted) SpotPalette.Muted else SpotPalette.GoldInk)
        Text(eventDate(time, "d"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = if (muted) SpotPalette.Muted else SpotPalette.Ink)
    }
}

@Composable
private fun EventsTabPreview(profile: SpotUiProfile, dark: Boolean) {
    val spotter = profile.accountType == AccountType.TALENT_SEEKER
    SPOTAppTheme(dark = dark) {
        Box(Modifier.background(SpotPalette.Ivory)) {
            EventsTabContent(profile, EventSamples.events(), if (spotter) emptyMap() else EventSamples.creatorStatus,
                if (spotter) EventSamples.organized() else emptyList(), spotter, EventFilter.ALL, "", "", false, "", false,
                System.currentTimeMillis(), {}, {}, {}, {}, { _, _ -> }, {}, {})
        }
    }
}

@Preview(name = "Events tab · Creator", widthDp = 360, heightDp = 1300, fontScale = 1.15f)
@Composable private fun EventsTabCreatorPreview() = EventsTabPreview(EventSamples.creator, false)

@Preview(name = "Events tab · Creator · dark", widthDp = 360, heightDp = 1300, fontScale = 1.15f)
@Composable private fun EventsTabCreatorDarkPreview() = EventsTabPreview(EventSamples.creator, true)

@Preview(name = "Events tab · Spotter", widthDp = 360, heightDp = 1000, fontScale = 1.15f)
@Composable private fun EventsTabSpotterPreview() = EventsTabPreview(EventSamples.spotter, false)
