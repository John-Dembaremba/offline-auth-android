package io.github.johndembaremba.offlineauth.sync

/**
 * Processes synchronized offline credentials.
 */
fun interface CredentialSyncProcessor {
    suspend fun process(credentials: List<SynchronizedCredential>): CredentialSyncResult
}
