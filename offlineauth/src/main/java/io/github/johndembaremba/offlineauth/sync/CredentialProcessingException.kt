package io.github.johndembaremba.offlineauth.sync

import io.github.johndembaremba.offlineauth.model.LocalCredentialSyncFailureReason

/**
 * Represents a predictable failure while processing a synchronized credential.
 */
class CredentialProcessingException(
    val reason: LocalCredentialSyncFailureReason,
    cause: Throwable? = null,
) : RuntimeException(
        reason.code,
        cause,
    )
