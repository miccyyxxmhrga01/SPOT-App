package com.example.spotapp.ui.theme.screens.profile

import com.example.spotapp.ui.theme.components.spotAmbientBackground
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import com.example.spotapp.ui.theme.components.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.spotapp.ProfileCourseGrid
import com.example.spotapp.data.*
import com.example.spotapp.data.cloudinary.uploadCommunityImage
import com.example.spotapp.ui.theme.components.SpotAvatar
import com.example.spotapp.ui.theme.models.SpotUiProfile
import kotlinx.coroutines.*
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectedEditProfile(profile: SpotUiProfile, onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var name by rememberSaveable(profile.uid) { mutableStateOf(profile.displayName) }
    var username by rememberSaveable(profile.uid) { mutableStateOf(profile.username) }
    var bio by rememberSaveable(profile.uid) { mutableStateOf(profile.bio) }
    var course by rememberSaveable(profile.uid) { mutableStateOf(profile.course) }
    var organization by rememberSaveable(profile.uid) { mutableStateOf(profile.organization) }
    var talents by rememberSaveable(profile.uid) { mutableStateOf(profile.talentCategories.ifEmpty { listOfNotNull(profile.talent.takeIf(String::isNotBlank)) }) }
    var seeking by rememberSaveable(profile.uid) { mutableStateOf(profile.seekingTalents) }
    var pronouns by rememberSaveable(profile.uid) { mutableStateOf(profile.pronouns) }
    var links by rememberSaveable(profile.uid) { mutableStateOf(profile.links) }
    var gender by rememberSaveable(profile.uid) { mutableStateOf(profile.gender) }
    var photo by rememberSaveable(profile.uid) { mutableStateOf(profile.profileImageUrl) }
    var localPhoto by rememberSaveable(profile.uid) { mutableStateOf<String?>(null) }
    var uploadedFor by rememberSaveable(profile.uid) { mutableStateOf("") }
    var uploadedUrl by rememberSaveable(profile.uid) { mutableStateOf("") }
    var more by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) { localPhoto = it.toString(); error = "" } }
    BackHandler { if (!busy) onBack() }
    fun save() {
        if (busy) return
        val role = profile.accountType
        if (role == null) { error = "Your profile is still loading. Reopen Edit profile and retry."; return }
        val value = ProfileDetails(role, name.trim(), username.trim().lowercase(Locale.ROOT).removePrefix("@"), course, talents, organization, seeking, bio)
        value.validationError()?.let { error = it; return }
        focus.clearFocus(); busy = true; error = ""; progress = 0f
        scope.launch {
            try {
                withTimeout(180_000) {
                    val selected = localPhoto
                    val url = if (selected == null) photo else if (selected == uploadedFor) uploadedUrl else {
                        uploadCommunityImage(context, selected, "spot/profiles/${profile.uid}/${UUID.randomUUID()}") { progress = it }
                            .also { uploadedFor = selected; uploadedUrl = it }
                    }
                    ProfileRepository.shared.save(value, url, pronouns, links, gender)
                }
                onSaved()
            } catch (_: TimeoutCancellationException) { error = "Save timed out. Your changes are kept; retry." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = if (e is IllegalArgumentException || e is IllegalStateException) e.message.orEmpty() else "Couldn't save your profile. Retry." }
            finally { busy = false }
        }
    }
    val student = profile.accountType == AccountType.STUDENT
    val selection = if (student) talents else seeking
    Surface(Modifier.fillMaxSize().spotAmbientBackground(SpotPalette.Surface), color = Color.Transparent) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().navigationBarsPadding()) {
            SpotScreenHeader("Edit profile", onBack = onBack, backEnabled = !busy) {
                SpotPillButton(if (busy) "Saving…" else "Save", ::save, Modifier.padding(end = 6.dp).testTag("profile-save"),
                    icon = if (busy) null else Icons.Rounded.Check, enabled = !busy, compact = true)
            }
            if (busy && localPhoto != null) LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth(),
                color = SpotPalette.GoldDeep, trackColor = SpotPalette.Track)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 24.dp)) {
                SpotCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), elevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(112.dp).clip(CircleShape).clickable(enabled = !busy, onClickLabel = "Change photo") { picker.launch("image/*") }) {
                            Box(Modifier.matchParentSize().background(EditRing, CircleShape).padding(3.dp).background(SpotPalette.Surface, CircleShape).padding(3.dp)) {
                                SpotAvatar(localPhoto ?: photo, "Profile photo", Modifier.fillMaxSize(), profile.uid)
                            }
                            Box(Modifier.align(Alignment.BottomEnd).size(34.dp).background(SpotGoldGradient, CircleShape).border(2.dp, SpotPalette.Surface, CircleShape),
                                contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PhotoCamera, null, Modifier.size(17.dp), tint = SpotPalette.OnGold) }
                        }
                        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SpotPillButton("Change photo", { picker.launch("image/*") }, icon = Icons.Rounded.PhotoLibrary,
                                style = SpotButtonStyle.Tonal, enabled = !busy, compact = true)
                            if (localPhoto != null || photo.isNotBlank()) SpotPillButton("Remove", { localPhoto = null; photo = "" },
                                icon = Icons.Rounded.DeleteOutline, style = SpotButtonStyle.Secondary, enabled = !busy, compact = true)
                        }
                    }
                }
                if (error.isNotBlank()) Row(Modifier.fillMaxWidth().padding(top = 12.dp)
                    .background(SpotPalette.Danger.copy(alpha = .07f), RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(18.dp), tint = SpotPalette.Danger)
                    Text(error, color = SpotPalette.Danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                }
                EditSection("About you")
                EditCard {
                    ProfileField("Name", name, { name = it.take(60) }, busy, icon = Icons.Rounded.Person)
                    ProfileField("Username", username, { username = it.take(30).removePrefix("@") }, busy, icon = Icons.Rounded.AlternateEmail)
                    ProfileField("Bio", bio, { bio = it.take(160) }, busy, singleLine = false, icon = Icons.Rounded.Notes,
                        supporting = "${bio.length}/160")
                }
                if (student) {
                    // The same one-tap chips as profile setup, right on the page.
                    EditSection("Course")
                    EditCard { ProfileCourseGrid(course, !busy, { course = it }, Modifier.testTag("edit-course")) }
                } else {
                    EditSection("Organization")
                    EditCard { ProfileField("Organization", organization, { organization = it.take(80) }, busy, icon = Icons.Rounded.Business) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EditSection(if (student) "Talents" else "Talents you seek", Modifier.weight(1f))
                    Text("${selection.size}/8", color = SpotPalette.GoldInk, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 14.dp).background(SpotPalette.GoldSoft, CircleShape).padding(horizontal = 10.dp, vertical = 2.dp))
                }
                EditCard {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (SpotTalentCategories + selection).distinct().forEach { talent ->
                            val selected = talent in selection
                            SpotOptionChip(talent, selected, {
                                val next = if (selected) selection - talent else (selection + talent).take(8)
                                if (student) talents = next else seeking = next
                            }, enabled = !busy && (selected || selection.size < 8))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                SpotCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = 1.dp) {
                    val turn by animateFloatAsState(if (more) 180f else 0f, label = "more details")
                    Row(Modifier.fillMaxWidth().clickable(onClickLabel = if (more) "Hide more details" else "Show more details") { more = !more }
                        .padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        SpotIconTile(Icons.Rounded.Tune, size = 38.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("More details", style = MaterialTheme.typography.titleSmall, color = SpotPalette.Ink)
                            Text("Pronouns, link and gender · optional", style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted)
                        }
                        Icon(Icons.Rounded.ExpandMore, null, Modifier.size(22.dp).rotate(turn), tint = SpotPalette.GoldDeep)
                    }
                    AnimatedVisibility(more) {
                        Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ProfileField("Pronouns", pronouns, { pronouns = it.take(40) }, busy)
                            ProfileField("Link", links, { links = it.take(300) }, busy, icon = Icons.Rounded.Link)
                            ProfileField("Gender", gender, { gender = it.take(40) }, busy)
                        }
                    }
                }
            }
        }
    }
}

private val EditRing get() = Brush.sweepGradient(listOf(SpotPalette.GoldBright, SpotPalette.GoldDeep, SpotPalette.Gold, SpotPalette.GoldBright))

@Composable
private fun EditSection(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp), color = SpotPalette.GoldInk,
        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun EditCard(content: @Composable ColumnScope.() -> Unit) {
    SpotCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), elevation = 1.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun ProfileField(label: String, value: String, change: (String) -> Unit, busy: Boolean, singleLine: Boolean = true,
    icon: ImageVector? = null, supporting: String? = null) {
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) }, enabled = !busy,
        leadingIcon = icon?.let { { Icon(it, null, Modifier.size(20.dp), tint = SpotPalette.GoldDeep) } },
        prefix = if (label == "Username") { { Text("@") } } else null,
        supportingText = supporting?.let { { Text(it) } },
        singleLine = singleLine, minLines = if (singleLine) 1 else 3, maxLines = if (singleLine) 1 else 5, shape = SpotAuthFieldShape,
        textStyle = MaterialTheme.typography.bodyMedium, colors = spotAuthFieldColors())
}
