package io.github.johndembaremba.offlineauth.validation

import io.github.johndembaremba.offlineauth.model.OfflineCredential
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CredentialValidatorTest {
    private val clock =
        Clock.fixed(
            Instant.parse("2026-11-04T12:00:00Z"),
            ZoneOffset.UTC,
        )

    private val validator =
        CredentialValidator(
            clock = clock,
        )

    @Test
    fun isValid_returnsTrueForValidCredential() {
        val credential =
            credential(
                issuedAt = "2026-11-04T10:00:00Z",
                expiresAt = "2026-11-05T12:00:00Z",
            )

        assertTrue(
            validator.isValid(credential),
        )
    }

    @Test
    fun isValid_returnsFalseForExpiredCredential() {
        val credential =
            credential(
                expiresAt = "2026-11-04T11:59:59Z",
            )

        assertFalse(
            validator.isValid(credential),
        )
    }

    @Test
    fun isValid_returnsFalseWhenCredentialExpiresNow() {
        val credential =
            credential(
                expiresAt = "2026-11-04T12:00:00Z",
            )

        assertFalse(
            validator.isValid(credential),
        )
    }

    @Test
    fun isValid_returnsFalseForInvalidCredentialVersion() {
        val credential =
            credential(
                credentialVersion = 0,
            )

        assertFalse(
            validator.isValid(credential),
        )
    }

    @Test
    fun isValid_returnsFalseForInvalidIssuedAt() {
        val credential =
            credential(
                issuedAt = "invalid",
            )

        assertFalse(
            validator.isValid(credential),
        )
    }

    @Test
    fun isValid_returnsFalseForInvalidExpiresAt() {
        val credential =
            credential(
                expiresAt = "invalid",
            )

        assertFalse(
            validator.isValid(credential),
        )
    }

    @Test
    fun isValid_returnsFalseWhenExpiryEqualsIssueTime() {
        val credential =
            credential(
                issuedAt = "2026-11-05T12:00:00Z",
                expiresAt = "2026-11-05T12:00:00Z",
            )

        assertFalse(
            validator.isValid(credential),
        )
    }

    @Test
    fun isValid_returnsFalseWhenExpiryPrecedesIssueTime() {
        val credential =
            credential(
                issuedAt = "2026-11-05T12:00:00Z",
                expiresAt = "2026-11-04T12:00:00Z",
            )

        assertFalse(
            validator.isValid(credential),
        )
    }

    private fun credential(
        credentialVersion: Int = 1,
        issuedAt: String = "2026-11-04T10:00:00Z",
        expiresAt: String = "2026-11-05T12:00:00Z",
    ): OfflineCredential =
        OfflineCredential(
            passwordAlgorithm = "pbkdf2_sha256",
            passwordVerifier =
                "pbkdf2_sha256${'$'}1000000${'$'}salt${'$'}hash",
            credentialVersion = credentialVersion,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
        )
}
