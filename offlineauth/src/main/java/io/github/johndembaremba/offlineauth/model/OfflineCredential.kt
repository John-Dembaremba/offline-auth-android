package io.github.johndembaremba.offlineauth.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a decrypted credential used for offline authentication.
 */
@Serializable
data class OfflineCredential(
    @SerialName("password_algorithm")
    val passwordAlgorithm: String,
    @SerialName("password_verifier")
    val passwordVerifier: String,
    @SerialName("credential_version")
    val credentialVersion: Int,
    @SerialName("issued_at")
    val issuedAt: String,
    @SerialName("expires_at")
    val expiresAt: String,
)
