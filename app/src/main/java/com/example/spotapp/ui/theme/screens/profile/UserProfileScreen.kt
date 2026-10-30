package com.example.spotapp.ui.theme.screens.profile

import com.example.spotapp.data.SpotLinks
import com.example.spotapp.ui.theme.components.spotAmbientBackground
import com.example.spotapp.ui.theme.components.LocalSpotBottomContentPadding
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import com.example.spotapp.ui.theme.components.*
import androidx.compose.material.icons.rounded.*
import com.example.spotapp.ui.theme.screens.home.AccountRoleBadge
import com.example.spotapp.ui.theme.screens.discover.PostedEvents
import com.example.spotapp.ui.theme.screens.discover.CommunityDetails
import com.example.spotapp.ui.theme.screens.discover.CollabInvitePage
import com.example.spotapp.data.AccountType
import com.example.spotapp.data.CampusEvent
import com.example.spotapp.data.DiscoverRepository
import com.example.spotapp.data.HomeRepository
import com.example.spotapp.data.Supabase
import com.example.spotapp.data.collaboration.CollaborationCreator
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Tab
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.offset

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect

import com.example.spotapp.ui.theme.components.SpotAvatar

import com.example.spotapp.ui.theme.spotSp

import android.content.Intent
import com.example.spotapp.ui.theme.screens.messages.ShareProfileSheet

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.AccountBox
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import com.example.spotapp.ui.theme.components.SpotButtonStyle
import com.example.spotapp.ui.theme.components.SpotCard
import com.example.spotapp.ui.theme.components.SpotEmptyState
import com.example.spotapp.ui.theme.components.SpotGoldGradient
import com.example.spotapp.ui.theme.components.SpotHeaderAction
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.components.SpotPillButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import coil3.compose.AsyncImage
import com.example.spotapp.data.cloudinaryVideoThumbnail
import com.example.spotapp.data.isPostVideoUrl

import com.example.spotapp.ui.theme.InterFontFamily
import com.example.spotapp.ui.theme.models.SpotUiPost
import com.example.spotapp.ui.theme.models.SpotUiProfile


private val ProfileWhite get() = SpotPalette.Surface
private val ProfileBlack get() = SpotPalette.Ink
private val ProfileGoldDeep = Color(0xFFE8B84B)
private val ProfileAmberText get() = if (SpotPalette.dark) SpotPalette.GoldInk else Color(0xFF8A5C10)
private val ProfileAvatarBg get() = if (SpotPalette.dark) SpotPalette.Track else Color(0xFFF0F0F0)


internal enum class ProfileTab {
    POSTS,
    REELS,
    TAGGED,
    /** A Spotter's past events. */
    EVENTS
}


private enum class ProfilePage {
    PROFILE,
    EDIT
}


@Composable
fun UserProfileScreen(
    profile: SpotUiProfile,
    isOwnProfile: Boolean,
    post: SpotUiPost?,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    onMessageClick: () -> Unit,
    bio: String? = null,
    onHireMeClick: (() -> Unit)? = null,
    onEditProfileClick: () -> Unit = {},
    onEditAvatarClick: () -> Unit = {},
    onEditBioClick: () -> Unit = {},
    posts: List<SpotUiPost> = listOfNotNull(post),
    postsStatus: @Composable () -> Unit = {},
    onOpenPost: ((SpotUiPost) -> Unit)? = null,
    following: Boolean? = null, onFollowChanged: (() -> Unit)? = null,
    followerCount: Long? = null, followingCount: Long? = null,
    followRequested: Boolean = false, followPending: Boolean = false,
    onDetailVisibilityChanged: (Boolean) -> Unit = {}, onOpenProfile: (SpotUiProfile) -> Unit = {},
    /** The signed-in member, for the events a Spotter posted (opened from their Spotter badge). */
    viewer: SpotUiProfile? = null, onOpenChat: (String) -> Unit = {}
) {

    val context = LocalContext.current

    var page by remember {
        mutableStateOf(ProfilePage.PROFILE)
    }
    var sharingProfile by remember(profile.uid) { mutableStateOf(false) }

    SideEffect { onDetailVisibilityChanged(page == ProfilePage.EDIT) }
    DisposableEffect(Unit) { onDispose { onDetailVisibilityChanged(false) } }
    var selectedTab by remember {
        mutableStateOf(ProfileTab.POSTS)
    }

    var isFollowing by remember {
        mutableStateOf(false)
    }

    var displayName by remember(profile) {
        mutableStateOf(profile.displayName)
    }

    var username by remember(profile) {
        mutableStateOf(profile.username)
    }

    var talent by remember(profile) {
        mutableStateOf(profile.talent)
    }

    var course by remember(profile) {
        mutableStateOf(profile.course)
    }

    var localBio by remember(profile, bio) {
        mutableStateOf(bio ?: profile.bio)
    }

    var pronouns by remember(profile.uid) {
        mutableStateOf("")
    }

    var links by remember(profile.uid) {
        mutableStateOf("")
    }

    var gender by remember(profile.uid) {
        mutableStateOf("")
    }

    var aiCreator by remember(profile.uid) {
        mutableStateOf(false)
    }

    var localAvatarUri by remember(profile.uid) {
        mutableStateOf<String?>(null)
    }

    var previewPost by remember(profile.uid) { mutableStateOf<SpotUiPost?>(null) }


    val avatarPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->

            if (uri != null) {
                localAvatarUri = uri.toString()
                onEditAvatarClick()
            }
        }


    previewPost?.let { selected ->
        PostPreviewDialog(post = selected, onDismiss = { previewPost = null })
    }


    AnimatedContent(
        targetState = page,

        modifier = Modifier.fillMaxSize(),

        transitionSpec = {

            if (targetState == ProfilePage.EDIT) {

                (
                        slideInHorizontally(
                            animationSpec = tween(240)
                        ) {
                            it
                        } +
                                fadeIn(
                                    animationSpec = tween(200)
                                )
                        ) togetherWith

                        (
                                slideOutHorizontally(
                                    animationSpec = tween(240)
                                ) {
                                    -it / 4
                                } +
                                        fadeOut(
                                            animationSpec = tween(150)
                                        )
                                )

            } else {

                (
                        slideInHorizontally(
                            animationSpec = tween(240)
                        ) {
                            -it / 4
                        } +
                                fadeIn(
                                    animationSpec = tween(200)
                                )
                        ) togetherWith

                        (
                                slideOutHorizontally(
                                    animationSpec = tween(240)
                                ) {
                                    it
                                } +
                                        fadeOut(
                                            animationSpec = tween(150)
                                        )
                                )
            }
        },

        label = "profile_edit_transition"
    ) { currentPage ->

        when (currentPage) {

            ProfilePage.PROFILE -> {

                ProfileMainPage(
                    profile = profile,
                    isOwnProfile = isOwnProfile,
                    posts = posts,
                    postsStatus = postsStatus,
                    displayName = displayName,
                    username = username,
                    talent = talent,
                    course = course,
                    bio = localBio,
                    avatarUri = localAvatarUri,

                    selectedTab = selectedTab,
                    isFollowing = following ?: isFollowing,
                    followerCount = followerCount, followingCount = followingCount,
                    followRequested = followRequested, followPending = followPending, onOpenProfile = onOpenProfile,
                    viewer = viewer, onOpenChat = onOpenChat,

                    onBack = onBack,

                    onSettingsClick = onSettingsClick,

                    onEditProfile = {
                        page = ProfilePage.EDIT
                    },

                    // Into your SPOT chats as a profile card; the sheet's "Share to other apps" sends text instead.
                    onShareProfile = { sharingProfile = true },

                    onChangeAvatar = {
                        page = ProfilePage.EDIT
                    },

                    onFollow = {
                        if (onFollowChanged != null) onFollowChanged() else isFollowing = !isFollowing
                    },

                    onMessage = onMessageClick,

                    onHireMe = onHireMeClick,

                    onTabSelected = {
                        selectedTab = it
                    },

                    onPostClick = { if (onOpenPost != null) onOpenPost(it) else previewPost = it }
                )
            }


            ProfilePage.EDIT -> {
                ConnectedEditProfile(profile, onBack = { page = ProfilePage.PROFILE }, onSaved = {
                    localAvatarUri = null; onEditProfileClick(); page = ProfilePage.PROFILE
                })
            }
        }
    }
    if (sharingProfile) ShareProfileSheet(profile.uid, displayName, username, profile.profileImageUrl, onMoreApps = {
        sharingProfile = false
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Check out $displayName (@$username) on SPOT.\n${SpotLinks.profile(profile.uid)}")
        }, "Share SPOT profile"))
    }, onDismiss = { sharingProfile = false })
}


@Composable
private fun ProfileMainPage(
    profile: SpotUiProfile,
    isOwnProfile: Boolean,
    posts: List<SpotUiPost>,
    postsStatus: @Composable () -> Unit,
    displayName: String,
    username: String,
    talent: String,
    course: String,
    bio: String,
    avatarUri: String?,
    selectedTab: ProfileTab,
    isFollowing: Boolean,
    followerCount: Long?, followingCount: Long?,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    onEditProfile: () -> Unit,
    onShareProfile: () -> Unit,
    onChangeAvatar: () -> Unit,
    onFollow: () -> Unit,
    onMessage: () -> Unit,
    onHireMe: (() -> Unit)?,
    onTabSelected: (ProfileTab) -> Unit,
    onPostClick: (SpotUiPost) -> Unit,
    followRequested: Boolean, followPending: Boolean, onOpenProfile: (SpotUiProfile) -> Unit,
    viewer: SpotUiProfile? = null, onOpenChat: (String) -> Unit = {}
) {
    var connections by remember { mutableStateOf<String?>(null) }
    connections?.let { mode -> ConnectionsPage(profile, mode, { connections = null }, onOpenProfile) }
    val spotter = profile.accountType == AccountType.TALENT_SEEKER
    val me = viewer ?: profile
    var postedEvents by rememberSaveable(profile.uid) { mutableStateOf(false) }
    if (postedEvents && spotter) PostedEvents(profile, me, { postedEvents = false },
        { postedEvents = false; onOpenProfile(it) }, { postedEvents = false; onOpenChat(it) })
    var openedEvent by rememberSaveable(profile.uid) { mutableStateOf<String?>(null) }
    var applyOnOpen by rememberSaveable(profile.uid) { mutableStateOf(false) }
    var collabInvite by remember(profile.uid) { mutableStateOf(false) }
    var talentFilter by rememberSaveable(profile.uid) { mutableStateOf<String?>(null) }
    var spotterTab by rememberSaveable(profile.uid) { mutableStateOf(ProfileTab.EVENTS) }
    val canSee = isOwnProfile || !profile.isPrivate || isFollowing
    // Events and tagged posts load only for a signed-in viewer, as in the app (previews have none).
    val connected = viewer != null && Supabase.uid() != null && profile.uid.isNotBlank()

    // A Creator's events are the ones they took part in; a Spotter's are the ones they posted.
    var events by remember(profile.uid) { mutableStateOf(emptyList<CampusEvent>()) }
    var moreEvents by remember(profile.uid) { mutableStateOf(false) }
    var eventsError by remember(profile.uid) { mutableStateOf("") }
    var eventsRetry by remember(profile.uid) { mutableIntStateOf(0) }
    LifecycleDisposableEffect(profile.uid, spotter, canSee, connected, eventsRetry) {
        val repository = DiscoverRepository.shared
        val listener = when {
            !connected -> null
            // Cancelled events stay in the organizer's own list, marked, and leave everyone else's view.
            spotter -> repository.listenOrganizedEvents(profile.uid, { page ->
                events = page.items.filter { isOwnProfile || !it.cancelled }; moreEvents = page.hasMore; eventsError = ""
            }, { eventsError = it })
            canSee -> repository.listenProfileEvents(profile.uid, { events = it; eventsError = "" }, { eventsError = it })
            else -> null
        }
        onDispose { listener?.remove() }
    }
    var status by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LifecycleDisposableEffect(profile.uid, spotter, connected) {
        val listener = if (spotter && connected) DiscoverRepository.shared.listenMyEventStatus { status = it } else null
        onDispose { listener?.remove() }
    }
    var tagged by remember(profile.uid) { mutableStateOf<List<SpotUiPost>?>(null) }
    val loadTagged = connected && !spotter && selectedTab == ProfileTab.TAGGED
    LifecycleDisposableEffect(profile.uid, loadTagged) {
        val listener = if (loadTagged) HomeRepository.listenTaggedPosts(profile.uid, { tagged = it.map { post -> post.asUiPost() } },
            { tagged = emptyList() }) else null
        onDispose { listener?.remove() }
    }
    fun openEvent(event: CampusEvent, apply: Boolean = false) { applyOnOpen = apply; openedEvent = event.eventId }
    val now = remember(events) { System.currentTimeMillis() }
    // A Creator can invite another Creator who's open to it; Spotters keep their own Invite.
    val canCollab = !isOwnProfile && !spotter && profile.openToCollab && viewer != null &&
        viewer.accountType != AccountType.TALENT_SEEKER && onHireMe == null

    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().spotAmbientBackground(ProfileWhite).verticalScroll(scroll)) {
        if (spotter) {
            val (past, upcoming) = events.partition { it.startsAt <= now }
            SpotterHeader(profile, me, avatarUri, displayName, username, bio, isOwnProfile,
                eventCount = spotCompactCount(events.size.toLong()) + if (moreEvents) "+" else "",
                followerCount = followerCount, followingCount = followingCount, onBack = onBack, onSettings = onSettingsClick,
                onShare = onShareProfile, onAvatar = onChangeAvatar, onAddBio = onEditProfile,
                onEvents = { postedEvents = true }, onConnections = { connections = it })
            Spacer(Modifier.height(14.dp))
            ProfileActions(isOwnProfile, isFollowing, onEditProfile, onShareProfile, onFollow, onMessage, onHireMe, followRequested, followPending)
            Spacer(Modifier.height(16.dp))
            ConnectedHighlights(profile, isOwnProfile, isFollowing)
            if (!canSee) PrivateNotice()
            if (eventsError.isNotBlank()) TextButton({ eventsRetry++ }, Modifier.padding(horizontal = 12.dp)) { Text(eventsError, color = SpotPalette.GoldInk) }
            SpotterUpcoming(upcoming.sortedBy { it.startsAt }, status, me, isOwnProfile, { event, apply -> openEvent(event, apply) }) { postedEvents = true }
            Spacer(Modifier.height(20.dp))
            ProfileTabs(listOf(ProfileTab.EVENTS to "Past events", ProfileTab.POSTS to "Posts"), spotterTab, { spotterTab = it })
            if (spotterTab == ProfileTab.EVENTS) SpotterPastEvents(past.sortedByDescending { it.startsAt }, status, me) { openEvent(it) }
            else {
                postsStatus()
                if (posts.isEmpty()) ProfileEmptyState(Icons.Rounded.GridView, "No posts yet", "Posts will appear here.")
                else ProfileGrid(posts, false, onPostClick)
            }
        } else {
            CreatorPortfolioHeader(profile, avatarUri, displayName, isOwnProfile, onBack, onSettingsClick, onShareProfile, onChangeAvatar)
            CreatorIdentity(profile, displayName, username, course, bio, isOwnProfile, posts.size, followerCount, followingCount, onEditProfile) {
                connections = it
            }
            Spacer(Modifier.height(12.dp))
            ProfileActions(isOwnProfile, isFollowing, onEditProfile, onShareProfile, onFollow, onMessage, onHireMe, followRequested, followPending,
                onCollab = if (canCollab) ({ collabInvite = true }) else null)
            Spacer(Modifier.height(16.dp))
            ConnectedHighlights(profile, isOwnProfile, isFollowing)
            if (!canSee) PrivateNotice()
            val top = posts.filter { it.likes > 0 && hasPostMedia(it) }.maxByOrNull { it.likes }
            if (top != null && posts.size > 1) TopPerformance(top) { onPostClick(top) }
            val talents = profile.talentCategories.filter { it.isNotBlank() }.distinct()
            if (talents.isNotEmpty() && canSee) TalentTiles(talents, posts, talentFilter) { talent ->
                talentFilter = if (talentFilter == talent) null else talent
                onTabSelected(ProfileTab.POSTS)
            }
            if (events.isNotEmpty()) PerformedEvents(events) { openEvent(it) }
            Spacer(Modifier.height(20.dp))
            ProfileTabs(listOf(ProfileTab.POSTS to "Posts", ProfileTab.REELS to "Reels", ProfileTab.TAGGED to "Tagged"), selectedTab, onTabSelected)
            AnimatedContent(selectedTab, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "profile_tabs") { tab ->
                Column {
                    when (tab) {
                        ProfileTab.REELS -> {
                            postsStatus()
                            val videos = posts.filter { it.localVideoRes != null || isPostVideoUrl(it.mediaUri.orEmpty()) }
                            if (videos.isEmpty()) ProfileEmptyState(Icons.Rounded.VideoLibrary, "No reels yet", "Video posts will appear here.")
                            else ProfileGrid(videos, true, onPostClick)
                        }
                        ProfileTab.TAGGED -> {
                            val list = tagged
                            if (list == null && loadTagged) SpotLoading(true, SkeletonKind.GRID)
                            else if (list.isNullOrEmpty()) ProfileEmptyState(Icons.Rounded.AccountBox, "No tagged posts yet",
                                if (isOwnProfile) "Posts you're tagged in will appear here." else "Posts they're tagged in will appear here.")
                            else ProfileGrid(list, false, onPostClick)
                        }
                        else -> {
                            postsStatus()
                            val filter = talentFilter
                            val shown = if (filter == null) posts
                                else posts.filter { post -> post.category.split(", ").any { it.equals(filter, ignoreCase = true) } }
                            if (filter != null) Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("$filter posts", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = ProfileBlack)
                                TextButton({ talentFilter = null }) { Text("Show all", color = SpotPalette.GoldInk) }
                            }
                            if (shown.isEmpty()) ProfileEmptyState(Icons.Rounded.GridView, if (filter == null) "No posts yet" else "No $filter posts yet",
                                "Posts will appear here.")
                            else ProfileGrid(shown, false, onPostClick)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp + LocalSpotBottomContentPadding.current))
    }
    // The banner sits behind the status bar; once it scrolls away, a backdrop fades in so content never runs under the clock.
    val scrim by remember { derivedStateOf { (scroll.value / 240f).coerceIn(0f, 1f) } }
    Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).graphicsLayer { alpha = scrim }.background(ProfileWhite))
    }
    openedEvent?.let { id ->
        CommunityDetails(id, true, me, close = { openedEvent = null; applyOnOpen = false },
            onProfile = { openedEvent = null; onOpenProfile(it) }, onChat = { openedEvent = null; onOpenChat(it) }, startApply = applyOnOpen)
    }
    if (collabInvite) CollabInvitePage(me, CollaborationCreator(profile, profile.talentCategories), { collabInvite = false }) { chat ->
        collabInvite = false; onOpenChat(chat)
    }
}

@Composable
private fun PrivateNotice() {
    SpotCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(20.dp), elevation = 1.dp) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SpotIconTile(Icons.Rounded.Lock, size = 40.dp)
            Column(Modifier.padding(start = 12.dp)) {
                Text("This account is private", style = MaterialTheme.typography.titleSmall, color = ProfileBlack)
                Text("Follow to see their posts and reels.", style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted)
            }
        }
    }
}

private val ProfileRing get() = Brush.sweepGradient(listOf(SpotPalette.GoldBright, SpotPalette.GoldDeep, ProfileGoldDeep, SpotPalette.GoldBright))

@Composable
internal fun ProfileAvatarLarge(profile: SpotUiProfile, avatarOverride: String?, displayName: String, isOwnProfile: Boolean,
    onEditAvatarClick: () -> Unit, size: Dp = 116.dp) {
    Box(Modifier.size(size)) {
        Box(Modifier.matchParentSize().shadow(10.dp, CircleShape, ambientColor = SpotPalette.Shadow, spotColor = SpotPalette.Shadow)
            .background(ProfileWhite, CircleShape).padding(4.dp).background(ProfileRing, CircleShape).padding(3.dp)
            .background(ProfileWhite, CircleShape).padding(3.dp)) {
            SpotAvatar(avatarOverride ?: profile.profileImageUrl, "$displayName profile picture", Modifier.fillMaxSize(), profile.uid)
        }
        if (isOwnProfile) IconButton(onEditAvatarClick, Modifier.align(Alignment.BottomEnd).offset(6.dp, 6.dp).size(44.dp)) {
            Box(Modifier.size(32.dp).shadow(3.dp, CircleShape, ambientColor = SpotPalette.Shadow, spotColor = SpotPalette.Shadow)
                .background(SpotGoldGradient, CircleShape).border(2.dp, ProfileWhite, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PhotoCamera, "Change profile photo", Modifier.size(16.dp), tint = SpotPalette.OnGold)
            }
        }
    }
}

@Composable
internal fun ProfileChip(text: String, icon: ImageVector? = null, filled: Boolean = false, leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null, trailing: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(modifier.clip(CircleShape).background(if (filled) SpotPalette.GoldSoft else ProfileWhite, CircleShape)
        .border(1.dp, if (filled) SpotPalette.GoldLine else SpotPalette.Line, CircleShape)
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .padding(start = if (leading != null) 6.dp else 12.dp, end = if (trailing != null) 6.dp else 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) leading()
        else if (icon != null) Icon(icon, null, Modifier.size(15.dp), tint = if (filled) SpotPalette.GoldDeep else SpotPalette.Muted)
        Text(text, Modifier.padding(start = if (icon != null || leading != null) 6.dp else 0.dp), color = if (filled) SpotPalette.GoldInk else ProfileBlack,
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) Icon(trailing, null, Modifier.padding(start = 2.dp).size(16.dp), tint = SpotPalette.Muted)
    }
}

@Composable
private fun ProfileActions(isOwnProfile: Boolean, isFollowing: Boolean, onEditProfile: () -> Unit, onShareProfile: () -> Unit,
    onFollow: () -> Unit, onMessage: () -> Unit, onHireMe: (() -> Unit)?, followRequested: Boolean, followPending: Boolean,
    onCollab: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isOwnProfile) {
            SpotPillButton("Edit profile", onEditProfile, Modifier.weight(1f), icon = Icons.Rounded.Edit, compact = true)
            SpotPillButton("Share", onShareProfile, Modifier.weight(1f), icon = Icons.Rounded.Share, style = SpotButtonStyle.Secondary, compact = true)
        } else {
            // Three buttons share the row with an invite; icons only fit when there are two.
            val roomy = onHireMe == null && onCollab == null
            val followed = isFollowing || followRequested
            SpotPillButton(if (followPending) "Updating…" else if (isFollowing) "Following" else if (followRequested) "Requested" else "Follow",
                onFollow, Modifier.weight(1f), icon = if (!roomy) null else if (followed) Icons.Rounded.Check else Icons.Rounded.PersonAdd,
                style = if (followed) SpotButtonStyle.Secondary else SpotButtonStyle.Primary, enabled = !followPending, compact = true)
            SpotPillButton("Message", onMessage, Modifier.weight(1f), icon = if (roomy) Icons.Rounded.ChatBubbleOutline else null,
                style = SpotButtonStyle.Secondary, compact = true)
            if (onHireMe != null) SpotPillButton("Invite", onHireMe, Modifier.weight(1f), style = SpotButtonStyle.Tonal, compact = true)
            else if (onCollab != null) SpotPillButton("Collab", onCollab, Modifier.weight(1f).testTag("profile-collab"),
                style = SpotButtonStyle.Tonal, compact = true)
        }
    }
}

@Composable
private fun ProfileGrid(posts: List<SpotUiPost>, reels: Boolean, onOpen: (SpotUiPost) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        posts.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { item -> ProfileGridTile(item, Modifier.weight(1f), reels) { onOpen(item) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ProfileGridTile(post: SpotUiPost, modifier: Modifier, reels: Boolean, onClick: () -> Unit) {
    val video = reels || isVideoPost(post)
    Box(modifier.aspectRatio(if (reels) .72f else 1f).clip(RoundedCornerShape(12.dp)).background(ProfileAvatarBg)
        .clickable(onClickLabel = if (video) "Open reel" else "Open post", onClick = onClick)) {
        ProfilePostMedia(post, Modifier.fillMaxSize())
        if (video) Icon(Icons.Rounded.PlayArrow, "Reel", Modifier.align(Alignment.TopEnd).padding(6.dp)
            .background(ProfileBlack.copy(alpha = .55f), CircleShape).padding(3.dp).size(14.dp), tint = ProfileWhite)
        if (post.pinned) Icon(Icons.Rounded.PushPin, "Pinned", Modifier.align(Alignment.TopStart).padding(6.dp)
            .background(ProfileBlack.copy(alpha = .55f), CircleShape).padding(3.dp).size(14.dp), tint = ProfileWhite)
        if (post.likes > 0 && !post.likesHidden) Row(Modifier.align(Alignment.BottomStart).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, ProfileBlack.copy(alpha = .55f)))).padding(horizontal = 7.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Favorite, null, Modifier.size(12.dp), tint = ProfileWhite)
            Text(spotCompactCount(post.likes.toLong()), Modifier.padding(start = 4.dp), color = ProfileWhite,
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}


/** A post's first photo or video frame; a text-only post shows the start of its caption. */
@Composable
internal fun ProfilePostMedia(post: SpotUiPost, modifier: Modifier) {
    when {
        post.localImageResources.isNotEmpty() -> Image(painterResource(post.localImageResources.first()), "Post", modifier, contentScale = ContentScale.Crop)
        !post.mediaUri.isNullOrBlank() -> AsyncImage(if (isPostVideoUrl(post.mediaUri.orEmpty())) cloudinaryVideoThumbnail(post.mediaUri.orEmpty()) else post.mediaUri,
            "Post", modifier, contentScale = ContentScale.Crop)
        post.localVideoRes != null -> Box(modifier.background(ProfileBlack))
        else -> Text(post.caption, modifier.background(SpotPalette.GoldWash).padding(10.dp), color = ProfileBlack,
            style = MaterialTheme.typography.labelMedium, maxLines = 5, overflow = TextOverflow.Ellipsis)
    }
}

internal fun isVideoPost(post: SpotUiPost): Boolean =
    post.localVideoRes != null || post.mediaUri?.let { isVideoUri(it) || isPostVideoUrl(it) } == true

internal fun hasPostMedia(post: SpotUiPost): Boolean =
    post.localImageResources.isNotEmpty() || post.localVideoRes != null || !post.mediaUri.isNullOrBlank()

@Composable
internal fun ProfileEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    SpotEmptyState(icon, title, message = subtitle)
}




@Composable
private fun PostPreviewDialog(
    post: SpotUiPost,
    onDismiss: () -> Unit
) {

    AlertDialog(
        onDismissRequest = onDismiss,

        title = {

            Text(
                text =
                    post.user.displayName,

                color = ProfileBlack,

                fontFamily = InterFontFamily,

                fontSize = 13.spotSp,

                fontWeight = FontWeight.Bold
            )
        },

        text = {

            Column {

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(
                                RoundedCornerShape(12.dp)
                            )
                            .background(
                                ProfileAvatarBg
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    when {

                        post.localImageResources.isNotEmpty() -> {

                            Image(
                                painter =
                                    painterResource(
                                        id =
                                            post.localImageResources.first()
                                    ),

                                contentDescription =
                                    "Post image",

                                contentScale =
                                    ContentScale.Crop,

                                modifier =
                                    Modifier.fillMaxSize()
                            )
                        }


                        !post.mediaUri.isNullOrBlank() -> {

                            AsyncImage(
                                model =
                                    post.mediaUri,

                                contentDescription =
                                    "Post image",

                                contentScale =
                                    ContentScale.Crop,

                                modifier =
                                    Modifier.fillMaxSize()
                            )
                        }


                        else -> {

                            Icon(
                                imageVector =
                                    Icons.Rounded.PlayArrow,

                                contentDescription =
                                    null,

                                tint = ProfileBlack,

                                modifier =
                                    Modifier.size(30.dp)
                            )
                        }
                    }
                }


                if (post.caption.isNotBlank()) {

                    Spacer(
                        modifier =
                            Modifier.height(10.dp)
                    )


                    Text(
                        text = post.caption,

                        color = ProfileBlack,

                        fontFamily = InterFontFamily,

                        fontSize = 11.spotSp,

                        lineHeight = 15.sp
                    )
                }
            }
        },

        confirmButton = {

            TextButton(
                onClick = onDismiss
            ) {

                Text(
                    text = "Close",

                    color = ProfileAmberText,

                    fontFamily = InterFontFamily,

                    fontSize = 11.spotSp,

                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}


private fun isVideoUri(
    uri: String
): Boolean {

    val extension =
        uri
            .substringAfterLast(
                ".",
                ""
            )
            .substringBefore("?")
            .lowercase()


    return extension in
            setOf(
                "mp4",
                "mov",
                "m4v",
                "webm",
                "3gp",
                "mkv"
            )
}
