package io.github.johndembaremba.offlineauth.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey

/**
 * Manages an RSA key pair stored in the Android Keystore.
 *
 * The private key is generated inside the keystore and never leaves it.
 * Only the public key is exposed, so a server can encrypt data that
 * only this device can decrypt.
 *
 * @property keyAlias name the key pair is stored under in the keystore.
 */
internal class KeyStoreManager(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : PublicKeyProvider {
    private val keyStore: KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

    fun getOrCreatePublicKey(): String {
        val publicKey = getExistingPublicKey() ?: generateKeyPair()

        return Base64.encodeToString(
            publicKey.encoded,
            Base64.NO_WRAP,
        )
    }

    internal fun getPrivateKey(): PrivateKey {
        val entry =
            keyStore.getEntry(
                keyAlias,
                null,
            ) as? KeyStore.PrivateKeyEntry
                ?: throw IllegalStateException(
                    "Offline authentication key pair does not exist.",
                )

        return entry.privateKey
    }

    private fun getExistingPublicKey(): PublicKey? {
        val certificate =
            keyStore.getCertificate(keyAlias)
                ?: return null

        return certificate.publicKey
    }

    private fun generateKeyPair(): PublicKey {
        val keyPairGenerator =
            KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                ANDROID_KEYSTORE,
            )

        val keyGenParameterSpec =
            KeyGenParameterSpec
                .Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_DECRYPT,
                ).setKeySize(RSA_KEY_SIZE)
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_RSA_OAEP,
                ).setDigests(
                    KeyProperties.DIGEST_SHA256,
                    KeyProperties.DIGEST_SHA1,
                ).build()

        keyPairGenerator.initialize(keyGenParameterSpec)

        return keyPairGenerator.generateKeyPair().public
    }

    override fun getPublicKey(): String = getOrCreatePublicKey()

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_KEY_ALIAS = "offline_auth_rsa_key"
        private const val RSA_KEY_SIZE = 2048
    }
}
