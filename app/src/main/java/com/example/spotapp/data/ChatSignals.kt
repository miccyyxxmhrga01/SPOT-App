package com.example.spotapp.data

import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * "typing…" and "just read" for one open chat, sent between phones on the chat's private Realtime channel
 * "chat:<id>" (members only, see supabase spot_chat_signals). Nothing is stored: a missed signal only means
 * "typing…" doesn't show, or Seen updates on the next refresh.
 */
class ChatSignals(private val conversationId: String, private val uid: String) {
    private var channel: RealtimeChannel? = null

    /** Listens until cancelled. [onTyping] and [onRead] get the member id of whoever is typing or just read the chat. */
    suspend fun run(onTyping: (String) -> Unit, onRead: (String) -> Unit) {
        val joined = Supabase.client.channel("chat:$conversationId") { isPrivate = true }
        try {
            coroutineScope {
                launch { joined.broadcastFlow<JsonObject>(TYPING).collect { sender(it)?.let(onTyping) } }
                launch { joined.broadcastFlow<JsonObject>(READ).collect { sender(it)?.let(onRead) } }
                joined.subscribe()
                channel = joined
                awaitCancellation()
            }
        } finally {
            channel = null
            withContext(NonCancellable) { runCatching { Supabase.client.realtime.removeChannel(joined) } }
        }
    }

    suspend fun typing() = send(TYPING)
    suspend fun read() = send(READ)

    private suspend fun send(event: String) {
        val joined = channel?.takeIf { it.status.value == RealtimeChannel.Status.SUBSCRIBED } ?: return
        runCatching { joined.broadcast(event, buildJsonObject { put("uid", uid) }) }
    }

    private fun sender(message: JsonObject) = (message["uid"] as? JsonPrimitive)?.contentOrNull?.takeIf { it != uid }

    private companion object {
        const val TYPING = "typing"
        const val READ = "read"
    }
}
