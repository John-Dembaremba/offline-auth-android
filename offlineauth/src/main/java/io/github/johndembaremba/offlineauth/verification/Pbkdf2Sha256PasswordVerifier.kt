package io.github.johndembaremba.offlineauth.verification

import android.os.Build
import android.util.Base64
import java.nio.CharBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Verifies Django-compatible PBKDF2-SHA256 password verifiers.
 *
 * Expected format:
 *
 * pbkdf2_sha256$iterations$salt$hash
 *
 * Android API 26+:
 *     SecretKeyFactory("PBKDF2WithHmacSHA256")
 *
 * Android API 23-25:
 *     PBKDF2 implemented with Mac("HmacSHA256")
 */
internal class Pbkdf2Sha256PasswordVerifier : PasswordVerifier {
    override fun verify(
        password: CharArray,
        verifier: String,
    ): Boolean {
        val parts =
            verifier.split(
                PART_SEPARATOR,
            )

        if (parts.size != EXPECTED_PART_COUNT) {
            return false
        }

        val algorithm =
            parts[0]

        val iterations =
            parts[1].toIntOrNull()
                ?: return false

        val salt =
            parts[2]

        val expectedHash =
            parts[3]

        if (algorithm != SUPPORTED_ALGORITHM) {
            return false
        }

        if (
            iterations <= 0 ||
            salt.isEmpty() ||
            expectedHash.isEmpty()
        ) {
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

        if (expectedHashBytes.isEmpty()) {
            return false
        }

        val saltBytes =
            salt.toByteArray(
                Charsets.UTF_8,
            )

        val actualHash =
            runCatching {
                deriveKey(
                    password = password,
                    salt = saltBytes,
                    iterations = iterations,
                    derivedKeyLength = expectedHashBytes.size,
                )
            }.getOrNull()
                ?: run {
                    expectedHashBytes.fill(0)
                    saltBytes.fill(0)
                    return false
                }

        return try {
            MessageDigest.isEqual(
                expectedHashBytes,
                actualHash,
            )
        } finally {
            expectedHashBytes.fill(0)
            actualHash.fill(0)
            saltBytes.fill(0)
        }
    }

    private fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        derivedKeyLength: Int,
    ): ByteArray =
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {
            deriveWithSecretKeyFactory(
                password = password,
                salt = salt,
                iterations = iterations,
                derivedKeyLength = derivedKeyLength,
            )
        } else {
            deriveWithHmacSha256(
                password = password,
                salt = salt,
                iterations = iterations,
                derivedKeyLength = derivedKeyLength,
            )
        }

    /**
     * Android API 26+ implementation.
     */
    private fun deriveWithSecretKeyFactory(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        derivedKeyLength: Int,
    ): ByteArray {
        val spec =
            PBEKeySpec(
                password,
                salt,
                iterations,
                derivedKeyLength * BITS_PER_BYTE,
            )

        return try {
            SecretKeyFactory
                .getInstance(
                    PBKDF2_ALGORITHM,
                ).generateSecret(
                    spec,
                ).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Android API 23-25 fallback.
     *
     * Implements PBKDF2-HMAC-SHA256 according to RFC 8018.
     */
    private fun deriveWithHmacSha256(
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

            val derivedKey =
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
                        destination = derivedKey,
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

            derivedKey
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
        val initialInput =
            ByteArray(
                salt.size + BLOCK_INDEX_SIZE_BYTES,
            )

        salt.copyInto(
            destination = initialInput,
        )

        writeBlockIndex(
            destination = initialInput,
            offset = salt.size,
            blockIndex = blockIndex,
        )

        var u =
            mac.doFinal(
                initialInput,
            )

        initialInput.fill(0)

        val result =
            u.copyOf()

        try {
            for (iteration in 2..iterations) {
                val nextU =
                    mac.doFinal(
                        u,
                    )

                u.fill(0)
                u = nextU

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

    private fun writeBlockIndex(
        destination: ByteArray,
        offset: Int,
        blockIndex: Int,
    ) {
        destination[offset] =
            (blockIndex ushr 24)
                .toByte()

        destination[offset + 1] =
            (blockIndex ushr 16)
                .toByte()

        destination[offset + 2] =
            (blockIndex ushr 8)
                .toByte()

        destination[offset + 3] =
            blockIndex
                .toByte()
    }

    private fun encodePasswordUtf8(password: CharArray): ByteArray {
        val byteBuffer =
            Charsets.UTF_8.encode(
                CharBuffer.wrap(
                    password,
                ),
            )

        return ByteArray(
            byteBuffer.remaining(),
        ).also { bytes ->
            byteBuffer.get(
                bytes,
            )
        }
    }

    companion object {
        private const val SUPPORTED_ALGORITHM =
            "pbkdf2_sha256"

        private const val PBKDF2_ALGORITHM =
            "PBKDF2WithHmacSHA256"

        private const val HMAC_SHA256 =
            "HmacSHA256"

        private const val EXPECTED_PART_COUNT =
            4

        private const val PART_SEPARATOR =
            "$"

        private const val BITS_PER_BYTE =
            8

        private const val BLOCK_INDEX_SIZE_BYTES =
            4
    }
}
