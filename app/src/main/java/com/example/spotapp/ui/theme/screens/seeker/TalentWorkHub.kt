package com.example.spotapp.ui.theme.screens.seeker

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.spotapp.data.AccountType
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.discover.CommunityWindow
import com.example.spotapp.ui.theme.screens.settings.SavedPostsPage

/** Spotter work tools, shown on Discover rather than above the home stories. */
@Composable
internal fun SpotterShortcuts(profile: SpotUiProfile, onOpen: (String) -> Unit) {
    if (profile.accountType != AccountType.TALENT_SEEKER) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)
        .testTag("spotter-shortcuts"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpotPillButton("Find creators", { onOpen("talent") }, icon = Icons.Rounded.PersonSearch, compact = true)
        SpotPillButton("Saved work", { onOpen("saved") }, icon = Icons.Rounded.BookmarkBorder, style = SpotButtonStyle.Secondary, compact = true)
        SpotPillButton("My events", { onOpen("events") }, icon = Icons.Rounded.Event, style = SpotButtonStyle.Secondary, compact = true)
    }
}

@Composable
internal fun TalentWorkHub(profile: SpotUiProfile, initialPage: String, close: () -> Unit,
    onProfile: (SpotUiProfile) -> Unit, onMessage: (String) -> Unit, onChat: (String) -> Unit) {
    var page by rememberSaveable { mutableStateOf(initialPage) }
    var invitee by remember { mutableStateOf<SpotUiProfile?>(null) }
    val seeker = profile.accountType == AccountType.TALENT_SEEKER
    val title = when (page) { "talent" -> "Find creators"; "events" -> "My events"; "saved" -> "Saved work";
        "requests" -> if (seeker) "Hiring requests" else "Your requests"; else -> "Jobs & auditions" }
    fun openProfile(person: SpotUiProfile) { close(); onProfile(person) }
    fun message(uid: String) { close(); onMessage(uid) }
    fun chat(id: String) { close(); onChat(id) }
    CommunityWindow(title, close) { padding ->
        CompositionLocalProvider(LocalSpotBottomContentPadding provides 24.dp) {
            AnimatedContent(page, modifier = Modifier.fillMaxSize().padding(padding).navigationBarsPadding(), label = "talent workspace") { selected ->
                when (selected) {
                    "talent" -> TalentDirectoryScreen(profile, ::openProfile, if (seeker) ({ invitee = it }) else null)
                    "events" -> WorkEvents(profile, ::openProfile, onChat = ::chat)
                    "saved" -> SavedPostsPage(profile, ::openProfile)
                    "requests" -> WorkRequests(profile, onProfile = ::openProfile, onMessage = ::message, onChat = ::chat)
                    else -> WorkOpportunities(profile, ::openProfile, ::message)
                }
            }
        }
    }
    invitee?.let { EventInviteDialog(profile, it, { invitee = null }, ::message) }
}

@Composable
internal fun WorkNotice(text: String, retry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = SeekerMuted)
        if (retry != null) TextButton(retry) { Text("Retry", color = SeekerGold) }
    }
}

internal fun workDate(time: Long): String = java.text.SimpleDateFormat("MMM d, yyyy · h:mm a", java.util.Locale.getDefault()).format(java.util.Date(time))
