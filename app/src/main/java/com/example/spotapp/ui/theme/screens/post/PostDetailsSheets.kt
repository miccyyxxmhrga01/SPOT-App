package com.example.spotapp.ui.theme.screens.post

import com.example.spotapp.ui.theme.components.SpotPalette
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.spotapp.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

// Quick picks open as sheets; hashtags and AI ideas are edited on the page itself.
internal enum class PostSheet { CATEGORY, PEOPLE, LOCATION, VISIBILITY }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PostDetailsSheet(sheet: PostSheet, uid: String, draft: PostDraft, update: (PostDraft) -> Unit, close: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = close, sheetState = state, containerColor = PostIvory) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(when (sheet) { PostSheet.CATEGORY -> "Talent categories"; PostSheet.PEOPLE -> "Mention people"; PostSheet.LOCATION -> "Location"; PostSheet.VISIBILITY -> "Who can view this post?" }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = close) { Text("Done", color = PostGold) }
            }
            when (sheet) {
                PostSheet.CATEGORY -> Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
                    Text("${draft.categories.size} / 8 selected", style = MaterialTheme.typography.bodySmall, color = PostMuted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (SpotTalentCategories + draft.categories).distinct().forEach { category ->
                            FilterChip(category in draft.categories, onClick = { update(draft.copy(categories = if (category in draft.categories) draft.categories - category else (draft.categories + category).take(8))) },
                                label = { Text(category) }, enabled = category in draft.categories || draft.categories.size < 8,
                                colors = FilterChipDefaults.filterChipColors(containerColor = SpotPalette.Surface, selectedContainerColor = SpotPalette.GoldWash, selectedLabelColor = PostGold), border = BorderStroke(1.dp, PostLine))
                        }
                    }
                }
                PostSheet.PEOPLE -> PeopleEditor(uid, draft.taggedPeople) { update(draft.copy(taggedPeople = it)) }
                PostSheet.LOCATION -> PostLocationEditor(draft.location, { update(draft.copy(location = it)) }, close)
                PostSheet.VISIBILITY -> listOf("public" to "Anyone on SPOT", "followers" to "Your followers", "private" to "Only you").forEach { (value, description) ->
                    Row(Modifier.fillMaxWidth().clickable { update(draft.copy(audience = value)) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(draft.audience == value, { update(draft.copy(audience = value)) }, colors = RadioButtonDefaults.colors(selectedColor = PostGold))
                        Column { Text(audienceLabel(value), style = MaterialTheme.typography.bodyMedium); Text(description, style = MaterialTheme.typography.bodySmall, color = PostMuted) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeopleEditor(uid: String, selected: List<String>, update: (List<String>) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var people by remember { mutableStateOf(emptyList<ChatPerson>()) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val directory = remember(uid) { SupabaseMessagesStore(uid) }
    LaunchedEffect(query) {
        loading = true; error = ""; delay(250)
        try { people = directory.people(query) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "People couldn't load. Try another search." }
        finally { loading = false }
    }
    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search name or username") }, singleLine = true, shape = RoundedCornerShape(14.dp))
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = PostGold)
    PostError(error)
    LazyColumn(Modifier.heightIn(max = 300.dp)) {
        if (!loading && people.isEmpty() && error.isBlank()) item { Text("No people found", Modifier.padding(vertical = 20.dp), style = MaterialTheme.typography.bodySmall, color = PostMuted) }
        items(people, key = { it.uid }) { person ->
            Row(Modifier.fillMaxWidth().clickable(enabled = person.uid in selected || selected.size < 20) { update(if (person.uid in selected) selected - person.uid else selected + person.uid) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(person.uid in selected, { checked -> if (!checked || selected.size < 20) update(if (checked) (selected + person.uid).distinct() else selected - person.uid) }, colors = CheckboxDefaults.colors(checkedColor = PostGold))
                Column { Text(person.name, style = MaterialTheme.typography.bodyMedium); Text("@${person.username}", style = MaterialTheme.typography.bodySmall, color = PostMuted) }
            }
        }
    }
    TextButton(onClick = { update(emptyList()) }, enabled = selected.isNotEmpty()) { Text("Clear mentions · ${selected.size}/20", color = PostGold) }
}
