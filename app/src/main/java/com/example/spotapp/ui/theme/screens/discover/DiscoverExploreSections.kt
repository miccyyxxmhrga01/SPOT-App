package com.example.spotapp.ui.theme.screens.discover

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.spotapp.data.PrivacyRepository
import com.example.spotapp.data.PublishedPost
import com.example.spotapp.data.SocialRepository
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** An icon for each of SPOT's talents, and older names for them. */
internal fun talentIcon(talent: String): ImageVector = when (talent.lowercase()) {
    "all" -> Icons.Rounded.Apps
    "singing", "singer", "vocals" -> Icons.Rounded.Mic
    "guitar", "guitarist", "music", "musician" -> Icons.Rounded.MusicNote
    "piano", "pianist" -> Icons.Rounded.Piano
    "drums", "drummer" -> Icons.Rounded.Album
    "drawing", "illustrator" -> Icons.Rounded.Draw
    "painting", "painter" -> Icons.Rounded.Brush
    "dancing", "dancer", "dance" -> Icons.Rounded.Nightlife
    "photography", "photographer" -> Icons.Rounded.PhotoCamera
    "basketball" -> Icons.Rounded.SportsBasketball
    "volleyball" -> Icons.Rounded.SportsVolleyball
    "futsal" -> Icons.Rounded.SportsSoccer
    "cooking", "chef" -> Icons.Rounded.Restaurant
    "coding", "programmer" -> Icons.Rounded.Code
    "art", "artist" -> Icons.Rounded.Palette
    else -> Icons.Rounded.AutoAwesome
}

@Composable
internal fun ExploreTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(start = 8.dp, top = 18.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold, color = SpotPalette.Ink)
}

/** Talents as tiles; the chosen one filters the posts below. Your own talents come right after All. */
@Composable
internal fun TalentBrowser(talents: List<String>, selected: String, mine: Set<String>, onSelect: (String) -> Unit) {
    Column {
        ExploreTitle("Browse by talent")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
            items(talents, key = { it }) { talent ->
                val chosen = talent == selected
                // Wide enough, with room below the name, that the rounded ripple never cuts off letters like g.
                Column(Modifier.width(72.dp).clip(RoundedCornerShape(16.dp)).selectable(chosen, role = Role.Tab) { onSelect(talent) }
                    .padding(bottom = 6.dp).testTag("talent-$talent"), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(58.dp).clip(RoundedCornerShape(20.dp)).background(if (chosen) SpotPalette.GoldSoft else SpotPalette.Surface)
                        .border(if (chosen) 1.5.dp else 1.dp, if (chosen) SpotPalette.GoldLine else SpotPalette.Line, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center) {
                        Icon(talentIcon(talent), null, Modifier.size(24.dp), tint = SpotPalette.GoldInk)
                        if (talent in mine) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(7.dp).background(SpotPalette.GoldDeep, CircleShape))
                    }
                    Text(talent, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, color = SpotPalette.Ink,
                        fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** The three most popular posts: the top one tall on the left, the next two stacked beside it. */
@Composable
internal fun TrendingBento(posts: List<PublishedPost>, onOpen: (PublishedPost) -> Unit) {
    if (posts.isEmpty()) return
    Column(Modifier.testTag("trending")) {
        ExploreTitle("Trending on SPOT")
        Row(Modifier.fillMaxWidth().height(264.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrendingTile(posts[0], 1, Modifier.weight(1f).fillMaxHeight(), large = true) { onOpen(posts[0]) }
            if (posts.size > 1) Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                posts.drop(1).take(2).forEachIndexed { index, post ->
                    TrendingTile(post, index + 2, Modifier.weight(1f).fillMaxWidth()) { onOpen(post) }
                }
            }
        }
    }
}

@Composable
private fun TrendingTile(post: PublishedPost, rank: Int, modifier: Modifier, large: Boolean = false, onClick: () -> Unit) {
    val media = post.media.firstOrNull()
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(SpotPalette.Track)
        .clickable(onClickLabel = "Open ${post.author.displayName}'s post", onClick = onClick).testTag("trending-${post.id}")) {
        if (media != null) AsyncImage(media.previewUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(post.caption, Modifier.fillMaxSize().background(SpotPalette.GoldWash).padding(12.dp), style = MaterialTheme.typography.bodyMedium,
            color = SpotPalette.Ink, maxLines = 8, overflow = TextOverflow.Ellipsis)
        if (large) Text("#$rank" + (post.categories.firstOrNull()?.let { " ${it.uppercase()}" } ?: ""),
            Modifier.align(Alignment.TopStart).padding(10.dp).background(SpotPalette.Surface.copy(alpha = .92f), RoundedCornerShape(10.dp))
                .padding(horizontal = 9.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            color = SpotPalette.GoldInk, maxLines = 1)
        if (media?.isVideo == true) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp).background(Color.Black.copy(alpha = .45f), CircleShape),
            contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, "Video", Modifier.size(16.dp), tint = Color.White) }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .64f))))
            .padding(start = 10.dp, end = 10.dp, top = 22.dp, bottom = 9.dp)) {
            Text(post.author.displayName.ifBlank { post.author.username }, color = Color.White, style = MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (post.likes > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Favorite, null, Modifier.size(12.dp), tint = Color.White)
                Text(post.likes.toString(), Modifier.padding(start = 4.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Creators whose work is getting attention, each with a Follow button. */
@Composable
internal fun CreatorsToKnow(creators: List<SpotUiProfile>, onProfile: (SpotUiProfile) -> Unit) {
    if (creators.isEmpty()) return
    Column(Modifier.testTag("creators-to-know")) {
        ExploreTitle("Creators to know")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
            items(creators, key = { it.uid }) { person -> CreatorCard(person, onProfile) }
        }
    }
}

@Composable
private fun CreatorCard(person: SpotUiProfile, onProfile: (SpotUiProfile) -> Unit) {
    val scope = rememberCoroutineScope()
    var following by remember(person.uid) { mutableStateOf<Boolean?>(null) }
    var requested by remember(person.uid) { mutableStateOf(false) }
    var busy by remember(person.uid) { mutableStateOf(false) }
    LifecycleDisposableEffect(person.uid) {
        val follow = SocialRepository.shared.listenFollowing(person.uid, { following = it }, { })
        val request = PrivacyRepository.shared.listenRequested(person.uid) { requested = it }
        onDispose { follow.remove(); request.remove() }
    }
    Surface(Modifier.width(144.dp).testTag("creator-${person.uid}"), shape = RoundedCornerShape(20.dp), color = SpotPalette.Surface,
        border = BorderStroke(1.dp, SpotPalette.Line)) {
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = "View profile") { onProfile(person) },
                horizontalAlignment = Alignment.CenterHorizontally) {
                SpotAvatar(person.profileImageUrl, person.displayName, Modifier.size(60.dp), person.uid)
                Text(person.displayName.ifBlank { person.username }, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall,
                    color = SpotPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(person.talentCategories.firstOrNull() ?: person.talent.substringBefore(',').ifBlank { "Creator" },
                    style = MaterialTheme.typography.labelMedium, color = SpotPalette.GoldInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val label = when { following == true -> "Following"; requested -> "Requested"; else -> "Follow" }
            val quiet = following == true || requested
            Button(onClick = {
                if (busy || following == null) return@Button
                busy = true
                scope.launch {
                    try {
                        when {
                            following == true -> SocialRepository.shared.setFollowing(person.uid, false)
                            requested -> PrivacyRepository.shared.requestFollow(person.uid, false)
                            else -> try { SocialRepository.shared.setFollowing(person.uid, true) }
                                catch (e: IllegalStateException) {
                                    // A private account takes a follow request instead.
                                    if (e.message?.contains("private", true) == true) PrivacyRepository.shared.requestFollow(person.uid, true) else throw e
                                }
                        }
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                    finally { busy = false }
                }
            }, Modifier.padding(top = 10.dp).fillMaxWidth().heightIn(min = 38.dp), enabled = !busy && following != null,
                contentPadding = PaddingValues(horizontal = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (quiet) SpotPalette.Track else SpotPalette.Gold,
                    contentColor = if (quiet) SpotPalette.Ink else SpotPalette.OnGold)) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
