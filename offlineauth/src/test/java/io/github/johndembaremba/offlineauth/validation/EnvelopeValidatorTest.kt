package io.github.johndembaremba.offlineauth.validation

import io.github.johndembaremba.offlineauth.model.EncryptedEnvelope
import io.github.johndembaremba.offlineauth.sync.CredentialProcessingException
import io.github.johndembaremba.offlineauth.sync.CredentialProcessingFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EnvelopeValidatorTest {
    private val validator =
        EnvelopeValidator()

    @Test
    fun validate_acceptsSupportedAlgorithms() {
        validator.validate(
            envelope =
                envelope(
                    keyAlgorithm = "RSA-OAEP-256",
                    contentAlgorithm = "A256GCM",
                ),
        )
    }

    @Test
    fun validate_rejectsUnsupportedKeyAlgorithm() {
        val exception =
            assertThrows(
                CredentialProcessingException::class.java,
            ) {
                validator.validate(
                    envelope =
                        envelope(
                            keyAlgorithm = "RSA1_5",
                            contentAlgorithm = "A256GCM",
                        ),
                )
            }

        assertEquals(
            CredentialProcessingFailureReason.UNSUPPORTED_KEY_ALGORITHM,
            exception.reason,
        )
    }

    @Test
    fun validate_rejectsUnsupportedContentAlgorithm() {
        val exception =
            assertThrows(
                CredentialProcessingException::class.java,
            ) {
                validator.validate(
                    envelope =
                        envelope(
                            keyAlgorithm = "RSA-OAEP-256",
                            contentAlgorithm = "A128GCM",
                        ),
                )
            }

        assertEquals(
            CredentialProcessingFailureReason.UNSUPPORTED_CONTENT_ALGORITHM,
            exception.reason,
        )
    }

    private fun envelope(
        keyAlgorithm: String,
        contentAlgorithm: String,
    ): EncryptedEnvelope =
        EncryptedEnvelope(
            keyAlgorithm = keyAlgorithm,
            contentAlgorithm = contentAlgorithm,
            encryptedKey = "encrypted-key",
            nonce = "nonce",
            ciphertext = "ciphertext",
        )
}
