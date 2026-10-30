package com.example.spotapp.ui.theme.screens.seeker

import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.discover.*
import com.example.spotapp.data.PageCursor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun WorkPerson(uid: String, onProfile: (SpotUiProfile) -> Unit) {
    var person by remember(uid) { mutableStateOf<SpotUiProfile?>(null) }
    var error by remember(uid) { mutableStateOf(false) }
    var retry by remember(uid) { mutableIntStateOf(0) }
    LaunchedEffect(uid, retry) {
        error = false
        try { person = ProfileRepository.shared.loadProfiles(listOf(uid)).firstOrNull()?.asProfileUi(); error = person == null }
        catch (c: CancellationException) { throw c }
        catch (_: Exception) { error = true }
    }
    val p = person
    if (p != null) Row(Modifier.fillMaxWidth().clickable { onProfile(p) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        SpotAvatar(p.profileImageUrl, p.displayName, Modifier.size(40.dp), p.uid)
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(p.displayName, style = MaterialTheme.typography.titleSmall)
            Text("@${p.username}", style = MaterialTheme.typography.bodySmall, color = SeekerMuted)
        }
        Text("Profile →", style = MaterialTheme.typography.labelSmall, color = SeekerGold)
    } else if (error) TextButton({ retry++ }) { Text("Profile unavailable · Retry") }
    else SpotLoading(true, SkeletonKind.ROWS)
}

@Composable
internal fun WorkRequests(profile: SpotUiProfile, opportunityId: String? = null,
    onProfile: (SpotUiProfile) -> Unit, onMessage: (String) -> Unit, category: String? = null, onChat: (String) -> Unit = {}) {
    var kind by rememberSaveable(profile.uid, opportunityId, category) { mutableStateOf(category ?: "all") }
    val queryKind = kind.takeUnless { it == "all" }
    var rows by remember(profile.uid, profile.accountType, opportunityId, queryKind) { mutableStateOf(emptyList<TalentRequest>()) }
    var cursor by remember(profile.uid, profile.accountType, opportunityId, queryKind) { mutableStateOf<PageCursor?>(null) }
    var after by remember(profile.uid, profile.accountType, opportunityId, queryKind) { mutableStateOf<PageCursor?>(null) }
    var more by remember(profile.uid, profile.accountType, opportunityId, queryKind) { mutableStateOf(false) }; var loaded by remember(profile.uid, profile.accountType, opportunityId, queryKind) { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }; var retry by remember { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    LifecycleDisposableEffect(profile.uid, profile.accountType, opportunityId, queryKind, after, retry) {
        loaded = false; error = ""
        val l = WorkRepository.shared.listenRequests(profile.accountType == AccountType.TALENT_SEEKER, after, opportunityId,
            { rows = it.items; cursor = it.cursor; more = it.hasMore; loaded = true }, { error = it }, kind = queryKind)
        onDispose { l.remove() }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("work-requests"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + LocalSpotBottomContentPadding.current), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (opportunityId == null && category == null) item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("all" to "All", "invitation" to "Invitations", "application" to "Applications").forEach { (value, label) ->
                    FilterChip(kind == value, { kind = value }, { Text(label, style = MaterialTheme.typography.labelSmall) })
                }
            }
        }
        if (error.isNotBlank()) item { WorkNotice(error) { retry++ } }
        if (!loaded && error.isBlank()) item { SpotLoading(true, SkeletonKind.ROWS) }
        val visible = rows
        if (loaded && visible.isEmpty()) item { WorkNotice(if (after == null) when (queryKind) { "invitation" -> "No event invitations yet."; "application" -> "No applications yet."; else -> "No requests yet." } else "No matching requests on this page.") }
        items(visible, key = { it.id }) { request ->
            Surface(onClick = { selected = request.id }, modifier = Modifier.animateItem(), shape = RoundedCornerShape(18.dp), color = SpotPalette.Surface, border = BorderStroke(1.dp, SpotPalette.Line)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (request.kind == "invitation") "EVENT INVITATION" else "APPLICATION", style = MaterialTheme.typography.labelSmall, color = SeekerGold)
                    Text(request.title, style = MaterialTheme.typography.titleMedium)
                    Text(request.status.replaceFirstChar { it.uppercase() } + if (request.canRespond(profile.uid)) " · Your response needed" else "", style = MaterialTheme.typography.labelMedium, color = SeekerMuted)
                    Text("View request →", style = MaterialTheme.typography.labelMedium, color = SeekerGold)
                }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (after != null) TextButton({ after = null }) { Text("Back to latest") }
            if (more && loaded) TextButton({ after = cursor }) { Text("Older requests") }
        } }
    }
    rows.firstOrNull { it.id == selected }?.let { RequestDetails(it, profile, { selected = null }, onProfile, onMessage, onChat) }
}

/** A collaboration invitation or job application, with its answer and a way to talk it over. Also opened from Messages. */
@Composable
internal fun RequestDetails(item: TalentRequest, profile: SpotUiProfile, close: () -> Unit,
    onProfile: (SpotUiProfile) -> Unit, onMessage: (String) -> Unit, onChat: (String) -> Unit) {
    var event by remember(item.id) { mutableStateOf<CampusEvent?>(null) }
    var opportunity by remember(item.id) { mutableStateOf<Opportunity?>(null) }
    var sourceError by remember(item.id) { mutableStateOf("") }
    var sourceLoaded by remember(item.id) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var showSource by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(item.sourceId, retry) {
        if (item.kind == "invitation") { sourceError = ""; try { event = WorkRepository.shared.event(item.sourceId); sourceLoaded = true }
            catch (c: CancellationException) { throw c }
            catch (_: Exception) { sourceError = "Event details couldn't load." } }
    }
    LifecycleDisposableEffect(item.id, retry) {
        val l = if (item.kind == "application") WorkRepository.shared.listenOpportunity(item.sourceId,
            { opportunity = it; sourceLoaded = true; sourceError = "" }, { sourceError = it }) else null
        onDispose { l?.remove() }
    }
    val activeSource = if (item.kind == "invitation") (event?.startsAt ?: 0) > System.currentTimeMillis() else opportunity?.isOpen() == true
    CommunityWindow(if (item.kind == "invitation") "Event invitation" else "Application", close, busy) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { WorkForm {
            Text(item.title, style = MaterialTheme.typography.headlineSmall)
            Text(item.status.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge, color = SeekerGold)
            WorkPerson(item.otherUid(profile.uid), onProfile)
            if (sourceError.isNotBlank()) WorkNotice(sourceError) { retry++ }
            if (!sourceLoaded && sourceError.isBlank()) SpotLoading(true)
            event?.let { Text("${workDate(it.startsAt)}\n${it.location}", style = MaterialTheme.typography.bodyMedium) }
            Text(item.details, style = MaterialTheme.typography.bodyMedium)
            if (event != null || opportunity != null) TextButton({ showSource = true }) { Text(if (item.kind == "invitation") "View attached event" else "View opportunity") }
            if (sourceLoaded && !activeSource && item.status == "pending") Text("This event or opportunity has ended or closed. You can still decline or withdraw the request.", style = MaterialTheme.typography.bodySmall, color = SeekerMuted)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            if (item.canRespond(profile.uid)) {
                WorkButton(if (busy) "Updating…" else "Accept", !busy && activeSource) { confirm = "accepted" }
                OutlinedButton({ confirm = "declined" }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Decline") }
            }
            if (item.canWithdraw(profile.uid)) OutlinedButton({ confirm = "withdrawn" }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Withdraw request") }
            WorkButton("Message about this", !busy) { onMessage(item.otherUid(profile.uid)) }
        } }
    }
    confirm?.let { next ->
        val verb = when (next) { "accepted" -> "Accept"; "declined" -> "Decline"; else -> "Withdraw" }
        SpotConfirmDialog(if (next == "accepted") Icons.Rounded.CheckCircle else Icons.Rounded.Cancel, "$verb this request?",
            "Both people will see the updated status. This response cannot be undone.", verb,
            onDismiss = { confirm = null }, danger = next != "accepted") {
            confirm = null; busy = true; error = ""; scope.launch {
                try { WorkRepository.shared.respond(item, next) }
                catch (c: CancellationException) { throw c }
                catch (_: Exception) { error = "Couldn't update. The request may have changed, the event may have ended, or access may be restricted. Retry when connected." }
                finally { busy = false }
            }
        }
    }
    if (showSource) {
        if (item.kind == "invitation") CommunityDetails(item.sourceId, true, profile, { showSource = false }, onProfile, onChat)
        else OpportunityDetails(item.sourceId, profile, { showSource = false }, onProfile, onMessage)
    }
}
