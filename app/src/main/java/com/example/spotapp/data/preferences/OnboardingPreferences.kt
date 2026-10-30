package com.example.spotapp.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Single DataStore instance scoped to the app Context.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "spot_prefs")

/**
 * Tracks whether the user has completed the onboarding flow.
 * Backed by Jetpack DataStore (async, coroutine/Flow-friendly — the
 * modern replacement for SharedPreferences).
 */
object OnboardingPreferences {

    private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

    /** Emits true once onboarding has been completed; false until then. */
    fun isOnboardingCompleted(context: Context): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[ONBOARDING_COMPLETED] ?: false }

    /** Call when the user finishes (or skips) onboarding. */
    suspend fun setOnboardingCompleted(context: Context) {
        context.dataStore.edit { prefs -> prefs[ONBOARDING_COMPLETED] = true }
    }
}