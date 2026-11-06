package io.github.johndembaremba.offlineauth.sync

import io.github.johndembaremba.offlineauth.crypto.CredentialDecryptor
import io.github.johndembaremba.offlineauth.model.EncryptedEnvelope
import io.github.johndembaremba.offlineauth.model.OfflineCredential
import io.github.johndembaremba.offlineauth.serialization.CredentialParser

/**
 * Decrypts and parses a synchronized offline credential envelope.
 */
class DefaultCredentialEnvelopeProcessor(
    private val envelopeDecryptor: CredentialDecryptor,
    private val credentialParser: CredentialParser,
) : CredentialEnvelopeProcessor {
    override fun process(envelope: EncryptedEnvelope): OfflineCredential {
        val decryptedPayload =
            try {
                envelopeDecryptor.decrypt(
                    envelope = envelope,
                )
            } catch (exception: Exception) {
                throw CredentialProcessingException(
                    reason =
                        CredentialProcessingFailureReason
                            .DECRYPTION_FAILED,
                    cause = exception,
                )
            }

        return try {
            credentialParser.parse(
                payload = decryptedPayload,
            )
        } catch (exception: Exception) {
            throw CredentialProcessingException(
                reason =
                    CredentialProcessingFailureReason
                        .INVALID_CREDENTIAL_PAYLOAD,
                cause = exception,
            )
        }
    }
}
