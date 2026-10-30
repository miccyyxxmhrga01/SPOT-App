package com.example.spotapp.ui.theme.navigation

import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.PhotoLibrary
import com.example.spotapp.ui.theme.screens.discover.CommunityWindow
import com.example.spotapp.ui.theme.components.LifecycleDisposableEffect

import com.example.spotapp.data.AuthRepository
import com.example.spotapp.data.asProfileUi
import com.example.spotapp.data.PrivacyRepository
import com.example.spotapp.ui.theme.components.SpotLoading
import com.example.spotapp.ui.theme.components.SkeletonKind

import com.example.spotapp.ui.theme.components.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material3.AlertDialog
import com.example.spotapp.ui.theme.components.ProvideSpotVideo
import com.example.spotapp.ui.theme.components.LocalSpotVideoEnabled
import com.example.spotapp.data.SocialRepository
import com.example.spotapp.data.OnlinePresence
import com.example.spotapp.data.PushNotifications
import com.example.spotapp.data.ProfileRepository
import com.example.spotapp.data.SpotLinks
import com.example.spotapp.data.SupabaseMessagesStore
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.spotapp.data.SocialNotification
import com.example.spotapp.data.isPostVideoUrl
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding

import androidx.compose.material3.Scaffold

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

import com.example.spotapp.SearchScreen

import com.example.spotapp.data.HomeRepository
import com.example.spotapp.data.SpotUser

import com.example.spotapp.data.PublishedPost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import com.example.spotapp.ui.theme.models.SpotRoute
import com.example.spotapp.ui.theme.models.SpotUiPost
import com.example.spotapp.ui.theme.models.SpotUiProfile

import com.example.spotapp.ui.theme.screens.discover.DiscoverScreen
import com.example.spotapp.ui.theme.screens.home.SpotConnectedHomePage
import com.example.spotapp.ui.theme.screens.home.ProfilePublishedPostViewer

import com.example.spotapp.ui.theme.screens.messages.MessagesScreen
import com.example.spotapp.ui.theme.screens.messages.MessagesTab

import com.example.spotapp.ui.theme.screens.notification.NotificationsScreen
import com.example.spotapp.ui.theme.screens.post.NewPostScreen
import com.example.spotapp.ui.theme.screens.profile.UserProfileScreen
import com.example.spotapp.ui.theme.screens.reels.ReelsScreen
import com.example.spotapp.ui.theme.screens.settings.SettingsScreen

import kotlin.math.abs


private const val MAIN_TAB_ANIMATION_DURATION =
    320

private const val BOTTOM_BAR_SCROLL_THRESHOLD =
    18f


private data class ProfileOrigin(val route: SpotRoute, val profile: SpotUiProfile, val parent: SpotRoute)

private fun mainTabIndex(
    route: SpotRoute
): Int {

    return when (route) {

        SpotRoute.HOME ->
            0

        SpotRoute.DISCOVER ->
            1

        SpotRoute.MESSAGES_CHATS,
        SpotRoute.MESSAGES_GROUPS,
        SpotRoute.COLLAB_REQUESTS ->
            2

        SpotRoute.NOTIFICATIONS ->
            3

        SpotRoute.PROFILE ->
            4

        else ->
            0
    }
}


private fun usesMainBottomNavigation(
    route: SpotRoute
): Boolean {

    return when (route) {

        SpotRoute.HOME,
        SpotRoute.DISCOVER,
        SpotRoute.MESSAGES_CHATS,
        SpotRoute.MESSAGES_GROUPS,
        SpotRoute.COLLAB_REQUESTS,
        SpotRoute.NOTIFICATIONS,
        SpotRoute.PROFILE ->
            true

        else ->
            false
    }
}


@Composable
fun SpotNavigation(onLogout: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        ProvideSpotVideo { SpotNavigationContent(onLogout) }
        // Messages that arrive while SPOT is open show here, styled like the app, instead of as a phone notification.
        SpotMessageBanner(Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun SpotNavigationContent(onLogout: () -> Unit) {
    val savedScreens = rememberSaveableStateHolder()
    val profileHistory = remember { mutableStateListOf<ProfileOrigin>() }
    val socialScope = rememberCoroutineScope()
    var notificationCount by remember { mutableStateOf(0) }
    var notificationTarget by remember { mutableStateOf<String?>(null) }
    var notificationPost by remember { mutableStateOf<PublishedPost?>(null) }
    var notificationError by remember { mutableStateOf("") }
    // Where a post opened by id returns to: Notifications, or the screen a share link found SPOT on.
    var notificationFrom by remember { mutableStateOf(SpotRoute.NOTIFICATIONS) }
    var linkProblem by remember { mutableStateOf<String?>(null) }
    var reelReturnRoute by remember { mutableStateOf(SpotRoute.HOME) }
    var reelParentRoute by remember { mutableStateOf(SpotRoute.HOME) }
    var reelReturnProfile by remember { mutableStateOf<SpotUiProfile?>(null) }


    var route by remember {
        mutableStateOf(
            SpotRoute.HOME
        )
    }


    var previousRoute by remember {
        mutableStateOf(
            SpotRoute.HOME
        )
    }


    val signedInProfile = remember {
        SpotUiProfile(AuthRepository.currentUid().orEmpty(), AuthRepository.currentUserDisplayName().ifBlank { "Your profile" },
            "", "", "", AuthRepository.currentUserPhotoUrl())
    }
    var currentProfile by remember {
        mutableStateOf(
            signedInProfile
        )
    }


    var selectedProfile by remember {
        mutableStateOf(
            signedInProfile
        )
    }


    var homePost by remember {
        mutableStateOf(
            SpotUiPost("home-draft", signedInProfile, "", "", emptyList(), aiCaption = false, aiTagged = false)
        )
    }


    var storyImageUri by remember {
        mutableStateOf<String?>(
            null
        )
    }


    var reelPosts by remember {
        mutableStateOf<List<SpotUiPost>>(
            emptyList()
        )
    }


    var initialReelPostId by remember {
        mutableStateOf<String?>(
            null
        )
    }


    var messageTargetUid by remember {
        mutableStateOf<String?>(
            null
        )
    }


    // A conversation an event or group page asked to open; Messages clears it once the chat is shown.
    var chatTarget by remember { mutableStateOf<String?>(null) }
    var workPage by rememberSaveable { mutableStateOf<String?>(null) }
    // The Create button: a story opens on Home's story row; an event opens its editor over the current screen.
    var storyRequest by remember { mutableStateOf(false) }
    var creatingEvent by rememberSaveable { mutableStateOf(false) }
    var shownEvent by rememberSaveable { mutableStateOf<String?>(null) }
    var inviteTarget by remember { mutableStateOf<SpotUiProfile?>(null) }
    var profileDetailOpen by remember { mutableStateOf(false) }
    var messagesDetailOpen by remember { mutableStateOf(false) }
    var messagesUnreadCount by remember { mutableStateOf(0) }
    // Set only when a Message button opened Messages, so system Back returns there instead of leaving SPOT.
    var messagesReturnRoute by remember { mutableStateOf<SpotRoute?>(null) }

    LifecycleDisposableEffect(currentProfile.uid) {
        val listener = SocialRepository.shared.listenUnread { notificationCount = it }
        onDispose { listener.remove() }
    }

    // Keeps the Messages badge live on every tab; while Messages is open it reports its own count.
    val lifecycleOwner = LocalLifecycleOwner.current
    val onMessages = route == SpotRoute.MESSAGES_CHATS || route == SpotRoute.MESSAGES_GROUPS || route == SpotRoute.COLLAB_REQUESTS
    LaunchedEffect(currentProfile.uid, onMessages) {
        if (onMessages || currentProfile.uid.isBlank()) return@LaunchedEffect
        val store = SupabaseMessagesStore(currentProfile.uid)
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            store.inbox().collect { inbox -> if (!inbox.loading && inbox.error.isBlank()) messagesUnreadCount = inbox.unreadCount(store.uid) }
        }
    }

    // Who's online: this phone says so only while SPOT is on screen, and only if its member shares their activity.
    var showActivity by remember(currentProfile.uid) { mutableStateOf<Boolean?>(null) }
    LifecycleDisposableEffect(currentProfile.uid) {
        val listener = if (currentProfile.uid.isBlank()) null
            else PrivacyRepository.shared.listenPreferences({ showActivity = it.showActivity })
        onDispose { listener?.remove() }
    }
    LaunchedEffect(currentProfile.uid, showActivity) {
        val share = showActivity ?: return@LaunchedEffect
        if (currentProfile.uid.isBlank()) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                try { OnlinePresence.run(currentProfile.uid, share); break }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { kotlinx.coroutines.delay(15_000) }
            }
        }
    }

    // Message notifications: Android 13+ asks for permission, and this phone's address is saved for the account.
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(currentProfile.uid) {
        if (currentProfile.uid.isBlank()) return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        try { PushNotifications.register() } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Tried again on the next launch. */ }
    }
    val notificationChat by PushNotifications.openConversation.collectAsStateWithLifecycle()

    var bottomNavigationVisible by remember {
        mutableStateOf(
            true
        )
    }


    val bottomNavigationScrollConnection =
        remember {

            object : NestedScrollConnection {

                private var accumulatedScroll =
                    0f

                private var lastDirection =
                    0


                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {

                    val verticalScroll =
                        available.y


                    if (verticalScroll == 0f) {
                        return Offset.Zero
                    }


                    val direction =
                        if (verticalScroll < 0f) {

                            -1

                        } else {

                            1
                        }


                    if (direction != lastDirection) {

                        accumulatedScroll =
                            0f

                        lastDirection =
                            direction
                    }


                    accumulatedScroll +=
                        abs(
                            verticalScroll
                        )


                    if (
                        accumulatedScroll >=
                        BOTTOM_BAR_SCROLL_THRESHOLD
                    ) {

                        bottomNavigationVisible =
                            direction > 0

                        accumulatedScroll =
                            0f
                    }


                    return Offset.Zero
                }
            }
        }


    LaunchedEffect(
        route
    ) {

        bottomNavigationVisible =
            true
    }


    LifecycleDisposableEffect(
        Unit
    ) {

        val listener =
            HomeRepository
                .listenToCurrentUser {
                        signedInUser ->


                    if (signedInUser != null) {

                        val mappedProfile =
                            signedInUser
                                .toFrontendProfile()


                        currentProfile =
                            mappedProfile


                        if (
                            selectedProfile.uid ==
                            signedInProfile.uid
                        ) {

                            selectedProfile =
                                mappedProfile
                        }
                    }
                }


        onDispose {

            listener?.remove()
        }
    }


    LaunchedEffect(
        currentProfile
    ) {

        if (
            homePost.id ==
            "home-draft"
        ) {

            homePost =
                homePost.copy(

                    user =
                        currentProfile,

                    category =
                        currentProfile
                            .talent
                            .ifBlank {

                                "Talent"
                            }
                )
        }
    }


    fun openProfile(
        profile: SpotUiProfile,
        from: SpotRoute
    ) {

        profileHistory.add(ProfileOrigin(from, selectedProfile, previousRoute))

        selectedProfile =
            profile

        previousRoute =
            from

        route =
            SpotRoute.PROFILE

        bottomNavigationVisible =
            true
    }


    fun openReels(post: SpotUiPost, from: SpotRoute) {
        reelReturnRoute = from
        reelParentRoute = previousRoute
        reelReturnProfile = selectedProfile
        previousRoute = from
        initialReelPostId = post.id
        reelPosts = listOf(post)
        route = SpotRoute.REELS
    }

    /** Opens a chat with [uid] from a Message button; system Back then returns to [from]. */
    fun openMessages(uid: String, from: SpotRoute) {
        messageTargetUid = uid
        previousRoute = from
        messagesReturnRoute = from
        route = SpotRoute.MESSAGES_CHATS
    }

    /** Opens an existing conversation, such as an event's or group's chat; system Back then returns to [from]. */
    fun openChat(id: String, from: SpotRoute) {
        chatTarget = id
        previousRoute = from
        messagesReturnRoute = from
        route = SpotRoute.MESSAGES_CHATS
    }


    fun navigate(destination: SpotRoute) {
        messagesReturnRoute = null
        if (usesMainBottomNavigation(destination)) profileHistory.clear()

        when (destination) {

            SpotRoute.PROFILE -> {

                if (
                    route ==
                    SpotRoute.PROFILE &&
                    selectedProfile.uid ==
                    currentProfile.uid
                ) {

                    return
                }


                if (
                    route !=
                    SpotRoute.PROFILE
                ) {

                    previousRoute =
                        route
                }


                selectedProfile =
                    currentProfile

                route =
                    SpotRoute.PROFILE
            }


            SpotRoute.SEARCH,
            SpotRoute.SETTINGS,
            SpotRoute.MESSAGES_CHATS,
            SpotRoute.MESSAGES_GROUPS,
            SpotRoute.COLLAB_REQUESTS,
            SpotRoute.NEW_POST -> {

                if (
                    destination ==
                    route
                ) {

                    return
                }


                previousRoute =
                    route

                route =
                    destination
            }


            SpotRoute.REELS -> {

                if (
                    route ==
                    SpotRoute.REELS
                ) {

                    return
                }


                previousRoute =
                    route

                route =
                    SpotRoute.REELS
            }


            else -> {

                if (
                    destination ==
                    route
                ) {

                    return
                }


                route =
                    destination
            }
        }


        bottomNavigationVisible =
            true
    }

    // A tapped message notification goes to Messages, which opens the chat and clears the request.
    LaunchedEffect(notificationChat) { if (notificationChat != null) navigate(SpotRoute.MESSAGES_CHATS) }
    // A tapped activity notification (someone joined your event, answered your invitation…) goes to Notifications.
    val notificationActivity by PushNotifications.openActivity.collectAsStateWithLifecycle()
    LaunchedEffect(notificationActivity) {
        if (notificationActivity) { navigate(SpotRoute.NOTIFICATIONS); PushNotifications.openActivity.value = false }
    }
    // A tapped share link opens its post, event or profile over the current screen.
    val sharedLink by SpotLinks.pending.collectAsStateWithLifecycle()
    LaunchedEffect(sharedLink) {
        val link = sharedLink ?: return@LaunchedEffect
        // Cleared only once handled: clearing it first restarts this effect and cancels the profile's load.
        try {
            when (link.kind) {
                SpotLinks.Kind.POST -> { notificationFrom = route; notificationTarget = link.id }
                SpotLinks.Kind.EVENT -> shownEvent = link.id
                SpotLinks.Kind.PROFILE -> {
                    val user = try { ProfileRepository.shared.profile(link.id) }
                        catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                    if (user != null) openProfile(user.asProfileUi(), route)
                    else linkProblem = "This profile was removed, or SPOT couldn't reach it. Check your connection and try the link again."
                }
            }
        } finally {
            if (SpotLinks.pending.value == link) SpotLinks.pending.value = null
        }
    }
    LaunchedEffect(notificationTarget) { if (notificationTarget == null) notificationFrom = SpotRoute.NOTIFICATIONS }
    linkProblem?.let { problem ->
        CommunityWindow("Profile", { linkProblem = null }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                SpotEmptyState(Icons.Rounded.PersonOff, "Profile unavailable", message = problem,
                    actionLabel = "Close", onAction = { linkProblem = null })
            }
        }
    }


    fun create(action: SpotCreateAction) {
        when (action) {
            SpotCreateAction.POST -> navigate(SpotRoute.NEW_POST)
            SpotCreateAction.STORY -> { storyRequest = true; navigate(SpotRoute.HOME) }
            SpotCreateAction.EVENT -> creatingEvent = true
        }
    }
    // Notifications open from Home's bell, so Back returns there.
    BackHandler(enabled = route == SpotRoute.NOTIFICATIONS) { navigate(SpotRoute.HOME) }

    fun backFromProfile() {
        val origin = profileHistory.removeLastOrNull()
        if (origin != null) { selectedProfile = origin.profile; route = origin.route; previousRoute = origin.parent }
        else route = previousRoute
    }
    BackHandler(enabled = route == SpotRoute.PROFILE && selectedProfile.uid != currentProfile.uid) { backFromProfile() }
    // An open conversation handles Back itself; this covers the inbox.
    BackHandler(enabled = route == SpotRoute.MESSAGES_CHATS && messagesReturnRoute != null && !messagesDetailOpen) {
        route = messagesReturnRoute ?: route
        messagesReturnRoute = null
    }
    notificationTarget?.let { id ->
        LifecycleDisposableEffect(id) {
            notificationPost = null; notificationError = ""
            val listener = HomeRepository.listenPost(id, { item ->
                if (item == null) notificationError = "This post is no longer available."
                else if (item.media.firstOrNull()?.isVideo == true) {
                    notificationTarget = null; openReels(item.asUiPost(), notificationFrom)
                } else notificationPost = item
            }, { notificationError = it })
            onDispose { listener.remove() }
        }
        if (notificationPost != null) ProfilePublishedPostViewer(notificationPost!!,
            onProfileClick = { openProfile(it, notificationFrom) },
            onOpenReels = { openReels(it, notificationFrom) }, profile = currentProfile,
            onClose = { notificationTarget = null })
        // Opens as a page right away: a spinner while the post loads, or why it can't be shown.
        else CommunityWindow("Post", { notificationTarget = null }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (notificationError.isBlank()) SpotEmptyState(Icons.Rounded.PhotoLibrary, "Opening post…", loading = true)
                else SpotEmptyState(Icons.Rounded.HideImage, "Post unavailable", message = notificationError,
                    actionLabel = "Close", onAction = { notificationTarget = null })
            }
        }
    }

    // Creating an event from the Create button, then its page.
    if (creatingEvent) com.example.spotapp.ui.theme.screens.discover.CommunityEditor(true, currentProfile,
        close = { creatingEvent = false }, created = { id -> creatingEvent = false; shownEvent = id })
    shownEvent?.let { id ->
        com.example.spotapp.ui.theme.screens.discover.CommunityDetails(id, true, currentProfile, close = { shownEvent = null },
            onProfile = { shownEvent = null; openProfile(it, route) }, onChat = { shownEvent = null; openChat(it, route) })
    }

    workPage?.let { page ->
        com.example.spotapp.ui.theme.screens.seeker.TalentWorkHub(currentProfile, page,
            close = { workPage = null },
            onProfile = { workPage = null; openProfile(it, route) },
            onMessage = { workPage = null; openMessages(it, route) },
            onChat = { workPage = null; openChat(it, route) })
    }
    inviteTarget?.let { person ->
        com.example.spotapp.ui.theme.screens.seeker.EventInviteDialog(currentProfile, person,
            close = { inviteTarget = null },
            onMessage = { inviteTarget = null; openMessages(it, route) })
    }

    if (
        usesMainBottomNavigation(
            route
        )
    ) {

        val viewingOwnProfile =
            selectedProfile.uid ==
                    currentProfile.uid


        val selectedBottomRoute =
            if (
                route ==
                SpotRoute.PROFILE &&
                !viewingOwnProfile &&
                usesMainBottomNavigation(
                    previousRoute
                )
            ) {

                previousRoute

            } else {

                route
            }


        val glass = rememberSpotGlassState()
        val mainContentInset = if (messagesDetailOpen || profileDetailOpen) 0.dp
            // The bar is 70dp with 10dp around it, and Create rises above it.
            else 106.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        CompositionLocalProvider(LocalSpotGlass provides glass, LocalSpotBottomContentPadding provides mainContentInset) {
        SpotScreenEntrance(Modifier.fillMaxSize()) {
        Scaffold(

            bottomBar = {

                SpotMainBottomNavigation(

                    selectedRoute =
                        selectedBottomRoute,

                    profile =
                        currentProfile,

                    visible = bottomNavigationVisible,
                    hidden = messagesDetailOpen || profileDetailOpen,
                    unreadMessagesCount = messagesUnreadCount,
                    onCreate = ::create,

                    onNavigate = {
                            destination ->

                        navigate(
                            destination
                        )
                    }
                )
            }

        ) {
                innerPadding ->


            AnimatedContent(

                targetState =
                    route,

                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding(),
                            start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                            end = innerPadding.calculateEndPadding(LocalLayoutDirection.current))
                        .consumeWindowInsets(PaddingValues(top = innerPadding.calculateTopPadding()))
                        .spotGlassSource(glass)
                        .nestedScroll(
                            bottomNavigationScrollConnection
                        ),

                transitionSpec = {

                    val oldIndex =
                        mainTabIndex(
                            initialState
                        )


                    val newIndex =
                        mainTabIndex(
                            targetState
                        )


                    if (
                        newIndex >
                        oldIndex
                    ) {

                        slideInHorizontally(

                            animationSpec =
                                tween(MAIN_TAB_ANIMATION_DURATION, easing = FastOutSlowInEasing),

                            initialOffsetX = {
                                    width ->

                                width / 16
                            }

                        ) + fadeIn(tween(260, easing = FastOutSlowInEasing)) togetherWith
                            slideOutHorizontally(

                                    animationSpec =
                                        tween(MAIN_TAB_ANIMATION_DURATION, easing = FastOutSlowInEasing),

                                    targetOffsetX = {
                                            width ->

                                        -width / 16
                                    }
                                ) + fadeOut(tween(210, easing = FastOutSlowInEasing))

                    } else {

                        slideInHorizontally(

                            animationSpec =
                                tween(MAIN_TAB_ANIMATION_DURATION, easing = FastOutSlowInEasing),

                            initialOffsetX = {
                                    width ->

                                -width / 16
                            }

                        ) + fadeIn(tween(260, easing = FastOutSlowInEasing)) togetherWith
                            slideOutHorizontally(

                                    animationSpec =
                                        tween(MAIN_TAB_ANIMATION_DURATION, easing = FastOutSlowInEasing),

                                    targetOffsetX = {
                                            width ->

                                        width / 16
                                    }
                                ) + fadeOut(tween(210, easing = FastOutSlowInEasing))
                    }
                },

                label =
                    "MainNavigationSlide"

            ) {
                    currentRoute ->


                savedScreens.SaveableStateProvider(if (currentRoute == SpotRoute.PROFILE) "profile:${selectedProfile.uid}" else currentRoute.name) {
                    CompositionLocalProvider(
                        LocalSpotBottomNavigationSuppressed provides true,
                        LocalSpotVideoEnabled provides (currentRoute == route && notificationTarget == null && workPage == null && inviteTarget == null &&
                            !creatingEvent && shownEvent == null)
                    ) {

                    when (currentRoute) {

                        SpotRoute.HOME -> {

                            SpotConnectedHomePage(
                                onWorkOpen = { workPage = it },
                                notificationCount = notificationCount,
                                onNotifications = { navigate(SpotRoute.NOTIFICATIONS) },
                                createStory = storyRequest,
                                onCreateStoryHandled = { storyRequest = false },
                                onChat = { openChat(it, SpotRoute.HOME) },

                                profile =
                                    currentProfile,

                                post =
                                    homePost,

                                storyImageUri =
                                    storyImageUri,

                                showBottomNavigation =
                                    false,

                                onPostChanged = {
                                        updatedPost ->

                                    homePost =
                                        updatedPost
                                },

                                onStoryChanged = {
                                        uri ->

                                    storyImageUri =
                                        uri
                                },

                                onNavigate =
                                    ::navigate,

                                onProfileClick = {
                                        profile ->

                                    openProfile(

                                        profile =
                                            profile,

                                        from =
                                            SpotRoute.HOME
                                    )
                                },

                                onOpenReels = {
                                        post ->

                                    openReels(

                                        post =
                                            post,

                                        from =
                                            SpotRoute.HOME
                                    )
                                }
                            )
                        }


                        SpotRoute.DISCOVER -> {

                            DiscoverScreen(
                                onMessage = { openMessages(it, SpotRoute.DISCOVER) },
                                onChat = { openChat(it, SpotRoute.DISCOVER) },
                                onWorkOpen = { workPage = it },

                                profile =
                                    currentProfile,

                                showBottomNavigation =
                                    false,

                                onNavigate =
                                    ::navigate,

                                onProfileClick = {
                                        profile ->

                                    openProfile(

                                        profile =
                                            profile,

                                        from =
                                            SpotRoute.DISCOVER
                                    )
                                },

                                onOpenReels = {
                                        post ->

                                    openReels(

                                        post =
                                            post,

                                        from =
                                            SpotRoute.DISCOVER
                                    )
                                }
                            )
                        }


                        SpotRoute.MESSAGES_CHATS -> {

                            MessagesScreen(
                                onProfileClick = { openProfile(it, SpotRoute.MESSAGES_CHATS) },
                                onOpenReels = { openReels(it, SpotRoute.MESSAGES_CHATS) },

                                onThreadVisibilityChanged = { messagesDetailOpen = it },
                                onUnreadCountChanged = { messagesUnreadCount = it },
                                profile =
                                    currentProfile,

                                onNavigate =
                                    ::navigate,

                                onBack = {

                                    route =
                                        previousRoute
                                },

                                initialTab =
                                    MessagesTab.CHATS,

                                startConversationWithUid =
                                    messageTargetUid,

                                onStartConversationConsumed = {

                                    messageTargetUid =
                                        null
                                },

                                openConversationId =
                                    notificationChat ?: chatTarget,

                                onOpenConversationConsumed = {

                                    PushNotifications.openConversation.value =
                                        null

                                    chatTarget =
                                        null
                                }
                            )
                        }


                        SpotRoute.MESSAGES_GROUPS -> {

                            MessagesScreen(
                                onProfileClick = { openProfile(it, SpotRoute.MESSAGES_GROUPS) },
                                onOpenReels = { openReels(it, SpotRoute.MESSAGES_GROUPS) },

                                onThreadVisibilityChanged = { messagesDetailOpen = it },
                                onUnreadCountChanged = { messagesUnreadCount = it },
                                profile =
                                    currentProfile,

                                onNavigate =
                                    ::navigate,

                                onBack = {

                                    route =
                                        previousRoute
                                },

                                initialTab =
                                    MessagesTab.GROUPS
                            )
                        }


                        SpotRoute.COLLAB_REQUESTS -> {

                            MessagesScreen(
                                onProfileClick = { openProfile(it, SpotRoute.COLLAB_REQUESTS) },
                                onOpenReels = { openReels(it, SpotRoute.COLLAB_REQUESTS) },

                                onThreadVisibilityChanged = { messagesDetailOpen = it },
                                onUnreadCountChanged = { messagesUnreadCount = it },
                                profile =
                                    currentProfile,

                                onNavigate =
                                    ::navigate,

                                onBack = {

                                    route =
                                        previousRoute
                                },

                                initialTab =
                                    MessagesTab.CHATS,
                                openRequests = true
                            )
                        }


                        SpotRoute.NOTIFICATIONS -> {
                            NotificationsScreen(currentProfile, ::navigate, showBottomNavigation = false,
                                onBack = { navigate(SpotRoute.HOME) },
                                onProfile = { openProfile(it, SpotRoute.NOTIFICATIONS) },
                                onMessage = { openMessages(it, SpotRoute.NOTIFICATIONS) },
                                onChat = { openChat(it, SpotRoute.NOTIFICATIONS) },
                                onOpenRequests = { navigate(SpotRoute.COLLAB_REQUESTS) },
                                onOpen = { item ->
                                // A changed or cancelled event opens that event.
                                if (item.type in listOf("event_updated", "event_cancelled") && item.targetId.isNotBlank()) shownEvent = item.targetId
                                else if (item.type == "follow" || item.postId.isBlank()) openProfile(
                                    SpotUiProfile(item.senderId, item.senderName, "", "", "", item.senderProfileImageUrl), SpotRoute.NOTIFICATIONS)
                                else notificationTarget = item.postId
                            })
                        }

                        SpotRoute.PROFILE -> {

                            val isOwnProfile =
                                selectedProfile.uid ==
                                        currentProfile.uid


                            var profilePosts by remember(selectedProfile.uid) { mutableStateOf(HomeRepository.cachedProfile(selectedProfile.uid)) }
                            var profileLoading by remember(selectedProfile.uid) { mutableStateOf(true) }
                            var profileError by remember(selectedProfile.uid) { mutableStateOf("") }
                            var profileRetry by remember(selectedProfile.uid) { mutableStateOf(0) }
                            var openedProfilePost by remember(selectedProfile.uid) { mutableStateOf<String?>(null) }
                            var profileHeader by remember(selectedProfile) { mutableStateOf(selectedProfile) }
                            LifecycleDisposableEffect(selectedProfile.uid, profileRetry) {
                                profileLoading = true
                                val listener = HomeRepository.listenProfilePosts(selectedProfile.uid,
                                    { profilePosts = it; profileLoading = false; profileError = "" },
                                    { profileError = it; profileLoading = false })
                                val identity = HomeRepository.listenToUser(selectedProfile.uid) { user ->
                                    if (user != null) profileHeader = user.asProfileUi()
                                }
                                onDispose { listener.remove(); identity.remove() }
                            }
                            var following by remember(selectedProfile.uid) { mutableStateOf<Boolean?>(null) }
                            var requested by remember(selectedProfile.uid) { mutableStateOf(false) }
                            var followBusy by remember(selectedProfile.uid) { mutableStateOf(false) }
                            var followCounts by remember(selectedProfile.uid) { mutableStateOf<Pair<Long, Long>?>(null) }
                            var followError by remember(selectedProfile.uid) { mutableStateOf("") }
                            LifecycleDisposableEffect(selectedProfile.uid, profileRetry) {
                                val listener = if (!isOwnProfile) SocialRepository.shared.listenFollowing(selectedProfile.uid,
                                    { following = it; followError = "" }, { followError = it }) else null
                                val request = if (!isOwnProfile) PrivacyRepository.shared.listenRequested(selectedProfile.uid) { requested = it } else null
                                onDispose { listener?.remove(); request?.remove() }
                            }
                            LaunchedEffect(selectedProfile.uid, following, profileRetry) {
                                try { followCounts = SocialRepository.shared.counts(selectedProfile.uid) }
                                catch (c: CancellationException) { throw c }
                                catch (_: Exception) { followError = "Profile counts couldn't load. Tap to retry." }
                            }
                            UserProfileScreen(
                                following = following, followerCount = followCounts?.first, followingCount = followCounts?.second,
                                followRequested = requested, followPending = followBusy,
                                onDetailVisibilityChanged = { profileDetailOpen = it },
                                onOpenProfile = { openProfile(it, SpotRoute.PROFILE) },
                                viewer = currentProfile, onOpenChat = { openChat(it, SpotRoute.PROFILE) },
                                onFollowChanged = {
                                    if (!followBusy && following != null) {
                                        followBusy = true
                                        val targetUid = selectedProfile.uid
                                        val shouldFollow = following != true
                                        socialScope.launch {
                                            try {
                                                if (shouldFollow && (profileHeader.isPrivate || profileHeader.privacyUpdating)) PrivacyRepository.shared.requestFollow(targetUid, !requested)
                                                else SocialRepository.shared.setFollowing(targetUid, shouldFollow)
                                                followError = ""
                                            }
                                            catch (c: CancellationException) { throw c }
                                            catch (_: Exception) { followError = "Couldn't update following. Tap to retry." }
                                            finally { followBusy = false }
                                        }
                                    }
                                },

                                profile =
                                    profileHeader,

                                isOwnProfile =
                                    isOwnProfile,

                                post = profilePosts.firstOrNull()?.asUiPost(),
                                // A hidden like count still shows to the post's owner.
                                posts = profilePosts.map { it.asUiPost().let { ui -> if (isOwnProfile) ui.copy(likesHidden = false) else ui } },
                                // A tagged post belongs to someone else, so it opens by id like a notification's post.
                                onOpenPost = {
                                    if (isPostVideoUrl(it.mediaUri.orEmpty())) openReels(it, SpotRoute.PROFILE)
                                    else if (profilePosts.any { own -> own.id == it.id }) openedProfilePost = it.id
                                    else notificationTarget = it.id
                                },
                                postsStatus = {
                                    if (followError.isNotBlank()) TextButton({ profileRetry++ }) { Text(followError) }
                                    if (profileLoading && profilePosts.isEmpty()) SpotLoading(true, SkeletonKind.GRID)
                                    if (profileError.isNotBlank()) TextButton(onClick = { profileRetry++ }) { Text(profileError, color = SpotPalette.GoldDeep) }
                                },

                                onHireMeClick = if (!isOwnProfile && currentProfile.accountType == com.example.spotapp.data.AccountType.TALENT_SEEKER && profileHeader.accountType == com.example.spotapp.data.AccountType.STUDENT)
                                    ({ inviteTarget = profileHeader }) else null,
                                onBack = ::backFromProfile,

                                onSettingsClick = {

                                    previousRoute =
                                        SpotRoute.PROFILE

                                    route =
                                        SpotRoute.SETTINGS
                                },

                                onMessageClick = { openMessages(selectedProfile.uid, SpotRoute.PROFILE) }
                            )
                            profilePosts.firstOrNull { it.id == openedProfilePost }?.let { item ->
                                ProfilePublishedPostViewer(item, onProfileClick = { openProfile(it, SpotRoute.PROFILE) },
                                    onOpenReels = { openReels(it, SpotRoute.PROFILE) }, profile = currentProfile) { openedProfilePost = null }
                            }
                        }


                        else -> Unit
                    }
                    }
                }
            }
        }
        }
        }
        return
    }


    SpotScreenEntrance(Modifier.fillMaxSize(), fullscreen = route == SpotRoute.REELS) {
    AnimatedContent(route, transitionSpec = { fadeIn(tween(260, easing = FastOutSlowInEasing)) + slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 16 } togetherWith fadeOut(tween(180)) }, label = "detail navigation") { shownRoute ->
    when (shownRoute) {

        SpotRoute.REELS -> {

            savedScreens.SaveableStateProvider("reels:${initialReelPostId.orEmpty()}") { ReelsScreen(

                posts =
                    reelPosts,

                initialPostId =
                    initialReelPostId,

                profile = currentProfile,
                onProfileClick = { openProfile(it, SpotRoute.REELS) },
                onBack = {
                    route = reelReturnRoute; previousRoute = reelParentRoute
                    reelReturnProfile?.let { selectedProfile = it }
                },
                onPostChanged = {
                        updatedPost ->


                    if (
                        updatedPost.id ==
                        homePost.id
                    ) {

                        homePost =
                            updatedPost
                    }


                    reelPosts =
                        reelPosts.map {
                                reelPost ->


                            if (
                                reelPost.id ==
                                updatedPost.id
                            ) {

                                updatedPost

                            } else {

                                reelPost
                            }
                        }
                }
            )
        }}


        SpotRoute.NEW_POST -> {

            NewPostScreen(

                profile =
                    currentProfile,

                onCancel = {

                    route =
                        previousRoute
                },

                onPublishStarted = {

                    route =
                        SpotRoute.HOME
                }
            )
        }


        SpotRoute.SETTINGS -> {

            SettingsScreen(
                profile = currentProfile, onProfile = { openProfile(it, SpotRoute.SETTINGS) },

                onBack = {

                    route =
                        previousRoute
                },

                onLogout = {

                    onLogout()
                }
            )
        }


        SpotRoute.SEARCH -> {

            SearchScreen(

                currentProfile =
                    currentProfile,

                onBack = {

                    route =
                        previousRoute
                },

                onProfileClick = {
                        profile ->

                    openProfile(

                        profile =
                            profile,

                        from =
                            SpotRoute.SEARCH
                    )
                },
                // Found posts and events open over Search, so Back returns to the results.
                onOpenPost = { notificationFrom = SpotRoute.SEARCH; notificationTarget = it },
                onOpenEvent = { shownEvent = it }
            )
        }


        else ->
            Unit
    }
    }
}


}

private fun SpotUser.toFrontendProfile():
        SpotUiProfile { return asProfileUi() }
