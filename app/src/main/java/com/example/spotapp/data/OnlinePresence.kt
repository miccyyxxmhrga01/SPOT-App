package com.example.spotapp.data

import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Who has SPOT open right now. While the app is on screen, each phone joins the private Realtime channel
 * "spot-online" (signed-in members only) and shares its member id there; leaving the app or losing the connection
 * takes it off. Members who don't share their activity status aren't shown and don't see anyone either.
 */
object OnlinePresence {
    private const val TOPIC = "spot-online"
    private val members = MutableStateFlow<Set<String>>(emptySet())

    /** Member ids with SPOT open, including yours while you share. Empty when you don't share. */
    val online: StateFlow<Set<String>> = members.asStateFlow()

    /** Shares [uid] as online until cancelled; run it only while the app is in the foreground. */
    suspend fun run(uid: String, share: Boolean) {
        members.value = emptySet()
        if (!share || uid.isBlank()) return
        val channel = Supabase.client.channel(TOPIC) {
            isPrivate = true
            presence { key = uid }
        }
        // A member may have SPOT open on more than one phone; each connection joins and leaves on its own.
        val connections = mutableMapOf<String, String>()
        try {
            coroutineScope {
                launch {
                    channel.presenceChangeFlow().collect { action ->
                        action.leaves.values.forEach { connections.remove(it.presenceRef) }
                        action.joins.values.forEach { presence ->
                            (presence.state["uid"] as? JsonPrimitive)?.contentOrNull?.let { connections[presence.presenceRef] = it }
                        }
                        members.value = connections.values.toSet()
                    }
                }
                // A rejoin after a dropped connection starts presence afresh, so say we're here every time it subscribes.
                launch {
                    channel.status.collect { status ->
                        if (status == RealtimeChannel.Status.SUBSCRIBED) channel.track(buildJsonObject { put("uid", uid) })
                    }
                }
                channel.subscribe()
                // A refused or stalled join fails here, so the caller tries again a little later.
                withTimeoutOrNull(20_000) { channel.status.first { it == RealtimeChannel.Status.SUBSCRIBED } }
                    ?: throw IllegalStateException("Couldn't join the online list.")
                awaitCancellation()
            }
        } finally {
            members.value = emptySet()
            withContext(NonCancellable) {
                runCatching { channel.untrack() }
                runCatching { Supabase.client.realtime.removeChannel(channel) }
            }
        }
    }
}
