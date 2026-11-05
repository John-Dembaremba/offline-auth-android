package io.github.johndembaremba.offlineauth.verification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class Pbkdf2Sha256PasswordVerifierTest {
    private val verifier = Pbkdf2Sha256PasswordVerifier()

    @Test
    fun verify_returnsTrueForCorrectPassword() {
        val encoded =
            createVerifier(
                password = "correct-password",
                iterations = 100_000,
                salt = "test-salt",
            )

        assertTrue(
            verifier.verify(
                password = "correct-password".toCharArray(),
                verifier = encoded,
            ),
        )
    }

    @Test
    fun verify_returnsFalseForIncorrectPassword() {
        val encoded =
            createVerifier(
                password = "correct-password",
                iterations = 100_000,
                salt = "test-salt",
            )

        assertFalse(
            verifier.verify(
                password = "wrong-password".toCharArray(),
                verifier = encoded,
            ),
        )
    }

    @Test
    fun verify_returnsFalseForUnsupportedAlgorithm() {
        val encoded =
            createVerifier(
                password = "password",
                iterations = 100_000,
                salt = "salt",
            ).replace(
                oldValue = "pbkdf2_sha256",
                newValue = "unsupported",
            )

        assertFalse(
            verifier.verify(
                password = "password".toCharArray(),
                verifier = encoded,
            ),
        )
    }

    @Test
    fun verify_returnsFalseForMalformedVerifier() {
        assertFalse(
            verifier.verify(
                password = "password".toCharArray(),
                verifier = "invalid",
            ),
        )
    }

    @Test
    fun verify_returnsFalseForInvalidIterations() {
        assertFalse(
            verifier.verify(
                password = "password".toCharArray(),
                verifier =
                    "pbkdf2_sha256${'$'}invalid${'$'}salt${'$'}hash",
            ),
        )
    }

    private fun createVerifier(
        password: String,
        iterations: Int,
        salt: String,
    ): String {
        val spec =
            PBEKeySpec(
                password.toCharArray(),
                salt.toByteArray(),
                iterations,
                HASH_LENGTH_BITS,
            )

        val hash =
            SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded

        val encodedHash =
            Base64
                .getEncoder()
                .encodeToString(hash)

        return buildString {
            append("pbkdf2_sha256")
            append('$')
            append(iterations)
            append('$')
            append(salt)
            append('$')
            append(encodedHash)
        }
    }

    companion object {
        private const val HASH_LENGTH_BITS = 256
    }
}
