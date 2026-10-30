package com.example.spotapp.ui.theme.screens.post

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotapp.data.PostMediaEdit
import com.example.spotapp.data.ai.SpotAiRepository
import com.example.spotapp.data.ai.TalentPredictionResponse
import com.example.spotapp.data.ai.spotAiError
import com.example.spotapp.data.ai.composerCategory
import com.example.spotapp.data.collaboration.CollaborationRepository
import com.example.spotapp.data.collaboration.DetectedCollaborationTalent
import com.example.spotapp.data.Supabase
import com.example.spotapp.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.update

/** [mediaKey] identifies the media + edit that was analyzed, so a result is never shown for other media. */
data class TalentAiUiState(
    val isLoading: Boolean = false,
    val prediction: TalentPredictionResponse? = null,
    val error: String? = null,
    val mediaKey: String? = null,
    val savingCollaborationTalents: Boolean = false,
    val savedCollaborationCategories: Set<String> = emptySet(),
    val collaborationError: String? = null
)

class TalentAiViewModel : ViewModel() {

    private val repository =
        SpotAiRepository()

    private val _uiState =
        MutableStateFlow(
            TalentAiUiState()
        )

    val uiState: StateFlow<TalentAiUiState> =
        _uiState.asStateFlow()

    private var job: Job? = null
    private var collaborationJob: Job? = null
    private val collaborations = CollaborationRepository()

    /** Saves actual, selected model results only after the creator chooses to use them for collabs. */
    fun saveCollaborationTalents(expectedMediaKey: String, categories: List<String>) {
        val snapshot = _uiState.value
        if (snapshot.mediaKey != expectedMediaKey || snapshot.savingCollaborationTalents) return
        val suggestions = snapshot.prediction?.suggestions.orEmpty()
            .filter { it.composerCategory() in categories }
            .sortedByDescending { it.confidence }
            .distinctBy { it.composerCategory() }
        if (suggestions.isEmpty()) return
        val uid = Supabase.uid()
        collaborationJob = viewModelScope.launch {
            _uiState.update { it.copy(savingCollaborationTalents = true, collaborationError = null) }
            try {
                withTimeout(30_000) {
                    check(uid != null && Supabase.uid() == uid) { "Sign in again to save your talents." }
                    collaborations.saveDetectedTalents(
                        suggestions.map { DetectedCollaborationTalent(it.rawClass, it.confidence) },
                        expectedUserId = uid
                    ).getOrThrow()
                    check(Supabase.uid() == uid) { "Your account changed. Sign in again to view your talents." }
                }
                if (_uiState.value.mediaKey == expectedMediaKey) _uiState.update {
                    it.copy(savedCollaborationCategories = it.savedCollaborationCategories + suggestions.mapNotNull { value -> value.composerCategory() })
                }
            } catch (_: TimeoutCancellationException) {
                if (_uiState.value.mediaKey == expectedMediaKey) _uiState.update { it.copy(collaborationError = "Saving took too long. Tap again to retry.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (_uiState.value.mediaKey == expectedMediaKey) _uiState.update { it.copy(collaborationError = userMessage(error, "Couldn't save your collaboration talents. Tap again to retry.")) }
            } finally {
                if (_uiState.value.mediaKey == expectedMediaKey) _uiState.update { it.copy(savingCollaborationTalents = false) }
            }
        }
    }

    /** Starts a check unless this media + edit was already checked (loading, done, or failed); failures retry only on "Try again". */
    fun analyzeIfNeeded(context: Context, mediaUri: String, edit: PostMediaEdit) {
        if (_uiState.value.mediaKey != mediaKey(mediaUri, edit)) analyze(context, mediaUri, edit)
    }

    fun analyze(
        context: Context,
        mediaUri: String,
        edit: PostMediaEdit
    ) {
        val key = mediaKey(mediaUri, edit)
        collaborationJob?.cancel()
        job?.cancel()
        job = viewModelScope.launch {

            _uiState.value =
                TalentAiUiState(
                    isLoading = true,
                    mediaKey = key
                )

            try {

                val result =
                    repository.predictTalent(
                        context = context.applicationContext,
                        mediaUri = mediaUri,
                        edit = edit
                    )

                _uiState.value =
                    TalentAiUiState(
                        prediction = result,
                        mediaKey = key
                    )

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {

                _uiState.value =
                    TalentAiUiState(
                        error = spotAiError(e),
                        mediaKey = key
                    )
            }
        }
    }

    companion object {
        fun mediaKey(mediaUri: String, edit: PostMediaEdit) = "$mediaUri|$edit"
    }
}
