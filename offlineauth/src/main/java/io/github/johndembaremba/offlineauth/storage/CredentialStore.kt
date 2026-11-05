package io.github.johndembaremba.offlineauth.storage

import io.github.johndembaremba.offlineauth.model.OfflineCredential

/**
 * Stores and retrieves synchronized offline credentials.
 */
interface CredentialStore {
    fun save(
        username: String,
        credential: OfflineCredential,
    )

    fun get(username: String): OfflineCredential?

    fun remove(username: String)

    fun replaceAll(credentials: Map<String, OfflineCredential>)

    fun clear()
}
