package com.example.spotapp.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** A post being published in the background while the user keeps using SPOT. */
data class PostUpload(val uid: String, val draft: PostDraft, val progress: Float = 0f,
    val status: Status = Status.UPLOADING, val error: String = "") {
    enum class Status { UPLOADING, DONE, FAILED }
    val thumbnail: String get() = draft.mediaUris.firstOrNull().orEmpty()
}

/** Publishing outlives the post screen; the draft stays saved until the server confirms the post. */
object PostUploadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _upload = MutableStateFlow<PostUpload?>(null)
    val upload: StateFlow<PostUpload?> = _upload.asStateFlow()

    fun uploading(draftId: String? = null): Boolean = _upload.value?.let {
        it.status == PostUpload.Status.UPLOADING && (draftId == null || it.draft.id == draftId)
    } == true

    fun start(context: Context, uid: String, draft: PostDraft) {
        check(!uploading()) { "Your last post is still uploading. Try again in a moment." }
        val app = context.applicationContext
        _upload.value = PostUpload(uid, draft)
        // Keeps SPOT alive, with a progress notification, if the member leaves before the post is up.
        PostUploadService.start(app)
        scope.launch {
            try {
                withTimeout(300_000) {
                    SpotPostsRepository.publish(app, draft) { value -> _upload.update { it?.copy(progress = value) } }
                }
                withContext(Dispatchers.IO) {
                    // A newer draft saved while this one uploaded must survive.
                    val store = PostDraftStore(app, uid)
                    if (store.load()?.id == draft.id) store.clear()
                    runCatching { PostMediaExporter.clear(app, uid, draft.id) }
                }
                _upload.update { it?.copy(progress = 1f, status = PostUpload.Status.DONE) }
                delay(2_200)
                _upload.update { if (it?.draft?.id == draft.id && it.status == PostUpload.Status.DONE) null else it }
            } catch (_: TimeoutCancellationException) {
                failed(app, uid, draft, "Uploading took too long. Your draft is saved.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failed(app, uid, draft, SpotPostsRepository.publishError(e)) }
        }
    }

    fun retry(context: Context) {
        val failed = _upload.value?.takeIf { it.status == PostUpload.Status.FAILED } ?: return
        start(context, failed.uid, failed.draft)
    }

    fun dismiss() {
        if (!uploading()) _upload.value = null
    }

    private suspend fun failed(context: Context, uid: String, draft: PostDraft, message: String) {
        // Keep the post recoverable from New Post unless the user already started another draft.
        withContext(Dispatchers.IO) { runCatching { PostDraftStore(context, uid).let { if (it.load() == null) it.save(draft) } } }
        _upload.update { it?.copy(status = PostUpload.Status.FAILED, error = message) }
    }
}
