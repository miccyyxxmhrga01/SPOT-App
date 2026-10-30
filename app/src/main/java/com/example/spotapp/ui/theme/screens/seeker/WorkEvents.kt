package com.example.spotapp.ui.theme.screens.seeker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.discover.*
import com.example.spotapp.data.PageCursor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun WorkEvents(profile: SpotUiProfile, onProfile: (SpotUiProfile) -> Unit,
    onSelect: ((CampusEvent) -> Unit)? = null, onChat: (String) -> Unit = {}) {
    var events by remember { mutableStateOf(emptyList<CampusEvent>()) }
    var after by remember { mutableStateOf<PageCursor?>(null) }
    var more by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var editor by rememberSaveable { mutableStateOf(false) }
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(profile.uid, page, revision) {
        loading = true; error = ""
        try {
            val result = WorkRepository.shared.myEvents(if (page == 0) null else after)
            events = (if (page == 0) result.items else events + result.items).distinctBy { it.eventId }
            after = result.cursor; more = result.hasMore
        } catch (c: CancellationException) { throw c }
        catch (_: Exception) { error = "Your events couldn't load." }
        finally { loading = false }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Button({ editor = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SeekerGold)) { Text("Create event") } }
        if (onSelect != null) item { Text("Choose an event for this invitation.", style = MaterialTheme.typography.bodyMedium) }
        if (error.isNotBlank()) item { WorkNotice(error) { revision++ } }
        if (loading) item { SpotLoading(true, SkeletonKind.ROWS) }
        if (!loading && error.isBlank() && events.none { onSelect == null || (it.startsAt > System.currentTimeMillis() && !it.cancelled) }) item {
            WorkNotice(if (onSelect == null) "Your events will appear here. Create one to get started." else "No upcoming events on this page. Create an event or load more.")
        }
        items(events.filter { onSelect == null || (it.startsAt > System.currentTimeMillis() && !it.cancelled) }, key = { it.eventId }) { event ->
            Surface(onClick = { if (onSelect != null) onSelect(event) else opened = event.eventId },
                color = SpotPalette.Surface, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, SpotPalette.Line)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(event.category, style = MaterialTheme.typography.labelSmall, color = SeekerGold)
                    Text(event.title, style = MaterialTheme.typography.titleMedium)
                    Text(workDate(event.startsAt), style = MaterialTheme.typography.bodySmall, color = SeekerMuted)
                    Text(event.location, style = MaterialTheme.typography.bodyMedium)
                    if (event.cancelled) Text("Cancelled · View event →", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    else Text(if (onSelect != null) "Select event →" else "${event.participantCount} participants · View event →", style = MaterialTheme.typography.labelMedium, color = SeekerGold)
                }
            }
        }
        if (more && !loading) item { TextButton({ page++ }) { Text("Load more events") } }
    }
    if (editor) CommunityEditor(true, profile, { editor = false }, { editor = false; page = 0; revision++ })
    opened?.let { CommunityDetails(it, true, profile, { opened = null; page = 0; revision++ }, onProfile, onChat) }
}

@Composable
internal fun EventInviteDialog(profile: SpotUiProfile, creator: SpotUiProfile, close: () -> Unit, onMessage: (String) -> Unit) {
    var event by remember { mutableStateOf<CampusEvent?>(null) }
    var details by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var sent by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (profile.accountType != AccountType.TALENT_SEEKER || creator.accountType != AccountType.STUDENT) return
    CommunityWindow(if (sent) "Invitation sent" else "Invite ${creator.displayName}", close, busy) { padding ->
        val chosen = event
        Column(Modifier.fillMaxSize().padding(padding).navigationBarsPadding()) {
            if (chosen == null) WorkEvents(profile, {}, { event = it })
            else WorkForm {
                Text(if (sent) "${creator.displayName} got your invitation as a message." else chosen.title, style = MaterialTheme.typography.titleMedium)
                Text("${workDate(chosen.startsAt)}\n${chosen.location}", style = MaterialTheme.typography.bodyMedium, color = SeekerMuted)
                if (sent) {
                    Text("You'll be notified when they answer.", style = MaterialTheme.typography.bodyMedium)
                    WorkButton("Open conversation") { close(); onMessage(creator.uid) }
                    OutlinedButton(close, Modifier.fillMaxWidth()) { Text("Done") }
                } else {
                    TextButton({ event = null }, enabled = !busy) { Text("Change event") }
                    OutlinedTextField(details, { details = it.take(2000) }, Modifier.fillMaxWidth(), label = { Text("Role and invitation details") },
                        placeholder = { Text("What will the creator do? Include timing, expectations and any compensation details.") }, minLines = 4, enabled = !busy)
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                    WorkButton(if (busy) "Sending…" else "Send invitation", !busy && details.trim().length >= 10) {
                        busy = true; error = ""
                        scope.launch { try { WorkRepository.shared.invite(chosen, creator.uid, details); sent = true }
                            catch (c: CancellationException) { throw c }
                            catch (e: Exception) { error = if (e is IllegalArgumentException || e is IllegalStateException) e.message.orEmpty() else "Couldn't send. Check your connection, event and account access, then retry." }
                            finally { busy = false } }
                    }
                    TextButton({ close(); onMessage(creator.uid) }, enabled = !busy) { Text("Discuss in a conversation first") }
                }
            }
        }
    }
}
