package io.github.johndembaremba.offlineauth.serialization

import org.junit.Assert.assertEquals
import org.junit.Test

class CredentialParserTest {
    @Test
    fun parse_returnsOfflineCredential() {
        val parser = CredentialParser()

        val payload =
            """
            {
              "password_algorithm": "pbkdf2_sha256",
              "password_verifier": "pbkdf2_sha256${'$'}1000000${'$'}salt${'$'}hash",
              "credential_version": 3,
              "issued_at": "2026-11-04T12:00:00Z",
              "expires_at": "2026-11-05T12:00:00Z"
            }
            """.trimIndent().toByteArray()

        val credential = parser.parse(payload)

        assertEquals(
            "pbkdf2_sha256",
            credential.passwordAlgorithm,
        )
        assertEquals(
            3,
            credential.credentialVersion,
        )
        assertEquals(
            "2026-11-05T12:00:00Z",
            credential.expiresAt,
        )
    }
}
