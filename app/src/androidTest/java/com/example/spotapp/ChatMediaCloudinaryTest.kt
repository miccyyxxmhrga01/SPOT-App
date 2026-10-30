package com.example.spotapp

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cloudinary.android.MediaManager
import com.example.spotapp.data.ChatMediaCrypto
import com.example.spotapp.data.cloudinary.CloudinaryManager
import com.example.spotapp.data.cloudinary.CloudinaryUploader
import com.example.spotapp.data.validChatMediaUrl
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@RunWith(AndroidJUnit4::class)
class ChatMediaCloudinaryTest {
    @Test fun encryptedPhotoUploadsAndDownloadsWithoutRevealingOriginal() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        CloudinaryManager.init(context)
        val id = UUID.randomUUID().toString()
        val path = "chat_media/demo-chat-test/test-client/$id"
        val original = ByteArray(4_096) { (it * 13).toByte() }
        val encrypted = File.createTempFile("spot-chat-test-", ".enc", context.cacheDir)
        try {
            val (secret, size) = encrypted.outputStream().use { output ->
                ChatMediaCrypto.encrypt(ByteArrayInputStream(original), output, path)
            }
            val url = withTimeout(90_000) {
                suspendCancellableCoroutine<String> { continuation ->
                    val request = CloudinaryUploader.uploadMedia(Uri.fromFile(encrypted), "spot/$path",
                        onSuccess = { if (continuation.isActive) continuation.resume(it) },
                        onError = { if (continuation.isActive) continuation.resumeWithException(IllegalStateException(it)) },
                        resourceType = "raw")
                    continuation.invokeOnCancellation { MediaManager.get().cancelRequest(request) }
                }
            }
            assertTrue("Cloudinary returned an unexpected media address", validChatMediaUrl(url, path))
            val connection = URL(url).openConnection() as HttpsURLConnection
            connection.connectTimeout = 15_000; connection.readTimeout = 30_000
            val downloaded = try {
                assertEquals(200, connection.responseCode)
                connection.inputStream.use { it.readBytes() }
            } finally { connection.disconnect() }
            assertFalse(downloaded.contentEquals(original))
            val restored = ByteArrayOutputStream()
            ChatMediaCrypto.decrypt(ByteArrayInputStream(downloaded), restored, path, secret, size)
            assertArrayEquals(original, restored.toByteArray())
        } finally { encrypted.delete() }
    }
}
