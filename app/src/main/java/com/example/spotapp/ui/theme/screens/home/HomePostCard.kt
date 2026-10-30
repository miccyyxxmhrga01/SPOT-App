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

// The home feed's post card and its media: photo pager, video player, caption and actions. Split from Homescreen.kt.

@Composable
internal fun HomePostCard(
    post: SpotUiPost, onProfileClick: () -> Unit, onLike: () -> Unit,
    onComment: () -> Unit, onShare: () -> Unit, onSave: () -> Unit,
    onNotInterested: () -> Unit, onReport: (() -> Unit)?, onOpenReels: () -> Unit,
    mediaContent: (@Composable () -> Unit)? = null, modifier: Modifier = Modifier,
    // Opens the full post options sheet; without it "⋯" shows the short menu.
    onMore: (() -> Unit)? = null
) {
    var menu by remember { mutableStateOf(false) }
    SpotCard(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClickLabel = "Open ${post.user.displayName}'s profile", onClick = onProfileClick),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(SpotGoldGradient, CircleShape).padding(2.dp)
                    .background(HomeWhite, CircleShape).padding(1.5.dp)) {
                    SpotAvatar(post.user.profileImageUrl, "${post.user.displayName} profile picture", Modifier.fillMaxSize(), post.user.uid)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("@${post.user.username.removePrefix("@")}", Modifier.weight(1f, fill = false), color = HomeBlack,
                            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        post.user.accountType?.let { AccountRoleBadge(it, Modifier.padding(start = 6.dp)) }
                    }
                    // A post without a talent category is a personal one, so every header reads the same way.
                    Text(listOf(post.category.ifBlank { "Personal" }, post.postedTime, if (post.edited) "Edited" else "").filter { it.isNotBlank() }.joinToString(" · "),
                        color = HomeMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box {
                SpotAnimatedIconButton(Icons.Rounded.MoreHoriz, "More options", { if (onMore != null) onMore() else menu = true }, tint = HomeMuted)
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Not interested") }, leadingIcon = { Icon(Icons.Rounded.NotInterested, null) },
                        onClick = { menu = false; onNotInterested() })
                    // Not offered on your own posts.
                    if (onReport != null) DropdownMenuItem(text = { Text("Report") }, leadingIcon = { Icon(Icons.Rounded.Flag, null) },
                        onClick = { menu = false; onReport() })
                }
            }
        }
        Box(Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(18.dp))) {
            if (mediaContent != null) mediaContent() else HomePostMedia(post, onOpenReels)
        }
        if (post.caption.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            ExpandableCaption(post.user.username, post.caption)
        }
        if (post.aiCaption) Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(13.dp), tint = HomeGoldDeep)
            Text("AI-assisted caption", Modifier.padding(start = 4.dp), color = HomeMuted, style = MaterialTheme.typography.labelSmall)
        }
        if (post.tags.isNotEmpty()) Row(Modifier.padding(horizontal = 16.dp).padding(top = 5.dp)) {
            Text(post.tags.joinToString("  ") { "#$it" }, color = HomeGoldDeep, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (post.aiTagged) Text("Auto-tagged", color = HomeMuted, style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // A hidden like count shows to the owner only; the caller clears likesHidden for them.
            HomeReaction(if (post.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                "Like", if (post.likesHidden) 0 else post.likes, onLike, if (post.liked) HomeBadgeRed else HomeBlack, post.liked)
            HomeReaction(Icons.Rounded.ChatBubbleOutline, "Comment", post.comments, onComment)
            HomeReaction(Icons.AutoMirrored.Outlined.Send, "Share", post.shares, onShare)
            Spacer(Modifier.weight(1f))
            SpotAnimatedIconButton(if (post.saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                if (post.saved) "Saved" else "Save", onSave, tint = if (post.saved) HomeGoldDeep else HomeBlack, selected = post.saved)
        }
    }
}

@Composable
private fun HomeReaction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, count: Int,
    onClick: () -> Unit, color: Color = HomeBlack, selected: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SpotAnimatedIconButton(icon, label, onClick, tint = color, selected = selected)
        if (count > 0) Text(formatHomeCount(count), color = HomeBlack, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(end = 6.dp).offset(x = (-6).dp))
    }
}

@Composable
private fun HomePostMedia(
    post: SpotUiPost,
    onOpenReels: () -> Unit
) {

    when {

        post.localImageResources
            .isNotEmpty() -> {

            HomeImagePager(
                images =
                    post.localImageResources
            )
        }


        post.localVideoRes !=
                null -> {

            HomeVideoPlayer(
                localVideoRes =
                    post.localVideoRes,

                videoUri =
                    null,

                onOpenReels =
                    onOpenReels
            )
        }


        isVideoUri(
            post.mediaUri
        ) -> {

            HomeVideoPlayer(
                localVideoRes =
                    null,

                videoUri =
                    post.mediaUri,

                onOpenReels =
                    onOpenReels
            )
        }


        !post.mediaUri
            .isNullOrBlank() -> {

            AsyncImage(
                model =
                    post.mediaUri,

                contentDescription =
                    "Post media",

                contentScale =
                    ContentScale.Crop,

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal =
                                8.dp
                        )
                        .aspectRatio(
                            1f
                        )
                        .clip(
                            RoundedCornerShape(
                                14.dp
                            )
                        )
            )
        }


        else -> {

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal =
                                8.dp
                        )
                        .aspectRatio(
                            1f
                        )
                        .clip(
                            RoundedCornerShape(
                                14.dp
                            )
                        )
                        .background(
                            HomePlaceholder
                        ),

                contentAlignment =
                    Alignment.Center
            ) {

                Icon(
                    imageVector =
                        Icons.Rounded.Person,

                    contentDescription =
                        null,

                    tint =
                        HomeMuted,

                    modifier =
                        Modifier.size(
                            28.dp
                        )
                )
            }
        }
    }
}


@Composable
private fun HomeImagePager(
    images: List<Int>
) {

    val pagerState =
        rememberPagerState(
            pageCount = {
                images.size
            }
        )


    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal =
                        8.dp
                )
                .aspectRatio(
                    1f
                )
                .clip(
                    RoundedCornerShape(
                        14.dp
                    )
                )
                .background(
                    HomePlaceholder
                )
    ) {

        HorizontalPager(
            state =
                pagerState,

            modifier =
                Modifier.fillMaxSize()
        ) { page ->

            Image(
                painter =
                    painterResource(
                        id =
                            images[page]
                    ),

                contentDescription =
                    "Post image ${page + 1}",

                contentScale =
                    ContentScale.Crop,

                modifier =
                    Modifier.fillMaxSize()
            )
        }


        if (
            images.size >
            1
        ) {

            Surface(
                modifier =
                    Modifier
                        .align(
                            Alignment.TopEnd
                        )
                        .padding(
                            10.dp
                        ),

                shape =
                    RoundedCornerShape(
                        20.dp
                    ),

                color =
                    HomeBlack.copy(
                        alpha =
                            0.55f
                    )
            ) {

                Text(
                    text =
                        "${pagerState.currentPage + 1}/${images.size}",

                    color =
                        HomeWhite,

                    fontFamily =
                        InterFontFamily,

                    fontSize =
                        11.spotSp,
                lineHeight = 16.sp,

                    fontWeight =
                        FontWeight.SemiBold,

                    modifier =
                        Modifier.padding(
                            horizontal =
                                8.dp,

                            vertical =
                                4.dp
                        )
                )
            }


            Row(
                modifier =
                    Modifier
                        .align(
                            Alignment.BottomCenter
                        )
                        .padding(
                            bottom =
                                10.dp
                        ),

                horizontalArrangement =
                    Arrangement.spacedBy(
                        4.dp
                    )
            ) {

                repeat(
                    images.size
                ) { index ->

                    Box(
                        modifier =
                            Modifier
                                .size(
                                    if (
                                        pagerState.currentPage ==
                                        index
                                    ) {

                                        6.dp

                                    } else {

                                        5.dp
                                    }
                                )
                                .background(
                                    color =
                                        if (
                                            pagerState.currentPage ==
                                            index
                                        ) {

                                            HomeWhite

                                        } else {

                                            HomeWhite.copy(
                                                alpha =
                                                    0.45f
                                            )
                                        },

                                    shape =
                                        CircleShape
                                )
                    )
                }
            }
        }
    }
}


@Composable
private fun HomeVideoPlayer(
    localVideoRes: Int?,
    videoUri: String?,
    onOpenReels: () -> Unit
) {

    val context =
        LocalContext.current


    var videoView by remember {
        mutableStateOf<VideoView?>(
            null
        )
    }


    val uri =
        remember(
            localVideoRes,
            videoUri
        ) {

            when {

                localVideoRes != null -> {

                    Uri.parse(
                        "android.resource://${context.packageName}/$localVideoRes"
                    )
                }


                !videoUri.isNullOrBlank() -> {

                    Uri.parse(
                        videoUri
                    )
                }


                else -> {

                    null
                }
            }
        }


    DisposableEffect(
        uri
    ) {

        onDispose {

            videoView?.stopPlayback()

            videoView =
                null
        }
    }


    if (uri == null) {
        return
    }


    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal =
                        8.dp
                )
                .aspectRatio(
                    1f
                )
                .clip(
                    RoundedCornerShape(
                        14.dp
                    )
                )
                .background(
                    Color.Black
                ),

        contentAlignment =
            Alignment.Center
    ) {

        AndroidView(
            factory = {

                VideoView(
                    context
                ).apply {

                    tag =
                        uri.toString()


                    setVideoURI(
                        uri
                    )


                    setOnPreparedListener { mediaPlayer ->

                        mediaPlayer.isLooping =
                            true


                        mediaPlayer.setVolume(
                            0f,
                            0f
                        )


                        start()
                    }


                    videoView =
                        this
                }
            },

            update = { view ->

                if (
                    view.tag !=
                    uri.toString()
                ) {

                    view.tag =
                        uri.toString()


                    view.setVideoURI(
                        uri
                    )
                }
            },

            modifier =
                Modifier.fillMaxSize()
        )


        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickable {

                        videoView?.pause()


                        onOpenReels()
                    }
        )


        Surface(
            modifier =
                Modifier
                    .align(
                        Alignment.BottomEnd
                    )
                    .padding(
                        10.dp
                    ),

            shape =
                RoundedCornerShape(
                    20.dp
                ),

            color =
                HomeBlack.copy(
                    alpha =
                        0.48f
                )
        ) {

            Text(
                text =
                    "Watch reel",

                color =
                    HomeWhite,

                fontFamily =
                    InterFontFamily,

                fontSize =
                    11.spotSp,
                lineHeight = 16.sp,

                fontWeight =
                    FontWeight.SemiBold,

                modifier =
                    Modifier.padding(
                        horizontal =
                            8.dp,

                        vertical =
                            4.dp
                    )
            )
        }
    }
}


@Composable
private fun ExpandableCaption(username: String, text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    var overflowing by remember(text) { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp).animateContentSize(tween(180))) {
        Text(text, color = HomeBlack, style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflowing = it.hasVisualOverflow })
        if (overflowing || expanded) Text(if (expanded) "See less" else "See more", color = HomeMuted,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(vertical = 8.dp))
    }
}


@Composable
internal fun ProfileAvatar(
    profile: SpotUiProfile,
    imageOverride: String?,
    size: Int
) {
    SpotAvatar(imageOverride ?: profile.profileImageUrl, "${profile.displayName} profile picture",
        Modifier.size(size.dp).border(1.dp, HomeLine, CircleShape), if (imageOverride.isNullOrBlank()) profile.uid else "")
}


private fun formatHomeCount(
    count: Int
): String {

    return when {

        count >=
                1_000_000 -> {

            String.format(
                "%.1fM",
                count /
                        1_000_000f
            )
        }


        count >=
                1_000 -> {

            String.format(
                "%.1fK",
                count /
                        1_000f
            )
        }


        else -> {

            count.toString()
        }
    }
}
