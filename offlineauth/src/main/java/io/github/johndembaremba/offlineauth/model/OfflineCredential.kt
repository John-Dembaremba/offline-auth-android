package io.github.johndembaremba.offlineauth.model

/**
 * Represents a decrypted credential that can be used for offline authentication.
 *
 * The credential contains the password verification data and the limits that
 * determine how long the credential remains valid on the device.
 */
data class OfflineCredential(
    val passwordAlgorithm: String,
    val passwordVerifier: String,
    val credentialVersion: Int,
    val issuedAt: String,
    val expiresAt: String,
)
