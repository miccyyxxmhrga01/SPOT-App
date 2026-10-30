package com.example.spotapp.ui.theme.screens.discover

import com.example.spotapp.ui.theme.components.spotAmbientBackground
import com.example.spotapp.ui.theme.components.SpotAvatar
import com.example.spotapp.ui.theme.components.SpotSectionTab
import com.example.spotapp.ui.theme.components.SpotSectionTabs
import com.example.spotapp.ui.theme.components.SpotScreenHeader
import com.example.spotapp.ui.theme.components.SpotHeaderAction
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.components.SpotSearchField
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role

import com.example.spotapp.ui.theme.spotSp

import android.net.Uri
import android.widget.VideoView

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send

import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified

import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Groups

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.layout.ContentScale

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.viewinterop.AndroidView

import coil3.compose.AsyncImage

import com.example.spotapp.ui.theme.InterFontFamily

import com.example.spotapp.ui.theme.models.SpotRoute
import com.example.spotapp.ui.theme.models.SpotUiPost
import com.example.spotapp.ui.theme.models.SpotUiProfile

import com.example.spotapp.ui.theme.navigation.SpotMainBottomNavigation



@Composable
fun DiscoverScreen(profile: SpotUiProfile, onNavigate: (SpotRoute) -> Unit,
    onProfileClick: (SpotUiProfile) -> Unit, onOpenReels: (SpotUiPost) -> Unit,
    showBottomNavigation: Boolean = true, onWorkOpen: (String) -> Unit = {}, onMessage: (String) -> Unit = {},
    onChat: (String) -> Unit = {}) {
    var selectedTopTab by rememberSaveable { mutableStateOf("Explore") }
    var eventSearch by rememberSaveable { mutableStateOf("") }
    var groupSearch by rememberSaveable { mutableStateOf("") }
    var groupFilters by rememberSaveable { mutableStateOf(false) }
    Scaffold(modifier = Modifier.spotAmbientBackground(SpotPalette.Ivory), containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = {
        if (showBottomNavigation) SpotMainBottomNavigation(SpotRoute.DISCOVER, profile, onNavigate = onNavigate)
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            DiscoverHeader(selectedTopTab, eventSearch, { eventSearch = it.take(100) }, groupSearch, { groupSearch = it.take(100) },
                { groupFilters = !groupFilters }) { onNavigate(SpotRoute.SEARCH) }
            com.example.spotapp.ui.theme.screens.seeker.SpotterShortcuts(profile, onWorkOpen)
            DiscoverSections(selectedTopTab) { selectedTopTab = it }
            androidx.compose.animation.AnimatedContent(selectedTopTab, label = "Discover section") { tab ->
                when (tab) {
                    "Groups" -> DiscoverCommunity(false, profile, onProfileClick, onChat, search = groupSearch, filtersOpen = groupFilters)
                    "Events" -> DiscoverEvents(profile, eventSearch, onProfileClick, onChat)
                    "Collabs" -> DiscoverCollaborations(profile, onProfileClick, onMessage, onChat)
                    else -> DiscoverExplore(profile, onProfileClick, onOpenReels)
                }
            }
        }
    }
}

/**
 * The title, then a search that fits the tab, always in the same place: Events and Groups search in place (Groups
 * with its category filter); Explore and Collabs open people search.
 */
@Composable
private fun DiscoverHeader(tab: String, eventSearch: String, onEventSearch: (String) -> Unit, groupSearch: String,
    onGroupSearch: (String) -> Unit, onGroupFilters: () -> Unit, onPeopleSearch: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text("Discover", Modifier.padding(start = 20.dp, end = 16.dp, top = 12.dp), style = MaterialTheme.typography.headlineLarge,
            color = SpotPalette.Ink)
        when (tab) {
            "Events" -> SpotSearchField(eventSearch, onEventSearch, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).testTag("discover-event-search"),
                "Search events")
            "Groups" -> SpotSearchField(groupSearch, onGroupSearch, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).testTag("discover-group-search"),
                "Search groups", extra = { IconButton(onGroupFilters) { Icon(Icons.Rounded.Tune, "Filter categories", Modifier.size(20.dp)) } })
            else -> Surface(onClick = onPeopleSearch, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)
                .heightIn(min = 50.dp).testTag("discover-search"), shape = RoundedCornerShape(25.dp), color = SpotPalette.Surface,
                border = BorderStroke(1.dp, SpotPalette.Line)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = SpotPalette.GoldInk)
                    Text("Search people", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyLarge, color = SpotPalette.Muted)
                }
            }
        }
    }
}


/** Explore · Events · Collabs · Groups, each an icon over its name; the chosen one sits on gold. */
@Composable
internal fun DiscoverSections(selected: String, onSelected: (String) -> Unit) {
    val tabs = listOf("Explore" to Icons.Outlined.Explore, "Events" to Icons.Outlined.CalendarMonth,
        "Collabs" to Icons.Outlined.Handshake, "Groups" to Icons.Outlined.Groups)
    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
        .background(SpotPalette.Surface).border(1.dp, SpotPalette.Line, RoundedCornerShape(22.dp)).padding(5.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        tabs.forEach { (id, icon) ->
            val chosen = id == selected
            val fill by animateColorAsState(if (chosen) SpotPalette.GoldSoft else Color.Transparent, tween(180), label = "discover-tab-$id")
            Column(Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(17.dp)).background(fill)
                .selectable(chosen, role = Role.Tab) { onSelected(id) }.testTag("section-$id").padding(vertical = 6.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(icon, null, Modifier.size(22.dp), tint = if (chosen) SpotPalette.Ink else SpotPalette.Muted)
                Text(id, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium, color = if (chosen) SpotPalette.Ink else SpotPalette.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}



