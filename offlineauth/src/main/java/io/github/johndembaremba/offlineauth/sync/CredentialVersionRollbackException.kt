package io.github.johndembaremba.offlineauth.sync

/**
 * Represents an attempt to replace a newer offline credential
 * with an older credential generation.
 */
internal class CredentialVersionRollbackException(
    val username: String,
    val storedCredentialVersion: Int,
    val incomingCredentialVersion: Int,
) : IllegalStateException(
        "Credential version rollback detected for $username",
    )
