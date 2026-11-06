package io.github.johndembaremba.offlineauth.crypto

import io.github.johndembaremba.offlineauth.model.EncryptedEnvelope

/**
 * Decrypts an encrypted offline credential envelope.
 */
internal fun interface CredentialDecryptor {
    fun decrypt(envelope: EncryptedEnvelope): ByteArray
}
