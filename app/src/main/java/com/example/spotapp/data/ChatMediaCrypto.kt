package com.example.spotapp.data

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Cloudinary holds ciphertext; the key and nonce live only in the member-readable message. */
internal object ChatMediaCrypto {
    const val MAX_BYTES = 25L * 1024 * 1024

    data class Secret(val key: String, val iv: String)

    fun encrypt(input: InputStream, output: OutputStream, path: String): Pair<Secret, Long> {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().encoded
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            updateAAD(path.toByteArray(Charsets.UTF_8))
        }
        var size = 0L
        CipherOutputStream(output, cipher).use { encrypted ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                size += count
                require(size <= MAX_BYTES) { "Choose a photo or video under 25 MB." }
                encrypted.write(buffer, 0, count)
            }
        }
        require(size > 0) { "Choose a photo or video with content." }
        return Secret(Base64.getEncoder().encodeToString(key), Base64.getEncoder().encodeToString(iv)) to size
    }

    fun decrypt(input: InputStream, output: OutputStream, path: String, secret: Secret, expectedSize: Long) {
        require(expectedSize in 1..MAX_BYTES)
        val key = Base64.getDecoder().decode(secret.key)
        val iv = Base64.getDecoder().decode(secret.iv)
        require(key.size == 32 && iv.size == 12) { "This attachment's security details are invalid." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            updateAAD(path.toByteArray(Charsets.UTF_8))
        }
        var size = 0L
        CipherInputStream(input, cipher).use { decrypted ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = decrypted.read(buffer)
                if (count < 0) break
                size += count
                require(size <= expectedSize) { "This attachment is invalid." }
                output.write(buffer, 0, count)
            }
        }
        require(size == expectedSize) { "This attachment is incomplete." }
    }
}
