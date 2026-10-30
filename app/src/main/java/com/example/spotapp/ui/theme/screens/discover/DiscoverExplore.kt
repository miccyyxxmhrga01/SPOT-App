package com.example.spotapp.ui.theme.screens.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.spotapp.data.AccountType
import com.example.spotapp.data.HomeRepository
import com.example.spotapp.data.PublishedPost
import com.example.spotapp.data.SpotTalentCategories
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.models.*
import com.example.spotapp.ui.theme.screens.home.*

/**
 * Explore: browse by talent, what's trending, creators worth following, then everyone's recent posts. Choosing a
 * talent narrows the posts to it.
 */
@Composable
internal fun DiscoverExplore(profile: SpotUiProfile, onProfile: (SpotUiProfile) -> Unit, onReel: (SpotUiPost) -> Unit) {
    var posts by remember { mutableStateOf(HomeRepository.cachedRecent()) }
    var popular by remember { mutableStateOf(HomeRepository.cachedPopular()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    var category by rememberSaveable { mutableStateOf("All") }
    var opened by remember { mutableStateOf<PublishedPost?>(null) }
    val hidden = remember { mutableStateListOf<String>() }
    LifecycleDisposableEffect(profile.uid, retry) {
        loading = true; error = ""
        val listener = HomeRepository.listenRecent({ posts = it; loading = false }, { error = it; loading = false })
        val trending = HomeRepository.listenPopular({ popular = it }, { })
        onDispose { listener.remove(); trending.remove() }
    }
    val mine = profile.talentCategories.filter { it in SpotTalentCategories }.toSet()
    val talents = (listOf("All") + mine + SpotTalentCategories).distinct()
    val filtered = posts.filter { it.id !in hidden && (category == "All" || it.categories.any { c -> c.equals(category, true) }) }
    val creators = (popular + posts).map { it.author }
        .filter { it.uid.isNotBlank() && it.uid != profile.uid && it.accountType != AccountType.TALENT_SEEKER }.distinctBy { it.uid }.take(8)
    fun open(post: PublishedPost) {
        val video = post.media.firstOrNull { it.isVideo }
        if (video != null) onReel(post.asUiPost().copy(mediaUri = video.url)) else opened = post
    }
    val bottomInset = LocalSpotBottomContentPadding.current
    LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize().testTag("discover-feed"),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp + bottomInset),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "talents") { TalentBrowser(talents, category, mine) { category = it } }
        if (category == "All") {
            item(span = { GridItemSpan(maxLineSpan) }, key = "trending") { TrendingBento(popular.filterNot { it.id in hidden }.take(3), ::open) }
            item(span = { GridItemSpan(maxLineSpan) }, key = "creators") { CreatorsToKnow(creators, onProfile) }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "more") { ExploreTitle(if (category == "All") "More to explore" else "$category posts") }
        if (loading && posts.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { SpotLoading(true, SkeletonKind.GRID) }
        if (error.isNotBlank()) item(span = { GridItemSpan(maxLineSpan) }) {
            SpotEmptyState(Icons.Rounded.CloudOff, "Explore couldn't load", message = error,
                actionLabel = "Retry", onAction = { retry++ }, actionIcon = Icons.Rounded.Refresh)
        }
        if (!loading && error.isBlank() && filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            SpotEmptyState(Icons.Rounded.Explore, if (category == "All") "No posts yet" else "No posts in $category yet",
                message = "New talent from the community will show up here.")
        }
        items(filtered, key = { it.id }) { post ->
            DiscoverMediaTile(post, onOpen = { open(post) }, onProfile = { onProfile(post.author) }, onHide = { hidden.add(post.id) },
                modifier = Modifier.animateItem())
        }
    }
    opened?.let { ProfilePublishedPostViewer(it, onProfile, onReel, profile) { opened = null } }
}

@Composable
internal fun DiscoverMediaTile(post: PublishedPost, onOpen: () -> Unit, onProfile: () -> Unit,
    onHide: () -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    val media = post.media.firstOrNull { it.isVideo } ?: post.media.firstOrNull()
    Box(modifier.fillMaxWidth().aspectRatio(.78f).clip(RoundedCornerShape(20.dp))
        .background(if (SpotPalette.dark) SpotPalette.Track else Color(0xFFEEE7D8)).clickable(onClickLabel = if (media?.isVideo == true) "Open reel" else "Open post", onClick = onOpen)
        .testTag("discover-post-${post.id}")) {
        if (media != null) AsyncImage(media.previewUrl, post.caption.ifBlank { "${post.author.displayName}'s post" },
            Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(post.caption, Modifier.align(Alignment.Center).padding(18.dp), style = MaterialTheme.typography.titleSmall,
            color = SpotPalette.Ink, maxLines = 6, overflow = TextOverflow.Ellipsis)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .2f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .7f)))))
        if (media?.isVideo == true || post.media.size > 1) Icon(if (media?.isVideo == true) Icons.Rounded.PlayArrow else Icons.Rounded.Collections,
            if (media?.isVideo == true) "Video" else "Album", Modifier.align(Alignment.TopStart).padding(12.dp).size(19.dp), tint = Color.White)
        Box(Modifier.align(Alignment.TopEnd)) {
            SpotAnimatedIconButton(Icons.Rounded.MoreVert, "Post options", { menu = true }, tint = Color.White)
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Not interested") }, onClick = { menu = false; onHide() })
            }
        }
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onProfile).padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            SpotAvatar(post.author.profileImageUrl, "${post.author.displayName} profile",
                Modifier.size(26.dp).border(1.5.dp, Color.White.copy(alpha = .85f), CircleShape), post.author.uid)
            Text("@${post.author.username.removePrefix("@")}", Modifier.padding(start = 6.dp), color = Color.White,
                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
