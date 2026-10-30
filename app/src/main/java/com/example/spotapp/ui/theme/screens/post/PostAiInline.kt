package com.example.spotapp.ui.theme.screens.post

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.spotapp.data.PostDraft
import com.example.spotapp.data.PostMediaEdit
import com.example.spotapp.data.ai.mergeTags
import com.example.spotapp.ui.theme.components.SpotPalette

/** AI Assist for one draft: suggestions only count for the media, categories and post type they were written for. */
internal class PostAiAssistUi(val state: PostAiAssistState, val available: Boolean, val ask: (AiAssistKind) -> Unit,
    val retry: () -> Unit)

@Composable
internal fun rememberPostAiAssist(draft: PostDraft): PostAiAssistUi {
    val cover = draft.mediaUris.firstOrNull()
    val context = LocalContext.current
    val ai: PostAiAssistViewModel = viewModel(key = "post-ai-${draft.id}")
    val state by ai.uiState.collectAsStateWithLifecycle()
    val edit = cover?.let { draft.mediaEdits[it] } ?: PostMediaEdit()
    val key = cover?.let { PostAiAssistViewModel.inputKey(it, edit, draft.categories, draft.personal) }
    // Ideas written for other media, categories or post type are stale; show the idle state instead.
    val current = state.takeIf { key != null && it.inputKey == key } ?: PostAiAssistState()
    return PostAiAssistUi(current, cover != null,
        ask = { kind -> if (cover != null) ai.request(context, kind, cover, edit, draft.categories, draft.caption, draft.personal) },
        retry = { if (cover != null) ai.retry(context, cover, edit, draft.categories, draft.caption, draft.personal) })
}

/**
 * "Write with AI" under the caption: Gemini writes caption ideas or hashtags from the post's first photo or video.
 * The ideas show where they're used: caption ideas below this row, hashtag ideas in the hashtags section.
 */
@Composable
internal fun AiWriteRow(ai: PostAiAssistUi, enabled: Boolean) {
    val state = ai.state
    Column(Modifier.fillMaxWidth().testTag("ai-assist"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp), tint = PostGold)
            Spacer(Modifier.width(6.dp))
            Text("Write with AI", style = MaterialTheme.typography.labelLarge, color = PostInk)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiIdeaButton("Caption ideas", "ai-generate-caption", ai.available && enabled && !state.loading, Modifier.weight(1f)) { ai.ask(AiAssistKind.CAPTION) }
            AiIdeaButton("Hashtags", "ai-suggest-tags", ai.available && enabled && !state.loading, Modifier.weight(1f)) { ai.ask(AiAssistKind.TAGS) }
        }
        when {
            state.loading -> {
                LinearProgressIndicator(Modifier.fillMaxWidth().testTag("ai-assist-progress"), color = PostGold, trackColor = PostLine)
                Text("Writing ideas…", style = MaterialTheme.typography.bodySmall, color = PostMuted)
            }
            state.error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { PostError(state.error) }
                TextButton(onClick = ai.retry, enabled = enabled) { Text("Try again", color = PostGold) }
            }
            !ai.available -> Text("Add a photo or video to get ideas.", style = MaterialTheme.typography.bodySmall, color = PostMuted)
            state.suggestions == null -> Text("From your first photo or video. Nothing is added until you choose it.",
                style = MaterialTheme.typography.bodySmall, color = PostMuted)
        }
    }
}

@Composable
private fun AiIdeaButton(label: String, tag: String, enabled: Boolean, modifier: Modifier = Modifier, click: () -> Unit) {
    Surface(onClick = click, enabled = enabled, shape = RoundedCornerShape(50), color = SpotPalette.GoldWash,
        border = BorderStroke(1.dp, SpotPalette.GoldLine), modifier = modifier.heightIn(min = 40.dp).testTag(tag)) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (enabled) SpotPalette.GoldInk else PostMuted, maxLines = 1)
        }
    }
}

/** Gemini's caption ideas, one at a time, inline under the caption. */
@Composable
internal fun AiCaptionIdeas(ai: PostAiAssistUi, draft: PostDraft, update: (PostDraft) -> Unit, enabled: Boolean) {
    val ideas = ai.state.suggestions?.captions.orEmpty()
    if (!ai.state.showCaptions || ai.state.suggestions == null) return
    if (ideas.isEmpty()) Text("No caption ideas this time. Tap Caption ideas again.", style = MaterialTheme.typography.bodySmall, color = PostMuted)
    else PostCaptionIdeasCard(ideas, "${ai.state.inputKey}|${ideas.hashCode()}", draft, update, enabled,
        title = "Caption ideas", subtitle = "AI ideas · ready to edit", ai = true, tag = "ai-caption-ideas",
        onRefresh = ai.retry)
}

/**
 * The post's hashtags, edited in place: added ones as chips with ×, a field to add another, and Gemini's ideas
 * below when asked for. A hashtag typed but not yet added is kept in [pending] so posting can still include it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HashtagsField(draft: PostDraft, update: (PostDraft) -> Unit, pending: MutableState<String>, ai: PostAiAssistUi,
    enabled: Boolean) {
    var error by remember { mutableStateOf("") }
    fun setTags(tags: List<String>) = update(draft.copy(tags = tags, aiTagged = draft.aiTagged && tags.isNotEmpty()))
    fun add() {
        val clean = pending.value.trim().removePrefix("#").replace(Regex("\\s+"), "")
        if (clean.isBlank()) { pending.value = ""; return }
        if (clean.length > 60) { error = "Keep each hashtag under 60 characters."; return }
        if (draft.tags.size >= 20) { error = "Use up to 20 hashtags."; return }
        setTags((draft.tags + clean).distinctBy { it.lowercase() })
        pending.value = ""; error = ""
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Tag, null, Modifier.size(18.dp), tint = PostGold)
            Spacer(Modifier.width(6.dp))
            Text("Hashtags", style = MaterialTheme.typography.titleSmall, color = PostInk, modifier = Modifier.weight(1f))
            Text("${draft.tags.size}/20", style = MaterialTheme.typography.labelSmall, color = PostMuted)
        }
        if (draft.tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            draft.tags.forEach { tag ->
                InputChip(true, {}, label = { Text("#$tag") }, enabled = enabled,
                    trailingIcon = {
                        IconButton(onClick = { setTags(draft.tags - tag) }, Modifier.size(24.dp), enabled = enabled) {
                            Icon(Icons.Rounded.Close, "Remove $tag", Modifier.size(14.dp))
                        }
                    },
                    colors = InputChipDefaults.inputChipColors(selectedContainerColor = SpotPalette.GoldWash, selectedLabelColor = SpotPalette.GoldInk),
                    border = BorderStroke(1.dp, SpotPalette.GoldLine))
            }
        }
        OutlinedTextField(pending.value, { pending.value = it.take(61); error = "" }, Modifier.fillMaxWidth().testTag("post-tag-input"),
            placeholder = { Text("Add a hashtag") }, prefix = { Text("#") }, singleLine = true, enabled = enabled && draft.tags.size < 20,
            shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            trailingIcon = { if (pending.value.isNotBlank()) IconButton(onClick = ::add) { Icon(Icons.Rounded.Check, "Add tag", tint = PostGold) } })
        PostError(error)
        if (ai.state.showTags && ai.state.suggestions != null) AiTagIdeas(ai.state.suggestions.tags, draft, update, enabled)
    }
}

/**
 * Gemini's hashtags that aren't on the post yet: tap one to add it, or add them all. An added hashtag moves up into the
 * post's hashtags (removing it there brings it back here), and the ideas disappear once every one is added.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiTagIdeas(tags: List<String>, draft: PostDraft, update: (PostDraft) -> Unit, enabled: Boolean = true) {
    if (tags.isEmpty()) {
        Text("No hashtag ideas this time. Tap Hashtags again.", style = MaterialTheme.typography.bodySmall, color = PostMuted)
        return
    }
    val remaining = tags.filterNot { tag -> draft.tags.any { it.equals(tag, ignoreCase = true) } }
    if (remaining.isEmpty()) return
    fun add(values: List<String>) = update(draft.copy(tags = mergeTags(draft.tags, values), aiTagged = true))
    Surface(color = SpotPalette.Track, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("ai-tag-ideas")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = PostGold)
                Spacer(Modifier.width(6.dp))
                Text("AI ideas · tap to add", style = MaterialTheme.typography.labelMedium, color = PostMuted, modifier = Modifier.weight(1f))
                TextButton(onClick = { add(remaining) }, enabled = enabled && draft.tags.size < 20,
                    modifier = Modifier.testTag("ai-add-all-tags")) { Text("Add all", color = if (enabled && draft.tags.size < 20) PostGold else PostMuted) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                remaining.forEach { tag ->
                    SuggestionChip(onClick = { add(listOf(tag)) }, label = { Text("+ #$tag") },
                        enabled = enabled && draft.tags.size < 20, modifier = Modifier.testTag("ai-tag-$tag"),
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = SpotPalette.Surface, labelColor = PostInk),
                        border = BorderStroke(1.dp, PostLine))
                }
            }
        }
    }
}
