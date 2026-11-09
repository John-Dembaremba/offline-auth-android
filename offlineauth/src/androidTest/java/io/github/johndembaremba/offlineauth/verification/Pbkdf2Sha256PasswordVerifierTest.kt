package io.github.johndembaremba.offlineauth.verification

import android.os.Build
import android.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.CharBuffer
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class Pbkdf2Sha256PasswordVerifierTest {
    private val verifier =
        Pbkdf2Sha256PasswordVerifier()

    @Test
    fun verify_returnsTrueForCorrectPassword() {
        assertTrue(
            verifier.verify(
                password =
                    "correct-password"
                        .toCharArray(),
                verifier =
                CORRECT_PASSWORD_VERIFIER,
            ),
        )
    }

    @Test
    fun verify_returnsFalseForIncorrectPassword() {
        assertFalse(
            verifier.verify(
                password =
                    "wrong-password"
                        .toCharArray(),
                verifier =
                CORRECT_PASSWORD_VERIFIER,
            ),
        )
    }

    @Test
    fun verify_returnsFalseForUnsupportedAlgorithm() {
        val encoded =
            CORRECT_PASSWORD_VERIFIER.replace(
                oldValue = "pbkdf2_sha256",
                newValue = "unsupported",
            )

        assertFalse(
            verifier.verify(
                password =
                    "correct-password"
                        .toCharArray(),
                verifier = encoded,
            ),
        )
    }

    @Test
    fun verify_returnsFalseForMalformedVerifier() {
        assertFalse(
            verifier.verify(
                password =
                    "password"
                        .toCharArray(),
                verifier = "invalid",
            ),
        )
    }

    @Test
    fun verify_returnsFalseForInvalidIterations() {
        assertFalse(
            verifier.verify(
                password =
                    "password"
                        .toCharArray(),
                verifier =
                    "pbkdf2_sha256" +
                        "${'$'}invalid" +
                        "${'$'}salt" +
                        "${'$'}A5Si7eMyyaE+uC6bJGMWBMMd+Xi04vD70sVJlE+deaU=",
            ),
        )
    }

    @Test
    fun verify_returnsFalseForZeroIterations() {
        assertFalse(
            verifier.verify(
                password =
                    "password"
                        .toCharArray(),
                verifier =
                    "pbkdf2_sha256" +
                        "${'$'}0" +
                        "${'$'}salt" +
                        "${'$'}A5Si7eMyyaE+uC6bJGMWBMMd+Xi04vD70sVJlE+deaU=",
            ),
        )
    }

    @Test
    fun verify_returnsFalseForEmptySalt() {
        assertFalse(
            verifier.verify(
                password =
                    "password"
                        .toCharArray(),
                verifier =
                    "pbkdf2_sha256" +
                        "${'$'}100000" +
                        "${'$'}" +
                        "${'$'}A5Si7eMyyaE+uC6bJGMWBMMd+Xi04vD70sVJlE+deaU=",
            ),
        )
    }

    @Test
    fun verify_returnsFalseForInvalidBase64Hash() {
        assertFalse(
            verifier.verify(
                password =
                    "password"
                        .toCharArray(),
                verifier =
                    "pbkdf2_sha256" +
                        "${'$'}100000" +
                        "${'$'}salt" +
                        "${'$'}%%%invalid-base64%%%",
            ),
        )
    }

    @Test
    fun verify_matchesKnownDjangoCompatibleVector() {
        assertTrue(
            verifier.verify(
                password =
                    "password"
                        .toCharArray(),
                verifier =
                SECOND_PASSWORD_VERIFIER,
            ),
        )
    }

    @Test
    fun nativeAndHmacFallback_produceIdenticalDerivedKey() {
        assumeTrue(
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O,
        )

        val password =
            "compatibility-password"
                .toCharArray()

        val salt =
            "compatibility-salt"
                .toByteArray()

        val native =
            deriveWithSecretKeyFactory(
                password = password,
                salt = salt,
                iterations = 100_000,
                lengthBytes = 32,
            )

        val fallback =
            deriveWithHmacSha256(
                password = password,
                salt = salt,
                iterations = 100_000,
                lengthBytes = 32,
            )

        try {
            assertArrayEquals(
                native,
                fallback,
            )
        } finally {
            password.fill('\u0000')
            salt.fill(0)
            native.fill(0)
            fallback.fill(0)
        }
    }

    private fun deriveWithSecretKeyFactory(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        lengthBytes: Int,
    ): ByteArray {
        val spec =
            PBEKeySpec(
                password,
                salt,
                iterations,
                lengthBytes * 8,
            )

        return try {
            SecretKeyFactory
                .getInstance(
                    "PBKDF2WithHmacSHA256",
                ).generateSecret(
                    spec,
                ).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun deriveWithHmacSha256(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        lengthBytes: Int,
    ): ByteArray {
        val passwordBytes =
            encodePasswordUtf8(
                password,
            )

        return try {
            val mac =
                Mac.getInstance(
                    "HmacSHA256",
                )

            mac.init(
                SecretKeySpec(
                    passwordBytes,
                    "HmacSHA256",
                ),
            )

            val hashLength =
                mac.macLength

            val blockCount =
                (lengthBytes + hashLength - 1) /
                    hashLength

            val output =
                ByteArray(
                    lengthBytes,
                )

            var offset =
                0

            for (blockIndex in 1..blockCount) {
                val block =
                    deriveBlock(
                        mac = mac,
                        salt = salt,
                        iterations = iterations,
                        blockIndex = blockIndex,
                    )

                val copyLength =
                    minOf(
                        block.size,
                        lengthBytes - offset,
                    )

                block.copyInto(
                    destination = output,
                    destinationOffset = offset,
                    endIndex = copyLength,
                )

                block.fill(0)

                offset +=
                    copyLength
            }

            output
        } finally {
            passwordBytes.fill(0)
        }
    }

    private fun deriveBlock(
        mac: Mac,
        salt: ByteArray,
        iterations: Int,
        blockIndex: Int,
    ): ByteArray {
        val input =
            ByteArray(
                salt.size + 4,
            )

        salt.copyInto(
            destination = input,
        )

        input[salt.size] =
            (blockIndex ushr 24)
                .toByte()

        input[salt.size + 1] =
            (blockIndex ushr 16)
                .toByte()

        input[salt.size + 2] =
            (blockIndex ushr 8)
                .toByte()

        input[salt.size + 3] =
            blockIndex
                .toByte()

        var u =
            mac.doFinal(
                input,
            )

        val result =
            u.copyOf()

        for (iteration in 2..iterations) {
            val next =
                mac.doFinal(
                    u,
                )

            u.fill(0)
            u = next

            for (index in result.indices) {
                result[index] =
                    (
                        result[index].toInt() xor
                            u[index].toInt()
                    ).toByte()
            }
        }

        input.fill(0)
        u.fill(0)

        return result
    }

    private fun encodePasswordUtf8(password: CharArray): ByteArray {
        val buffer =
            Charsets.UTF_8.encode(
                CharBuffer.wrap(
                    password,
                ),
            )

        return ByteArray(
            buffer.remaining(),
        ).also {
            buffer.get(
                it,
            )
        }
    }

    companion object {
        private const val CORRECT_PASSWORD_VERIFIER =
            "pbkdf2_sha256" +
                "${'$'}100000" +
                "${'$'}test-salt" +
                "${'$'}WQctIv0/K3WVE3NHe2KTvKu2CQEkH7mfyBTFXHN7Sms="

        private const val SECOND_PASSWORD_VERIFIER =
            "pbkdf2_sha256" +
                "${'$'}100000" +
                "${'$'}salt" +
                "${'$'}A5Si7eMyyaE+uC6bJGMWBMMd+Xi04vD70sVJlE+deaU="
    }
}
