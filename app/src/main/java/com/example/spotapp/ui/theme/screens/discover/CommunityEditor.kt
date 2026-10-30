package com.example.spotapp.ui.theme.screens.discover

import com.example.spotapp.ui.theme.components.SpotConfirmDialog
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.components.SpotAuthButton
import com.example.spotapp.ui.theme.components.SpotButtonStyle
import com.example.spotapp.ui.theme.components.SpotPillButton
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import kotlinx.coroutines.*
import java.util.UUID

/**
 * Creating a group, or an event ([isEvent]) through [EventEditor]; [existing] edits an event before it starts, and
 * [existingGroup] edits a group its owner runs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommunityEditor(isEvent: Boolean, profile: SpotUiProfile, close: () -> Unit, created: (String) -> Unit,
    existing: CampusEvent? = null, existingGroup: CampusGroup? = null) {
    if (isEvent) { EventEditor(profile, close, created, existing); return }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val id = rememberSaveable { existingGroup?.groupId ?: UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(existingGroup?.name.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(existingGroup?.description.orEmpty()) }
    var image by rememberSaveable { mutableStateOf(existingGroup?.imageUrl.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(existingGroup?.category.orEmpty()) }
    var privacy by rememberSaveable { mutableStateOf(existingGroup?.privacy ?: "public") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf("") }
    var discard by remember { mutableStateOf(false) }
    val changed = if (existingGroup == null) name.isNotBlank() || description.isNotBlank() || image.isNotBlank()
        else name != existingGroup.name || description != existingGroup.description || image != existingGroup.imageUrl ||
            category != existingGroup.category || privacy != existingGroup.privacy
    fun back() { if (changed) discard = true else close() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; image = uri.toString() }
    }
    var talentSheet by remember { mutableStateOf(false) }
    fun save() {
        if (busy) return
        val g = GroupDraft(id, name, description, image, category, privacy)
        val issue = g.error()
        if (issue != null) { error = issue; return }
        focus.clearFocus(); busy = true; error = ""; progress = 0f
        scope.launch {
            try {
                val savedId = withTimeout(180_000) {
                    if (existingGroup != null) {
                        DiscoverRepository.shared.updateGroup(context.applicationContext, existingGroup, g) { progress = it }
                        existingGroup.groupId
                    } else DiscoverRepository.shared.createGroup(context.applicationContext, g) { progress = it }
                }
                created(savedId)
            } catch (_: TimeoutCancellationException) { error = "Saving took too long. Please retry; the same draft won't be created twice." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = if (e is IllegalStateException || e is IllegalArgumentException) e.message.orEmpty() else "Couldn't save. Check your connection and retry." }
            finally { busy = false }
        }
    }
    // Laid out like the event editor: a live cover preview, labelled fields, who can join as two cards, and the
    // main action pinned above the keyboard.
    EventWindow(::back, busy) {
        Column(Modifier.fillMaxSize().imePadding().testTag("group-editor")) {
            EventPageHeader(if (existingGroup != null) "Edit group" else "New group", ::back, close = existingGroup == null, enabled = !busy)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                GroupCoverField(image, name, enabled = !busy,
                    pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, remove = { image = "" })
                FormField(name, { name = it.take(80); error = "" }, "Group name", "e.g. Campus Acoustic Club", enabled = !busy,
                    counter = "${name.length} / 80", modifier = Modifier.testTag("group-name"))
                Column {
                    PickerField("Talent", category, "Choose a talent", null, Icons.Rounded.ExpandMore, changed = false, enabled = !busy,
                        modifier = Modifier.testTag("group-talent")) { talentSheet = true }
                    Text("Creators with this talent find it first.", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium,
                        color = SpotPalette.Muted)
                }
                FormField(description, { description = it.take(2000); error = "" }, "About", "What's the group for, and who should join?",
                    enabled = !busy, singleLine = false, counter = "${description.length} / 2,000", modifier = Modifier.testTag("group-about"))
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FormLabel("Who can join")
                    FeeOption(privacy == "public", "Public", "Anyone on SPOT can join and see who's in.", !busy,
                        Modifier.testTag("group-public"), onSelect = { privacy = "public" })
                    FeeOption(privacy == "private", "Private", "People ask to join and you approve them. Only members see who's in.", !busy,
                        Modifier.testTag("group-private"), onSelect = { privacy = "private" })
                }
            }
            EventPinnedBar {
                CommunityError(error)
                if (busy) Text(if (progress in 0.01f..0.9f) "Uploading cover · ${(progress * 100).toInt()}%" else "Saving…",
                    style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted)
                Box(Modifier.testTag("group-save")) {
                    SpotAuthButton(if (existingGroup != null) "Save changes" else "Create group", loading = busy,
                        enabled = !busy && (existingGroup == null || changed), onClick = ::save)
                }
                if (existingGroup == null) Text("You'll be its owner and admin.", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium,
                    color = SpotPalette.Muted, textAlign = TextAlign.Center)
            }
        }
    }
    if (talentSheet) TalentPickSheet(category, { category = it; talentSheet = false }, { talentSheet = false })
    if (discard) SpotConfirmDialog(Icons.Rounded.DeleteOutline, if (existingGroup != null) "Discard your changes?" else "Discard this group?",
        "Your unsaved changes will be lost.",
        "Discard", onDismiss = { discard = false }, danger = true, dismiss = "Keep editing", onConfirm = close)
}

/** The cover as it will look: the drawn cover with the group's initials until a photo is picked, with Add, Change and Remove on it. */
@Composable
private fun GroupCoverField(image: String, name: String, enabled: Boolean, pick: () -> Unit, remove: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(20.dp)).testTag("group-cover")) {
        CommunityCover(image, false, Modifier.fillMaxSize(), title = name)
        Row(Modifier.align(Alignment.BottomEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (image.isBlank()) SpotPillButton("Add cover photo", pick, enabled = enabled, style = SpotButtonStyle.Secondary, compact = true,
                icon = Icons.Rounded.AddPhotoAlternate)
            else {
                SpotPillButton("Change", pick, enabled = enabled, style = SpotButtonStyle.Secondary, compact = true)
                SpotPillButton("Remove", remove, enabled = enabled, style = SpotButtonStyle.Secondary, compact = true)
            }
        }
    }
}
