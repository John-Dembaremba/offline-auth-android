package io.github.johndembaremba.offlineauth.model

/**
 * Encrypted credential envelope received from a backend.
 */
data class EncryptedEnvelope(
    val keyAlgorithm: String,
    val contentAlgorithm: String,
    val encryptedKey: String,
    val nonce: String,
    val ciphertext: String,
)
