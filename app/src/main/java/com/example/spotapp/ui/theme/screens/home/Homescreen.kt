package com.example.spotapp.ui.theme.screens.home

import com.example.spotapp.data.SpotLinks
import com.example.spotapp.ui.theme.components.*
import androidx.compose.material.icons.rounded.Bookmark
import com.example.spotapp.ui.theme.components.SpotAvatar

import com.example.spotapp.ui.theme.spotSp

import android.content.Intent
import android.net.Uri
import android.widget.VideoView

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send

import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.NotInterested
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search


import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.layout.ContentScale

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.viewinterop.AndroidView

import coil3.compose.AsyncImage

import com.example.spotapp.data.AccountType
import com.example.spotapp.ui.theme.InterFontFamily

import com.example.spotapp.ui.theme.models.SpotRoute
import com.example.spotapp.ui.theme.models.SpotUiPost
import com.example.spotapp.ui.theme.models.SpotUiProfile

import com.example.spotapp.ui.theme.navigation.SpotMainBottomNavigation


internal val HomeWhite get() = SpotPalette.Surface
internal val HomeBlack get() = SpotPalette.Ink
internal val HomeMuted get() = SpotPalette.Muted
internal val HomeLine get() = if (SpotPalette.dark) SpotPalette.Line else Color(0xFFE8E5DD)
internal val HomePlaceholder get() = if (SpotPalette.dark) SpotPalette.Track else Color(0xFFEDEDED)

internal val HomeGold =
    Color(0xFFFDD87A)

internal val HomeGoldDeep get() =
    SpotPalette.GoldDeep

internal val HomeBadgeRed get() =
    SpotPalette.Heart


internal val HomeStoryGradient get() =
    Brush.linearGradient(
        colors =
            listOf(
                HomeGoldDeep,
                Color(0xFFE8794C),
                Color(0xFFD2456E)
            )
    )


private val VideoExtensions =
    setOf(
        "mp4",
        "mov",
        "webm",
        "mkv",
        "3gp"
    )


internal fun isVideoUri(
    uri: String?
): Boolean {

    if (uri.isNullOrBlank()) {
        return false
    }


    return uri
        .substringAfterLast(
            ".",
            ""
        )
        .substringBefore(
            "?"
        )
        .lowercase() in VideoExtensions
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotHomePage(
    profile: SpotUiProfile,
    post: SpotUiPost,
    storyImageUri: String?,
    onPostChanged: (SpotUiPost) -> Unit,
    onStoryChanged: (String) -> Unit,
    onNavigate: (SpotRoute) -> Unit,
    onProfileClick: (SpotUiProfile) -> Unit,
    onOpenReels: (SpotUiPost) -> Unit,
    onSearchClick: () -> Unit = {
        onNavigate(
            SpotRoute.SEARCH
        )
    },
    hasUnseenStory: Boolean = true,
    showBottomNavigation: Boolean = true,
    showPost: Boolean = true,
    contentAfterStories: @Composable () -> Unit = {},
    feedContent: LazyListScope.() -> Unit = {},
    onOverlayVisible: (Boolean) -> Unit = {},
    storiesContent: (@Composable () -> Unit)? = null,
    notificationCount: Int = 0,
    onNotificationsClick: (() -> Unit)? = null,
    // Pulling down at the top of the feed calls [onRefresh]; [refreshing] keeps the spinner up until it's done.
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null
) {

    var showComments by remember {
        mutableStateOf(false)
    }


    var showStoryViewer by remember {
        mutableStateOf(false)
    }

    androidx.compose.runtime.SideEffect { onOverlayVisible(showStoryViewer || showComments) }


    val commentsSheetState =
        rememberModalBottomSheetState()


    val postComments =
        remember {
            mutableStateListOf<SpotUiComment>()
        }


    val context =
        LocalContext.current


    val storyPicker =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.GetContent()
        ) { uri ->

            if (uri != null) {

                onStoryChanged(
                    uri.toString()
                )


                showStoryViewer =
                    true
            }
        }


    if (showComments) {

        HomeCommentsSheet(
            sheetState =
                commentsSheetState,

            comments =
                postComments,

            currentUserAvatarUrl =
                profile.profileImageUrl,

            onDismiss = {

                showComments =
                    false
            },

            onSendComment = { text ->

                postComments.add(
                    SpotUiComment(
                        id =
                            "c${System.currentTimeMillis()}",

                        username =
                            profile.username,

                        avatarUrl =
                            profile.profileImageUrl,

                        text =
                            text
                    )
                )


                onPostChanged(
                    post.copy(
                        comments =
                            post.comments + 1
                    )
                )
            },

            onToggleLike = { commentId ->

                val index =
                    postComments.indexOfFirst {
                        it.id ==
                                commentId
                    }


                if (index != -1) {

                    val comment =
                        postComments[index]


                    postComments[index] =
                        comment.copy(
                            liked =
                                !comment.liked,

                            likes =
                                if (comment.liked) {

                                    (
                                            comment.likes - 1
                                            ).coerceAtLeast(0)

                                } else {

                                    comment.likes + 1
                                }
                        )
                }
            }
        )
    }


    if (showStoryViewer) {

        AlertDialog(
            onDismissRequest = {

                showStoryViewer =
                    false
            },

            confirmButton = {

                TextButton(
                    onClick = {

                        showStoryViewer =
                            false
                    }
                ) {

                    Text(
                        text =
                            "Close",

                        fontFamily =
                            InterFontFamily,

                        fontSize =
                            12.spotSp,
                lineHeight = 18.sp,

                        fontWeight =
                            FontWeight.Medium
                    )
                }
            },

            title = {

                Text(
                    text =
                        "@${profile.username}",

                    fontFamily =
                        InterFontFamily,

                    fontSize =
                        15.spotSp,
                lineHeight = 22.sp,

                    fontWeight =
                        FontWeight.SemiBold
                )
            },

            text = {

                if (
                    storyImageUri
                        .isNullOrBlank()
                ) {

                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        Text(
                            text =
                                "No story image yet.",

                            fontFamily =
                                InterFontFamily,

                            fontSize =
                                12.spotSp
                        )


                        Spacer(
                            modifier =
                                Modifier.height(
                                    10.dp
                                )
                        )


                        Button(
                            onClick = {

                                storyPicker.launch(
                                    "image/*"
                                )
                            },

                            colors =
                                ButtonDefaults
                                    .buttonColors(
                                        containerColor =
                                            HomeGold,

                                        contentColor =
                                            HomeBlack
                                    )
                        ) {

                            Text(
                                text =
                                    "Add story",

                                fontFamily =
                                    InterFontFamily,

                                fontSize =
                                    12.spotSp,
                lineHeight = 18.sp,

                                fontWeight =
                                    FontWeight.SemiBold
                            )
                        }
                    }

                } else {

                    AsyncImage(
                        model =
                            storyImageUri,

                        contentDescription =
                            "Story",

                        contentScale =
                            ContentScale.Crop,

                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(
                                    0.78f
                                )
                                .clip(
                                    RoundedCornerShape(
                                        16.dp
                                    )
                                )
                    )
                }
            }
        )
    }


    Scaffold(
        containerColor =
            HomeWhite,

        contentWindowInsets =
            WindowInsets(
                0,
                0,
                0,
                0
            ),

        bottomBar = {

            if (showBottomNavigation) {

                SpotMainBottomNavigation(
                    selectedRoute =
                        SpotRoute.HOME,

                    profile =
                        profile,

                    onNavigate =
                        onNavigate
                )
            }
        }
    ) { innerPadding ->

        val pull = rememberPullToRefreshState()
        val feed: @Composable (Modifier) -> Unit = { modifier -> LazyColumn(modifier.testTag("home-feed")) {
            item(key = "home-intro") {
                HomeHeader(onSearchClick, notificationCount, onNotificationsClick)
                if (storiesContent != null) storiesContent() else HomeStoryRow(profile, storyImageUri, hasUnseenStory,
                    onStoryClick = { showStoryViewer = true }, onAddStory = { storyPicker.launch("image/*") })
                contentAfterStories()
            }
            feedContent()
            if (showPost) item(key = "local-post") {
            HomePostCard(
                post =
                    post,

                onProfileClick = {

                    onProfileClick(
                        post.user
                    )
                },

                onOpenReels = {

                    onOpenReels(
                        post
                    )
                },

                onLike = {

                    onPostChanged(
                        post.copy(
                            liked =
                                !post.liked,

                            likes =
                                if (post.liked) {

                                    (
                                            post.likes - 1
                                            ).coerceAtLeast(0)

                                } else {

                                    post.likes + 1
                                }
                        )
                    )
                },

                onComment = {

                    showComments =
                        true
                },

                onShare = {

                    val shareIntent =
                        Intent(
                            Intent.ACTION_SEND
                        ).apply {

                            type =
                                "text/plain"


                            putExtra(
                                Intent.EXTRA_TEXT,
                                // The link opens the post in SPOT; the composer's draft has none yet.
                                "${post.user.displayName} on SPOT: ${post.caption}" +
                                    if (post.id == "home-draft") "" else "\n${SpotLinks.post(post.id)}"
                            )
                        }


                    context.startActivity(
                        Intent.createChooser(
                            shareIntent,
                            "Share SPOT post"
                        )
                    )


                    onPostChanged(
                        post.copy(
                            shares =
                                post.shares + 1
                        )
                    )
                },

                onSave = {

                    onPostChanged(
                        post.copy(
                            saved =
                                !post.saved
                        )
                    )
                },

                onNotInterested = {},

                // Your own post: nothing to report.
                onReport = null
            )
            }
            item { Spacer(Modifier.height(14.dp + LocalSpotBottomContentPadding.current)) }
        } }
        val page = Modifier.fillMaxSize().spotAmbientBackground(HomeWhite).padding(innerPadding)
        if (onRefresh == null) feed(page)
        else PullToRefreshBox(refreshing, onRefresh, page.testTag("home-refresh"), state = pull, indicator = {
            PullToRefreshDefaults.Indicator(pull, refreshing, Modifier.align(Alignment.TopCenter),
                containerColor = SpotPalette.Surface, color = SpotPalette.GoldDeep)
        }) { feed(Modifier.fillMaxSize()) }
    }
}


@Composable
internal fun SpotWordmark(modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(buildAnnotatedString {
            append("SP")
            withStyle(SpanStyle(color = HomeGoldDeep)) { append("O") }
            append("T")
        }, color = HomeBlack, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold,
            fontSize = 22.spotSp, lineHeight = 28.sp, letterSpacing = 1.5.sp)
        Box(Modifier.width(24.dp).height(2.dp).clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(HomeGoldDeep, HomeGold))))
    }
}


@Composable
private fun HomeStoryRow(
    profile: SpotUiProfile,
    storyImageUri: String?,
    hasUnseenStory: Boolean,
    onStoryClick: () -> Unit,
    onAddStory: () -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(
                    96.dp
                )
                .padding(
                    start =
                        16.dp,

                    end =
                        12.dp,

                    top =
                        8.dp,

                    bottom =
                        5.dp
                ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Box(
                modifier =
                    Modifier
                        .size(
                            56.dp
                        )
                        .clickable {

                            if (
                                storyImageUri
                                    .isNullOrBlank()
                            ) {

                                onAddStory()

                            } else {

                                onStoryClick()
                            }
                        },

                contentAlignment =
                    Alignment.Center
            ) {

                if (
                    !storyImageUri.isNullOrBlank() &&
                    hasUnseenStory
                ) {

                    Box(
                        modifier =
                            Modifier
                                .size(
                                    56.dp
                                )
                                .background(
                                    HomeStoryGradient,
                                    CircleShape
                                )
                    )
                }


                Box(
                    modifier =
                        Modifier
                            .size(
                                51.dp
                            )
                            .background(
                                HomeWhite,
                                CircleShape
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    ProfileAvatar(
                        profile =
                            profile,

                        imageOverride =
                            storyImageUri,

                        size =
                            47
                    )
                }


                Box(
                    modifier =
                        Modifier
                            .align(
                                Alignment.BottomEnd
                            )
                            .size(
                                17.dp
                            )
                            .background(
                                HomeWhite,
                                CircleShape
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Box(
                        modifier =
                            Modifier
                                .size(
                                    14.dp
                                )
                                .shadow(
                                    elevation =
                                        1.dp,

                                    shape =
                                        CircleShape
                                )
                                .background(
                                    HomeGoldDeep,
                                    CircleShape
                                ),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        Icon(
                            imageVector =
                                Icons.Rounded.Add,

                            contentDescription =
                                "Add story",

                            tint =
                                HomeBlack,

                            modifier =
                                Modifier.size(
                                    10.dp
                                )
                        )
                    }
                }
            }


            Spacer(
                modifier =
                    Modifier.height(
                        4.dp
                    )
            )


            Text(
                text =
                    "Your Story",

                fontFamily =
                    InterFontFamily,

                fontSize =
                    11.spotSp,
                lineHeight = 16.sp,

                fontWeight =
                    FontWeight.Medium,

                color =
                    HomeBlack
            )
        }
    }
}


/**
 * Creator or Spotter as a small icon next to a name: a gold sparkle for Creators, a dark person-search for Spotters.
 * Only the profile page spells the role out. [described] is false where the role's name is already shown.
 */
@Composable
internal fun AccountRoleBadge(type: AccountType, modifier: Modifier = Modifier, size: Dp = 18.dp, described: Boolean = true) {
    val spotter = type == AccountType.TALENT_SEEKER
    Box(modifier.size(size).background(if (spotter) SolidColor(SpotPalette.OnGold) else SpotGoldGradient, CircleShape)
        .then(if (described) Modifier.semantics { contentDescription = type.label } else Modifier),
        contentAlignment = Alignment.Center) {
        Icon(if (spotter) Icons.Rounded.PersonSearch else Icons.Rounded.AutoAwesome, null, Modifier.size(size * .64f),
            tint = if (spotter) SpotPalette.GoldBright else SpotPalette.OnGold)
    }
}
