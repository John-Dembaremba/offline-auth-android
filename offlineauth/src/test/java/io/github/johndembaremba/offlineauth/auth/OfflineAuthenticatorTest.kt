package io.github.johndembaremba.offlineauth.auth

import io.github.johndembaremba.offlineauth.model.OfflineCredential
import io.github.johndembaremba.offlineauth.validation.CredentialValidator
import io.github.johndembaremba.offlineauth.verification.PasswordVerifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class OfflineAuthenticatorTest {
    private val clock =
        Clock.fixed(
            Instant.parse("2026-11-05T08:00:00Z"),
            ZoneOffset.UTC,
        )

    private val credentialValidator =
        CredentialValidator(
            clock = clock,
        )

    @Test
    fun authenticate_returnsTrueForValidCredentialAndCorrectPassword() {
        val passwordVerifier =
            PasswordVerifier { password, verifier ->
                password.concatToString() == "correct-password" &&
                    verifier == "stored-verifier"
            }

        val authenticator =
            OfflineAuthenticator(
                credentialValidator = credentialValidator,
                passwordVerifiers =
                    mapOf(
                        "test_algorithm" to passwordVerifier,
                    ),
            )

        val credential =
            credential(
                passwordAlgorithm = "test_algorithm",
            )

        assertTrue(
            authenticator.authenticate(
                password = "correct-password".toCharArray(),
                credential = credential,
            ),
        )
    }

    @Test
    fun authenticate_returnsFalseForIncorrectPassword() {
        val passwordVerifier =
            PasswordVerifier { _, _ ->
                false
            }

        val authenticator =
            OfflineAuthenticator(
                credentialValidator = credentialValidator,
                passwordVerifiers =
                    mapOf(
                        "test_algorithm" to passwordVerifier,
                    ),
            )

        assertFalse(
            authenticator.authenticate(
                password = "wrong-password".toCharArray(),
                credential =
                    credential(
                        passwordAlgorithm = "test_algorithm",
                    ),
            ),
        )
    }

    @Test
    fun authenticate_returnsFalseForExpiredCredential() {
        val passwordVerifier =
            PasswordVerifier { _, _ ->
                true
            }

        val authenticator =
            OfflineAuthenticator(
                credentialValidator = credentialValidator,
                passwordVerifiers =
                    mapOf(
                        "test_algorithm" to passwordVerifier,
                    ),
            )

        val credential =
            credential(
                passwordAlgorithm = "test_algorithm",
                expiresAt = "2026-11-05T07:59:59Z",
            )

        assertFalse(
            authenticator.authenticate(
                password = "correct-password".toCharArray(),
                credential = credential,
            ),
        )
    }

    @Test
    fun authenticate_returnsFalseForUnsupportedAlgorithm() {
        val authenticator =
            OfflineAuthenticator(
                credentialValidator = credentialValidator,
                passwordVerifiers = emptyMap(),
            )

        assertFalse(
            authenticator.authenticate(
                password = "password".toCharArray(),
                credential =
                    credential(
                        passwordAlgorithm = "unsupported",
                    ),
            ),
        )
    }

    private fun credential(
        passwordAlgorithm: String = "test_algorithm",
        passwordVerifier: String = "stored-verifier",
        credentialVersion: Int = 1,
        issuedAt: String = "2026-11-05T06:00:00Z",
        expiresAt: String = "2026-11-06T08:00:00Z",
    ): OfflineCredential =
        OfflineCredential(
            passwordAlgorithm = passwordAlgorithm,
            passwordVerifier = passwordVerifier,
            credentialVersion = credentialVersion,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
        )
}
