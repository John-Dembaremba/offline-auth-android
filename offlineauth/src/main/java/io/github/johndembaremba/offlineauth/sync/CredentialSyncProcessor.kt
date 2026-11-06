package io.github.johndembaremba.offlineauth.sync

import io.github.johndembaremba.offlineauth.model.CredentialSyncResult

/**
 * Processes synchronized offline credentials against an authoritative
 * set of usernames.
 */
fun interface CredentialSyncProcessor {
    suspend fun process(
        credentials: List<SynchronizedCredential>,
        snapshotUsernames: Set<String>,
    ): CredentialSyncResult
}
