package com.example.spotapp.ui.theme.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.spotapp.data.AccountType
import com.example.spotapp.data.CampusEvent
import com.example.spotapp.data.DiscoverRepository
import com.example.spotapp.data.PublishedPost
import com.example.spotapp.data.TalentFee
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.discover.eventDate
import kotlinx.coroutines.CancellationException

/** Home's header: the wordmark, search, and the bell for Notifications with how many are new. */
@Composable
internal fun HomeHeader(onSearch: () -> Unit, notificationCount: Int, onNotifications: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 12.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        SpotWordmark()
        Spacer(Modifier.weight(1f))
        SpotHeaderAction(Icons.Rounded.Search, "Search", onSearch)
        if (onNotifications != null) {
            Spacer(Modifier.width(8.dp))
            Box {
                SpotHeaderAction(Icons.Rounded.NotificationsNone, if (notificationCount > 0) "Notifications, $notificationCount new" else "Notifications",
                    onNotifications, tag = "home-notifications")
                if (notificationCount > 0) Box(Modifier.align(Alignment.TopEnd).offset(2.dp, (-2).dp).defaultMinSize(19.dp, 19.dp)
                    .background(SpotPalette.Heart, CircleShape).border(2.dp, SpotPalette.Surface, CircleShape).padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center) {
                    Text(if (notificationCount > 99) "99+" else notificationCount.toString(), color = Color.White,
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** For you (everything, with Popular and events) or Following (only people you follow, and you). */
@Composable
internal fun FeedSwitch(following: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 2.dp, bottom = 6.dp).clip(RoundedCornerShape(18.dp))
        .background(SpotPalette.Track).padding(4.dp).selectableGroup().testTag("feed-switch")) {
        listOf(false to "For you", true to "Following").forEach { (value, label) ->
            val chosen = following == value
            val fill by animateColorAsState(if (chosen) SpotPalette.Surface else Color.Transparent, tween(180), label = "feed-switch-$label")
            Box(Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(14.dp)).background(fill)
                .selectable(chosen, role = Role.Tab) { onChange(value) }.testTag("feed-$label"), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (chosen) SpotPalette.Ink else SpotPalette.Muted,
                    fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

/** Popular posts as tall tiles you scroll sideways; videos carry a play badge. Hidden while there are none. */
@Composable
internal fun PopularStrip(posts: List<PublishedPost>, loading: Boolean, error: String, onRetry: () -> Unit, onOpen: (PublishedPost) -> Unit) {
    if (posts.isEmpty() && !loading && error.isBlank()) return
    Column(Modifier.fillMaxWidth().testTag("popular-section")) {
        SpotSectionHeader("Popular on SPOT", icon = Icons.Rounded.LocalFireDepartment)
        when {
            loading && posts.isEmpty() -> SpotLoading(true, SkeletonKind.CARDS)
            error.isNotBlank() && posts.isEmpty() -> TextButton(onRetry, Modifier.padding(horizontal = 8.dp)) {
                Text("Popular posts couldn't load · Retry", color = SpotPalette.GoldInk)
            }
            else -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(posts, key = { it.id }) { post -> PopularTile(post) { onOpen(post) } }
            }
        }
    }
}

@Composable
private fun PopularTile(post: PublishedPost, onClick: () -> Unit) {
    val media = post.media.firstOrNull()
    Box(Modifier.size(132.dp, 186.dp).clip(RoundedCornerShape(18.dp)).background(SpotPalette.Track)
        .clickable(onClickLabel = "Open ${post.author.displayName}'s post", onClick = onClick).testTag("popular-${post.id}")) {
        if (media != null) AsyncImage(media.previewUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(post.caption, Modifier.fillMaxSize().background(SpotPalette.GoldWash).padding(12.dp), style = MaterialTheme.typography.bodyMedium,
            color = SpotPalette.Ink, maxLines = 6, overflow = TextOverflow.Ellipsis)
        if (media?.isVideo == true) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp).background(Color.Black.copy(alpha = .45f), CircleShape),
            contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, "Video", Modifier.size(16.dp), tint = Color.White) }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .62f))))
            .padding(start = 10.dp, end = 10.dp, top = 24.dp, bottom = 9.dp)) {
            Text(post.author.displayName.ifBlank { post.author.username }, color = Color.White, style = MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (post.likes > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Favorite, null, Modifier.size(12.dp), tint = Color.White)
                Text(post.likes.toString(), Modifier.padding(start = 4.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * Upcoming events by other Spotters; for Creators those needing their talents come first. Tapping one opens it,
 * where Creators apply. Hidden while there are none.
 */
@Composable
internal fun HomeEventsRow(profile: SpotUiProfile, refresh: Int, onOpen: (CampusEvent) -> Unit) {
    var events by remember(profile.uid) { mutableStateOf<List<CampusEvent>>(emptyList()) }
    val creator = profile.accountType != AccountType.TALENT_SEEKER
    LaunchedEffect(profile.uid, refresh) {
        events = try {
            val now = System.currentTimeMillis()
            val talents = profile.talentCategories.toSet()
            DiscoverRepository.shared.events("", "").items.filter { it.startsAt > now && it.organizerId != profile.uid }
                .sortedWith(compareByDescending<CampusEvent> { creator && it.category in talents }.thenBy { it.startsAt }).take(8)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }
    if (events.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 6.dp).testTag("home-events")) {
        SpotSectionHeader(if (creator) "Events looking for talent" else "Upcoming events", icon = Icons.Rounded.Event)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(events, key = { it.eventId }) { event -> HomeEventCard(event, creator) { onOpen(event) } }
        }
    }
}

@Composable
private fun HomeEventCard(event: CampusEvent, creator: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.width(244.dp).testTag("home-event-${event.eventId}"), shape = RoundedCornerShape(18.dp),
        color = SpotPalette.Surface, border = BorderStroke(1.dp, SpotPalette.Line)) {
        Row(Modifier.padding(12.dp)) {
            Column(Modifier.width(46.dp).clip(RoundedCornerShape(12.dp)).background(SpotPalette.GoldSoft).padding(top = 6.dp, bottom = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(eventDate(event.startsAt, "MMM").uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp, color = SpotPalette.GoldInk)
                Text(eventDate(event.startsAt, "d"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SpotPalette.Ink)
            }
            Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleSmall, color = SpotPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${event.category} · ${event.location}", style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    when (event.talentFee) {
                        TalentFee.PAID -> FeeTag("Paid", SpotPalette.GreenFill, SpotPalette.GreenInk)
                        TalentFee.FREE -> FeeTag("Free", SpotPalette.Track, SpotPalette.Muted)
                    }
                    Text(if (creator && !event.isFull) "Apply ›" else "View ›", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold, color = SpotPalette.GoldInk)
                }
            }
        }
    }
}

@Composable
private fun FeeTag(label: String, fill: Color, ink: Color) {
    Text(label, Modifier.padding(end = 8.dp).background(fill, RoundedCornerShape(9.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = ink)
}
