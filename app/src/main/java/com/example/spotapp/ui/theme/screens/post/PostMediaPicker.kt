package com.example.spotapp.ui.theme.screens.post

import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.components.spotAmbientBackground
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.spotapp.data.*
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostMediaPicker(draft: PostDraft, update: (PostDraft) -> Unit, close: () -> Unit, next: () -> Unit, textPost: () -> Unit) {
    val context = LocalContext.current
    val library = remember { MediaLibraryRepository(context.applicationContext) }
    var revision by remember { mutableIntStateOf(0) }
    var access by remember { mutableStateOf(library.hasAccess()) }
    var fullAccess by remember { mutableStateOf(library.hasFullAccess()) }
    var filter by rememberSaveable { mutableStateOf(MediaFilter.ALL) }
    var album by rememberSaveable { mutableStateOf("") }
    var albumName by rememberSaveable { mutableStateOf("Recents") }
    var showAlbums by remember { mutableStateOf(false) }
    var albums by remember { mutableStateOf(emptyList<MediaAlbum>()) }
    var media by remember { mutableStateOf(emptyList<LibraryMedia>()) }
    var more by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = library.hasAccess(); fullAccess = library.hasFullAccess(); revision++
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { selected ->
        val values = selected.map { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            uri.toString()
        }
        val combined = (draft.mediaUris + values).distinct()
        update(draft.copy(mediaUris = combined.take(10)))
        if (combined.size > 10) error = "You can select up to 10 photos or videos."
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            access = library.hasAccess(); fullAccess = library.hasFullAccess(); revision++
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(filter, album, access, revision) {
        media = emptyList(); page = 0; more = false; error = ""
        if (access) {
            loading = true
            try { albums = library.albums(); val result = library.page(filter, album, null); media = result.items; more = result.hasMore }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Couldn't load this album. Try again or use the photo picker." }
            finally { loading = false }
        } else { albums = emptyList(); loading = false }
    }
    LaunchedEffect(page, filter, album, revision) {
        if (page > 0 && more && !loading) {
            loading = true
            try { val result = library.page(filter, album, media.lastOrNull()); media = (media + result.items).distinctBy { it.uri }; more = result.hasMore }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Couldn't load more media. Tap to retry." }
            finally { loading = false }
        }
    }
    Column(Modifier.fillMaxSize().spotAmbientBackground(PostIvory).navigationBarsPadding().testTag("post-picker")) {
        PostTopBar("Create post", close, close = true, step = 0) {
            IconButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) {
                Icon(Icons.Rounded.FolderOpen, "Browse photos and videos", tint = PostGold)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showAlbums = true }, enabled = access) {
                Text(albumName, style = MaterialTheme.typography.titleMedium, color = PostInk)
                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = PostMuted, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("Photos & videos", style = MaterialTheme.typography.bodySmall, color = PostMuted)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)
            .background(PostLine.copy(alpha = .5f), RoundedCornerShape(16.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            MediaFilter.entries.forEach { value ->
                val label = when (value) { MediaFilter.ALL -> "All"; MediaFilter.VIDEOS -> "Videos"; MediaFilter.PHOTOS -> "Photos" }
                val color by animateColorAsState(if (filter == value) SpotPalette.Surface else Color.Transparent, label = "Media filter")
                Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(color).clickable { filter = value }.heightIn(min = 44.dp).padding(6.dp)) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = if (filter == value) PostInk else PostMuted)
                }
            }
        }
        if (access && !fullAccess) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Selected photos & videos", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), color = PostMuted)
            TextButton(onClick = { permissions.launch(library.permissions()) }) { Text("Manage", color = PostGold) }
        }
        if (loading && media.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PostGold, modifier = Modifier.size(28.dp)) }
        else if (!access || media.isEmpty()) Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(42.dp), tint = PostGold)
            Spacer(Modifier.height(12.dp))
            Text(if (access) "No media in this album" else "Your next spotlight", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(if (access) "Choose another album or browse your files." else "Choose the photos and videos you want to share.", style = MaterialTheme.typography.bodySmall, color = PostMuted)
            if (!access) TextButton(onClick = { permissions.launch(library.permissions()) }) { Text("Open gallery", color = PostGold) }
            TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) { Text("Choose photos & videos", color = PostGold) }
        } else PostMediaGrid(media, draft.mediaUris, Modifier.weight(1f), { uri ->
            if (draft.mediaUris.size >= 10 && uri !in draft.mediaUris) error = "You can select up to 10 photos or videos."
            else { update(draft.copy(mediaUris = orderedMediaSelection(draft.mediaUris, uri))); error = "" }
        }, more, loading) { page++ }
        if (error.isNotBlank()) Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            PostError(error, Modifier.weight(1f)); TextButton(onClick = { revision++ }) { Text("Retry", color = PostGold) }
        }
        Surface(color = SpotPalette.Surface, shadowElevation = 3.dp, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp), border = BorderStroke(1.dp, PostLine)) {
            Column {
                if (draft.mediaUris.isNotEmpty()) MediaSelectionStrip(draft.mediaUris, draft.mediaEdits, null, {}, remove = { update(draft.copy(mediaUris = draft.mediaUris - it)) })
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    if (draft.mediaUris.isEmpty()) TextButton(onClick = textPost) { Text(if (draft.caption.isBlank()) "Write a text post" else "Continue draft", color = PostMuted) }
                    else Text("${draft.mediaUris.size} / 10 selected", style = MaterialTheme.typography.bodySmall, color = PostMuted)
                    PostNextButton(enabled = draft.mediaUris.isNotEmpty(), onClick = next)
                }
            }
        }
    }
    if (showAlbums) ModalBottomSheet(onDismissRequest = { showAlbums = false }, containerColor = PostIvory) {
        Text("Albums", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
            item { ListItem(headlineContent = { Text("Recents") }, modifier = Modifier.clickable { album = ""; albumName = "Recents"; showAlbums = false }, colors = ListItemDefaults.colors(containerColor = PostIvory)) }
            items(albums, key = { it.id }) { value -> ListItem(headlineContent = { Text(value.name) }, modifier = Modifier.clickable { album = value.id; albumName = value.name; showAlbums = false }, colors = ListItemDefaults.colors(containerColor = PostIvory)) }
        }
    }
}

@Composable
internal fun PostMediaGrid(media: List<LibraryMedia>, selected: List<String>, modifier: Modifier = Modifier, toggle: (String) -> Unit,
    more: Boolean = false, loading: Boolean = false, loadMore: () -> Unit = {}) {
    LazyVerticalGrid(GridCells.Fixed(3), modifier.testTag("post-media-grid"), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(media, key = { it.uri }) { item ->
            val index = selected.indexOf(item.uri)
            Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).border(if (index >= 0) 2.dp else 0.dp, if (index >= 0) PostGold else Color.Transparent, RoundedCornerShape(10.dp))
                .clickable { toggle(item.uri) }.semantics { contentDescription = "${if (item.isVideo) "Video" else "Photo"}${if (index >= 0) ", selected ${index + 1}" else ""}" }) {
                MediaThumbnail(item.uri, Modifier.fillMaxSize(), knownVideo = item.isVideo)
                Surface(color = if (index >= 0) PostGold else Color.Black.copy(alpha = .35f), shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White), modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(24.dp)) {
                    Box(contentAlignment = Alignment.Center) { if (index >= 0) Text("${index + 1}", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                }
                if (item.isVideo && item.durationMs > 0) Text("${item.durationMs / 60000}:${(item.durationMs / 1000 % 60).toString().padStart(2, '0')}", Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = .35f)).padding(horizontal = 4.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (more) item(span = { GridItemSpan(3) }) {
            TextButton(onClick = loadMore, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text(if (loading) "Loading…" else "Load more", color = PostGold) }
        }
    }
}
