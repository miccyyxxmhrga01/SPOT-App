package com.example.spotapp.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Read regular metadata first, then finite fragmented clips that omit a container duration. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal suspend fun storyVideoDuration(context: Context, source: String): Long {
    val uri = Uri.parse(source)
    val metadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val duration = try { retriever.setDataSource(context, uri); retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0 }
            catch (_: Exception) { 0L } finally { retriever.release() }
        if (duration > 0) duration else {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                val formats = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                val trackDuration = formats.filter { it.containsKey(MediaFormat.KEY_DURATION) }
                    .maxOfOrNull { it.getLong(MediaFormat.KEY_DURATION) / 1000 } ?: 0
                if (trackDuration > 0) trackDuration else {
                    // Fragmented MP4s can have zero-duration headers but valid sample timestamps.
                    repeat(extractor.trackCount) { extractor.selectTrack(it) }
                    var lastTimestamp = -1L
                    var sampleCount = 0
                    val deadline = android.os.SystemClock.elapsedRealtime() + 15_000
                    while (extractor.sampleTime >= 0) {
                        currentCoroutineContext().ensureActive()
                        check(++sampleCount <= 100_000 && android.os.SystemClock.elapsedRealtime() < deadline)
                        lastTimestamp = maxOf(lastTimestamp, extractor.sampleTime)
                        if (lastTimestamp > 300_000_000 || !extractor.advance()) break
                    }
                    if (lastTimestamp > 0) lastTimestamp / 1000 + 1 else 0
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { 0L } finally { extractor.release() }
        }
    }
    if (metadata > 0) return metadata
    return withContext(Dispatchers.Main.immediate) {
        val player = ExoPlayer.Builder(context.applicationContext).build()
        try {
            withTimeout(15_000) {
                suspendCancellableCoroutine { continuation ->
                    player.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY && continuation.isActive) {
                                if (player.duration > 0) continuation.resume(player.duration)
                                else continuation.resumeWithException(IllegalArgumentException("The clip's length couldn't be read. Choose another video."))
                            }
                        }
                        override fun onPlayerError(error: PlaybackException) {
                            if (continuation.isActive) continuation.resumeWithException(IllegalArgumentException("This video couldn't open. Choose another."))
                        }
                    })
                    player.setMediaItem(MediaItem.fromUri(uri)); player.prepare()
                }
            }
        } finally { player.release() }
    }
}
