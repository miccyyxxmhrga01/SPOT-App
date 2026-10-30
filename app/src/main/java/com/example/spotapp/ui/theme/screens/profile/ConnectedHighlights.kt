package com.example.spotapp.ui.theme.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.discover.CommunityWindow
import com.example.spotapp.ui.theme.screens.home.StoryCreator
import com.example.spotapp.ui.theme.screens.home.StoryViewer
import kotlinx.coroutines.*
import java.util.UUID

@Composable
internal fun ConnectedHighlights(profile: SpotUiProfile, own: Boolean, following: Boolean) {
    var highlights by remember(profile.uid) { mutableStateOf(emptyList<StoryHighlight>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StoryHighlight?>(null) }
    var opened by remember { mutableStateOf<StoryHighlight?>(null) }
    LifecycleDisposableEffect(profile.uid, following, retry) {
        if (!own && !following) { highlights = highlights.filter { it.audience == "public" }; opened = opened?.takeIf { it.audience == "public" } }
        loading = highlights.isEmpty(); error = ""
        val listener = StoryRepository.shared.listenHighlights(profile.uid, following, {
            highlights = it; loading = false
            opened = opened?.let { current -> it.firstOrNull { next -> next.id == current.id } }
        }, { error = it; loading = false; highlights = emptyList(); opened = null })
        onDispose { listener.remove() }
    }
    if (loading) SpotLoading(true, SkeletonKind.STORIES)
    if (error.isNotBlank()) TextButton({ retry++ }, Modifier.padding(horizontal = 12.dp)) { Text("Highlights unavailable · Retry") }
    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (own) item {
            Column(Modifier.width(66.dp).clickable { creating = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(58.dp).background(SpotPalette.GoldWash, CircleShape).border(1.5.dp, SpotPalette.GoldLine, CircleShape),
                    contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, "New highlight", Modifier.size(24.dp), tint = SpotPalette.GoldDeep) }
                Spacer(Modifier.height(7.dp)); Text("New", style = MaterialTheme.typography.labelSmall, color = SpotPalette.GoldInk)
            }
        }
        items(highlights, key = { it.id }) { h ->
            Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(h.media[h.cover].preview, "Open ${h.title}", Modifier.size(58.dp).border(1.5.dp, Color(0xFFE8B84B), CircleShape).padding(3.dp).clip(CircleShape).clickable { opened = h }, contentScale = ContentScale.Crop)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(h.title, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    if (own) IconButton({ editing = h }, Modifier.size(28.dp)) { Icon(Icons.Rounded.MoreHoriz, "Edit ${h.title}", Modifier.size(18.dp)) }
                }
            }
        }
    }
    if (creating || editing != null) HighlightEditor(profile, editing, { creating = false; editing = null }, { creating = false; editing = null; retry++ })
    opened?.let { h ->
        StoryViewer(h.media.mapIndexed { index, media -> StoryItem("${h.id}-$index", h.userId, media.url, media.type,
            username = h.title, profileImageUrl = profile.profileImageUrl, composition = media.composition) }, currentUid = profile.uid,
            close = { opened = null }, highlight = true)
    }
}

@Composable
private fun HighlightEditor(profile: SpotUiProfile, existing: StoryHighlight?, close: () -> Unit, saved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val id = rememberSaveable { existing?.id ?: "highlight-${UUID.randomUUID()}" }
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    var sources by remember { mutableStateOf(existing?.media.orEmpty()) }
    var selection by rememberSaveable { mutableStateOf(existing?.media.orEmpty().map { it.key }) }
    var cover by rememberSaveable { mutableStateOf(existing?.media?.getOrNull(existing.cover)?.key.orEmpty()) }
    var audience by rememberSaveable { mutableStateOf(existing?.audience ?: if (profile.isPrivate || profile.privacyUpdating) "followers" else "public") }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    var addStory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(retry) {
        loading = true; error = ""
        try { sources = (StoryRepository.shared.highlightSources() + existing?.media.orEmpty()).distinctBy { it.key } }
        catch (e: CancellationException) { throw e } catch (_: Exception) { error = "Your moments couldn't load. Retry." }
        finally { loading = false }
    }
    fun action(remove: Boolean) {
        if (busy) return
        val chosen = selection.mapNotNull { key -> sources.firstOrNull { it.key == key } }
        busy = true; error = ""
        scope.launch {
            try {
                withTimeout(30_000) {
                    if (remove) StoryRepository.shared.removeHighlight(id)
                    else StoryRepository.shared.saveHighlight(id, title, chosen, chosen.indexOfFirst { it.key == cover }.coerceAtLeast(0), audience)
                }; saved()
            } catch (_: TimeoutCancellationException) { error = "Save timed out. Retry." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = if (e is IllegalArgumentException) e.message.orEmpty() else "Couldn't save this highlight. Retry." }
            finally { busy = false; confirmDelete = false }
        }
    }
    CommunityWindow(if (existing == null) "New highlight" else "Edit highlight", close, busy) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
            OutlinedTextField(title, { title = it.take(32) }, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true, enabled = !busy, shape = RoundedCornerShape(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${selection.size}/8 moments", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                TextButton({ addStory = true }, enabled = !busy) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Text("Add story") }
            }
            if (selection.isNotEmpty()) {
                Text("Cover", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(selection) { key -> sources.firstOrNull { it.key == key }?.let { media ->
                        AsyncImage(media.preview, "Use as cover", Modifier.size(54.dp).border(if (cover == key || cover.isBlank() && selection.first() == key) 3.dp else 1.dp,
                            Color(0xFFE8B84B), CircleShape).padding(3.dp).clip(CircleShape).clickable { cover = key }, contentScale = ContentScale.Crop)
                    } }
                }
            }
            if (error.isNotBlank()) TextButton({ retry++ }) { Text(error, color = MaterialTheme.colorScheme.error) }
            if (loading) SpotLoading(true, SkeletonKind.GRID, Modifier.weight(1f))
            else if (sources.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Add a story or post first.", style = MaterialTheme.typography.bodyMedium) }
            else LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(sources, key = { it.key }) { media ->
                    val selected = media.key in selection
                    Box(Modifier.aspectRatio(.8f).clip(RoundedCornerShape(10.dp)).clickable(enabled = !busy) {
                        if (selected) selection = selection - media.key else if (selection.size < 8) selection = selection + media.key
                        if (cover !in selection) cover = selection.firstOrNull().orEmpty()
                    }) {
                        AsyncImage(media.preview, "Select moment", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (media.type == "video") Icon(Icons.Rounded.PlayArrow, "Video", Modifier.align(Alignment.BottomStart).size(20.dp), tint = Color.White)
                        Icon(if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, if (selected) "Selected" else "Unselected",
                            Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).background(SpotPalette.Surface.copy(alpha = .75f), CircleShape), tint = if (selected) SpotPalette.GoldDeep else SpotPalette.Muted)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (if (profile.isPrivate || profile.privacyUpdating) listOf("followers", "private") else listOf("public", "followers", "private")).forEach { v ->
                    FilterChip(audience == v, { audience = v }, label = { Text(audienceLabel(v)) }, enabled = !busy,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SpotPalette.GoldWash))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (existing != null) IconButton({ confirmDelete = true }, enabled = !busy) { Icon(Icons.Rounded.DeleteOutline, "Delete highlight") }
                Button({ action(false) }, Modifier.weight(1f), enabled = !busy && !loading && title.isNotBlank() && selection.isNotEmpty()) {
                    Text(if (busy) "Saving…" else "Save highlight")
                }
            }
        }
    }
    if (addStory) StoryCreator(profile, { addStory = false }, { addStory = false; retry++ })
    if (confirmDelete) SpotConfirmDialog(Icons.Rounded.DeleteOutline, "Delete highlight?", null, "Delete",
        onDismiss = { confirmDelete = false }, danger = true, busy = busy) { action(true) }
}
