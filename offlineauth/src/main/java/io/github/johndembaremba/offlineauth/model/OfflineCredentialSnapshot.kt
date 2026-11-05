package io.github.johndembaremba.offlineauth.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Authoritative offline credential snapshot returned by a backend.
 */
@Serializable
data class OfflineCredentialSnapshot(
    @SerialName("protocol_version")
    val protocolVersion: Int,
    val credentials: List<SynchronizedCredentialPayload>,
    val failures: List<CredentialFailurePayload>,
)

@Serializable
data class SynchronizedCredentialPayload(
    @SerialName("user_id")
    val userId: String,
    val username: String,
    val envelope: EncryptedEnvelope,
)

@Serializable
data class CredentialFailurePayload(
    @SerialName("user_id")
    val userId: String,
    val username: String,
    val reason: String,
)
