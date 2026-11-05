package io.github.johndembaremba.offlineauth.serialization

import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotParserTest {
    @Test
    fun parse_returnsCredentialSnapshot() {
        val payload =
            """
            {
              "protocol_version": 1,
              "credentials": [
                {
                  "user_id": "1",
                  "username": "alice",
                  "envelope": {
                    "key_algorithm": "RSA-OAEP-256",
                    "content_algorithm": "A256GCM",
                    "encrypted_key": "encrypted-key",
                    "nonce": "nonce",
                    "ciphertext": "ciphertext"
                  }
                }
              ],
              "failures": [
                {
                  "user_id": "2",
                  "username": "bob",
                  "reason": "unusable_password"
                }
              ]
            }
            """.trimIndent()

        val snapshot =
            SnapshotParser().parse(payload)

        assertEquals(
            1,
            snapshot.protocolVersion,
        )

        assertEquals(
            "alice",
            snapshot.credentials.first().username,
        )

        assertEquals(
            "bob",
            snapshot.failures.first().username,
        )

        assertEquals(
            "unusable_password",
            snapshot.failures.first().reason,
        )
    }
}
