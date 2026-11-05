package io.github.johndembaremba.offlineauth.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Encrypted credential envelope received from a backend.
 */
@Serializable
data class EncryptedEnvelope(
    @SerialName("key_algorithm")
    val keyAlgorithm: String,
    @SerialName("content_algorithm")
    val contentAlgorithm: String,
    @SerialName("encrypted_key")
    val encryptedKey: String,
    val nonce: String,
    val ciphertext: String,
)
