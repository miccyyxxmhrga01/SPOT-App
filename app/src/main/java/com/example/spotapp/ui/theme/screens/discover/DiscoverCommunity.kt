package com.example.spotapp.ui.theme.screens.discover

import com.example.spotapp.ui.theme.components.*

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.data.PageCursor
import kotlinx.coroutines.*

private data class CommunityCardData(val id: String, val title: String, val description: String, val image: String,
    val category: String, val subtitle: String, val footer: String, val date: Long = 0, val talentFee: String? = null)

@Composable
internal fun DiscoverCommunity(isEvent: Boolean, profile: SpotUiProfile, onProfile: (SpotUiProfile) -> Unit, onChat: (String) -> Unit,
    // The search field and its category filter button sit in Discover's header, like the other tabs' searches.
    search: String = "", filtersOpen: Boolean = false) {
    val repository = remember { DiscoverRepository.shared }
    var category by rememberSaveable(isEvent) { mutableStateOf("") }
    var create by rememberSaveable(isEvent) { mutableStateOf(false) }
    var selected by rememberSaveable(isEvent) { mutableStateOf<String?>(null) }
    var items by remember(isEvent) { mutableStateOf(emptyList<CommunityCardData>()) }
    var cursor by remember(isEvent) { mutableStateOf<PageCursor?>(null) }
    var hasMore by remember(isEvent) { mutableStateOf(false) }
    var loading by remember(isEvent) { mutableStateOf(true) }
    var error by remember(isEvent) { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    var pageRequest by remember { mutableIntStateOf(0) }
    var canCreate by remember(isEvent) { mutableStateOf(!isEvent) }
    var roleError by remember { mutableStateOf("") }
    suspend fun load(after: PageCursor?) {
        loading = true; error = ""
        try {
            val page = if (isEvent) repository.events(search, category, after).let { p ->
                SocialPage(p.items.map { e -> CommunityCardData(e.eventId, e.title, e.description, e.imageUrl, e.category,
                    e.location, "${e.participantCount}${e.participantLimit?.let { "/$it" }.orEmpty()} accepted · ${e.organizer}", e.startsAt, e.talentFee) }, p.cursor, p.hasMore)
            } else repository.groups(search, category, after).let { p ->
                SocialPage(p.items.map { g -> CommunityCardData(g.groupId, g.name, g.description, g.imageUrl, g.category,
                    if (g.privacy == "private") "Private · request to join" else "Public group", "${g.memberCount} ${if (g.memberCount == 1L) "member" else "members"} · ${g.ownerName}") }, p.cursor, p.hasMore)
            }
            items = (if (after == null) page.items else items + page.items).distinctBy { it.id }
            cursor = page.cursor; hasMore = page.hasMore
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "${if (isEvent) "Events" else "Groups"} couldn't load. Check your connection and retry." }
        finally { loading = false }
    }
    // A new search starts from the first page.
    LaunchedEffect(search) { pageRequest = 0 }
    LaunchedEffect(isEvent, search, category, revision, pageRequest) {
        // Query changes reset pageRequest below, cancelling any stale in-flight page.
        if (pageRequest == 0) { items = emptyList(); cursor = null; hasMore = false; loading = true; delay(250) }
        load(if (pageRequest == 0) null else cursor)
    }
    LaunchedEffect(isEvent, profile.uid, revision) {
        roleError = ""
        if (isEvent) try { canCreate = repository.canCreateEvents() }
        catch (e: CancellationException) { throw e } catch (_: Exception) { canCreate = false; roleError = "Your event permissions couldn't be checked. Tap Retry to reconnect." }
    }
    // Pull down to refresh, as on Home.
    val pull = rememberPullToRefreshState()
    val refreshing = loading && pageRequest == 0 && items.isNotEmpty()
    PullToRefreshBox(refreshing, { pageRequest = 0; revision++ }, Modifier.fillMaxSize(), state = pull, indicator = {
        PullToRefreshDefaults.Indicator(pull, refreshing, Modifier.align(Alignment.TopCenter),
            containerColor = SpotPalette.Surface, color = SpotPalette.GoldDeep)
    }) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (canCreate) SpotPillButton(if (isEvent) "Create event" else "Create group", { create = true },
                    Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
                Column(Modifier.animateContentSize()) {
                    if (filtersOpen) CommunityCategories(category, { category = it; pageRequest = 0 }, all = true)
                    else if (category.isNotBlank()) InputChip(true, { category = ""; pageRequest = 0 }, label = { Text(category) }, trailingIcon = { Icon(Icons.Rounded.Close, "Clear category", Modifier.size(14.dp)) },
                        colors = InputChipDefaults.inputChipColors(selectedContainerColor = SpotPalette.GoldWash, selectedLabelColor = CommunityGold))
                }
                CommunityError(roleError) { revision++ }
            }
        }
        items(items, key = { it.id }) { item ->
            Card(onClick = { selected = item.id }, colors = CardDefaults.cardColors(containerColor = SpotPalette.Surface), border = BorderStroke(1.dp, SpotPalette.Line),
                shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth().animateItem()) {
                CommunityCover(item.image, isEvent, Modifier.fillMaxWidth().height(if (isEvent) 160.dp else 130.dp), title = item.title)
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CommunityBadge(item.category)
                        if (isEvent) CommunityBadge(eventDate(item.date, "MMM d"))
                        if (item.talentFee != null) CommunityBadge(if (item.talentFee == TalentFee.PAID) "Paid" else "Free")
                    }
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (isEvent) eventDate(item.date) else item.description, style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.subtitle, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(color = CommunityLine)
                    Text(item.footer, style = MaterialTheme.typography.labelSmall, color = CommunityGold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item {
            if (loading) SpotLoading(true, SkeletonKind.CARDS)
            CommunityError(error) { if (items.isEmpty()) pageRequest = 0; revision++ }
            if (!loading && error.isBlank() && items.isEmpty()) SpotEmptyState(if (isEvent) Icons.Rounded.Event else Icons.Rounded.Groups,
                if (search.isNotBlank() || category.isNotBlank()) "No matches yet" else if (isEvent) "No events yet" else "Be the first to start a group",
                message = if (search.isNotBlank() || category.isNotBlank()) "Try another search or category."
                    else if (isEvent) "Upcoming events from the community will appear here." else "Groups bring creators with shared talents together.")
            if (hasMore && !loading) TextButton(onClick = { pageRequest++ }, modifier = Modifier.fillMaxWidth()) { Text("Load more") }
        }
    }
    }
    if (create) CommunityEditor(isEvent, profile, close = { create = false }, created = { id -> create = false; selected = id; pageRequest = 0; revision++ })
    // The page stays selected while its chat is open, so Back from the chat returns to it.
    selected?.let { id -> CommunityDetails(id, isEvent, profile, close = { selected = null; pageRequest = 0; revision++ }, onProfile = { selected = null; onProfile(it) }, onChat = onChat) }
}
