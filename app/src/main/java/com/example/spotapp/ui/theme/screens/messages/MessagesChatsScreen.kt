package com.example.spotapp.ui.theme.screens.messages

import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.screens.discover.CommunityWindow

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.models.SpotRoute
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.ui.theme.screens.discover.CommunityDetails
import com.example.spotapp.ui.theme.screens.discover.eventDate
import com.example.spotapp.ui.theme.screens.home.ProfilePublishedPostViewer
import com.example.spotapp.ui.theme.screens.seeker.RequestDetails
import com.example.spotapp.ui.theme.models.SpotUiPost
import com.example.spotapp.ui.theme.navigation.SpotMainBottomNavigation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val Canvas get() = SpotPalette.Ivory
private val Ink get() = SpotPalette.Ink
private val Muted get() = SpotPalette.Muted
private val Line get() = SpotPalette.Line
private val Gold = Color(0xFFD1AD32)
private val GoldSoft get() = SpotPalette.GoldSoft
private val GoldInk get() = SpotPalette.GoldInk
private val Danger get() = SpotPalette.Danger

// Requests open as a page of their own, keyed like a conversation; conversation ids never contain '#'.
private const val REQUESTS_PAGE = "#requests"

@Composable
fun MessagesScreen(
    profile: SpotUiProfile, onNavigate: (SpotRoute) -> Unit, onBack: () -> Unit,
    initialTab: MessagesTab = MessagesTab.CHATS, startConversationWithName: String? = null,
    onStartConversationConsumed: () -> Unit = {}, startConversationWithUid: String? = null,
    onThreadVisibilityChanged: (Boolean) -> Unit = {}, onUnreadCountChanged: (Int) -> Unit = {},
    onProfileClick: (SpotUiProfile) -> Unit = {}, onOpenReels: (SpotUiPost) -> Unit = {},
    openConversationId: String? = null, onOpenConversationConsumed: () -> Unit = {},
    openRequests: Boolean = false,
    store: MessagesStore = remember(profile.uid) { SupabaseMessagesStore(profile.uid) }
) {
    var retry by remember { mutableIntStateOf(0) }
    val inbox by remember(store, retry) { store.inbox() }.collectAsStateWithLifecycle(MessagesInbox())
    val work by remember(store, retry) { store.workInbox() }.collectAsStateWithLifecycle(WorkInbox())
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var search by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var requestsOpen by rememberSaveable { mutableStateOf(openRequests) }
    var compose by remember { mutableStateOf(false) }
    var preselected by remember { mutableStateOf<ChatPerson?>(null) }
    // A conversation to start from Requests, handled like startConversationWithUid.
    var startWith by remember { mutableStateOf<String?>(null) }
    var openRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    var openEventId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmAnswer by remember { mutableStateOf<Pair<TalentRequest, String>?>(null) }
    // The chat held down in the list, and an option from its sheet that asks first ("block", "leave" or "remove").
    var optionsId by remember { mutableStateOf<String?>(null) }
    var confirmOption by remember { mutableStateOf<Pair<CampusConversation, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val all = inbox.conversations + inbox.requests
    val visible = inbox.conversations.filter { it.visible(inbox.preferences[it.id] ?: ChatPreferences()) }
    val unreadCount = inbox.unreadCount(store.uid)
    // People with SPOT open, minus you and anyone you blocked.
    val onlineNow by OnlinePresence.online.collectAsStateWithLifecycle()
    val online = onlineNow - inbox.blockedIds - store.uid
    // The date and place shown with each event chat.
    val eventIds = inbox.conversations.filter { it.communityKind == "event" }.map { it.communityId }.distinct().sorted()
    var events by remember(store) { mutableStateOf<Map<String, CampusEvent>>(emptyMap()) }
    LaunchedEffect(store, eventIds, retry) {
        if (eventIds.isNotEmpty()) try { events = store.events(eventIds) }
        catch (e: CancellationException) { throw e } catch (_: Exception) { /* Rows fall back to the chat's name. */ }
    }
    // Read selectedId here, not inside SideEffect: SideEffect reads aren't tracked, so opening or
    // closing a chat would otherwise never recompose this scope and the main navigation would stay visible.
    val threadOpen = selectedId != null || requestsOpen
    SideEffect {
        onThreadVisibilityChanged(threadOpen)
        onUnreadCountChanged(unreadCount)
    }
    DisposableEffect(Unit) { onDispose { onThreadVisibilityChanged(false) } }
    fun act(action: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try { withTimeout(60_000) { action() } }
            catch (_: TimeoutCancellationException) { failure = "That action took too long. Please reconnect and retry." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure = messagesError(e) }
            finally { busy = false }
        }
    }
    LaunchedEffect(store, retry) {
        try { store.syncProfile() } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Inbox renders the retry state. */ }
    }
    // Waits for the inbox, so an existing chat with this person opens instead of the new-conversation form.
    val startUid = startWith ?: startConversationWithUid
    LaunchedEffect(startUid, startConversationWithName, inbox.loading) {
        if ((startUid != null || startConversationWithName != null) && !inbox.loading) {
            try {
                val person = startUid?.let { store.person(it) }
                if (person == null) {
                    compose = true
                    // A display name is not an identity. Let the user select a verified directory record.
                } else {
                    val existing = all.firstOrNull { it.kind == "direct" && person.uid in it.partyIds && (it.invitedIds.isNotEmpty() || it.memberIds.size >= 2) }
                    if (existing != null) selectedId = existing.id else { preselected = person; compose = true }
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { failure = messagesError(e) }
            if (startWith != null) startWith = null else onStartConversationConsumed()
        }
    }
    // A tapped notification, or an event's or group's chat button, opens its chat once the inbox has it. A chat
    // that was only just started gets a moment to arrive; one this account can't see is ignored.
    val requestedChatReady = openConversationId != null && all.any { it.id == openConversationId }
    LaunchedEffect(openConversationId, inbox.loading, requestedChatReady) {
        val id = openConversationId ?: return@LaunchedEffect
        if (inbox.loading) return@LaunchedEffect
        if (requestedChatReady) {
            selectedId = id
            all.firstOrNull { it.id == id }?.let { chat -> MessagesTab.entries.firstOrNull(chat::inTab)?.let { tab = it } }
        } else delay(5_000)
        onOpenConversationConsumed()
    }
    // While a chat is on screen, its new messages don't also pop up as phone notifications.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(selectedId, lifecycleOwner) {
        val shown = selectedId
        val observer = LifecycleEventObserver { _, event ->
            PushNotifications.visibleConversationId = shown.takeIf { event.targetState.isAtLeast(Lifecycle.State.RESUMED) }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (PushNotifications.visibleConversationId == shown) PushNotifications.visibleConversationId = null
        }
    }
    // New messages from other chats pop up in SpotMessageBanner at the top of every screen.
    BackHandler(enabled = requestsOpen && selectedId == null) { requestsOpen = false }
    // Failures show as a notice at the bottom instead of interrupting with a pop-up.
    LaunchedEffect(failure) {
        if (failure.isNotBlank()) { val shown = failure; failure = ""; snackbar.showSnackbar(shown, "OK", duration = SnackbarDuration.Long) }
    }

    Scaffold(containerColor = Canvas, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        if (selectedId == null && !requestsOpen) SpotMainBottomNavigation(
            selectedRoute = SpotRoute.MESSAGES_CHATS, profile = profile, onNavigate = onNavigate,
            unreadMessagesCount = unreadCount)
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("messages-progress"), color = Gold, trackColor = GoldSoft)
            val actions = RequestActions(
                openChat = { selectedId = it.id }, openRequest = { openRequestId = it.id },
                respond = { request, answer -> confirmAnswer = request to answer }, message = { startWith = it },
                review = { application, accept -> act { DiscoverRepository.shared.reviewApplication(application.eventId, application.userId, accept) } },
                openEvent = { openEventId = it }, profile = onProfileClick, retry = { retry++ })
            AnimatedContent(targetState = selectedId ?: REQUESTS_PAGE.takeIf { requestsOpen }, modifier = Modifier.weight(1f), transitionSpec = {
                val direction = if (targetState != null) 1 else -1
                (fadeIn(tween(180)) + slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 8 * direction }) togetherWith
                    (fadeOut(tween(120)) + slideOutHorizontally(tween(220)) { -it / 10 * direction })
            }, label = "messages-screen") { id ->
                val chat = all.firstOrNull { it.id == id }
                when {
                    id == null -> MessagesList(inbox, work, visible, store.uid, tab, { tab = it }, search, { search = it },
                        { preselected = null; compose = true }, { selectedId = it.id }, { retry++ }, { requestsOpen = true }, events, online,
                        onOptions = { optionsId = it.id })
                    id == REQUESTS_PAGE -> RequestsPage(inbox, work, store.uid, busy, actions) { requestsOpen = false }
                    chat == null -> Column(Modifier.fillMaxSize().spotAmbientBackground(Canvas)) { TextButton({ selectedId = null }) { Text("Back to Messages") }; MessageEmpty(Icons.Rounded.Forum, "Opening conversation", inbox.error.ifBlank { "Waiting for your conversation to sync." }, "Retry", { retry++ }, loading = inbox.error.isBlank()) }
                    store.uid in chat.invitedIds -> InvitationPage(chat, store.uid, busy, { selectedId = null },
                        { act { store.respond(chat.id, true) } }, { act { store.respond(chat.id, false); selectedId = null } },
                        onMessage = { selectedId = null; startWith = chat.creatorId })
                    else -> key(chat.id) {
                        ConversationPage(chat, inbox.preferences[chat.id] ?: ChatPreferences(), inbox.blockedIds, store,
                            { selectedId = null }, { message -> scope.launch { snackbar.showSnackbar(message) } }, { selectedId = null },
                            profile, onProfileClick, onOpenReels, onOpenConversation = { selectedId = it },
                            online = chat.kind == "direct" && chat.otherUid(store.uid) in online)
                    }
                }
            }
        }
    }
    if (compose) NewConversationDialog(store, preselected, inbox.blockedIds, onDismiss = { compose = false }, onCreated = {
        compose = false; selectedId = it
    }, existing = inbox.conversations)
    work.talentRequests.firstOrNull { it.id == openRequestId }?.let { request ->
        RequestDetails(request, profile, { openRequestId = null }, onProfile = { openRequestId = null; onProfileClick(it) },
            onMessage = { openRequestId = null; startWith = it }, onChat = { openRequestId = null; selectedId = it })
    }
    openEventId?.let { id ->
        CommunityDetails(id, true, profile, { openEventId = null }, { openEventId = null; onProfileClick(it) }, { openEventId = null; selectedId = it })
    }
    inbox.conversations.firstOrNull { it.id == optionsId }?.let { chat ->
        val prefs = inbox.preferences[chat.id] ?: ChatPreferences()
        val blocked = chat.kind == "direct" && chat.otherUid(store.uid) in inbox.blockedIds
        ChatOptionsSheet(chat, prefs, store.uid, blocked, onDismiss = { optionsId = null }) { option ->
            optionsId = null
            when (option) {
                "pin" -> act { store.preferences(chat.id, mapOf("pinned" to !prefs.pinned)) }
                "read" -> act { store.preferences(chat.id, mapOf("readAt" to true)) }
                "unread" -> act { store.preferences(chat.id, mapOf("unread" to true)) }
                "mute" -> act { store.preferences(chat.id, mapOf("muted" to !prefs.muted)) }
                "profile" -> chat.people[chat.otherUid(store.uid)]?.let { onProfileClick(SpotUiProfile(it.uid, it.name, it.username, "", "", it.photo)) }
                else -> confirmOption = chat to option
            }
        }
    }
    confirmOption?.let { (chat, option) ->
        val name = chat.nameFor(store.uid, inbox.preferences[chat.id] ?: ChatPreferences())
        val blocked = chat.otherUid(store.uid) in inbox.blockedIds
        // The same wording as the chat's own info page.
        SpotConfirmDialog(when (option) { "block" -> Icons.Rounded.Block; "leave" -> Icons.Rounded.ExitToApp; else -> Icons.Rounded.Archive },
            when (option) { "block" -> if (blocked) "Unblock $name?" else "Block $name?"; "leave" -> "Leave collaboration?"; else -> "Remove from inbox?" },
            when (option) {
                "block" -> if (blocked) "You can message and see each other's posts again. Follows aren't restored." else "They can't message you, and you'll stop seeing each other's posts and stories. You'll also unfollow each other. Past messages stay."
                "leave" -> "You will lose access to this collaboration and its shared media."
                else -> "This hides the conversation for you. It returns when a new message arrives; other members keep their history."
            },
            when (option) { "block" -> if (blocked) "Unblock" else "Block"; "leave" -> "Leave"; else -> "Remove" },
            onDismiss = { confirmOption = null }, danger = !(option == "block" && blocked)) {
            confirmOption = null
            act {
                when (option) {
                    "block" -> store.block(chat.otherUid(store.uid), !blocked)
                    "leave" -> store.leave(chat.id)
                    else -> store.preferences(chat.id, mapOf("hiddenAt" to true))
                }
            }
        }
    }
    confirmAnswer?.let { (request, answer) ->
        val verb = if (answer == "accepted") "Accept" else "Decline"
        SpotConfirmDialog(if (answer == "accepted") Icons.Rounded.CheckCircle else Icons.Rounded.Cancel, "$verb this invitation?",
            if (answer == "accepted") "You'll join ${request.title} and its event chat. Both of you will see your answer."
                else "Both of you will see your answer. This can't be undone.", verb,
            onDismiss = { confirmAnswer = null }, danger = answer != "accepted") { confirmAnswer = null; act { WorkRepository.shared.respond(request, answer) } }
    }
}

@Composable
internal fun ChatAvatar(name: String, photo: String = "", group: Boolean = false, size: Int = 48, uid: String = "") {
    if (!group) SpotAvatar(photo, name, Modifier.size(size.dp), uid)
    else Box(Modifier.size(size.dp).clip(CircleShape).background(GoldSoft), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Groups, null, tint = GoldInk, modifier = Modifier.size((size * .42f).dp))
    }
}

@Composable
private fun CircleAction(icon: ImageVector, label: String, onClick: () -> Unit, gold: Boolean = false, tag: String = "", enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .92f else 1f, spring(stiffness = 550f), label = "message-action-press")
    IconButton(onClick, enabled = enabled, interactionSource = interaction, modifier = Modifier.size(48.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(CircleShape).background(if (gold) GoldSoft else SpotPalette.Surface.copy(alpha = .8f)).border(1.dp, Line, CircleShape).testTag(tag)) {
        Icon(icon, label, tint = if (enabled) if (gold) GoldInk else Ink else Muted.copy(alpha = .5f), modifier = Modifier.size(21.dp))
    }
}

@Composable
internal fun MessageEmpty(icon: ImageVector, title: String, message: String, action: String? = null, onAction: () -> Unit = {}, loading: Boolean = false) {
    SpotEmptyState(icon, title, message = message, actionLabel = action, onAction = onAction, loading = loading,
        actionIcon = if (action == "New conversation") Icons.Rounded.EditNote else null)
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = Line,
    focusedContainerColor = SpotPalette.Surface, unfocusedContainerColor = SpotPalette.Surface, cursorColor = GoldInk)

@Composable
private fun NewConversationDialog(store: MessagesStore, initial: ChatPerson?, blocked: Set<String>, onDismiss: () -> Unit,
    onCreated: (String) -> Unit, existing: List<CampusConversation>) {
    var query by rememberSaveable { mutableStateOf("") }
    var people by remember { mutableStateOf<List<ChatPerson>>(emptyList()) }
    var chosen by remember { mutableStateOf(listOfNotNull(initial)) }
    var group by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    val id = rememberSaveable { UUID.randomUUID().toString() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(query, retry) {
        loading = true; error = ""
        try { delay(250); people = store.people(query).filter { it.uid !in blocked } }
        catch (e: CancellationException) { throw e } catch (e: Exception) { error = messagesError(e) }
        finally { loading = false }
    }
    CommunityWindow("New conversation", onDismiss, saving) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).navigationBarsPadding().imePadding().padding(horizontal = 18.dp, vertical = 6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!group, { group = false; chosen = chosen.take(1) }, { Text("Chat") }, enabled = !saving)
                    FilterChip(group, { group = true }, { Text("Collaboration") }, enabled = !saving)
                }
                if (group) OutlinedTextField(title, { title = it.take(60) }, label = { Text("Collaboration name") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), singleLine = true, textStyle = MaterialTheme.typography.bodyMedium, colors = fieldColors(), enabled = !saving)
                OutlinedTextField(query, { query = it }, label = { Text("Find a name or username") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    modifier = Modifier.fillMaxWidth().testTag("people-search"), singleLine = true, textStyle = MaterialTheme.typography.bodyMedium, colors = fieldColors(), enabled = !saving)
                if (chosen.isNotEmpty()) Text("To: ${chosen.joinToString { it.name }}", style = MaterialTheme.typography.bodySmall, color = GoldInk, modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Gold) }
                    else if (error.isNotBlank()) item { Text(error, color = Danger, style = MaterialTheme.typography.bodySmall); TextButton({ retry++ }) { Text("Retry") } }
                    else if (people.isEmpty()) item { Text("No matching connections yet. People appear here after opening Messages on SPOT.", style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.padding(vertical = 18.dp)) }
                    items(people, key = { it.uid }) { person ->
                        val checked = person.uid in chosen.map { it.uid }
                        Surface(onClick = {
                            if (checked) chosen = chosen.filter { it.uid != person.uid }
                            else if (!group) chosen = listOf(person)
                            else if (chosen.size < 7) chosen = chosen + person
                        }, enabled = !saving, shape = RoundedCornerShape(14.dp), color = if (checked) GoldSoft else SpotPalette.Surface) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                ChatAvatar(person.name, person.photo, size = 40, uid = person.uid)
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text(person.name, style = MaterialTheme.typography.titleSmall)
                                    Text("@${person.username.removePrefix("@")}", style = MaterialTheme.typography.bodySmall, color = Muted)
                                }
                                Icon(if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, if (checked) "Selected" else "Select", tint = if (checked) GoldInk else Muted)
                            }
                        }
                    }
                }
                OutlinedTextField(note, { note = it.take(500) }, label = { Text("Invitation message (optional)") }, minLines = 2, maxLines = 3,
                    modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyMedium, colors = fieldColors(), enabled = !saving)
                Text("They choose whether to accept before joining the conversation.", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(vertical = 10.dp))
                Button(onClick = {
                    scope.launch {
                        saving = true; error = ""
                        try {
                            val found = if (!group) existing.firstOrNull { it.kind == "direct" && chosen.single().uid in it.partyIds && (it.invitedIds.isNotEmpty() || it.memberIds.size >= 2) } else null
                            val created = found?.id ?: withTimeout(45_000) { store.create(chosen, title, note, group, id) }
                            onCreated(created)
                        } catch (_: TimeoutCancellationException) { error = "Couldn't connect. Retry with the same invitation." }
                        catch (e: CancellationException) { throw e } catch (e: Exception) { error = messagesError(e) }
                        finally { saving = false }
                    }
                }, enabled = !saving && chosen.isNotEmpty() && (!group || title.isNotBlank()), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("create-conversation"),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = SpotPalette.OnGold)) {
                    if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = SpotPalette.OnGold) else Text("Start conversation")
                }
            }
    }
}

@Composable
private fun InvitationPage(chat: CampusConversation, uid: String, busy: Boolean, onBack: () -> Unit, onAccept: () -> Unit, onDecline: () -> Unit,
    onMessage: () -> Unit = {}) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().spotAmbientBackground(Canvas).verticalScroll(rememberScrollState())) {
        ThreadHeader(chat.nameFor(uid), "Invitation to ${if (chat.kind == "group") "collaborate" else "connect"}", chat, uid, onBack)
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${chat.people[chat.creatorId]?.name ?: "A campus connection"} invited you", style = MaterialTheme.typography.titleLarge, color = Ink)
            Surface(shape = RoundedCornerShape(20.dp), color = SpotPalette.Surface, border = BorderStroke(1.dp, Line)) {
                Text(chat.inviteNote.ifBlank { "Let's connect on SPOT." }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().padding(20.dp))
            }
            Text("Accept to see this conversation and exchange messages. Decline to remove the invitation.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            Button(onClick = onAccept, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = SpotPalette.OnGold)) { Text("Accept invitation") }
            OutlinedButton(onClick = onDecline, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Decline") }
            // A collaboration invitation can be talked over in a direct chat with whoever sent it.
            if (chat.kind == "group" && chat.creatorId != uid) TextButton(onClick = onMessage, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.ChatBubbleOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text("Message ${chat.people[chat.creatorId]?.name?.substringBefore(' ') ?: "them"} first")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationPage(chat: CampusConversation, prefs: ChatPreferences, blockedIds: Set<String>, store: MessagesStore,
    onBack: () -> Unit, notice: (String) -> Unit, onRemoved: () -> Unit,
    profile: SpotUiProfile? = null, onProfileClick: (SpotUiProfile) -> Unit = {}, onOpenReels: (SpotUiPost) -> Unit = {},
    onOpenConversation: (String) -> Unit = {}, online: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf("chat") }
    var retry by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf("") }
    var messagesLoading by remember { mutableStateOf(true) }
    val live by remember(chat.id, retry) {
        store.messages(chat.id).onEach { messagesLoading = false; loadError = "" }.catch { loadError = messagesError(it); messagesLoading = false; emit(emptyList()) }
    }.collectAsStateWithLifecycle(emptyList())
    var earlier by remember { mutableStateOf<List<CampusMessage>>(emptyList()) }
    var more by remember { mutableStateOf(true) }
    var loadingOlder by remember { mutableStateOf(false) }
    val hidden by remember(chat.id) { store.hiddenMessages(chat.id).catch { emit(emptySet()) } }.collectAsStateWithLifecycle(emptySet())
    // "loaded" keeps hidden messages so paging still starts from the oldest one fetched.
    val loaded = (earlier + live).distinctBy { it.id }.sortedWith(compareBy<CampusMessage> { it.createdAt }.thenBy { it.id })
    val messages = loaded.filter { it.id !in hidden }
    var options by remember { mutableStateOf<CampusMessage?>(null) }
    var unsendTarget by remember { mutableStateOf<CampusMessage?>(null) }
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    var draft by rememberSaveable(chat.id) { mutableStateOf("") }
    var draftId by rememberSaveable(chat.id) { mutableStateOf(UUID.randomUUID().toString()) }
    var mediaUri by rememberSaveable(chat.id) { mutableStateOf<String?>(null) }
    var mediaMime by rememberSaveable(chat.id) { mutableStateOf<String?>(null) }
    var uploaded by remember { mutableStateOf<ChatAttachment?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var actionError by remember { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var mediaPreview by remember { mutableStateOf<CampusMessage?>(null) }
    var attachmentMenu by remember { mutableStateOf(false) }
    // Blocking is between two people, so it only closes a direct chat.
    val blocked = chat.kind == "direct" && chat.otherUid(store.uid) in blockedIds
    val canSend = !blocked && chat.canMessage
    val list = rememberLazyListState()
    val name = chat.nameFor(store.uid, prefs)
    fun act(action: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true; actionError = ""
            try { withTimeout(45_000) { action() } }
            catch (_: TimeoutCancellationException) { actionError = "Couldn't connect. Please retry." }
            catch (e: CancellationException) { throw e } catch (e: Exception) { actionError = messagesError(e) }
            finally { busy = false }
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            mediaUri = uri.toString(); mediaMime = context.contentResolver.getType(uri); uploaded = null; draftId = UUID.randomUUID().toString()
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { File.createTempFile("chat-camera-", ".jpg", context.cacheDir).also { file -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } } }
                mediaUri = Uri.fromFile(file).toString(); mediaMime = "image/jpeg"; uploaded = null; draftId = UUID.randomUUID().toString()
            } catch (e: Exception) { actionError = "Couldn't prepare that photo. Please try again." }
        }
    }
    val owner = LocalLifecycleOwner.current
    val lifecycle by owner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    // "typing…" follows Activity status and "Seen" follows read receipts, both ways (data/ChatSignals.kt).
    var mySettings by remember { mutableStateOf<SpotPreferences?>(null) }
    LifecycleDisposableEffect(store.uid) {
        val listener = if (store is SupabaseMessagesStore) runCatching { PrivacyRepository.shared.listenPreferences({ mySettings = it }) }.getOrNull() else null
        onDispose { listener?.remove() }
    }
    val shareActivity = mySettings?.showActivity == true
    val receipts = mySettings?.readReceipts == true
    val signals = remember(chat.id) { if (store is SupabaseMessagesStore) ChatSignals(chat.id, store.uid) else null }
    var typing by remember(chat.id) { mutableStateOf(emptyMap<String, Long>()) }
    var typingSentAt by remember(chat.id) { mutableLongStateOf(0L) }
    var seenAt by remember(chat.id) { mutableLongStateOf(0L) }
    var seenCheck by remember(chat.id) { mutableIntStateOf(0) }
    LaunchedEffect(signals, blocked) {
        if (signals == null || blocked) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try { signals.run(onTyping = { typing = typing + (it to System.currentTimeMillis()) }, onRead = { seenCheck++ }) }
            catch (e: CancellationException) { throw e } catch (_: Exception) { /* Extras only: the chat works without them. */ }
        }
    }
    // "typing…" lasts 5 seconds after the last signal, and ends when their message arrives.
    LaunchedEffect(typing) { if (typing.isNotEmpty()) { delay(5_000); typing = typing.filterValues { System.currentTimeMillis() - it < 5_000 } } }
    LaunchedEffect(live.lastOrNull()?.id) { live.lastOrNull()?.senderId?.let { typing = typing - it } }
    val lastMine = messages.lastOrNull()?.takeIf { it.senderId == store.uid }
    LaunchedEffect(chat.id, seenCheck, lastMine?.id, receipts) {
        seenAt = if (chat.kind != "direct" || lastMine == null || !receipts) 0
            else try { store.seenAt(chat.id) } catch (e: CancellationException) { throw e } catch (_: Exception) { seenAt }
    }
    // At most one typing signal every 3 seconds.
    fun typed() {
        val now = System.currentTimeMillis()
        if (signals == null || !shareActivity || now - typingSentAt < 3_000) return
        typingSentAt = now
        scope.launch { signals.typing() }
    }
    LaunchedEffect(chat.updatedAt, lifecycle, page, loadError, live) {
        // Opening a chat you marked unread also clears that mark.
        if (lifecycle.isAtLeast(Lifecycle.State.RESUMED) && page == "chat" && loadError.isBlank() && live.isNotEmpty() &&
            (chat.updatedAt > prefs.readAt || prefs.markedUnread)) {
            try {
                store.preferences(chat.id, mapOf("readAt" to true))
                if (receipts) signals?.read()
            } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
        if (live.isNotEmpty()) messagesLoading = false
    }
    LaunchedEffect(live.lastOrNull()?.id) {
        if (messages.isNotEmpty() && search == null && (live.lastOrNull()?.senderId == store.uid || list.firstVisibleItemIndex >= (messages.size - 8).coerceAtLeast(0)))
            list.animateScrollToItem((messages.size - 1).coerceAtLeast(0) + 1)
    }
    BackHandler { if (busy) notice("Please wait for this action to finish.") else if (search != null) search = null else if (page == "report") page = "info" else if (page != "chat") page = "chat" else onBack() }
    if (attachmentMenu) ModalBottomSheet(onDismissRequest = { attachmentMenu = false }, containerColor = Canvas) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            InfoRow(Icons.Rounded.CameraAlt, "Camera", "Take a photo", onClick = { attachmentMenu = false; try { camera.launch(null) } catch (_: Exception) { actionError = "No camera app is available." } })
            InfoRow(Icons.Rounded.PhotoLibrary, "Photos & videos", "Up to 25 MB", onClick = { attachmentMenu = false; gallery.launch(arrayOf("image/*", "video/*")) })
        }
    }
    // The draft stays in the composer; the reason shows as a notice instead of a pop-up.
    LaunchedEffect(actionError) { if (actionError.isNotBlank()) { notice(actionError); actionError = "" } }
    if (dialog != null) {
        val current = dialog!!
        var text by remember(current) { mutableStateOf(if (current == "nickname") prefs.nickname else "") }
        // Unblocking and nicknames are gentle; everything else here is a warning.
        val destructive = current in listOf("leave", "remove") || current == "block" && !blocked
        val icon = when (current) { "nickname" -> Icons.Rounded.Edit; "block" -> Icons.Rounded.Block; "leave" -> Icons.Rounded.ExitToApp; else -> Icons.Rounded.Archive }
        SpotConfirmDialog(icon, when (current) {
                "nickname" -> "Nickname"; "block" -> if (blocked) "Unblock $name?" else "Block $name?"; "leave" -> "Leave collaboration?"; else -> "Remove from inbox?"
            }, when (current) {
                "nickname" -> "Only you see this nickname. Leave it blank to restore the original name."
                "block" -> if (blocked) "You can message and see each other's posts again. Follows aren't restored." else "They can't message you, and you'll stop seeing each other's posts and stories. You'll also unfollow each other. Past messages stay."
                "leave" -> "You will lose access to this collaboration and its shared media."
                else -> "This hides the conversation for you. It returns when a new message arrives; other members keep their history."
            }, when (current) { "nickname" -> "Save"; "block" -> if (blocked) "Unblock" else "Block"; "leave" -> "Leave"; else -> "Remove" },
            onDismiss = { dialog = null }, danger = destructive, busy = busy,
            content = if (current == "nickname") { {
                OutlinedTextField(text, { text = it.take(60) }, modifier = Modifier.fillMaxWidth().testTag("conversation-dialog-input"),
                    placeholder = { Text(chat.people[chat.otherUid(store.uid)]?.name ?: chat.title) },
                    singleLine = true, shape = RoundedCornerShape(16.dp), colors = fieldColors())
            } } else null) {
            act {
                when (current) {
                    "nickname" -> store.preferences(chat.id, mapOf("nickname" to text.trim()))
                    "block" -> store.block(chat.otherUid(store.uid), !blocked)
                    "leave" -> { store.leave(chat.id); onRemoved() }
                    else -> { store.preferences(chat.id, mapOf("hiddenAt" to true)); onRemoved() }
                }
                dialog = null
            }
        }
    }
    fun messageAction(action: suspend () -> Unit) {
        scope.launch {
            try { withTimeout(30_000) { action() } }
            catch (_: TimeoutCancellationException) { actionError = "Couldn't connect. Please retry." }
            catch (e: CancellationException) { throw e } catch (e: Exception) { actionError = messagesError(e) }
        }
    }
    options?.let { message ->
        ModalBottomSheet(onDismissRequest = { options = null }, containerColor = Canvas) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).testTag("message-options")) {
                if (message.text.isNotBlank()) InfoRow(Icons.Rounded.ContentCopy, "Copy", "Copy the message text", onClick = {
                    clipboard.setText(AnnotatedString(message.text)); options = null; notice("Message copied.")
                })
                if (message.canUnsend(store.uid)) InfoRow(Icons.AutoMirrored.Rounded.Undo, "Unsend", "Remove this message for everyone", danger = true, onClick = {
                    options = null; unsendTarget = message
                })
                InfoRow(Icons.Rounded.DeleteOutline, "Delete for you", "Others in this chat can still see it", danger = true, onClick = {
                    options = null; messageAction { store.deleteForMe(chat.id, message.id); notice("Message deleted for you.") }
                })
            }
        }
    }
    unsendTarget?.let { message ->
        SpotConfirmDialog(Icons.AutoMirrored.Rounded.Undo, "Unsend message?", "It will be removed for everyone in this chat. People may have already seen it.",
            "Unsend", onDismiss = { unsendTarget = null }, danger = true, confirmTag = "confirm-unsend") {
            unsendTarget = null; messageAction { store.unsend(chat.id, message.id) }
        }
    }
    if (mediaPreview != null) MediaDialog(mediaPreview!!, store, onDismiss = { mediaPreview = null })
    var sharedPost by remember { mutableStateOf<PublishedPost?>(null) }
    // Shared videos open in Reels, like they do from the home feed.
    fun openPost(post: PublishedPost) { if (post.media.firstOrNull()?.isVideo == true) onOpenReels(post.asUiPost()) else sharedPost = post }
    sharedPost?.let { post ->
        ProfilePublishedPostViewer(post, onProfileClick = onProfileClick, onOpenReels = onOpenReels, profile = profile) { sharedPost = null }
    }
    // The event an invitation in this chat is about.
    var invitedEvent by rememberSaveable { mutableStateOf<String?>(null) }
    invitedEvent?.let { id ->
        if (profile != null) CommunityDetails(id, true, profile, { invitedEvent = null }, { invitedEvent = null; onProfileClick(it) },
            { invitedEvent = null; onOpenConversation(it) })
    }
    AnimatedContent(page, transitionSpec = {
        val order = listOf("chat", "info", "media", "report")
        val direction = if (order.indexOf(targetState) > order.indexOf(initialState)) 1 else -1
        (fadeIn(tween(180)) + slideInHorizontally(tween(230, easing = FastOutSlowInEasing)) { direction * it / 6 }) togetherWith
            (fadeOut(tween(100)) + slideOutHorizontally(tween(200)) { -direction * it / 10 })
    }, label = "conversation-details") { current ->
        when (current) {
            "info" -> ConversationInfo(chat, prefs, store.uid, blocked, busy, { page = "chat" }, { dialog = "nickname" },
                { page = "chat"; search = "" }, { page = "media" }, { act { store.preferences(chat.id, mapOf("muted" to !prefs.muted)) } },
                { page = "report" }, { dialog = "block" }, { dialog = "remove" }, { dialog = "leave" })
            "report" -> ReportConversationPage(name, busy, { page = "info" }) { reason ->
                act { store.report(chat.id, reason); page = "info"; notice("Report submitted.") }
            }
            "media" -> SharedMediaPage(messages, { page = "info" }, { mediaPreview = it }, more && live.size >= 50, loadingOlder, {
                if (loaded.isNotEmpty()) scope.launch {
                    loadingOlder = true
                    try { val older = store.olderMessages(chat.id, loaded.first()); earlier = older + earlier; more = older.size == 50 }
                    catch (e: CancellationException) { throw e } catch (e: Exception) { actionError = messagesError(e) }
                    finally { loadingOlder = false }
                }
            })
            else -> Column(Modifier.fillMaxSize().spotAmbientBackground(Canvas) .imePadding()) {
                AnimatedContent(search != null, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) }, label = "message search") { searching ->
                if (!searching) ThreadHeader(name, when { blocked -> "Blocked"; chat.isCommunity -> "${if (chat.communityKind == "event") "Event" else "Group"} chat · ${memberCount(chat)}"
                    chat.memberIds.size < 2 -> if (chat.invitedIds.isEmpty()) "Invitation closed" else "Invitation pending"; chat.kind == "group" -> memberCount(chat); online -> "Active now"; else -> "Campus connection" }, chat, store.uid,
                    { if (busy) notice("Please wait for your message to finish.") else onBack() }, { page = "info" })
                else Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ search = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close search") }
                    SpotSearchField(search.orEmpty(), { search = it }, Modifier.weight(1f).testTag("thread-search"), "Search messages")
                }
                }
                val shown = messages.filter { search.isNullOrBlank() || it.text.contains(search.orEmpty(), true) }
                LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth().testTag("thread-messages"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item(key = "thread-start") {
                        if (loadError.isNotBlank()) MessageEmpty(Icons.Rounded.CloudOff, "Conversation unavailable", loadError, "Retry", { loadError = ""; messagesLoading = true; retry++ })
                        else if (messagesLoading) SpotLoading(true, SkeletonKind.ROWS)
                        else if (messages.isEmpty()) MessageEmpty(Icons.Rounded.WavingHand, "Say hello", if (canSend) "" else "You can message once the invitation is accepted.")
                        else if (live.size >= 50 && more) TextButton(onClick = {
                            scope.launch {
                                loadingOlder = true
                                try { val older = store.olderMessages(chat.id, loaded.first()); earlier = older + earlier; more = older.size == 50 }
                                catch (e: CancellationException) { throw e } catch (e: Exception) { actionError = messagesError(e) }
                                finally { loadingOlder = false }
                            }
                        }, enabled = !loadingOlder, modifier = Modifier.fillMaxWidth()) { if (loadingOlder) CircularProgressIndicator(Modifier.size(18.dp), color = Gold, strokeWidth = 2.dp) else Text("Load earlier messages") }
                    }
                    itemsIndexed(shown, key = { _, message -> message.id }) { index, message ->
                        Column(Modifier.animateItem(fadeInSpec = tween(180), placementSpec = spring(stiffness = 450f))) {
                            if (index == 0 || dayKey(shown[index - 1].createdAt) != dayKey(message.createdAt))
                                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) { Text(dayLabel(message.createdAt), style = MaterialTheme.typography.labelSmall, color = Muted) }
                            MessageBubble(message, message.senderId == store.uid, if (chat.kind == "group" && message.senderId != store.uid)
                                chat.people[message.senderId]?.name else null, store, { mediaPreview = message },
                                onLongPress = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); options = message }, onOpenPost = ::openPost,
                                onOpenEvent = { invitedEvent = it }, onOpenProfile = onProfileClick)
                        }
                    }
                    if (search == null && lastMine != null && seenAt >= lastMine.createdAt) item(key = "thread-seen") {
                        Text("Seen", Modifier.fillMaxWidth().padding(end = 6.dp).testTag("thread-seen"), style = MaterialTheme.typography.labelSmall,
                            color = Muted, textAlign = TextAlign.End)
                    }
                    val typingNames = if (!shareActivity) emptyList() else typing.keys.mapNotNull { if (chat.kind == "direct") name else chat.people[it]?.name }
                    if (search == null && typingNames.isNotEmpty()) item(key = "thread-typing") {
                        TypingBubble(if (chat.kind == "direct") "typing…" else "${typingNames.joinToString(", ")} ${if (typingNames.size == 1) "is" else "are"} typing…")
                    }
                    if (search != null && shown.isEmpty() && !messagesLoading) item { MessageEmpty(Icons.Rounded.Search, "No messages found", "Try another word or load earlier messages.") }
                }
                if (search == null) {
                    when {
                        !canSend -> Text(if (blocked) "You blocked this connection. Unblock them in conversation info." else if (chat.invitedIds.isEmpty()) "This invitation is closed. You can remove it from your inbox." else "Waiting for your invitation to be accepted.",
                            style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.fillMaxWidth().background(SpotPalette.Surface).padding(18.dp))
                        else -> Column(Modifier.background(SpotPalette.Surface)) {
                            if (busy) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Gold, trackColor = GoldSoft)
                            AnimatedVisibility(mediaUri != null, enter = expandVertically(tween(180)) + fadeIn(), exit = shrinkVertically(tween(160)) + fadeOut()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (mediaMime?.startsWith("video/") == true) Icon(Icons.Rounded.Videocam, null, tint = GoldInk)
                                else AsyncImage(mediaUri, "Selected attachment", Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                                Text(if (mediaMime?.startsWith("video/") == true) "Video ready to send" else "Photo ready to send", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                                IconButton({ mediaUri = null; mediaMime = null; uploaded = null; draftId = UUID.randomUUID().toString() }, enabled = !busy) { Icon(Icons.Rounded.Close, "Remove attachment") }
                            }
                            }
                            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                                CircleAction(Icons.Rounded.Add, "Add attachment", { attachmentMenu = true }, gold = true, enabled = !busy)
                                OutlinedTextField(draft, { draft = it.take(4000); if (uploaded == null) draftId = UUID.randomUUID().toString(); if (it.isNotBlank()) typed() }, placeholder = { Text("Message…", style = MaterialTheme.typography.bodyLarge) },
                                    enabled = !busy, maxLines = 4, textStyle = MaterialTheme.typography.bodyLarge, shape = RoundedCornerShape(22.dp), colors = fieldColors(),
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).testTag("message-draft"))
                                CircleAction(Icons.AutoMirrored.Rounded.Send, "Send message", {
                                    if (!busy) scope.launch {
                                        busy = true; actionError = ""; progress = 0f
                                        try {
                                            withTimeout(5 * 60_000) {
                                                if (mediaUri != null && uploaded == null) uploaded = store.upload(context, chat.id, draftId, Uri.parse(mediaUri), mediaMime) { progress = it * .85f }
                                                store.send(chat.id, ChatDraft(draftId, draft, uploaded))
                                            }
                                            draft = ""; mediaUri = null; mediaMime = null; uploaded = null; draftId = UUID.randomUUID().toString(); progress = 1f
                                        } catch (_: TimeoutCancellationException) { actionError = "Sending took too long. Your draft is still here; retry to check whether it was sent." }
                                        catch (e: CancellationException) { throw e } catch (e: Exception) { actionError = messagesError(e) }
                                        finally { busy = false }
                                    }
                                }, gold = true, tag = "send-message", enabled = !busy && (draft.isNotBlank() || mediaUri != null))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadHeader(title: String, subtitle: String, chat: CampusConversation, uid: String, onBack: () -> Unit, onInfo: (() -> Unit)? = null) {
    Surface(color = SpotPalette.Surface.copy(alpha = .96f), shadowElevation = 1.dp) {
        Box(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Box(Modifier.align(Alignment.CenterStart)) { CircleAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack) }
            Row(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 52.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
                .then(if (onInfo != null) Modifier.clickable(onClick = onInfo).testTag("conversation-info") else Modifier),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (onInfo != null) IconButton(onInfo, modifier = Modifier.align(Alignment.CenterEnd)) { Icon(Icons.Rounded.Info, "Conversation info", tint = Muted) }
        }
    }
}

/** Three pulsing dots in a bubble, with who is typing beside it. */
@Composable
private fun TypingBubble(label: String) {
    val pulse = rememberInfiniteTransition(label = "typing")
    Row(Modifier.testTag("thread-typing"), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.background(SpotPalette.Surface, RoundedCornerShape(18.dp)).border(1.dp, Line, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { dot ->
                val alpha by pulse.animateFloat(.3f, 1f, infiniteRepeatable(tween(450, delayMillis = dot * 150), RepeatMode.Reverse), label = "dot $dot")
                Box(Modifier.size(7.dp).graphicsLayer { this.alpha = alpha }.background(Muted, CircleShape))
            }
        }
        Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(message: CampusMessage, mine: Boolean, sender: String?, store: MessagesStore, onMedia: () -> Unit, onLongPress: () -> Unit,
    onOpenPost: (PublishedPost) -> Unit = {}, onOpenEvent: (String) -> Unit = {}, onOpenProfile: (SpotUiProfile) -> Unit = {}) {
    val sharedPostId = remember(message.text) { SharedPostLink.postId(message.text) }
    val sharedProfileId = remember(message.text) { SharedProfileLink.uid(message.text) }
    val storyId = remember(message.text) { StoryReplyLink.storyId(message.text) }
    val eventId = remember(message.text) { EventInviteLink.eventId(message.text) }
    val sharedEventId = remember(message.text) { SharedEventLink.eventId(message.text) }
    val text = when {
        storyId != null -> StoryReplyLink.note(message.text)
        eventId != null -> EventInviteLink.note(message.text)
        sharedEventId != null -> SharedEventLink.note(message.text)
        sharedProfileId != null -> SharedProfileLink.note(message.text)
        sharedPostId != null -> SharedPostLink.note(message.text)
        else -> message.text
    }
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (mine) 20.dp else 5.dp, bottomEnd = if (mine) 5.dp else 20.dp)
    // Long press opens the message options (copy / unsend / delete for you); haptics confirm the press.
    val press = Modifier.combinedClickable(remember { MutableInteractionSource() }, indication = null,
        onLongClickLabel = "Message options", onLongClick = onLongPress, onClick = {})
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 285.dp).fillMaxWidth(.88f), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            if (sender != null) Text(sender, style = MaterialTheme.typography.labelSmall, color = GoldInk, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
            if (message.unsent) Surface(shape = shape, color = Color.Transparent, border = BorderStroke(1.dp, Line), modifier = Modifier.testTag("message-unsent")) {
                Text(when { mine -> "You unsent a message"; sender != null -> "$sender unsent a message"; else -> "This message was unsent" },
                    style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = Muted,
                    modifier = press.padding(horizontal = 14.dp, vertical = 10.dp))
            }
            else Surface(shape = shape, color = if (mine) GoldSoft else SpotPalette.Surface, shadowElevation = .5.dp) {
                Column(press.testTag("message-${message.id}")) {
                    message.attachment?.let { AttachmentPreview(it, store, onMedia, onLongPress) }
                    if (storyId != null) StoryReplyCard(storyId, mine)
                    else if (eventId != null) EventInviteCard(eventId, mine, onOpenEvent, onLongPress)
                    else if (sharedEventId != null) EventInviteCard(sharedEventId, mine, onOpenEvent, onLongPress, invitation = false)
                    else if (sharedProfileId != null) SharedProfileCard(sharedProfileId, store, onOpenProfile, onLongPress)
                    else if (sharedPostId != null) SharedPostCard(sharedPostId, onOpenPost, onLongPress)
                    if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodyLarge, color = Ink, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp))
                    MessageLinks(text)
                }
            }
            Text("${clockTime(message.createdAt)}${if (mine && !message.unsent) if (message.pending) " · Sending" else " · Sent" else ""}",
                style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
        }
    }
}

/** A post shared into the chat: author, cover and caption, loaded live so edits and removals show up. */
@Composable
private fun SharedPostCard(postId: String, onOpen: (PublishedPost) -> Unit, onLongPress: () -> Unit) {
    var post by remember(postId) { mutableStateOf<PublishedPost?>(null) }
    var unavailable by remember(postId) { mutableStateOf(false) }
    LifecycleDisposableEffect(postId) {
        val listener = HomeRepository.listenPost(postId, { post = it; unavailable = it == null }, { unavailable = true })
        onDispose { listener.remove() }
    }
    val shape = RoundedCornerShape(16.dp)
    val current = post
    Column(Modifier.fillMaxWidth().padding(6.dp).clip(shape).background(SpotPalette.Surface).border(1.dp, Line, shape)
        .combinedClickable(enabled = current != null, onClickLabel = "Open post", onLongClickLabel = "Message options",
            onLongClick = onLongPress) { current?.let(onOpen) }.testTag("shared-post-$postId")) {
        when {
            unavailable -> Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                SpotIconTile(Icons.Rounded.HideImage, size = 36.dp, tint = Muted, container = SpotPalette.Track)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Post unavailable", style = MaterialTheme.typography.titleSmall, color = Ink)
                    Text("It was removed or you can't view it.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
            current == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Gold, strokeWidth = 2.dp)
            }
            else -> {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SpotAvatar(current.author.profileImageUrl, null, Modifier.size(28.dp), current.author.uid)
                    Text(current.author.displayName.ifBlank { current.author.username }, style = MaterialTheme.typography.labelLarge, color = Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                }
                current.media.firstOrNull()?.let { media ->
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(SpotPalette.Track)) {
                        AsyncImage(media.previewUrl, "Shared post", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (media.isVideo) Icon(Icons.Rounded.PlayCircle, "Video", Modifier.align(Alignment.Center).size(46.dp), tint = Color.White)
                        if (current.media.size > 1) Icon(Icons.Rounded.Collections, "${current.media.size} items",
                            Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp), tint = Color.White)
                    }
                }
                if (current.caption.isNotBlank()) Text(current.caption, style = MaterialTheme.typography.bodyMedium, color = Ink,
                    maxLines = if (current.media.isEmpty()) 5 else 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
            }
        }
    }
}

/**
 * The event a Spotter invited a creator to, or one someone shared ([invitation] false); tapping it opens the event.
 * An invited creator answers in Messages, under Requests.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EventInviteCard(eventId: String, mine: Boolean, onOpen: (String) -> Unit, onLongPress: () -> Unit, invitation: Boolean = true) {
    var event by remember(eventId) { mutableStateOf<CampusEvent?>(null) }
    var unavailable by remember(eventId) { mutableStateOf(false) }
    LaunchedEffect(eventId) {
        try { event = WorkRepository.shared.event(eventId); unavailable = event == null }
        catch (e: CancellationException) { throw e } catch (_: Exception) { unavailable = true }
    }
    val shape = RoundedCornerShape(16.dp)
    val current = event
    Row(Modifier.fillMaxWidth().padding(6.dp).clip(shape).background(SpotPalette.Surface).border(1.dp, Line, shape)
        .combinedClickable(enabled = current != null, onClickLabel = "Open event", onLongClickLabel = "Message options",
            onLongClick = onLongPress) { onOpen(eventId) }.padding(12.dp).testTag("event-invite-$eventId"),
        verticalAlignment = Alignment.CenterVertically) {
        SpotIconTile(Icons.Rounded.Event, size = 38.dp, container = GoldSoft)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(if (invitation) "Event invitation" else "Shared event", style = MaterialTheme.typography.labelMedium, color = GoldInk)
            Text(current?.title ?: if (unavailable) "Event unavailable" else "Loading…", style = MaterialTheme.typography.titleSmall, color = Ink,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (current != null) Text("${eventDate(current.startsAt)} · ${current.location}", style = MaterialTheme.typography.bodySmall, color = Muted,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (invitation && !mine && current != null) Text("Answer under Requests", style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** A profile someone shared: their photo, name and @username; tapping opens the profile. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SharedProfileCard(uid: String, store: MessagesStore, onOpen: (SpotUiProfile) -> Unit, onLongPress: () -> Unit) {
    var person by remember(uid) { mutableStateOf<ChatPerson?>(null) }
    var unavailable by remember(uid) { mutableStateOf(false) }
    LaunchedEffect(uid) {
        try { person = store.person(uid); unavailable = person == null }
        catch (e: CancellationException) { throw e } catch (_: Exception) { unavailable = true }
    }
    val shape = RoundedCornerShape(16.dp)
    val current = person
    Row(Modifier.fillMaxWidth().padding(6.dp).clip(shape).background(SpotPalette.Surface).border(1.dp, Line, shape)
        .combinedClickable(enabled = current != null, onClickLabel = "Open profile", onLongClickLabel = "Message options",
            onLongClick = onLongPress) { current?.let { onOpen(SpotUiProfile(it.uid, it.name, it.username, "", "", it.photo)) } }
        .padding(12.dp).testTag("shared-profile-$uid"), verticalAlignment = Alignment.CenterVertically) {
        SpotAvatar(current?.photo, current?.name, Modifier.size(42.dp), uid)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("Shared profile", style = MaterialTheme.typography.labelMedium, color = GoldInk)
            Text(current?.name ?: if (unavailable) "Profile unavailable" else "Loading…", style = MaterialTheme.typography.titleSmall, color = Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (current != null && current.username.isNotBlank()) Text("@${current.username}", style = MaterialTheme.typography.bodySmall,
                color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (current != null) Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
    }
}

/** The story a reply was sent from, as a small portrait thumbnail while the story can still be seen. */
@Composable
private fun StoryReplyCard(storyId: String, mine: Boolean) {
    var story by remember(storyId) { mutableStateOf<StoryItem?>(null) }
    var unavailable by remember(storyId) { mutableStateOf(false) }
    LaunchedEffect(storyId) {
        try { story = StoryRepository.shared.story(storyId)?.takeUnless { it.deleted }; unavailable = story == null }
        catch (e: CancellationException) { throw e } catch (_: Exception) { unavailable = true }
    }
    Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp).testTag("story-reply-$storyId"), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(40.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(8.dp)).background(SpotPalette.Track),
            contentAlignment = Alignment.Center) {
            val current = story
            if (current != null) AsyncImage(current.preview, "Story", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else if (unavailable) Icon(Icons.Rounded.HideImage, null, Modifier.size(18.dp), tint = Muted)
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text(if (mine) "You replied to their story" else "Replied to your story", style = MaterialTheme.typography.labelLarge, color = Ink)
            if (unavailable) Text("Story unavailable", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
private fun MessageLinks(text: String) {
    val links = remember(text) { Regex("https?://[^\\s<>]+").findAll(text).map { it.value.trimEnd('.', ',', ')') }.distinct().toList() }
    val handler = LocalUriHandler.current
    var error by remember { mutableStateOf(false) }
    links.forEach { link -> TextButton(onClick = { try { handler.openUri(link) } catch (_: Exception) { error = true } }, contentPadding = PaddingValues(horizontal = 14.dp)) {
        Icon(Icons.Rounded.OpenInNew, null, Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text(Uri.parse(link).host ?: "Open link", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    } }
    if (error) Text("No app could open this link.", style = MaterialTheme.typography.bodySmall, color = Danger, modifier = Modifier.padding(12.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AttachmentPreview(attachment: ChatAttachment, store: MessagesStore, onOpen: () -> Unit, onLongPress: () -> Unit) {
    val context = LocalContext.current
    var uri by remember(attachment.path) { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(attachment.path, retry) {
        error = false
        try { uri = store.mediaFile(context, attachment) } catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
    }
    Box(Modifier.fillMaxWidth().height(185.dp).clip(RoundedCornerShape(16.dp)).background(if (attachment.isVideo) Color(0xFF171717) else GoldSoft)
        .combinedClickable(onLongClick = onLongPress, onLongClickLabel = "Message options") { if (error) retry++ else if (uri != null) onOpen() },
        contentAlignment = Alignment.Center) {
        when {
            error -> Text("Tap to retry attachment", style = MaterialTheme.typography.bodySmall, color = if (attachment.isVideo) Color.White else GoldInk)
            uri == null -> CircularProgressIndicator(Modifier.size(26.dp), color = Gold)
            attachment.isVideo -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.PlayCircle, "Play video", tint = Color.White, modifier = Modifier.size(52.dp))
                Text("Video · Tap to play", style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
            else -> AsyncImage(uri, "Shared photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun MediaDialog(message: CampusMessage, store: MessagesStore, onDismiss: () -> Unit) {
    val attachment = message.attachment ?: return
    val context = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(attachment.path) { try { uri = store.mediaFile(context, attachment) } catch (e: CancellationException) { throw e } catch (e: Exception) { error = messagesError(e) } }
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (error.isNotBlank()) Text(error, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                else if (uri == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Gold)
                else if (attachment.isVideo) ChatVideo(uri!!, Modifier.fillMaxSize().padding(vertical = 64.dp))
                else AsyncImage(uri, "Shared photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                IconButton(onDismiss, Modifier.align(Alignment.TopEnd).padding(12.dp).background(Color.Black.copy(alpha = .6f), CircleShape)) { Icon(Icons.Rounded.Close, "Close media", tint = Color.White) }
            }
        }
    }
}

/**
 * The decrypted video plays at once with sound, in the same player as Reels: tap to pause, drag the timeline to
 * seek, Retry if it fails. The old VideoView only showed the first frame and its play controls never appeared.
 */
@Composable
private fun ChatVideo(uri: Uri, modifier: Modifier) {
    SpotVideo(uri.toString(), active = true, modifier = modifier.testTag("chat-video"), muted = false, scrubber = true, skip = true)
}

@Composable
private fun ConversationInfo(chat: CampusConversation, prefs: ChatPreferences, uid: String, blocked: Boolean, busy: Boolean,
    onBack: () -> Unit, nickname: () -> Unit, search: () -> Unit, media: () -> Unit, mute: () -> Unit,
    report: () -> Unit, block: () -> Unit, remove: () -> Unit, leave: () -> Unit) {
    val group = chat.kind == "group"
    val other = chat.people[chat.otherUid(uid)]
    // With a nickname set, the real name stays visible under it.
    val subtitle = if (group) memberCount(chat) else listOfNotNull(
        other?.name?.takeIf { prefs.nickname.isNotBlank() }, other?.username?.takeIf { it.isNotBlank() }?.let { "@$it" }
    ).joinToString(" · ").ifBlank { "Campus connection" }
    Column(Modifier.fillMaxSize().spotAmbientBackground(Canvas).verticalScroll(rememberScrollState())) {
        SpotScreenHeader("Conversation info", onBack = onBack)
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 4.dp, bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(116.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(GoldSoft, GoldSoft.copy(alpha = 0f))), CircleShape))
                Box(Modifier.size(98.dp).border(2.5.dp, SpotGoldGradient, CircleShape).padding(5.dp)) {
                    ChatAvatar(chat.nameFor(uid), other?.photo.orEmpty(), group, size = 88, uid = chat.otherUid(uid))
                }
            }
            Text(chat.nameFor(uid, prefs), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    chat.communityKind == "event" -> InfoPill("Event chat", Icons.Rounded.Event)
                    chat.isCommunity -> InfoPill("Group chat", Icons.Rounded.Groups)
                    else -> InfoPill(if (group) "Collaboration" else "Campus connection", if (group) Icons.Rounded.Groups else Icons.Rounded.School)
                }
                if (blocked) InfoPill("Blocked", Icons.Rounded.Block, danger = true)
                if (prefs.muted) InfoPill("Muted", Icons.Rounded.NotificationsOff)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction(Icons.Rounded.Search, "Search", search, Modifier.weight(1f))
            QuickAction(Icons.Rounded.PhotoLibrary, "Media", media, Modifier.weight(1f))
            QuickAction(Icons.Rounded.Edit, "Nickname", nickname, Modifier.weight(1f), enabled = !busy)
        }
        InfoSection("Notifications")
        InfoCard {
            InfoSwitchRow(Icons.Rounded.NotificationsOff, "Mute in-app alerts", "Pause new-message banners", prefs.muted, enabled = !busy, onToggle = mute)
        }
        if (group) {
            InfoSection("Members")
            // An event's or group's chat can hold far more people than fit here; its page lists them all.
            val listed = chat.partyIds.take(30)
            InfoCard { listed.forEachIndexed { index, personId ->
                val person = chat.people[personId]
                if (index > 0) HorizontalDivider(color = Line, modifier = Modifier.padding(start = 66.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChatAvatar(person?.name ?: "Campus connection", person?.photo.orEmpty(), size = 40, uid = personId)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(if (personId == uid) "You" else person?.name ?: "Campus connection", style = MaterialTheme.typography.titleSmall, color = Ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!person?.username.isNullOrBlank()) Text("@${person?.username}", style = MaterialTheme.typography.bodySmall, color = Muted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    when (personId) {
                        in chat.memberIds -> InfoPill("Joined")
                        in chat.invitedIds -> InfoPill("Invited", muted = true)
                        else -> InfoPill("Left", muted = true)
                    }
                }
            }
                if (chat.partyIds.size > listed.size) Text("and ${chat.partyIds.size - listed.size} more", style = MaterialTheme.typography.bodySmall, color = Muted,
                    modifier = Modifier.padding(start = 66.dp, top = 4.dp, bottom = 12.dp))
            }
        }
        InfoSection("Privacy & support")
        InfoCard {
            InfoRow(Icons.Rounded.Flag, "Report", "Describe inappropriate content or behavior", danger = true, enabled = !busy, onClick = report)
            HorizontalDivider(color = Line, modifier = Modifier.padding(start = 66.dp))
            // An event's or group's chat is left by leaving the event or group itself.
            if (!chat.isCommunity) {
                if (group) InfoRow(Icons.Rounded.ExitToApp, "Leave collaboration", "Stop participating in this group", danger = true, enabled = !busy, onClick = leave)
                else InfoRow(Icons.Rounded.Block, if (blocked) "Unblock" else "Block", "Stop messages and hide each other's posts", danger = true, enabled = !busy, onClick = block)
                HorizontalDivider(color = Line, modifier = Modifier.padding(start = 66.dp))
            }
            InfoRow(Icons.Rounded.Archive, "Remove from inbox", "Hide this conversation until a new message", danger = true, enabled = !busy, onClick = remove)
        }
        Spacer(Modifier.navigationBarsPadding().height(28.dp))
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    SpotCard(modifier, shape = RoundedCornerShape(20.dp), elevation = 1.dp) {
        Column(Modifier.fillMaxWidth().heightIn(min = 88.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(42.dp).background(SpotGoldGradient, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(21.dp), tint = SpotPalette.OnGold)
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun InfoPill(text: String, icon: ImageVector? = null, danger: Boolean = false, muted: Boolean = false) {
    val tint = when { danger -> Danger; muted -> Muted; else -> GoldInk }
    Row(Modifier.background(when { danger -> Danger.copy(alpha = .08f); muted -> SpotPalette.Track; else -> GoldSoft }, CircleShape)
        .padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(14.dp), tint = tint); Spacer(Modifier.width(5.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
    }
}

@Composable
private fun InfoSection(title: String) {
    Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = GoldInk,
        modifier = Modifier.padding(start = 26.dp, end = 26.dp, top = 24.dp, bottom = 8.dp))
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    SpotCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(22.dp), elevation = 1.dp, content = content)
}

/**
 * What holding a chat in the list offers, as in Messenger. [onOption] gets "pin", "read", "unread", "mute", "profile",
 * "block", "leave" or "remove"; the last three ask first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatOptionsSheet(chat: CampusConversation, prefs: ChatPreferences, uid: String, blocked: Boolean,
    onDismiss: () -> Unit, onOption: (String) -> Unit) {
    val direct = chat.kind == "direct"
    val other = chat.people[chat.otherUid(uid)]
    val name = chat.nameFor(uid, prefs)
    val unread = chat.unread(uid, prefs)
    // Opens at full height and scrolls, so the last options stay reachable at large text sizes.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Canvas) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).testTag("chat-options")) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (direct) SpotAvatar(other?.photo, name, Modifier.size(44.dp), other?.uid.orEmpty())
                else SpotIconTile(if (chat.communityKind == "event") Icons.Rounded.Event else Icons.Rounded.Groups, size = 44.dp, container = GoldSoft)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(when {
                        direct -> other?.username?.takeIf(String::isNotBlank)?.let { "@$it" } ?: "Chat"
                        chat.communityKind == "event" -> "Event chat"
                        chat.isCommunity -> "Group chat"
                        else -> "Collaboration"
                    }, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            SpotCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), elevation = 1.dp) {
                // The Events tab keeps event chats in date order, so they aren't pinned.
                if (chat.communityKind != "event") {
                    InfoRow(Icons.Rounded.PushPin, if (prefs.pinned) "Unpin" else "Pin to top",
                        if (prefs.pinned) "Back to its usual place" else "Keep it above other chats", chevron = false) { onOption("pin") }
                    HorizontalDivider(color = Line, modifier = Modifier.padding(start = 66.dp))
                }
                InfoRow(if (unread) Icons.Rounded.MarkChatRead else Icons.Rounded.MarkChatUnread, if (unread) "Mark as read" else "Mark as unread",
                    if (unread) "Clear the unread dot" else "Come back to it later", chevron = false) { onOption(if (unread) "read" else "unread") }
                HorizontalDivider(color = Line, modifier = Modifier.padding(start = 66.dp))
                InfoRow(if (prefs.muted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff, if (prefs.muted) "Unmute" else "Mute",
                    if (prefs.muted) "Get notified again" else "No notifications from it", chevron = false) { onOption("mute") }
                if (direct && other != null) {
                    HorizontalDivider(color = Line, modifier = Modifier.padding(start = 66.dp))
                    InfoRow(Icons.Rounded.AccountCircle, "View profile", "See ${other.name.substringBefore(' ')}'s posts") { onOption("profile") }
                }
            }
            Spacer(Modifier.height(12.dp))
            SpotCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), elevation = 1.dp) {
                // An event's or group's chat is left by leaving the event or group itself.
                if (!chat.isCommunity) {
                    if (direct) InfoRow(Icons.Rounded.Block, if (blocked) "Unblock" else "Block",
                        if (blocked) "Let them message you again" else "Stop their messages and posts", danger = true) { onOption("block") }
                    else InfoRow(Icons.Rounded.ExitToApp, "Leave collaboration", "Stop participating in it", danger = true) { onOption("leave") }
                    HorizontalDivider(color = Line, modifier = Modifier.padding(start = 66.dp))
                }
                InfoRow(Icons.Rounded.Archive, "Remove from inbox", "Hidden until a new message", danger = true) { onOption("remove") }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, subtitle: String, danger: Boolean = false, enabled: Boolean = true,
    chevron: Boolean = !danger, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        SpotIconTile(icon, size = 38.dp, tint = if (danger) Danger else SpotPalette.GoldDeep, container = if (danger) Danger.copy(alpha = .08f) else GoldSoft)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (danger) Danger else Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 2.dp))
        }
        if (chevron) Icon(Icons.Rounded.ChevronRight, null, tint = Muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun InfoSwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(checked, enabled, Role.Switch) { onToggle() }.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        SpotIconTile(icon, size = 38.dp, container = GoldSoft)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(checked, onCheckedChange = null, enabled = enabled, colors = SwitchDefaults.colors(checkedThumbColor = Color.White,
            checkedTrackColor = SpotPalette.GoldDeep, uncheckedThumbColor = Muted, uncheckedTrackColor = SpotPalette.Track, uncheckedBorderColor = Line))
    }
}

/** Why the member is reporting this conversation; the reason needs at least 5 characters. */
@Composable
private fun ReportConversationPage(name: String, busy: Boolean, onBack: () -> Unit, onSend: (String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().imePadding().testTag("report-page")) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, enabled = !busy)
            Text("Report conversation", style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.padding(start = 12.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Describe the content or behavior you're reporting in your conversation with $name.",
                style = MaterialTheme.typography.bodyMedium, color = Muted)
            OutlinedTextField(reason, { reason = it.take(1000) }, Modifier.fillMaxWidth().testTag("conversation-report-input"),
                placeholder = { Text("What happened?") }, supportingText = { Text("At least 5 characters") },
                minLines = 5, maxLines = 10, enabled = !busy, shape = RoundedCornerShape(16.dp), colors = fieldColors())
        }
        Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            SpotAuthButton("Send report", loading = busy, enabled = reason.trim().length >= 5, onClick = { onSend(reason) })
        }
    }
}

@Composable
private fun SharedMediaPage(messages: List<CampusMessage>, onBack: () -> Unit, onOpen: (CampusMessage) -> Unit, more: Boolean, loading: Boolean, loadMore: () -> Unit) {
    val shared = messages.filter { it.attachment != null || it.text.contains(Regex("https?://")) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Text("Photos, videos & links", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (shared.isEmpty()) item { MessageEmpty(Icons.Rounded.PhotoLibrary, "Nothing shared yet", "Photos, videos and links appear here after they're sent.") }
            items(shared, key = { it.id }) { message ->
                Surface(shape = RoundedCornerShape(18.dp), color = SpotPalette.Surface, border = BorderStroke(1.dp, Line), onClick = { if (message.attachment != null) onOpen(message) }) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (message.attachment?.isVideo == true) Icons.Rounded.PlayCircle else if (message.attachment != null) Icons.Rounded.Image else Icons.Rounded.Link, null, tint = GoldInk)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(message.attachment?.name?.ifBlank { "Photo" } ?: "Shared link", style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(dayLabel(message.createdAt), style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                        }
                        MessageLinks(message.text)
                    }
                }
            }
            if (more) item { TextButton(loadMore, enabled = !loading, modifier = Modifier.fillMaxWidth()) { if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = Gold, strokeWidth = 2.dp) else Text("Load earlier messages") } }
        }
    }
}

private fun memberCount(chat: CampusConversation) = "${chat.memberIds.size} ${if (chat.memberIds.size == 1) "member" else "members"}"
private fun clockTime(time: Long) = if (time == 0L) "Now" else SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(time))
private fun dayKey(time: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(time))
private fun dayLabel(time: Long) = if (dayKey(time) == dayKey(System.currentTimeMillis())) "Today" else SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(time))
internal fun shortTime(time: Long) = if (time == 0L) "Now" else if (dayKey(time) == dayKey(System.currentTimeMillis())) clockTime(time) else SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(time))
