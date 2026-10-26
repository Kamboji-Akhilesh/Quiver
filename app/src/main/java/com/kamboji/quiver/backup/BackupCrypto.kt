package com.kamboji.quiver.backup

import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password encryption for backup files: AES-256-GCM with a PBKDF2-SHA256 key.
 * Standard JDK crypto only. GCM authenticates too, so a wrong password or a
 * tampered file fails loudly instead of decrypting to garbage.
 *
 * The envelope stays JSON (`"app": "quiver"`, `"enc": …`) so import can tell
 * encrypted from plain backups; KDF parameters travel with the file, so the
 * iteration count can rise later without breaking old backups.
 */
object BackupCrypto {
    // ponytail: 100k rounds ≈ 2.5 s on the emulator in a debug build (600k took 15 s; OWASP's 2023
    // floor is 600k). Each file records its count, so raise this once Quiver ships release builds.
    private const val ITERATIONS = 100_000
    private const val ENC = "aes-256-gcm"

    class WrongPassword : Exception("Wrong password, or the file is damaged")

    fun isEncrypted(json: JSONObject): Boolean = json.optString("enc") == ENC

    fun encrypt(plain: ByteArray, password: CharArray, iterations: Int = ITERATIONS): JSONObject {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
        return JSONObject()
            .put("app", "quiver")
            .put("enc", ENC)
            .put("kdf", "pbkdf2-sha256")
            .put("iter", iterations)
            .put("salt", b64(salt))
            .put("iv", b64(iv))
            .put("data", b64(cipher.doFinal(plain)))
    }

    fun decrypt(envelope: JSONObject, password: CharArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = key(password, unb64(envelope.getString("salt")), envelope.getInt("iter"))
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, unb64(envelope.getString("iv"))))
        return try {
            cipher.doFinal(unb64(envelope.getString("data")))
        } catch (e: AEADBadTagException) {
            throw WrongPassword()
        }
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int) =
        SecretKeySpec(pbkdf2(password, salt, iterations), "AES")

    /**
     * PBKDF2-HMAC-SHA256 for one 32-byte block (RFC 8018), on the platform's
     * native HMAC. Android's own "PBKDF2WithHmacSHA256" is pure Java: 1.5x slower
     * than this loop (output pinned against the JDK in BackupCryptoTest).
     */
    internal fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val pw = String(password).toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(pw, "HmacSHA256")) }
        pw.fill(0)
        var u = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
        val out = u.copyOf()
        repeat(iterations - 1) {
            u = mac.doFinal(u)
            for (i in out.indices) out[i] = (out[i].toInt() xor u[i].toInt()).toByte()
        }
        return out
    }

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String) = Base64.getDecoder().decode(s)
}
