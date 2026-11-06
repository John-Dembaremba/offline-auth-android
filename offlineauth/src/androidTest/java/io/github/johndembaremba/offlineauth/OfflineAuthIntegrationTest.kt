package io.github.johndembaremba.offlineauth

import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.johndembaremba.offlineauth.model.EncryptedEnvelope
import io.github.johndembaremba.offlineauth.serialization.SnapshotParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyFactory
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PSource

@RunWith(AndroidJUnit4::class)
class OfflineAuthIntegrationTest {
    @Test
    fun syncAndAuthenticate_completeEndToEndFlow() =
        runBlocking {
            val context =
                ApplicationProvider.getApplicationContext<android.content.Context>()

            val manager =
                OfflineAuthManager.create(context)

            val publicKey =
                decodePublicKey(
                    manager.getPublicKey(),
                )

            val username = "integration-user"
            val password = "correct-password"

            val now = Instant.now()

            val credentialJson =
                createCredentialJson(
                    password = password,
                    issuedAt = now.minusSeconds(60),
                    expiresAt = now.plusSeconds(3600),
                )

            val envelope =
                encryptCredential(
                    publicKey = publicKey,
                    plaintext = credentialJson.encodeToByteArray(),
                )

            val snapshotJson =
                createSnapshotJson(
                    username = username,
                    envelope = envelope,
                )

            val snapshot =
                SnapshotParser().parse(
                    snapshotJson,
                )

            val result =
                manager.sync(
                    snapshot,
                )

            assertEquals(
                listOf(username),
                result.successfulUsernames,
            )

            assertTrue(
                result.failures.isEmpty(),
            )

            assertTrue(
                manager.authenticate(
                    username = username,
                    password = password.toCharArray(),
                ),
            )

            assertFalse(
                manager.authenticate(
                    username = username,
                    password = "wrong-password".toCharArray(),
                ),
            )
        }

    private fun createCredentialJson(
        password: String,
        issuedAt: Instant,
        expiresAt: Instant,
    ): String {
        val passwordVerifier =
            createPasswordVerifier(
                password = password,
            )

        return """
            {
              "password_algorithm": "pbkdf2_sha256",
              "password_verifier": "$passwordVerifier",
              "credential_version": 1,
              "issued_at": "$issuedAt",
              "expires_at": "$expiresAt"
            }
            """.trimIndent()
    }

    private fun createPasswordVerifier(password: String): String {
        val salt = "integration-test-salt"

        val passwordSpec =
            PBEKeySpec(
                password.toCharArray(),
                salt.toByteArray(),
                PBKDF2_ITERATIONS,
                PBKDF2_KEY_LENGTH_BITS,
            )

        return try {
            val secretKeyFactory =
                SecretKeyFactory.getInstance(
                    "PBKDF2WithHmacSHA256",
                )

            val encodedHash =
                Base64.encodeToString(
                    secretKeyFactory
                        .generateSecret(passwordSpec)
                        .encoded,
                    Base64.NO_WRAP,
                )

            "pbkdf2_sha256" +
                "${'$'}$PBKDF2_ITERATIONS" +
                "${'$'}$salt" +
                "${'$'}$encodedHash"
        } finally {
            passwordSpec.clearPassword()
        }
    }

    private fun decodePublicKey(encodedPublicKey: String): PublicKey {
        val decodedKey =
            Base64.decode(
                encodedPublicKey,
                Base64.NO_WRAP,
            )

        return KeyFactory
            .getInstance("RSA")
            .generatePublic(
                X509EncodedKeySpec(
                    decodedKey,
                ),
            )
    }

    private fun encryptCredential(
        publicKey: PublicKey,
        plaintext: ByteArray,
    ): EncryptedEnvelope {
        val aesKey =
            KeyGenerator
                .getInstance("AES")
                .apply {
                    init(AES_KEY_SIZE_BITS)
                }.generateKey()

        val nonce =
            ByteArray(
                GCM_NONCE_SIZE_BYTES,
            ).also {
                SecureRandom().nextBytes(it)
            }

        val contentCipher =
            Cipher.getInstance(
                "AES/GCM/NoPadding",
            )

        contentCipher.init(
            Cipher.ENCRYPT_MODE,
            aesKey,
            GCMParameterSpec(
                GCM_TAG_SIZE_BITS,
                nonce,
            ),
        )

        val ciphertext =
            contentCipher.doFinal(
                plaintext,
            )

        val keyCipher =
            Cipher.getInstance(
                "RSA/ECB/OAEPWithSHA-256AndMGF1Padding",
            )

        keyCipher.init(
            Cipher.ENCRYPT_MODE,
            publicKey,
            OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA1,
                PSource.PSpecified.DEFAULT,
            ),
        )

        val encryptedKey =
            keyCipher.doFinal(
                aesKey.encoded,
            )

        return EncryptedEnvelope(
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
    }

    private fun createSnapshotJson(
        username: String,
        envelope: EncryptedEnvelope,
    ): String =
        """
        {
          "protocol_version": 1,
          "credentials": [
            {
              "user_id": "integration-user-id",
              "username": "$username",
              "envelope": {
                "key_algorithm": "${envelope.keyAlgorithm}",
                "content_algorithm": "${envelope.contentAlgorithm}",
                "encrypted_key": "${envelope.encryptedKey}",
                "nonce": "${envelope.nonce}",
                "ciphertext": "${envelope.ciphertext}"
              }
            }
          ],
          "failures": []
        }
        """.trimIndent()

    companion object {
        private const val AES_KEY_SIZE_BITS = 256
        private const val GCM_NONCE_SIZE_BYTES = 12
        private const val GCM_TAG_SIZE_BITS = 128

        private const val PBKDF2_ITERATIONS = 100_000
        private const val PBKDF2_KEY_LENGTH_BITS = 256
    }
}
