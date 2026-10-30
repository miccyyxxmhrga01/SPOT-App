package com.example.spotapp.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeStore: DataStore<Preferences> by preferencesDataStore(name = "spot_theme")

enum class ThemeMode(val label: String) {
    System("System"), Light("Light"), Dark("Dark")
}

/** Which look the member picked; follows the phone's setting until they choose. */
object ThemePreferences {
    private val MODE = stringPreferencesKey("theme_mode")

    fun mode(context: Context): Flow<ThemeMode> =
        context.themeStore.data.map { prefs -> ThemeMode.entries.firstOrNull { it.name == prefs[MODE] } ?: ThemeMode.System }

    suspend fun setMode(context: Context, mode: ThemeMode) {
        context.themeStore.edit { it[MODE] = mode.name }
    }
}
