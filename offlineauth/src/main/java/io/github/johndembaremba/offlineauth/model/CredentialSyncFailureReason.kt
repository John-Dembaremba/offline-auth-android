package io.github.johndembaremba.offlineauth.model

/**
 * Describes why an offline credential failed to synchronize.
 */
sealed interface CredentialSyncFailureReason {
    data class Local(
        val reason: LocalCredentialSyncFailureReason,
    ) : CredentialSyncFailureReason

    data class Backend(
        val reason: String,
    ) : CredentialSyncFailureReason
}

/**
 * Stable failure reasons produced by the Android library.
 */
enum class LocalCredentialSyncFailureReason(
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

    CREDENTIAL_VALIDATION_FAILED(
        code = "credential_validation_failed",
    ),

    UNSUPPORTED_PASSWORD_ALGORITHM(
        code = "unsupported_password_algorithm",
    ),

    CREDENTIAL_VERSION_ROLLBACK(
        code = "credential_version_rollback",
    ),

    PROCESSING_FAILED(
        code = "credential_processing_failed",
    ),
}
