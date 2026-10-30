package com.example.spotapp

import android.content.Context
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.test.platform.app.InstrumentationRegistry
import com.example.spotapp.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

internal object PostTestMedia {
    fun image(context: Context, name: String = "image"): String {
        val folder = File(context.filesDir, "post_media/post-test-fixtures").apply { mkdirs() }
        val file = File(folder, "$name.png")
        val bitmap = Bitmap.createBitmap(400, 240, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(Color.rgb(247, 236, 210))
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(173, 132, 32); canvas.drawCircle(210f, 115f, 85f, paint)
        paint.color = Color.rgb(30, 30, 30); canvas.drawRect(40f, 150f, 360f, 200f, paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        return FileProvider.getUriForFile(context, "${context.packageName}.postmedia", file).toString()
    }
    fun video(context: Context): String {
        val folder = File(context.filesDir, "post_media/post-test-fixtures").apply { mkdirs() }
        val file = File(folder, "clip.mp4")
        context.resources.openRawResource(R.raw.dance).use { input -> file.outputStream().use { input.copyTo(it) } }
        return FileProvider.getUriForFile(context, "${context.packageName}.postmedia", file).toString()
    }
}

class MediaEditingTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun bytes(uri: String) = context.contentResolver.openInputStream(uri.toUri())!!.use { it.readBytes() }
    @Test fun cropRotateExportsRealPixelsWithoutChangingOriginal() = runBlocking {
        val uri = PostTestMedia.image(context)
        val original = bytes(uri)
        val rotated = PostMediaExporter.imagePreview(context, uri, PostMediaEdit(1))
        assertEquals(240, rotated.width); assertEquals(400, rotated.height); rotated.recycle()
        val square = PostMediaExporter.export(context, "post-edit-test", "post-image", uri, PostMediaEdit(1, "square"))
        val decoded = BitmapFactory.decodeByteArray(bytes(square), 0, bytes(square).size)
        assertEquals(240, decoded.width); assertEquals(240, decoded.height); decoded.recycle()
        assertArrayEquals(original, bytes(uri))
        assertEquals(square, PostMediaExporter.export(context, "post-edit-test", "post-image", uri, PostMediaEdit(1, "square")))
        assertEquals(uri, PostMediaExporter.export(context, "post-edit-test", "post-image", uri, PostMediaEdit()))
        PostMediaExporter.clear(context, "post-edit-test", "post-image")
    }
    @Test fun mutedExportHasVideoAndNoAudioAndPreservesTheSource() = runBlocking {
        val uri = PostTestMedia.video(context); val original = bytes(uri)
        val output = PostMediaExporter.export(context, "post-edit-test", "post-video", uri, PostMediaEdit(muted = true))
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, output.toUri(), null)
            val tracks = (0 until extractor.trackCount).map { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty() }
            assertEquals(1, tracks.count { it.startsWith("video/") }); assertFalse(tracks.any { it.startsWith("audio/") })
        } finally { extractor.release() }
        assertArrayEquals(original, bytes(uri))
        assertEquals("video/mp4", context.contentResolver.getType(output.toUri()))
        PostMediaExporter.clear(context, "post-edit-test", "post-video")
    }
    @Test fun draftsRoundTripEditsAndReadLegacyData() {
        val draft = PostDraft("post-codec", "Caption", listOf("Guitar"), listOf("Campus"), listOf("content://photo"), "followers", mediaEdits = mapOf("content://photo" to PostMediaEdit(1, "portrait", true)))
        assertEquals(draft, PostDraftCodec.decode(PostDraftCodec.encode(draft)))
        val personal = draft.copy(categories = emptyList(), personal = true)
        assertEquals(personal, PostDraftCodec.decode(PostDraftCodec.encode(personal)))
        val old = PostDraftCodec.decode("""{"id":"legacy","caption":"Before edits","mediaUris":[],"categories":[],"tags":[]} """)!!
        assertTrue(old.mediaEdits.isEmpty()); assertFalse(old.aiCaption || old.aiTagged || old.aiCategorySuggested || old.personal)
    }
    @Test fun realMediaLibraryReadsAlbumsFiltersAndOrderedPages() = runBlocking {
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT >= 29)
        val resolver = context.contentResolver
        val created = mutableListOf<android.net.Uri>()
        val album = "SPOT-test-${System.currentTimeMillis()}"
        val source = PostTestMedia.image(context)
        try {
            repeat(2) { index ->
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "post-flow-$index.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$album")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                })!!
                created += uri
                resolver.openOutputStream(uri)!!.use { output -> resolver.openInputStream(source.toUri())!!.use { it.copyTo(output) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
            val library = MediaLibraryRepository(context)
            val bucket = library.albums().first { it.name == album }
            val photos = library.page(MediaFilter.PHOTOS, bucket.id, null)
            assertEquals(created.map { it.toString() }.toSet(), photos.items.map { it.uri }.toSet())
            assertTrue(photos.items.first().id > photos.items.last().id)
            assertFalse(photos.hasMore)
            assertTrue(library.page(MediaFilter.VIDEOS, bucket.id, null).items.isEmpty())
            assertEquals(listOf(photos.items.last()), library.page(MediaFilter.ALL, bucket.id, photos.items.first()).items)
        } finally { created.forEach { resolver.delete(it, null, null) } }
    }
}
