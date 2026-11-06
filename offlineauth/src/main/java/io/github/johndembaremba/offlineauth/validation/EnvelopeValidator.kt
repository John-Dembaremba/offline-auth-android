package io.github.johndembaremba.offlineauth.validation

import io.github.johndembaremba.offlineauth.model.EncryptedEnvelope
import io.github.johndembaremba.offlineauth.sync.CredentialProcessingException
import io.github.johndembaremba.offlineauth.sync.CredentialProcessingFailureReason

/**
 * Validates that an encrypted envelope uses supported algorithms.
 */
class EnvelopeValidator {
    fun validate(envelope: EncryptedEnvelope) {
        if (envelope.keyAlgorithm != SUPPORTED_KEY_ALGORITHM) {
            throw CredentialProcessingException(
                reason =
                    CredentialProcessingFailureReason
                        .UNSUPPORTED_KEY_ALGORITHM,
            )
        }

        if (envelope.contentAlgorithm != SUPPORTED_CONTENT_ALGORITHM) {
            throw CredentialProcessingException(
                reason =
                    CredentialProcessingFailureReason
                        .UNSUPPORTED_CONTENT_ALGORITHM,
            )
        }
    }

    companion object {
        const val SUPPORTED_KEY_ALGORITHM =
            "RSA-OAEP-256"

        const val SUPPORTED_CONTENT_ALGORITHM =
            "A256GCM"
    }
}
