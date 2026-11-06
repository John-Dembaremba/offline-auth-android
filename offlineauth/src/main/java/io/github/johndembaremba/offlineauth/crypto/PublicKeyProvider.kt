package io.github.johndembaremba.offlineauth.crypto

/**
 * Provides the public key used when requesting encrypted offline credentials.
 */
internal fun interface PublicKeyProvider {
    fun getPublicKey(): String
}
