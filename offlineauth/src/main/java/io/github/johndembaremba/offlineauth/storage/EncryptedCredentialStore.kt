package io.github.johndembaremba.offlineauth.storage

import android.content.Context
import android.util.Base64
import io.github.johndembaremba.offlineauth.model.OfflineCredential
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores offline credentials encrypted with AES-256-GCM.
 *
 * The AES key remains inside Android Keystore. Only the encrypted
 * credential and its nonce are persisted in private SharedPreferences.
 */
internal class EncryptedCredentialStore private constructor(
    context: Context,
    private val storageKeyManager: StorageKeyManager,
    private val json: Json,
) : CredentialStore {
    constructor(
        context: Context,
    ) : this(
        context = context,
        storageKeyManager = StorageKeyManager(),
        json =
            Json {
                ignoreUnknownKeys = true
            },
    )

    internal constructor(
        context: Context,
        storageKeyManager: StorageKeyManager,
    ) : this(
        context = context,
        storageKeyManager = storageKeyManager,
        json =
            Json {
                ignoreUnknownKeys = true
            },
    )

    private val preferences =
        context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    override fun save(
        username: String,
        credential: OfflineCredential,
    ) {
        val plaintext =
            json
                .encodeToString(credential)
                .toByteArray(Charsets.UTF_8)

        val cipher =
            Cipher.getInstance(
                AES_GCM_TRANSFORMATION,
            )

        cipher.init(
            Cipher.ENCRYPT_MODE,
            storageKeyManager.getOrCreateKey(),
        )

        val ciphertext =
            cipher.doFinal(
                plaintext,
            )

        val storedCredential =
            StoredCredential(
                nonce =
                    Base64.encodeToString(
                        cipher.iv,
                        Base64.NO_WRAP,
                    ),
                ciphertext =
                    Base64.encodeToString(
                        ciphertext,
                        Base64.NO_WRAP,
                    ),
            )

        preferences
            .edit()
            .putString(
                storageKey(username),
                json.encodeToString(storedCredential),
            ).apply()
    }

    override fun get(username: String): OfflineCredential? {
        val storedValue =
            preferences.getString(
                storageKey(username),
                null,
            ) ?: return null

        val storedCredential =
            runCatching {
                json.decodeFromString<StoredCredential>(
                    storedValue,
                )
            }.getOrNull()
                ?: return null

        val plaintext =
            decrypt(
                storedCredential = storedCredential,
            ) ?: return null

        return runCatching {
            json.decodeFromString<OfflineCredential>(
                plaintext.decodeToString(),
            )
        }.getOrNull()
    }

    override fun remove(username: String) {
        preferences
            .edit()
            .remove(
                storageKey(username),
            ).apply()
    }

    override fun clear() {
        preferences
            .edit()
            .clear()
            .apply()
    }

    override fun replaceAll(credentials: Map<String, OfflineCredential>) {
        clear()

        credentials.forEach { (username, credential) ->
            save(
                username = username,
                credential = credential,
            )
        }
    }

    private fun decrypt(storedCredential: StoredCredential): ByteArray? =
        runCatching {
            val cipher =
                Cipher.getInstance(
                    AES_GCM_TRANSFORMATION,
                )

            val nonce =
                Base64.decode(
                    storedCredential.nonce,
                    Base64.NO_WRAP,
                )

            cipher.init(
                Cipher.DECRYPT_MODE,
                storageKeyManager.getOrCreateKey(),
                GCMParameterSpec(
                    GCM_TAG_LENGTH_BITS,
                    nonce,
                ),
            )

            cipher.doFinal(
                Base64.decode(
                    storedCredential.ciphertext,
                    Base64.NO_WRAP,
                ),
            )
        }.getOrNull()

    private fun storageKey(username: String): String = "$CREDENTIAL_PREFIX$username"

    @Serializable
    private data class StoredCredential(
        val nonce: String,
        val ciphertext: String,
    )

    override fun applySnapshot(
        successfulCredentials: Map<String, OfflineCredential>,
        snapshotUsernames: Set<String>,
    ) {
        val existingUsernames =
            preferences.all.keys
                .filter { key ->
                    key.startsWith(CREDENTIAL_PREFIX)
                }.map { key ->
                    key.removePrefix(CREDENTIAL_PREFIX)
                }.toSet()

        val usernamesToRemove =
            existingUsernames - snapshotUsernames

        usernamesToRemove.forEach { username ->
            remove(
                username = username,
            )
        }

        successfulCredentials.forEach { (username, credential) ->
            save(
                username = username,
                credential = credential,
            )
        }
    }

    companion object {
        private const val PREFERENCES_NAME =
            "offline_auth_credentials"

        private const val CREDENTIAL_PREFIX =
            "credential:"

        private const val AES_GCM_TRANSFORMATION =
            "AES/GCM/NoPadding"

        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
