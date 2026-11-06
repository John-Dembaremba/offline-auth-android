package io.github.johndembaremba.offlineauth

import android.os.Debug
import android.util.Base64
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.johndembaremba.offlineauth.model.EncryptedEnvelope
import io.github.johndembaremba.offlineauth.model.OfflineCredentialSnapshot
import io.github.johndembaremba.offlineauth.model.SynchronizedCredentialPayload
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
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PSource

@RunWith(AndroidJUnit4::class)
class OfflineAuthMemoryPerformanceTest {
    @Test
    fun measureSyncMemoryBehaviour() {
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
                "========== OFFLINE AUTH MEMORY TEST ==========",
            )

            for (userCount in USER_COUNTS) {
                forceGc()

                val before =
                    captureMemory()

                val snapshot =
                    createSnapshot(
                        publicKey = publicKey,
                        userCount = userCount,
                    )

                forceGc()

                val beforeSync =
                    captureMemory()

                val result =
                    manager.sync(snapshot)

                val afterSync =
                    captureMemory()

                assertEquals(
                    userCount,
                    result.successfulUsernames.size,
                )

                assertTrue(
                    result.failures.isEmpty(),
                )

                forceGc()

                val afterGc =
                    captureMemory()

                Log.i(
                    TAG,
                    buildString {
                        append("MEMORY")

                        append(" | users=")
                        append(userCount)

                        append(" | before=")
                        append(formatMb(before.totalPssKb))
                        append(" MB")

                        append(" | beforeSync=")
                        append(formatMb(beforeSync.totalPssKb))
                        append(" MB")

                        append(" | afterSync=")
                        append(formatMb(afterSync.totalPssKb))
                        append(" MB")

                        append(" | afterGc=")
                        append(formatMb(afterGc.totalPssKb))
                        append(" MB")

                        append(" | syncDelta=")
                        append(
                            formatMb(
                                afterSync.totalPssKb -
                                    beforeSync.totalPssKb,
                            ),
                        )
                        append(" MB")

                        append(" | retainedDelta=")
                        append(
                            formatMb(
                                afterGc.totalPssKb -
                                    beforeSync.totalPssKb,
                            ),
                        )
                        append(" MB")
                    },
                )
            }

            Log.i(
                TAG,
                "===============================================",
            )
        }
    }

    private suspend fun forceGc() {
        Runtime.getRuntime().gc()
        System.runFinalization()
        delay(GC_SETTLE_DELAY_MS)
    }

    private fun captureMemory(): MemorySnapshot {
        val memoryInfo =
            Debug.MemoryInfo()

        Debug.getMemoryInfo(
            memoryInfo,
        )

        return MemorySnapshot(
            totalPssKb = memoryInfo.totalPss,
        )
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
                    userId = "memory-user-$index",
                    username = "memory-user-$index",
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
            "memory-test-salt"

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

    private fun formatMb(kilobytes: Int): String =
        String.format(
            Locale.US,
            "%.2f",
            kilobytes / 1024.0,
        )

    private data class MemorySnapshot(
        val totalPssKb: Int,
    )

    companion object {
        private const val TAG =
            "OfflineAuthMemory"

        private val USER_COUNTS =
            listOf(
                100,
                500,
                1000,
            )

        private const val TEST_PASSWORD =
            "memory-test-password"

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

        private const val GC_SETTLE_DELAY_MS =
            500L
    }
}
