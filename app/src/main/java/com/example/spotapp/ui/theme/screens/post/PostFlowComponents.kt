package com.example.spotapp.ui.theme.screens.post

import com.example.spotapp.ui.theme.components.SpotPalette
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import com.example.spotapp.ui.theme.components.SpotHeaderAction
import com.example.spotapp.ui.theme.components.SpotPillButton
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.spotapp.data.*
import kotlinx.coroutines.CancellationException

internal val PostGold get() = SpotPalette.GoldDeep
internal val PostIvory get() = SpotPalette.Ivory
// Text follows the theme; PostStage stays black behind photos and video in both themes.
internal val PostInk get() = if (SpotPalette.dark) SpotPalette.Ink else Color(0xFF171717)
internal val PostStage = Color(0xFF171717)
internal val PostLine get() = SpotPalette.Line
internal val PostMuted get() = if (SpotPalette.dark) SpotPalette.Muted else Color(0xFF77746C)

@Composable
internal fun PostTopBar(title: String, back: () -> Unit, close: Boolean = false, dark: Boolean = false,
    step: Int? = null,
    actions: @Composable RowScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon = if (close) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack
            val label = if (close) "Close create post" else "Back"
            // The dark editor keeps a bare icon; light steps share the app's round header buttons.
            if (dark) IconButton(onClick = back) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp)) }
            else SpotHeaderAction(icon, label, back)
            Text(title, style = MaterialTheme.typography.titleLarge, color = if (dark) Color.White else PostInk,
                modifier = Modifier.weight(1f).padding(start = if (dark) 0.dp else 6.dp))
            actions()
        }
        step?.let { active ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Media", "Edit", "Details").forEachIndexed { index, label ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        val activeColor = if (dark) Color(0xFFD1AD32) else PostGold
                        Box(Modifier.fillMaxWidth().height(3.dp).background(if (index <= active) activeColor else if (dark) Color.White.copy(alpha = .15f) else PostLine, CircleShape))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = if (index == active) activeColor else if (dark) Color.White.copy(alpha = .55f) else PostMuted)
                    }
                }
            }
        }
    }
}

@Composable
internal fun MediaThumbnail(uri: String, modifier: Modifier = Modifier, knownVideo: Boolean? = null, edit: PostMediaEdit = PostMediaEdit()) {
    val context = LocalContext.current
    val video by produceState(knownVideo, uri, knownVideo) {
        if (knownVideo == null) value = try { MediaLibraryRepository(context).describe(uri).isVideo } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }
    val bitmap by produceState<android.graphics.Bitmap?>(null, uri, video, edit) {
        value = try {
            if (video == true) PostMediaExporter.thumbnail(context, uri)
            else if (edit.changesImage) PostMediaExporter.imagePreview(context, uri, edit, 240) else null
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }
    Box(modifier.background(if (SpotPalette.dark) SpotPalette.Track else Color(0xFFEDE9E0)), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) Image(image.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else if (video != true) AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.Videocam, null, tint = PostMuted)
        if (video == true) Icon(Icons.Rounded.PlayArrow, null, Modifier.align(Alignment.BottomStart).padding(3.dp).size(15.dp).background(Color.Black.copy(alpha = .35f), CircleShape), tint = Color.White)
    }
}

@Composable
internal fun MediaSelectionStrip(media: List<String>, edits: Map<String, PostMediaEdit>, active: String?, select: (String) -> Unit,
    remove: ((String) -> Unit)? = null, add: (() -> Unit)? = null, dark: Boolean = false) {
    LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(media, key = { _, uri -> uri }) { index, uri ->
            Box {
                Surface(onClick = { select(uri) }, shape = RoundedCornerShape(10.dp), border = BorderStroke(if (active == uri) 2.dp else 1.dp, if (active == uri) PostGold else PostLine), modifier = Modifier.size(58.dp).testTag("selected-media-$index")) {
                    MediaThumbnail(uri, edit = edits[uri] ?: PostMediaEdit())
                }
                Text("${index + 1}", Modifier.align(Alignment.BottomEnd).padding(2.dp).background(PostGold, CircleShape).padding(horizontal = 5.dp, vertical = 1.dp), color = MaterialTheme.colorScheme.onTertiary, style = MaterialTheme.typography.labelSmall)
                if (remove != null) IconButton(onClick = { remove(uri) }, Modifier.align(Alignment.TopEnd).size(26.dp)) { Icon(Icons.Rounded.Close, "Remove selected media ${index + 1}", Modifier.size(20.dp).background(Color.Black.copy(alpha = .65f), CircleShape).padding(3.dp), tint = Color.White) }
            }
        }
        if (add != null && media.size < 10) item {
            OutlinedIconButton(onClick = add, modifier = Modifier.size(58.dp), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, if (dark) Color.White.copy(alpha = .3f) else PostLine)) {
                Icon(Icons.Rounded.Add, "Add media", tint = if (dark) Color.White else PostGold)
            }
        }
    }
}

@Composable
internal fun PostNextButton(label: String = "Next", enabled: Boolean = true, onClick: () -> Unit) {
    SpotPillButton(label, onClick, Modifier.testTag("post-next"), trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward, enabled = enabled)
}

@Composable
internal fun PostError(error: String, modifier: Modifier = Modifier) {
    if (error.isNotBlank()) Text(error, modifier.testTag("post-error"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}
