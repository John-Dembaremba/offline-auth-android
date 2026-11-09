package io.github.johndembaremba.offlineauth

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.johndembaremba.offlineauth.crypto.KeyStoreManager
import io.github.johndembaremba.offlineauth.model.EncryptedEnvelope
import io.github.johndembaremba.offlineauth.model.OfflineCredentialSnapshot
import io.github.johndembaremba.offlineauth.model.SynchronizedCredentialPayload
import io.github.johndembaremba.offlineauth.sync.UnsupportedProtocolVersionException
import io.github.johndembaremba.offlineauth.testing.Pbkdf2TestHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
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
class OfflineAuthStorageSecurityTest {
    @Test
    fun privateKey_isNotExportableFromAndroidKeyStore() {
        val keyStoreManager =
            KeyStoreManager()

        val exportedPublicKey =
            Base64.decode(
                keyStoreManager.getPublicKey(),
                Base64.NO_WRAP,
            )

        val androidKeyStore =
            KeyStore
                .getInstance(
                    "AndroidKeyStore",
                ).apply {
                    load(null)
                }

        val matchingAlias =
            androidKeyStore
                .aliases()
                .toList()
                .firstOrNull { alias ->
                    val certificate =
                        androidKeyStore.getCertificate(alias)
                            ?: return@firstOrNull false

                    certificate.publicKey.encoded
                        .contentEquals(exportedPublicKey)
                }

        assertNotNull(
            "Offline-auth RSA key must exist in Android Keystore.",
            matchingAlias,
        )

        val privateKey =
            androidKeyStore.getKey(
                matchingAlias,
                null,
            ) as PrivateKey

        /*
         * Android Keystore-backed private keys must not expose their encoded
         * key material to the application.
         */
        assertNull(
            privateKey.encoded,
        )

        assertNull(
            privateKey.format,
        )
    }

    @Test
    fun synchronizedCredential_isNotPersistedAsPlaintext() {
        runBlocking {
            val context =
                ApplicationProvider.getApplicationContext<Context>()

            val manager =
                OfflineAuthManager.create(context)

            val publicKey =
                decodePublicKey(
                    manager.getPublicKey(),
                )

            val username =
                "plaintext-security-user"

            val password =
                "offline-auth-secret-password-928473"

            val passwordVerifier =
                createPasswordVerifier(
                    password = password,
                    salt = "plaintext-security-salt-928473",
                )

            val now =
                Instant.now()

            val credentialJson =
                createCredentialJson(
                    passwordVerifier = passwordVerifier,
                    issuedAt = now.minusSeconds(60),
                    expiresAt = now.plusSeconds(3600),
                )

            val envelope =
                encryptCredential(
                    publicKey = publicKey,
                    plaintext = credentialJson.encodeToByteArray(),
                )

            val result =
                manager.sync(
                    singleCredentialSnapshot(
                        username = username,
                        envelope = envelope,
                    ),
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

            /*
             * The plaintext password is never part of the persisted
             * credential payload.
             */
            assertFalse(
                appPrivateStorageContains(
                    context = context,
                    searchValue = password,
                ),
            )

            /*
             * More importantly, the actual password verifier contained in the
             * decrypted credential must also not appear in plaintext in app
             * private storage.
             */
            assertFalse(
                appPrivateStorageContains(
                    context = context,
                    searchValue = passwordVerifier,
                ),
            )

            /*
             * The complete decrypted credential JSON must not be stored
             * directly either.
             */
            assertFalse(
                appPrivateStorageContains(
                    context = context,
                    searchValue = credentialJson,
                ),
            )
        }
    }

    @Test
    fun unsupportedProtocolVersion_doesNotReplaceExistingCredential() {
        runBlocking {
            val context =
                ApplicationProvider.getApplicationContext<Context>()

            val manager =
                OfflineAuthManager.create(context)

            val publicKey =
                decodePublicKey(
                    manager.getPublicKey(),
                )

            val username =
                "protocol-security-user"

            val existingPassword =
                "existing-password"

            val replacementPassword =
                "replacement-password"

            val validSnapshot =
                createSnapshot(
                    username = username,
                    password = existingPassword,
                    credentialVersion = 1,
                    protocolVersion = 1,
                    publicKey = publicKey,
                )

            val validResult =
                manager.sync(
                    validSnapshot,
                )

            assertEquals(
                listOf(username),
                validResult.successfulUsernames,
            )

            assertTrue(
                manager.authenticate(
                    username = username,
                    password = existingPassword.toCharArray(),
                ),
            )

            val unsupportedSnapshot =
                createSnapshot(
                    username = username,
                    password = replacementPassword,
                    credentialVersion = 2,
                    protocolVersion = UNSUPPORTED_PROTOCOL_VERSION,
                    publicKey = publicKey,
                )

            try {
                manager.sync(
                    unsupportedSnapshot,
                )

                fail(
                    "Expected UnsupportedProtocolVersionException.",
                )
            } catch (exception: UnsupportedProtocolVersionException) {
                assertEquals(
                    UNSUPPORTED_PROTOCOL_VERSION,
                    exception.protocolVersion,
                )
            }

            /*
             * Rejected protocol versions must not mutate already stored
             * credentials.
             */
            assertTrue(
                manager.authenticate(
                    username = username,
                    password = existingPassword.toCharArray(),
                ),
            )

            assertFalse(
                manager.authenticate(
                    username = username,
                    password = replacementPassword.toCharArray(),
                ),
            )
        }
    }

    private fun createSnapshot(
        username: String,
        password: String,
        credentialVersion: Int,
        protocolVersion: Int,
        publicKey: PublicKey,
    ): OfflineCredentialSnapshot {
        val verifier =
            createPasswordVerifier(
                password = password,
                salt = "protocol-security-salt",
            )

        val now =
            Instant.now()

        val credentialJson =
            """
            {
              "password_algorithm": "pbkdf2_sha256",
              "password_verifier": "$verifier",
              "credential_version": $credentialVersion,
              "issued_at": "${now.minusSeconds(60)}",
              "expires_at": "${now.plusSeconds(3600)}"
            }
            """.trimIndent()

        val envelope =
            encryptCredential(
                publicKey = publicKey,
                plaintext = credentialJson.encodeToByteArray(),
            )

        return OfflineCredentialSnapshot(
            protocolVersion = protocolVersion,
            credentials =
                listOf(
                    SynchronizedCredentialPayload(
                        userId = "security-$username",
                        username = username,
                        envelope = envelope,
                    ),
                ),
            failures = emptyList(),
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
                        userId = "security-$username",
                        username = username,
                        envelope = envelope,
                    ),
                ),
            failures = emptyList(),
        )

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

    private fun createPasswordVerifier(
        password: String,
        salt: String,
    ): String =
        Pbkdf2TestHelper.createVerifier(
            password = password,
            salt = salt,
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

    private fun appPrivateStorageContains(
        context: Context,
        searchValue: String,
    ): Boolean {
        val dataDirectory =
            File(
                context.applicationInfo.dataDir,
            )

        val searchBytes =
            searchValue.toByteArray()

        return dataDirectory
            .walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.length() <= MAX_SCANNED_FILE_BYTES
            }.any { file ->
                try {
                    containsBytes(
                        source = file.readBytes(),
                        target = searchBytes,
                    )
                } catch (_: Exception) {
                    false
                }
            }
    }

    private fun containsBytes(
        source: ByteArray,
        target: ByteArray,
    ): Boolean {
        if (target.isEmpty()) {
            return true
        }

        if (target.size > source.size) {
            return false
        }

        for (startIndex in 0..source.size - target.size) {
            var matches =
                true

            for (targetIndex in target.indices) {
                if (
                    source[startIndex + targetIndex] !=
                    target[targetIndex]
                ) {
                    matches = false
                    break
                }
            }

            if (matches) {
                return true
            }
        }

        return false
    }

    companion object {
        private const val UNSUPPORTED_PROTOCOL_VERSION =
            999

        private const val AES_KEY_SIZE_BITS =
            256

        private const val GCM_NONCE_SIZE_BYTES =
            12

        private const val GCM_TAG_SIZE_BITS =
            128

        private const val PBKDF2_ITERATIONS =
            100_000

        private const val MAX_SCANNED_FILE_BYTES =
            5L * 1024L * 1024L
    }
}
