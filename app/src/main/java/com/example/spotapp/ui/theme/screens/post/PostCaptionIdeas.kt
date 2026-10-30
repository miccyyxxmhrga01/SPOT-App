package com.example.spotapp.ui.theme.screens.post

import androidx.compose.material.icons.rounded.AutoAwesome
import com.example.spotapp.ui.theme.components.SpotConfirmDialog
import com.example.spotapp.ui.theme.components.SpotPalette
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.spotapp.data.PostDraft

/**
 * One caption idea at a time, with "Use caption" and a next arrow. Ideas written by Gemini ([ai]) mark the post's
 * caption as AI-assisted once used. [onRefresh] asks for new ideas.
 */
@Composable
internal fun PostCaptionIdeasCard(captions: List<String>, ideasKey: String?, draft: PostDraft,
    update: (PostDraft) -> Unit, enabled: Boolean, title: String = "Caption idea", subtitle: String = "Ready to edit",
    ai: Boolean = false, tag: String = "post-caption-ideas", onRefresh: (() -> Unit)? = null) {
    var index by rememberSaveable(ideasKey) { mutableIntStateOf(0) }
    var replacing by rememberSaveable(ideasKey) { mutableStateOf<String?>(null) }
    val caption = captions[index % captions.size]
    fun apply(value: String) {
        update(draft.copy(caption = value, aiCaption = ai))
        replacing = null
    }
    Surface(color = SpotPalette.Surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, PostLine),
        modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.EditNote, null, Modifier.size(20.dp), tint = PostGold)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = PostInk)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PostMuted)
                }
                Text("${index % captions.size + 1}/${captions.size}", style = MaterialTheme.typography.labelSmall, color = PostMuted)
            }
            AnimatedContent(caption, label = "Caption idea") { text ->
                Text(text, style = MaterialTheme.typography.bodyMedium, color = PostInk, modifier = Modifier.testTag("post-caption-idea-text"))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = {
                    if (draft.caption.isNotBlank() && draft.caption != caption) replacing = caption else apply(caption)
                }, enabled = enabled && draft.caption != caption, modifier = Modifier.testTag("post-use-caption-idea")) {
                    Text(if (draft.caption == caption) "Added to caption" else "Use caption", color = if (enabled) PostGold else PostMuted)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onRefresh != null) TextButton(onClick = onRefresh, enabled = enabled) { Text("New ideas", color = PostGold) }
                    IconButton(onClick = { index = (index + 1) % captions.size }, enabled = enabled) {
                        Icon(Icons.AutoMirrored.Outlined.NavigateNext, "Next caption idea", tint = PostGold)
                    }
                }
            }
        }
    }
    replacing?.let { proposed ->
        SpotConfirmDialog(Icons.Rounded.AutoAwesome, "Replace your caption?", "Your current text will be replaced with this idea. You can edit it afterward.",
            "Replace", onDismiss = { replacing = null }, confirmEnabled = enabled, dismiss = "Keep mine",
            content = { Text(proposed, style = MaterialTheme.typography.bodyMedium, color = PostInk) }) { apply(proposed) }
    }
}
