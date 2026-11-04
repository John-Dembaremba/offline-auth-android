package io.github.johndembaremba.offlineauth.crypto

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.johndembaremba.offlineauth.model.EncryptedEnvelope
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PublicKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

@RunWith(AndroidJUnit4::class)
class EnvelopeDecryptorTest {
    private val testAlias = "offline_auth_envelope_test_key"

    @After
    fun tearDown() {
        val keyStore =
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
            }

        if (keyStore.containsAlias(testAlias)) {
            keyStore.deleteEntry(testAlias)
        }
    }

    @Test
    fun decrypt_returnsOriginalPlaintext() {
        val keyStoreManager = KeyStoreManager(testAlias)
        val envelopeDecryptor = EnvelopeDecryptor(keyStoreManager)

        val publicKey =
            decodePublicKey(
                keyStoreManager.getOrCreatePublicKey(),
            )

        val aesKey =
            KeyGenerator
                .getInstance("AES")
                .apply {
                    init(256)
                }.generateKey()

        val nonce =
            ByteArray(12) { index ->
                index.toByte()
            }

        val plaintext =
            """
            {
              "username": "test-user",
              "verifier": "pbkdf2_sha256${'$'}1000000${'$'}salt${'$'}hash"
            }
            """.trimIndent().toByteArray()

        val ciphertext =
            encryptPayload(
                plaintext = plaintext,
                aesKey = aesKey.encoded,
                nonce = nonce,
            )

        val encryptedKey =
            encryptAesKey(
                aesKey = aesKey.encoded,
                publicKey = publicKey,
            )

        val envelope =
            EncryptedEnvelope(
                keyAlgorithm = "RSA-OAEP-256",
                contentAlgorithm = "A256GCM",
                encryptedKey =
                    Base64.encodeToString(
                        encryptedKey,
                        Base64.NO_WRAP,
                    ),
                nonce =
                    Base64.encodeToString(
                        nonce,
                        Base64.NO_WRAP,
                    ),
                ciphertext =
                    Base64.encodeToString(
                        ciphertext,
                        Base64.NO_WRAP,
                    ),
            )

        val decrypted =
            envelopeDecryptor.decrypt(
                envelope = envelope,
            )

        assertArrayEquals(
            plaintext,
            decrypted,
        )
    }

    private fun decodePublicKey(encodedPublicKey: String): PublicKey {
        val decoded =
            Base64.decode(
                encodedPublicKey,
                Base64.NO_WRAP,
            )

        return KeyFactory
            .getInstance("RSA")
            .generatePublic(
                X509EncodedKeySpec(decoded),
            )
    }

    private fun encryptAesKey(
        aesKey: ByteArray,
        publicKey: PublicKey,
    ): ByteArray {
        val cipher =
            Cipher.getInstance(
                "RSA/ECB/OAEPWithSHA-256AndMGF1Padding",
            )

        val oaepParameters =
            OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA1,
                PSource.PSpecified.DEFAULT,
            )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            publicKey,
            oaepParameters,
        )

        return cipher.doFinal(aesKey)
    }

    private fun encryptPayload(
        plaintext: ByteArray,
        aesKey: ByteArray,
        nonce: ByteArray,
    ): ByteArray {
        val cipher =
            Cipher.getInstance(
                "AES/GCM/NoPadding",
            )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(
                aesKey,
                "AES",
            ),
            GCMParameterSpec(
                128,
                nonce,
            ),
        )

        return cipher.doFinal(plaintext)
    }
}
