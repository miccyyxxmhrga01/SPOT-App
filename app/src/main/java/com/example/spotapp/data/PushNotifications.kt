package com.example.spotapp.data

import android.os.Vibrator
import android.os.VibrationEffect
import android.media.RingtoneManager
import android.media.Ringtone
import android.media.AudioManager
import android.media.AudioAttributes
import kotlinx.coroutines.flow.MutableSharedFlow
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.spotapp.MainActivity
import com.example.spotapp.R
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Phone notifications for new chat messages and for activity around your events, groups and requests.
 * Supabase's message-push function decides who gets one (not the sender, not muted chats, not people who
 * blocked the sender) and Firebase delivers it.
 */
object PushNotifications {
    const val CHANNEL_MESSAGES = "messages"
    const val CHANNEL_ACTIVITY = "activity"
    const val CHANNEL_UPLOADS = "uploads"
    const val EXTRA_CONVERSATION = "conversation_id"
    const val EXTRA_ACTIVITY = "open_activity"

    /** A chat a tapped notification asked to open; Messages clears it once the chat is shown. */
    val openConversation = MutableStateFlow<String?>(null)

    /** True after an activity notification was tapped; navigation shows Notifications and clears it. */
    val openActivity = MutableStateFlow(false)

    /** The chat on screen right now, so its new messages don't also pop up as notifications. */
    @Volatile var visibleConversationId: String? = null

    /** True while SPOT is on screen (between MainActivity's onStart and onStop). */
    @Volatile var appVisible = false

    /** A message that arrived while SPOT is open; the app shows it in its own banner. */
    data class InAppMessage(val conversationId: String, val title: String, val body: String)

    /** Messages for the in-app banner. Nobody listening (no signed-in screen) means a phone notification instead. */
    val inApp = MutableSharedFlow<InAppMessage>(extraBufferCapacity = 8)

    /**
     * Shows a new message: SPOT's own banner while the app is open, otherwise a phone notification, so a message
     * never pops up twice.
     */
    fun deliver(context: Context, conversationId: String, title: String, body: String) {
        if (appVisible && inApp.subscriptionCount.value > 0 && inApp.tryEmit(InAppMessage(conversationId, title, body))) {
            chime(context)
            return
        }
        show(context, conversationId, title, body)
    }

    private var lastChime: Ringtone? = null

    /**
     * The banner's sound: the Messages channel's own, so the member's choices for it apply, along with the phone's
     * silent, vibrate and Do Not Disturb modes (a notification sound is muted by Do Not Disturb like any other).
     */
    private fun chime(context: Context) {
        val channel = if (Build.VERSION.SDK_INT >= 26)
            context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_MESSAGES) else null
        if (channel != null && channel.importance < NotificationManager.IMPORTANCE_DEFAULT) return
        when (context.getSystemService(AudioManager::class.java)?.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> {
                // A channel whose sound the member switched off has none.
                val sound = if (channel != null) channel.sound ?: return else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                runCatching {
                    lastChime?.stop()
                    lastChime = RingtoneManager.getRingtone(context, sound)?.apply {
                        audioAttributes = channel?.audioAttributes ?: AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                        play()
                    }
                }
            }
            AudioManager.RINGER_MODE_VIBRATE -> runCatching {
                val vibrator = context.getSystemService(Vibrator::class.java)
                if (Build.VERSION.SDK_INT >= 26) vibrator?.vibrate(VibrationEffect.createOneShot(160, VibrationEffect.DEFAULT_AMPLITUDE))
                else @Suppress("DEPRECATION") vibrator?.vibrate(160)
            }
            else -> Unit
        }
    }

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(
            NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "New messages in your chats" },
            NotificationChannel(CHANNEL_ACTIVITY, "Events, groups and requests", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "People joining your events and groups, invitations and applications" },
            NotificationChannel(CHANNEL_UPLOADS, "Posting", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Progress while your post uploads (PostUploadService)" }))
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Sends this phone's notifications to the signed-in account. */
    suspend fun register() {
        if (Supabase.uid() == null) return
        save(FirebaseMessaging.getInstance().token.await())
    }

    suspend fun save(token: String) {
        rpc("save_push_token", buildJsonObject { put("p_token", token) })
    }

    /** Stops this phone getting the account's notifications. Call before signing out, while the session still works. */
    suspend fun unregister() {
        val messaging = runCatching { FirebaseMessaging.getInstance() }.getOrNull() ?: return
        runCatching { rpc("delete_push_token", buildJsonObject { put("p_token", messaging.token.await()) }) }
        // A fresh token also means nothing sent to the old one reaches this phone.
        runCatching { messaging.deleteToken().await() }
    }

    /** A new chat message. One notification per chat: a newer message replaces the older one. */
    fun show(context: Context, conversationId: String, title: String, body: String) =
        post(context, CHANNEL_MESSAGES, NotificationCompat.CATEGORY_MESSAGE, conversationId, title, body) {
            putExtra(EXTRA_CONVERSATION, conversationId)
        }

    /** Activity such as someone joining your event; tapping it opens Notifications. */
    fun showActivity(context: Context, notificationId: String, title: String, body: String) =
        post(context, CHANNEL_ACTIVITY, NotificationCompat.CATEGORY_SOCIAL, notificationId, title, body) {
            putExtra(EXTRA_ACTIVITY, true)
        }

    private fun post(context: Context, channel: String, category: String, tag: String, title: String, body: String,
        extras: Intent.() -> Unit) {
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(context, tag.hashCode(),
            Intent(context, MainActivity::class.java).apply(extras)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_spot_message)
            .setColor(ContextCompat.getColor(context, R.color.spot_gold))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(category)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try { NotificationManagerCompat.from(context).notify(tag, 1, notification) }
        catch (_: SecurityException) { /* Permission was withdrawn after the check. */ }
    }

    /** Reads what a tapped notification asked SPOT to open: a chat, or the Notifications tab. */
    fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_ACTIVITY, false) == true) {
            intent.removeExtra(EXTRA_ACTIVITY)
            openActivity.value = true
        }
        val id = intent?.getStringExtra(EXTRA_CONVERSATION)?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,200}")) } ?: return
        intent.removeExtra(EXTRA_CONVERSATION)
        openConversation.value = id
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) continuation.resume(task.result)
        else continuation.resumeWithException(task.exception ?: IllegalStateException("Firebase request failed."))
    }
}
