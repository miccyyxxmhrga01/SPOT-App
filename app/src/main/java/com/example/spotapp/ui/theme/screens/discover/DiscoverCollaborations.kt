package com.example.spotapp.ui.theme.screens.discover

import androidx.compose.material.icons.rounded.DeleteOutline
import com.example.spotapp.ui.theme.components.SpotConfirmDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.spotapp.data.ChatPerson
import com.example.spotapp.data.SpotTalentCategories
import com.example.spotapp.data.SupabaseMessagesStore
import com.example.spotapp.data.collaboration.CollabPreferences
import com.example.spotapp.data.collaboration.CollaborationCreator
import com.example.spotapp.data.collaboration.CollaborationViewModel
import com.example.spotapp.data.messagesError
import com.example.spotapp.ui.theme.components.LocalSpotBottomContentPadding
import com.example.spotapp.ui.theme.components.SpotAuthFieldShape
import com.example.spotapp.ui.theme.components.SpotAvatar
import com.example.spotapp.ui.theme.components.SpotButtonStyle
import com.example.spotapp.ui.theme.components.SpotEmptyState
import com.example.spotapp.ui.theme.components.SpotHeaderAction
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.components.SpotPillButton
import com.example.spotapp.ui.theme.components.spotAuthFieldColors
import com.example.spotapp.ui.theme.models.SpotUiProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * Suggested Collaborators: creators matched on relevant or complementary talents, shared interests, location and
 * collaboration preferences, each with a short reason. [onMessage] starts a conversation; an invitation to
 * collaborate is sent through Messages and [onChat] opens it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DiscoverCollaborations(profile: SpotUiProfile, onProfile: (SpotUiProfile) -> Unit, onMessage: (String) -> Unit,
    onChat: (String) -> Unit = {}) {
    val model: CollaborationViewModel = viewModel(key = "collaborators-${profile.uid}")
    val state by model.uiState.collectAsStateWithLifecycle()
    var confirmRemove by rememberSaveable { mutableStateOf<String?>(null) }
    var editingPreferences by rememberSaveable { mutableStateOf(false) }
    var inviting by remember { mutableStateOf<CollaborationCreator?>(null) }
    LifecycleResumeEffect(model) {
        model.loadSuggestions()
        onPauseOrDispose { }
    }
    // The undo bar for a dismissed suggestion stays a few seconds.
    LaunchedEffect(state.dismissed) { if (state.dismissed != null) { delay(6_000); model.clearDismissed() } }
    val noPreferences = state.preferences.location.isBlank() && state.preferences.collabTalents.isEmpty()
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().testTag("collaboration-suggestions"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp + LocalSpotBottomContentPadding.current),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            // The title wraps instead of truncating at large text sizes.
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Suggested Collaborators", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = SpotPalette.Ink, maxLines = 2, modifier = Modifier.weight(1f))
                SpotHeaderAction(Icons.Outlined.Tune, "Collaboration preferences", { editingPreferences = true }, enabled = !state.loading)
                SpotHeaderAction(Icons.Outlined.Refresh, "Refresh collaborators", model::loadSuggestions, enabled = !state.loading)
            }
        }
        if (state.loading) item {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFFD1AD32), trackColor = SpotPalette.GoldSoft)
        }
        state.error?.let { error -> item {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = model::loadSuggestions) { Text("Try again") }
            }
        } }
        state.actionError?.let { error -> item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = model::clearActionError) { Text("OK") }
            }
        } }
        if (!state.loading && state.error == null && noPreferences) item {
            Surface(color = SpotPalette.GoldWash, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, SpotPalette.GoldLine)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Get better matches", style = MaterialTheme.typography.titleSmall, color = SpotPalette.Ink)
                    Text("Add where you're based and the talents you'd like to work with.", style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted)
                    SpotPillButton("Set preferences", { editingPreferences = true }, icon = Icons.Outlined.Tune, style = SpotButtonStyle.Tonal, compact = true)
                }
            }
        }
        if (state.myTalents.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().testTag("my-collab-talents"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Your collab talents", style = MaterialTheme.typography.labelLarge, color = SpotPalette.Ink)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.myTalents.forEach { talent ->
                        InputChip(selected = false, onClick = { confirmRemove = talent }, label = { Text(talent) },
                            enabled = state.removing == null && !state.loading, modifier = Modifier.testTag("my-talent-$talent"),
                            trailingIcon = {
                                if (state.removing == talent) CircularProgressIndicator(Modifier.size(14.dp), color = SpotPalette.GoldInk, strokeWidth = 2.dp)
                                else Icon(Icons.Rounded.Close, "Remove $talent", Modifier.size(16.dp))
                            },
                            colors = InputChipDefaults.inputChipColors(containerColor = SpotPalette.GoldWash, labelColor = SpotPalette.GoldInk,
                                trailingIconColor = SpotPalette.GoldInk),
                            border = BorderStroke(1.dp, SpotPalette.GoldLine))
                    }
                }
                state.talentError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
        if (!state.loading && state.error == null && state.creators.isEmpty()) item {
            SpotEmptyState(Icons.Outlined.Handshake, "No suggestions yet",
                message = "Creators appear here once they add talents to their profile or confirm them with SPOT AI.")
        }
        items(state.creators, key = { it.profile.uid }) { creator ->
            SuggestionCard(creator, Modifier.animateItem(), onProfile = { onProfile(creator.profile) },
                onInvite = { inviting = creator }, onMessage = { onMessage(creator.profile.uid) }, onDismiss = { model.dismiss(creator) })
        }
    }
    AnimatedVisibility(state.dismissed != null, Modifier.align(Alignment.BottomCenter)
        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp + LocalSpotBottomContentPadding.current),
        enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
        Surface(color = SpotPalette.Ink, shape = RoundedCornerShape(16.dp), shadowElevation = 4.dp) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Suggestion hidden", color = SpotPalette.OnInk, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(model::undoDismiss) { Text("Undo", color = SpotPalette.GoldOnInk) }
            }
        }
    }
    }
    confirmRemove?.let { talent ->
        SpotConfirmDialog(Icons.Rounded.DeleteOutline, "Remove $talent?", "Creators won't find you for $talent. You can add it again from SPOT AI.",
            "Remove", onDismiss = { confirmRemove = null }, danger = true, dismiss = "Keep") { confirmRemove = null; model.removeTalent(talent) }
    }
    if (editingPreferences) CollabPreferencesPage(state.preferences, state.savingPreferences, state.actionError,
        onSave = { model.savePreferences(it) { editingPreferences = false } }, onDismiss = { editingPreferences = false; model.clearActionError() })
    inviting?.let { creator ->
        CollabInvitePage(profile, creator, onDismiss = { inviting = null }, onSent = { chatId -> inviting = null; onChat(chatId) })
    }
}

@Composable
private fun SuggestionCard(creator: CollaborationCreator, modifier: Modifier, onProfile: () -> Unit, onInvite: () -> Unit,
    onMessage: () -> Unit, onDismiss: () -> Unit) {
    val person = creator.profile
    Surface(modifier.fillMaxWidth().animateContentSize().testTag("collaborator-${person.uid}"),
        color = SpotPalette.Surface, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, SpotPalette.Line), shadowElevation = 1.dp) {
        Column(Modifier.padding(start = 14.dp, top = 14.dp, end = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Row(Modifier.weight(1f).clickable(onClickLabel = "View profile", onClick = onProfile), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SpotAvatar(person.profileImageUrl, "View ${person.displayName}'s profile", Modifier.size(52.dp), uid = person.uid)
                    Column {
                        Text(person.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (person.username.isNotBlank()) Text("@${person.username.removePrefix("@")}", style = MaterialTheme.typography.labelMedium,
                            color = SpotPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // The first three talents as chips, then how many more, instead of a long wrapping list.
                        if (creator.talents.isNotEmpty()) FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            creator.talents.take(3).forEach { TalentPill(it) }
                            if (creator.talents.size > 3) TalentPill("+${creator.talents.size - 3}")
                        }
                    }
                }
                IconButton(onDismiss, Modifier.testTag("dismiss-${person.uid}")) { Icon(Icons.Rounded.Close, "Hide suggestion", tint = SpotPalette.Muted) }
            }
            if (creator.reason.isNotBlank()) Row(Modifier.padding(end = 10.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.padding(top = 2.dp).size(16.dp), tint = SpotPalette.GoldDeep)
                Text(creator.reason, style = MaterialTheme.typography.bodySmall, color = SpotPalette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp).testTag("reason-${person.uid}"))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SpotPillButton("Invite", onInvite, icon = Icons.Rounded.GroupAdd, compact = true)
                SpotPillButton("Message", onMessage, icon = Icons.Outlined.ChatBubbleOutline, style = SpotButtonStyle.Secondary, compact = true)
            }
        }
    }
}

/** Where you're based, the talents you'd like to work with, and whether others see you in their suggestions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollabPreferencesPage(current: CollabPreferences, saving: Boolean, error: String?, onSave: (CollabPreferences) -> Unit,
    onDismiss: () -> Unit) {
    var location by rememberSaveable { mutableStateOf(current.location) }
    var talents by rememberSaveable { mutableStateOf(current.collabTalents) }
    var open by rememberSaveable { mutableStateOf(current.openToCollab) }
    CommunityWindow("Collab preferences", onDismiss, saving) { padding ->
        CommunityFormBody(padding, "Save", enabled = !saving, busy = saving,
            onAction = { onSave(CollabPreferences(location.trim(), talents, open)) }) {
            OutlinedTextField(location, { location = it.take(80) }, Modifier.fillMaxWidth().testTag("collab-location"),
                label = { Text("Where you're based") }, placeholder = { Text("City or campus") }, singleLine = true, enabled = !saving,
                shape = SpotAuthFieldShape, colors = spotAuthFieldColors())
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Talents you'd like to work with", style = MaterialTheme.typography.titleSmall, color = SpotPalette.Ink)
                Text("Pick up to 8.", style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (SpotTalentCategories + talents).distinct().forEach { talent ->
                    val selected = talent in talents
                    FilterChip(selected, { talents = if (selected) talents - talent else (talents + talent).take(8) },
                        label = { Text(talent) }, enabled = !saving && (selected || talents.size < 8),
                        leadingIcon = if (selected) { { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) } } else null,
                        colors = communityChipColors(), shape = CircleShape)
                }
            }
            Row(Modifier.fillMaxWidth().toggleable(open, !saving, Role.Switch) { open = it }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Open to collaborations", style = MaterialTheme.typography.titleSmall, color = SpotPalette.Ink)
                    Text("When off, you aren't suggested to others.", style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted)
                }
                Switch(open, onCheckedChange = null, enabled = !saving)
            }
            if (error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = SpotPalette.ErrorText)
        }
    }
}

/**
 * An invitation to collaborate, sent through Messages: it arrives under the creator's Requests, where they can accept,
 * decline or message you about it. [onSent] gets the conversation id.
 */
@Composable
internal fun CollabInvitePage(profile: SpotUiProfile, creator: CollaborationCreator, onDismiss: () -> Unit, onSent: (String) -> Unit) {
    val person = creator.profile
    val first = person.displayName.substringBefore(' ').ifBlank { "them" }
    val id = rememberSaveable(person.uid) { UUID.randomUUID().toString() }
    var title by rememberSaveable(person.uid) {
        mutableStateOf(listOfNotNull(profile.talentCategories.firstOrNull(), creator.talents.firstOrNull()).distinct()
            .joinToString(" × ").ifBlank { "Campus" }.plus(" collab").take(60))
    }
    var note by rememberSaveable(person.uid) {
        // Sent from a profile there's no suggestion to mention.
        mutableStateOf((if (creator.reason.isBlank()) "Hi $first! Want to create something together?"
            else "Hi $first! SPOT suggested we collaborate (${creator.reason}). Want to create something together?").take(500))
    }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun send() {
        sending = true; error = ""
        scope.launch {
            try {
                val chat = withTimeout(45_000) {
                    SupabaseMessagesStore(profile.uid).create(listOf(ChatPerson(person.uid, person.displayName, person.username, person.profileImageUrl)),
                        title, note, group = true, id = id)
                }
                onSent(chat)
            } catch (_: TimeoutCancellationException) { error = "Couldn't connect. Retry; the same invitation won't be sent twice." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = messagesError(e) }
            finally { sending = false }
        }
    }
    CommunityWindow("Invite $first", onDismiss, sending) { padding ->
        CommunityFormBody(padding, "Send invitation", enabled = !sending && title.isNotBlank(), busy = sending, onAction = ::send) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SpotAvatar(person.profileImageUrl, person.displayName, Modifier.size(44.dp), uid = person.uid)
                Column {
                    Text(person.displayName, style = MaterialTheme.typography.titleSmall, color = SpotPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (creator.talents.isNotEmpty()) Text(creator.talents.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                        color = SpotPalette.GoldInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            OutlinedTextField(title, { title = it.take(60) }, Modifier.fillMaxWidth().testTag("collab-invite-title"),
                label = { Text("Collaboration name") }, singleLine = true, enabled = !sending,
                shape = SpotAuthFieldShape, colors = spotAuthFieldColors())
            OutlinedTextField(note, { note = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("Message") },
                minLines = 4, maxLines = 8, enabled = !sending, shape = SpotAuthFieldShape, colors = spotAuthFieldColors())
            Text("It arrives in their Messages under Requests. They can accept, decline or message you first.",
                style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted)
            if (error.isNotBlank()) Text(error, style = MaterialTheme.typography.bodySmall, color = SpotPalette.ErrorText)
        }
    }
}

/** One of a suggested Creator's talents, as a small gold chip. */
@Composable
private fun TalentPill(label: String) {
    Text(label, Modifier.background(SpotPalette.GoldWash, CircleShape).border(1.dp, SpotPalette.GoldLine, CircleShape)
        .padding(horizontal = 9.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = SpotPalette.GoldInk, maxLines = 1)
}
