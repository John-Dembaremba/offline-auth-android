package io.github.johndembaremba.offlineauth.crypto

/**
 * Provides the public key used when requesting encrypted offline credentials.
 */
fun interface PublicKeyProvider {
    fun getPublicKey(): String
}
