package com.kamboji.quiver.backup

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {
    private val plain = """{"app":"quiver","notes":[{"title":"₹ secret"}]}""".toByteArray()

    @Test fun roundTrip() {
        val env = BackupCrypto.encrypt(plain, "hunter22".toCharArray(), iterations = 10_000)
        assertTrue(BackupCrypto.isEncrypted(env))
        assertFalse("ciphertext must not contain the plaintext", env.toString().contains("secret"))
        assertArrayEquals(plain, BackupCrypto.decrypt(JSONObject(env.toString()), "hunter22".toCharArray()))
    }

    @Test(expected = BackupCrypto.WrongPassword::class)
    fun wrongPasswordFails() {
        val env = BackupCrypto.encrypt(plain, "hunter22".toCharArray(), iterations = 10_000)
        BackupCrypto.decrypt(env, "hunter23".toCharArray())
    }

    @Test(expected = BackupCrypto.WrongPassword::class)
    fun tamperingFails() {
        val env = BackupCrypto.encrypt(plain, "pw".toCharArray(), iterations = 10_000)
        val data = env.getString("data").toCharArray().also { it[3] = if (it[3] == 'A') 'B' else 'A' }
        BackupCrypto.decrypt(env.put("data", String(data)), "pw".toCharArray())
    }

    @Test fun pbkdf2MatchesTheJdk() {
        val salt = ByteArray(16) { it.toByte() }
        for (pw in listOf("hunter22", "pässwörd ₹", "x".repeat(80))) {
            val spec = javax.crypto.spec.PBEKeySpec(pw.toCharArray(), salt, 5000, 256)
            val jdk = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            assertArrayEquals(jdk, BackupCrypto.pbkdf2(pw.toCharArray(), salt, 5000))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun absurdIterationCountIsRefused() {
        val env = BackupCrypto.encrypt(plain, "pw".toCharArray(), iterations = 10_000)
        BackupCrypto.decrypt(env.put("iter", Int.MAX_VALUE), "pw".toCharArray())
    }

    @Test fun plainBackupIsNotEncrypted() =
        assertFalse(BackupCrypto.isEncrypted(JSONObject("""{"app":"quiver","version":1}""")))
}
