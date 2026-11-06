package io.github.johndembaremba.offlineauth.verification

/**
 * Verifies a password against an encoded password verifier.
 */
internal fun interface PasswordVerifier {
    fun verify(
        password: CharArray,
        verifier: String,
    ): Boolean
}
