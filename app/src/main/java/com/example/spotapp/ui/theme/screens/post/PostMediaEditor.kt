package com.example.spotapp.ui.theme.screens.post

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.components.*
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/** One light or color control in the Edit tab, and how it reads and writes the look. */
private data class LookTool(val key: String, val label: String, val icon: ImageVector,
    val read: (MediaLook) -> Float, val write: (MediaLook, Float) -> MediaLook)

private val LookTools = listOf(
    LookTool("brightness", "Brightness", Icons.Rounded.LightMode, { it.brightness }, { look, v -> look.copy(brightness = v) }),
    LookTool("contrast", "Contrast", Icons.Rounded.Contrast, { it.contrast }, { look, v -> look.copy(contrast = v) }),
    LookTool("saturation", "Saturation", Icons.Rounded.Palette, { it.saturation }, { look, v -> look.copy(saturation = v) }),
    LookTool("warmth", "Warmth", Icons.Rounded.Thermostat, { it.warmth }, { look, v -> look.copy(warmth = v) }),
)

private val CropChoices = listOf("original" to "Original", "square" to "1:1", "portrait" to "4:5", "landscape" to "16:9")

/**
 * Instagram-style editor: a Filter tab and an Edit tab (crop, rotate, brightness, contrast, saturation, warmth)
 * for photos, and sound for videos. The look is drawn live over the preview, so sliders respond instantly.
 */
@Composable
internal fun PostMediaEditor(draft: PostDraft, update: (PostDraft) -> Unit, active: Boolean, back: () -> Unit, add: () -> Unit, next: () -> Unit,
    selection: String? = null, onSelection: (String) -> Unit = {}) {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf(draft.mediaUris.firstOrNull().orEmpty()) }
    val uri = (selection ?: selected).takeIf { it in draft.mediaUris } ?: draft.mediaUris.firstOrNull().orEmpty()
    val edit = draft.mediaEdits[uri] ?: PostMediaEdit()
    var tab by rememberSaveable { mutableStateOf("filter") }
    // The open tool: "crop", one of the LookTools, or null for the tab strip.
    var tool by rememberSaveable(uri) { mutableStateOf<String?>(null) }
    var before by remember(uri) { mutableStateOf(edit) }
    var remove by remember { mutableStateOf(false) }
    var error by remember(uri) { mutableStateOf("") }
    // Its own scroll position, so the Edit tools always open at Crop.
    val toolsRow = remember { LazyListState() }
    val info by produceState<LibraryMedia?>(null, uri) {
        value = null
        try { value = MediaLibraryRepository(context).describe(uri) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "This media is unavailable. Remove it and choose it again." }
    }
    // Rendered for rotation and crop only; the filter strip reuses it for its small previews.
    val bitmap by produceState<android.graphics.Bitmap?>(null, uri, edit.geometry, info?.isVideo) {
        value = null
        if (info?.isVideo == false) try { value = PostMediaExporter.imagePreview(context, uri, edit.geometry) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "This photo couldn't open. Remove it and choose another." }
    }
    fun change(value: PostMediaEdit) { update(draft.copy(mediaEdits = draft.mediaEdits + (uri to value))); error = "" }
    fun open(key: String) { before = edit; tool = key }
    val photo = info?.isVideo == false
    val image = bitmap?.asImageBitmap()
    Column(Modifier.fillMaxSize().background(PostStage).navigationBarsPadding().testTag("post-editor")) {
        PostTopBar("Edit media", back, dark = true, step = 1) {
            Text("${draft.mediaUris.indexOf(uri) + 1} / ${draft.mediaUris.size}", color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 20.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (info?.isVideo == true) SpotVideo(uri, active && !remove, Modifier.fillMaxSize(), muted = edit.muted)
            else if (image != null) Image(image, "Edited photo preview", Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                colorFilter = edit.look.colorFilter())
            else if (error.isBlank()) Box(Modifier.align(Alignment.Center)) { SpotLoading(true, SkeletonKind.POST) }
            if (error.isNotBlank()) Text(error, Modifier.align(Alignment.Center).padding(32.dp), color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
        if (tool == null) MediaSelectionStrip(draft.mediaUris, draft.mediaEdits, uri, { selected = it; onSelection(it) }, add = add, dark = true)
        val lookTool = LookTools.firstOrNull { it.key == tool }
        when {
            photo && tool == "crop" -> ToolPanel("Crop", { change(before); tool = null }, { tool = null }) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CropChoices.forEach { (value, label) ->
                        FilterChip(edit.crop == value, { change(edit.copy(crop = value)) }, { Text(label, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(containerColor = Color.Transparent, labelColor = Color.White,
                                selectedContainerColor = PostGold, selectedLabelColor = SpotPalette.OnGold))
                    }
                }
                Text("Crops from the center. Your original stays untouched.", Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .6f))
            }
            photo && lookTool != null -> ToolPanel(lookTool.label, { change(before); tool = null }, { tool = null }) {
                val value = lookTool.read(edit.look)
                Text("${(value * 100).roundToInt()}", Modifier.fillMaxWidth(), color = Color.White, style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center)
                Slider(value, { change(edit.copy(look = lookTool.write(edit.look, it))) }, Modifier.padding(horizontal = 16.dp)
                    .semantics { contentDescription = lookTool.label }.testTag("look-slider-${lookTool.key}"), valueRange = -1f..1f,
                    // The slider goes both ways from 0, so the track stays neutral and only the thumb is gold.
                    colors = SliderDefaults.colors(thumbColor = PostGold, activeTrackColor = Color.White.copy(alpha = .25f),
                        inactiveTrackColor = Color.White.copy(alpha = .25f)))
            }
            photo -> {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    EditorTab("Filter", tab == "filter", Modifier.weight(1f)) { tab = "filter" }
                    EditorTab("Edit", tab == "edit", Modifier.weight(1f)) { tab = "edit" }
                }
                if (tab == "filter") LookFilterRow(edit.look.filter, { change(edit.copy(look = edit.look.copy(filter = it))) },
                    Modifier.padding(vertical = 8.dp), dark = true) { filter ->
                    if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = filter)
                    else Box(Modifier.fillMaxSize().background(SpotPalette.Surface.copy(alpha = .08f)))
                }
                else LazyRow(Modifier.fillMaxWidth().padding(vertical = 8.dp), toolsRow, contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { EditTool(Icons.Rounded.Crop, "Crop", edit.crop != "original") { open("crop") } }
                    item { EditTool(Icons.Rounded.RotateRight, "Rotate", edit.rotation != 0) { change(edit.rotate()) } }
                    items(LookTools, key = { it.key }) { look -> EditTool(look.icon, look.label, look.read(edit.look) != 0f) { open(look.key) } }
                }
            }
            info?.isVideo == true -> Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                EditTool(if (edit.muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, if (edit.muted) "Sound off" else "Sound on", edit.muted) {
                    change(edit.copy(muted = !edit.muted))
                }
                Text("Filters are for photos.", Modifier.padding(start = 12.dp), color = Color.White.copy(alpha = .6f), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (tool == null) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (draft.mediaUris.size > 1) {
                IconButton(onClick = { update(draft.copy(mediaUris = movePostMedia(draft.mediaUris, draft.mediaUris.indexOf(uri), -1))) }, enabled = draft.mediaUris.indexOf(uri) > 0) { Icon(Icons.Rounded.KeyboardArrowLeft, "Move earlier", tint = if (draft.mediaUris.indexOf(uri) > 0) Color.White else Color.DarkGray) }
                IconButton(onClick = { update(draft.copy(mediaUris = movePostMedia(draft.mediaUris, draft.mediaUris.indexOf(uri), 1))) }, enabled = draft.mediaUris.indexOf(uri) < draft.mediaUris.lastIndex) { Icon(Icons.Rounded.KeyboardArrowRight, "Move later", tint = if (draft.mediaUris.indexOf(uri) < draft.mediaUris.lastIndex) Color.White else Color.DarkGray) }
            }
            if (edit != PostMediaEdit()) IconButton(onClick = { change(PostMediaEdit()) }) { Icon(Icons.Rounded.RestartAlt, "Reset edits", tint = Color.White) }
            IconButton(onClick = { remove = true }) { Icon(Icons.Rounded.DeleteOutline, "Remove this media", tint = Color.White) }
            Spacer(Modifier.weight(1f)); PostNextButton(enabled = error.isBlank() && info != null, onClick = next)
        }
    }
    if (remove) SpotConfirmDialog(Icons.Rounded.DeleteOutline, "Remove this media?", null, "Remove",
        onDismiss = { remove = false }, danger = true, dismiss = "Keep") {
        val remaining = draft.mediaUris - uri; update(draft.copy(mediaUris = remaining, mediaEdits = draft.mediaEdits - uri)); remove = false; if (remaining.isEmpty()) back()
    }
}

/** A tool's controls with Cancel (undo this tool's changes) and Done, like Instagram. */
@Composable
private fun ToolPanel(title: String, cancel: () -> Unit, done: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        content()
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(cancel) { Text("Cancel", color = Color.White) }
            Text(title, Modifier.weight(1f), color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center, maxLines = 1)
            TextButton(done, Modifier.testTag("post-tool-done")) { Text("Done", color = PostGold) }
        }
    }
}

@Composable
private fun EditorTab(label: String, selected: Boolean, modifier: Modifier, select: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Tab, onClick = select).heightIn(min = 44.dp).padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = if (selected) Color.White else Color.White.copy(alpha = .55f), style = MaterialTheme.typography.titleSmall)
        Box(Modifier.padding(top = 8.dp).fillMaxWidth(.5f).height(2.dp).background(if (selected) PostGold else Color.Transparent, CircleShape))
    }
}

@Composable
private fun EditTool(icon: ImageVector, label: String, changed: Boolean, open: () -> Unit) {
    Column(Modifier.width(76.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = label, onClick = open).padding(vertical = 6.dp)
        .testTag("edit-tool-$label"), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).background(if (changed) PostGold else Color.White.copy(alpha = .1f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = if (changed) SpotPalette.OnGold else Color.White)
        }
        Text(label, Modifier.padding(top = 6.dp), color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
