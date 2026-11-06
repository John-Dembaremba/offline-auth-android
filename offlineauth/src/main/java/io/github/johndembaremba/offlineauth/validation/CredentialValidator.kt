package io.github.johndembaremba.offlineauth.validation

import io.github.johndembaremba.offlineauth.model.OfflineCredential
import java.time.Clock
import java.time.Instant

/**
 * Validates whether an offline credential is still usable.
 */
internal class CredentialValidator(
    private val clock: Clock = Clock.systemUTC(),
) {
    fun isValid(credential: OfflineCredential): Boolean {
        if (credential.credentialVersion <= 0) {
            return false
        }

        val issuedAt =
            runCatching {
                Instant.parse(credential.issuedAt)
            }.getOrNull()
                ?: return false

        val expiresAt =
            runCatching {
                Instant.parse(credential.expiresAt)
            }.getOrNull()
                ?: return false

        if (!expiresAt.isAfter(issuedAt)) {
            return false
        }

        return Instant.now(clock).isBefore(expiresAt)
    }
}
