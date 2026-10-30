package com.example.spotapp.ui.theme.screens.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil3.compose.AsyncImage
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.UUID

@Composable
internal fun ConnectedStories(profile: SpotUiProfile, onOverlay: (Boolean) -> Unit, onProfile: (SpotUiProfile) -> Unit,
    startCreating: Boolean = false, onStartCreating: () -> Unit = {}) {
    val repo = remember { StoryRepository.shared }
    var own by remember(profile.uid) { mutableStateOf(emptyList<StoryItem>()) }
    var public by remember(profile.uid) { mutableStateOf(emptyList<StoryItem>()) }
    var followed by remember(profile.uid) { mutableStateOf(emptyList<String>()) }
    val connections = remember(profile.uid) { mutableStateMapOf<String, List<StoryItem>>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var seen by rememberSaveable(profile.uid) { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    LifecycleDisposableEffect(profile.uid, retry) {
        loading = own.isEmpty(); error = ""
        val a = repo.listenStories(profile.uid, { own = it; loading = false }, { error = it; loading = false })
        val b = repo.listenStories(result = { public = it }, error = { error = it })
        val c = PrivacyRepository.shared.listenConnections(profile.uid, false, { followed = it.take(30) }, { error = it })
        onDispose { a.remove(); b.remove(); c.remove() }
    }
    LifecycleDisposableEffect(followed, retry) {
        connections.keys.toList().filterNot { it in followed }.forEach { connections.remove(it) }
        val registrations = followed.map { uid -> repo.listenStories(uid, { connections[uid] = it }, { connections.remove(uid) }) }
        onDispose { registrations.forEach { it.remove() } }
    }
    val all = (own + public + connections.values.flatten()).distinctBy { it.id }.filter { it.active(now) }
    val owners = all.groupBy { it.userId }
    // The viewer swipes through people in the same order as the circles, your own first.
    val order = listOf(profile.uid).filter { owners[it].orEmpty().isNotEmpty() } + owners.keys.filter { it != profile.uid }
    val groups = order.map { uid -> owners.getValue(uid).sortedBy { it.createdAt } }
    val opened = owners[selected].orEmpty()
    // Story from the bottom bar's Create button.
    LaunchedEffect(startCreating) { if (startCreating) { creating = true; onStartCreating() } }
    SideEffect { onOverlay(creating || selected != null) }
    DisposableEffect(Unit) { onDispose { onOverlay(false) } }
    Column {
        LazyRow(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                StoryCircle(profile.uid, profile.profileImageUrl, "Your Story", owners[profile.uid].orEmpty().any { it.id !in seen }, true,
                    { if (!loading) { if (owners[profile.uid].isNullOrEmpty()) creating = true else selected = profile.uid } }, { creating = true })
            }
            items(owners.keys.filter { it != profile.uid }, key = { it }) { uid ->
                val items = owners.getValue(uid)
                StoryCircle(uid, items.first().profileImageUrl, items.first().username.ifBlank { "Story" }, items.any { it.id !in seen }, false, { selected = uid }, {})
            }
        }
        if (loading) SpotLoading(true, SkeletonKind.STORIES)
        if (error.isNotBlank()) TextButton({ retry++ }) { Text("Stories unavailable · Retry", style = MaterialTheme.typography.labelSmall) }
    }
    if (creating) StoryCreator(profile, { creating = false }, { moment ->
        own = (own + moment).distinctBy { it.id }
        creating = false; selected = profile.uid
    })
    if (selected != null && opened.isNotEmpty()) StoryViewer(groups, order.indexOf(selected), profile.uid, close = { selected = null },
        onSeen = { seen = (seen + it).distinct().takeLast(300) }, onProfile = { uid -> selected = null; onProfile(SpotUiProfile(uid, "", "", "", "")) },
        onDelete = { repo.removeStory(it) }, seen = seen)
    else if (selected != null && !creating) LaunchedEffect(selected, opened) { selected = null }
}

private val StoryRing get() = Brush.sweepGradient(listOf(SpotPalette.GoldBright, SpotPalette.GoldDeep, Color(0xFFE8B84B), SpotPalette.GoldBright))

@Composable
private fun StoryCircle(uid: String, photo: String, label: String, unseen: Boolean, own: Boolean, open: () -> Unit, add: () -> Unit) {
    // As wide as "Your Story", so more of each name fits before it's cut off.
    Column(Modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(66.dp)) {
            Box(Modifier.matchParentSize().clip(CircleShape)
                .background(if (unseen) StoryRing else SolidColor(SpotPalette.Line)).clickable(onClick = open)
                .padding(if (unseen) 3.dp else 1.5.dp).background(SpotPalette.Surface, CircleShape).padding(2.5.dp)) {
                SpotAvatar(photo, label, Modifier.fillMaxSize(), uid)
            }
            if (own) IconButton(add, Modifier.align(Alignment.BottomEnd).offset(8.dp, 8.dp).size(36.dp).testTag("add-story")) {
                Box(Modifier.size(24.dp).shadow(3.dp, CircleShape, ambientColor = SpotPalette.Shadow, spotColor = SpotPalette.Shadow)
                    .background(SpotGoldGradient, CircleShape).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, "Add story", Modifier.size(15.dp), tint = SpotPalette.OnGold)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = SpotPalette.Ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}
