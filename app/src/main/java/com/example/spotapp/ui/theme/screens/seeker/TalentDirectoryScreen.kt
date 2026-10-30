package com.example.spotapp.ui.theme.screens.seeker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.components.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import com.example.spotapp.data.PageCursor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal val SeekerGold get() = SpotPalette.GoldDeep
internal val SeekerInk get() = SpotPalette.Ink
internal val SeekerMuted get() = SpotPalette.Muted

@Composable
internal fun TalentDirectoryScreen(profile: SpotUiProfile, onProfile: (SpotUiProfile) -> Unit,
    onInvite: ((SpotUiProfile) -> Unit)? = null, initialCategory: String = "") {
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(initialCategory) }
    var people by remember { mutableStateOf(emptyList<SpotUiProfile>()) }
    var cursor by remember { mutableStateOf<PageCursor?>(null) }
    var more by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(0) }
    LaunchedEffect(profile.uid, search, category, revision, page) {
        loading = true; error = ""
        if (page == 0) { people = emptyList(); cursor = null; more = false; delay(250) }
        try {
            val result = ProfileRepository.shared.creators(search, category, if (page == 0) null else cursor)
            people = (if (page == 0) result.items else people + result.items).distinctBy { it.uid }.filter { it.uid != profile.uid }
            cursor = result.cursor; more = result.hasMore
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Talent couldn't load. Check your connection and retry." }
        finally { loading = false }
    }
    val bottom = LocalSpotBottomContentPadding.current
    Column(Modifier.fillMaxSize().testTag("talent-directory")) {
        SpotSearchField(search, { search = it.take(100); page = 0 }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), "Name or @username", loading)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items((listOf("") + profile.seekingTalents + SpotTalentCategories).distinct()) { value ->
                FilterChip(category == value, { category = value; page = 0 }, label = { Text(value.ifBlank { "All talents" }) },
                    shape = RoundedCornerShape(14.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SpotPalette.GoldSoft, selectedLabelColor = SeekerGold))
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottom + 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (loading && people.isEmpty()) item { SpotLoading(true, SkeletonKind.ROWS) }
            items(people, key = { it.uid }) { creator ->
                TalentCreatorCard(creator, { onProfile(creator) }, onInvite?.let { { it(creator) } }, Modifier.animateItem())
            }
            if (error.isNotBlank()) item { TextButton({ revision++ }) { Text("$error Retry", color = SeekerGold) } }
            if (!loading && error.isBlank() && people.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.PersonSearch, null, Modifier.size(40.dp), tint = SeekerGold)
                    Text(if (search.isBlank() && category.isBlank()) "No creators yet" else "No matching creators", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall)
                    if (search.isNotBlank() || category.isNotBlank()) TextButton({ search = ""; category = ""; page = 0 }) { Text("Clear filters") }
                }
            }
            if (more) item { TextButton({ page++ }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = SeekerGold, strokeWidth = 2.dp) else Text("More creators")
            } }
        }
    }
}

@Composable
internal fun TalentCreatorCard(creator: SpotUiProfile, onProfile: () -> Unit, onInvite: (() -> Unit)?, modifier: Modifier = Modifier) {
    Surface(onClick = onProfile, modifier = modifier.fillMaxWidth().testTag("talent-${creator.uid}"), color = SpotPalette.Surface,
        shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, SpotPalette.Line)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SpotAvatar(creator.profileImageUrl, "${creator.displayName} profile", Modifier.size(54.dp), creator.uid)
                Column(Modifier.weight(1f)) {
                    Text(creator.displayName, style = MaterialTheme.typography.titleSmall, color = SeekerInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("@${creator.username}", style = MaterialTheme.typography.bodySmall, color = SeekerMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (creator.course.isNotBlank()) Text(creator.course, style = MaterialTheme.typography.labelSmall, color = SeekerMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (creator.isPrivate) Icon(Icons.Rounded.Lock, "Private account", Modifier.size(16.dp), tint = SeekerMuted)
            }
            if (creator.talent.isNotBlank()) Text(creator.talent, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelMedium, color = SeekerGold)
            if (creator.bio.isNotBlank()) Text(creator.bio, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall,
                color = SeekerMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (onInvite != null) Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onProfile, Modifier.weight(1f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, SpotPalette.Line)) { Text("View profile") }
                Button(onInvite, Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = SeekerGold)) { Text("Invite") }
            }
        }
    }
}
