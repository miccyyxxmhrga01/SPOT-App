package com.example.spotapp

import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.ui.draw.drawWithContent
import com.example.spotapp.ui.theme.components.SpotOptionChip
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.animateColorAsState
import com.example.spotapp.ui.theme.spotSp

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import com.example.spotapp.ui.theme.components.SpotAuthFieldShape
import com.example.spotapp.ui.theme.components.SpotIconTile
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.components.spotAuthFieldColors
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.spotapp.data.AccountType
import com.example.spotapp.data.AuthRepository
import com.example.spotapp.data.ProfileDetails
import com.example.spotapp.data.cloudinary.CloudinaryUploader
import com.example.spotapp.ui.theme.SPOTAppTheme
import com.example.spotapp.ui.theme.InterFontFamily
import com.example.spotapp.ui.theme.components.SpotAuthButton
import com.example.spotapp.ui.theme.components.awaitSpotAuthResult
import com.example.spotapp.data.PostMediaExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import java.util.Locale

private val ProfileTalentOptions = com.example.spotapp.data.SpotTalentCategories

// Keep the original degree names as the stored values for student profiles.
internal val ProfileCourseOptions = listOf(
    "Bachelor of Science in Computer Science",
    "Bachelor of Science in Information Technology",
    "Bachelor of Science in Criminology",
    "Bachelor of Science in Food Technology",
    "Bachelor of Science in Electrical Technology",
    "Bachelor of Science in Electronics Technology"
)

@Composable
fun ProfileSetupScreen(
    onSetupComplete: () -> Unit
) {
    var loading by remember {
        mutableStateOf(false)
    }

    var message by remember {
        mutableStateOf("")
    }

    val scope = rememberCoroutineScope()

    val context = LocalContext.current

    val latestComplete by rememberUpdatedState(
        onSetupComplete
    )

    ProfileSetupFlow(
        initialName = AuthRepository.currentUserDisplayName(),

        loading = loading,

        message = message,

        onEdit = {
            message = ""
        },

        onExit = {
            if (!loading) {
                AuthRepository.logOut()
            }
        },

        onSave = { profile, photo ->

            if (loading) {
                return@ProfileSetupFlow
            }

            loading = true
            message = ""

            /*
             * Saves the profile AFTER we have the
             * Cloudinary image URL.
             */
            fun saveProfile(
                profileImageUrl: String?
            ) {

                scope.launch {

                    try {

                        val result =
                            awaitSpotAuthResult { callback ->

                                AuthRepository.completeProfile(
                                    profile = profile,
                                    profileImageUrl = profileImageUrl,
                                    callback = callback
                                )
                            }

                        if (result.first) {

                            latestComplete()

                        } else {

                            message =
                                result.second
                                    ?: "Could not save your profile. Please retry."

                            loading = false
                        }

                    } catch (
                        timeout: TimeoutCancellationException
                    ) {

                        message =
                            "This took too long. Check your connection and retry."

                        loading = false

                    } catch (
                        cancelled: CancellationException
                    ) {

                        loading = false

                        throw cancelled

                    } catch (
                        error: Exception
                    ) {

                        message =
                            error.message
                                ?: "Could not save your profile. Please retry."

                        loading = false
                    }
                }
            }

            /*
             * Photo is optional in your existing UI.
             *
             * If the user didn't select one,
             * simply save the profile without an image.
             */
            if (photo == null) {

                saveProfile(
                    profileImageUrl = null
                )

            } else {

                /*
                 * Upload selected profile picture
                 * to Cloudinary first, shrunk to the
                 * size SPOT shows (the original is
                 * sent if it can't be shrunk).
                 */
                scope.launch {

                    val shrunk =
                        try {
                            PostMediaExporter.shrinkForUpload(context, photo.toString())
                        } catch (cancelled: CancellationException) {
                            loading = false
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }

                    CloudinaryUploader.uploadMedia(
                        uri = shrunk?.let(Uri::fromFile) ?: photo,

                        folder = "spot/profile_images",

                        onSuccess = { imageUrl ->

                            shrunk?.delete()

                            // Cloudinary returned the permanent URL.
                            saveProfile(
                                profileImageUrl = imageUrl
                            )
                        },

                        onError = { error ->

                            shrunk?.delete()

                            loading = false

                            message =
                                "Profile photo upload failed: $error"
                        }
                    )
                }
            }
        }
    )
}

// Copy shown on the role cards; the stored account types keep their short labels.
internal fun profileRoleTitle(type: AccountType) =
    if (type == AccountType.STUDENT) "Show my talent" else "Discover talent"

private fun profileRoleDescription(type: AccountType) =
    if (type == AccountType.STUDENT) "Post, perform and get discovered." else "Find and work with creators."

/** "Computer Science" for "Bachelor of Science in Computer Science"; the full name is what's stored. */
internal fun profileCourseLabel(course: String) = course.removePrefix("Bachelor of Science in ")

/** As in Edit profile, a profile lists up to this many talents. */
private const val MAX_PROFILE_TALENTS = 8

/**
 * Four short steps on one calm page: a slim progress bar, a small title, the questions, and the main
 * button pinned above the keyboard. Every choice is on the page itself; nothing opens a pop-up.
 */
@Composable
internal fun ProfileSetupFlow(
    initialName: String = "",
    loading: Boolean = false,
    message: String = "",
    onEdit: () -> Unit = {},
    onExit: () -> Unit,
    onSave: (ProfileDetails, Uri?) -> Unit,
    // Previews open later steps directly; the app always starts at the first one.
    startStep: Int = 0,
    startRole: AccountType? = null,
    startUsername: String = ""
) {
    var step by rememberSaveable { mutableIntStateOf(startStep) }
    var roleValue by rememberSaveable { mutableStateOf(startRole?.storageValue) }
    val role = AccountType.fromStorage(roleValue)
    var name by rememberSaveable { mutableStateOf(initialName) }
    var username by rememberSaveable { mutableStateOf(startUsername) }
    var course by rememberSaveable { mutableStateOf("") }
    var organization by rememberSaveable { mutableStateOf("") }
    var talents by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var interests by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var bio by rememberSaveable { mutableStateOf("") }
    var photoValue by rememberSaveable { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf("") }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val usernameFocus = remember { FocusRequester() }
    val photo = photoValue?.let(Uri::parse)
    val isStudent = role == AccountType.STUDENT
    val details = ProfileDetails(role ?: AccountType.STUDENT, name, username, course, talents, organization, interests, bio)

    fun edited() { localError = ""; onEdit() }
    fun back() {
        if (loading) return
        focus.clearFocus()
        if (step > 0) { step--; edited() }
        else onExit()
    }
    fun advance() {
        if (loading) return
        val error = when (step) {
            0 -> if (role == null) "Choose how you'd like to use SPOT." else null
            1 -> details.identityError()
            2 -> details.detailsError()
            else -> details.validationError()
        }
        when {
            error != null -> localError = error
            step < 3 -> { focus.clearFocus(); step++; edited() }
            else -> { focus.clearFocus(); onSave(details, photo) }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                photoValue = uri.toString()
                edited()
            } catch (error: SecurityException) {
                localError = "Couldn't keep access to this photo. Please choose another image."
            }
        }
    }

    BackHandler { back() }
    Column(Modifier.fillMaxSize().background(SpotPalette.Ivory).statusBarsPadding().navigationBarsPadding().imePadding()) {
        SetupProgress(step, enabled = !loading, onBack = ::back)

        // The questions scroll when large text or the keyboard leaves too little room; the button stays put.
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(when (step) {
                0 -> "How will you use SPOT?"
                1 -> "Your name"
                2 -> if (isStudent) "Course & talents" else "What you're looking for"
                else -> "Photo & bio"
            }, Modifier.padding(top = 18.dp), style = MaterialTheme.typography.headlineSmall, color = SpotPalette.Ink)
            Text(when (step) {
                0 -> "Choose the profile that fits you."
                1 -> "This is how people find and mention you."
                2 -> if (isStudent) "Help people find what you do." else "Both are optional."
                else -> "Both are optional. You can add them later."
            }, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = SpotPalette.Muted)
            Spacer(Modifier.height(20.dp))

            when (step) {
                0 -> Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SetupRoleOption(AccountType.STUDENT, role, !loading) { roleValue = it.storageValue; edited() }
                    SetupRoleOption(AccountType.TALENT_SEEKER, role, !loading) { roleValue = it.storageValue; edited() }
                }
                1 -> {
                    SetupPreviewCard(name, username, role?.label.orEmpty(), photo)
                    Spacer(Modifier.height(18.dp))
                    SetupField(name, { name = it.take(60); edited() }, "Display name", "e.g. Alex Santos",
                        Modifier.testTag("profile-name"), enabled = !loading,
                        capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next,
                        onDone = { usernameFocus.requestFocus() })
                    Spacer(Modifier.height(14.dp))
                    SetupField(username, { username = it.lowercase(Locale.ROOT).take(30); edited() }, "Username", "yourname",
                        Modifier.focusRequester(usernameFocus).testTag("profile-username"), enabled = !loading, prefix = "@",
                        helper = "3–30 letters, numbers or underscores.", onDone = { advance() })
                }
                2 -> {
                    if (isStudent) {
                        SetupLabel("Course", trailing = "Bachelor of Science")
                        ProfileCourseGrid(course, !loading, { course = it; focus.clearFocus(); edited() }, Modifier.testTag("profile-course"))
                    } else {
                        SetupField(organization, { organization = it.take(80); edited() }, "Organization", "Company, team or independent",
                            Modifier.testTag("profile-organization"), enabled = !loading, optional = true,
                            capitalization = KeyboardCapitalization.Words, onDone = { focus.clearFocus() })
                    }
                    Spacer(Modifier.height(20.dp))
                    val selected = if (isStudent) talents else interests
                    SetupLabel(if (isStudent) "Talents" else "Looking for", optional = !isStudent, trailing = "${selected.size}/$MAX_PROFILE_TALENTS")
                    SetupTalentChips(selected, !loading) { value ->
                        val updated = if (value in selected) selected - value else (selected + value).take(MAX_PROFILE_TALENTS)
                        if (isStudent) talents = updated else interests = updated
                        focus.clearFocus()
                        edited()
                    }
                }
                else -> {
                    SetupPhotoRow(name, username, role?.label.orEmpty(), photo, !loading,
                        onPick = { picker.launch(arrayOf("image/*")) },
                        onRemove = { photoValue = null; edited() })
                    Spacer(Modifier.height(20.dp))
                    SetupField(bio, { bio = it.take(160); edited() }, "Bio", "What do you love creating?",
                        Modifier.testTag("profile-bio"), enabled = !loading, optional = true, trailing = "${bio.length}/160",
                        capitalization = KeyboardCapitalization.Sentences, singleLine = false, onDone = { focus.clearFocus() })
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)) {
            val error = localError.ifBlank { message }
            if (error.isNotBlank()) {
                Text(error, style = MaterialTheme.typography.bodySmall, color = SpotPalette.ErrorText,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }
            Box(Modifier.testTag("profile-submit")) {
                SpotAuthButton(if (step == 3) "Finish setup" else "Continue", loading = loading,
                    enabled = step != 0 || role != null, onClick = { advance() })
            }
            if (step == 3) {
                TextButton(onClick = { focus.clearFocus(); onSave(details.copy(bio = ""), null) }, enabled = !loading,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp).heightIn(min = 44.dp)
                        .testTag("profile-skip")) {
                    Text("Skip for now", style = MaterialTheme.typography.labelLarge, color = SpotPalette.GoldInk)
                }
            } else {
                Text(when (step) {
                    0 -> "Next: name & username"
                    1 -> if (isStudent) "Next: course & talents" else "Next: what you're looking for"
                    else -> "Last: photo & bio"
                }, Modifier.fillMaxWidth().padding(top = 10.dp), style = MaterialTheme.typography.labelMedium,
                    color = SpotPalette.Muted, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Back (or sign out on the first step), four slim progress segments and the step count, on one line. */
@Composable
private fun SetupProgress(step: Int, enabled: Boolean, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = if (step == 0) "Sign out" else "Previous profile step" }) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(22.dp), tint = SpotPalette.Ink)
        }
        Row(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(4) { index ->
                val done by animateColorAsState(if (index <= step) SpotPalette.Gold else SpotPalette.Track, label = "setup progress")
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(done))
            }
        }
        Text("${step + 1}/4", style = MaterialTheme.typography.labelLarge, color = SpotPalette.GoldInk, maxLines = 1)
    }
}

/** One profile type: icon, name and a one-line description, with a radio button. */
@Composable
private fun SetupRoleOption(type: AccountType, selected: AccountType?, enabled: Boolean, onSelect: (AccountType) -> Unit) {
    val active = selected == type
    val shape = RoundedCornerShape(18.dp)
    val border by animateColorAsState(if (active) SpotPalette.GoldDeep else SpotPalette.Line, label = "role border")
    Row(Modifier.fillMaxWidth().clip(shape).background(if (active) SpotPalette.GoldWash else SpotPalette.Surface)
        .border(if (active) 1.5.dp else 1.dp, border, shape)
        .selectable(active, enabled, Role.RadioButton) { onSelect(type) }
        .testTag("role-${type.storageValue}").padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        SpotIconTile(if (type == AccountType.STUDENT) Icons.Rounded.StarOutline else Icons.Rounded.PersonSearch, size = 40.dp,
            tint = if (active) SpotPalette.OnGold else SpotPalette.GoldDeep, container = if (active) SpotPalette.Gold else SpotPalette.GoldSoft)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(profileRoleTitle(type), style = MaterialTheme.typography.titleSmall, color = SpotPalette.Ink)
            Text(profileRoleDescription(type), style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted)
        }
        RadioButton(active, onClick = null, enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = SpotPalette.GoldDeep, unselectedColor = SpotPalette.Muted))
    }
}

@Composable
private fun SetupLabel(text: String, optional: Boolean = false, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(buildAnnotatedString {
            append(text)
            if (optional) withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = SpotPalette.Muted)) { append(" · optional") }
        }, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = SpotPalette.Ink)
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = SpotPalette.Muted)
    }
}

/** A small label above the field, an optional hint under it. */
@Composable
private fun SetupField(
    value: String, onChange: (String) -> Unit, label: String, placeholder: String,
    modifier: Modifier = Modifier, enabled: Boolean = true, prefix: String? = null,
    optional: Boolean = false, trailing: String? = null, helper: String? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    singleLine: Boolean = true, imeAction: ImeAction = ImeAction.Done, onDone: () -> Unit = {}
) {
    SetupLabel(label, optional, trailing)
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), enabled = enabled,
        placeholder = { Text(placeholder) },
        prefix = prefix?.let { { Text(it, color = SpotPalette.Muted) } },
        supportingText = helper?.let { { Text(it) } },
        singleLine = singleLine, minLines = if (singleLine) 1 else 3, maxLines = if (singleLine) 1 else 5,
        textStyle = MaterialTheme.typography.bodyLarge, shape = SpotAuthFieldShape,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = KeyboardType.Text,
            imeAction = imeAction, autoCorrectEnabled = capitalization != KeyboardCapitalization.None),
        keyboardActions = KeyboardActions(onNext = { onDone() }, onDone = { onDone() }),
        colors = spotAuthFieldColors().copy(unfocusedContainerColor = SpotPalette.Surface))
}

/** How the profile will look to others, updating as the name and username are typed. */
@Composable
private fun SetupPreviewCard(name: String, username: String, role: String, photo: Uri?) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SpotPalette.Surface)
        .border(1.dp, SpotPalette.Line, RoundedCornerShape(18.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        SetupAvatar(name, photo, 44.dp)
        Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
            Text(name.ifBlank { "Your name" }, style = MaterialTheme.typography.titleSmall,
                color = if (name.isBlank()) SpotPalette.Muted else SpotPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("@${username.trim().ifBlank { "username" }}", style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (role.isNotBlank()) Text(role, Modifier.background(SpotPalette.GoldSoft, CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium, color = SpotPalette.GoldInk, maxLines = 1)
    }
}

@Composable
private fun SetupAvatar(name: String, photo: Uri?, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(SpotPalette.GoldSoft), contentAlignment = Alignment.Center) {
        if (photo != null) AsyncImage(photo, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(initialsOf(name), style = if (size > 60.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall,
            color = SpotPalette.GoldInk)
    }
}

/**
 * The six courses as an even two-column grid (pick one), shared with Edit profile. Tiles in a row match
 * heights when a name wraps; the full degree name is what's stored.
 */
@Composable
internal fun ProfileCourseGrid(selected: String, enabled: Boolean, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileCourseOptions.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    CourseTile(option, option == selected, enabled, Modifier.weight(1f).fillMaxHeight()) { onSelect(option) }
                }
            }
        }
    }
}

@Composable
private fun CourseTile(option: String, active: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(modifier.heightIn(min = 48.dp).clip(shape).background(if (active) SpotPalette.GoldWash else SpotPalette.Surface)
        .border(if (active) 1.5.dp else 1.dp, if (active) SpotPalette.GoldDeep else SpotPalette.Line, shape)
        .selectable(active, enabled, Role.RadioButton, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(profileCourseLabel(option), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
            color = if (active) SpotPalette.GoldInk else SpotPalette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (active) Icon(Icons.Rounded.CheckCircle, null, Modifier.padding(start = 6.dp).size(18.dp), tint = SpotPalette.GoldDeep)
    }
}

/** Every talent as a one-tap chip, up to [MAX_PROFILE_TALENTS]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupTalentChips(selected: List<String>, enabled: Boolean, onToggle: (String) -> Unit) {
    // Each chip keeps a 48dp touch area, which already spaces the rows.
    FlowRow(Modifier.fillMaxWidth().testTag("profile-talents"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileTalentOptions.forEach { talent ->
            val on = talent in selected
            SpotOptionChip(talent, on, { onToggle(talent) }, enabled = enabled && (on || selected.size < MAX_PROFILE_TALENTS))
        }
    }
}

/** The photo beside the name it goes with, with Add, Change and Remove right there. */
@Composable
private fun SetupPhotoRow(name: String, username: String, role: String, photo: Uri?, enabled: Boolean,
    onPick: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(92.dp)) {
            val dash = SpotPalette.GoldDeep
            Box(Modifier.matchParentSize().clip(CircleShape)
                .drawWithContent {
                    drawContent()
                    if (photo == null) drawCircle(dash, radius = size.minDimension / 2 - 1.dp.toPx(),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
                }
                .clickable(enabled = enabled, role = Role.Button, onClick = onPick)
                .semantics { contentDescription = if (photo == null) "Add profile photo" else "Change profile photo" }
                .testTag("profile-photo"), contentAlignment = Alignment.Center) {
                SetupAvatar(name, photo, 92.dp)
            }
            Box(Modifier.align(Alignment.BottomEnd).size(32.dp).background(SpotPalette.Gold, CircleShape)
                .border(2.dp, SpotPalette.Ivory, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.CameraAlt, null, Modifier.size(16.dp), tint = SpotPalette.OnGold)
            }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = SpotPalette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("@${username.trim()} · $role", style = MaterialTheme.typography.bodySmall, color = SpotPalette.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 6.dp).offset(x = (-12).dp)) {
                TextButton(onPick, enabled = enabled) {
                    Text(if (photo == null) "Add photo" else "Change", style = MaterialTheme.typography.labelLarge, color = SpotPalette.GoldInk)
                }
                if (photo != null) TextButton(onRemove, enabled = enabled) {
                    Text("Remove", style = MaterialTheme.typography.labelLarge, color = SpotPalette.Muted)
                }
            }
        }
    }
}

private fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifBlank { "?" }


@Preview(name = "Profile 1 - role", widthDp = 360, heightDp = 760)
@Preview(name = "Profile 1 - large text", widthDp = 360, heightDp = 760, fontScale = 1.15f)
@Preview(name = "Profile 1 - small phone", widthDp = 320, heightDp = 568)
@Composable
private fun ProfileSetupPreview() {
    SPOTAppTheme(dark = false) { ProfileSetupFlow(onExit = {}, onSave = { _, _ -> }) }
}

@Preview(name = "Profile 1 - dark", widthDp = 360, heightDp = 760)
@Composable
private fun ProfileSetupDarkPreview() {
    SPOTAppTheme(dark = true) { ProfileSetupFlow(onExit = {}, onSave = { _, _ -> }, startRole = AccountType.STUDENT) }
}

@Preview(name = "Profile 2 - name", widthDp = 360, heightDp = 760)
@Composable
private fun ProfileIdentityPreview() {
    SPOTAppTheme(dark = false) { ProfileSetupFlow("Alex Santos", onExit = {}, onSave = { _, _ -> },
        startStep = 1, startRole = AccountType.STUDENT, startUsername = "alexsantos") }
}

@Preview(name = "Profile 3 - course and talents", widthDp = 360, heightDp = 760)
@Preview(name = "Profile 3 - large text", widthDp = 360, heightDp = 760, fontScale = 1.15f)
@Composable
private fun ProfileTalentsPreview() {
    SPOTAppTheme(dark = false) { ProfileSetupFlow("Alex Santos", onExit = {}, onSave = { _, _ -> },
        startStep = 2, startRole = AccountType.STUDENT, startUsername = "alexsantos") }
}

@Preview(name = "Profile 3 - looking for", widthDp = 360, heightDp = 760)
@Composable
private fun ProfileSeekerPreview() {
    SPOTAppTheme(dark = true) { ProfileSetupFlow("Alex Santos", onExit = {}, onSave = { _, _ -> },
        startStep = 2, startRole = AccountType.TALENT_SEEKER, startUsername = "alexsantos") }
}

@Preview(name = "Profile 4 - photo and bio", widthDp = 360, heightDp = 760)
@Composable
private fun ProfileAboutPreview() {
    SPOTAppTheme(dark = false) { ProfileSetupFlow("Alex Santos", onExit = {}, onSave = { _, _ -> },
        startStep = 3, startRole = AccountType.STUDENT, startUsername = "alexsantos") }
}
