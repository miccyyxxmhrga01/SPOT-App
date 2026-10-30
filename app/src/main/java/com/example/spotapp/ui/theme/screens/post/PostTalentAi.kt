package com.example.spotapp.ui.theme.screens.post

import com.example.spotapp.ui.theme.components.SpotPalette
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.spotapp.data.PostDraft
import com.example.spotapp.data.PostMediaEdit
import com.example.spotapp.data.ai.composerCategory
import kotlin.math.roundToInt

/** Suggests talent categories for the post's first photo/video automatically. Nothing is applied until the user taps a suggestion. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TalentAiCard(draft: PostDraft, update: (PostDraft) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val cover = draft.mediaUris.firstOrNull() ?: return
    val context = LocalContext.current
    val ai: TalentAiViewModel = viewModel(key = "talent-ai-${draft.id}")
    val state by ai.uiState.collectAsStateWithLifecycle()
    val edit = draft.mediaEdits[cover] ?: PostMediaEdit()
    val coverKey = TalentAiViewModel.mediaKey(cover, edit)
    // Checks the cover as soon as it's shown or changed; each cover + edit is only sent once.
    LaunchedEffect(coverKey, enabled) { if (enabled) ai.analyzeIfNeeded(context, cover, edit) }
    // A result for a different cover or edit is stale; show the idle state instead.
    val current = state.takeIf { it.mediaKey == coverKey } ?: TalentAiUiState()
    val prediction = current.prediction
    val suggestions = prediction?.suggestions.orEmpty()
        .mapNotNull { suggestion -> suggestion.composerCategory()?.let { it to suggestion.confidencePercent } }
        .distinctBy { it.first }
    val selectedSuggestions = suggestions.map { it.first }.filter { it in draft.categories }
    val talentsSaved = selectedSuggestions.isNotEmpty() && selectedSuggestions.all { it in current.savedCollaborationCategories }
    fun analyze() = ai.analyze(context, cover, edit)
    fun toggle(category: String) {
        val added = category !in draft.categories
        val categories = if (added) (draft.categories + category).take(8) else draft.categories - category
        update(draft.copy(categories = categories,
            aiCategorySuggested = added || (draft.aiCategorySuggested && categories.any { chosen -> suggestions.any { it.first == chosen } })))
    }
    Surface(color = SpotPalette.Track, shape = RoundedCornerShape(18.dp), modifier = modifier.fillMaxWidth().testTag("talent-ai")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp), tint = PostGold)
                Spacer(Modifier.width(8.dp))
                Text("SPOT AI", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (prediction != null || current.error != null) TextButton(onClick = ::analyze, enabled = enabled && !current.savingCollaborationTalents) { Text("Try again", color = PostGold) }
            }
            when {
                // Before the first check starts (one frame), show it as loading rather than an empty card.
                current.isLoading || (enabled && prediction == null && current.error == null) -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth().testTag("talent-ai-progress"), color = PostGold, trackColor = PostLine)
                    Text("Looking at your media…", style = MaterialTheme.typography.bodySmall, color = PostMuted)
                }
                current.error != null -> PostError(current.error)
                prediction != null && suggestions.isEmpty() ->
                    Text("No supported talent category was suggested. You can select one yourself.", style = MaterialTheme.typography.bodySmall, color = PostMuted)
                prediction != null -> {
                    Text(if (prediction.recognizedTalent) "Suggested from your media. Tap to add or remove." else "SPOT AI isn't sure. Only add a suggestion that fits.",
                        style = MaterialTheme.typography.bodySmall, color = PostMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { (category, confidence) ->
                            val selected = category in draft.categories
                            FilterChip(selected, onClick = { toggle(category) }, label = { Text("$category · ${confidence.roundToInt()}%") },
                                enabled = enabled && !current.savingCollaborationTalents && (selected || draft.categories.size < 8), modifier = Modifier.testTag("talent-ai-$category"),
                                leadingIcon = if (selected) { { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) } } else null,
                                colors = FilterChipDefaults.filterChipColors(containerColor = SpotPalette.Surface, selectedContainerColor = SpotPalette.GoldWash, selectedLabelColor = PostGold),
                                border = BorderStroke(1.dp, PostLine))
                        }
                    }
                    if (selectedSuggestions.isNotEmpty()) {
                        Text("Use your selected talents to help other creators find you for collaborations.", style = MaterialTheme.typography.bodySmall, color = PostMuted)
                        OutlinedButton(onClick = { ai.saveCollaborationTalents(coverKey, selectedSuggestions) },
                            enabled = enabled && !current.savingCollaborationTalents && !talentsSaved,
                            modifier = Modifier.testTag("save-collaboration-talents")) {
                            Text(when { current.savingCollaborationTalents -> "Saving…"; talentsSaved -> "Added to collaborations"; else -> "Use selected talents for collabs" })
                        }
                        current.collaborationError?.let { PostError(it) }
                    }
                }
                else -> Text("SPOT AI suggests a talent from your first photo or video.", style = MaterialTheme.typography.bodySmall, color = PostMuted)
            }
        }
    }
}
