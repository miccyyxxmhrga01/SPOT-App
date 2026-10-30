package com.example.spotapp.ui.theme.screens.discover

import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.components.SpotLoading
import com.example.spotapp.ui.theme.components.SkeletonKind
import com.example.spotapp.ui.theme.components.SpotButtonStyle
import com.example.spotapp.ui.theme.components.SpotPillButton

import com.example.spotapp.ui.theme.components.LifecycleDisposableEffect

import com.example.spotapp.ui.theme.components.SpotConfirmDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PersonAdd

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.data.PageCursor
import kotlinx.coroutines.*

/**
 * An event's or group's page. Events open [EventPage]. A group's members get its chat ([onChat] receives the
 * conversation id) and a page of posts from its members.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommunityDetails(id: String, isEvent: Boolean, profile: SpotUiProfile, close: () -> Unit, onProfile: (SpotUiProfile) -> Unit,
    onChat: (String) -> Unit, startApply: Boolean = false) {
    if (isEvent) { EventPage(id, profile, close, onProfile, onChat, startApply); return }
    val repo = remember { DiscoverRepository.shared }
    val scope = rememberCoroutineScope()
    var group by remember { mutableStateOf<CampusGroup?>(null) }
    var joined by remember { mutableStateOf<Boolean?>(null) }
    var requested by remember { mutableStateOf<Boolean?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var peopleMode by rememberSaveable { mutableStateOf<String?>(null) }
    var posts by remember { mutableStateOf<SocialPage<GroupPost>?>(null) }
    var postsError by remember { mutableStateOf("") }
    var postCount by rememberSaveable { mutableIntStateOf(20) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LifecycleDisposableEffect(id, revision) {
        error = ""; loading = true; joined = null; requested = null
        val failed: (String) -> Unit = { error = it; loading = false }
        val registrations = listOf(
            repo.listenGroup(id, { group = it; loading = false }, failed),
            repo.listenRequest(id, { requested = it }, failed),
            repo.listenMembership(id, false, { joined = it }, failed))
        onDispose { registrations.forEach { it.remove() } }
    }
    // Joining a private group is what makes its posts readable, so they reload when membership changes.
    LifecycleDisposableEffect(id, revision, postCount, joined) {
        postsError = ""
        val listener = repo.listenGroupPosts(id, postCount, { posts = it; postsError = "" }, { postsError = it })
        onDispose { listener.remove() }
    }
    fun act(failure: String = "Couldn't update your membership. Please retry.", action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = ""
        scope.launch { try { withTimeout(30_000) { action() } }
        catch (_: TimeoutCancellationException) { error = "The request took too long. Reconnect and retry." }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = if (e is IllegalStateException) e.message.orEmpty() else failure }
        finally { busy = false } }
    }
    fun openChat() = act("Couldn't open the chat. Please retry.") { onChat(repo.openChat(id, false)) }
    CommunityWindow("Group", close, busy) { padding ->
        // imePadding keeps the post composer above the keyboard.
        LazyColumn(Modifier.fillMaxSize().padding(padding).navigationBarsPadding().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = CommunityGold) }
            item { CommunityError(error) { revision++ } }
            if (!loading && error.isBlank() && group == null) item { Text("This group is no longer available.") }
            group?.let { g ->
                item { CommunityCover(g.imageUrl, false, Modifier.fillMaxWidth().height(200.dp), title = g.name) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CommunityBadge("${g.category} · ${g.privacy.replaceFirstChar { it.uppercase() }}")
                        Text(g.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("${g.memberCount} ${if (g.memberCount == 1L) "member" else "members"}", style = MaterialTheme.typography.bodySmall, color = CommunityGold)
                        Text(g.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item { OwnerRow(g.ownerName, g.ownerProfileImageUrl, "Owner · Admin", g.ownerId) { onProfile(SpotUiProfile(g.ownerId, g.ownerName, "", "", "", g.ownerProfileImageUrl)) } }
                val owner = g.ownerId == profile.uid
                val member = owner || joined == true
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (owner) CommunityBadge("You own this group")
                        else MembershipButton(when { busy -> "Updating…"; joined == true -> "Leave group"; requested == true -> "Cancel join request"; g.privacy == "private" -> "Request to join"; else -> "Join group" },
                            primary = joined != true && requested != true, enabled = !busy && joined != null && requested != null && error.isBlank()) {
                            act { when { joined == true -> repo.setGroupJoined(id, false); requested == true -> repo.cancelRequest(id); g.privacy == "private" -> repo.requestJoin(id); else -> repo.setGroupJoined(id, true) } }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (owner) {
                                SpotPillButton("Edit group", { editing = true }, icon = Icons.Rounded.Edit, style = SpotButtonStyle.Secondary,
                                    enabled = !busy, compact = true)
                                SpotPillButton("Delete group", { confirmDelete = true }, icon = Icons.Rounded.DeleteOutline,
                                    style = SpotButtonStyle.Secondary, enabled = !busy, compact = true)
                            }
                            if (member) SpotPillButton("Group chat", ::openChat, icon = Icons.Rounded.Forum, enabled = !busy, compact = true)
                            if (g.privacy == "public" || member) SpotPillButton("Members", { peopleMode = "members" }, icon = Icons.Rounded.Groups, style = SpotButtonStyle.Secondary, compact = true)
                            if (owner && g.privacy == "private") SpotPillButton("Join requests", { peopleMode = "requests" }, icon = Icons.Rounded.PersonAdd, style = SpotButtonStyle.Secondary, compact = true)
                        }
                        if (requested == true && !member) Text("Your request is waiting for the owner's approval.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
                groupPage(g, profile, member, readable = g.privacy == "public" || member, posts = posts, error = postsError,
                    retry = { revision++ }, more = { postCount += 20 }, onProfile = onProfile)
            }
        }
    }
    when (val mode = peopleMode) {
        null -> Unit
        else -> CommunityPeople(id, false, mode == "requests", close = { peopleMode = null }, onProfile = { peopleMode = null; onProfile(it) },
            manage = group?.ownerId == profile.uid)
    }
    group?.takeIf { editing }?.let { g ->
        CommunityEditor(false, profile, close = { editing = false }, created = { editing = false }, existingGroup = g)
    }
    if (confirmDelete) SpotConfirmDialog(Icons.Rounded.DeleteOutline, "Delete this group?",
        "It'll be removed for everyone, with its members, posts and group chat. You can't undo this.", "Delete group",
        onDismiss = { if (!busy) confirmDelete = false }, danger = true, busy = busy) {
        act("Couldn't delete this group. Please retry.") {
            try { repo.deleteGroup(id) } finally { confirmDelete = false }
            close()
        }
    }
}

/** Joining is the gold call to action; leaving or cancelling is a quieter outline. */
@Composable
private fun MembershipButton(label: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    if (primary) Button(onClick, Modifier.fillMaxWidth(), enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = CommunityGold)) { Text(label) }
    else OutlinedButton(onClick, Modifier.fillMaxWidth(), enabled = enabled) { Text(label) }
}

@Composable
private fun OwnerRow(name: String, image: String, role: String, uid: String = "", open: () -> Unit) {
    Surface(onClick = open, color = SpotPalette.Surface, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CommunityAvatar(image, uid)
            Column { Text(name, style = MaterialTheme.typography.titleSmall); Text(role, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
        }
    }
}

/** A group's members or join requests, or an event's performers. With [manage], the group's owner can remove members. */
@Composable
internal fun CommunityPeople(id: String, isEvent: Boolean, requests: Boolean, close: () -> Unit, onProfile: (SpotUiProfile) -> Unit,
    manage: Boolean = false) {
    var removing by remember { mutableStateOf<CommunityMember?>(null) }
    var people by remember { mutableStateOf(emptyList<CommunityMember>()) }
    var cursor by remember { mutableStateOf<PageCursor?>(null) }
    var more by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id, page, revision) {
        loading = true; error = ""
        try {
            val result = DiscoverRepository.shared.people(id, isEvent, requests, if (page == 0) null else cursor)
            people = (if (page == 0) result.items else people + result.items).distinctBy { it.userId }
            cursor = result.cursor; more = result.hasMore
        } catch (e: CancellationException) { throw e } catch (_: Exception) { error = "This list couldn't load. Check your access and retry."; people = emptyList() }
        finally { loading = false }
    }
    CommunityWindow(if (requests) "Join requests" else if (isEvent) "Performers" else "Members", close, busy != null) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).navigationBarsPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { TextButton(onClick = { page = 0; revision++ }) { Text("Refresh list") }; CommunityError(error) { page = 0; revision++ } }
            items(people, key = { it.userId }) { person ->
                Column {
                    OwnerRow(person.displayName, person.profileImageUrl,
                        if (requests) "Requested to join" else if (person.role == "owner") "Owner · Admin" else if (isEvent) "Performer" else "Member", person.userId) {
                        onProfile(SpotUiProfile(person.userId, person.displayName, "", "", "", person.profileImageUrl))
                    }
                    if (requests) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(true, false).forEach { approve -> TextButton(onClick = {
                            if (busy == null) { busy = person.userId; scope.launch {
                                try { withTimeout(30_000) { DiscoverRepository.shared.reviewRequest(id, person.userId, approve) }; people = people.filterNot { it.userId == person.userId } }
                                catch (_: TimeoutCancellationException) { error = "Request timed out. Refresh to check its status." }
                                catch (e: CancellationException) { throw e } catch (_: Exception) { error = "Couldn't review this request. Please retry." }
                                finally { busy = null }
                            } }
                        }, enabled = busy == null) { Text(if (approve) "Approve" else "Decline") } }
                    }
                    if (manage && !requests && !isEvent && person.role != "owner") TextButton(onClick = { removing = person }, enabled = busy == null) {
                        Icon(Icons.Rounded.PersonRemove, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Remove from group")
                    }
                }
            }
            item {
                if (loading) SpotLoading(true, SkeletonKind.ROWS)
                else if (error.isBlank() && people.isEmpty()) Text(if (requests) "No pending requests" else if (isEvent) "No performers yet" else "No members yet", style = MaterialTheme.typography.bodyMedium)
                if (more && !loading) TextButton(onClick = { page++ }) { Text("Load more") }
            }
        }
    }
    removing?.let { person ->
        SpotConfirmDialog(Icons.Rounded.PersonRemove, "Remove ${person.displayName.ifBlank { "this member" }}?",
            "They'll leave the group and its chat. They can ask to join again later.", "Remove",
            onDismiss = { if (busy == null) removing = null }, danger = true, busy = busy != null) {
            busy = person.userId
            scope.launch {
                try {
                    withTimeout(30_000) { DiscoverRepository.shared.removeMember(id, person.userId) }
                    people = people.filterNot { it.userId == person.userId }
                } catch (_: TimeoutCancellationException) { error = "That took too long. Refresh to check the member list." }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = userMessage(e, "Couldn't remove this member. Please retry.") }
                finally { busy = null; removing = null }
            }
        }
    }
}
