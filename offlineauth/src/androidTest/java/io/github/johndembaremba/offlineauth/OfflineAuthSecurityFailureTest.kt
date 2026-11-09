package io.github.johndembaremba.offlineauth

import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.johndembaremba.offlineauth.model.CredentialSyncFailure
import io.github.johndembaremba.offlineauth.model.CredentialSyncFailureReason
import io.github.johndembaremba.offlineauth.model.EncryptedEnvelope
import io.github.johndembaremba.offlineauth.model.LocalCredentialSyncFailureReason
import io.github.johndembaremba.offlineauth.model.OfflineCredentialSnapshot
import io.github.johndembaremba.offlineauth.model.SynchronizedCredentialPayload
import io.github.johndembaremba.offlineauth.testing.Pbkdf2TestHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

@RunWith(AndroidJUnit4::class)
class OfflineAuthSecurityFailureTest {
    @Test
    fun malformedBase64_isReportedAsDecryptionFailure() {
        runBlocking {
            val manager =
                createManager()

            val envelope =
                EncryptedEnvelope(
                    keyAlgorithm = "RSA-OAEP-256",
                    contentAlgorithm = "A256GCM",
                    encryptedKey = "%%%invalid-base64%%%",
                    nonce = "%%%invalid-base64%%%",
                    ciphertext = "%%%invalid-base64%%%",
                )

            val result =
                manager.sync(
                    singleCredentialSnapshot(
                        username = "security-malformed-base64",
                        envelope = envelope,
                    ),
                )

            assertSingleLocalFailure(
                failures = result.failures,
                expectedReason =
                    LocalCredentialSyncFailureReason.DECRYPTION_FAILED,
            )

            assertTrue(
                result.successfulUsernames.isEmpty(),
            )
        }
    }

    @Test
    fun invalidNonce_isReportedAsDecryptionFailure() {
        runBlocking {
            val manager =
                createManager()

            val publicKey =
                decodePublicKey(
                    manager.getPublicKey(),
                )

            val credential =
                createValidCredentialJson()

            val envelope =
                encryptCredential(
                    publicKey = publicKey,
                    plaintext = credential.encodeToByteArray(),
                )

            /*
             * Replace the valid 12-byte GCM nonce with an invalid one.
             */
            val invalidNonce =
                Base64.encodeToString(
                    ByteArray(1) { 0x01 },
                    Base64.NO_WRAP,
                )

            val result =
                manager.sync(
                    singleCredentialSnapshot(
                        username = "security-invalid-nonce",
                        envelope =
                            envelope.copy(
                                nonce = invalidNonce,
                            ),
                    ),
                )

            assertSingleLocalFailure(
                failures = result.failures,
                expectedReason =
                    LocalCredentialSyncFailureReason.DECRYPTION_FAILED,
            )
        }
    }

    @Test
    fun wrongRsaWrappingKey_isReportedAsDecryptionFailure() {
        runBlocking {
            val manager =
                createManager()

            /*
             * Generate a completely unrelated RSA key pair.
             *
             * The Android Keystore private key owned by OfflineAuthManager
             * cannot unwrap an AES key encrypted with this public key.
             */
            val wrongKeyPair =
                KeyPairGenerator
                    .getInstance("RSA")
                    .apply {
                        initialize(RSA_KEY_SIZE_BITS)
                    }.generateKeyPair()

            val credential =
                createValidCredentialJson()

            val envelope =
                encryptCredential(
                    publicKey = wrongKeyPair.public,
                    plaintext = credential.encodeToByteArray(),
                )

            val result =
                manager.sync(
                    singleCredentialSnapshot(
                        username = "security-wrong-rsa-key",
                        envelope = envelope,
                    ),
                )

            assertSingleLocalFailure(
                failures = result.failures,
                expectedReason =
                    LocalCredentialSyncFailureReason.DECRYPTION_FAILED,
            )
        }
    }

    @Test
    fun corruptedCiphertext_isReportedAsDecryptionFailure() {
        runBlocking {
            val manager =
                createManager()

            val publicKey =
                decodePublicKey(
                    manager.getPublicKey(),
                )

            val envelope =
                encryptCredential(
                    publicKey = publicKey,
                    plaintext =
                        createValidCredentialJson()
                            .encodeToByteArray(),
                )

            val corrupted =
                corruptCiphertext(
                    envelope.ciphertext,
                )

            val result =
                manager.sync(
                    singleCredentialSnapshot(
                        username = "security-corrupted-ciphertext",
                        envelope =
                            envelope.copy(
                                ciphertext = corrupted,
                            ),
                    ),
                )

            assertSingleLocalFailure(
                failures = result.failures,
                expectedReason =
                    LocalCredentialSyncFailureReason.DECRYPTION_FAILED,
            )
        }
    }

    @Test
    fun invalidCredentialJson_isReportedAsInvalidCredentialPayload() {
        runBlocking {
            val manager =
                createManager()

            val publicKey =
                decodePublicKey(
                    manager.getPublicKey(),
                )

            /*
             * Encryption is valid, but decrypted content is not valid JSON.
             */
            val envelope =
                encryptCredential(
                    publicKey = publicKey,
                    plaintext =
                        "this-is-not-json"
                            .encodeToByteArray(),
                )

            val result =
                manager.sync(
                    singleCredentialSnapshot(
                        username = "security-invalid-json",
                        envelope = envelope,
                    ),
                )

            assertSingleLocalFailure(
                failures = result.failures,
                expectedReason =
                    LocalCredentialSyncFailureReason.INVALID_CREDENTIAL_PAYLOAD,
            )
        }
    }

    @Test
    fun expiredCredential_isReportedAsCredentialValidationFailure() {
        runBlocking {
            val manager =
                createManager()

            val publicKey =
                decodePublicKey(
                    manager.getPublicKey(),
                )

            val now =
                Instant.now()

            val expiredCredential =
                createCredentialJson(
                    passwordAlgorithm = "pbkdf2_sha256",
                    credentialVersion = 1,
                    issuedAt =
                        now.minusSeconds(
                            7200,
                        ),
                    expiresAt =
                        now.minusSeconds(
                            3600,
                        ),
                )

            val envelope =
                encryptCredential(
                    publicKey = publicKey,
                    plaintext =
                        expiredCredential
                            .encodeToByteArray(),
                )

            val result =
                manager.sync(
                    singleCredentialSnapshot(
                        username = "security-expired",
                        envelope = envelope,
                    ),
                )

            assertSingleLocalFailure(
                failures = result.failures,
                expectedReason =
                    LocalCredentialSyncFailureReason.CREDENTIAL_VALIDATION_FAILED,
            )

            assertFalse(
                manager.authenticate(
                    username = "security-expired",
                    password = TEST_PASSWORD.toCharArray(),
                ),
            )
        }
    }

    @Test
    fun unsupportedPasswordAlgorithm_isRejected() {
        runBlocking {
            val manager =
                createManager()

            val publicKey =
                decodePublicKey(
                    manager.getPublicKey(),
                )

            val now =
                Instant.now()

            val credential =
                createCredentialJson(
                    passwordAlgorithm = "argon2",
                    credentialVersion = 1,
                    issuedAt = now.minusSeconds(60),
                    expiresAt = now.plusSeconds(3600),
                )

            val envelope =
                encryptCredential(
                    publicKey = publicKey,
                    plaintext =
                        credential.encodeToByteArray(),
                )

            val result =
                manager.sync(
                    singleCredentialSnapshot(
                        username = "security-password-algorithm",
                        envelope = envelope,
                    ),
                )

            assertSingleLocalFailure(
                failures = result.failures,
                expectedReason =
                    LocalCredentialSyncFailureReason.UNSUPPORTED_PASSWORD_ALGORITHM,
            )
        }
    }

    @Test
    fun unsupportedKeyAlgorithm_isRejectedBeforeDecryption() {
        runBlocking {
            val manager =
                createManager()

            val publicKey =
                decodePublicKey(
                    manager.getPublicKey(),
                )

            val envelope =
                encryptCredential(
                    publicKey = publicKey,
                    plaintext =
                        createValidCredentialJson()
                            .encodeToByteArray(),
                )

            val result =
                manager.sync(
                    singleCredentialSnapshot(
                        username = "security-key-algorithm",
                        envelope =
                            envelope.copy(
                                keyAlgorithm = "RSA1_5",
                            ),
                    ),
                )

            assertSingleLocalFailure(
                failures = result.failures,
                expectedReason =
                    LocalCredentialSyncFailureReason.UNSUPPORTED_KEY_ALGORITHM,
            )
        }
    }

    @Test
    fun unsupportedContentAlgorithm_isRejectedBeforeDecryption() {
        runBlocking {
            val manager =
                createManager()

            val publicKey =
                decodePublicKey(
                    manager.getPublicKey(),
                )

            val envelope =
                encryptCredential(
                    publicKey = publicKey,
                    plaintext =
                        createValidCredentialJson()
                            .encodeToByteArray(),
                )

            val result =
                manager.sync(
                    singleCredentialSnapshot(
                        username = "security-content-algorithm",
                        envelope =
                            envelope.copy(
                                contentAlgorithm = "A128CBC",
                            ),
                    ),
                )

            assertSingleLocalFailure(
                failures = result.failures,
                expectedReason =
                    LocalCredentialSyncFailureReason.UNSUPPORTED_CONTENT_ALGORITHM,
            )
        }
    }

    private fun createManager(): OfflineAuthManager {
        val context =
            ApplicationProvider.getApplicationContext<android.content.Context>()

        return OfflineAuthManager.create(
            context,
        )
    }

    private fun assertSingleLocalFailure(
        failures: List<CredentialSyncFailure>,
        expectedReason: LocalCredentialSyncFailureReason,
    ) {
        assertEquals(
            1,
            failures.size,
        )

        val failure =
            failures.single()

        assertEquals(
            CredentialSyncFailureReason.Local(
                expectedReason,
            ),
            failure.reason,
        )
    }

    private fun singleCredentialSnapshot(
        username: String,
        envelope: EncryptedEnvelope,
    ): OfflineCredentialSnapshot =
        OfflineCredentialSnapshot(
            protocolVersion = 1,
            credentials =
                listOf(
                    SynchronizedCredentialPayload(
                        userId = "security-user-$username",
                        username = username,
                        envelope = envelope,
                    ),
                ),
            failures = emptyList(),
        )

    private fun createValidCredentialJson(): String {
        val now =
            Instant.now()

        return createCredentialJson(
            passwordAlgorithm = "pbkdf2_sha256",
            credentialVersion = 1,
            issuedAt = now.minusSeconds(60),
            expiresAt = now.plusSeconds(3600),
        )
    }

    private fun createCredentialJson(
        passwordAlgorithm: String,
        credentialVersion: Int,
        issuedAt: Instant,
        expiresAt: Instant,
    ): String {
        val passwordVerifier =
            createPasswordVerifier(
                TEST_PASSWORD,
            )

        return """
            {
              "password_algorithm": "$passwordAlgorithm",
              "password_verifier": "$passwordVerifier",
              "credential_version": $credentialVersion,
              "issued_at": "$issuedAt",
              "expires_at": "$expiresAt"
            }
            """.trimIndent()
    }

    private fun createPasswordVerifier(password: String): String =
        Pbkdf2TestHelper.createVerifier(
            password = password,
            salt = "security-test-salt",
            iterations = PBKDF2_ITERATIONS,
        )

    private fun decodePublicKey(encodedPublicKey: String): PublicKey {
        val decoded =
            Base64.decode(
                encodedPublicKey,
                Base64.NO_WRAP,
            )

        return KeyFactory
            .getInstance(
                "RSA",
            ).generatePublic(
                X509EncodedKeySpec(
                    decoded,
                ),
            )
    }

    private fun encryptCredential(
        publicKey: PublicKey,
        plaintext: ByteArray,
    ): EncryptedEnvelope {
        val aesKey =
            KeyGenerator
                .getInstance(
                    "AES",
                ).apply {
                    init(
                        AES_KEY_SIZE_BITS,
                    )
                }.generateKey()

        val nonce =
            ByteArray(
                GCM_NONCE_SIZE_BYTES,
            ).also {
                SecureRandom().nextBytes(
                    it,
                )
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

    private fun corruptCiphertext(ciphertext: String): String {
        val decoded =
            Base64.decode(
                ciphertext,
                Base64.NO_WRAP,
            )

        require(
            decoded.isNotEmpty(),
        )

        decoded[0] =
            (decoded[0].toInt() xor 0x01)
                .toByte()

        return Base64.encodeToString(
            decoded,
            Base64.NO_WRAP,
        )
    }

    companion object {
        private const val TEST_PASSWORD =
            "security-test-password"

        private const val AES_KEY_SIZE_BITS =
            256

        private const val RSA_KEY_SIZE_BITS =
            2048

        private const val GCM_NONCE_SIZE_BYTES =
            12

        private const val GCM_TAG_SIZE_BITS =
            128

        private const val PBKDF2_ITERATIONS =
            100_000
    }
}
