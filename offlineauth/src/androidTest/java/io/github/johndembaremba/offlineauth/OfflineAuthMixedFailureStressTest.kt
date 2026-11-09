package io.github.johndembaremba.offlineauth

import android.util.Base64
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.johndembaremba.offlineauth.model.CredentialFailurePayload
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
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

@RunWith(AndroidJUnit4::class)
class OfflineAuthMixedFailureStressTest {
    @Test
    fun mixedFailureSnapshot_remainsCorrectAndStable() {
        runBlocking {
            val context =
                ApplicationProvider.getApplicationContext<android.content.Context>()

            val manager =
                OfflineAuthManager.create(context)

            val publicKey =
                decodePublicKey(
                    manager.getPublicKey(),
                )

            /*
             * Seed all users that must already exist before the mixed snapshot.
             *
             * This MUST be one authoritative snapshot.
             *
             * If rollback users and backend-failure users were seeded in
             * separate snapshots, the second authoritative sync would remove
             * the users that were absent from it.
             */
            val seedSnapshot =
                OfflineCredentialSnapshot(
                    protocolVersion = 1,
                    credentials =
                        createCredentials(
                            publicKey = publicKey,
                            startIndex = BACKEND_FAILURE_START_INDEX,
                            count = BACKEND_FAILURE_COUNT,
                            credentialVersion = 1,
                        ) +
                            createCredentials(
                                publicKey = publicKey,
                                startIndex = ROLLBACK_START_INDEX,
                                count = ROLLBACK_COUNT,
                                credentialVersion = 2,
                            ),
                    failures = emptyList(),
                )

            val seedResult =
                manager.sync(
                    seedSnapshot,
                )

            assertEquals(
                BACKEND_FAILURE_COUNT + ROLLBACK_COUNT,
                seedResult.successfulUsernames.size,
            )

            assertTrue(
                seedResult.failures.isEmpty(),
            )

            /*
             * Verify seeded credentials exist before running the mixed sync.
             */
            assertTrue(
                manager.authenticate(
                    username = usernameForIndex(BACKEND_FAILURE_START_INDEX),
                    password = TEST_PASSWORD.toCharArray(),
                ),
            )

            assertTrue(
                manager.authenticate(
                    username = usernameForIndex(ROLLBACK_START_INDEX),
                    password = TEST_PASSWORD.toCharArray(),
                ),
            )

            val mixedSnapshot =
                createMixedSnapshot(
                    publicKey = publicKey,
                )

            Log.i(
                TAG,
                "========== MIXED FAILURE STRESS TEST ==========",
            )

            val start =
                System.nanoTime()

            val result =
                manager.sync(
                    mixedSnapshot,
                )

            val durationMs =
                (System.nanoTime() - start) /
                    NANOS_PER_MILLISECOND

            val backendFailures =
                result.failures.filter {
                    it.reason is CredentialSyncFailureReason.Backend
                }

            val decryptionFailures =
                result.failures.filter {
                    it.reason ==
                        CredentialSyncFailureReason.Local(
                            LocalCredentialSyncFailureReason.DECRYPTION_FAILED,
                        )
                }

            val rollbackFailures =
                result.failures.filter {
                    it.reason ==
                        CredentialSyncFailureReason.Local(
                            LocalCredentialSyncFailureReason.CREDENTIAL_VERSION_ROLLBACK,
                        )
                }

            /*
             * Expected mixed snapshot:
             *
             * 900 successful credentials
             *  50 backend failures
             *  30 corrupted encrypted credentials
             *  20 rollback attempts
             *
             * Total = 1000 authoritative users.
             */
            assertEquals(
                SUCCESS_COUNT,
                result.successfulUsernames.size,
            )

            assertEquals(
                BACKEND_FAILURE_COUNT,
                backendFailures.size,
            )

            assertEquals(
                CORRUPTED_COUNT,
                decryptionFailures.size,
            )

            assertEquals(
                ROLLBACK_COUNT,
                rollbackFailures.size,
            )

            assertEquals(
                TOTAL_FAILURE_COUNT,
                result.failures.size,
            )

            /*
             * Verify normal successful users were stored correctly.
             */
            assertTrue(
                manager.authenticate(
                    username = usernameForIndex(0),
                    password = TEST_PASSWORD.toCharArray(),
                ),
            )

            assertTrue(
                manager.authenticate(
                    username = usernameForIndex(SUCCESS_COUNT - 1),
                    password = TEST_PASSWORD.toCharArray(),
                ),
            )

            /*
             * Wrong passwords must still fail.
             */
            assertFalse(
                manager.authenticate(
                    username = usernameForIndex(0),
                    password = "wrong-password".toCharArray(),
                ),
            )

            /*
             * Backend failure users were present in the authoritative
             * snapshot failure list.
             *
             * Their previously stored credentials must therefore remain.
             */
            assertTrue(
                manager.authenticate(
                    username = usernameForIndex(BACKEND_FAILURE_START_INDEX),
                    password = TEST_PASSWORD.toCharArray(),
                ),
            )

            /*
             * Corrupted users had no previously stored credential.
             *
             * Their incoming encrypted payload failed decryption, so no
             * credential should have been stored.
             */
            assertFalse(
                manager.authenticate(
                    username = usernameForIndex(CORRUPTED_START_INDEX),
                    password = TEST_PASSWORD.toCharArray(),
                ),
            )

            /*
             * Rollback users already had credential version 2.
             *
             * The mixed snapshot attempts to replace them with version 1.
             * That replacement must be rejected and version 2 preserved.
             */
            assertTrue(
                manager.authenticate(
                    username = usernameForIndex(ROLLBACK_START_INDEX),
                    password = TEST_PASSWORD.toCharArray(),
                ),
            )

            val firstRollback =
                rollbackFailures.first()

            assertEquals(
                2,
                firstRollback.storedCredentialVersion,
            )

            assertEquals(
                1,
                firstRollback.incomingCredentialVersion,
            )

            /*
             * Every rollback failure should expose the expected versions.
             */
            rollbackFailures.forEach { failure ->
                assertEquals(
                    2,
                    failure.storedCredentialVersion,
                )

                assertEquals(
                    1,
                    failure.incomingCredentialVersion,
                )
            }

            Log.i(
                TAG,
                buildString {
                    append("SUMMARY")

                    append(" | totalUsers=")
                    append(TOTAL_USER_COUNT)

                    append(" | success=")
                    append(result.successfulUsernames.size)

                    append(" | backendFailures=")
                    append(backendFailures.size)

                    append(" | decryptionFailures=")
                    append(decryptionFailures.size)

                    append(" | rollbackFailures=")
                    append(rollbackFailures.size)

                    append(" | totalFailures=")
                    append(result.failures.size)

                    append(" | duration=")
                    append(format(durationMs))
                    append(" ms")
                },
            )

            Log.i(
                TAG,
                "===============================================",
            )
        }
    }

    private fun createMixedSnapshot(publicKey: PublicKey): OfflineCredentialSnapshot {
        val successfulCredentials =
            createCredentials(
                publicKey = publicKey,
                startIndex = 0,
                count = SUCCESS_COUNT,
                credentialVersion = 1,
            )

        val corruptedCredentials =
            createCredentials(
                publicKey = publicKey,
                startIndex = CORRUPTED_START_INDEX,
                count = CORRUPTED_COUNT,
                credentialVersion = 1,
            ).map { payload ->
                payload.copy(
                    envelope =
                        payload.envelope.copy(
                            ciphertext =
                                corruptBase64Ciphertext(
                                    payload.envelope.ciphertext,
                                ),
                        ),
                )
            }

        /*
         * These users already have version 2 from the seed snapshot.
         *
         * Sending version 1 here must trigger rollback protection.
         */
        val rollbackCredentials =
            createCredentials(
                publicKey = publicKey,
                startIndex = ROLLBACK_START_INDEX,
                count = ROLLBACK_COUNT,
                credentialVersion = 1,
            )

        /*
         * Backend failures are authoritative members of the snapshot.
         *
         * The local store should preserve any existing credential for these
         * users rather than deleting it.
         */
        val backendFailures =
            (0 until BACKEND_FAILURE_COUNT).map { offset ->
                val index =
                    BACKEND_FAILURE_START_INDEX + offset

                CredentialFailurePayload(
                    userId = "mixed-user-id-$index",
                    username = usernameForIndex(index),
                    reason = "backend_failure",
                )
            }

        return OfflineCredentialSnapshot(
            protocolVersion = 1,
            credentials =
                successfulCredentials +
                    corruptedCredentials +
                    rollbackCredentials,
            failures = backendFailures,
        )
    }

    private fun createCredentials(
        publicKey: PublicKey,
        startIndex: Int,
        count: Int,
        credentialVersion: Int,
    ): List<SynchronizedCredentialPayload> {
        val now =
            Instant.now()

        val issuedAt =
            now.minusSeconds(60)

        val expiresAt =
            now.plusSeconds(CREDENTIAL_TTL_SECONDS)

        val verifier =
            createPasswordVerifier(
                password = TEST_PASSWORD,
            )

        return (0 until count).map { offset ->
            val index =
                startIndex + offset

            val credentialJson =
                createCredentialJson(
                    passwordVerifier = verifier,
                    credentialVersion = credentialVersion,
                    issuedAt = issuedAt,
                    expiresAt = expiresAt,
                )

            val envelope =
                encryptCredential(
                    publicKey = publicKey,
                    plaintext = credentialJson.encodeToByteArray(),
                )

            SynchronizedCredentialPayload(
                userId = "mixed-user-id-$index",
                username = usernameForIndex(index),
                envelope = envelope,
            )
        }
    }

    private fun corruptBase64Ciphertext(ciphertext: String): String {
        val decoded =
            Base64.decode(
                ciphertext,
                Base64.NO_WRAP,
            )

        require(decoded.isNotEmpty()) {
            "Ciphertext must not be empty."
        }

        /*
         * Flip one bit while keeping the payload valid Base64.
         *
         * AES-GCM authentication should reject the modified ciphertext.
         */
        decoded[0] =
            (decoded[0].toInt() xor 0x01)
                .toByte()

        return Base64.encodeToString(
            decoded,
            Base64.NO_WRAP,
        )
    }

    private fun createCredentialJson(
        passwordVerifier: String,
        credentialVersion: Int,
        issuedAt: Instant,
        expiresAt: Instant,
    ): String =
        """
        {
          "password_algorithm": "pbkdf2_sha256",
          "password_verifier": "$passwordVerifier",
          "credential_version": $credentialVersion,
          "issued_at": "$issuedAt",
          "expires_at": "$expiresAt"
        }
        """.trimIndent()

    private fun createPasswordVerifier(password: String): String =
        Pbkdf2TestHelper.createVerifier(
            password = password,
            salt = "mixed-failure-test-salt",
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

    private fun usernameForIndex(index: Int): String = "mixed-user-$index"

    private fun format(value: Double): String =
        String.format(
            Locale.US,
            "%.3f",
            value,
        )

    companion object {
        private const val TAG =
            "OfflineAuthMixed"

        /*
         * Snapshot distribution:
         *
         * 0..899     -> successful credentials
         * 900..949   -> backend failures
         * 950..979   -> corrupted credentials
         * 980..999   -> rollback attempts
         */
        private const val TOTAL_USER_COUNT =
            1000

        private const val SUCCESS_COUNT =
            900

        private const val BACKEND_FAILURE_COUNT =
            50

        private const val CORRUPTED_COUNT =
            30

        private const val ROLLBACK_COUNT =
            20

        private const val TOTAL_FAILURE_COUNT =
            BACKEND_FAILURE_COUNT +
                CORRUPTED_COUNT +
                ROLLBACK_COUNT

        private const val BACKEND_FAILURE_START_INDEX =
            SUCCESS_COUNT

        private const val CORRUPTED_START_INDEX =
            BACKEND_FAILURE_START_INDEX +
                BACKEND_FAILURE_COUNT

        private const val ROLLBACK_START_INDEX =
            CORRUPTED_START_INDEX +
                CORRUPTED_COUNT

        private const val TEST_PASSWORD =
            "mixed-failure-test-password"

        private const val AES_KEY_SIZE_BITS =
            256

        private const val GCM_NONCE_SIZE_BYTES =
            12

        private const val GCM_TAG_SIZE_BITS =
            128

        private const val PBKDF2_ITERATIONS =
            100_000

        private const val CREDENTIAL_TTL_SECONDS =
            3600L

        private const val NANOS_PER_MILLISECOND =
            1_000_000.0
    }
}
