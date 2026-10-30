package com.example.spotapp.data.collaboration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotapp.data.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class CollaborationUiState(
    val loading: Boolean = true,
    val creators: List<CollaborationCreator> = emptyList(),
    val myTalents: List<String> = emptyList(),
    val preferences: CollabPreferences = CollabPreferences(),
    val removing: String? = null,
    /** The last suggestion dismissed, while it can still be undone. */
    val dismissed: CollaborationCreator? = null,
    val dismissedAt: Int = 0,
    val savingPreferences: Boolean = false,
    val error: String? = null,
    val talentError: String? = null,
    val actionError: String? = null
)

class CollaborationViewModel(private val repository: CollaborationRepository = CollaborationRepository()) : ViewModel() {
    private val state = MutableStateFlow(CollaborationUiState())
    val uiState = state.asStateFlow()
    private var load: Job? = null

    fun loadSuggestions() {
        load?.cancel()
        load = viewModelScope.launch {
            state.update { it.copy(loading = true, error = null) }
            try {
                withTimeout(25_000) {
                    val mine = async { repository.getMyTalents() }
                    val preferences = async { repository.getPreferences() }
                    val creators = repository.getCreators()
                    mine.await().mapCatching { talents -> Triple(talents, preferences.await().getOrThrow(), creators.getOrThrow()) }
                }.onSuccess { (talents, preferences, creators) ->
                    state.update { it.copy(loading = false, creators = creators, myTalents = talents, preferences = preferences) }
                }.onFailure { error -> state.update { it.copy(loading = false,
                    error = userMessage(error, "Collaborators couldn't load. Please retry.")) } }
            } catch (_: TimeoutCancellationException) {
                state.update { it.copy(loading = false, error = "This is taking longer than expected. Please retry.") }
            }
        }
    }

    /** Suggestions are reloaded afterwards, since shared talents change how creators rank. */
    fun removeTalent(talent: String) {
        if (state.value.removing != null) return
        state.update { it.copy(removing = talent, talentError = null) }
        viewModelScope.launch {
            try {
                withTimeout(25_000) { repository.removeTalent(talent) }
                    .onSuccess {
                        state.update { it.copy(removing = null, myTalents = it.myTalents - talent) }
                        loadSuggestions()
                    }
                    .onFailure { error -> state.update { it.copy(removing = null,
                        talentError = userMessage(error, "Couldn't remove $talent. Please retry.")) } }
            } catch (_: TimeoutCancellationException) {
                state.update { it.copy(removing = null, talentError = "This is taking longer than expected. Please retry.") }
            }
        }
    }

    /** Hides the suggestion right away; it comes back if saving fails. */
    fun dismiss(creator: CollaborationCreator) {
        state.update { it.copy(creators = it.creators - creator, dismissed = creator,
            dismissedAt = it.creators.indexOf(creator).coerceAtLeast(0), actionError = null) }
        viewModelScope.launch {
            val saved = try { withTimeout(25_000) { repository.dismiss(creator.profile.uid) } }
                catch (_: TimeoutCancellationException) { Result.failure(IllegalStateException("This is taking longer than expected. Please retry.")) }
            saved.onFailure { error -> state.update { it.copy(creators = restore(it.creators, creator, it.dismissedAt), dismissed = null,
                actionError = userMessage(error, "Couldn't hide this suggestion. Please retry.")) } }
        }
    }

    fun undoDismiss() {
        val creator = state.value.dismissed ?: return
        state.update { it.copy(creators = restore(it.creators, creator, it.dismissedAt), dismissed = null, actionError = null) }
        viewModelScope.launch {
            val saved = try { withTimeout(25_000) { repository.undoDismiss(creator.profile.uid) } }
                catch (_: TimeoutCancellationException) { Result.failure(IllegalStateException("This is taking longer than expected. Please retry.")) }
            saved.onFailure { error -> state.update { it.copy(creators = it.creators - creator,
                actionError = userMessage(error, "Couldn't bring this suggestion back. Please retry.")) } }
        }
    }

    fun clearDismissed() = state.update { it.copy(dismissed = null) }
    fun clearActionError() = state.update { it.copy(actionError = null) }

    /** Saving changes who matches, so suggestions reload. [done] runs only once the preferences are saved. */
    fun savePreferences(preferences: CollabPreferences, done: () -> Unit) {
        if (state.value.savingPreferences) return
        state.update { it.copy(savingPreferences = true, actionError = null) }
        viewModelScope.launch {
            val saved = try { withTimeout(25_000) { repository.savePreferences(preferences) } }
                catch (_: TimeoutCancellationException) { Result.failure(IllegalStateException("This is taking longer than expected. Please retry.")) }
            saved.onSuccess {
                state.update { it.copy(savingPreferences = false, preferences = preferences) }
                done(); loadSuggestions()
            }.onFailure { error -> state.update { it.copy(savingPreferences = false,
                actionError = userMessage(error, "Couldn't save your preferences. Please retry.")) } }
        }
    }

    private fun restore(list: List<CollaborationCreator>, creator: CollaborationCreator, at: Int) =
        if (list.any { it.profile.uid == creator.profile.uid }) list
        else list.toMutableList().apply { add(at.coerceIn(0, size), creator) }
}
