package io.github.johndembaremba.offlineauth

import android.util.Base64
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.johndembaremba.offlineauth.model.EncryptedEnvelope
import io.github.johndembaremba.offlineauth.model.OfflineCredentialSnapshot
import io.github.johndembaremba.offlineauth.model.SynchronizedCredentialPayload
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PSource

@RunWith(AndroidJUnit4::class)
class OfflineAuthPerformanceTest {
    @Test
    fun measureSnapshotSyncPerformance() {
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
             * Perform one warm-up sync before collecting measurements.
             *
             * This reduces distortion from class loading, JIT compilation,
             * Keystore initialization, and other first-run work.
             */
            val warmupSnapshot =
                createSnapshot(
                    publicKey = publicKey,
                    userCount = 1,
                )

            manager.sync(warmupSnapshot)

            Log.i(
                TAG,
                "========== OFFLINE AUTH PERFORMANCE TEST ==========",
            )

            Log.i(
                TAG,
                "Available processors: ${Runtime.getRuntime().availableProcessors()}",
            )

            for (userCount in USER_COUNTS) {
                /*
                 * Snapshot construction and backend-style encryption happen
                 * before the timer starts.
                 *
                 * We want to measure Android library sync performance only.
                 */
                val snapshot =
                    createSnapshot(
                        publicKey = publicKey,
                        userCount = userCount,
                    )

                val start =
                    System.nanoTime()

                val result =
                    manager.sync(snapshot)

                val duration =
                    System.nanoTime() - start

                val durationMs =
                    duration / NANOS_PER_MILLISECOND

                val averagePerUserMs =
                    durationMs / userCount.toDouble()

                assertEquals(
                    userCount,
                    result.successfulUsernames.size,
                )

                assertTrue(
                    result.failures.isEmpty(),
                )

                Log.i(
                    TAG,
                    buildString {
                        append("users=")
                        append(userCount)

                        append(" | total=")
                        append(format(durationMs))
                        append(" ms")

                        append(" | avg=")
                        append(format(averagePerUserMs))
                        append(" ms/user")

                        append(" | success=")
                        append(result.successfulUsernames.size)

                        append(" | failures=")
                        append(result.failures.size)
                    },
                )
            }

            Log.i(
                TAG,
                "===================================================",
            )
        }
    }

    private fun createSnapshot(
        publicKey: PublicKey,
        userCount: Int,
    ): OfflineCredentialSnapshot {
        val now =
            Instant.now()

        val issuedAt =
            now.minusSeconds(60)

        val expiresAt =
            now.plusSeconds(CREDENTIAL_TTL_SECONDS)

        /*
         * Generate the PBKDF2 verifier only once.
         *
         * Every test user may use the same verifier because password hashing
         * is not part of the sync benchmark.
         */
        val passwordVerifier =
            createPasswordVerifier(
                password = TEST_PASSWORD,
            )

        val credentials =
            (0 until userCount).map { index ->
                val credentialJson =
                    createCredentialJson(
                        passwordVerifier = passwordVerifier,
                        credentialVersion = 1,
                        issuedAt = issuedAt,
                        expiresAt = expiresAt,
                    )

                val envelope =
                    encryptCredential(
                        publicKey = publicKey,
                        plaintext = credentialJson.encodeToByteArray(),
                    )

                SynchronizedCredentialPayload(
                    userId = "performance-user-$index",
                    username = "performance-user-$index",
                    envelope = envelope,
                )
            }

        return OfflineCredentialSnapshot(
            protocolVersion = 1,
            credentials = credentials,
            failures = emptyList(),
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

    private fun createPasswordVerifier(password: String): String {
        val salt =
            "performance-test-salt"

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

    private fun format(value: Double): String =
        String.format(
            Locale.US,
            "%.3f",
            value,
        )

    companion object {
        private const val TAG =
            "OfflineAuthPerf"

        private val USER_COUNTS =
            listOf(
                1,
                10,
                50,
                100,
                500,
                1000,
            )

        private const val TEST_PASSWORD =
            "performance-test-password"

        private const val AES_KEY_SIZE_BITS =
            256

        private const val GCM_NONCE_SIZE_BYTES =
            12

        private const val GCM_TAG_SIZE_BITS =
            128

        private const val PBKDF2_ITERATIONS =
            100_000

        private const val PBKDF2_KEY_LENGTH_BITS =
            256

        private const val CREDENTIAL_TTL_SECONDS =
            3600L

        private const val NANOS_PER_MILLISECOND =
            1_000_000.0
    }
}
