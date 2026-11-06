package io.github.johndembaremba.offlineauth.auth

import io.github.johndembaremba.offlineauth.model.OfflineCredential
import io.github.johndembaremba.offlineauth.validation.CredentialValidator
import io.github.johndembaremba.offlineauth.verification.PasswordVerifier
import io.github.johndembaremba.offlineauth.verification.Pbkdf2Sha256PasswordVerifier

/**
 * Authenticates a user against a previously synchronized offline credential.
 *
 * The credential is validated first, then the password is verified using
 * the algorithm declared by the credential.
 */
internal class OfflineAuthenticator(
    private val credentialValidator: CredentialValidator =
        CredentialValidator(),
    private val passwordVerifiers: Map<String, PasswordVerifier> =
        defaultPasswordVerifiers(),
) {
    fun authenticate(
        password: CharArray,
        credential: OfflineCredential,
    ): Boolean {
        if (!credentialValidator.isValid(credential)) {
            return false
        }

        val verifier =
            passwordVerifiers[credential.passwordAlgorithm]
                ?: return false

        return verifier.verify(
            password = password,
            verifier = credential.passwordVerifier,
        )
    }

    companion object {
        private const val PBKDF2_SHA256 = "pbkdf2_sha256"

        private fun defaultPasswordVerifiers(): Map<String, PasswordVerifier> =
            mapOf(
                PBKDF2_SHA256 to Pbkdf2Sha256PasswordVerifier(),
            )
    }
}
