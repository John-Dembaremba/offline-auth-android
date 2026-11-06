package io.github.johndembaremba.offlineauth

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.johndembaremba.offlineauth.auth.OfflineAuthenticator
import io.github.johndembaremba.offlineauth.crypto.EnvelopeDecryptor
import io.github.johndembaremba.offlineauth.crypto.KeyStoreManager
import io.github.johndembaremba.offlineauth.model.EncryptedEnvelope
import io.github.johndembaremba.offlineauth.model.OfflineCredentialSnapshot
import io.github.johndembaremba.offlineauth.model.SynchronizedCredentialPayload
import io.github.johndembaremba.offlineauth.serialization.CredentialParser
import io.github.johndembaremba.offlineauth.storage.EncryptedCredentialStore
import io.github.johndembaremba.offlineauth.sync.DefaultCredentialEnvelopeProcessor
import io.github.johndembaremba.offlineauth.sync.OfflineCredentialSyncProcessor
import io.github.johndembaremba.offlineauth.sync.ParallelismStrategy
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
class OfflineAuthParallelismPerformanceTest {
    @Test
    fun compareParallelismLevels() {
        runBlocking {
            val context =
                ApplicationProvider.getApplicationContext<Context>()

            /*
             * Use one Keystore RSA key for every benchmark configuration.
             *
             * This ensures all generated envelopes can be decrypted by every
             * manager instance used during the comparison.
             */
            val keyStoreManager =
                KeyStoreManager()

            val publicKey =
                decodePublicKey(
                    keyStoreManager.getPublicKey(),
                )

            Log.i(
                TAG,
                "========== PARALLELISM PERFORMANCE TEST ==========",
            )

            Log.i(
                TAG,
                "Available processors: ${Runtime.getRuntime().availableProcessors()}",
            )

            Log.i(
                TAG,
                "Runs per workload: $REPEAT_COUNT",
            )

            for (userCount in USER_COUNTS) {
                /*
                 * Build the encrypted workload only once per user count.
                 *
                 * Snapshot generation is deliberately outside all measured
                 * sections.
                 */
                val snapshot =
                    createSnapshot(
                        publicKey = publicKey,
                        userCount = userCount,
                    )

                for (parallelism in PARALLELISM_LEVELS) {
                    val manager =
                        createManager(
                            context = context,
                            keyStoreManager = keyStoreManager,
                            parallelism = parallelism,
                        )

                    /*
                     * Warm up this particular execution configuration before
                     * collecting measurements.
                     */
                    manager.sync(
                        createSnapshot(
                            publicKey = publicKey,
                            userCount = 1,
                        ),
                    )

                    val durations =
                        mutableListOf<Double>()

                    repeat(REPEAT_COUNT) { runIndex ->
                        val start =
                            System.nanoTime()

                        val result =
                            manager.sync(snapshot)

                        val duration =
                            System.nanoTime() - start

                        val durationMs =
                            duration / NANOS_PER_MILLISECOND

                        durations += durationMs

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
                                append("run=")
                                append(runIndex + 1)
                                append("/")
                                append(REPEAT_COUNT)

                                append(" | users=")
                                append(userCount)

                                append(" | parallelism=")
                                append(parallelism)

                                append(" | total=")
                                append(format(durationMs))
                                append(" ms")

                                append(" | avg/user=")
                                append(
                                    format(
                                        durationMs / userCount.toDouble(),
                                    ),
                                )
                                append(" ms")
                            },
                        )
                    }

                    val statistics =
                        calculateStatistics(
                            durations = durations,
                            userCount = userCount,
                        )

                    Log.i(
                        TAG,
                        buildString {
                            append("SUMMARY")

                            append(" | users=")
                            append(userCount)

                            append(" | parallelism=")
                            append(parallelism)

                            append(" | runs=")
                            append(REPEAT_COUNT)

                            append(" | avg=")
                            append(format(statistics.averageMs))
                            append(" ms")

                            append(" | median=")
                            append(format(statistics.medianMs))
                            append(" ms")

                            append(" | min=")
                            append(format(statistics.minimumMs))
                            append(" ms")

                            append(" | max=")
                            append(format(statistics.maximumMs))
                            append(" ms")

                            append(" | avg/user=")
                            append(format(statistics.averagePerUserMs))
                            append(" ms")
                        },
                    )
                }
            }

            Log.i(
                TAG,
                "==================================================",
            )
        }
    }

    private fun createManager(
        context: Context,
        keyStoreManager: KeyStoreManager,
        parallelism: Int,
    ): OfflineAuthManager {
        val credentialStore =
            EncryptedCredentialStore(
                context = context,
            )

        val envelopeDecryptor =
            EnvelopeDecryptor(
                keyStoreManager = keyStoreManager,
            )

        val credentialParser =
            CredentialParser()

        val credentialProcessor =
            DefaultCredentialEnvelopeProcessor(
                envelopeDecryptor = envelopeDecryptor,
                credentialParser = credentialParser,
            )

        val parallelismStrategy =
            FixedParallelismStrategy(
                parallelism = parallelism,
            )

        val syncProcessor =
            OfflineCredentialSyncProcessor(
                credentialStore = credentialStore,
                envelopeProcessor = credentialProcessor,
                parallelismStrategy = parallelismStrategy,
            )

        return OfflineAuthManager(
            publicKeyProvider = keyStoreManager,
            credentialStore = credentialStore,
            authenticator = OfflineAuthenticator(),
            syncProcessor = syncProcessor,
        )
    }

    private fun calculateStatistics(
        durations: List<Double>,
        userCount: Int,
    ): PerformanceStatistics {
        require(durations.isNotEmpty())

        val sorted =
            durations.sorted()

        val average =
            durations.average()

        return PerformanceStatistics(
            averageMs = average,
            medianMs = calculateMedian(sorted),
            minimumMs = sorted.first(),
            maximumMs = sorted.last(),
            averagePerUserMs = average / userCount.toDouble(),
        )
    }

    private fun calculateMedian(sortedValues: List<Double>): Double {
        val middle =
            sortedValues.size / 2

        return if (sortedValues.size % 2 == 0) {
            (
                sortedValues[middle - 1] +
                    sortedValues[middle]
            ) / 2.0
        } else {
            sortedValues[middle]
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

        val passwordVerifier =
            createPasswordVerifier(
                TEST_PASSWORD,
            )

        val credentials =
            (0 until userCount).map { index ->
                val credentialJson =
                    createCredentialJson(
                        passwordVerifier = passwordVerifier,
                        issuedAt = issuedAt,
                        expiresAt = expiresAt,
                    )

                val envelope =
                    encryptCredential(
                        publicKey = publicKey,
                        plaintext = credentialJson.encodeToByteArray(),
                    )

                SynchronizedCredentialPayload(
                    userId = "parallel-user-$index",
                    username = "parallel-user-$index",
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
        issuedAt: Instant,
        expiresAt: Instant,
    ): String =
        """
        {
          "password_algorithm": "pbkdf2_sha256",
          "password_verifier": "$passwordVerifier",
          "credential_version": 1,
          "issued_at": "$issuedAt",
          "expires_at": "$expiresAt"
        }
        """.trimIndent()

    private fun createPasswordVerifier(password: String): String {
        val salt =
            "parallelism-test-salt"

        val specification =
            PBEKeySpec(
                password.toCharArray(),
                salt.toByteArray(),
                PBKDF2_ITERATIONS,
                PBKDF2_KEY_LENGTH_BITS,
            )

        return try {
            val factory =
                SecretKeyFactory.getInstance(
                    "PBKDF2WithHmacSHA256",
                )

            val encodedHash =
                Base64.encodeToString(
                    factory
                        .generateSecret(specification)
                        .encoded,
                    Base64.NO_WRAP,
                )

            "pbkdf2_sha256" +
                "${'$'}$PBKDF2_ITERATIONS" +
                "${'$'}$salt" +
                "${'$'}$encodedHash"
        } finally {
            specification.clearPassword()
        }
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

    private data class PerformanceStatistics(
        val averageMs: Double,
        val medianMs: Double,
        val minimumMs: Double,
        val maximumMs: Double,
        val averagePerUserMs: Double,
    )

    private class FixedParallelismStrategy(
        private val parallelism: Int,
    ) : ParallelismStrategy {
        override fun determine(credentialCount: Int): Int = parallelism
    }

    companion object {
        private const val TAG =
            "OfflineAuthParallel"

        private val USER_COUNTS =
            listOf(
                100,
                500,
                1000,
            )

        private val PARALLELISM_LEVELS =
            listOf(
                1,
                2,
                4,
            )

        private const val REPEAT_COUNT =
            5

        private const val TEST_PASSWORD =
            "parallelism-test-password"

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
