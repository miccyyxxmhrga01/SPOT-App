package com.example.spotapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.example.spotapp.ui.theme.screens.discover.eventDate
import com.example.spotapp.ui.theme.screens.home.AccountRoleBadge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import kotlinx.coroutines.*

private val SearchTabs = listOf(SpotSectionTab("people", "People", Icons.Rounded.Person),
    SpotSectionTab("posts", "Posts", Icons.Rounded.GridView), SpotSectionTab("events", "Events", Icons.Rounded.Event))

/** People by name or username, posts by caption, #hashtag, talent or author, and events by name. */
@Composable
fun SearchScreen(currentProfile: SpotUiProfile, onBack: () -> Unit, onProfileClick: (SpotUiProfile) -> Unit,
    onOpenPost: (String) -> Unit = {}, onOpenEvent: (String) -> Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf("people") }
    var people by remember { mutableStateOf(emptyList<SpotUser>()) }
    var posts by remember { mutableStateOf(emptyList<PublishedPost>()) }
    var events by remember { mutableStateOf(emptyList<CampusEvent>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    // Post search needs two letters; shorter terms would match nearly everything.
    val postTermTooShort = tab == "posts" && query.trim().length < 2
    LaunchedEffect(currentProfile.uid, query, tab, retry) {
        loading = true; error = ""
        try {
            delay(250)
            when (tab) {
                "posts" -> posts = if (postTermTooShort) emptyList() else DiscoverRepository().posts(query).items
                "events" -> events = DiscoverRepository().events(query, "").items
                else -> people = ProfileRepository.shared.search(query)
            }
        }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Search couldn't load." }
        finally { loading = false }
    }
    val empty = when (tab) { "posts" -> posts.isEmpty(); "events" -> events.isEmpty(); else -> people.isEmpty() }
    Surface(Modifier.fillMaxSize().spotAmbientBackground(SpotPalette.Surface), color = Color.Transparent) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SpotHeaderAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                Spacer(Modifier.width(6.dp))
                SpotSearchField(query, { query = it.take(100) }, Modifier.weight(1f), when (tab) {
                    "posts" -> "Caption, #hashtag or name"; "events" -> "Event name"; else -> "Name or username"
                }, loading)
            }
            SpotSectionTabs(SearchTabs, tab, { tab = it }, Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp))
            SpotLoading(loading, SkeletonKind.ROWS, Modifier.fillMaxWidth()) {
                when {
                    error.isNotBlank() -> SpotEmptyState(Icons.Rounded.CloudOff, error, actionLabel = "Retry", onAction = { retry++ })
                    postTermTooShort -> SpotEmptyState(Icons.Rounded.ImageSearch, "Search posts",
                        message = "Type at least 2 letters: a caption word, #hashtag, talent or name.")
                    empty && tab == "posts" -> SpotEmptyState(Icons.Rounded.ImageSearch, "No posts found", message = "Try another word or #hashtag.")
                    empty && tab == "events" -> SpotEmptyState(Icons.Rounded.EventBusy, if (query.isBlank()) "No events yet" else "No events found",
                        message = if (query.isBlank()) "" else "Event names start with the words you type.")
                    empty -> SpotEmptyState(Icons.Rounded.PersonSearch, if (query.isBlank()) "No profiles yet" else "No results",
                        message = if (query.isBlank()) "" else "Try a different name or username.")
                }
            }
            if (!loading && error.isBlank()) LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(if (tab == "people") 0.dp else 10.dp)) {
                when (tab) {
                    "posts" -> items(posts, key = { it.id }) { post -> PostResult(post) { onOpenPost(post.id) } }
                    "events" -> items(events, key = { it.eventId }) { event -> EventResult(event) { onOpenEvent(event.eventId) } }
                    else -> items(people, key = { it.uid }) { person ->
                        Row(Modifier.fillMaxWidth().animateItem().clip(RoundedCornerShape(18.dp)).clickable { onProfileClick(person.asProfileUi()) }
                            .padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            SpotAvatar(person.profileImageUrl, person.displayName, Modifier.size(48.dp), person.uid)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(person.displayName, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall,
                                        color = SpotPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    person.accountType?.let { AccountRoleBadge(it, Modifier.padding(start = 6.dp)) }
                                }
                                Text("@${person.username}", style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted)
                            }
                            if (person.isPrivate) Icon(Icons.Rounded.Lock, "Private account", Modifier.size(16.dp), tint = SpotPalette.Muted)
                        }
                    }
                }
            }
        }
    }
}

/** A found post: its first photo or video frame, the author and the caption's first lines. */
@Composable
private fun PostResult(post: PublishedPost, onClick: () -> Unit) {
    Surface(onClick = onClick, color = SpotPalette.Surface, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, SpotPalette.Line)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val media = post.media.firstOrNull()
            if (media != null) Box(Modifier.size(66.dp).clip(RoundedCornerShape(12.dp)).background(SpotPalette.Track)) {
                AsyncImage(media.previewUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (media.isVideo) Icon(Icons.Rounded.PlayCircle, "Video", Modifier.align(Alignment.Center).size(26.dp), tint = Color.White)
            } else SpotIconTile(Icons.AutoMirrored.Rounded.Notes, size = 66.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpotAvatar(post.author.profileImageUrl, null, Modifier.size(22.dp), post.author.uid)
                    Text(post.author.displayName, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.titleSmall,
                        color = SpotPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(post.caption.ifBlank { "View post" }, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
                    color = SpotPalette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A found event: its date, title and place. */
@Composable
private fun EventResult(event: CampusEvent, onClick: () -> Unit) {
    Surface(onClick = onClick, color = SpotPalette.Surface, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, SpotPalette.Line)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (event.imageUrl.isNotBlank()) AsyncImage(event.imageUrl, null, Modifier.size(66.dp).clip(RoundedCornerShape(12.dp))
                .background(SpotPalette.Track), contentScale = ContentScale.Crop)
            else SpotIconTile(Icons.Rounded.Event, size = 66.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(eventDate(event.startsAt, "EEE, MMM d · h:mm a"), style = MaterialTheme.typography.labelMedium, color = SpotPalette.GoldInk)
                Text(event.title, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.titleSmall, color = SpotPalette.Ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (event.location.isNotBlank()) Text(event.location, style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
