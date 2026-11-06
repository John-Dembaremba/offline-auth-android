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
    UNSUPPORTED_KEY_ALGORITHM(
        code = "unsupported_key_algorithm",
    ),

    UNSUPPORTED_CONTENT_ALGORITHM(
        code = "unsupported_content_algorithm",
    ),

    DECRYPTION_FAILED(
        code = "decryption_failed",
    ),

    INVALID_CREDENTIAL_PAYLOAD(
        code = "invalid_credential_payload",
    ),

    PROCESSING_FAILED(
        code = "credential_processing_failed",
    ),
    CREDENTIAL_VALIDATION_FAILED(
        code = "credential_validation_failed",
    ),
    UNSUPPORTED_PASSWORD_ALGORITHM(
        code = "unsupported_password_algorithm",
    ),
    CREDENTIAL_VERSION_ROLLBACK(
        code = "credential_version_rollback",
    ),
}
