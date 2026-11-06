package io.github.johndembaremba.offlineauth.sync

import io.github.johndembaremba.offlineauth.crypto.CredentialDecryptor
import io.github.johndembaremba.offlineauth.model.EncryptedEnvelope
import io.github.johndembaremba.offlineauth.model.OfflineCredential
import io.github.johndembaremba.offlineauth.serialization.CredentialParser
import io.github.johndembaremba.offlineauth.validation.CredentialValidator
import io.github.johndembaremba.offlineauth.validation.EnvelopeValidator

/**
 * Decrypts and parses a synchronized offline credential envelope.
 */
class DefaultCredentialEnvelopeProcessor(
    private val envelopeDecryptor: CredentialDecryptor,
    private val credentialParser: CredentialParser,
    private val envelopeValidator: EnvelopeValidator =
        EnvelopeValidator(),
    private val credentialValidator: CredentialValidator =
        CredentialValidator(),
) : CredentialEnvelopeProcessor {
    override fun process(envelope: EncryptedEnvelope): OfflineCredential {
        envelopeValidator.validate(
            envelope = envelope,
        )

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

        val credential =
            try {
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

        if (!credentialValidator.isValid(credential)) {
            throw CredentialProcessingException(
                reason =
                    CredentialProcessingFailureReason
                        .CREDENTIAL_VALIDATION_FAILED,
            )
        }

        if (credential.passwordAlgorithm != SUPPORTED_PASSWORD_ALGORITHM) {
            throw CredentialProcessingException(
                reason =
                    CredentialProcessingFailureReason
                        .UNSUPPORTED_PASSWORD_ALGORITHM,
            )
        }

        return credential
    }

    companion object {
        private const val SUPPORTED_PASSWORD_ALGORITHM =
            "pbkdf2_sha256"
    }
}
