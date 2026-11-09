package io.github.johndembaremba.offlineauth

import android.os.Debug
import android.util.Base64
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.johndembaremba.offlineauth.model.EncryptedEnvelope
import io.github.johndembaremba.offlineauth.model.OfflineCredentialSnapshot
import io.github.johndembaremba.offlineauth.model.SynchronizedCredentialPayload
import io.github.johndembaremba.offlineauth.testing.Pbkdf2TestHelper
import kotlinx.coroutines.delay
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
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

@RunWith(AndroidJUnit4::class)
class OfflineAuthStressTest {
    @Test
    fun repeatedSnapshotSync_remainsStable() {
        runBlocking {
            val context =
                ApplicationProvider.getApplicationContext<android.content.Context>()

            val manager =
                OfflineAuthManager.create(context)

            val publicKey =
                decodePublicKey(
                    manager.getPublicKey(),
                )

            Log.i(
                TAG,
                "========== OFFLINE AUTH STRESS TEST ==========",
            )

            for (scenario in SCENARIOS) {
                val snapshot =
                    createSnapshot(
                        publicKey = publicKey,
                        userCount = scenario.userCount,
                    )

                forceGc()

                val baselineMemory =
                    captureMemoryMb()

                var minimumDurationMs =
                    Double.MAX_VALUE

                var maximumDurationMs =
                    0.0

                var totalDurationMs =
                    0.0

                var maximumObservedMemoryMb =
                    baselineMemory

                repeat(scenario.cycles) { cycleIndex ->
                    val start =
                        System.nanoTime()

                    val result =
                        manager.sync(snapshot)

                    val durationMs =
                        (System.nanoTime() - start) /
                            NANOS_PER_MILLISECOND

                    assertEquals(
                        scenario.userCount,
                        result.successfulUsernames.size,
                    )

                    assertTrue(
                        result.failures.isEmpty(),
                    )

                    totalDurationMs +=
                        durationMs

                    minimumDurationMs =
                        minOf(
                            minimumDurationMs,
                            durationMs,
                        )

                    maximumDurationMs =
                        maxOf(
                            maximumDurationMs,
                            durationMs,
                        )

                    if (
                        cycleIndex == 0 ||
                        (cycleIndex + 1) % MEMORY_SAMPLE_INTERVAL == 0 ||
                        cycleIndex == scenario.cycles - 1
                    ) {
                        val currentMemoryMb =
                            captureMemoryMb()

                        maximumObservedMemoryMb =
                            maxOf(
                                maximumObservedMemoryMb,
                                currentMemoryMb,
                            )

                        Log.i(
                            TAG,
                            buildString {
                                append("PROGRESS")

                                append(" | users=")
                                append(scenario.userCount)

                                append(" | cycle=")
                                append(cycleIndex + 1)
                                append("/")
                                append(scenario.cycles)

                                append(" | duration=")
                                append(format(durationMs))
                                append(" ms")

                                append(" | memory=")
                                append(format(currentMemoryMb))
                                append(" MB")
                            },
                        )
                    }
                }

                forceGc()

                val finalMemory =
                    captureMemoryMb()

                val averageDurationMs =
                    totalDurationMs /
                        scenario.cycles.toDouble()

                Log.i(
                    TAG,
                    buildString {
                        append("SUMMARY")

                        append(" | users=")
                        append(scenario.userCount)

                        append(" | cycles=")
                        append(scenario.cycles)

                        append(" | avg=")
                        append(format(averageDurationMs))
                        append(" ms")

                        append(" | min=")
                        append(format(minimumDurationMs))
                        append(" ms")

                        append(" | max=")
                        append(format(maximumDurationMs))
                        append(" ms")

                        append(" | baselineMemory=")
                        append(format(baselineMemory))
                        append(" MB")

                        append(" | peakMemory=")
                        append(format(maximumObservedMemoryMb))
                        append(" MB")

                        append(" | finalMemory=")
                        append(format(finalMemory))
                        append(" MB")

                        append(" | retainedDelta=")
                        append(
                            format(
                                finalMemory -
                                    baselineMemory,
                            ),
                        )
                        append(" MB")
                    },
                )
            }

            Log.i(
                TAG,
                "==============================================",
            )
        }
    }

    private suspend fun forceGc() {
        Runtime.getRuntime().gc()
        System.runFinalization()
        delay(GC_SETTLE_DELAY_MS)
    }

    private fun captureMemoryMb(): Double {
        val memoryInfo =
            Debug.MemoryInfo()

        Debug.getMemoryInfo(
            memoryInfo,
        )

        return memoryInfo.totalPss /
            KILOBYTES_PER_MEGABYTE
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
                password = TEST_PASSWORD,
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
                    userId = "stress-user-$index",
                    username = "stress-user-$index",
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

    private fun createPasswordVerifier(password: String): String =
        Pbkdf2TestHelper.createVerifier(
            password = password,
            salt = "stress-test-salt",
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

    private fun format(value: Double): String =
        String.format(
            Locale.US,
            "%.3f",
            value,
        )

    private data class StressScenario(
        val userCount: Int,
        val cycles: Int,
    )

    companion object {
        private const val TAG =
            "OfflineAuthStress"

        private val SCENARIOS =
            listOf(
                StressScenario(
                    userCount = 100,
                    cycles = 100,
                ),
                StressScenario(
                    userCount = 500,
                    cycles = 50,
                ),
                StressScenario(
                    userCount = 1000,
                    cycles = 25,
                ),
            )

        private const val MEMORY_SAMPLE_INTERVAL =
            5

        private const val TEST_PASSWORD =
            "stress-test-password"

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

        private const val GC_SETTLE_DELAY_MS =
            500L

        private const val NANOS_PER_MILLISECOND =
            1_000_000.0

        private const val KILOBYTES_PER_MEGABYTE =
            1024.0
    }
}
