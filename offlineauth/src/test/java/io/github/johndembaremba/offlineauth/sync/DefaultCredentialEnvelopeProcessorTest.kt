package io.github.johndembaremba.offlineauth.sync

import io.github.johndembaremba.offlineauth.crypto.CredentialDecryptor
import io.github.johndembaremba.offlineauth.crypto.EnvelopeDecryptor
import io.github.johndembaremba.offlineauth.model.EncryptedEnvelope
import io.github.johndembaremba.offlineauth.serialization.CredentialParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DefaultCredentialEnvelopeProcessorTest {
    @Test
    fun process_returnsDecryptionFailedWhenEnvelopeDecryptionFails() {
        val decryptor =
            CredentialDecryptor {
                throw IllegalStateException(
                    "internal crypto failure",
                )
            }

        val processor =
            DefaultCredentialEnvelopeProcessor(
                envelopeDecryptor = decryptor,
                credentialParser = CredentialParser(),
            )

        val exception =
            assertThrows(
                CredentialProcessingException::class.java,
            ) {
                processor.process(
                    envelope = envelope(),
                )
            }

        assertEquals(
            CredentialProcessingFailureReason.DECRYPTION_FAILED,
            exception.reason,
        )

        assertEquals(
            "decryption_failed",
            exception.reason.code,
        )
    }

    @Test
    fun process_returnsInvalidCredentialPayloadWhenParsingFails() {
        val decryptor =
            CredentialDecryptor {
                """
                {
                  "invalid": "credential"
                }
                """.trimIndent().encodeToByteArray()
            }

        val processor =
            DefaultCredentialEnvelopeProcessor(
                envelopeDecryptor = decryptor,
                credentialParser = CredentialParser(),
            )

        val exception =
            assertThrows(
                CredentialProcessingException::class.java,
            ) {
                processor.process(
                    envelope = envelope(),
                )
            }

        assertEquals(
            CredentialProcessingFailureReason.INVALID_CREDENTIAL_PAYLOAD,
            exception.reason,
        )

        assertEquals(
            "invalid_credential_payload",
            exception.reason.code,
        )
    }

    @Test
    fun process_returnsCredentialValidationFailedWhenCredentialIsInvalid() {
        val decryptor =
            CredentialDecryptor {
                """
                {
                  "password_algorithm": "pbkdf2_sha256",
                  "password_verifier": "verifier",
                  "credential_version": 0,
                  "issued_at": "2026-11-06T08:00:00Z",
                  "expires_at": "2026-11-07T08:00:00Z"
                }
                """.trimIndent().encodeToByteArray()
            }

        val processor =
            DefaultCredentialEnvelopeProcessor(
                envelopeDecryptor = decryptor,
                credentialParser = CredentialParser(),
            )

        val exception =
            assertThrows(
                CredentialProcessingException::class.java,
            ) {
                processor.process(
                    envelope = envelope(),
                )
            }

        assertEquals(
            CredentialProcessingFailureReason.CREDENTIAL_VALIDATION_FAILED,
            exception.reason,
        )

        assertEquals(
            "credential_validation_failed",
            exception.reason.code,
        )
    }

    @Test
    fun process_rejectsUnsupportedPasswordAlgorithm() {
        val decryptor =
            CredentialDecryptor {
                """
                {
                  "password_algorithm": "argon2",
                  "password_verifier": "verifier",
                  "credential_version": 1,
                  "issued_at": "2026-11-06T08:00:00Z",
                  "expires_at": "2030-11-07T08:00:00Z"
                }
                """.trimIndent().encodeToByteArray()
            }

        val processor =
            DefaultCredentialEnvelopeProcessor(
                envelopeDecryptor = decryptor,
                credentialParser = CredentialParser(),
            )

        val exception =
            assertThrows(
                CredentialProcessingException::class.java,
            ) {
                processor.process(
                    envelope = envelope(),
                )
            }

        assertEquals(
            CredentialProcessingFailureReason.UNSUPPORTED_PASSWORD_ALGORITHM,
            exception.reason,
        )

        assertEquals(
            "unsupported_password_algorithm",
            exception.reason.code,
        )
    }

    private fun envelope(): EncryptedEnvelope =
        EncryptedEnvelope(
            keyAlgorithm = "RSA-OAEP-256",
            contentAlgorithm = "A256GCM",
            encryptedKey = "encrypted-key",
            nonce = "nonce",
            ciphertext = "ciphertext",
        )
}
