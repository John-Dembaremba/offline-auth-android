package io.github.johndembaremba.offlineauth.testing

import android.util.Base64
import java.nio.CharBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Creates Django-compatible PBKDF2-SHA256 password verifiers for
 * instrumentation tests.
 *
 * This helper intentionally uses HmacSHA256 directly so test fixtures
 * work on every supported Android version, including API 23-25.
 */
internal object Pbkdf2TestHelper {
    fun createVerifier(
        password: String,
        salt: String,
        iterations: Int = DEFAULT_ITERATIONS,
    ): String {
        val passwordChars =
            password.toCharArray()

        val saltBytes =
            salt.toByteArray(
                Charsets.UTF_8,
            )

        val derivedKey =
            try {
                deriveKey(
                    password = passwordChars,
                    salt = saltBytes,
                    iterations = iterations,
                    derivedKeyLength = HASH_LENGTH_BYTES,
                )
            } finally {
                passwordChars.fill('\u0000')
                saltBytes.fill(0)
            }

        return try {
            val encodedHash =
                Base64.encodeToString(
                    derivedKey,
                    Base64.NO_WRAP,
                )

            buildString {
                append("pbkdf2_sha256")
                append('$')
                append(iterations)
                append('$')
                append(salt)
                append('$')
                append(encodedHash)
            }
        } finally {
            derivedKey.fill(0)
        }
    }

    private fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        derivedKeyLength: Int,
    ): ByteArray {
        require(iterations > 0)
        require(derivedKeyLength > 0)

        val passwordBytes =
            encodePasswordUtf8(
                password,
            )

        return try {
            val mac =
                Mac.getInstance(
                    HMAC_SHA256,
                )

            mac.init(
                SecretKeySpec(
                    passwordBytes,
                    HMAC_SHA256,
                ),
            )

            val hashLength =
                mac.macLength

            val blockCount =
                (derivedKeyLength + hashLength - 1) /
                    hashLength

            val output =
                ByteArray(
                    derivedKeyLength,
                )

            var destinationOffset =
                0

            for (blockIndex in 1..blockCount) {
                val block =
                    deriveBlock(
                        mac = mac,
                        salt = salt,
                        iterations = iterations,
                        blockIndex = blockIndex,
                    )

                try {
                    val bytesToCopy =
                        minOf(
                            block.size,
                            derivedKeyLength - destinationOffset,
                        )

                    block.copyInto(
                        destination = output,
                        destinationOffset = destinationOffset,
                        startIndex = 0,
                        endIndex = bytesToCopy,
                    )

                    destinationOffset +=
                        bytesToCopy
                } finally {
                    block.fill(0)
                }
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
                salt.size + BLOCK_INDEX_SIZE_BYTES,
            )

        salt.copyInto(
            destination = input,
        )

        input[salt.size] =
            (blockIndex ushr 24).toByte()

        input[salt.size + 1] =
            (blockIndex ushr 16).toByte()

        input[salt.size + 2] =
            (blockIndex ushr 8).toByte()

        input[salt.size + 3] =
            blockIndex.toByte()

        var u =
            mac.doFinal(
                input,
            )

        input.fill(0)

        val result =
            u.copyOf()

        try {
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

            return result
        } finally {
            u.fill(0)
        }
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
        ).also { bytes ->
            buffer.get(
                bytes,
            )
        }
    }

    private const val HMAC_SHA256 =
        "HmacSHA256"

    private const val HASH_LENGTH_BYTES =
        32

    private const val BLOCK_INDEX_SIZE_BYTES =
        4

    private const val DEFAULT_ITERATIONS =
        100_000
}
