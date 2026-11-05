package io.github.johndembaremba.offlineauth.verification

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Verifies PBKDF2-SHA256 encoded password verifiers.
 *
 * Expected format:
 * pbkdf2_sha256$iterations$salt$hash
 */
class Pbkdf2Sha256PasswordVerifier : PasswordVerifier {
    override fun verify(
        password: CharArray,
        verifier: String,
    ): Boolean {
        val parts = verifier.split(PART_SEPARATOR)

        if (parts.size != EXPECTED_PART_COUNT) {
            return false
        }

        val algorithm = parts[0]
        val iterations =
            parts[1].toIntOrNull()
                ?: return false
        val salt = parts[2]
        val expectedHash = parts[3]

        if (algorithm != SUPPORTED_ALGORITHM) {
            return false
        }

        if (iterations <= 0 || salt.isEmpty() || expectedHash.isEmpty()) {
            return false
        }

        val expectedHashBytes =
            runCatching {
                Base64.decode(
                    expectedHash,
                    Base64.NO_WRAP,
                )
            }.getOrNull()
                ?: return false

        val spec =
            PBEKeySpec(
                password,
                salt.toByteArray(Charsets.UTF_8),
                iterations,
                expectedHashBytes.size * BITS_PER_BYTE,
            )

        return try {
            val secretKeyFactory =
                SecretKeyFactory.getInstance(
                    PBKDF2_ALGORITHM,
                )

            val actualHash =
                secretKeyFactory
                    .generateSecret(spec)
                    .encoded

            MessageDigest.isEqual(
                expectedHashBytes,
                actualHash,
            )
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        private const val SUPPORTED_ALGORITHM = "pbkdf2_sha256"
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"

        private const val EXPECTED_PART_COUNT = 4
        private const val PART_SEPARATOR = "$"
        private const val BITS_PER_BYTE = 8
    }
}
