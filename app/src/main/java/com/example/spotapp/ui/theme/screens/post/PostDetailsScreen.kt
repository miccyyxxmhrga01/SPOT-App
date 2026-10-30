package com.example.spotapp.ui.theme.screens.post

import com.example.spotapp.ui.theme.components.SpotPalette
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.components.SpotAvatar
import com.example.spotapp.ui.theme.components.spotAmbientBackground
import com.example.spotapp.ui.theme.models.SpotUiProfile

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PostDetailsScreen(profile: SpotUiProfile, draft: PostDraft, update: (PostDraft) -> Unit, error: String,
    busy: Boolean, back: () -> Unit, edit: () -> Unit, save: () -> Unit, publish: () -> Unit) {
    var sheet by remember { mutableStateOf<PostSheet?>(null) }
    val focus = LocalFocusManager.current
    fun open(value: PostSheet) { if (!busy) { focus.clearFocus(); sheet = value } }
    val ai = rememberPostAiAssist(draft)
    // A hashtag typed but not yet added still goes into the draft when it's saved or posted.
    val pendingTag = rememberSaveable(draft.id) { mutableStateOf("") }
    fun withPendingTag(then: () -> Unit) {
        val clean = pendingTag.value.trim().removePrefix("#").replace(Regex("\\s+"), "")
        if (clean.isNotBlank() && clean.length <= 60 && draft.tags.size < 20 && draft.tags.none { it.equals(clean, ignoreCase = true) }) {
            update(draft.copy(tags = draft.tags + clean))
        }
        pendingTag.value = ""
        then()
    }
    val audienceIcon = when (draft.audience) { "public" -> Icons.Outlined.Public; "followers" -> Icons.Outlined.PeopleOutline; else -> Icons.Outlined.Lock }
    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.spotAmbientBackground(PostIvory).imePadding().testTag("post-details"),
        topBar = { PostTopBar("New post", back, step = 2) }, bottomBar = {
            Surface(color = SpotPalette.Surface, shadowElevation = 5.dp, border = BorderStroke(1.dp, PostLine),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { withPendingTag(save) }, enabled = !busy, border = BorderStroke(1.dp, PostLine),
                        modifier = Modifier.weight(1f).heightIn(min = 50.dp).testTag("save-draft"), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PostInk)) {
                        Icon(Icons.Outlined.Drafts, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Draft")
                    }
                    Button(onClick = { withPendingTag(publish) }, enabled = !busy && (draft.caption.isNotBlank() || draft.mediaUris.isNotEmpty()),
                        modifier = Modifier.weight(1.5f).heightIn(min = 50.dp).testTag("publish-post"), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PostGold, contentColor = SpotPalette.OnGold)) {
                        Text("Post"); Spacer(Modifier.width(12.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
                    }
                }
            }
        }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SpotAvatar(profile.profileImageUrl, "Your profile picture", Modifier.size(42.dp), uid = profile.uid)
                Column(Modifier.weight(1f)) {
                    Text(profile.displayName.ifBlank { "Your post" }, style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = PostInk)
                    if (profile.username.isNotBlank()) Text("@${profile.username.removePrefix("@")}",
                        style = MaterialTheme.typography.bodySmall, color = PostMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Surface(onClick = { open(PostSheet.VISIBILITY) }, enabled = !busy, shape = CircleShape,
                    color = SpotPalette.Surface, border = BorderStroke(1.dp, PostLine)) {
                    Row(Modifier.heightIn(min = 44.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(audienceIcon, null, Modifier.size(16.dp), tint = PostGold)
                        Text(audienceLabel(draft.audience), style = MaterialTheme.typography.labelSmall, color = PostInk)
                        Icon(Icons.Outlined.ExpandMore, "Change audience", Modifier.size(15.dp), tint = PostMuted)
                    }
                }
            }
            if (draft.mediaUris.isNotEmpty()) PostDraftMediaPreview(draft, edit)
            Surface(color = SpotPalette.Surface, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, PostLine)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Your caption", style = MaterialTheme.typography.titleSmall, color = PostInk, modifier = Modifier.weight(1f))
                        if (draft.caption.isNotEmpty()) Text("${draft.caption.length}/2,000", style = MaterialTheme.typography.labelSmall, color = PostMuted)
                    }
                    TextField(draft.caption, { update(draft.copy(caption = it.take(2000), aiCaption = draft.aiCaption && it.isNotBlank())) },
                        Modifier.fillMaxWidth().testTag("post-caption"), placeholder = { Text(if (draft.personal) "Say something about this moment…" else "Share the story behind your talent…", style = MaterialTheme.typography.bodyMedium) },
                        minLines = 3, maxLines = 7, enabled = !busy, textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent, cursorColor = PostGold))
                    AiWriteRow(ai, enabled = !busy)
                    AiCaptionIdeas(ai, draft, update, enabled = !busy)
                    HorizontalDivider(color = PostLine.copy(alpha = .6f))
                    HashtagsField(draft, update, pendingTag, ai, enabled = !busy)
                    HorizontalDivider(color = PostLine.copy(alpha = .6f))
                    AssistChip(onClick = { open(PostSheet.PEOPLE) }, enabled = !busy,
                        label = { Text("Mention${if (draft.taggedPeople.isEmpty()) "" else " · ${draft.taggedPeople.size}"}") },
                        leadingIcon = { Icon(Icons.Outlined.AlternateEmail, null, Modifier.size(17.dp), tint = PostGold) },
                        border = BorderStroke(1.dp, PostLine))
                }
            }
            Surface(color = SpotPalette.Surface, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, PostLine)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    PostTypeChoice(draft.personal, enabled = !busy) { update(draft.copy(personal = it)) }
                    HorizontalDivider(color = PostLine.copy(alpha = .6f))
                    AnimatedVisibility(!draft.personal) {
                        Column {
                            DetailsRow(Icons.Outlined.AutoAwesome, "Talent category", draft.categories.joinToString(" · ").ifBlank { "Choose at least one talent" },
                                "post-category", enabled = !busy, selected = draft.categories.isNotEmpty()) { open(PostSheet.CATEGORY) }
                            HorizontalDivider(color = PostLine.copy(alpha = .6f))
                        }
                    }
                    DetailsRow(Icons.Outlined.Place, "Location", draft.location.ifBlank { "Search for a place · optional" },
                        "post-location-row", enabled = !busy, selected = draft.location.isNotBlank()) { open(PostSheet.LOCATION) }
                    HorizontalDivider(color = PostLine.copy(alpha = .6f))
                    DetailsRow(audienceIcon, "Who can view", audienceLabel(draft.audience), "post-visibility", enabled = !busy) { open(PostSheet.VISIBILITY) }
                }
            }
            if (!draft.personal) TalentAiCard(draft, update, enabled = !busy)
            PostError(error)
        }
    }
    sheet?.let { value -> PostDetailsSheet(value, profile.uid, draft, update) { sheet = null } }
}

/** Talent posts need a category; personal posts share a moment without one. */
@Composable
private fun PostTypeChoice(personal: Boolean, enabled: Boolean, choose: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Text("What are you sharing?", style = MaterialTheme.typography.titleSmall, color = PostInk)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PostTypeOption(Icons.Outlined.AutoAwesome, "Talent", !personal, enabled, "post-type-talent", Modifier.weight(1f)) { choose(false) }
            PostTypeOption(Icons.Outlined.PersonOutline, "Personal", personal, enabled, "post-type-personal", Modifier.weight(1f)) { choose(true) }
        }
        Text(if (personal) "Share a moment without a talent category." else "Pick the talent this post shows.",
            style = MaterialTheme.typography.bodySmall, color = PostMuted, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun PostTypeOption(icon: ImageVector, label: String, selected: Boolean, enabled: Boolean, tag: String,
    modifier: Modifier = Modifier, click: () -> Unit) {
    Surface(selected = selected, onClick = click, enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).semantics { role = Role.RadioButton }.testTag(tag),
        shape = RoundedCornerShape(14.dp), color = if (selected) SpotPalette.GoldWash else SpotPalette.Surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) PostGold else PostLine)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = PostGold)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = PostInk, maxLines = 1)
        }
    }
}

@Composable
private fun DetailsRow(icon: ImageVector, title: String, value: String, tag: String, enabled: Boolean = true,
    selected: Boolean = false, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().animateContentSize().heightIn(min = 66.dp).clickable(enabled = enabled, onClick = click)
        .testTag(tag).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).background(PostIvory, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = PostGold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = PostInk)
            if (value.isNotBlank()) Text(value, style = MaterialTheme.typography.bodySmall, color = if (selected) PostGold else PostMuted,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = PostMuted)
    }
}
