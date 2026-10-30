package com.example.spotapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/** Real local edits using Android media APIs. Originals are never overwritten. */
object PostMediaExporter {
    // What SPOT uploads: photos up to 1440 px and videos up to 720p. Photos already this small are uploaded untouched.
    private const val UPLOAD_PHOTO_EDGE = 1440
    private const val SMALL_PHOTO_BYTES = 1024 * 1024L
    private const val UPLOAD_VIDEO_SHORT_SIDE = 720
    private const val UPLOAD_VIDEO_BITRATE = 3_000_000
    // Longer clips would take too long to re-encode on a phone, so they're uploaded as they are.
    private const val MAX_REENCODE_MS = 90_000L

    suspend fun imagePreview(context: Context, uri: String, edit: PostMediaEdit, maxEdge: Int = 1200): Bitmap =
        withContext(Dispatchers.IO) { renderImage(context, uri, edit, maxEdge) }

    private fun renderImage(context: Context, value: String, edit: PostMediaEdit, maxEdge: Int): Bitmap {
        val uri = value.toUri(); val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This image couldn't be opened. Choose another image." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
        var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("This image couldn't be opened.")
        val orientation = runCatching { resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } }.getOrNull()
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
            }
            postRotate(edit.rotation * 90f)
        }
        fun replace(next: Bitmap) { if (bitmap !== next) bitmap.recycle(); bitmap = next }
        replace(Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true))
        edit.aspectRatio?.let { ratio ->
            val current = bitmap.width.toFloat() / bitmap.height
            val width = if (current > ratio) (bitmap.height * ratio).roundToInt().coerceAtLeast(1) else bitmap.width
            val height = if (current < ratio) (bitmap.width / ratio).roundToInt().coerceAtLeast(1) else bitmap.height
            replace(Bitmap.createBitmap(bitmap, (bitmap.width - width) / 2, (bitmap.height - height) / 2, width, height))
        }
        val factor = maxEdge.toFloat() / maxOf(bitmap.width, bitmap.height)
        if (factor < 1) replace(Bitmap.createScaledBitmap(bitmap, (bitmap.width * factor).roundToInt().coerceAtLeast(1), (bitmap.height * factor).roundToInt().coerceAtLeast(1), true))
        // The filter and adjustments go on last, on the already small image.
        if (!edit.look.isOriginal) replace(Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { styled ->
            Canvas(styled).drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(edit.look.colorMatrix()) })
        })
        return bitmap
    }

    suspend fun thumbnail(context: Context, uri: String): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri.toUri())
            if (Build.VERSION.SDK_INT >= 27) retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 240, 240)
            else retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { original ->
                val scale = 240f / maxOf(original.width, original.height)
                Bitmap.createScaledBitmap(original, (original.width * scale).roundToInt().coerceAtLeast(1), (original.height * scale).roundToInt().coerceAtLeast(1), true).also { if (it !== original) original.recycle() }
            }
        } catch (_: Exception) { null } finally { retriever.release() }
    }

    /** The file to upload for a post item: edits applied and shrunk for upload, or [uri] itself when it's already fine. */
    suspend fun export(context: Context, uid: String, draftId: String, uri: String, edit: PostMediaEdit, progress: (Float) -> Unit = {}): String =
        withContext(Dispatchers.IO) {
            val output = prepareUpload(context, uri, edit, draftDirectory(context, uid, draftId), progress) ?: return@withContext uri
            FileProvider.getUriForFile(context, "${context.packageName}.postmedia", output).toString()
        }

    /** A smaller copy of a story, cover or profile photo in the cache, or null to upload [uri] as it is. Delete the copy after uploading. */
    suspend fun shrinkForUpload(context: Context, uri: String, progress: (Float) -> Unit = {}): File? =
        withContext(Dispatchers.IO) { prepareUpload(context, uri, PostMediaEdit(), File(context.cacheDir, "upload_media"), progress) }

    /**
     * Photos become JPEGs of at most [UPLOAD_PHOTO_EDGE] px and videos are re-encoded to 720p, so Cloudinary stores and serves
     * only what SPOT shows. Returns null when [uri] should be uploaded as it is: already small, or unedited and not shrinkable here.
     */
    private suspend fun prepareUpload(context: Context, uri: String, edit: PostMediaEdit, directory: File, progress: (Float) -> Unit): File? {
        val type = context.contentResolver.getType(uri.toUri()).orEmpty()
        val video = type.startsWith("video/")
        val edited = if (video) edit.muted else edit.changesImage
        val videoScale = if (video) videoScale(context, uri) else 1f
        // Unedited GIFs keep their animation.
        val shrink = if (video) videoScale < 1f else type != "image/gif" && !photoIsSmall(context, uri)
        if (!edited && !shrink) return null
        directory.mkdirs()
        val key = MessageDigest.getInstance("SHA-256").digest("$uri|$edit".toByteArray()).joinToString("") { "%02x".format(it) }
        val output = File(directory, "$key.${if (video) "mp4" else "jpg"}")
        if (output.length() > 0L) return output
        val partial = File(directory, "$key.partial")
        try {
            try {
                when {
                    video && shrink -> reencodeVideo(context.applicationContext, uri, videoScale, edit.muted, partial, progress)
                    video -> muteVideo(context, uri, partial)
                    else -> writeJpeg(renderImage(context, uri, edit, UPLOAD_PHOTO_EDGE), partial)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                // Shrinking only saves space: if this phone can't do it, the original is uploaded as before.
                if (!edited) return null
                if (!(video && shrink)) throw error
                partial.delete(); muteVideo(context, uri, partial)
            }
            currentCoroutineContext().ensureActive()
            // An unedited file that didn't get smaller is uploaded as it is.
            if (!edited && sizeOf(context, uri) in 1..partial.length()) return null
            check(partial.renameTo(output)) { "Couldn't save your edited media. Please retry." }
        } finally { if (partial.exists()) partial.delete() }
        return output
    }

    private fun sizeOf(context: Context, uri: String): Long =
        runCatching { context.contentResolver.openAssetFileDescriptor(uri.toUri(), "r")?.use { it.length } }.getOrNull() ?: -1L

    private fun photoIsSmall(context: Context, uri: String): Boolean {
        if (sizeOf(context, uri) !in 1..SMALL_PHOTO_BYTES) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { context.contentResolver.openInputStream(uri.toUri())?.use { BitmapFactory.decodeStream(it, null, bounds) } }
        return maxOf(bounds.outWidth, bounds.outHeight) in 1..UPLOAD_PHOTO_EDGE
    }

    /** JPEG has no transparency, so transparent areas go on white instead of turning black. */
    private fun writeJpeg(image: Bitmap, file: File) {
        var bitmap = image
        try {
            if (image.hasAlpha()) bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888).also {
                Canvas(it).apply { drawColor(Color.WHITE); drawBitmap(image, 0f, 0f, null) }
            }
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)) { "Image export failed." } }
        } finally { if (bitmap !== image) bitmap.recycle(); image.recycle() }
    }

    /** How much to scale a clip down to 720p, or 1 to leave it: already small or low-bitrate, too long to re-encode, or unreadable. */
    private fun videoScale(context: Context, uri: String): Float {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri.toUri())
            fun value(key: Int) = retriever.extractMetadata(key)?.toLongOrNull() ?: 0L
            val shortSide = minOf(value(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH), value(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
            val bitrate = value(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            if (shortSide <= UPLOAD_VIDEO_SHORT_SIDE || value(MediaMetadataRetriever.METADATA_KEY_DURATION) !in 1..MAX_REENCODE_MS ||
                bitrate in 1..UPLOAD_VIDEO_BITRATE * 3L / 2) 1f
            else UPLOAD_VIDEO_SHORT_SIDE.toFloat() / shortSide
        } catch (_: Exception) { 1f } finally { retriever.release() }
    }

    /** Re-encodes a clip as H.264 at [scale] of its size and a feed-friendly bitrate. [progress] runs on the main thread. */
    @androidx.annotation.OptIn(UnstableApi::class)
    private suspend fun reencodeVideo(context: Context, uri: String, scale: Float, muted: Boolean, output: File, progress: (Float) -> Unit) =
        withContext(Dispatchers.Main) {
            val item = EditedMediaItem.Builder(MediaItem.fromUri(uri)).setRemoveAudio(muted)
                .setEffects(Effects(emptyList(), listOf(ScaleAndRotateTransformation.Builder().setScale(scale, scale).build()))).build()
            val encoders = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(UPLOAD_VIDEO_BITRATE).build()).build()
            val main = Handler(Looper.getMainLooper())
            suspendCancellableCoroutine<Unit> { continuation ->
                // Transformer calls back on the thread that built it: the main thread here.
                val transformer = Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).setEncoderFactory(encoders)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) = continuation.resume(Unit)
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) =
                            continuation.resumeWithException(exportException)
                    }).build()
                val holder = ProgressHolder()
                val poll = object : Runnable {
                    override fun run() {
                        if (!continuation.isActive) return
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) progress(holder.progress / 100f)
                        main.postDelayed(this, 500)
                    }
                }
                transformer.start(item, output.absolutePath)
                main.post(poll)
                continuation.invokeOnCancellation { main.post { transformer.cancel() } }
            }
        }

    private suspend fun muteVideo(context: Context, uri: String, output: File) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            extractor.setDataSource(context, uri.toUri(), null)
            val tracks = (0 until extractor.trackCount).filter { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
            require(tracks.size == 1) { "This video can't be muted here. Keep its original sound or choose another clip." }
            val index = tracks.single(); val format = extractor.getTrackFormat(index)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val retriever = MediaMetadataRetriever()
            try { retriever.setDataSource(context, uri.toUri()); val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0; muxer.setOrientationHint(rotation) }
            finally { retriever.release() }
            val target = muxer.addTrack(format)
            extractor.selectTrack(index); muxer.start(); started = true
            val size = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 4 * 1024 * 1024
            val buffer = ByteBuffer.allocateDirect(size.coerceIn(4 * 1024 * 1024, 32 * 1024 * 1024))
            val info = MediaCodec.BufferInfo(); var frames = 0
            while (true) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                buffer.clear()
                val bytes = extractor.readSampleData(buffer, 0)
                if (bytes < 0) break
                val flags = extractor.sampleFlags
                // Only complete, unencrypted samples can be copied directly into the output.
                require(flags and MediaExtractor.SAMPLE_FLAG_SYNC.inv() == 0) {
                    "This clip can't be muted here. Keep its original sound or choose another clip."
                }
                val codecFlags = if (flags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(0, bytes, extractor.sampleTime, codecFlags)
                muxer.writeSampleData(target, buffer, info); frames++; extractor.advance()
            }
            check(frames > 0) { "The video has no readable frames." }
            muxer.stop(); started = false
        } finally { if (started) runCatching { muxer?.stop() }; muxer?.release(); extractor.release() }
    }

    fun clear(context: Context, uid: String, draftId: String) {
        val directory = draftDirectory(context, uid, draftId)
        if (directory.exists()) directory.deleteRecursively()
    }

    /** Every draft's media for [uid], once that account is deleted. */
    fun clearAccount(context: Context, uid: String) {
        draftDirectory(context, uid, "_").parentFile?.takeIf { it.exists() }?.deleteRecursively()
    }
    private fun draftDirectory(context: Context, uid: String, draftId: String): File {
        require(uid.matches(Regex("[a-zA-Z0-9_-]{1,128}")) && draftId.matches(Regex("[a-zA-Z0-9_-]{1,100}")))
        val root = File(context.filesDir, "post_media").canonicalFile
        return File(root, "$uid/$draftId").canonicalFile.also { check(it.path.startsWith(root.path + File.separator)) }
    }
}
