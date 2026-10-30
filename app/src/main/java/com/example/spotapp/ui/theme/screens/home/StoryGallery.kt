package com.example.spotapp.ui.theme.screens.home

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.screens.post.MediaThumbnail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val GalleryInk = Color(0xFF171717)

/**
 * Instagram-style "Add to story": your recent photos and videos straight away, an album switcher,
 * and the system file browser for anything outside the gallery. Tapping an item opens it in the editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StoryGallery(close: () -> Unit, choose: (String) -> Unit) {
    val context = LocalContext.current
    val library = remember { MediaLibraryRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var access by remember { mutableStateOf(library.hasAccess()) }
    var fullAccess by remember { mutableStateOf(library.hasFullAccess()) }
    var revision by remember { mutableIntStateOf(0) }
    var album by rememberSaveable { mutableStateOf("") }
    var albumName by rememberSaveable { mutableStateOf("Recents") }
    var albums by remember { mutableStateOf(emptyList<MediaAlbum>()) }
    var showAlbums by remember { mutableStateOf(false) }
    var media by remember { mutableStateOf(emptyList<LibraryMedia>()) }
    var more by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = library.hasAccess(); fullAccess = library.hasFullAccess(); revision++
    }
    val browse = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { value ->
        if (value != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(value, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            choose(value.toString())
        }
    }
    BackHandler(onBack = close)
    // Like Instagram, ask right away; Android only shows the prompt while it's still allowed to.
    LaunchedEffect(Unit) { if (!access) permissions.launch(library.permissions()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { access = library.hasAccess(); fullAccess = library.hasFullAccess(); revision++ }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(album, access, revision) {
        media = emptyList(); more = false; error = ""
        if (!access) return@LaunchedEffect
        loading = true
        try { albums = library.albums(); val page = library.page(MediaFilter.ALL, album, null); media = page.items; more = page.hasMore }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Couldn't load your photos. Try again or browse your files." }
        finally { loading = false }
    }
    fun loadMore() {
        if (loading || !more) return
        loading = true
        scope.launch {
            try { val page = library.page(MediaFilter.ALL, album, media.lastOrNull()); media = (media + page.items).distinctBy { it.uri }; more = page.hasMore }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Couldn't load more. Scroll to try again." }
            finally { loading = false }
        }
    }
    val grid = rememberLazyGridState()
    LaunchedEffect(grid) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.distinctUntilChanged()
            .collect { last -> if (last >= media.size - 12) loadMore() }
    }

    Column(Modifier.fillMaxSize().background(GalleryInk).safeDrawingPadding().testTag("story-gallery")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            StoryTool(Icons.Rounded.Close, "Close", close)
            Text("Add to story", Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            StoryTool(Icons.Rounded.FolderOpen, "Browse files", { browse.launch(arrayOf("image/*", "video/*")) })
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ showAlbums = true }, enabled = access, modifier = Modifier.testTag("story-albums")) {
                Text(albumName, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(20.dp), tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            if (access && !fullAccess) TextButton({ permissions.launch(library.permissions()) }) { Text("Manage access", color = StoryGold) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !access -> GalleryMessage(Icons.Rounded.PhotoLibrary, "Share a moment", "Allow SPOT to show your photos and videos here.") {
                    Button({ permissions.launch(library.permissions()) }, colors = ButtonDefaults.buttonColors(containerColor = StoryGold, contentColor = GalleryInk)) { Text("Allow access") }
                    TextButton({ browse.launch(arrayOf("image/*", "video/*")) }) { Text("Browse files", color = Color.White) }
                }
                loading && media.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = StoryGold)
                media.isEmpty() -> GalleryMessage(Icons.Rounded.HideImage, "Nothing here yet", "Choose another album or browse your files.") {
                    TextButton({ browse.launch(arrayOf("image/*", "video/*")) }) { Text("Browse files", color = StoryGold) }
                }
                else -> LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize().testTag("story-gallery-grid"), grid,
                    contentPadding = PaddingValues(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(media, key = { it.uri }) { item ->
                        val length = if (item.isVideo && item.durationMs > 0) "%d:%02d".format(item.durationMs / 60_000, item.durationMs / 1_000 % 60) else ""
                        Box(Modifier.aspectRatio(.75f).clickable(onClickLabel = "Add to story") { choose(item.uri) }
                            .semantics { contentDescription = if (item.isVideo) "Video $length" else "Photo" }) {
                            MediaThumbnail(item.uri, Modifier.fillMaxSize(), knownVideo = item.isVideo)
                            if (length.isNotEmpty()) Text(length, Modifier.align(Alignment.BottomEnd).padding(6.dp),
                                color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        if (error.isNotBlank()) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(error, Modifier.weight(1f), color = StoryGold, style = MaterialTheme.typography.bodySmall)
            TextButton({ revision++ }) { Text("Retry", color = Color.White) }
        }
    }
    if (showAlbums) ModalBottomSheet(onDismissRequest = { showAlbums = false }, containerColor = GalleryInk) {
        Text("Albums", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = Color.White, style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
            item { AlbumRow("Recents", album.isEmpty()) { album = ""; albumName = "Recents"; showAlbums = false } }
            items(albums, key = { it.id }) { value -> AlbumRow(value.name, album == value.id) { album = value.id; albumName = value.name; showAlbums = false } }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AlbumRow(name: String, selected: Boolean, select: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = select).heightIn(min = 52.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (selected) Icon(Icons.Rounded.Check, "Selected", tint = StoryGold)
    }
}

@Composable
private fun BoxScope.GalleryMessage(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String,
    actions: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(44.dp), tint = StoryGold)
        Text(title, Modifier.padding(top = 12.dp), color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(body, Modifier.padding(top = 6.dp, bottom = 12.dp), color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center)
        actions()
    }
}
