package com.example.spotapp.ui.theme.screens.home

import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.Icons
import com.example.spotapp.ui.theme.components.SpotConfirmDialog
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.components.LifecycleDisposableEffect
import com.example.spotapp.ui.theme.models.SpotUiProfile
import kotlinx.coroutines.*
import java.util.UUID

@Composable
internal fun StoryCreator(profile: SpotUiProfile, close: () -> Unit, created: (StoryItem) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uri by rememberSaveable { mutableStateOf<String?>(null) }
    var id by rememberSaveable { mutableStateOf("story-${UUID.randomUUID()}") }
    var uploaded by rememberSaveable { mutableStateOf("") }
    var editJson by rememberSaveable { mutableStateOf(StoryComposition().encode()) }
    val composition = remember(editJson) { StoryComposition.decode(editJson) }
    var video by rememberSaveable { mutableStateOf(false) }
    var duration by rememberSaveable { mutableLongStateOf(0L) }
    var ready by remember { mutableStateOf(false) }
    var audience by rememberSaveable { mutableStateOf("followers") }
    var changedAudience by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf("") }
    LifecycleDisposableEffect(profile.uid) {
        val l = PrivacyRepository.shared.listenPreferences({ if (!changedAudience) audience = if ((profile.isPrivate || profile.privacyUpdating) && it.storyAudience == "public") "followers" else it.storyAudience })
        onDispose { l.remove() }
    }
    LaunchedEffect(profile.isPrivate, profile.privacyUpdating) {
        if ((profile.isPrivate || profile.privacyUpdating) && audience == "public") audience = "followers"
    }
    LaunchedEffect(uri) {
        ready = false
        val source = uri ?: return@LaunchedEffect
        try {
            val info = MediaLibraryRepository(context).describe(source)
            require(info.size in 1..(if (info.isVideo) 100L else 10L) * 1024 * 1024) { "Choose a photo under 10 MB or video under 100 MB." }
            video = info.isVideo
            duration = if (video) storyVideoDuration(context, source) else 0
            require(!video || duration in 1..300_000) { "Choose a video up to five minutes, then trim it to 60 seconds." }
            if (video && composition.trimEndMs == 0L) editJson = composition.copy(trimEndMs = minOf(duration, 60_000)).encode()
            ready = true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "This media couldn't open. Choose another." }
    }
    // The gallery opens first, like Instagram, and again when the editor's photo button is tapped.
    var choosing by rememberSaveable { mutableStateOf(false) }
    fun pick(value: String) {
        uri = value; uploaded = ""; id = "story-${UUID.randomUUID()}"; ready = false
        editJson = StoryComposition().encode(); duration = 0; error = ""; choosing = false
    }
    fun requestClose() { if (!busy) { if (uri != null) discard = true else close() } }
    fun publish() {
        val source = uri ?: return
        if (busy || !ready) return
        busy = true; error = ""; progress = 0f
        val snapshot = composition
        scope.launch {
            try {
                withTimeout(180_000) {
                    snapshot.validate()
                    if (video) require(snapshot.trimEndMs > snapshot.trimStartMs && snapshot.trimEndMs <= duration && snapshot.trimEndMs - snapshot.trimStartMs <= 60_000)
                    if (uploaded.isBlank()) uploadStoryMedia(context.applicationContext, source, "spot/stories/${profile.uid}/$id", maxDurationMs = 300_000) { progress = it }
                        .also { uploaded = it.first; video = it.second }
                    StoryRepository.shared.createStory(id, uploaded, video, audience, snapshot)
                }
                created(StoryItem(id, profile.uid, uploaded, if (video) "video" else "image", System.currentTimeMillis(),
                    audience = if ((profile.isPrivate || profile.privacyUpdating) && audience == "public") "followers" else audience,
                    username = profile.username, profileImageUrl = profile.profileImageUrl, composition = snapshot))
            } catch (_: TimeoutCancellationException) { error = "Sharing timed out. Retry." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = if (e is IllegalArgumentException || e is IllegalStateException) e.message.orEmpty() else "Story wasn't confirmed. Your edit is kept; retry." }
            finally { busy = false }
        }
    }
    Dialog(onDismissRequest = ::requestClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        StorySystemBars()
        if (uri == null || choosing) StoryGallery(close = { if (uri == null) close() else choosing = false }, choose = ::pick)
        else StoryEditor(uri, video, duration, composition, { editJson = it.encode() }, audience, profile.isPrivate || profile.privacyUpdating,
            { audience = it; changedAudience = true }, busy, progress, error, ready, ::requestClose,
            { if (!busy) choosing = true }, ::publish)
    }
    if (discard) SpotConfirmDialog(Icons.Rounded.DeleteOutline, "Discard this story?", null, "Discard",
        onDismiss = { discard = false }, danger = true, dismiss = "Keep editing") { discard = false; close() }
}
