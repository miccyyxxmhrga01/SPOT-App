package com.example.spotapp.data

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.spotapp.MainActivity
import com.example.spotapp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Keeps SPOT running while a post uploads, with its progress in a quiet notification, so leaving SPOT or swiping it
 * away doesn't stop the upload. The upload itself runs in [PostUploadManager]; this service only holds the process
 * and mirrors its progress. A failed upload left in the background gets a notification that opens SPOT to retry.
 */
class PostUploadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, progress(0f), type)
        } catch (_: Exception) {
            // Not allowed right now (e.g. started from the background): the upload carries on while SPOT is open.
            stopSelf(); return START_NOT_STICKY
        }
        if (!watching) {
            watching = true
            scope.launch {
                PostUploadManager.upload.collect { upload ->
                    when (upload?.status) {
                        PostUpload.Status.UPLOADING -> notify(progress(upload.progress))
                        PostUpload.Status.FAILED -> { if (!PushNotifications.appVisible) failed(upload.error); finish() }
                        else -> finish()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    // Android 15 limits how long a data-sync service may run; the upload's own 5-minute limit ends sooner.
    override fun onTimeout(startId: Int, fgsType: Int) = finish()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun openSpot() = PendingIntent.getActivity(this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun progress(value: Float): Notification =
        NotificationCompat.Builder(this, PushNotifications.CHANNEL_UPLOADS)
            .setSmallIcon(R.drawable.ic_stat_spot_message)
            .setContentTitle("Posting to SPOT")
            .setContentText(if (value <= 0f) "Preparing your post…" else "${(value * 100).roundToInt()}% uploaded")
            .setProgress(100, (value * 100).roundToInt(), value <= 0f)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openSpot())
            .build()

    private fun notify(notification: Notification) {
        if (PushNotifications.canNotify(this)) {
            try { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification) } catch (_: SecurityException) { }
        }
    }

    private fun failed(reason: String?) {
        if (!PushNotifications.canNotify(this)) return
        val notification = NotificationCompat.Builder(this, PushNotifications.CHANNEL_UPLOADS)
            .setSmallIcon(R.drawable.ic_stat_spot_message)
            .setContentTitle("Your post wasn't shared")
            .setContentText(reason?.takeIf { it.isNotBlank() } ?: "Open SPOT to try again. Your draft is saved.")
            .setAutoCancel(true)
            .setContentIntent(openSpot())
            .build()
        try { NotificationManagerCompat.from(this).notify(FAILED_ID, notification) } catch (_: SecurityException) { }
    }

    companion object {
        private const val NOTIFICATION_ID = 7301
        private const val FAILED_ID = 7302

        /** Called as an upload starts, while SPOT is on screen. */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, PostUploadService::class.java)) }
        }
    }
}
