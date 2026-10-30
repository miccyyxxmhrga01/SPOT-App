package com.example.spotapp.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class ChatMediaCryptoTest {
    private val path = "chat_media/demo/me/message"

    @Test fun encryptedAttachmentRoundTripsAndRejectsTampering() {
        val original = ByteArray(180_000) { (it * 31).toByte() }
        val encrypted = ByteArrayOutputStream()
        val (secret, size) = ChatMediaCrypto.encrypt(ByteArrayInputStream(original), encrypted, path)
        assertEquals(original.size.toLong(), size)
        assertFalse(encrypted.toByteArray().contentEquals(original))
        val restored = ByteArrayOutputStream()
        ChatMediaCrypto.decrypt(ByteArrayInputStream(encrypted.toByteArray()), restored, path, secret, size)
        assertArrayEquals(original, restored.toByteArray())
        assertThrows(Exception::class.java) {
            ChatMediaCrypto.decrypt(ByteArrayInputStream(encrypted.toByteArray()), ByteArrayOutputStream(), path + "-other", secret, size)
        }
        val altered = encrypted.toByteArray().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(Exception::class.java) {
            ChatMediaCrypto.decrypt(ByteArrayInputStream(altered), ByteArrayOutputStream(), path, secret, size)
        }
    }

    @Test fun emptyOrOversizedAttachmentsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ChatMediaCrypto.encrypt(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream(), path)
        }
        val stream = object : ByteArrayInputStream(ByteArray(64 * 1024)) {
            var remaining = ChatMediaCrypto.MAX_BYTES + 1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (remaining <= 0) return -1
                val count = minOf(len.toLong(), remaining).toInt()
                b.fill(1, off, off + count)
                remaining -= count
                return count
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChatMediaCrypto.encrypt(stream, ByteArrayOutputStream(), path)
        }
    }
}
