package com.example.spotapp.data

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Receives SPOT's message and activity pushes from Firebase. They carry data only, so SPOT decides whether to show each one.
 * Firebase calls these on a background thread and may stop the service right after, so the work finishes inside them.
 */
class SpotMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Firebase changed this phone's address: keep the signed-in account's notifications coming here.
        runBlocking { withTimeoutOrNull(10_000) { runCatching { awaitSession(); if (Supabase.uid() != null) PushNotifications.save(token) } } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"]
        if (type != "message" && type != "activity") return
        // Firebase may start SPOT just for this push, so wait briefly for the saved session.
        val signedIn = runBlocking { withTimeoutOrNull(5_000) { awaitSession(); Supabase.uid() } }
        // Only the account signed in on this phone sees its messages and activity.
        if (signedIn == null || signedIn != data["recipient_id"]) return
        val title = data["title"].orEmpty().ifBlank { "SPOT" }
        val body = data["body"].orEmpty()
        if (type == "activity") PushNotifications.showActivity(this, data["notification_id"] ?: return, title, body)
        // The reader of a chat already sees its new messages.
        else data["conversation_id"]?.takeIf { it != PushNotifications.visibleConversationId }?.let {
            PushNotifications.deliver(this, it, title, body)
        }
    }
}
