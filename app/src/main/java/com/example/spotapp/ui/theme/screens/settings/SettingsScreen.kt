package com.example.spotapp.ui.theme.screens.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.spotapp.BuildConfig
import com.example.spotapp.SignupPolicy
import com.example.spotapp.SignupPolicyDialog
import com.example.spotapp.data.*
import com.example.spotapp.data.collaboration.CollaborationRepository
import com.example.spotapp.data.preferences.ThemeMode
import com.example.spotapp.data.preferences.ThemePreferences
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.spotSp
import androidx.compose.runtime.collectAsState
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.home.ProfilePublishedPostViewer
import com.example.spotapp.ui.theme.screens.profile.ConnectedEditProfile
import com.example.spotapp.ui.theme.screens.profile.ConnectionsPage
import kotlinx.coroutines.*

/** A settings line: a switch when [checked] is set, otherwise a link that may show a value or a red count. */
private class SettingsLine(val title: String, val icon: ImageVector, val keywords: String = "", val value: String? = null,
    val badge: Int = 0, val checked: Boolean? = null, val enabled: Boolean = true, val onToggle: (Boolean) -> Unit = {},
    val onClick: () -> Unit = {})

private val PageTitles = mapOf("audience" to "Audience", "saved" to "Saved posts", "security" to "Sign-in & password",
    "delete" to "Delete account", "feedback" to "Send feedback", "about" to "Help & about")

/** Pages opened from Sign-in & password. */
private val SecurityPages = setOf("password", "delete")

@Composable
fun SettingsScreen(onBack: () -> Unit, onLogout: () -> Unit, onAddProfile: () -> Unit = {},
    profile: SpotUiProfile? = null, onProfile: (SpotUiProfile) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeMode by remember { ThemePreferences.mode(context.applicationContext) }.collectAsState(ThemeMode.System)
    var current by remember(profile) { mutableStateOf(profile) }
    var page by rememberSaveable { mutableStateOf("settings") }
    var query by rememberSaveable { mutableStateOf("") }
    var prefs by remember { mutableStateOf(SpotPreferences()) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var privateConfirmation by remember { mutableStateOf<Boolean?>(null) }
    var logout by remember { mutableStateOf(false) }
    var policy by remember { mutableStateOf<SignupPolicy?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val id = profile?.uid ?: HomeRepository.currentUserId().orEmpty()
    LifecycleDisposableEffect(id, retry) {
        error = ""
        val a = if (id.isNotBlank()) ProfileRepository.shared.listen(id, { current = it?.asProfileUi() ?: current }, { error = it }) else null
        val b = if (id.isNotBlank()) PrivacyRepository.shared.listenPreferences({ prefs = it; loaded = true }, { error = it }) else null
        onDispose { a?.remove(); b?.remove() }
    }
    // Counts shown beside Saved posts, Follow requests and Blocked accounts.
    var savedCount by remember { mutableIntStateOf(0) }
    var requestCount by remember { mutableIntStateOf(0) }
    var blockedCount by remember { mutableIntStateOf(0) }
    LifecycleDisposableEffect(id, retry) {
        val privacy = PrivacyRepository.shared
        val listeners = if (id.isBlank() || Supabase.uid() == null) emptyList() else listOf(
            privacy.listenSaved({ savedCount = it.size }, {}),
            privacy.listenRequests({ requestCount = it.size }, {}),
            privacy.listenBlocked({ blockedCount = it.size }, {}))
        onDispose { listeners.forEach { it.remove() } }
    }
    // Change password and Delete account sit under Sign-in & password, so Back returns there.
    fun back() { if (!busy) { if (page == "settings") onBack() else { page = if (page in SecurityPages) "security" else "settings"; error = ""; notice = "" } } }
    // Read again on each page change, so a password set here asks for it next time.
    val hasPassword = remember(page) { AuthRepository.currentUserHasPassword() }
    BackHandler { back() }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = ""
        scope.launch {
            try { withTimeout(180_000) { block() } }
            catch (_: TimeoutCancellationException) { error = "Update timed out. Retry." }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Couldn't save changes. Retry." }
            finally { busy = false }
        }
    }
    fun save(next: SpotPreferences) = action { PrivacyRepository.shared.savePreferences(next) }
    // Keeps the place and talents set in Discover › Collabs; only the switch changes.
    fun setCollab(open: Boolean) = action {
        val repository = CollaborationRepository()
        val saved = repository.getPreferences().getOrThrow()
        repository.savePreferences(saved.copy(openToCollab = open)).getOrThrow()
        current = current?.copy(openToCollab = open)
    }
    AnimatedContent(page, transitionSpec = {
        val backward = targetState == "settings" || initialState in SecurityPages
        (fadeIn(tween(170)) + slideInHorizontally(tween(220)) { if (backward) -it / 8 else it / 8 }) togetherWith fadeOut(tween(120))
    }, label = "settings page") { shown ->
        val account = current
        when {
            shown == "edit" && account != null -> ConnectedEditProfile(account, { page = "settings" }, { page = "settings" })
            shown in listOf("requests", "blocked") && account != null -> ConnectionsPage(account, shown, { page = "settings" }, onProfile)
            else -> Surface(Modifier.fillMaxSize().spotAmbientBackground(SpotPalette.Ivory), color = Color.Transparent) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    SpotScreenHeader(when (shown) {
                        "settings" -> "Settings"
                        "password" -> if (hasPassword) "Change password" else "Set a password"
                        else -> PageTitles[shown].orEmpty()
                    },
                        onBack = ::back, backEnabled = !busy)
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = SpotPalette.GoldDeep, trackColor = SpotPalette.Track)
                    if (error.isNotBlank()) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .background(SpotPalette.Danger.copy(alpha = .07f), RoundedCornerShape(14.dp)).padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(18.dp), tint = SpotPalette.Danger)
                        Text(error, Modifier.weight(1f).padding(horizontal = 8.dp), color = SpotPalette.Danger, style = MaterialTheme.typography.bodySmall)
                        TextButton({ retry++ }) { Text("Retry", color = SpotPalette.Danger, fontWeight = FontWeight.SemiBold) }
                    }
                    if (shown == "saved" && account != null) SavedPostsPage(account, onProfile)
                    else if (shown == "feedback") FeedbackPage(account)
                    else if (shown == "password") ChangePasswordPage(hasPassword, busy, { busy = it }) { notice = it; page = "security" }
                    else if (shown == "delete") DeleteAccountPage(hasPassword, account?.username.orEmpty(), busy, onBusy = { busy = it })
                    else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        when (shown) {
                            "settings" -> {
                                item { AccountCard(account, id) { page = "edit" } }
                                item { SpotSearchField(query, { query = it }, placeholder = "Search settings") }
                                val creator = account != null && account.accountType != AccountType.TALENT_SEEKER
                                val switchesReady = loaded && !busy
                                val sections = listOf(
                                    "Quick switches" to listOfNotNull(
                                        SettingsLine("Private account", Icons.Rounded.Lock, "privacy public approve followers",
                                            checked = account?.isPrivate == true, enabled = !busy && account != null, onToggle = { privateConfirmation = it }),
                                        SettingsLine("Activity status", Icons.Rounded.Circle, "show when I'm active online green dot typing",
                                            checked = prefs.showActivity, enabled = switchesReady, onToggle = { save(prefs.copy(showActivity = it)) }),
                                        SettingsLine("Read receipts", Icons.Rounded.DoneAll, "seen messages chats read",
                                            checked = prefs.readReceipts, enabled = switchesReady, onToggle = { save(prefs.copy(readReceipts = it)) }),
                                        if (creator) SettingsLine("Open to collabs", Icons.Rounded.Handshake, "collaboration invites suggestions",
                                            checked = account?.openToCollab == true, enabled = !busy, onToggle = { setCollab(it) }) else null),
                                    "Account" to listOf(
                                        SettingsLine("Appearance", Icons.Rounded.DarkMode, "theme dark light night mode", value = themeMode.label) {
                                            val next = ThemeMode.entries[(themeMode.ordinal + 1) % ThemeMode.entries.size]
                                            scope.launch { ThemePreferences.setMode(context.applicationContext, next) }
                                        },
                                        SettingsLine("Saved posts", Icons.Rounded.BookmarkBorder, "bookmarks", value = settingsCount(savedCount)) { page = "saved" },
                                        SettingsLine("Sign-in & password", Icons.Rounded.Key, "email security login") { page = "security" }),
                                    "Privacy & safety" to listOf(
                                        SettingsLine("Audience", Icons.Rounded.Visibility, "default posts stories who sees",
                                            value = audienceSummary(prefs, account?.isPrivate == true).takeIf { loaded }) { page = "audience" },
                                        SettingsLine("Follow requests", Icons.Rounded.PersonAdd, "approve followers", badge = requestCount) { page = "requests" },
                                        SettingsLine("Blocked accounts", Icons.Rounded.Block, "block", value = settingsCount(blockedCount)) { page = "blocked" }),
                                    "Notify me about" to listOf(
                                        SettingsLine("Likes", Icons.Rounded.FavoriteBorder, "notifications activity", checked = prefs.likes,
                                            enabled = switchesReady, onToggle = { save(prefs.copy(likes = it)) }),
                                        SettingsLine("Comments", Icons.Rounded.ChatBubbleOutline, "notifications activity", checked = prefs.comments,
                                            enabled = switchesReady, onToggle = { save(prefs.copy(comments = it)) }),
                                        SettingsLine("New followers", Icons.Rounded.GroupAdd, "notifications activity follows", checked = prefs.follows,
                                            enabled = switchesReady, onToggle = { save(prefs.copy(follows = it)) }),
                                        SettingsLine("Messages", Icons.Rounded.Forum, "notifications chats push", checked = prefs.messages,
                                            enabled = switchesReady, onToggle = { save(prefs.copy(messages = it)) }),
                                        SettingsLine("Events & groups", Icons.Rounded.Event, "notifications applications joins community",
                                            checked = prefs.community, enabled = switchesReady, onToggle = { save(prefs.copy(community = it)) }),
                                        SettingsLine("Invitations & requests", Icons.Rounded.WorkOutline, "notifications collab work talent",
                                            checked = prefs.requests, enabled = switchesReady, onToggle = { save(prefs.copy(requests = it)) })),
                                    "Support" to listOf(
                                        SettingsLine("Send feedback", Icons.Rounded.Feedback, "bug report idea") { page = "feedback" },
                                        SettingsLine("Help & about", Icons.Rounded.Info, "terms privacy policy support version") { page = "about" }))
                                val term = query.trim()
                                val visible = sections.map { (title, lines) ->
                                    title to lines.filter { line -> term.isEmpty() || listOf(line.title, line.keywords, title).any { it.contains(term, true) } }
                                }.filter { it.second.isNotEmpty() }
                                if (visible.isEmpty()) item { SpotEmptyState(Icons.Rounded.SearchOff, "No settings found", message = "Try another word.") }
                                visible.forEach { (title, lines) ->
                                    item(key = title) { SettingsSection(title) }
                                    item(key = "$title rows") { SettingsCard { lines.forEachIndexed { index, line ->
                                        SettingsLineRow(line)
                                        if (index < lines.lastIndex) SettingsDivider()
                                    } } }
                                    if (title == "Quick switches" && account?.privacyUpdating == true) item(key = "privacy setup") {
                                        SpotCard(color = SpotPalette.GoldWash, border = SpotPalette.GoldLine, shape = RoundedCornerShape(20.dp), elevation = 1.dp) {
                                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                                                SpotIconTile(Icons.Rounded.WarningAmber, size = 38.dp, container = SpotPalette.Surface)
                                                Column(Modifier.padding(start = 12.dp)) {
                                                    Text("Privacy setup is unfinished.", style = MaterialTheme.typography.titleSmall, color = SpotPalette.Ink)
                                                    SpotPillButton("Finish privacy setup", { action { PrivacyRepository.shared.setPrivate(true) } },
                                                        Modifier.padding(top = 10.dp), enabled = !busy, compact = true)
                                                }
                                            }
                                        }
                                    }
                                }
                                item(key = "logout") {
                                    OutlinedButton({ logout = true }, Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 50.dp).testTag("settings-logout"),
                                        shape = CircleShape, border = BorderStroke(1.dp, SpotPalette.Danger.copy(alpha = .3f)),
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = SpotPalette.Surface, contentColor = SpotPalette.Danger)) {
                                        Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(18.dp))
                                        Text("Log out", Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                item(key = "version") { Text("SPOT ${BuildConfig.VERSION_NAME}", Modifier.fillMaxWidth().padding(top = 4.dp),
                                    style = MaterialTheme.typography.labelSmall, color = SpotPalette.Muted, textAlign = TextAlign.Center) }
                            }
                            "audience" -> {
                                item { SettingsCard { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text("New posts and stories start with these audiences.", style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted)
                                    AudiencePreference("Posts", Icons.Rounded.GridView, prefs.postAudience, account?.isPrivate == true, loaded && !busy) { save(prefs.copy(postAudience = it)) }
                                    HorizontalDivider(color = SpotPalette.Line)
                                    AudiencePreference("Stories", Icons.Rounded.AutoStories, prefs.storyAudience, account?.isPrivate == true, loaded && !busy) { save(prefs.copy(storyAudience = it)) }
                                } } }
                            }
                            "security" -> {
                                item { SettingsCard {
                                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        SpotIconTile(Icons.Rounded.MailOutline, size = 38.dp)
                                        Column(Modifier.padding(start = 14.dp)) {
                                            Text("Signed in as", style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted)
                                            Text(AuthRepository.currentUserEmail(), style = MaterialTheme.typography.bodyLarge, color = SpotPalette.Ink)
                                        }
                                    }
                                } }
                                item { SettingsCard { SettingsRow("Change password", Icons.Rounded.Key, "Set a new password for this account") { if (!busy) page = "password" } } }
                                if (notice.isNotBlank()) item {
                                    Row(Modifier.fillMaxWidth().background(SpotPalette.GoldSoft, RoundedCornerShape(14.dp)).padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = SpotPalette.GoldDeep)
                                        Text(notice, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, color = SpotPalette.Ink)
                                    }
                                }
                                item { Box(Modifier.padding(top = 12.dp).testTag("setting-delete-account")) { SettingsCard {
                                    SettingsRow("Delete account", Icons.Rounded.DeleteForever, "Remove your account and data", danger = true) {
                                        if (!busy) page = "delete"
                                    }
                                } } }
                            }
                            "about" -> {
                                item { SpotCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), elevation = 1.dp) {
                                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        SpotLogoMedallion(72.dp)
                                        SpotWordmarkText(26.sp, Modifier.padding(top = 14.dp))
                                        Text("Campus talent & connections", style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted,
                                            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                                        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, color = SpotPalette.Muted,
                                            modifier = Modifier.padding(top = 10.dp).background(SpotPalette.Track, CircleShape).padding(horizontal = 10.dp, vertical = 3.dp))
                                    }
                                } }
                                item { SettingsCard {
                                    SettingsRow("Terms of Service", Icons.Rounded.Description, "The rules for using SPOT") { policy = SignupPolicy.TERMS }
                                    SettingsDivider()
                                    SettingsRow("Privacy Policy", Icons.Rounded.PrivacyTip, "How SPOT handles your data") { policy = SignupPolicy.PRIVACY }
                                } }
                            }
                        }
                    }
                }
            }
        }
    }
    privateConfirmation?.let { private ->
        SpotConfirmDialog(if (private) Icons.Rounded.Lock else Icons.Rounded.Public, if (private) "Make account private?" else "Make account public?",
            if (private) "Existing public posts, stories and highlights will become follower-only. Current followers keep access; new followers need your approval."
            else "New followers won't need approval. Existing follower-only posts and highlights keep their audience.",
            if (private) "Make private" else "Make public", onDismiss = { privateConfirmation = null }) {
            privateConfirmation = null; action { PrivacyRepository.shared.setPrivate(private) }
        }
    }
    if (logout) SpotConfirmDialog(Icons.AutoMirrored.Rounded.Logout, "Log out of SPOT?", "You'll need to sign in again to use SPOT on this phone.",
        "Log out", danger = true, onDismiss = { logout = false }, onConfirm = onLogout)
    policy?.let { SignupPolicyDialog(it) { policy = null } }
}

/** Current password (when the account has one), then the new one twice; Save stays off until every rule is met. */
@Composable
private fun ChangePasswordPage(hasPassword: Boolean, busy: Boolean, onBusy: (Boolean) -> Unit, onChanged: (String) -> Unit) {
    // Passwords stay out of saved state.
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf("") }
    val ready = (!hasPassword || current.isNotEmpty()) && AuthRepository.passwordError(newPassword) == null && confirm == newPassword
    fun save() {
        if (!ready || busy) return
        onBusy(true); failure = ""
        AuthRepository.changePassword(current.takeIf { hasPassword }, newPassword) { ok, message ->
            onBusy(false)
            if (ok) onChanged(message.orEmpty()) else failure = message.orEmpty()
        }
    }
    SpotNewPasswordForm(
        intro = if (hasPassword) "Signed in as ${AuthRepository.currentUserEmail()}"
            else "You sign in with Google. Add a password to also sign in with your email.",
        newPassword = newPassword, onNewPassword = { newPassword = it; failure = "" },
        confirm = confirm, onConfirm = { confirm = it; failure = "" },
        visible = visible, onToggleVisible = { visible = !visible }, busy = busy, error = failure,
        saveLabel = "Save password", ready = ready, onSave = ::save, modifier = Modifier.testTag("change-password-page")
    ) {
        if (hasPassword) SpotPasswordField(current, { current = it; failure = "" }, "Current password", visible, { visible = !visible },
            Modifier.testTag("current-password"), enabled = !busy)
    }
}

/**
 * What deleting takes, then the password (Google-only accounts type DELETE instead) and a red button pinned above the
 * keyboard. A yes/no pop-up comes last. Once the account is gone this phone signs out, and Login says so.
 */
@Composable
internal fun DeleteAccountPage(hasPassword: Boolean, username: String, busy: Boolean, onBusy: (Boolean) -> Unit,
    // Previews and review tests pass a stand-in: they run beside a real signed-in account.
    deleteAccount: ((password: String?, callback: (Boolean, String?) -> Unit) -> Unit)? = null) {
    val context = LocalContext.current
    val delete = deleteAccount ?: { password, callback -> AuthRepository.deleteAccount(context, password, callback) }
    // The password stays out of saved state.
    var password by remember { mutableStateOf("") }
    var typed by rememberSaveable { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    val ready = if (hasPassword) password.isNotEmpty() else typed.trim() == "DELETE"
    fun confirmed() {
        confirming = false
        if (!ready || busy) return
        onBusy(true); failure = ""
        delete(password.takeIf { hasPassword }) { ok, message ->
            onBusy(false)
            if (!ok) failure = message.orEmpty()
        }
    }
    Column(Modifier.fillMaxSize().imePadding().testTag("delete-account-page")) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SpotCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = SpotPalette.Danger.copy(alpha = .06f),
                border = SpotPalette.Danger.copy(alpha = .25f), elevation = 0.dp) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    SpotIconTile(Icons.Rounded.WarningAmber, size = 38.dp, tint = SpotPalette.Danger, container = SpotPalette.Danger.copy(alpha = .1f))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("This can't be undone", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = SpotPalette.Ink)
                        Text("Your account is deleted right away.", style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted)
                    }
                }
            }
            Column {
                SettingsSection("What gets deleted")
                Spacer(Modifier.height(8.dp))
                SettingsCard {
                    DeletedItem(Icons.Rounded.Person, "Your profile and followers")
                    DeletedItem(Icons.Rounded.GridView, "Posts, stories and comments")
                    DeletedItem(Icons.Rounded.ChatBubbleOutline, "Messages and direct chats")
                    DeletedItem(Icons.Rounded.Groups, "Groups you own, events you host")
                    DeletedItem(Icons.Rounded.FavoriteBorder, "Likes, saves and applications")
                }
            }
            if (hasPassword) SpotPasswordField(password, { password = it; failure = "" }, "Password", visible, { visible = !visible },
                Modifier.testTag("delete-password"), enabled = !busy, imeAction = ImeAction.Done, onDone = { if (ready && !busy) confirming = true })
            else OutlinedTextField(typed, { typed = it.uppercase().take(12); failure = "" }, Modifier.fillMaxWidth().testTag("delete-type"),
                enabled = !busy, singleLine = true, label = { Text("Type DELETE to confirm") }, shape = SpotAuthFieldShape,
                colors = spotAuthFieldColors(), keyboardOptions = KeyboardOptions(KeyboardCapitalization.Characters, autoCorrectEnabled = false,
                    imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { if (ready && !busy) confirming = true }))
            if (failure.isNotBlank()) Text(failure, Modifier.testTag("delete-error").semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall, color = SpotPalette.ErrorText)
        }
        Button({ confirming = true }, Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().heightIn(min = 56.dp)
            .testTag("delete-account-button"), enabled = ready && !busy, shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = SpotPalette.Danger, contentColor = MaterialTheme.colorScheme.onError,
                disabledContainerColor = SpotPalette.Danger.copy(alpha = .3f), disabledContentColor = Color.White.copy(alpha = .85f))) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            else {
                Icon(Icons.Rounded.DeleteForever, null, Modifier.size(20.dp))
                Text("Delete account", Modifier.padding(start = 8.dp), fontSize = 16.spotSp, fontWeight = FontWeight.Bold)
            }
        }
    }
    if (confirming) SpotConfirmDialog(Icons.Rounded.DeleteForever, "Delete your account?",
        "${username.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "Your account"} and everything in it will be gone for good.",
        "Delete", onDismiss = { confirming = false }, danger = true, confirmTag = "delete-account-confirm", onConfirm = ::confirmed)
}

@Composable
private fun DeletedItem(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SpotIconTile(icon, size = 32.dp, tint = SpotPalette.Muted, container = SpotPalette.Track)
        Text(text, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Ink)
    }
}

@Composable
private fun DeleteAccountPreview(hasPassword: Boolean, dark: Boolean = false) {
    SPOTAppTheme(dark = dark) {
        Column(Modifier.fillMaxSize().background(SpotPalette.Ivory)) {
            SpotScreenHeader("Delete account", onBack = {})
            DeleteAccountPage(hasPassword, "alexsantos", busy = false, onBusy = {}) { _, _ -> }
        }
    }
}

@Preview(name = "Delete account · password", widthDp = 360, heightDp = 728, fontScale = 1.15f)
@Composable private fun DeleteAccountPasswordPreview() = DeleteAccountPreview(hasPassword = true)

@Preview(name = "Delete account · Google · dark", widthDp = 360, heightDp = 728, fontScale = 1.15f)
@Composable private fun DeleteAccountGooglePreview() = DeleteAccountPreview(hasPassword = false, dark = true)

/** The signed-in account in a slim card; it opens Edit profile. */
@Composable
private fun AccountCard(account: SpotUiProfile?, uid: String, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = account != null, modifier = Modifier.fillMaxWidth().testTag("settings-account"),
        shape = RoundedCornerShape(22.dp), color = SpotPalette.Surface, border = BorderStroke(1.dp, SpotPalette.Line)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).background(SpotGoldGradient, CircleShape).padding(2.dp).background(SpotPalette.Surface, CircleShape).padding(2.dp)) {
                SpotAvatar(account?.profileImageUrl, "Your profile", Modifier.fillMaxSize(), uid)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(account?.displayName.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = SpotPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(account?.username?.takeIf { it.isNotBlank() }?.let { "@$it" }, account?.accountType?.label).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("Edit", Modifier.background(SpotPalette.GoldSoft, CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = SpotPalette.GoldInk)
        }
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(title.uppercase(), Modifier.padding(start = 6.dp, top = 10.dp), style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold, letterSpacing = .8.sp, color = SpotPalette.GoldInk)
}

/** One line per setting: no description, a value or count on the right, or a switch. */
@Composable
private fun SettingsLineRow(line: SettingsLine) {
    val switch = line.checked
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
        .then(if (switch != null) Modifier.toggleable(switch, line.enabled, Role.Switch, onValueChange = line.onToggle)
            else Modifier.clickable(onClick = line.onClick))
        .padding(start = 14.dp, end = if (switch != null) 14.dp else 8.dp, top = 8.dp, bottom = 8.dp)
        .testTag("setting-${line.title}"), verticalAlignment = Alignment.CenterVertically) {
        SpotIconTile(line.icon, size = 36.dp, container = SpotPalette.GoldWash)
        Text(line.title, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyLarge, color = SpotPalette.Ink,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        when {
            switch != null -> Switch(switch, onCheckedChange = null, enabled = line.enabled, colors = SwitchDefaults.colors(checkedThumbColor = Color.White,
                checkedTrackColor = SpotPalette.GoldDeep, uncheckedThumbColor = SpotPalette.Muted,
                uncheckedTrackColor = SpotPalette.Track, uncheckedBorderColor = SpotPalette.Line))
            line.badge > 0 -> Text(settingsCount(line.badge).orEmpty(), Modifier.background(SpotPalette.Heart, CircleShape)
                .defaultMinSize(minWidth = 22.dp).padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
            line.value != null -> Text(line.value, style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted, maxLines = 1)
        }
        if (switch == null) Icon(Icons.Rounded.ChevronRight, null, Modifier.padding(start = 2.dp).size(20.dp), tint = SpotPalette.Muted)
    }
}

/** A count for a row, or nothing when there are none. Lists load 50 at most. */
private fun settingsCount(count: Int): String? = when {
    count <= 0 -> null
    count >= 50 -> "50+"
    else -> count.toString()
}

/** Where new posts and stories start; a private account can't post publicly. */
private fun audienceSummary(prefs: SpotPreferences, private: Boolean): String {
    fun shown(value: String) = if (private && value == "public") "followers" else value
    val posts = shown(prefs.postAudience)
    return if (posts == shown(prefs.storyAudience)) audienceLabel(posts) else "Custom"
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    SpotCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = 1.dp, content = content)
}

@Composable
private fun SettingsDivider() = HorizontalDivider(Modifier.padding(start = 66.dp), color = SpotPalette.Line)

@Composable
private fun SettingsRow(title: String, icon: ImageVector, subtitle: String? = null, trailing: String? = null, danger: Boolean = false, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = action).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        SpotIconTile(icon, size = 38.dp, tint = if (danger) SpotPalette.Danger else SpotPalette.GoldDeep,
            container = if (danger) SpotPalette.Danger.copy(alpha = .08f) else SpotPalette.GoldSoft)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (danger) SpotPalette.Danger else SpotPalette.Ink)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted)
        }
        if (trailing != null) Text(trailing, Modifier.padding(end = 4.dp), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold, color = SpotPalette.GoldInk)
        if (!danger) Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = SpotPalette.Muted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudiencePreference(title: String, icon: ImageVector, value: String, private: Boolean, enabled: Boolean, change: (String) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = SpotPalette.GoldDeep)
            Text(title, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall, color = SpotPalette.Ink)
        }
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (if (private) listOf("followers", "private") else listOf("public", "followers", "private")).forEach { option ->
                // A private account can't post publicly, so "Public" shows as Followers.
                val selected = value == option || private && value == "public" && option == "followers"
                FilterChip(selected, { change(option) }, label = { Text(audienceLabel(option)) }, enabled = enabled,
                    leadingIcon = { Icon(when (option) { "public" -> Icons.Rounded.Public; "followers" -> Icons.Rounded.Group; else -> Icons.Rounded.Lock },
                        null, Modifier.size(16.dp)) },
                    shape = CircleShape, colors = FilterChipDefaults.filterChipColors(containerColor = SpotPalette.Surface, iconColor = SpotPalette.Muted,
                        selectedContainerColor = SpotPalette.GoldSoft, selectedLabelColor = SpotPalette.GoldInk, selectedLeadingIconColor = SpotPalette.GoldDeep),
                    border = FilterChipDefaults.filterChipBorder(enabled, selected, borderColor = SpotPalette.Line, selectedBorderColor = SpotPalette.GoldLine,
                        selectedBorderWidth = 1.dp))
            }
        }
    }
}

/** One look for every settings confirmation: icon, title, message and a pill action (red when it's a warning). */
@Composable
internal fun SavedPostsPage(profile: SpotUiProfile, onProfile: (SpotUiProfile) -> Unit) {
    var ids by remember { mutableStateOf(emptyList<String>()) }
    val posts = remember { mutableStateMapOf<String, PublishedPost?>() }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    var opened by remember { mutableStateOf<PublishedPost?>(null) }
    LifecycleDisposableEffect(profile.uid, retry) {
        val l = PrivacyRepository.shared.listenSaved({ ids = it; loaded = true }, { error = it })
        onDispose { l.remove() }
    }
    LifecycleDisposableEffect(ids, retry) {
        posts.keys.toList().filterNot { it in ids }.forEach { posts.remove(it) }
        val listeners = ids.map { id -> HomeRepository.listenPost(id, { posts[id] = it }, { posts[id] = null }) }
        onDispose { listeners.forEach { it.remove() } }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!loaded && error.isBlank()) item { SpotLoading(true) }
        if (error.isNotBlank()) item {
            SpotEmptyState(Icons.Rounded.CloudOff, "Saved posts couldn't load", message = error, actionLabel = "Retry",
                onAction = { error = ""; retry++ }, actionIcon = Icons.Rounded.Refresh)
        }
        if (loaded && ids.isEmpty()) item {
            SpotEmptyState(Icons.Rounded.BookmarkBorder, "No saved posts yet", message = "Tap the bookmark on any post to keep it here.")
        }
        items(ids, key = { it }) { id ->
            val post = posts[id]
            when {
                post != null -> Surface(onClick = { opened = post }, color = SpotPalette.Surface, shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, SpotPalette.Line), shadowElevation = 1.dp) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val media = post.media.firstOrNull()
                        if (media != null) Box(Modifier.size(66.dp).clip(RoundedCornerShape(12.dp)).background(SpotPalette.Track)) {
                            AsyncImage(media.previewUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            if (media.isVideo) Icon(Icons.Rounded.PlayCircle, "Video", Modifier.align(Alignment.Center).size(26.dp), tint = Color.White)
                        } else SpotIconTile(Icons.AutoMirrored.Rounded.Notes, size = 66.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SpotAvatar(post.author.profileImageUrl, null, Modifier.size(22.dp), post.author.uid)
                                Text(post.author.displayName, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.titleSmall,
                                    color = SpotPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(post.caption.ifBlank { "View post" }, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
                                color = SpotPalette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Rounded.Bookmark, "Saved", Modifier.size(20.dp), tint = SpotPalette.GoldDeep)
                    }
                }
                posts.containsKey(id) -> SpotCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = SpotPalette.Ivory, elevation = 0.dp) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        SpotIconTile(Icons.Rounded.HideImage, size = 40.dp, tint = SpotPalette.Muted, container = SpotPalette.Track)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("Post unavailable", style = MaterialTheme.typography.titleSmall, color = SpotPalette.Ink)
                            Text("It was removed or you can't view it.", style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted)
                        }
                    }
                }
                else -> SpotLoading(true)
            }
        }
    }
    opened?.let { ProfilePublishedPostViewer(it, onProfile, profile = profile) { opened = null } }
}
