package com.example.spotapp.ui.theme.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spotapp.data.AccountType
import com.example.spotapp.data.CampusEvent
import com.example.spotapp.ui.theme.components.SpotCard
import com.example.spotapp.ui.theme.components.SpotHeaderAction
import com.example.spotapp.ui.theme.components.SpotIconTile
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.components.spotCompactCount
import com.example.spotapp.ui.theme.models.SpotUiPost
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.discover.DateTile
import com.example.spotapp.ui.theme.screens.discover.EventRow
import com.example.spotapp.ui.theme.screens.discover.FeaturedEvent
import com.example.spotapp.ui.theme.screens.discover.talentIcon
import com.example.spotapp.ui.theme.screens.home.AccountRoleBadge

private val CollabFill get() = SpotPalette.GreenFill
private val CollabLine get() = SpotPalette.GreenLine
private val CollabInk get() = SpotPalette.GreenInk
private val CollabDot = Color(0xFF2E9D57)
private val PortfolioCover = Brush.linearGradient(listOf(Color(0xFFFFE49A), Color(0xFFF6CD62), Color(0xFFE4B143)))
private val PortfolioCoverHeight = 104.dp

/**
 * A Creator's profile reads like a talent portfolio: a short gold cover with the avatar overlapping it, who they are,
 * then their most liked post, their talents, the events they performed at, and their posts.
 */
@Composable
internal fun CreatorPortfolioHeader(profile: SpotUiProfile, avatarUri: String?, displayName: String, own: Boolean,
    onBack: () -> Unit, onSettings: () -> Unit, onShare: () -> Unit, onAvatar: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)).background(PortfolioCover)
            .drawBehind {
                // Soft spotlights, echoing SPOT's stage theme.
                drawCircle(Color.White.copy(alpha = .22f), size.width * .38f, Offset(size.width * .92f, 0f))
                drawCircle(Color.White.copy(alpha = .14f), size.width * .22f, Offset(size.width * .04f, size.height))
            }.statusBarsPadding().height(PortfolioCoverHeight))
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!own) SpotHeaderAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.weight(1f))
            if (own) SpotHeaderAction(Icons.Rounded.Settings, "Settings", onSettings)
            else SpotHeaderAction(Icons.Rounded.Share, "Share profile", onShare)
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = PortfolioCoverHeight - 50.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.Bottom) {
            ProfileAvatarLarge(profile, avatarUri, displayName, own, onAvatar, size = 100.dp)
            Spacer(Modifier.weight(1f))
            if (profile.openToCollab) CollabStatus(Modifier.padding(bottom = 10.dp, start = 10.dp))
        }
    }
}

@Composable
private fun CollabStatus(modifier: Modifier = Modifier) {
    Row(modifier.background(CollabFill, CircleShape).border(1.dp, CollabLine, CircleShape).padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
        .testTag("open-to-collab"), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(CollabDot, CircleShape))
        Text("Open to collabs", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold, color = CollabInk, maxLines = 1)
    }
}

/** Name and role, handle and course, bio, then counts in one line. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CreatorIdentity(profile: SpotUiProfile, displayName: String, username: String, course: String, bio: String, own: Boolean,
    postCount: Int, followerCount: Long?, followingCount: Long?, onAddBio: () -> Unit, onConnections: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically) {
            Text(displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = SpotPalette.Ink,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            profile.accountType?.let { ProfileRoleTag(it) }
        }
        val detail = course.replace("Bachelor of Science in ", "BS ")
        Text(listOf("@${username.ifBlank { "username" }}", detail).filter { it.isNotBlank() }.joinToString(" · "),
            Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        ProfileBio(bio, own, onAddBio)
        FlowRow(Modifier.padding(top = 8.dp).offset(x = (-6).dp)) {
            InlineStat(spotCompactCount(postCount.toLong()), if (postCount == 1) "post" else "posts")
            InlineStat(followerCount?.let(::spotCompactCount) ?: "—", if (followerCount == 1L) "follower" else "followers") { onConnections("followers") }
            InlineStat(followingCount?.let(::spotCompactCount) ?: "—", "following") { onConnections("following") }
        }
    }
}

@Composable
private fun InlineStat(value: String, label: String, onClick: (() -> Unit)? = null) {
    Row(Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(12.dp))
        .then(if (onClick != null) Modifier.clickable(onClickLabel = "Open $label", onClick = onClick) else Modifier)
        .padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = SpotPalette.Ink, maxLines = 1)
        Text(label, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted, maxLines = 1)
    }
}

/** A bio, left-aligned and folded after four lines; your own profile offers to add one. */
@Composable
private fun ProfileBio(bio: String, own: Boolean, onAddBio: () -> Unit) {
    var expanded by remember(bio) { mutableStateOf(false) }
    var overflows by remember(bio) { mutableStateOf(false) }
    if (bio.isNotBlank()) Column(Modifier.padding(top = 8.dp).clip(RoundedCornerShape(10.dp))
        .clickable(enabled = overflows || expanded, onClickLabel = if (expanded) "Show less" else "Show more") { expanded = !expanded }) {
        Text(bio, style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Ink, maxLines = if (expanded) 20 else 4,
            overflow = TextOverflow.Ellipsis, onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow })
        if (overflows || expanded) Text(if (expanded) "Show less" else "more", style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold, color = SpotPalette.GoldInk)
    } else if (own) TextButton(onAddBio, Modifier.offset(x = (-12).dp)) {
        Text("Add a bio", color = SpotPalette.GoldInk, fontWeight = FontWeight.SemiBold)
    }
}

/** The person's role beside their name: gold for Creators, ink for Spotters. A Spotter's opens the events they posted. */
@Composable
internal fun ProfileRoleTag(role: AccountType, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val spotter = role == AccountType.TALENT_SEEKER
    val ink = if (spotter) SpotPalette.GoldBright else SpotPalette.GoldInk
    Row(modifier.heightIn(min = 30.dp).clip(CircleShape).background(if (spotter) SpotPalette.OnGold else SpotPalette.GoldSoft)
        .then(if (onClick != null) Modifier.clickable(onClickLabel = "View posted events", role = Role.Button, onClick = onClick) else Modifier)
        .padding(start = 5.dp, end = if (onClick != null) 6.dp else 11.dp), verticalAlignment = Alignment.CenterVertically) {
        AccountRoleBadge(role, size = 20.dp, described = false)
        Text(role.label, Modifier.padding(start = 5.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            color = ink, maxLines = 1)
        if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, Modifier.padding(start = 1.dp).size(16.dp), tint = ink)
    }
}

@Composable
internal fun ProfileSectionTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SpotPalette.Ink)
        trailing()
    }
}

/** Their most liked post with media, shown large. */
@Composable
internal fun TopPerformance(post: SpotUiPost, onOpen: () -> Unit) {
    Column {
        ProfileSectionTitle("Top performance")
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(196.dp).clip(RoundedCornerShape(22.dp)).background(SpotPalette.Track)
            .clickable(onClickLabel = "Open post", onClick = onOpen).testTag("top-performance")) {
            ProfilePostMedia(post, Modifier.fillMaxSize())
            Text("MOST LIKED", Modifier.align(Alignment.TopStart).padding(12.dp).background(SpotPalette.Surface.copy(alpha = .92f), RoundedCornerShape(10.dp))
                .padding(horizontal = 9.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                color = SpotPalette.GoldInk, letterSpacing = .6.sp)
            if (isVideoPost(post)) Box(Modifier.align(Alignment.Center).size(52.dp).background(Color.Black.copy(alpha = .45f), CircleShape),
                contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, "Video", Modifier.size(28.dp), tint = Color.White) }
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .66f))))
                .padding(start = 14.dp, end = 14.dp, top = 30.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    if (post.caption.isNotBlank()) Text(post.caption, style = MaterialTheme.typography.titleSmall, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(post.category.substringBefore(", "), post.postedTime).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .9f), maxLines = 1)
                }
                Icon(Icons.Rounded.Favorite, null, Modifier.padding(start = 8.dp, bottom = 2.dp).size(15.dp), tint = Color.White)
                Text(spotCompactCount(post.likes.toLong()), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

/** Their talents with how many posts each has; choosing one shows only those posts. */
@Composable
internal fun TalentTiles(talents: List<String>, posts: List<SpotUiPost>, selected: String?, onSelect: (String) -> Unit) {
    Column {
        ProfileSectionTitle("Talents")
        talents.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { talent ->
                    val count = posts.count { post -> post.category.split(", ").any { it.equals(talent, ignoreCase = true) } }
                    TalentTile(talent, count, talent == selected, Modifier.weight(1f).fillMaxHeight()) { onSelect(talent) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TalentTile(talent: String, count: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.selectable(selected, role = Role.Button, onClick = onClick).testTag("talent-tile-$talent"), shape = RoundedCornerShape(18.dp),
        color = if (selected) SpotPalette.GoldSoft else SpotPalette.Surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) SpotPalette.GoldLine else SpotPalette.Line)) {
        Column(Modifier.padding(12.dp)) {
            SpotIconTile(talentIcon(talent), size = 34.dp, container = if (selected) SpotPalette.Surface else SpotPalette.GoldWash)
            Text(talent, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = SpotPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (count == 1) "1 post" else "$count posts", style = MaterialTheme.typography.labelSmall, color = SpotPalette.Muted, maxLines = 1)
        }
    }
}

/** Events they performed at or are going to, newest first. */
@Composable
internal fun PerformedEvents(events: List<CampusEvent>, onOpen: (CampusEvent) -> Unit) {
    val now = remember(events) { System.currentTimeMillis() }
    Column {
        ProfileSectionTitle(if (events.all { it.startsAt <= now }) "Performed at" else "Events")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(events, key = { it.eventId }) { event ->
                val upcoming = event.startsAt > now
                Surface(onClick = { onOpen(event) }, modifier = Modifier.width(236.dp).testTag("profile-event-${event.eventId}"),
                    shape = RoundedCornerShape(18.dp), color = SpotPalette.Surface, border = BorderStroke(1.dp, SpotPalette.Line)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        DateTile(event.startsAt, muted = !upcoming)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(event.title, style = MaterialTheme.typography.titleSmall, color = SpotPalette.Ink, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Text(if (upcoming) "Going" else "Performed", style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold, color = if (upcoming) CollabInk else SpotPalette.Muted)
                        }
                    }
                }
            }
        }
    }
}

/** Labelled tabs in a white track; the chosen one sits on gold. */
@Composable
internal fun ProfileTabs(tabs: List<Pair<ProfileTab, String>>, selected: ProfileTab, onSelect: (ProfileTab) -> Unit, modifier: Modifier = Modifier) {
    val track = RoundedCornerShape(22.dp)
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(track).background(SpotPalette.Surface).border(1.dp, SpotPalette.Line, track)
        .padding(4.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        tabs.forEach { (tab, label) ->
            val active = tab == selected
            Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(18.dp))
                .background(if (active) SpotPalette.GoldSoft else Color.Transparent)
                .selectable(active, role = Role.Tab) { onSelect(tab) }.testTag("profile-tab-${tab.name}"), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) SpotPalette.Ink else SpotPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * A Spotter's profile leads with their events: a compact header with their counts, what they're looking for, then
 * the next event to apply to and the ones after it. Past events and posts sit in tabs below.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpotterHeader(profile: SpotUiProfile, viewer: SpotUiProfile, avatarUri: String?, displayName: String, username: String, bio: String,
    own: Boolean, eventCount: String, followerCount: Long?, followingCount: Long?, onBack: () -> Unit, onSettings: () -> Unit,
    onShare: () -> Unit, onAvatar: () -> Unit, onAddBio: () -> Unit, onEvents: () -> Unit, onConnections: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = if (own) 20.dp else 8.dp, end = 8.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (!own) { SpotHeaderAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack); Spacer(Modifier.width(4.dp)) }
        Text(username.ifBlank { "username" }, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = SpotPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (own) SpotHeaderAction(Icons.Rounded.Settings, "Settings", onSettings)
        else SpotHeaderAction(Icons.Rounded.Share, "Share profile", onShare)
    }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        ProfileAvatarLarge(profile, avatarUri, displayName, own, onAvatar, size = 84.dp)
        Row(Modifier.weight(1f).padding(start = 8.dp)) {
            GridStat(eventCount, "Events", Modifier.weight(1f), onEvents)
            GridStat(followerCount?.let(::spotCompactCount) ?: "—", "Followers", Modifier.weight(1f)) { onConnections("followers") }
            GridStat(followingCount?.let(::spotCompactCount) ?: "—", "Following", Modifier.weight(1f)) { onConnections("following") }
        }
    }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically) {
            Text(displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SpotPalette.Ink,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            ProfileRoleTag(AccountType.TALENT_SEEKER, Modifier.testTag("spotter-badge"), onClick = onEvents)
        }
        if (profile.organization.isNotBlank()) Text(profile.organization, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyMedium,
            color = SpotPalette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        ProfileBio(bio, own, onAddBio)
        if (profile.seekingTalents.isNotEmpty()) {
            Text("LOOKING FOR", Modifier.padding(top = 12.dp, bottom = 6.dp), style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = SpotPalette.GoldInk)
            // Talents the viewer has stand out, so a Creator sees at a glance that they fit.
            val mine = if (own) emptySet() else viewer.talentCategories.map { it.lowercase() }.toSet()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                profile.seekingTalents.forEach { ProfileChip(it, filled = it.lowercase() in mine) }
            }
        }
    }
}

@Composable
private fun GridStat(value: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = "Open $label", onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SpotPalette.Ink, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = SpotPalette.Muted, maxLines = 1, softWrap = false)
    }
}

/** The next event as a card with its action, then up to two more; the rest open from See all. */
@Composable
internal fun SpotterUpcoming(upcoming: List<CampusEvent>, status: Map<String, String>, viewer: SpotUiProfile, own: Boolean,
    onOpen: (CampusEvent, Boolean) -> Unit, onSeeAll: () -> Unit) {
    val creator = viewer.accountType != AccountType.TALENT_SEEKER
    ProfileSectionTitle("Upcoming events") {
        if (upcoming.size > 3) TextButton(onSeeAll) { Text("See all", color = SpotPalette.GoldInk, fontWeight = FontWeight.SemiBold) }
    }
    if (upcoming.isEmpty()) {
        if (own) SpotCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp), elevation = 1.dp) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                SpotIconTile(Icons.Rounded.Event, size = 40.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("No upcoming events", style = MaterialTheme.typography.titleSmall, color = SpotPalette.Ink)
                    Text("Tap + to create one.", style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted)
                }
            }
        }
        else Text("No upcoming events", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted)
        return
    }
    val first = upcoming.first()
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FeaturedEvent(first, status[first.eventId], viewer, creator, onOpen = { onOpen(first, false) }, onApply = { onOpen(first, true) },
            showOrganizer = false)
        upcoming.drop(1).take(2).forEach { event ->
            EventRow(event, status[event.eventId], viewer, creator, past = false) { onOpen(event, false) }
        }
    }
}

@Composable
internal fun SpotterPastEvents(past: List<CampusEvent>, status: Map<String, String>, viewer: SpotUiProfile, onOpen: (CampusEvent) -> Unit) {
    val creator = viewer.accountType != AccountType.TALENT_SEEKER
    if (past.isEmpty()) ProfileEmptyState(Icons.Rounded.Event, "No past events yet", "Events they held will appear here.")
    else Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        past.forEach { event -> EventRow(event, status[event.eventId], viewer, creator, past = true) { onOpen(event) } }
    }
}
