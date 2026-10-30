package com.example.spotapp.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * What lets SPOT open instantly: whether this phone already reached Home with an account, and the last public feed it
 * showed. MainActivity then starts on Home with the saved posts while the account check and fresh posts load behind
 * it, and Home still has something to show offline. Only public posts are saved, in the app's cache folder.
 */
object StartupCache {
    private lateinit var app: Context
    private val json = Json { ignoreUnknownKeys = true }

    fun init(context: Context) { app = context.applicationContext }

    private val ready get() = ::app.isInitialized
    private val prefs get() = app.getSharedPreferences("spot_startup", Context.MODE_PRIVATE)
    private fun valid(uid: String) = uid.matches(Regex("[A-Za-z0-9_-]{1,128}"))
    private fun folder(uid: String) = File(app.cacheDir, "feed/$uid")

    /** True once [uid] has opened Home on this phone. */
    fun reachedHome(uid: String): Boolean = ready && valid(uid) && prefs.getBoolean("home_$uid", false)

    fun rememberHome(uid: String, home: Boolean) {
        if (ready && valid(uid)) prefs.edit().putBoolean("home_$uid", home).apply()
    }

    internal fun feed(uid: String, name: String): List<PostRow> {
        if (!ready || !valid(uid)) return emptyList()
        return runCatching { json.decodeFromString<List<PostRow>>(File(folder(uid), "$name.json").readText()) }.getOrDefault(emptyList())
    }

    internal suspend fun saveFeed(uid: String, name: String, rows: List<PostRow>) {
        if (!ready || !valid(uid)) return
        withContext(Dispatchers.IO) {
            runCatching {
                val target = File(folder(uid).apply { mkdirs() }, "$name.json")
                // Written next to the old copy and swapped in, so a crash mid-write never leaves half a feed.
                val partial = File(target.parentFile, "$name.json.partial")
                partial.writeText(json.encodeToString(rows))
                if (!partial.renameTo(target)) { target.delete(); partial.renameTo(target) }
            }
        }
    }

    /** Everything kept for [uid], once that account is deleted. */
    fun forget(uid: String) {
        if (!ready || !valid(uid)) return
        prefs.edit().remove("home_$uid").apply()
        folder(uid).deleteRecursively()
    }
}
