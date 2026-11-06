package io.github.johndembaremba.offlineauth.sync

/**
 * Represents a predictable failure while processing a synchronized credential.
 */
class CredentialProcessingException(
    val reason: CredentialProcessingFailureReason,
    cause: Throwable? = null,
) : RuntimeException(reason.code, cause)

enum class CredentialProcessingFailureReason(
    val code: String,
) {
    DECRYPTION_FAILED(
        code = "decryption_failed",
    ),

    INVALID_CREDENTIAL_PAYLOAD(
        code = "invalid_credential_payload",
    ),

    PROCESSING_FAILED(
        code = "credential_processing_failed",
    ),
}
