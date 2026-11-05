package io.github.johndembaremba.offlineauth.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Manages the AES key used to encrypt credentials stored on the device.
 *
 * The key is generated and retained inside Android Keystore and is never
 * exported by the library.
 */
internal class StorageKeyManager(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) {
    private val keyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

    fun getOrCreateKey(): SecretKey {
        val existingKey = getExistingKey()

        if (existingKey != null) {
            return existingKey
        }

        return generateKey()
    }

    private fun getExistingKey(): SecretKey? =
        keyStore.getKey(
            keyAlias,
            null,
        ) as? SecretKey

    private fun generateKey(): SecretKey {
        val keyGenerator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE,
            )

        val parameters =
            KeyGenParameterSpec
                .Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or
                        KeyProperties.PURPOSE_DECRYPT,
                ).setKeySize(AES_KEY_SIZE)
                .setBlockModes(
                    KeyProperties.BLOCK_MODE_GCM,
                ).setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE,
                ).build()

        keyGenerator.init(parameters)

        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_KEY_ALIAS =
            "offline_auth_storage_key"

        private const val AES_KEY_SIZE = 256
    }
}
