package io.github.johndembaremba.offlineauth.sync

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
