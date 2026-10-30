package com.example.spotapp.ui.theme.screens.seeker

import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.Icons
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.discover.*
import com.example.spotapp.data.PageCursor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
internal fun WorkForm(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
@Composable
internal fun WorkButton(text: String, enabled: Boolean = true, action: () -> Unit) {
    Button(action, Modifier.fillMaxWidth(), enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = SeekerGold)) { Text(text) }
}

@Composable
internal fun WorkOpportunities(profile: SpotUiProfile, onProfile: (SpotUiProfile) -> Unit, onMessage: (String) -> Unit) {
    val seeker = profile.accountType == AccountType.TALENT_SEEKER
    var mine by rememberSaveable { mutableStateOf(seeker) }
    var rows by remember { mutableStateOf(emptyList<Opportunity>()) }
    var after by remember { mutableStateOf<PageCursor?>(null) }
    var more by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var create by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(profile.uid, mine, page, revision) {
        loading = true; error = ""
        if (page == 0) { rows = emptyList(); after = null }
        try { val result = WorkRepository.shared.opportunities(mine, if (page == 0) null else after)
            rows = (if (page == 0) result.items else rows + result.items).distinctBy { it.id }; after = result.cursor; more = result.hasMore }
        catch (c: CancellationException) { throw c }
        catch (_: Exception) { error = "Opportunities couldn't load." }
        finally { loading = false }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("work-opportunities"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + LocalSpotBottomContentPadding.current), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (seeker) item {
            WorkButton("Post a job or audition") { create = true }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(mine, { mine = true; page = 0 }, { Text("My postings") })
                FilterChip(!mine, { mine = false; page = 0 }, { Text("Explore") })
            }
        }
        if (error.isNotBlank()) item { WorkNotice(error) { revision++ } }
        if (loading) item { SpotLoading(true, SkeletonKind.ROWS) }
        if (!loading && error.isBlank() && rows.isEmpty()) item { WorkNotice(if (mine) "No postings yet. Share what you're looking for." else "No opportunities yet. Check back soon.") }
        items(rows, key = { it.id }) { item ->
            Surface(onClick = { selected = item.id }, shape = RoundedCornerShape(18.dp), color = SpotPalette.Surface, border = BorderStroke(1.dp, SpotPalette.Line)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("${if (item.kind == "audition") "AUDITION" else "JOB"} · ${item.category}", style = MaterialTheme.typography.labelSmall, color = SeekerGold)
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(item.location, style = MaterialTheme.typography.bodyMedium, color = SeekerMuted)
                    Text(if (item.isOpen()) "Apply by ${workDate(item.deadline)}" else "Applications closed", style = MaterialTheme.typography.bodySmall, color = SeekerMuted)
                }
            }
        }
        if (more && !loading) item { TextButton({ page++ }) { Text("Load more opportunities") } }
    }
    if (create) OpportunityEditor({ create = false }, { id -> create = false; mine = true; page = 0; revision++; selected = id })
    selected?.let { OpportunityDetails(it, profile, { selected = null; page = 0; revision++ }, onProfile, onMessage) }
}

@Composable
private fun OpportunityEditor(close: () -> Unit, created: (String) -> Unit) {
    val id = rememberSaveable { WorkRepository.shared.newOpportunityId() }
    var title by rememberSaveable { mutableStateOf("") }; var kind by rememberSaveable { mutableStateOf("job") }
    var description by rememberSaveable { mutableStateOf("") }; var category by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }; var deadline by rememberSaveable { mutableLongStateOf(0) }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    CommunityWindow("New opportunity", close, busy) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { WorkForm {
            Text("Give creators a clear brief.", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("job", "audition").forEach { value ->
                FilterChip(kind == value, { kind = value }, { Text(if (value == "job") "Job" else "Audition") }, enabled = !busy)
            } }
            OutlinedTextField(title, { title = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true, enabled = !busy)
            OutlinedTextField(description, { description = it.take(3000) }, Modifier.fillMaxWidth(), label = { Text("Role, expectations and arrangements") }, minLines = 4, enabled = !busy)
            Text("Include the schedule and whether this is paid or voluntary.", style = MaterialTheme.typography.bodySmall, color = SeekerMuted)
            Text("Talent category", style = MaterialTheme.typography.labelLarge)
            CommunityCategories(category, { if (!busy) category = it })
            OutlinedTextField(location, { location = it.take(160) }, Modifier.fillMaxWidth(), label = { Text("Location or Online") }, singleLine = true, enabled = !busy)
            OutlinedButton(onClick = {
                val c = Calendar.getInstance().apply { if (deadline > System.currentTimeMillis()) timeInMillis = deadline else add(Calendar.DAY_OF_MONTH, 7) }
                DatePickerDialog(context, { _, y, m, d ->
                    c.set(y, m, d)
                    TimePickerDialog(context, { _, h, minute -> c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, minute); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0); deadline = c.timeInMillis }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).apply { datePicker.minDate = System.currentTimeMillis() }.show()
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (deadline == 0L) "Choose application deadline" else workDate(deadline)) }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            WorkButton(if (busy) "Publishing…" else "Publish opportunity", !busy) {
                val draft = OpportunityDraft(title, kind, description, category, location, deadline)
                error = draft.problem().orEmpty()
                if (error.isBlank()) { busy = true; scope.launch { try { WorkRepository.shared.publish(id, draft); created(id) }
                    catch (c: CancellationException) { throw c }
                    catch (_: Exception) { error = "Couldn't publish. Check your connection and try again." }
                    finally { busy = false } } }
            }
        } }
    }
}

@Composable
internal fun OpportunityDetails(id: String, profile: SpotUiProfile, close: () -> Unit, onProfile: (SpotUiProfile) -> Unit, onMessage: (String) -> Unit) {
    var item by remember(id) { mutableStateOf<Opportunity?>(null) }
    var loaded by remember(id) { mutableStateOf(false) }; var error by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }; var busy by remember { mutableStateOf(false) }
    var application by remember(id) { mutableStateOf<TalentRequest?>(null) }
    var statusLoaded by remember(id) { mutableStateOf(false) }
    var note by rememberSaveable(id) { mutableStateOf("") }; var applicants by rememberSaveable(id) { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LifecycleDisposableEffect(id, revision) {
        error = ""
        val l = WorkRepository.shared.listenOpportunity(id, { item = it; loaded = true }, { error = it })
        val a = if (profile.accountType == AccountType.STUDENT) WorkRepository.shared.listenApplication(id, { application = it; statusLoaded = true }, { error = it }) else null
        onDispose { l.remove(); a?.remove() }
    }
    CommunityWindow("Opportunity", close, busy) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { WorkForm {
            if (error.isNotBlank()) WorkNotice(error) { revision++ }
            if (!loaded && error.isBlank()) SpotLoading(true)
            if (loaded && item == null) WorkNotice("This opportunity is no longer available.")
            item?.let { current ->
                Text("${if (current.kind == "audition") "AUDITION" else "JOB"} · ${current.category}", style = MaterialTheme.typography.labelMedium, color = SeekerGold)
                Text(current.title, style = MaterialTheme.typography.headlineSmall)
                WorkPerson(current.seekerId, onProfile)
                Text(current.description, style = MaterialTheme.typography.bodyMedium)
                Text(current.location, style = MaterialTheme.typography.bodyMedium)
                Text("Application deadline: ${workDate(current.deadline)}", style = MaterialTheme.typography.bodySmall, color = SeekerMuted)
                if (current.seekerId == profile.uid) {
                    WorkButton("Review applications") { applicants = true }
                    if (current.isOpen()) OutlinedButton({ confirmClose = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Close applications") }
                    else Text("Applications closed", style = MaterialTheme.typography.labelLarge)
                } else {
                    OutlinedButton({ onMessage(current.seekerId) }, Modifier.fillMaxWidth()) { Text("Discuss in a conversation") }
                    if (profile.accountType == AccountType.STUDENT) {
                        when {
                            application != null -> Text("Your application: ${application!!.status.replaceFirstChar { it.uppercase() }}. See updates in Notifications → Applications.", style = MaterialTheme.typography.bodyMedium, color = SeekerGold)
                            !current.isOpen() -> Text("Applications closed", style = MaterialTheme.typography.labelLarge)
                            !statusLoaded -> SpotLoading(true)
                            else -> {
                                OutlinedTextField(note, { note = it.take(2000) }, Modifier.fillMaxWidth(), label = { Text("Introduce yourself") }, placeholder = { Text("Share your experience, availability and why you're interested.") }, minLines = 3, enabled = !busy)
                                WorkButton(if (busy) "Applying…" else "Send application", !busy && note.trim().length >= 10) {
                                    busy = true; error = ""; scope.launch {
                                        try { WorkRepository.shared.apply(current, note) }
                                        catch (c: CancellationException) { throw c }
                                        catch (_: Exception) { error = "Couldn't apply. Check the deadline, your access and connection, then retry." }
                                        finally { busy = false }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } }
    }
    if (applicants) CommunityWindow("Applications", { applicants = false }) { padding -> Box(Modifier.fillMaxSize().padding(padding).navigationBarsPadding()) {
        WorkRequests(profile, id, onProfile, onMessage)
    } }
    if (confirmClose) SpotConfirmDialog(Icons.Rounded.DoNotDisturbOn, "Close applications?",
        "Creators won't be able to apply or be accepted after this. Existing requests remain available.", "Close",
        onDismiss = { confirmClose = false }, danger = true, dismiss = "Keep open") {
        confirmClose = false; busy = true; scope.launch { try { WorkRepository.shared.close(id) }
            catch (c: CancellationException) { throw c }
            catch (_: Exception) { error = "Couldn't close applications. Please retry." }
            finally { busy = false } }
    }
}
