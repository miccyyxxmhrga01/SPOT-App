package com.example.spotapp.ui.theme.screens.home

import com.example.spotapp.ui.theme.components.SpotPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.example.spotapp.data.LookFilters
import com.example.spotapp.data.StoryComposition
import com.example.spotapp.data.StoryOverlay
import com.example.spotapp.data.audienceLabel
import com.example.spotapp.ui.theme.components.LookFilterRow
import java.util.UUID

internal val StoryGold = Color(0xFFFDD87A)
private val StoryInk = Color(0xFF171717)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StoryTool(icon: ImageVector, label: String, action: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, selected: Boolean = false) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        IconButton(action, modifier.size(48.dp).background(if (selected) StoryGold else Color.Black.copy(alpha = .38f), CircleShape), enabled = enabled) {
            Icon(icon, label, Modifier.size(23.dp), tint = if (!enabled) Color.Gray else if (selected) StoryInk else Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StoryEditor(uri: String?, video: Boolean, durationMs: Long, composition: StoryComposition,
    update: (StoryComposition) -> Unit, audience: String, privateAccount: Boolean, onAudience: (String) -> Unit,
    busy: Boolean, progress: Float, error: String, canShare: Boolean, close: () -> Unit, choose: () -> Unit, share: () -> Unit) {
    StorySystemBars()
    var panel by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable(uri) { mutableStateOf<String?>(null) }
    val layer = composition.overlays.firstOrNull { it.id == selected }
    val editing = uri != null && !busy
    Column(Modifier.fillMaxSize().background(StoryInk).safeDrawingPadding().imePadding().testTag("story-editor")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            StoryTool(Icons.Rounded.Close, "Close editor", close, enabled = !busy)
            Text("Story", Modifier.weight(1f).padding(horizontal = 16.dp), color = Color.White, style = MaterialTheme.typography.titleSmall)
            StoryTool(Icons.Rounded.PhotoLibrary, "Choose photo or video", choose, enabled = !busy)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (uri == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                StoryTool(Icons.Rounded.AddPhotoAlternate, "Add photo or video", choose, selected = true)
            } else StoryCanvas(uri, video, composition, active = !busy && panel !in listOf("text", "stickers", "audience"),
                modifier = Modifier.fillMaxSize(), editable = !busy, cropMode = panel == "crop", selected = selected,
                onChange = update, onSelect = { selected = it })
            if (uri != null && panel !in listOf("crop", "trim", "filters")) Column(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!video) StoryTool(Icons.Rounded.AutoFixHigh, "Filters", { panel = "filters" }, enabled = editing,
                    selected = composition.filter != "normal")
                StoryTool(Icons.Rounded.TextFields, if (layer != null) "Edit text" else "Add text", { panel = "text" }, enabled = editing && (layer != null || composition.overlays.size < 4))
                StoryTool(Icons.Rounded.EmojiEmotions, "Add sticker", { panel = "stickers" }, enabled = editing && composition.overlays.size < 4)
                StoryTool(Icons.Rounded.Crop, "Crop and zoom", { panel = "crop" }, enabled = editing)
                if (video) {
                    StoryTool(Icons.Rounded.ContentCut, "Trim video", { panel = "trim" }, enabled = editing && durationMs > 0)
                    StoryTool(if (composition.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        if (composition.muted) "Unmute video" else "Mute video", { update(composition.copy(muted = !composition.muted)) }, enabled = editing)
                }
                if (layer != null) StoryTool(Icons.Rounded.DeleteOutline, "Remove selected layer", {
                    update(composition.copy(overlays = composition.overlays.filterNot { it.id == selected })); selected = null
                }, enabled = editing)
            }
        }
        if (panel == "crop") Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StoryTool(if (composition.fill) Icons.Rounded.FitScreen else Icons.Rounded.CropFree, if (composition.fill) "Fit media" else "Fill story", { update(composition.copy(fill = !composition.fill)) }, selected = composition.fill)
                StoryTool(Icons.AutoMirrored.Rounded.RotateRight, "Rotate media", { update(composition.copy(rotation = (composition.rotation + 1) % 4)) })
                StoryTool(Icons.Rounded.RestartAlt, "Reset crop", { update(composition.copy(scale = 1.0, offsetX = 0.0, offsetY = 0.0, fill = false, rotation = 0)) })
                StoryTool(Icons.Rounded.Check, "Done cropping", { panel = "" }, selected = true)
            }
            Slider(composition.scale.toFloat(), { update(composition.copy(scale = it.toDouble())) }, valueRange = 1f..4f,
                modifier = Modifier.semantics { contentDescription = "Zoom" }, colors = SliderDefaults.colors(thumbColor = StoryGold, activeTrackColor = StoryGold))
        }
        if (panel == "filters" && uri != null && !video) Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(LookFilters.firstOrNull { it.key == composition.filter }?.label ?: "Normal", Modifier.weight(1f),
                    color = Color.White, style = MaterialTheme.typography.titleSmall)
                StoryTool(Icons.Rounded.Check, "Done with filters", { panel = "" }, selected = true)
            }
            LookFilterRow(composition.filter, { update(composition.copy(filter = it)) }, Modifier.padding(vertical = 8.dp), dark = true) { filter ->
                AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = filter)
            }
        }
        if (panel == "trim" && durationMs > 0) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${storyTime(composition.trimStartMs)} – ${storyTime(composition.trimEndMs.takeIf { it > 0 } ?: durationMs)}",
                    Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.labelMedium)
                StoryTool(Icons.Rounded.Check, "Done trimming", { panel = "" }, selected = true)
            }
            val end = composition.trimEndMs.takeIf { it > 0 } ?: minOf(durationMs, 60_000)
            RangeSlider(composition.trimStartMs.toFloat()..end.toFloat(), { range ->
                val chosen = clampStoryTrim(range.start.toLong(), range.endInclusive.toLong(), durationMs, composition.trimStartMs)
                update(composition.copy(trimStartMs = chosen.first, trimEndMs = chosen.second))
            }, valueRange = 0f..durationMs.toFloat(), modifier = Modifier.testTag("story-trim-range").semantics { contentDescription = "Video start and end" },
                colors = SliderDefaults.colors(thumbColor = StoryGold, activeTrackColor = StoryGold))
        }
        if (error.isNotBlank()) Text(error, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = StoryGold, style = MaterialTheme.typography.bodySmall)
        if (busy) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = StoryGold)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val audienceIcon = when (audience) { "public" -> Icons.Rounded.Public; "private" -> Icons.Rounded.Lock; else -> Icons.Rounded.Group }
            StoryTool(audienceIcon, "Audience: ${audienceLabel(audience)}", { panel = "audience" }, enabled = !busy)
            Spacer(Modifier.weight(1f))
            StoryTool(Icons.AutoMirrored.Rounded.ArrowForward, if (busy) "Sharing story" else "Share story", share,
                Modifier.testTag("publish-story"), enabled = canShare && !busy, selected = true)
        }
    }
    if (panel == "text") StoryTextSheet(layer, { panel = "" }, { added ->
        update(composition.copy(overlays = if (layer == null) composition.overlays + added else composition.overlays.map { if (it.id == layer.id) added else it }))
        selected = added.id; panel = ""
    })
    if (panel == "stickers") ModalBottomSheet(onDismissRequest = { panel = "" }, containerColor = SpotPalette.Ivory) {
        LazyVerticalGrid(GridCells.Fixed(5), Modifier.fillMaxWidth().height(250.dp).padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(listOf("😀", "😍", "🔥", "✨", "❤️", "🎉", "👏", "💯", "🎤", "🎸", "🎨", "🎭", "💃", "🏆", "⭐", "🎬", "🎵", "📸", "🙌", "💪")) { emoji ->
                TextButton({
                    val added = StoryOverlay(UUID.randomUUID().toString(), emoji, size = .12, background = false)
                    update(composition.copy(overlays = composition.overlays + added)); selected = added.id; panel = ""
                }, Modifier.size(48.dp)) { Text(emoji, style = MaterialTheme.typography.headlineMedium) }
            }
        }
    }
    if (panel == "audience") ModalBottomSheet(onDismissRequest = { panel = "" }, containerColor = SpotPalette.Ivory) {
        Text("Who can view?", Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.titleMedium)
        (if (privateAccount) listOf("followers", "private") else listOf("public", "followers", "private")).forEach { value ->
            TextButton({ onAudience(value); panel = "" }, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Icon(when (value) { "public" -> Icons.Rounded.Public; "private" -> Icons.Rounded.Lock; else -> Icons.Rounded.Group }, null, Modifier.size(22.dp))
                Text(audienceLabel(value), Modifier.weight(1f).padding(12.dp), color = Color(0xFF171717))
                if (audience == value) Icon(Icons.Rounded.Check, null)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoryTextSheet(existing: StoryOverlay?, close: () -> Unit, done: (StoryOverlay) -> Unit) {
    var text by rememberSaveable { mutableStateOf(existing?.text.orEmpty()) }
    var color by rememberSaveable { mutableLongStateOf(existing?.color ?: 0xFFFFFFFF) }
    var size by rememberSaveable { mutableFloatStateOf(existing?.size?.toFloat() ?: .07f) }
    var background by rememberSaveable { mutableStateOf(existing?.background ?: true) }
    ModalBottomSheet(onDismissRequest = close, containerColor = StoryInk) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            OutlinedTextField(text, { text = it.take(160) }, Modifier.fillMaxWidth().testTag("story-text-input"), placeholder = { Text("Write something…") },
                minLines = 2, maxLines = 4, textStyle = MaterialTheme.typography.titleMedium.copy(color = Color(color)),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = StoryGold, unfocusedBorderColor = Color.Gray, cursorColor = StoryGold))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0xFFFFFFFF, 0xFFFDD87A, 0xFF171717, 0xFFFF8BA0, 0xFF9EDBC2).forEachIndexed { index, value ->
                    IconButton({ color = value }, Modifier.size(44.dp).semantics { contentDescription = listOf("White", "Gold", "Black", "Pink", "Mint")[index] }) {
                        Box(Modifier.size(24.dp).background(Color(value), CircleShape).border(if (color == value) 2.dp else 0.dp, Color.White, CircleShape))
                    }
                }
            }
            Slider(size, { size = it }, valueRange = .025f.. .15f, modifier = Modifier.semantics { contentDescription = "Text size" },
                colors = SliderDefaults.colors(thumbColor = StoryGold, activeTrackColor = StoryGold))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StoryTool(Icons.Rounded.FormatColorFill, "Text background", { background = !background }, selected = background)
                StoryTool(Icons.Rounded.Check, "Done editing text", {
                    done((existing ?: StoryOverlay(UUID.randomUUID().toString(), text.trim())).copy(text = text.trim(), color = color, size = size.toDouble().coerceIn(.025, .15), background = background))
                }, selected = true, enabled = text.isNotBlank())
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

internal fun clampStoryTrim(start: Long, end: Long, duration: Long, previousStart: Long): Pair<Long, Long> {
    val minimum = minOf(1_000L, duration)
    var a = start.coerceIn(0, (duration - minimum).coerceAtLeast(0))
    var b = end.coerceIn(a + minimum, duration)
    if (b - a > 60_000) { if (a != previousStart) b = a + 60_000 else a = b - 60_000 }
    return a to b
}

private fun storyTime(ms: Long): String = "%d:%02d".format(ms / 60_000, ms / 1_000 % 60)
