package io.github.johndembaremba.offlineauth.model

/**
 * Result of synchronizing offline credentials.
 */
data class CredentialSyncResult(
    val successfulUsernames: List<String>,
    val failures: List<CredentialSyncFailure>,
)

/**
 * Describes a credential that could not be synchronized.
 */
data class CredentialSyncFailure(
    val username: String,
    val reason: CredentialSyncFailureReason,
    val storedCredentialVersion: Int? = null,
    val incomingCredentialVersion: Int? = null,
)
