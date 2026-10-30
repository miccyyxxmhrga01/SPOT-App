package com.example.spotapp.ui.theme.screens.post

import androidx.compose.material.icons.rounded.Drafts
import androidx.compose.material.icons.Icons
import com.example.spotapp.ui.theme.components.SpotConfirmDialog
import com.example.spotapp.ui.theme.components.SpotPalette
import com.example.spotapp.ui.theme.components.LifecycleDisposableEffect

import android.app.Activity
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.spotapp.data.*
import com.example.spotapp.ui.theme.models.SpotUiProfile
import kotlinx.coroutines.*
import java.util.UUID

/** The existing navigation entry point; all creation steps share one restorable draft. */
@Composable
fun NewPostScreen(profile: SpotUiProfile, onCancel: () -> Unit, onPublishStarted: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val store = remember(profile.uid) { PostDraftStore(context, profile.uid) }
    val saver = remember { Saver<PostDraft, String>(save = { PostDraftCodec.encode(it) }, restore = { PostDraftCodec.decode(it) }) }
    // The draft being uploaded in the background is not reopened for editing.
    val saved = remember(profile.uid) { store.load()?.takeUnless { PostUploadManager.uploading(it.id) } }
    var draft by rememberSaveable(profile.uid, stateSaver = saver) {
        mutableStateOf(saved ?: PostDraft("post-${UUID.randomUUID()}", "", emptyList(), emptyList(), emptyList(), "followers"))
    }
    var defaultsApplied by rememberSaveable(profile.uid) { mutableStateOf(saved != null) }
    LifecycleDisposableEffect(profile.uid) {
        val listener = PrivacyRepository.shared.listenPreferences({ preferences ->
            if (!defaultsApplied) {
                val audience = if ((profile.isPrivate || profile.privacyUpdating) && preferences.postAudience == "public") "followers" else preferences.postAudience
                draft = draft.copy(audience = audience); defaultsApplied = true
            }
        })
        onDispose { listener.remove() }
    }
    var step by rememberSaveable(profile.uid) { mutableStateOf(PostStep.PICKER) }
    val view = LocalView.current
    val activity = remember(view) { generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull() }
    // Dark status icons only on the light pages; the editor's black stage and dark mode need light ones.
    val dark = SpotPalette.dark
    DisposableEffect(activity, step, dark) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = !dark && step != PostStep.EDITOR
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }
    var editingUri by rememberSaveable(profile.uid) { mutableStateOf(draft.mediaUris.firstOrNull().orEmpty()) }
    var publishing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    var leave by remember { mutableStateOf(false) }
    val busy = publishing || saving
    fun changed(value: PostDraft) { if (!busy) { defaultsApplied = true; draft = if ((profile.isPrivate || profile.privacyUpdating) && value.audience == "public") value.copy(audience = "followers") else value; error = "" } }
    fun back() {
        if (busy) return
        focus.clearFocus()
        when (step) {
            PostStep.DETAILS -> step = if (draft.mediaUris.isEmpty()) PostStep.PICKER else PostStep.EDITOR
            PostStep.EDITOR -> step = PostStep.PICKER
            PostStep.PICKER -> if (draft.caption.isNotBlank() || draft.mediaUris.isNotEmpty() || draft.categories.isNotEmpty() || draft.tags.isNotEmpty() || draft.location.isNotBlank() || draft.taggedPeople.isNotEmpty()) leave = true else onCancel()
        }
    }
    fun saveAndClose(discard: Boolean = false) {
        if (busy) return
        focus.clearFocus(); saving = true
        val value = draft
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (discard) { store.clear(); PostMediaExporter.clear(context, profile.uid, value.id) }
                    else store.save(value)
                }
                onCancel()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Couldn't save your draft. Please try again."; leave = false }
            finally { saving = false }
        }
    }
    fun publish() {
        if (busy) return
        // Personal posts drop any talent picked before switching, so they publish with no category.
        val value = draft.copy(mediaEdits = draft.mediaEdits.filterKeys { it in draft.mediaUris },
            categories = if (draft.personal) emptyList() else draft.categories,
            aiCategorySuggested = draft.aiCategorySuggested && !draft.personal)
        value.error()?.let { error = it; return }
        // Checked before saving so this draft can't replace the one still uploading.
        if (PostUploadManager.uploading()) { error = "Your last post is still uploading. Try again in a moment."; return }
        focus.clearFocus(); publishing = true; error = ""
        scope.launch {
            try {
                // Stable ID survives upload failures, process recreation, and lost acknowledgements.
                withContext(Dispatchers.IO) { store.save(value) }
                // Home shows the upload's progress; the draft is cleared once the post is live.
                PostUploadManager.start(context, profile.uid, value)
                onPublishStarted()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = SpotPostsRepository.publishError(e) }
            finally { publishing = false }
        }
    }
    BackHandler { back() }
    AnimatedContent(step, modifier = Modifier.fillMaxSize(), transitionSpec = {
        val forward = targetState.ordinal > initialState.ordinal
        (slideInHorizontally(tween(230)) { if (forward) it / 5 else -it / 5 } + fadeIn(tween(180))) togetherWith
            (slideOutHorizontally(tween(230)) { if (forward) -it / 5 else it / 5 } + fadeOut(tween(150))) using SizeTransform(clip = false)
    }, label = "post creation step") { current ->
        when (current) {
            PostStep.PICKER -> PostMediaPicker(draft, ::changed, ::back, { step = PostStep.EDITOR }, { step = PostStep.DETAILS })
            PostStep.EDITOR -> PostMediaEditor(draft, ::changed, active = step == PostStep.EDITOR && !busy && !leave,
                back = ::back, add = { step = PostStep.PICKER }, next = { step = PostStep.DETAILS }, selection = editingUri, onSelection = { editingUri = it })
            PostStep.DETAILS -> PostDetailsScreen(profile, draft, ::changed, error, busy, ::back,
                edit = { focus.clearFocus(); step = PostStep.EDITOR }, save = { saveAndClose() }, publish = ::publish)
        }
    }
    if (leave) SpotConfirmDialog(Icons.Rounded.Drafts, "Save this post for later?", "Your media, edits and details will be kept on this device.",
        "Save draft", onDismiss = { leave = false }, busy = busy, dismiss = "Discard",
        onDismissButton = { saveAndClose(discard = true) }) { saveAndClose() }
}
