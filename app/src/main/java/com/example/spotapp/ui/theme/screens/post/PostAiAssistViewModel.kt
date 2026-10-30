package com.example.spotapp.ui.theme.screens.post

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotapp.data.PostMediaEdit
import com.example.spotapp.data.ai.PostAiAssist
import com.example.spotapp.data.ai.PostAiSuggestions
import com.example.spotapp.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class AiAssistKind { CAPTION, TAGS }

/** [inputKey] identifies the media, edit, categories and post type the suggestions were written for. */
internal data class PostAiAssistState(
    val loading: Boolean = false,
    val suggestions: PostAiSuggestions? = null,
    val error: String? = null,
    val inputKey: String? = null,
    val showCaptions: Boolean = false,
    val showTags: Boolean = false
)

/**
 * One Gemini request answers both Generate Caption and Suggest Tags, so the second button reuses the first's result.
 * The result is kept while the composer is open; a new one is only requested for new input or on "Try again".
 */
internal class PostAiAssistViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PostAiAssistState())
    val uiState: StateFlow<PostAiAssistState> = _uiState.asStateFlow()
    private var job: Job? = null

    fun request(context: Context, kind: AiAssistKind, mediaUri: String, edit: PostMediaEdit, categories: List<String>,
        caption: String, personal: Boolean) {
        val key = inputKey(mediaUri, edit, categories, personal)
        val current = _uiState.value
        if (current.inputKey == key && (current.suggestions != null || current.loading)) {
            _uiState.update { it.show(kind) }
            return
        }
        fetch(context, key, PostAiAssistState(loading = true, inputKey = key).show(kind), mediaUri, edit, categories, caption, personal)
    }

    fun retry(context: Context, mediaUri: String, edit: PostMediaEdit, categories: List<String>, caption: String, personal: Boolean) {
        val key = inputKey(mediaUri, edit, categories, personal)
        val shown = _uiState.value.takeIf { it.inputKey == key }
        fetch(context, key, PostAiAssistState(loading = true, inputKey = key, showCaptions = shown?.showCaptions ?: true,
            showTags = shown?.showTags ?: false), mediaUri, edit, categories, caption, personal)
    }

    private fun fetch(context: Context, key: String, start: PostAiAssistState, mediaUri: String, edit: PostMediaEdit,
        categories: List<String>, caption: String, personal: Boolean) {
        job?.cancel()
        _uiState.value = start
        job = viewModelScope.launch {
            try {
                val suggestions = PostAiAssist.suggest(context.applicationContext, mediaUri, edit, categories, caption, personal)
                _uiState.update { if (it.inputKey == key) it.copy(loading = false, suggestions = suggestions) else it }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                _uiState.update { if (it.inputKey == key) it.copy(loading = false, error = userMessage(error, "AI Assist isn't available right now. Try again later.")) else it }
            }
        }
    }

    private fun PostAiAssistState.show(kind: AiAssistKind) =
        if (kind == AiAssistKind.CAPTION) copy(showCaptions = true) else copy(showTags = true)

    companion object {
        fun inputKey(mediaUri: String, edit: PostMediaEdit, categories: List<String>, personal: Boolean) =
            "${TalentAiViewModel.mediaKey(mediaUri, edit)}|${categories.joinToString(",")}|$personal"
    }
}
