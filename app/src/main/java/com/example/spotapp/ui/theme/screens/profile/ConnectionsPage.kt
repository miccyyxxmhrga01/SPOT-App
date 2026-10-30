package com.example.spotapp.ui.theme.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.discover.CommunityWindow
import kotlinx.coroutines.*

@Composable
internal fun ConnectionsPage(profile: SpotUiProfile, mode: String, close: () -> Unit, onProfile: (SpotUiProfile) -> Unit = {}) {
    var ids by remember { mutableStateOf(emptyList<String>()) }
    var people by remember { mutableStateOf(emptyList<SpotUser>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LifecycleDisposableEffect(profile.uid, mode, retry) {
        loading = true; error = ""
        val result: (List<String>) -> Unit = { ids = it; if (it.isEmpty()) { people = emptyList(); loading = false } }
        val failed: (String) -> Unit = { error = it; loading = false }
        val listener = when (mode) {
            "requests" -> PrivacyRepository.shared.listenRequests(result, failed)
            "blocked" -> PrivacyRepository.shared.listenBlocked(result, failed)
            else -> PrivacyRepository.shared.listenConnections(profile.uid, mode == "followers", result, failed)
        }
        onDispose { listener.remove() }
    }
    LaunchedEffect(ids, retry) {
        if (ids.isEmpty()) return@LaunchedEffect
        try { people = ProfileRepository.shared.loadProfiles(ids); loading = false }
        catch (e: CancellationException) { throw e } catch (_: Exception) { error = "Profiles couldn't load."; loading = false }
    }
    fun act(id: String, accept: Boolean) {
        if (busy != null) return
        busy = id; error = ""
        scope.launch {
            try { withTimeout(30_000) {
                if (mode == "blocked") PrivacyRepository.shared.unblock(id) else PrivacyRepository.shared.reviewRequest(id, accept)
            } }
            catch (_: TimeoutCancellationException) { error = "Request timed out. Retry." }
            catch (e: CancellationException) { throw e } catch (_: Exception) { error = "Couldn't update this account. Retry." }
            finally { busy = null }
        }
    }
    val shown = people.filter { it.uid in ids && (query.isBlank() || it.displayName.contains(query, true) || it.username.contains(query.removePrefix("@"), true)) }
    CommunityWindow(when(mode) { "requests" -> "Follow requests"; "blocked" -> "Blocked accounts"; "followers" -> "Followers"; else -> "Following" }, close, busy != null) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).navigationBarsPadding().padding(horizontal = 16.dp)) {
            SpotSearchField(query, { query = it }, loading = loading)
            if (error.isNotBlank()) TextButton({ retry++ }) { Text("$error Retry", color = MaterialTheme.colorScheme.error) }
            if (loading) SpotLoading(true, SkeletonKind.ROWS)
            else if (shown.isEmpty()) Text(if (query.isBlank()) "No accounts here yet" else "No results", Modifier.padding(vertical = 24.dp), style = MaterialTheme.typography.bodyMedium)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) {
                items(shown, key = { it.uid }) { person ->
                    Column(Modifier.animateItem().padding(vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth().clickable { onProfile(person.asProfileUi()) }, verticalAlignment = Alignment.CenterVertically) {
                            SpotAvatar(person.profileImageUrl, person.displayName, Modifier.size(42.dp), person.uid)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(person.displayName, style = MaterialTheme.typography.titleSmall)
                                Text("@${person.username}", style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted)
                            }
                            if (mode == "blocked") TextButton({ act(person.uid, false) }, enabled = busy == null) { Text("Unblock") }
                        }
                        if (mode == "requests") Row(Modifier.padding(start = 54.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ act(person.uid, true) }, enabled = busy == null) { Text(if (busy == person.uid) "Updating…" else "Approve") }
                            TextButton({ act(person.uid, false) }, enabled = busy == null) { Text("Decline") }
                        }
                    }
                }
            }
        }
    }
}
